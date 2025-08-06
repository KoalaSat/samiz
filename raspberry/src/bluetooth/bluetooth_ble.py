"""
Main Bluetooth BLE service for the Samiz Raspberry Pi service.
Orchestrates BLE client, server, scanner, and advertiser components.
"""

import asyncio
import uuid
from typing import Dict, Optional
from bleak import BleakScanner
from bleak.backends.device import BLEDevice

from models.logger import Logger
from .bluetooth_ble_callback import BluetoothBleCallback, BluetoothBleClientCallback, BluetoothBleScannerCallback
from .bluetooth_ble_client import BluetoothBleClient
from .bluetooth_ble_scanner import BluetoothBleScanner


class BluetoothBle:
    """
    Main BLE service that coordinates all BLE operations.
    Similar to the Android BluetoothBle.kt implementation.
    """
    
    def __init__(self, callback: BluetoothBleCallback):
        """
        Initialize the BLE service.
        
        Args:
            callback: Main callback interface for BLE events
        """
        self.callback = callback
        
        # BLE UUIDs (matching Android implementation)
        self.service_uuid = uuid.UUID("0000180f-0000-1000-8000-00805f9b34fb")
        self.descriptor_uuid = uuid.UUID("00002902-0000-1000-8000-00805f9b34fb")
        self.read_characteristic_uuid = uuid.UUID("12345678-0000-1000-8000-00805f9b34fb")
        self.write_characteristic_uuid = uuid.UUID("87654321-0000-1000-8000-00805f9b34fb")
        
        # Settings
        self.advertiser_uuid_pref = "advertiser_uuid"
        self.mtu_size = 512
        
        # Device tracking
        self.clients: Dict[str, str] = {}  # address -> address (simplified from Android)
        self.servers: Dict[str, str] = {}  # address -> address
        self.device_read_characteristics: Dict[str, str] = {}
        self.device_write_characteristics: Dict[str, str] = {}
        
        # Components
        self.bluetooth_ble_client: Optional[BluetoothBleClient] = None
        self.bluetooth_ble_scanner: Optional[BluetoothBleScanner] = None
        # TODO: Add server and advertiser components
        
        # Running state
        self._running = False
        self._device_uuid: Optional[uuid.UUID] = None
    
    async def start(self) -> None:
        """
        Start the BLE service and all its components.
        """
        try:
            Logger.d("BluetoothBle", "Starting BLE service")
            
            # Check if Bluetooth is available
            if not await self._check_bluetooth_available():
                Logger.e("BluetoothBle", "Bluetooth not available")
                return
            
            Logger.d("BluetoothBle", "Bluetooth is available and enabled")
            
            # Initialize components
            await self._initialize_components()
            
            # Start scanner first
            if self.bluetooth_ble_scanner:
                await self.bluetooth_ble_scanner.start_scanning()
            
            self._running = True
            Logger.d("BluetoothBle", "BLE service started successfully")
            
        except Exception as e:
            Logger.e("BluetoothBle", f"Failed to start BLE service: {e}")
            raise
    
    async def close(self) -> None:
        """
        Close the BLE service and cleanup all resources.
        """
        Logger.d("BluetoothBle", "Closing BLE service")
        self._running = False
        
        try:
            # Close all components
            if self.bluetooth_ble_client:
                await self.bluetooth_ble_client.close()
            
            if self.bluetooth_ble_scanner:
                await self.bluetooth_ble_scanner.close()
            
            # Clear device tracking
            self.clients.clear()
            self.servers.clear()
            self.device_read_characteristics.clear()
            self.device_write_characteristics.clear()
            
            Logger.d("BluetoothBle", "BLE service closed")
            
        except Exception as e:
            Logger.e("BluetoothBle", f"Error closing BLE service: {e}")
    
    async def _check_bluetooth_available(self) -> bool:
        """
        Check if Bluetooth is available and enabled.
        
        Returns:
            True if Bluetooth is available, False otherwise
        """
        try:
            # Try to discover devices briefly to test Bluetooth availability
            devices = await BleakScanner.discover(timeout=1.0)
            return True
        except Exception as e:
            Logger.e("BluetoothBle", f"Bluetooth not available: {e}")
            return False
    
    async def _initialize_components(self) -> None:
        """
        Initialize all BLE components.
        """
        # Initialize client
        client_callback = BluetoothBleClientCallbackImpl(self)
        self.bluetooth_ble_client = BluetoothBleClient(self, client_callback)
        await self.bluetooth_ble_client.start()
        
        # Initialize scanner
        scanner_callback = BluetoothBleScannerCallbackImpl(self)
        self.bluetooth_ble_scanner = BluetoothBleScanner(self, scanner_callback)
        
        # TODO: Initialize server and advertiser components
    
    async def read_message(self, device_address: str) -> bool:
        """
        Read a message from the specified device.
        
        Args:
            device_address: MAC address of the device
            
        Returns:
            True if read request sent successfully, False otherwise
        """
        if not self.bluetooth_ble_client:
            Logger.e("BluetoothBle", f"{device_address} - Client not initialized")
            return False
        
        if device_address not in self.device_read_characteristics:
            Logger.e("BluetoothBle", f"{device_address} - Read characteristic not found")
            return False
        
        Logger.d("BluetoothBle", f"{device_address} - Sending read message")
        result = await self.bluetooth_ble_client.read_characteristic(device_address)
        return result is not None
    
    async def write_message(self, device_address: str, message: bytes) -> bool:
        """
        Write a message to the specified device.
        
        Args:
            device_address: MAC address of the device
            message: Message bytes to send
            
        Returns:
            True if write request sent successfully, False otherwise
        """
        if not self.bluetooth_ble_client:
            Logger.e("BluetoothBle", f"{device_address} - Client not initialized")
            return False
        
        if device_address not in self.device_write_characteristics:
            Logger.e("BluetoothBle", f"{device_address} - Write characteristic not found")
            return False
        
        return await self.bluetooth_ble_client.write_message(device_address, message)
    
    async def connect_to_device(self, device_address: str, remote_uuid: Optional[str] = None) -> None:
        """
        Handle device connection logic.
        
        Args:
            device_address: MAC address of the device
            remote_uuid: Optional UUID advertised by the device
        """
        Logger.d("BluetoothBle", f"{device_address} - Handling device role")
        
        if device_address in self.clients or device_address in self.servers:
            return
        
        # Determine role based on UUID comparison (similar to Android logic)
        device_uuid = self.get_device_uuid()
        
        if remote_uuid is None or uuid.UUID(remote_uuid) > device_uuid:
            Logger.d("BluetoothBle", f"{device_address} - I AM CLIENT")
            if self.bluetooth_ble_client:
                await self.bluetooth_ble_client.connect_to_device(device_address)
        else:
            Logger.d("BluetoothBle", f"{device_address} - I AM SERVER")
            # TODO: Handle server connection when server component is implemented
    
    def get_device_uuid(self) -> uuid.UUID:
        """
        Get or generate a unique UUID for this device.
        
        Returns:
            Device UUID
        """
        if self._device_uuid is None:
            # In a real implementation, this would be stored persistently
            # For now, generate a random UUID each time
            # self._device_uuid = uuid.uuid4()
            self._device_uuid = uuid.UUID('00000000-0000-0000-0000-000000000000') # TODO
            Logger.d("BluetoothBle", f"Generated device UUID: {self._device_uuid}")
        
        return self._device_uuid
    
    async def notify_client(self, device_address: str) -> bool:
        """
        Notify a client about characteristic changes.
        
        Args:
            device_address: MAC address of the client device
            
        Returns:
            True if notification sent successfully, False otherwise
        """
        # TODO: Implement when server component is available
        Logger.d("BluetoothBle", f"{device_address} - Notify client (not implemented yet)")
        return False


