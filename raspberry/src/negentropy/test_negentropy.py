#!/usr/bin/env python3
"""
Simple test script for the Python Negentropy implementation.
Tests basic functionality and compatibility with the protocol.
"""

import hashlib
import random
from . import VectorStorage, Negentropy, NegentropyError

def create_test_id(n: int) -> bytes:
    """Create a deterministic 32-byte ID for testing"""
    return hashlib.sha256(f"test_item_{n}".encode()).digest()

def test_storage():
    """Test basic storage functionality"""
    print("Testing VectorStorage...")
    
    storage = VectorStorage()
    
    # Add some test items
    test_items = [(1000, create_test_id(1)), (2000, create_test_id(2)), (1500, create_test_id(3))]
    
    for timestamp, item_id in test_items:
        storage.insert(timestamp, item_id)
    
    print(f"  Inserted {len(test_items)} items")
    
    # Seal storage
    storage.seal()
    print(f"  Storage sealed, size: {storage.size()}")
    
    # Test iteration
    items = []
    storage.iterate(0, storage.size(), lambda item, idx: items.append((item.timestamp, item.get_id())) or True)
    
    # Should be sorted by timestamp then ID
    expected_order = [1000, 1500, 2000]
    actual_order = [item[0] for item in items]
    
    if actual_order == expected_order:
        print("  ✓ Items correctly sorted")
    else:
        print(f"  ✗ Items not sorted correctly: {actual_order}")
        return False
    
    # Test fingerprint
    fingerprint = storage.fingerprint(0, storage.size())
    print(f"  Fingerprint length: {len(fingerprint)} bytes")
    
    return True

def test_negentropy_basic():
    """Test basic Negentropy protocol functionality"""
    print("\nTesting Negentropy protocol...")
    
    # Create storage with some items
    storage = VectorStorage()
    for i in range(10):
        storage.insert(i * 1000, create_test_id(i))
    storage.seal()
    
    # Create Negentropy instance
    ne = Negentropy(storage)
    
    # Test initiate
    try:
        msg = ne.initiate()
        print(f"  ✓ Initiate successful, message length: {len(msg)} chars")
    except Exception as e:
        print(f"  ✗ Initiate failed: {e}")
        return False
    
    # Test double initiate should fail
    try:
        ne.initiate()
        print("  ✗ Double initiate should have failed")
        return False
    except NegentropyError:
        print("  ✓ Double initiate correctly failed")
    
    return True

def test_reconciliation():
    """Test reconciliation between two storages"""
    print("\nTesting reconciliation...")
    
    # Create two storages with different items
    storage1 = VectorStorage()
    storage2 = VectorStorage()
    
    # Storage 1 has items 0-9
    for i in range(10):
        storage1.insert(i * 1000, create_test_id(i))
    storage1.seal()
    
    # Storage 2 has items 5-14
    for i in range(5, 15):
        storage2.insert(i * 1000, create_test_id(i))
    storage2.seal()
    
    # Create Negentropy instances
    ne1 = Negentropy(storage1)  # client
    ne2 = Negentropy(storage2)  # server
    
    # Client initiates
    msg = ne1.initiate()
    print(f"  Initial message length: {len(msg)}")
    
    # Simple ping-pong test (normally would need multiple rounds)
    response = ne2.reconcile(msg)
    print(f"  Server response length: {len(response)}")
    
    # Client processes response
    result = ne1.reconcile(response)
    
    if isinstance(result, tuple):
        new_msg, have_ids, need_ids = result
        print(f"  Have IDs: {len(have_ids)}, Need IDs: {len(need_ids)}")
        
        # We expect:
        # have_ids: items 0-4 (storage1 has but storage2 doesn't)
        # need_ids: items 10-14 (storage2 has but storage1 doesn't)
        if len(have_ids) > 0 and len(need_ids) > 0:
            print("  ✓ Reconciliation found differences")
            return True
        else:
            print("  ✗ Reconciliation didn't find expected differences")
            return False
    else:
        print("  ✗ Expected tuple result from reconciliation")
        return False

def test_varint_encoding():
    """Test variable integer encoding/decoding"""
    print("\nTesting varint encoding...")
    
    from . import encode_varint, decode_varint
    
    test_values = [0, 1, 127, 128, 255, 256, 16383, 16384, 2097151, 2097152]
    
    for value in test_values:
        encoded = encode_varint(value)
        decoded = decode_varint(bytearray(encoded))
        
        if decoded == value:
            print(f"  ✓ {value} -> {len(encoded)} bytes -> {decoded}")
        else:
            print(f"  ✗ {value} -> {decoded} (mismatch)")
            return False
    
    return True

def test_hex_conversion():
    """Test hex string conversion functions"""
    print("\nTesting hex conversion...")
    
    from . import hex_to_bytes, bytes_to_hex
    
    test_data = b"Hello, Negentropy!"
    hex_str = bytes_to_hex(test_data)
    back_to_bytes = hex_to_bytes(hex_str)
    
    if back_to_bytes == test_data:
        print(f"  ✓ Hex conversion: {len(test_data)} bytes <-> {len(hex_str)} chars")
        return True
    else:
        print("  ✗ Hex conversion failed")
        return False

def main():
    """Run all tests"""
    print("=== Negentropy Python Implementation Tests ===\n")
    
    tests = [
        test_storage,
        test_negentropy_basic,
        test_reconciliation,
        test_varint_encoding,
        test_hex_conversion,
    ]
    
    passed = 0
    total = len(tests)
    
    for test in tests:
        try:
            if test():
                passed += 1
            else:
                print("  Test failed!")
        except Exception as e:
            print(f"  Test crashed: {e}")
    
    print(f"\n=== Results: {passed}/{total} tests passed ===")
    
    if passed == total:
        print("🎉 All tests passed!")
        return True
    else:
        print("❌ Some tests failed")
        return False

if __name__ == "__main__":
    success = main()
    exit(0 if success else 1)
