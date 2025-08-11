"""
Abstract base class for storage implementations
"""

from abc import ABC, abstractmethod
from typing import Callable
from .item import Item
from .bound import Bound


class StorageBase(ABC):
    """Abstract base class for storage implementations"""
    
    @abstractmethod
    def size(self) -> int:
        """Get number of items in storage"""
        pass
    
    @abstractmethod
    def get_item(self, index: int) -> Item:
        """Get item at index"""
        pass
    
    @abstractmethod
    def iterate(self, begin: int, end: int, callback: Callable[[Item, int], bool]):
        """Iterate over items in range [begin, end)"""
        pass
    
    @abstractmethod
    def find_lower_bound(self, begin: int, end: int, bound: Bound) -> int:
        """Find lower bound for given bound in range"""
        pass
    
    @abstractmethod
    def fingerprint(self, begin: int, end: int) -> bytes:
        """Get fingerprint for range [begin, end)"""
        pass