class BluetoothBleClientCallbackImpl(BluetoothBleClientCallback):
    """
    Implementation of client callback that bridges to main BLE callback.
    """
    
    def __init__(self, bluetooth_ble: BluetoothBle):
        self.bluetooth_ble = bluetooth_ble
    
    async def on_disconnection(self, device_address: str) -> None:
        Logger.d("BluetoothBle", f"{device_address} - Disconnecting from server")
        self.bluetooth_ble.servers.pop(device_address, None)
        self.bluetooth_ble.device_read_characteristics.pop(device_address, None)
        self.bluetooth_ble.device_write_characteristics.pop(device_address, None)
        
        # Attempt to reconnect
        await self.bluetooth_ble.connect_to_device(device_address)
    
    async def on_characteristic_discovered(self, device_address: str, characteristic_uuid: str) -> None:
        Logger.d("BluetoothBle", f"{device_address} - Characteristic discovered")
        self.bluetooth_ble.servers[device_address] = device_address
        
        if characteristic_uuid.lower() == str(self.bluetooth_ble.read_characteristic_uuid).lower():
            self.bluetooth_ble.device_read_characteristics[device_address] = characteristic_uuid
            Logger.d("BluetoothBle", f"{device_address} - READ Characteristic discovered")
        elif characteristic_uuid.lower() == str(self.bluetooth_ble.write_characteristic_uuid).lower():
            self.bluetooth_ble.device_write_characteristics[device_address] = characteristic_uuid
            Logger.d("BluetoothBle", f"{device_address} - WRITE Characteristic discovered")
    
    async def on_descriptor_write(self, device_address: str) -> None:
        read_char = self.bluetooth_ble.device_read_characteristics.get(device_address)
        write_char = self.bluetooth_ble.device_write_characteristics.get(device_address)
        
        if read_char and write_char:
            self.bluetooth_ble.servers[device_address] = device_address
            await self.bluetooth_ble.callback.on_connection(self.bluetooth_ble, device_address)
        else:
            Logger.e("BluetoothBle", f"{device_address} - Missing characteristics")
    
    async def on_read_response(self, device_address: str, message: bytes) -> None:
        self.bluetooth_ble.servers[device_address] = device_address
        await self.bluetooth_ble.callback.on_read_response(self.bluetooth_ble, device_address, message)
    
    async def on_write_success(self, device_address: str) -> None:
        self.bluetooth_ble.servers[device_address] = device_address
        await self.bluetooth_ble.callback.on_write_success(self.bluetooth_ble, device_address)
    
    async def on_characteristic_changed(self, device_address: str) -> None:
        await self.bluetooth_ble.callback.on_characteristic_changed(self.bluetooth_ble, device_address)


class BluetoothBleScannerCallbackImpl(BluetoothBleScannerCallback):
    """
    Implementation of scanner callback that bridges to main BLE service.
    """
    
    def __init__(self, bluetooth_ble: BluetoothBle):
        self.bluetooth_ble = bluetooth_ble
    
    async def on_device_found(self, device_address: str, remote_uuid: Optional[str]) -> None:
        await self.bluetooth_ble.connect_to_device(device_address, remote_uuid)
