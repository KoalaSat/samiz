"""
AppDatabase - Main database class for SQLite operations.
This mirrors the Kotlin AppDatabase functionality using SQLite directly.
"""

import sqlite3
import os
from typing import Optional
from models.logger import Logger
from .application_dao import ApplicationDao


class AppDatabase:
    """
    Main database class that manages SQLite connection and provides DAO access.
    This corresponds to the Kotlin AppDatabase class.
    """
    
    # Database version (corresponds to Kotlin version = 10)
    DATABASE_VERSION = 10
    
    def __init__(self, db_path: str):
        """
        Initialize the database connection.
        
        Args:
            db_path: Path to the SQLite database file
        """
        self.db_path = db_path
        self.connection: Optional[sqlite3.Connection] = None
        self._application_dao: Optional[ApplicationDao] = None
        
    def __enter__(self):
        """Context manager entry."""
        self.open()
        return self
    
    def __exit__(self, exc_type, exc_val, exc_tb):
        """Context manager exit."""
        self.close()
    
    def open(self) -> None:
        """
        Open database connection and create tables if they don't exist.
        """
        try:
            # Create directory if it doesn't exist
            os.makedirs(os.path.dirname(self.db_path), exist_ok=True)
            
            self.connection = sqlite3.connect(self.db_path, check_same_thread=False)
            self.connection.row_factory = sqlite3.Row
            
            # Enable WAL mode for better concurrent access
            self.connection.execute("PRAGMA journal_mode=WAL")
            
            # Create tables
            self._create_tables()
            
            Logger.i("AppDatabase", f"Database opened successfully at {self.db_path}")
            
        except Exception as e:
            Logger.e("AppDatabase", f"Error opening database: {e}")
            raise
    
    def close(self) -> None:
        """
        Close database connection.
        """
        try:
            if self.connection:
                self.connection.close()
                self.connection = None
                self._application_dao = None
                Logger.d("AppDatabase", "Database connection closed")
        except Exception as e:
            Logger.e("AppDatabase", f"Error closing database: {e}")
    
    def application_dao(self) -> ApplicationDao:
        """
        Get the ApplicationDao instance.
        
        Returns:
            ApplicationDao instance for database operations
        """
        if not self.connection:
            raise RuntimeError("Database connection is not open")
        
        if not self._application_dao:
            self._application_dao = ApplicationDao(self.connection)
        
        return self._application_dao
    
    def _create_tables(self) -> None:
        """
        Create database tables if they don't exist.
        This mirrors the Kotlin Room database schema.
        """
        try:
            cursor = self.connection.cursor()
            
            # Create event table with the same structure as Kotlin
            cursor.execute("""
                CREATE TABLE IF NOT EXISTS event (
                    id INTEGER PRIMARY KEY AUTOINCREMENT,
                    eventId TEXT NOT NULL,
                    createdAt INTEGER NOT NULL,
                    local INTEGER NOT NULL
                )
            """)
            
            # Create unique index on eventId (corresponds to Kotlin Index annotation)
            cursor.execute("""
                CREATE UNIQUE INDEX IF NOT EXISTS notification_by_eventId 
                ON event(eventId)
            """)
            
            self.connection.commit()
            Logger.d("AppDatabase", "Database tables created successfully")
            
        except Exception as e:
            Logger.e("AppDatabase", f"Error creating tables: {e}")
            raise
    
    @staticmethod
    def get_database(pub_key: str, base_path: str = "/app/data") -> 'AppDatabase':
        """
        Get database instance for a specific public key.
        This corresponds to the Kotlin getDatabase companion function.
        
        Args:
            pub_key: Public key to create database-specific filename
            base_path: Base directory path for database files
            
        Returns:
            AppDatabase instance
        """
        # Create database filename similar to Kotlin: samiz_db_<pubKey>
        db_filename = f"samiz_db_{pub_key}.db"
        db_path = os.path.join(base_path, db_filename)
        
        return AppDatabase(db_path)
    
    def get_database_info(self) -> dict:
        """
        Get database information for debugging purposes.
        
        Returns:
            Dictionary with database information
        """
        if not self.connection:
            return {"status": "closed"}
        
        try:
            cursor = self.connection.cursor()
            
            # Get database version
            cursor.execute("PRAGMA user_version")
            version = cursor.fetchone()[0]
            
            # Get table count
            cursor.execute("SELECT COUNT(*) FROM sqlite_master WHERE type='table'")
            table_count = cursor.fetchone()[0]
            
            # Get event count
            cursor.execute("SELECT COUNT(*) FROM event")
            event_count = cursor.fetchone()[0]
            
            return {
                "status": "open",
                "path": self.db_path,
                "version": version,
                "table_count": table_count,
                "event_count": event_count
            }
            
        except Exception as e:
            Logger.e("AppDatabase", f"Error getting database info: {e}")
            return {"status": "error", "error": str(e)}
