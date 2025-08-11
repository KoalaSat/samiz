"""
Logger utility for the Samiz Raspberry Pi service.
Provides structured logging with different levels and component identification.
"""

import logging
import sys
from datetime import datetime
from typing import Optional

class Logger:
    """
    Centralized logging utility with component-based logging.
    Similar to the Android Logger.kt implementation.
    """
    
    _logger: Optional[logging.Logger] = None
    
    @classmethod
    def setup_logging(cls, level: int = logging.INFO) -> None:
        """
        Initialize the logging system.
        
        Args:
            level: Logging level (default: INFO)
        """
        if cls._logger is None:
            # Create logger
            cls._logger = logging.getLogger("samiz")
            cls._logger.setLevel(level)
            
            # Create console handler
            handler = logging.StreamHandler(sys.stdout)
            handler.setLevel(level)
            
            # Create formatter
            formatter = logging.Formatter(
                '%(asctime)s - %(levelname)s - %(message)s',
                datefmt='%Y-%m-%d %H:%M:%S'
            )
            handler.setFormatter(formatter)
            
            # Add handler to logger
            cls._logger.addHandler(handler)
    
    @classmethod
    def _log(cls, level: int, component: str, message: str) -> None:
        """
        Internal logging method.
        
        Args:
            level: Logging level
            component: Component name (e.g., "BluetoothBle", "BluetoothReconciliation")
            message: Log message
        """
        if cls._logger is None:
            cls.setup_logging()
        
        formatted_message = f"[{component}] {message}"
        cls._logger.log(level, formatted_message)
    
    @classmethod
    def d(cls, component: str, message: str) -> None:
        """
        Alias for debug method (matching Android Logger.d).
        
        Args:
            component: Component name
            message: Debug message
        """
        cls._log(logging.INFO, component, message)
    
    @classmethod
    def info(cls, component: str, message: str) -> None:
        """
        Log info message.
        
        Args:
            component: Component name
            message: Info message
        """
        cls._log(logging.INFO, component, message)
    
    @classmethod
    def i(cls, component: str, message: str) -> None:
        """
        Alias for info method (matching Android Logger.i).
        
        Args:
            component: Component name
            message: Info message
        """
        cls.info(component, message)
    
    @classmethod
    def warning(cls, component: str, message: str) -> None:
        """
        Log warning message.
        
        Args:
            component: Component name
            message: Warning message
        """
        cls._log(logging.WARNING, component, message)
    
    @classmethod
    def w(cls, component: str, message: str) -> None:
        """
        Alias for warning method (matching Android Logger.w).
        
        Args:
            component: Component name
            message: Warning message
        """
        cls.warning(component, message)
    
    @classmethod
    def error(cls, component: str, message: str) -> None:
        """
        Log error message.
        
        Args:
            component: Component name
            message: Error message
        """
        cls._log(logging.ERROR, component, message)
    
    @classmethod
    def e(cls, component: str, message: str) -> None:
        """
        Alias for error method (matching Android Logger.e).
        
        Args:
            component: Component name
            message: Error message
        """
        cls.error(component, message)
    
    @classmethod
    def critical(cls, component: str, message: str) -> None:
        """
        Log critical message.
        
        Args:
            component: Component name
            message: Critical message
        """
        cls._log(logging.CRITICAL, component, message)
    
    @classmethod
    def set_level(cls, level: int) -> None:
        """
        Set logging level.
        
        Args:
            level: New logging level
        """
        if cls._logger is None:
            cls.setup_logging()
        cls._logger.setLevel(level)
