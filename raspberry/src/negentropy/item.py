"""
Item class for the Negentropy protocol
"""

from .constants import ID_SIZE
from .exceptions import NegentropyError


class Item:
    """Represents a timestamped item with ID"""
    
    def __init__(self, timestamp: int = 0, id_bytes: bytes = b''):
        self.timestamp = timestamp
        if len(id_bytes) == 0:
            self.id = b'\x00' * ID_SIZE
        elif len(id_bytes) != ID_SIZE:
            raise NegentropyError("bad id size for Item")
        else:
            self.id = id_bytes
    
    def get_id(self) -> bytes:
        return self.id
    
    def __eq__(self, other) -> bool:
        if not isinstance(other, Item):
            return False
        return self.timestamp == other.timestamp and self.id == other.id
    
    def __lt__(self, other) -> bool:
        if not isinstance(other, Item):
            return NotImplemented
        if self.timestamp != other.timestamp:
            return self.timestamp < other.timestamp
        return self.id < other.id
    
    def __le__(self, other) -> bool:
        if not isinstance(other, Item):
            return NotImplemented
        if self.timestamp != other.timestamp:
            return self.timestamp <= other.timestamp
        return self.id <= other.id
