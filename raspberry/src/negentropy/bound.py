"""
Bound class for the Negentropy protocol
"""

from .constants import ID_SIZE
from .exceptions import NegentropyError
from .item import Item


class Bound:
    """Represents a boundary in the search space"""
    
    def __init__(self, timestamp: int = 0, id_bytes: bytes = b''):
        self.item = Item(timestamp)
        self.id_len = len(id_bytes)
        if self.id_len > ID_SIZE:
            raise NegentropyError("bad id size for Bound")
        if id_bytes:
            self.item.id = id_bytes + b'\x00' * (ID_SIZE - len(id_bytes))
    
    @classmethod
    def from_item(cls, item: Item):
        """Create bound from item"""
        bound = cls(item.timestamp)
        bound.item = item
        bound.id_len = ID_SIZE
        return bound
    
    def __eq__(self, other) -> bool:
        if not isinstance(other, Bound):
            return False
        return self.item == other.item
    
    def __lt__(self, other) -> bool:
        if not isinstance(other, Bound):
            return NotImplemented
        return self.item < other.item
