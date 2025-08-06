"""
Bluetooth BLE Client implementation for the Samiz Raspberry Pi service.
Handles BLE client connections and communication.
"""

import asyncio
import uuid
from typing import Dict, Optional, List
from bleak import BleakClient, BleakScanner
from bleak.backends.device import BLEDevice

from models.logger import Logger
from .bluetooth_ble_callback import BluetoothBleClientCallback
from utils.compression import Compression


class BluetoothBleClient:
    """
    BLE Client implementation using Bleak.
    Handles connections to BLE servers and manages communication.
    """
    
    def __init__(self, bluetooth_ble, callback: BluetoothBleClientCallback):
        """
        Initialize the BLE client.
        
        Args:
            bluetooth_ble: Reference to the main BluetoothBle instance
            callback: Callback interface for client events
        """
        self.bluetooth_ble = bluetooth_ble
        self.callback = callback
        
        # Device connections
        self.device_clients: Dict[str, BleakClient] = {}
        
        # Message handling
        self.read_messages: Dict[str, List[bytes]] = {}
        self.write_messages: Dict[str, List[bytes]] = {}
        
        # Running state
        self._running = False
        
    async def start(self) -> None:
        """
        Start the BLE client service.
        """
        Logger.d("BluetoothBleClient", "Starting BLE client")
        self._running = True
        
    async def close(self) -> None:
        """
        Close all client connections and cleanup resources.
        """
        Logger.d("BluetoothBleClient", "Closing BLE client")
        self._running = False
        
        # Disconnect all clients
        for address, client in self.device_clients.items():
            try:
                if client.is_connected:
                    await client.disconnect()
                    Logger.d("BluetoothBleClient", f"{address} - Disconnected")
            except Exception as e:
                Logger.e("BluetoothBleClient", f"{address} - Error disconnecting: {e}")
        
        self.device_clients.clear()
        self.read_messages.clear()
        self.write_messages.clear()
    
    async def connect_to_device(self, device_address: str, device: Optional[BLEDevice] = None) -> bool:
        """
        Connect to a BLE device as a client.
        
        Args:
            device_address: MAC address of the device to connect to
            device: Optional BLEDevice object
            
        Returns:
            True if connection successful, False otherwise
        """
        if device_address in self.device_clients:
            return True
        
        try:
            Logger.d("BluetoothBleClient", f"{device_address} - Connecting to device")
            
            # Create client
            if device:
                client = BleakClient(device)
            else:
                client = BleakClient(device_address)
            
            # Set up disconnect callback
            client.set_disconnected_callback(lambda client: asyncio.create_task(
                self._handle_disconnection(device_address)
            ))
            
            # Connect
            await client.connect()
            
            if client.is_connected:
                Logger.d("BluetoothBleClient", f"{device_address} - Connected successfully")
                self.device_clients[device_address] = client
                
                # Discover services and characteristics
                await self._discover_services(device_address, client)
                
                return True
            else:
                Logger.e("BluetoothBleClient", f"{device_address} - Failed to connect")
                return False
                
        except Exception as e:
            Logger.e("BluetoothBleClient", f"{device_address} - Connection error: {e}")
            return False
    
    async def _discover_services(self, device_address: str, client: BleakClient) -> None:
        """
        Discover services and characteristics on the connected device.
        
        Args:
            device_address: MAC address of the device
            client: Connected BleakClient instance
        """
        try:
            Logger.d("BluetoothBleClient", f"{device_address} - Discovering services")
            
            # Get the target service
            service_uuid = str(self.bluetooth_ble.service_uuid)
            services = client.services
            
            target_service = None
            for service in services:
                if str(service.uuid).lower() == service_uuid.lower():
                    target_service = service
                    break
            
            if not target_service:
                Logger.e("BluetoothBleClient", f"{device_address} - Target service not found")
                return
            
            Logger.d("BluetoothBleClient", f"{device_address} - Found target service: {target_service.uuid}")
            
            # Discover characteristics
            read_char_uuid = str(self.bluetooth_ble.read_characteristic_uuid)
            write_char_uuid = str(self.bluetooth_ble.write_characteristic_uuid)
            
            for characteristic in target_service.characteristics:
                char_uuid = str(characteristic.uuid).lower()
                
                if char_uuid == read_char_uuid.lower():
                    Logger.d("BluetoothBleClient", f"{device_address} - Found READ characteristic")
                    await self.callback.on_characteristic_discovered(device_address, str(characteristic.uuid))
                    
                    # Enable notifications if supported
                    if "notify" in characteristic.properties:
                        await client.start_notify(characteristic.uuid, 
                                                lambda sender, data: asyncio.create_task(
                                                    self._handle_notification(device_address, data)
                                                ))
                        Logger.d("BluetoothBleClient", f"{device_address} - Notifications enabled")
                
                elif char_uuid == write_char_uuid.lower():
                    Logger.d("BluetoothBleClient", f"{device_address} - Found WRITE characteristic")
                    await self.callback.on_characteristic_discovered(device_address, str(characteristic.uuid))
            
            # Signal that descriptor write is complete (similar to Android callback)
            await self.callback.on_descriptor_write(device_address)
            
        except Exception as e:
            Logger.e("BluetoothBleClient", f"{device_address} - Service discovery error: {e}")
    
    async def _handle_disconnection(self, device_address: str) -> None:
        """
        Handle device disconnection.
        
        Args:
            device_address: MAC address of the disconnected device
        """
        Logger.d("BluetoothBleClient", f"{device_address} - Device disconnected")
        
        # Cleanup
        if device_address in self.device_clients:
            del self.device_clients[device_address]
        
        if device_address in self.read_messages:
            del self.read_messages[device_address]
        
        if device_address in self.write_messages:
            del self.write_messages[device_address]
        
        # Notify callback
        await self.callback.on_disconnection(device_address)
    
    async def _handle_notification(self, device_address: str, data: bytes) -> None:
        """
        Handle characteristic change notifications.
        
        Args:
            device_address: MAC address of the notifying device
            data: Notification data
        """
        Logger.d("BluetoothBleClient", f"{device_address} - Characteristic changed")
        await self.callback.on_characteristic_changed(device_address)
    
    async def read_characteristic(self, device_address: str) -> Optional[bytes]:
        """
        Read from the device's read characteristic.
        
        Args:
            device_address: MAC address of the device
            
        Returns:
            Read data or None if failed
        """
        client = self.device_clients.get(device_address)
        if not client or not client.is_connected:
            Logger.e("BluetoothBleClient", f"{device_address} - Client not connected")
            return None
        
        try:
            read_char_uuid = self.bluetooth_ble.read_characteristic_uuid
            data = await client.read_gatt_char(read_char_uuid)
            
            Logger.d("BluetoothBleClient", f"{device_address} - Read response received")
            
            # Process chunked message
            message = await self._process_read_message(device_address, data)
            if message:
                await self.callback.on_read_response(device_address, message)
            
            return data
            
        except Exception as e:
            Logger.e("BluetoothBleClient", f"{device_address} - Read error: {e}")
            return None
    
    async def write_message(self, device_address: str, message: bytes) -> bool:
        """
        Write a message to the device's write characteristic.
        
        Args:
            device_address: MAC address of the device
            message: Message bytes to write
            
        Returns:
            True if write successful, False otherwise
        """
        client = self.device_clients.get(device_address)
        if not client or not client.is_connected:
            Logger.e("BluetoothBleClient", f"{device_address} - Client not connected")
            return False
        
        try:
            Logger.d("BluetoothBleClient", f"{device_address} - Sending write message")
            
            # Split message into chunks
            chunks = Compression.split_in_chunks(message)
            self.write_messages[device_address] = chunks
            
            Logger.d("BluetoothBleClient", f"{device_address} - Split into {len(chunks)} chunks")
            
            # Send first chunk
            success = await self._send_next_write_chunk(device_address)
            return success
            
        except Exception as e:
            Logger.e("BluetoothBleClient", f"{device_address} - Write error: {e}")
            return False
    
    async def _send_next_write_chunk(self, device_address: str) -> bool:
        """
        Send the next chunk in the write message queue.
        
        Args:
            device_address: MAC address of the device
            
        Returns:
            True if more chunks to send, False if complete
        """
        chunks = self.write_messages.get(device_address, [])
        if not chunks:
            Logger.d("BluetoothBleClient", f"{device_address} - No more chunks to send")
            return False
        
        client = self.device_clients.get(device_address)
        if not client or not client.is_connected:
            return False
        
        try:
            # Get and send next chunk
            chunk = chunks.pop(0)
            write_char_uuid = self.bluetooth_ble.write_characteristic_uuid
            
            await client.write_gatt_char(write_char_uuid, chunk)
            
            Logger.d("BluetoothBleClient", f"{device_address} - Sent chunk, {len(chunks)} remaining")
            
            # Update chunks list
            if chunks:
                self.write_messages[device_address] = chunks
                # Continue sending next chunk
                await asyncio.sleep(0.01)  # Small delay between chunks
                return await self._send_next_write_chunk(device_address)
            else:
                # All chunks sent
                if device_address in self.write_messages:
                    del self.write_messages[device_address]
                await self.callback.on_write_success(device_address)
                return False
                
        except Exception as e:
            Logger.e("BluetoothBleClient", f"{device_address} - Chunk send error: {e}")
            return False
    
    async def _process_read_message(self, device_address: str, data: bytes) -> Optional[bytes]:
        """
        Process a read message, handling chunked data.
        
        Args:
            device_address: MAC address of the device
            data: Raw read data
            
        Returns:
            Complete message or None if still receiving chunks
        """
        if len(data) < 2:
            return data
        
        try:
            chunk_index = data[0]
            total_chunks = data[-1]
            
            Logger.d("BluetoothBleClient", f"{device_address} - Received chunk {chunk_index}")
            
            # Add chunk to collection
            if device_address not in self.read_messages:
                self.read_messages[device_address] = []
            
            self.read_messages[device_address].append(data)
            
            # Check if we have all chunks
            chunks = self.read_messages[device_address]
            if len(chunks) == total_chunks:
                Logger.d("BluetoothBleClient", f"{device_address} - All chunks received: {total_chunks}")
                
                # Join chunks and cleanup
                complete_message = Compression.join_chunks(chunks)
                del self.read_messages[device_address]
                
                return complete_message
            else:
                # Request next chunk
                await self.read_characteristic(device_address)
                return None
                
        except Exception as e:
            Logger.e("BluetoothBleClient", f"{device_address} - Chunk processing error: {e}")
            return data
