"""
EventEntity model representing events in the SQLite database.
This mirrors the Kotlin EventEntity structure.
"""

from dataclasses import dataclass
from typing import Optional


@dataclass
class EventEntity:
    """
    Represents an event entity in the database.
    This corresponds to the Kotlin EventEntity class.
    
    Fields:
    - id: Auto-generated primary key
    - event_id: The actual event ID (unique)
    - created_at: Timestamp when event was created
    - local: Whether event is local (1) or external (0)
    """
    id: Optional[int]
    event_id: str
    created_at: int
    local: int

    def __post_init__(self):
        """Validate the local field to be 0 or 1."""
        if self.local not in [0, 1]:
            raise ValueError("local field must be 0 (external) or 1 (local)")

    @classmethod
    def create_local(cls, event_id: str, created_at: int) -> 'EventEntity':
        """Create a local event entity."""
        return cls(id=None, event_id=event_id, created_at=created_at, local=1)

    @classmethod
    def create_external(cls, event_id: str, created_at: int) -> 'EventEntity':
        """Create an external event entity."""
        return cls(id=None, event_id=event_id, created_at=created_at, local=0)

    @classmethod
    def from_row(cls, row: tuple) -> 'EventEntity':
        """Create EventEntity from database row tuple."""
        return cls(id=row[0], event_id=row[1], created_at=row[2], local=row[3])
