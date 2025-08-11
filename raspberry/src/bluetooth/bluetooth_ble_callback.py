"""
Bluetooth BLE callback interfaces for the Samiz Raspberry Pi service.
Defines the callback interfaces for BLE communication events.
"""

from abc import ABC, abstractmethod
from typing import Optional

class BluetoothBleCallback(ABC):
    """
    Main callback interface for BluetoothBle events.
    Similar to the Android BluetoothBleCallback interface.
    """
    
    @abstractmethod
    async def on_connection(self, bluetooth_ble, device_address: str) -> None:
        """
        Called when a BLE connection is established.
        
        Args:
            bluetooth_ble: Reference to the BluetoothBle instance
            device_address: MAC address of the connected device
        """
        pass
    
    @abstractmethod
    async def on_read_response(self, bluetooth_ble, device_address: str, message: bytes) -> None:
        """
        Called when a read response is received from a device.
        
        Args:
            bluetooth_ble: Reference to the BluetoothBle instance
            device_address: MAC address of the device
            message: Received message bytes
        """
        pass
    
    @abstractmethod
    async def on_read_request(self, bluetooth_ble, device_address: str) -> Optional[bytes]:
        """
        Called when a read request is received from a device.
        
        Args:
            bluetooth_ble: Reference to the BluetoothBle instance
            device_address: MAC address of the requesting device
            
        Returns:
            Optional bytes to send as response, or None if no response
        """
        pass
    
    @abstractmethod
    async def on_write_request(self, bluetooth_ble, device_address: str, message: bytes) -> None:
        """
        Called when a write request is received from a device.
        
        Args:
            bluetooth_ble: Reference to the BluetoothBle instance
            device_address: MAC address of the device
            message: Written message bytes
        """
        pass
    
    @abstractmethod
    async def on_write_success(self, bluetooth_ble, device_address: str) -> None:
        """
        Called when a write operation is successful.
        
        Args:
            bluetooth_ble: Reference to the BluetoothBle instance
            device_address: MAC address of the device
        """
        pass
    
    @abstractmethod
    async def on_characteristic_changed(self, bluetooth_ble, device_address: str) -> None:
        """
        Called when a characteristic change notification is received.
        
        Args:
            bluetooth_ble: Reference to the BluetoothBle instance
            device_address: MAC address of the device
        """
        pass


class BluetoothBleClientCallback(ABC):
    """
    Callback interface for BLE client events.
    Similar to the Android BluetoothBleClientCallback interface.
    """
    
    @abstractmethod
    async def on_disconnection(self, device_address: str) -> None:
        """
        Called when a client disconnects from a server.
        
        Args:
            device_address: MAC address of the disconnected device
        """
        pass
    
    @abstractmethod
    async def on_characteristic_discovered(self, device_address: str, characteristic_uuid: str) -> None:
        """
        Called when a characteristic is discovered on a server.
        
        Args:
            device_address: MAC address of the server device
            characteristic_uuid: UUID of the discovered characteristic
        """
        pass
    
    @abstractmethod
    async def on_descriptor_write(self, device_address: str) -> None:
        """
        Called when a descriptor write operation completes.
        
        Args:
            device_address: MAC address of the device
        """
        pass
    
    @abstractmethod
    async def on_read_response(self, device_address: str, message: bytes) -> None:
        """
        Called when a read response is received.
        
        Args:
            device_address: MAC address of the device
            message: Received message bytes
        """
        pass
    
    @abstractmethod
    async def on_write_success(self, device_address: str) -> None:
        """
        Called when a write operation is successful.
        
        Args:
            device_address: MAC address of the device
        """
        pass
    
    @abstractmethod
    async def on_characteristic_changed(self, device_address: str) -> None:
        """
        Called when a characteristic change notification is received.
        
        Args:
            device_address: MAC address of the device
        """
        pass


class BluetoothBleServerCallback(ABC):
    """
    Callback interface for BLE server events.
    Similar to the Android BluetoothBleServerCallback interface.
    """
    
    @abstractmethod
    async def on_disconnection(self, device_address: str) -> None:
        """
        Called when a client disconnects from the server.
        
        Args:
            device_address: MAC address of the disconnected device
        """
        pass
    
    @abstractmethod
    async def on_read_request(self, device_address: str, characteristic_uuid: str) -> Optional[bytes]:
        """
        Called when a read request is received from a client.
        
        Args:
            device_address: MAC address of the client device
            characteristic_uuid: UUID of the requested characteristic
            
        Returns:
            Optional bytes to send as response, or None if no response
        """
        pass
    
    @abstractmethod
    async def on_write_request(self, device_address: str, characteristic_uuid: str, message: bytes) -> None:
        """
        Called when a write request is received from a client.
        
        Args:
            device_address: MAC address of the client device
            characteristic_uuid: UUID of the written characteristic
            message: Written message bytes
        """
        pass


class BluetoothBleScannerCallback(ABC):
    """
    Callback interface for BLE scanner events.
    """
    
    @abstractmethod
    async def on_device_found(self, device_address: str, remote_uuid: Optional[str]) -> None:
        """
        Called when a BLE device is discovered during scanning.
        
        Args:
            device_address: MAC address of the discovered device
            remote_uuid: Optional UUID advertised by the device
        """
        pass
