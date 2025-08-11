#!/usr/bin/env python3
"""
Main entry point for the Samiz Raspberry Pi Bluetooth service.
This orchestrator service manages BLE communication and synchronization.
"""

import asyncio
import logging
import sys
from pathlib import Path

# Add src directory to Python path
sys.path.insert(0, str(Path(__file__).parent / "src"))

from services.bluetooth_reconciliation import BluetoothReconciliation
from models.logger import Logger

async def main():
    """
    Main entry point function that starts the BluetoothReconciliation service.
    """
    try:
        Logger.setup_logging()
        Logger.info("main", "Starting Samiz Raspberry Pi Bluetooth Service")
        
        # Initialize and start the BluetoothReconciliation orchestrator
        reconciliation_service = BluetoothReconciliation()
        
        # Start the service
        await reconciliation_service.start()
        
        # Keep the service running
        Logger.info("main", "Service started successfully. Press Ctrl+C to stop.")
        
        # Wait indefinitely until interrupted
        try:
            while True:
                await asyncio.sleep(1)
        except KeyboardInterrupt:
            Logger.info("main", "Shutdown signal received")
            try:
                Logger.info("main", "Stopping service")
                await reconciliation_service.close()
                Logger.info("main", "Service stopped gracefully")
            except Exception as e:
                Logger.error("main", f"Error during cleanup: {e}")
        
    except Exception as e:
        Logger.error("main", f"Fatal error: {e}")
        sys.exit(1)

if __name__ == "__main__":
    asyncio.run(main())
