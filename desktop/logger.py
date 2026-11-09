"""
MadGammon Comprehensive Logging System
Logs to both ~/.local/share/madgammon/logs/madgammon.log and console.
Catches all uncaught exceptions with full stack traces.
"""
import os
import sys
import logging
import traceback
from logging.handlers import RotatingFileHandler

_logger_initialized = False

def setup_logging():
    global _logger_initialized
    if _logger_initialized:
        return logging.getLogger("madgammon")

    logger = logging.getLogger("madgammon")
    logger.setLevel(logging.DEBUG)

    # Prevent duplicate handlers
    if logger.handlers:
        return logger

    fmt = logging.Formatter(
        "[%(asctime)s.%(msecs)03d] [%(levelname)s] [%(threadName)s] [%(name)s:%(funcName)s:%(lineno)d] %(message)s",
        datefmt="%Y-%m-%d %H:%M:%S"
    )

    # Console Handler
    ch = logging.StreamHandler(sys.stdout)
    ch.setLevel(logging.DEBUG)
    ch.setFormatter(fmt)
    logger.addHandler(ch)

    # Determine log directories
    user_log_dir = os.path.expanduser("~/.local/share/madgammon/logs")
    script_log_dir = os.path.join(os.path.dirname(os.path.abspath(__file__)), "logs")

    for log_dir in [user_log_dir, script_log_dir]:
        try:
            os.makedirs(log_dir, exist_ok=True)
            log_file = os.path.join(log_dir, "madgammon.log")
            fh = RotatingFileHandler(
                log_file,
                maxBytes=10 * 1024 * 1024,  # 10 MB
                backupCount=5,
                encoding="utf-8"
            )
            fh.setLevel(logging.DEBUG)
            fh.setFormatter(fmt)
            logger.addHandler(fh)
            logger.info("Logging initialized to file: %s", log_file)
        except Exception as e:
            sys.stderr.write(f"Warning: Failed to setup log file in {log_dir}: {e}\n")

    # Global uncaught exception hook
    def uncaught_exception_handler(exc_type, exc_value, exc_traceback):
        if issubclass(exc_type, KeyboardInterrupt):
            sys.__excepthook__(exc_type, exc_value, exc_traceback)
            return
        logger.critical(
            "UNCAUGHT EXCEPTION OCCURRED!\n%s",
            "".join(traceback.format_exception(exc_type, exc_value, exc_traceback))
        )
        sys.__excepthook__(exc_type, exc_value, exc_traceback)

    sys.excepthook = uncaught_exception_handler

    _logger_initialized = True
    return logger

def get_logger(name="madgammon"):
    setup_logging()
    return logging.getLogger(name)
