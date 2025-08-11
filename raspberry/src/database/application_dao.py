"""
ApplicationDao - Data Access Object for database operations.
This mirrors the Kotlin ApplicationDao interface functionality.
"""

import sqlite3
from typing import List, Optional
from models.logger import Logger
from .event_entity import EventEntity


class ApplicationDao:
    """
    Data Access Object for event operations.
    This corresponds to the Kotlin ApplicationDao interface.
    """
    
    def __init__(self, db_connection: sqlite3.Connection):
        """
        Initialize the DAO with a database connection.
        
        Args:
            db_connection: SQLite database connection
        """
        self.db_connection = db_connection
        self.db_connection.row_factory = sqlite3.Row  # Enable column access by name
    
    def exists_event(self, event_id: str) -> bool:
        """
        Check if an event exists in the database.
        
        Args:
            event_id: The event ID to check
            
        Returns:
            True if event exists, False otherwise
        """
        try:
            cursor = self.db_connection.cursor()
            cursor.execute("SELECT EXISTS (SELECT 1 FROM event WHERE eventId = ?)", (event_id,))
            result = cursor.fetchone()[0]
            return bool(result)
        except Exception as e:
            Logger.e("ApplicationDao", f"Error checking if event exists: {e}")
            return False
    
    def get_events(self) -> List[EventEntity]:
        """
        Get all events grouped by eventId.
        
        Returns:
            List of EventEntity objects
        """
        try:
            cursor = self.db_connection.cursor()
            cursor.execute("SELECT * FROM event GROUP BY eventId")
            rows = cursor.fetchall()
            
            events = []
            for row in rows:
                event = EventEntity(
                    id=row['id'],
                    event_id=row['eventId'],
                    created_at=row['createdAt'],
                    local=row['local']
                )
                events.append(event)
            
            return events
        except Exception as e:
            Logger.e("ApplicationDao", f"Error getting events: {e}")
            return []
    
    def insert_event(self, event_entity: EventEntity) -> Optional[int]:
        """
        Insert an event into the database with IGNORE conflict strategy.
        
        Args:
            event_entity: The EventEntity to insert
            
        Returns:
            Row ID of inserted event or None if ignored due to conflict
        """
        try:
            cursor = self.db_connection.cursor()
            cursor.execute(
                "INSERT OR IGNORE INTO event (eventId, createdAt, local) VALUES (?, ?, ?)",
                (event_entity.event_id, event_entity.created_at, event_entity.local)
            )
            
            if cursor.rowcount > 0:
                row_id = cursor.lastrowid
                self.db_connection.commit()
                return row_id
            else:
                return None
                
        except Exception as e:
            Logger.e("ApplicationDao", f"Error inserting event: {e}")
            self.db_connection.rollback()
            return None
    
    def delete_all(self) -> int:
        """
        Delete all events from the database.
        
        Returns:
            Number of rows deleted
        """
        try:
            cursor = self.db_connection.cursor()
            cursor.execute("DELETE FROM event")
            deleted_count = cursor.rowcount
            self.db_connection.commit()
            
            Logger.d("ApplicationDao", f"Deleted {deleted_count} events from database")
            return deleted_count
            
        except Exception as e:
            Logger.e("ApplicationDao", f"Error deleting all events: {e}")
            self.db_connection.rollback()
            return 0
