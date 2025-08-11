"""
Accumulator class for the Negentropy protocol
"""

import hashlib
from typing import Union
from .constants import ID_SIZE, FINGERPRINT_SIZE
from .utils import encode_varint


class Accumulator:
    """Accumulator for computing fingerprints"""
    
    def __init__(self):
        self.buf = bytearray(ID_SIZE)
    
    def set_to_zero(self):
        """Reset accumulator to zero"""
        self.buf = bytearray(ID_SIZE)
    
    def add(self, other_buf: Union[bytes, bytearray, 'Item', 'Accumulator']):
        """Add another buffer/item/accumulator to this one"""
        # Import here to avoid circular imports
        from .item import Item
        
        if isinstance(other_buf, Item):
            other_buf = other_buf.id
        elif isinstance(other_buf, Accumulator):
            other_buf = other_buf.buf
        
        other_buf = bytes(other_buf)
        
        # Perform addition with carry in little-endian byte order
        carry = 0
        for i in range(ID_SIZE):
            sum_val = self.buf[i] + other_buf[i] + carry
            self.buf[i] = sum_val & 0xFF
            carry = sum_val >> 8
    
    def negate(self):
        """Two's complement negation"""
        # One's complement
        for i in range(ID_SIZE):
            self.buf[i] = (~self.buf[i]) & 0xFF
        
        # Add one
        carry = 1
        for i in range(ID_SIZE):
            sum_val = self.buf[i] + carry
            self.buf[i] = sum_val & 0xFF
            carry = sum_val >> 8
            if carry == 0:
                break
    
    def sub(self, other_buf: Union[bytes, bytearray, 'Item', 'Accumulator']):
        """Subtract another buffer/item/accumulator from this one"""
        # Import here to avoid circular imports
        from .item import Item
        
        if isinstance(other_buf, Item):
            other_buf = other_buf.id
        elif isinstance(other_buf, Accumulator):
            other_buf = bytes(other_buf.buf)
        
        # Create negated version and add it
        neg_acc = Accumulator()
        neg_acc.buf = bytearray(other_buf)
        neg_acc.negate()
        self.add(neg_acc.buf)
    
    def get_fingerprint(self, n: int) -> bytes:
        """Get fingerprint hash"""
        input_data = bytes(self.buf) + encode_varint(n)
        hash_result = hashlib.sha256(input_data).digest()
        return hash_result[:FINGERPRINT_SIZE]
