"""
SqliteStorage - High-level storage service for database operations.
This provides a clean interface for database operations and manages the database lifecycle.
"""

import os
from typing import Optional, List
from models.logger import Logger
from .app_database import AppDatabase
from .application_dao import ApplicationDao
from .event_entity import EventEntity


class SqliteStorage:
    """
    High-level storage service that manages database operations.
    This provides a clean interface for the application to interact with the database.
    """
    
    def __init__(self, db_name: str = "default", base_path: str = "/app/data"):
        """
        Initialize the SQLite storage service.
        
        Args:
            db_name: Public key for database filename
            base_path: Base directory for database files
        """
        self.db_name = db_name
        self.base_path = base_path
        self.database: Optional[AppDatabase] = None
        self.dao: Optional[ApplicationDao] = None
        
        Logger.d("SqliteStorage", f"SqliteStorage initialized for db_name: {db_name}")
    
    async def initialize(self) -> None:
        """
        Initialize the database connection and create tables.
        """
        try:
            Logger.d("SqliteStorage", "Initializing database...")
            
            # Create database instance
            self.database = AppDatabase.get_database(self.db_name, self.base_path)
            
            # Open database connection
            self.database.open()
            
            # Get DAO instance
            self.dao = self.database.application_dao()
            
            # Log database info
            db_info = self.database.get_database_info()
            Logger.i("SqliteStorage", f"Database initialized: {db_info}")
            
        except Exception as e:
            Logger.e("SqliteStorage", f"Failed to initialize database: {e}")
            raise
    
    async def cleanup(self) -> None:
        """
        Cleanup database resources.
        """
        try:
            Logger.d("SqliteStorage", "Cleaning up database...")
            
            if self.database:
                self.database.close()
                self.database = None
                self.dao = None
            
            Logger.d("SqliteStorage", "Database cleanup completed")
            
        except Exception as e:
            Logger.e("SqliteStorage", f"Error during database cleanup: {e}")
    
    def exists_event(self, event_id: str) -> bool:
        """
        Check if an event exists in the database.
        
        Args:
            event_id: The event ID to check
            
        Returns:
            True if event exists, False otherwise
        """
        if not self.dao:
            Logger.w("SqliteStorage", "Database not initialized, cannot check event existence")
            return False
        
        return self.dao.exists_event(event_id)
    
    def get_all_events(self) -> List[EventEntity]:
        """
        Get all events from the database.
        
        Returns:
            List of all events
        """
        if not self.dao:
            Logger.w("SqliteStorage", "Database not initialized, returning empty list")
            return []
        
        return self.dao.get_events()
    
    def insert_event(self, event_id: str, created_at: int, is_local: bool = False) -> Optional[int]:
        """
        Insert a new event into the database.
        
        Args:
            event_id: The event ID
            created_at: Timestamp when event was created
            is_local: Whether the event is local (True) or external (False)
            
        Returns:
            Row ID of inserted event or None if ignored due to conflict
        """
        if not self.dao:
            Logger.w("SqliteStorage", "Database not initialized, cannot insert event")
            return None
        
        local_flag = 1 if is_local else 0
        event_entity = EventEntity(
            id=None,
            event_id=event_id,
            created_at=created_at,
            local=local_flag
        )
        
        return self.dao.insert_event(event_entity)
    
    def delete_all_events(self) -> int:
        """
        Delete all events from the database.
        
        Returns:
            Number of events deleted
        """
        if not self.dao:
            Logger.w("SqliteStorage", "Database not initialized, cannot delete events")
            return 0
        
        return self.dao.delete_all()
    
    def get_event_count(self) -> int:
        """
        Get the total number of events in the database.
        
        Returns:
            Number of events
        """
        events = self.get_all_events()
        return len(events)
    
    def get_database_info(self) -> dict:
        """
        Get database information for debugging.
        
        Returns:
            Dictionary with database information
        """
        if not self.database:
            return {"status": "not_initialized"}
        
        info = self.database.get_database_info()
        info.update({
            "db_name": self.db_name,
            "base_path": self.base_path
        })
        
        return info
