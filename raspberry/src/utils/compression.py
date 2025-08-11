"""
Compression utilities for the Samiz Raspberry Pi service.
Handles message chunking and compression exactly matching the Android implementation.
"""

import zlib
from typing import List

class Compression:
    """
    Utility class for message compression and chunking.
    Exactly matches the Android Compression.kt implementation.
    """
    
    # Chunk size matching Kotlin implementation
    CHUNK_SIZE = 500
    
    @classmethod
    def hex_string_to_byte_array(cls, hex_string: str) -> bytes:
        """
        Convert hex string to byte array.
        Matches Kotlin: hexStringToByteArray
        
        Args:
            hex_string: Hex string to convert
            
        Returns:
            Byte array
        """
        return bytes.fromhex(hex_string)
    
    @classmethod
    def byte_array_to_hex_string(cls, data: bytes) -> str:
        """
        Convert byte array to hex string.
        Matches Kotlin: byteArrayToHexString
        
        Args:
            data: Byte array to convert
            
        Returns:
            Hex string (lowercase)
        """
        return ''.join(f'{byte:02x}' for byte in data)
    
    @classmethod
    def split_in_chunks(cls, message: bytes) -> List[bytes]:
        """
        Split message into chunks for BLE transmission.
        Exactly matches Kotlin: splitInChunks
        
        Process:
        1. Compress the message first
        2. Split compressed data into chunks of CHUNK_SIZE
        3. Each chunk has: [chunk_index] + [data] + [total_chunks]
        
        Args:
            message: Original message to split
            
        Returns:
            List of data chunks
        """
        # First compress the message (matches Kotlin behavior)
        compressed_data = cls._compress_byte_array(message)
        
        # Calculate number of chunks needed
        num_chunks = (len(compressed_data) + cls.CHUNK_SIZE - 1) // cls.CHUNK_SIZE
        
        chunks = []
        chunk_index = 0
        
        for i in range(num_chunks):
            start = i * cls.CHUNK_SIZE
            end = min((i + 1) * cls.CHUNK_SIZE, len(compressed_data))
            chunk_data = compressed_data[start:end]
            
            # Create chunk with index at first byte and total chunks at last byte
            chunk_with_index = bytearray(len(chunk_data) + 2)
            chunk_with_index[0] = chunk_index  # chunk index
            chunk_with_index[1:-1] = chunk_data  # data in the middle
            chunk_with_index[-1] = num_chunks  # total chunks at the end
            
            chunks.append(bytes(chunk_with_index))
            chunk_index += 1
        
        return chunks
    
    @classmethod
    def join_chunks(cls, chunks: List[bytes]) -> bytes:
        """
        Join chunks back into original message.
        Exactly matches Kotlin: joinChunks
        
        Process:
        1. Sort chunks by index (first byte)
        2. Extract data from each chunk (remove first and last byte)
        3. Concatenate all chunk data
        4. Decompress the result
        
        Args:
            chunks: List of data chunks
            
        Returns:
            Original decompressed message
        """
        # Sort chunks by their index (first byte)
        sorted_chunks = sorted(chunks, key=lambda chunk: chunk[0])
        
        # Reassemble the compressed data
        reassembled_data = bytearray()
        for chunk in sorted_chunks:
            # Extract data (remove first byte index and last byte total count)
            chunk_data = chunk[1:-1]
            reassembled_data.extend(chunk_data)
        
        # Decompress the reassembled data
        return cls._decompress_byte_array(bytes(reassembled_data))
    
    @classmethod
    def _compress_byte_array(cls, data: bytes) -> bytes:
        """
        Compress byte array using deflate compression.
        Matches Kotlin: compressByteArray (private)
        
        Args:
            data: Data to compress
            
        Returns:
            Compressed data
        """
        if not data:
            return data
        
        # Use zlib.compress which uses deflate algorithm (same as Java's DeflaterOutputStream)
        return zlib.compress(data)
    
    @classmethod
    def _decompress_byte_array(cls, data: bytes) -> bytes:
        """
        Decompress byte array using deflate decompression.
        Matches Kotlin: decompressByteArray (private)
        
        Args:
            data: Compressed data
            
        Returns:
            Decompressed data
        """
        if not data:
            return data
        
        # Use zlib.decompress which uses inflate algorithm (same as Java's InflaterInputStream)
        return zlib.decompress(data)
