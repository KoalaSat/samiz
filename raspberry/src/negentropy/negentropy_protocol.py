"""
Main Negentropy protocol class
"""

from typing import Optional, List, Tuple, Union
from .storage_base import StorageBase
from .item import Item
from .bound import Bound
from .enums import Mode
from .constants import PROTOCOL_VERSION, ID_SIZE, FINGERPRINT_SIZE, MAX_U64
from .exceptions import NegentropyError
from .utils import (
    get_byte, get_bytes, decode_varint, encode_varint, 
    bytes_to_hex, load_input_buffer
)


class Negentropy:
    """Main Negentropy protocol class"""
    
    def __init__(self, storage: StorageBase, frame_size_limit: int = 0):
        if frame_size_limit != 0 and frame_size_limit < 4096:
            raise NegentropyError("frameSizeLimit too small")
        
        self.storage = storage
        self.frame_size_limit = frame_size_limit
        self.is_initiator = False
        self.want_bytes_output = False
        
        self.last_timestamp_in = 0
        self.last_timestamp_out = 0
    
    def initiate(self) -> Union[bytes, str]:
        """Initiate protocol and return initial message"""
        if self.is_initiator:
            raise NegentropyError("already initiated")
        
        self.is_initiator = True
        
        output = bytearray([PROTOCOL_VERSION])
        output.extend(self._split_range(0, self.storage.size(), Bound(MAX_U64)))
        
        return self._render_output(output)
    
    def set_initiator(self):
        """Mark this instance as initiator"""
        self.is_initiator = True
    
    def reconcile(self, query: Union[str, bytes]) -> Union[
        Tuple[Optional[Union[bytes, str]], List[str], List[str]],  # initiator
        Union[bytes, str]  # non-initiator
    ]:
        """Reconcile with query message"""
        query_bytes = bytearray(load_input_buffer(query))
        
        self.last_timestamp_in = 0
        self.last_timestamp_out = 0
        
        full_output = bytearray([PROTOCOL_VERSION])
        
        # Check protocol version
        protocol_version = get_byte(query_bytes)
        if protocol_version < 0x60 or protocol_version > 0x6F:
            raise NegentropyError("invalid negentropy protocol version byte")
        
        if protocol_version != PROTOCOL_VERSION:
            if self.is_initiator:
                raise NegentropyError(f"unsupported negentropy protocol version requested: {protocol_version - 0x60}")
            else:
                return self._render_output(full_output)
        
        storage_size = self.storage.size()
        prev_bound = Bound(0)
        prev_index = 0
        skip = False
        
        have_ids = []
        need_ids = []
        
        while len(query_bytes) > 0:
            output_chunk = bytearray()
            
            def do_skip():
                nonlocal skip
                if skip:
                    skip = False
                    output_chunk.extend(self._encode_bound(prev_bound))
                    output_chunk.extend(encode_varint(Mode.Skip))
            
            curr_bound = self._decode_bound(query_bytes)
            mode = Mode(decode_varint(query_bytes))
            
            lower = prev_index
            upper = self.storage.find_lower_bound(prev_index, storage_size, curr_bound)
            
            if mode == Mode.Skip:
                skip = True
            elif mode == Mode.Fingerprint:
                their_fingerprint = get_bytes(query_bytes, FINGERPRINT_SIZE)
                our_fingerprint = self.storage.fingerprint(lower, upper)
                
                if their_fingerprint != our_fingerprint:
                    do_skip()
                    output_chunk.extend(self._split_range(lower, upper, curr_bound))
                else:
                    skip = True
            elif mode == Mode.IdList:
                num_ids = decode_varint(query_bytes)
                
                their_elements = set()
                for _ in range(num_ids):
                    elem = get_bytes(query_bytes, ID_SIZE)
                    if self.is_initiator:
                        their_elements.add(elem)
                
                if self.is_initiator:
                    skip = True
                    
                    # Process our items in this range
                    def process_our_item(item: Item, _) -> bool:
                        item_id = item.get_id()
                        if item_id not in their_elements:
                            # ID exists on our side, but not their side
                            have_ids.append(bytes_to_hex(item_id) if not self.want_bytes_output else item_id)
                        else:
                            # ID exists on both sides
                            their_elements.remove(item_id)
                        return True
                    
                    self.storage.iterate(lower, upper, process_our_item)
                    
                    # Remaining elements exist on their side but not ours
                    for elem in their_elements:
                        need_ids.append(bytes_to_hex(elem) if not self.want_bytes_output else elem)
                
                else:
                    do_skip()
                    
                    response_ids = bytearray()
                    num_response_ids = 0
                    end_bound = curr_bound
                    
                    def collect_response_id(item: Item, index: int) -> bool:
                        nonlocal num_response_ids, end_bound, upper
                        if self._exceeded_frame_size_limit(len(full_output) + len(response_ids)):
                            end_bound = Bound.from_item(item)
                            upper = index  # shrink upper for correct fingerprint
                            return False
                        
                        response_ids.extend(item.get_id())
                        num_response_ids += 1
                        return True
                    
                    self.storage.iterate(lower, upper, collect_response_id)
                    
                    output_chunk.extend(self._encode_bound(end_bound))
                    output_chunk.extend(encode_varint(Mode.IdList))
                    output_chunk.extend(encode_varint(num_response_ids))
                    output_chunk.extend(response_ids)
                    
                    full_output.extend(output_chunk)
                    output_chunk = bytearray()
            
            else:
                raise NegentropyError("unexpected mode")
            
            # Check frame size limit
            if self._exceeded_frame_size_limit(len(full_output) + len(output_chunk)):
                # Frame size limit exceeded: return fingerprint for remaining range
                remaining_fingerprint = self.storage.fingerprint(upper, storage_size)
                
                full_output.extend(self._encode_bound(Bound(MAX_U64)))
                full_output.extend(encode_varint(Mode.Fingerprint))
                full_output.extend(remaining_fingerprint)
                break
            else:
                full_output.extend(output_chunk)
            
            prev_index = upper
            prev_bound = curr_bound
        
        # Return results based on whether this is initiator
        if self.is_initiator:
            if len(full_output) == 1:  # Only version byte
                return None, have_ids, need_ids
            else:
                return self._render_output(full_output), have_ids, need_ids
        else:
            return self._render_output(full_output)
    
    def _split_range(self, lower: int, upper: int, upper_bound: Bound) -> bytes:
        """Split range into buckets with fingerprints or ID list"""
        output = bytearray()
        
        num_elems = upper - lower
        buckets = 16
        
        if num_elems < buckets * 2:
            # Small range: send as ID list
            output.extend(self._encode_bound(upper_bound))
            output.extend(encode_varint(Mode.IdList))
            output.extend(encode_varint(num_elems))
            
            self.storage.iterate(lower, upper, lambda item, _: output.extend(item.get_id()) or True)
        else:
            # Large range: split into buckets with fingerprints
            items_per_bucket = num_elems // buckets
            buckets_with_extra = num_elems % buckets
            curr = lower
            
            for i in range(buckets):
                bucket_size = items_per_bucket + (1 if i < buckets_with_extra else 0)
                our_fingerprint = self.storage.fingerprint(curr, curr + bucket_size)
                curr += bucket_size
                
                if curr == upper:
                    next_bound = upper_bound
                else:
                    # Get minimal bound between current and next item
                    prev_item = self.storage.get_item(curr - 1)
                    curr_item = self.storage.get_item(curr)
                    next_bound = self._get_minimal_bound(prev_item, curr_item)
                
                output.extend(self._encode_bound(next_bound))
                output.extend(encode_varint(Mode.Fingerprint))
                output.extend(our_fingerprint)
        
        return bytes(output)
    
    def _exceeded_frame_size_limit(self, size: int) -> bool:
        """Check if frame size limit is exceeded"""
        return self.frame_size_limit and size > self.frame_size_limit - 200
    
    def _decode_timestamp_in(self, data: bytearray) -> int:
        """Decode timestamp with delta compression"""
        timestamp = decode_varint(data)
        if timestamp == 0:
            timestamp = MAX_U64
        else:
            timestamp -= 1
        
        timestamp += self.last_timestamp_in
        if timestamp < self.last_timestamp_in:  # overflow
            timestamp = MAX_U64
        
        self.last_timestamp_in = timestamp
        return timestamp
    
    def _decode_bound(self, data: bytearray) -> Bound:
        """Decode bound from data"""
        timestamp = self._decode_timestamp_in(data)
        id_len = decode_varint(data)
        if id_len > ID_SIZE:
            raise NegentropyError("bound key too long")
        
        id_bytes = get_bytes(data, id_len)
        return Bound(timestamp, id_bytes)
    
    def _encode_timestamp_out(self, timestamp: int) -> bytes:
        """Encode timestamp with delta compression"""
        if timestamp == MAX_U64:
            self.last_timestamp_out = MAX_U64
            return encode_varint(0)
        
        temp = timestamp
        timestamp -= self.last_timestamp_out
        self.last_timestamp_out = temp
        return encode_varint(timestamp + 1)
    
    def _encode_bound(self, bound: Bound) -> bytes:
        """Encode bound to bytes"""
        output = bytearray()
        
        output.extend(self._encode_timestamp_out(bound.item.timestamp))
        output.extend(encode_varint(bound.id_len))
        output.extend(bound.item.get_id()[:bound.id_len])
        
        return bytes(output)
    
    def _get_minimal_bound(self, prev_item: Item, curr_item: Item) -> Bound:
        """Get minimal bound to distinguish between two items"""
        if curr_item.timestamp != prev_item.timestamp:
            return Bound(curr_item.timestamp)
        else:
            # Find shared prefix length
            shared_prefix_bytes = 0
            curr_id = curr_item.get_id()
            prev_id = prev_item.get_id()
            
            for i in range(ID_SIZE):
                if curr_id[i] != prev_id[i]:
                    break
                shared_prefix_bytes += 1
            
            return Bound(curr_item.timestamp, curr_id[:shared_prefix_bytes + 1])
    
    def _render_output(self, data: bytearray) -> Union[bytes, str]:
        """Render output as bytes or hex string"""
        if self.want_bytes_output:
            return bytes(data)
        else:
            return bytes_to_hex(data)
