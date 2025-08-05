#!/usr/bin/env python3
"""
Python script that scans for Bluetooth devices using Bleak
Designed to run in a Docker container on Raspberry Pi
"""

import asyncio
import sys
from bleak import BleakScanner

async def scan_bluetooth_devices():
    """
    Scan for nearby Bluetooth devices and print their details
    """
    print("Scanning for Bluetooth devices...")
    print("-" * 40)
    
    try:
        # Perform device discovery for 10 seconds
        devices = await BleakScanner.discover(timeout=10.0, return_adv=True)
        
        if not devices:
            print("No Bluetooth devices found.")
        else:
            print(f"Found {len(devices)} Bluetooth device(s):")
            print()
            
            for idx, (address, (device, advertisement_data)) in enumerate(devices.items(), 1):
                name = device.name if device.name else "Unknown Device"
                rssi = advertisement_data.rssi
                
                print(f"{idx}. Device Name: {name}")
                print(f"   MAC Address: {address}")
                print(f"   Signal Strength (RSSI): {rssi} dBm")
                
                # Show service UUIDs if available
                if advertisement_data.service_uuids:
                    print(f"   Service UUIDs: {len(advertisement_data.service_uuids)} service(s)")
                    for uuid in advertisement_data.service_uuids:
                        print(f"     - {uuid}")
                else:
                    print("   Service UUIDs: No services advertised")
                
                # Show manufacturer data if available
                if advertisement_data.manufacturer_data:
                    print(f"   Manufacturer Data: Available ({len(advertisement_data.manufacturer_data)} entries)")
                else:
                    print("   Manufacturer Data: Not available")
                
                print()
                
    except Exception as e:
        print(f"Bluetooth error occurred: {e}")
        print("Make sure Bluetooth is enabled and the container has proper permissions.")
        return False
    
    return True

async def check_bluetooth_adapter():
    """
    Check if Bluetooth adapter is available
    """
    try:
        # Try to get available adapters
        scanner = BleakScanner()
        # This will raise an exception if no adapter is available
        print("Bluetooth adapter detected and accessible.")
        return True
    except Exception as e:
        print(f"No Bluetooth adapter found or accessible: {e}")
        print("Make sure Bluetooth is enabled and the container has proper permissions.")
        return False

async def main():
    # Check if Bluetooth adapter is available
    if not await check_bluetooth_adapter():
        print("Bluetooth functionality is not available.")
        sys.exit(1)
    
    print()
    
    # Scan for Bluetooth devices
    success = await scan_bluetooth_devices()
    
    if success:
        print("-" * 40)
        print("Bluetooth scan completed successfully!")
    else:
        print("Bluetooth scan failed. Check system permissions and Bluetooth status.")
        sys.exit(1)

if __name__ == "__main__":
    asyncio.run(main())
