"""
Test script for the SQLite database implementation.
This can be used to verify that the database system works correctly.
"""

import asyncio
import os
import sys
import tempfile
import time

# Add parent directory to Python path for imports
sys.path.insert(0, os.path.join(os.path.dirname(__file__), '..'))

from database.sqlite_storage import SqliteStorage
from database.event_entity import EventEntity
from models.logger import Logger


async def test_database():
    """
    Test the database implementation with basic CRUD operations.
    """
    Logger.setup_logging()
    Logger.i("TestDatabase", "Starting database tests...")
    
    # Use temporary directory for testing
    with tempfile.TemporaryDirectory() as temp_dir:
        try:
            # Initialize storage
            storage = SqliteStorage(pub_key="test_key", base_path=temp_dir)
            await storage.initialize()
            
            # Test database info
            db_info = storage.get_database_info()
            Logger.i("TestDatabase", f"Database info: {db_info}")
            
            # Test inserting events
            Logger.i("TestDatabase", "Testing event insertion...")
            
            current_time = int(time.time())
            
            # Insert local event
            result1 = storage.insert_event("local_event_1", current_time, is_local=True)
            Logger.i("TestDatabase", f"Inserted local event: {result1}")
            
            # Insert external event
            result2 = storage.insert_event("external_event_1", current_time + 1, is_local=False)
            Logger.i("TestDatabase", f"Inserted external event: {result2}")
            
            # Insert duplicate (should be ignored)
            result3 = storage.insert_event("local_event_1", current_time, is_local=True)
            Logger.i("TestDatabase", f"Duplicate insertion result: {result3}")
            
            # Test event existence
            Logger.i("TestDatabase", "Testing event existence...")
            exists1 = storage.exists_event("local_event_1")
            exists2 = storage.exists_event("external_event_1")
            exists3 = storage.exists_event("non_existent_event")
            Logger.i("TestDatabase", f"local_event_1 exists: {exists1}")
            Logger.i("TestDatabase", f"external_event_1 exists: {exists2}")
            Logger.i("TestDatabase", f"non_existent_event exists: {exists3}")
            
            # Test getting events
            Logger.i("TestDatabase", "Testing event retrieval...")
            
            all_events = storage.get_all_events()
            Logger.i("TestDatabase", f"Total events: {len(all_events)}")
            for event in all_events:
                Logger.i("TestDatabase", f"Event: {event.event_id}, local: {event.local}")
            
            # Test counts
            Logger.i("TestDatabase", "Testing event counts...")
            total_count = storage.get_event_count()
            Logger.i("TestDatabase", f"Total event count: {total_count}")
            
            # Test deletion
            Logger.i("TestDatabase", "Testing event deletion...")
            deleted_count = storage.delete_all_events()
            Logger.i("TestDatabase", f"Deleted {deleted_count} events")
            
            # Verify deletion
            final_count = storage.get_event_count()
            Logger.i("TestDatabase", f"Final event count after deletion: {final_count}")
            
            # Cleanup
            await storage.cleanup()
            
            Logger.i("TestDatabase", "All database tests completed successfully!")
            
        except Exception as e:
            Logger.e("TestDatabase", f"Database test failed: {e}")
            raise


if __name__ == "__main__":
    asyncio.run(test_database())
