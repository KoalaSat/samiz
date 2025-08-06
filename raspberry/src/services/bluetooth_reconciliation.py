"""
Bluetooth Reconciliation service for the Samiz Raspberry Pi service.
Main orchestrator that handles BLE communication and data synchronization.
"""

import asyncio
import json
from typing import Dict, List, Optional, Any
from datetime import datetime

from models.logger import Logger
from bluetooth.bluetooth_ble import BluetoothBle
from bluetooth.bluetooth_ble_callback import BluetoothBleCallback
from utils.compression import Compression
from negentropy import Negentropy, VectorStorage

class BluetoothReconciliation(BluetoothBleCallback):
    """
    Main orchestrator service for Bluetooth communication and reconciliation.
    Similar to the Android BluetoothReconciliation.kt implementation.
    """
    
    def __init__(self):
        """
        Initialize the BluetoothReconciliation service.
        """
        # Device tracking for reconciliation
        self.device_send_ids: Dict[str, List[str]] = {}
        self.device_reconciliation: Dict[str, bytes] = {}
        
        # BLE service
        self.bluetooth_ble: Optional[BluetoothBle] = None
        
        # Service state
        self._running = False
        
        # TODO: Add NostrClient when implementing Nostr functionality
        # self.nostr_client = NostrClient()
        
        Logger.d("BluetoothReconciliation", "BluetoothReconciliation initialized")
    
    async def start(self) -> None:
        """
        Start the BluetoothReconciliation service and all its components.
        """
        try:
            Logger.d("BluetoothReconciliation", "Starting BluetoothReconciliation service")
            
            # Initialize and start BLE service
            Logger.d("BluetoothReconciliation", "Starting BLE")
            self.bluetooth_ble = BluetoothBle(self)
            await self.bluetooth_ble.start()
            
            # TODO: Initialize database cleanup
            Logger.d("BluetoothReconciliation", "Cleaning DB (not implemented yet)")
            
            # TODO: Start Nostr client
            Logger.d("BluetoothReconciliation", "Starting nostr client (not implemented yet)")
            
            self._running = True
            Logger.i("BluetoothReconciliation", "BluetoothReconciliation service started successfully")
            
        except Exception as e:
            Logger.e("BluetoothReconciliation", f"Failed to start service: {e}")
            raise
    
    async def close(self) -> None:
        """
        Close the BluetoothReconciliation service and cleanup resources.
        """
        Logger.d("BluetoothReconciliation", "Closing BluetoothReconciliation service")
        self._running = False
        
        try:
            # Close BLE service
            if self.bluetooth_ble:
                await self.bluetooth_ble.close()
            
            # TODO: Close Nostr client when implemented
            
            # Clear reconciliation data
            self.device_send_ids.clear()
            self.device_reconciliation.clear()
            
            Logger.i("BluetoothReconciliation", "BluetoothReconciliation service closed")
            
        except Exception as e:
            Logger.e("BluetoothReconciliation", f"Error closing service: {e}")
    
    # BluetoothBleCallback implementation
    
    async def on_connection(self, bluetooth_ble, device_address: str) -> None:
        """
        Handle new BLE connection.
        
        Args:
            bluetooth_ble: Reference to BluetoothBle instance
            device_address: MAC address of the connected device
        """
        Logger.d("BluetoothReconciliation", f"{device_address} - Generating negentropy vector")
        
        init_output = self._generateNegentropy()

        Logger.d("BluetoothReconciliation", f"{device_address} - Generated negentropy init message: {init_output}")

        neg_open_msg = self._create_neg_open_message(device_address, init_output)
        
        await bluetooth_ble.write_message(device_address, neg_open_msg)
    
    async def on_read_response(self, bluetooth_ble, device_address: str, message: bytes) -> None:
        """
        Handle read response from a BLE device.
        
        Args:
            bluetooth_ble: Reference to BluetoothBle instance
            device_address: MAC address of the device
            message: Received message bytes
        """
        try:
            Logger.d("BluetoothReconciliation", f"{device_address} - Read response received")
            
            # Parse JSON message
            json_array = json.loads(message)
            msg_type = json_array[0] if len(json_array) > 0 else None
            
            if msg_type == "NEG-MSG":
                await self._handle_neg_msg(bluetooth_ble, device_address, json_array)
            elif msg_type == "EVENT":
                await self._handle_event_msg(bluetooth_ble, device_address, json_array)
            elif msg_type == "EOSE":
                Logger.d("BluetoothReconciliation", f"{device_address} - All missing events received")
                await self._send_have_event(bluetooth_ble, device_address, False)
            else:
                Logger.w("BluetoothReconciliation", f"{device_address} - Unknown message type: {msg_type}")
                
        except (json.JSONDecodeError, UnicodeDecodeError, IndexError) as e:
            Logger.e("BluetoothReconciliation", f"{device_address} - Invalid JSON onReadResponse: {e}")
    
    async def on_read_request(self, bluetooth_ble, device_address: str) -> Optional[bytes]:
        """
        Handle read request from a BLE device.
        
        Args:
            bluetooth_ble: Reference to BluetoothBle instance
            device_address: MAC address of the requesting device
            
        Returns:
            Response bytes or None
        """
        Logger.d("BluetoothReconciliation", f"{device_address} - Received read request")
        
        # Check if we have reconciliation data to send
        reconciliation = self.device_reconciliation.get(device_address)
        if reconciliation:
            Logger.d("BluetoothReconciliation", f"{device_address} - Sending reconciliation messages")
            self.device_reconciliation.pop(device_address, None)
            return self._create_neg_message(device_address, reconciliation)
        
        # Check if we have events to send
        need_ids = self.device_send_ids.get(device_address)
        if need_ids and len(need_ids) > 0:
            Logger.d("BluetoothReconciliation", f"{device_address} - Checking needed event")
            event_id = need_ids.pop()  # Get last event
            
            # TODO: Get actual event from database/storage
            event_json = []
            Logger.d("BluetoothReconciliation", f"{device_address} - Generating missing event: {event_id[:5]}...{event_id[-5:]}")
            Logger.d("BluetoothReconciliation", f"{device_address} - {len(need_ids)} events left")
            
            return self._create_event_message(device_address, event_json)
        
        # No more data to send
        return self._create_end_message(device_address)
    
    async def on_write_request(self, bluetooth_ble, device_address: str, message: bytes) -> None:
        """
        Handle write request from a BLE device.
        
        Args:
            bluetooth_ble: Reference to BluetoothBle instance
            device_address: MAC address of the device
            message: Written message bytes
        """
        try:
            Logger.d("BluetoothReconciliation", f"{device_address} - Write request received")
            
            # Parse JSON message
            json_array = json.loads(message)
            msg_type = json_array[0] if len(json_array) > 0 else None

            Logger.w("BluetoothReconciliation", f"{device_address} - Write message: {json_array}")
            
            if msg_type == "NEG-OPEN":
                await self._handle_neg_open(bluetooth_ble, device_address, json_array)
            elif msg_type == "EVENT":
                await self._handle_event_msg(bluetooth_ble, device_address, json_array)
            elif msg_type == "REQ":
                await self._handle_req_msg(bluetooth_ble, device_address, json_array)
            else:
                Logger.w("BluetoothReconciliation", f"{device_address} - Unknown write message type: {msg_type}")
                
        except (json.JSONDecodeError, UnicodeDecodeError, IndexError) as e:
            Logger.e("BluetoothReconciliation", f"{device_address} - Invalid JSON onWriteRequest: {e}")
    
    async def on_write_success(self, bluetooth_ble, device_address: str) -> None:
        """
        Handle successful write operation.
        
        Args:
            bluetooth_ble: Reference to BluetoothBle instance
            device_address: MAC address of the device
        """
        await bluetooth_ble.read_message(device_address)
    
    async def on_characteristic_changed(self, bluetooth_ble, device_address: str) -> None:
        """
        Handle characteristic change notification.
        
        Args:
            bluetooth_ble: Reference to BluetoothBle instance
            device_address: MAC address of the device
        """
        await bluetooth_ble.read_message(device_address)
    
    # Private helper methods
    
    async def _handle_neg_msg(self, bluetooth_ble, device_address: str, json_array: List[Any]) -> None:
        """
        Handle negentropy reconciliation message.
        """
        try:
            hex_msg = json_array[2] if len(json_array) > 2 else None
            if hex_msg:
                Logger.d("BluetoothReconciliation", f"{device_address} - Received negentropy reconciliation message: {hex_msg}")
                
                reconcile, send, need = self.negentropy.reconcile(hex_msg)

                self.device_send_ids[device_address] = send
                
                await self._send_subscription_event(bluetooth_ble, device_address, send)
            else:
                Logger.e("BluetoothReconciliation", f"{device_address} - Bad formatted negentropy reconciliation message")
        except IndexError as e:
            Logger.e("BluetoothReconciliation", f"{device_address} - Error parsing NEG-MSG: {e}")
        except Exception as e:
            Logger.e("BluetoothReconciliation", f"{device_address} - Error in negentropy reconciliation: {e}")
    
    async def _handle_event_msg(self, bluetooth_ble, device_address: str, json_array: List[Any]) -> None:
        """
        Handle event message.
        """
        try:
            msg = json_array[2] if len(json_array) > 2 else None
            if msg:
                Logger.d("BluetoothReconciliation", f"{device_address} - Received missing nostr note")
                # TODO: Process actual event and store in database
                # TODO: Update received events counter
                await self._send_have_event(bluetooth_ble, device_address, True)
        except IndexError as e:
            Logger.e("BluetoothReconciliation", f"{device_address} - Error parsing EVENT: {e}")
    
    async def _handle_neg_open(self, bluetooth_ble, device_address: str, json_array: List[Any]) -> None:
        """
        Handle negentropy open message.
        """
        try:
            Logger.d("BluetoothReconciliation", f"{device_address} - NEG-OPEN")
            
            hex_msg = json_array[3] if len(json_array) > 3 else None
            if hex_msg:
                recontiliation = self._generateNegentropy()
                
                self.device_reconciliation[device_address] = recontiliation

                Logger.d("BluetoothReconciliation", f"{device_address} - Reconciliation message stored")
            
        except IndexError as e:
            Logger.e("BluetoothReconciliation", f"{device_address} - Error parsing NEG-OPEN: {e}")
        except Exception as e:
            Logger.e("BluetoothReconciliation", f"{device_address} - Error in reconciliation: {e}")
    
    async def _handle_req_msg(self, bluetooth_ble, device_address: str, json_array: List[Any]) -> None:
        """
        Handle request message.
        """
        try:
            filters_string = json_array[2] if len(json_array) > 2 else None
            if filters_string:
                filters = json.loads(filters_string)
                ids = filters.get("ids", [])
                
                if ids:
                    self.device_send_ids[device_address] = ids
                    Logger.d("BluetoothReconciliation", f"{device_address} - Device needs {len(ids)} events")
                else:
                    Logger.d("BluetoothReconciliation", f"{device_address} - Device needs no events")
                    
        except (json.JSONDecodeError, IndexError) as e:
            Logger.e("BluetoothReconciliation", f"{device_address} - Error parsing REQ: {e}")
    
    async def _send_have_event(self, bluetooth_ble, device_address: str, follow_workflow: bool) -> None:
        """
        Send available events to device.
        """
        Logger.d("BluetoothReconciliation", f"{device_address} - Checking for needed messages")
        
        send_ids = self.device_send_ids.get(device_address)
        if send_ids and len(send_ids) > 0:
            event_id = send_ids.pop()
            
            # await self._write_event(bluetooth_ble, device_address, event_json) TODO
        else:
            Logger.d("BluetoothReconciliation", f"{device_address} - No more events to send")
            if follow_workflow:
                await bluetooth_ble._write_end(device_address)
    
    async def _write_event(self, bluetooth_ble, device_address: str, event_json: str) -> None:
        """
        Write an event to the device.
        """
        Logger.d("BluetoothReconciliation", f"{device_address} - Sending missing event")
        message = self._create_event_message(device_address, event_json)
        await bluetooth_ble.write_message(device_address, message)
        
        # TODO: Update sent events counter
        remaining = len(self.device_send_ids.get(device_address, []))
        Logger.d("BluetoothReconciliation", f"{device_address} - {remaining} events left")

    async def _write_end(self, bluetooth_ble, device_address: str) -> None:
        """
        Write an event to the device.
        """
        Logger.d("BluetoothReconciliation", f"{device_address} - Sending EOSE")
        message = self._create_end_message(device_address)
        await bluetooth_ble.write_message(device_address, message)
    
    async def _send_subscription_event(self, bluetooth_ble, device_address: str, need_ids: List[str]) -> None:
        """
        Send subscription request for needed events.
        """
        filters = {"ids": need_ids}
        message = self._create_subscription_message(device_address, filters)
        await bluetooth_ble.write_message(device_address, message)
    
    # Message creation helper methods

    def _generateNegentropy(self) -> str:
        storage = VectorStorage()
        storage.seal()
        self.negentropy = Negentropy(storage)
        return self.negentropy.initiate()
    
    def _create_neg_open_message(self, device_address: str, msg: str) -> bytes:
        """
        Create a negentropy open message.
        
        Args:
            device_address: MAC address of the device
            msg: Initial negentropy message string
            
        Returns:
            JSON bytes message
        """
        
        json_array = [
            "NEG-OPEN",  # type
            device_address.replace(":", ""),  # subscription ID
            "{}",  # nostr filters
            msg  # initial message as hex string
        ]
        
        return json.dumps(json_array).encode('utf-8')
    
    def _create_neg_message(self, device_address: str, msg: bytes) -> bytes:
        """
        Create a negentropy reconciliation message.
        
        Args:
            device_address: MAC address of the device
            msg: Reconciliation message bytes
            
        Returns:
            JSON bytes message
        """
        json_array = [
            "NEG-MSG",  # type
            device_address.replace(":", ""),  # subscription ID
            msg  # reconciliation message as hex string
        ]

        return json.dumps(json_array).encode('utf-8')
    
    def _create_event_message(self, device_address: str, event_json: str) -> bytes:
        """
        Create an event message.
        
        Args:
            device_address: MAC address of the device
            event_json: Event JSON string
            
        Returns:
            JSON bytes message
        """
        json_array = [
            "EVENT",  # type
            device_address.replace(":", ""),  # subscription ID
            event_json  # event data
        ]
        return json.dumps(json_array).encode('utf-8')
    
    def _create_subscription_message(self, device_address: str, filters: Dict[str, Any]) -> bytes:
        """
        Create a subscription request message.
        
        Args:
            device_address: MAC address of the device
            filters: Subscription filters
            
        Returns:
            JSON bytes message
        """
        json_array = [
            "REQ",  # type
            device_address.replace(":", ""),  # subscription ID
            json.dumps(filters)  # filters
        ]
        return json.dumps(json_array).encode('utf-8')
    
    def _create_end_message(self, device_address: str) -> bytes:
        """
        Create an end-of-stream message.
        
        Args:
            device_address: MAC address of the device
            
        Returns:
            JSON bytes message
        """
        json_array = [
            "EOSE",  # type
            device_address.replace(":", "")  # subscription ID
        ]
        return json.dumps(json_array).encode('utf-8')
