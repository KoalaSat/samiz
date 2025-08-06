"""
Utility functions for the Negentropy protocol
"""

from typing import Union
from .exceptions import NegentropyError


def get_byte(data: bytearray) -> int:
    """Get and consume one byte from data"""
    if len(data) < 1:
        raise NegentropyError("parse ends prematurely")
    return data.pop(0)


def get_bytes(data: bytearray, n: int) -> bytes:
    """Get and consume n bytes from data"""
    if len(data) < n:
        raise NegentropyError("parse ends prematurely")
    result = bytes(data[:n])
    del data[:n]
    return result


def decode_varint(data: bytearray) -> int:
    """Decode variable-length integer"""
    result = 0
    
    while True:
        if len(data) == 0:
            raise NegentropyError("premature end of varint")
        
        byte = data.pop(0)
        result = (result << 7) | (byte & 0x7F)
        
        if (byte & 0x80) == 0:
            break
    
    return result


def encode_varint(n: int) -> bytes:
    """Encode variable-length integer"""
    if n == 0:
        return b'\x00'
    
    output = []
    
    while n:
        output.append(n & 0x7F)
        n >>= 7
    
    output.reverse()
    
    for i in range(len(output) - 1):
        output[i] |= 0x80
    
    return bytes(output)


def hex_to_bytes(hex_str: str) -> bytes:
    """Convert hex string to bytes"""
    if hex_str.startswith('0x'):
        hex_str = hex_str[2:]
    if len(hex_str) % 2 == 1:
        raise NegentropyError("odd length of hex string")
    return bytes.fromhex(hex_str)


def bytes_to_hex(data: bytes) -> str:
    """Convert bytes to hex string"""
    return data.hex()


def load_input_buffer(inp: Union[str, bytes, bytearray]) -> bytes:
    """Load input as bytes, handling different input types"""
    if isinstance(inp, str):
        return hex_to_bytes(inp)
    elif isinstance(inp, (bytes, bytearray)):
        return bytes(inp)
    else:
        raise NegentropyError("unsupported input type")
