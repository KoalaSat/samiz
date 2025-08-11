"""
nostr utility
"""

import json
from typing import Dict, Any

class Nostr:
    """
    nostr utility
    """
    
    @classmethod
    def create_neg_open_message(self, device_address: str, msg: str) -> bytes:
        """
        Create a negentropy open message.
        
        Args:
            device_address: MAC address of the device
            msg: Initial negentropy message string
            
        Returns:
            JSON bytes message
        """
        
        json_array = [
            "NEG-OPEN",  # type
            device_address.replace(":", ""),  # subscription ID
            "{}",  # nostr filters
            msg  # initial message as hex string
        ]
        
        return json.dumps(json_array).encode('utf-8')
    
    @classmethod
    def create_neg_message(self, device_address: str, msg: bytes) -> bytes:
        """
        Create a negentropy reconciliation message.
        
        Args:
            device_address: MAC address of the device
            msg: Reconciliation message bytes
            
        Returns:
            JSON bytes message
        """
        json_array = [
            "NEG-MSG",  # type
            device_address.replace(":", ""),  # subscription ID
            msg  # reconciliation message as hex string
        ]

        return json.dumps(json_array).encode('utf-8')
    
    @classmethod
    def create_event_message(self, device_address: str, event_json: str) -> bytes:
        """
        Create an event message.
        
        Args:
            device_address: MAC address of the device
            event_json: Event JSON string
            
        Returns:
            JSON bytes message
        """
        json_array = [
            "EVENT",  # type
            device_address.replace(":", ""),  # subscription ID
            event_json  # event data
        ]
        return json.dumps(json_array).encode('utf-8')
    
    @classmethod
    def create_subscription_message(self, device_address: str, filters: Dict[str, Any]) -> bytes:
        """
        Create a subscription request message.
        
        Args:
            device_address: MAC address of the device
            filters: Subscription filters
            
        Returns:
            JSON bytes message
        """
        json_array = [
            "REQ",  # type
            device_address.replace(":", ""),  # subscription ID
            json.dumps(filters)  # filters
        ]
        return json.dumps(json_array).encode('utf-8')
    
    @classmethod
    def create_end_message(self, device_address: str) -> bytes:
        """
        Create an end-of-stream message.
        
        Args:
            device_address: MAC address of the device
            
        Returns:
            JSON bytes message
        """
        json_array = [
            "EOSE",  # type
            device_address.replace(":", "")  # subscription ID
        ]
        return json.dumps(json_array).encode('utf-8')