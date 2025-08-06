"""
BLE Scanner implementation for the Samiz Raspberry Pi service.
Handles device discovery and scanning using Bleak.
"""

import asyncio
from typing import Optional, TYPE_CHECKING

from bleak import BleakScanner

from models.logger import Logger
from .bluetooth_ble_callback import BluetoothBleScannerCallback

if TYPE_CHECKING:
    from .bluetooth_ble import BluetoothBle


class BluetoothBleScanner:
    """
    BLE Scanner implementation using Bleak.
    Handles device discovery and scanning.
    """
    
    def __init__(self, bluetooth_ble: 'BluetoothBle', callback: BluetoothBleScannerCallback):
        """
        Initialize the BLE scanner.
        
        Args:
            bluetooth_ble: Reference to the main BluetoothBle instance
            callback: Callback interface for scanner events
        """
        self.bluetooth_ble = bluetooth_ble
        self.callback = callback
        self._scanning = False
        self._scan_task: Optional[asyncio.Task] = None
    
    async def start_scanning(self) -> None:
        """
        Start BLE device scanning.
        """
        if self._scanning:
            Logger.d("BluetoothBleScanner", "Already scanning")
            return
        
        Logger.d("BluetoothBleScanner", "Starting BLE scan")
        self._scanning = True
        self._scan_task = asyncio.create_task(self._scan_loop())
    
    async def close(self) -> None:
        """
        Stop scanning and cleanup resources.
        """
        Logger.d("BluetoothBleScanner", "Stopping BLE scan")
        self._scanning = False
        
        if self._scan_task:
            self._scan_task.cancel()
            try:
                await self._scan_task
            except asyncio.CancelledError:
                pass
    
    async def _scan_loop(self) -> None:
        """
        Main scanning loop.
        """
        while self._scanning:
            try:
                # Scan for devices
                devices = await BleakScanner.discover(timeout=10.0, return_adv=True)
                
                for address, (device, advertisement_data) in devices.items():
                    if not self._scanning:
                        break
                    
                    # Look for our service UUID in advertisements
                    remote_uuid = None
                    if advertisement_data.service_uuids:
                        for service_uuid in advertisement_data.service_uuids:
                            if str(service_uuid).lower() == str(self.bluetooth_ble.service_uuid).lower():
                                remote_uuid = str(service_uuid)
                                break
                    
                    if remote_uuid:
                        Logger.d("BluetoothBleScanner", f"Found target device: {address}")
                        await self.callback.on_device_found(address, remote_uuid)
                
                # Wait before next scan cycle
                if self._scanning:
                    await asyncio.sleep(5.0)
                    
            except Exception as e:
                Logger.e("BluetoothBleScanner", f"Scan error: {e}")
                if self._scanning:
                    await asyncio.sleep(5.0)  # Wait before retrying
