"""
Vector-based storage implementation
"""

from typing import List, Union, Callable
from .storage_base import StorageBase
from .item import Item
from .bound import Bound
from .accumulator import Accumulator
from .constants import ID_SIZE
from .exceptions import NegentropyError
from .utils import load_input_buffer


class VectorStorage(StorageBase):
    """Vector-based storage implementation"""
    
    def __init__(self):
        self.items: List[Item] = []
        self.sealed = False
    
    def insert(self, timestamp: int, id_data: Union[str, bytes]):
        """Insert item into storage"""
        if self.sealed:
            raise NegentropyError("already sealed")
        
        id_bytes = load_input_buffer(id_data)
        if len(id_bytes) != ID_SIZE:
            raise NegentropyError("bad id size for added item")
        
        self.items.append(Item(timestamp, id_bytes))
    
    def seal(self):
        """Sort items and check for duplicates"""
        if self.sealed:
            raise NegentropyError("already sealed")
        
        self.sealed = True
        self.items.sort()
        
        # Check for duplicates
        for i in range(1, len(self.items)):
            if self.items[i-1] == self.items[i]:
                raise NegentropyError("duplicate item inserted")
    
    def unseal(self):
        """Unseal storage for modifications"""
        self.sealed = False
    
    def size(self) -> int:
        """Get number of items"""
        self._check_sealed()
        return len(self.items)
    
    def get_item(self, index: int) -> Item:
        """Get item at index"""
        self._check_sealed()
        if index >= len(self.items):
            raise NegentropyError("out of range")
        return self.items[index]
    
    def iterate(self, begin: int, end: int, callback: Callable[[Item, int], bool]):
        """Iterate over range [begin, end)"""
        self._check_sealed()
        self._check_bounds(begin, end)
        
        for i in range(begin, end):
            if not callback(self.items[i], i):
                break
    
    def find_lower_bound(self, begin: int, end: int, bound: Bound) -> int:
        """Find lower bound using binary search"""
        self._check_sealed()
        self._check_bounds(begin, end)
        
        # Binary search for lower bound
        left, right = begin, end
        while left < right:
            mid = (left + right) // 2
            if self.items[mid] < bound.item:
                left = mid + 1
            else:
                right = mid
        
        return left
    
    def fingerprint(self, begin: int, end: int) -> bytes:
        """Compute fingerprint for range"""
        accumulator = Accumulator()
        accumulator.set_to_zero()
        
        self.iterate(begin, end, lambda item, _: accumulator.add(item) or True)
        
        return accumulator.get_fingerprint(end - begin)
    
    def _check_sealed(self):
        """Check if storage is sealed"""
        if not self.sealed:
            raise NegentropyError("not sealed")
    
    def _check_bounds(self, begin: int, end: int):
        """Check if bounds are valid"""
        if begin > end or end > len(self.items):
            raise NegentropyError("bad range")
