"""
Enums used in the Negentropy protocol
"""

from enum import IntEnum


class Mode(IntEnum):
    Skip = 0
    Fingerprint = 1
    IdList = 2
