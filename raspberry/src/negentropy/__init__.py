"""
Negentropy Python Implementation

A Python implementation of the Negentropy protocol for efficient set reconciliation.
"""

from .constants import (
    PROTOCOL_VERSION,
    ID_SIZE,
    FINGERPRINT_SIZE,
    MAX_U64,
)

from .enums import Mode

from .exceptions import NegentropyError

from .utils import (
    hex_to_bytes,
    bytes_to_hex,
    encode_varint,
    decode_varint,
    load_input_buffer,
)

from .item import Item
from .bound import Bound
from .accumulator import Accumulator
from .storage_base import StorageBase
from .vector_storage import VectorStorage
from .negentropy_protocol import Negentropy

__version__ = "1.0.0"
__author__ = "Python port of Doug Hoyte's Negentropy"
__license__ = "MIT"

__all__ = [
    "Negentropy",
    "VectorStorage", 
    "Item",
    "Bound",
    "Accumulator",
    "StorageBase",
    "Mode",
    "NegentropyError",
    "PROTOCOL_VERSION",
    "ID_SIZE", 
    "FINGERPRINT_SIZE",
    "MAX_U64",
    "hex_to_bytes",
    "bytes_to_hex",
    "encode_varint",
    "decode_varint",
    "load_input_buffer",
]
