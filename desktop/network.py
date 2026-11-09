import socket
import threading
import json
import time
import secrets
from typing import Callable, Optional, Dict
from logger import get_logger

try:
    from transport_mesh import MultiPathTransportRacer, LanPeerDiscovery
    from crypto_vault import DoubleRatchetSession, HybridKeyExchange, pad_to_16kb, unpad_from_16kb
except ImportError:
    MultiPathTransportRacer = None
    LanPeerDiscovery = None
    DoubleRatchetSession = None

log = get_logger("madgammon.network")

def get_local_lan_ip() -> str:
    """Detects primary LAN IPv4 address of this machine."""
    s = socket.socket(socket.AF_INET, socket.SOCK_DGRAM)
    try:
        # Does not actually send data over network
        s.connect(("8.8.8.8", 80))
        ip = s.getsockname()[0]
        log.debug("Detected LAN IP: %s", ip)
    except Exception as e:
        log.warning("Could not detect LAN IP (%s), falling back to 127.0.0.1", e)
        ip = "127.0.0.1"
    finally:
        s.close()
    return ip

class P2PNetworkManager:
    """
    Direct TCP P2P transport compatible with MadGammon Android TorP2PManager.
    """
    def __init__(self, on_message: Callable[[str, str], None], on_status: Callable[[str], None]):
        self.on_message = on_message
        self.on_status = on_status
        self.server_socket: Optional[socket.socket] = None
        self.active_socket: Optional[socket.socket] = None
        self.is_running = False
        self.is_connected = False
        self.peer_address_str = ""
        self.listen_thread: Optional[threading.Thread] = None
        self.rx_thread: Optional[threading.Thread] = None

    def start_host(self, port: int = 8080):
        log.info("Starting P2P Host on port %d...", port)
        self.stop()
        self.is_running = True
        try:
            self.server_socket = socket.socket(socket.AF_INET, socket.SOCK_STREAM)
            self.server_socket.setsockopt(socket.SOL_SOCKET, socket.SO_REUSEADDR, 1)
            self.server_socket.bind(("0.0.0.0", port))
            self.server_socket.listen(1)

            lan_ip = get_local_lan_ip()
            status_msg = f"HOST_LISTENING:{lan_ip}:{port}"
            log.info("Host listening on %s:%d", lan_ip, port)
            self.on_status(status_msg)

            self.listen_thread = threading.Thread(target=self._accept_loop, daemon=True, name="P2P-Accept")
            self.listen_thread.start()
        except Exception as e:
            err = f"ERROR: Failed to start host: {e}"
            log.exception(err)
            self.on_status(err)

    def _accept_loop(self):
        log.debug("Entering _accept_loop")
        while self.is_running and self.server_socket:
            try:
                sock, addr = self.server_socket.accept()
                self.active_socket = sock
                self.peer_address_str = f"{addr[0]}:{addr[1]}"
                self.is_connected = True
                log.info("Accepted connection from peer: %s", self.peer_address_str)
                self.on_status(f"CONNECTED:{self.peer_address_str}")

                self.rx_thread = threading.Thread(target=self._receive_loop, daemon=True, name="P2P-Rx")
                self.rx_thread.start()
                break
            except Exception as e:
                if self.is_running:
                    log.warning("Accept loop error: %s", e)
                break

    def connect_to_peer(self, host: str, port: int = 8080):
        log.info("Initiating connection to peer at %s:%d...", host, port)
        self.stop()
        self.is_running = True
        self.on_status(f"CONNECTING:{host}:{port}")

        def _connect():
            try:
                sock = socket.socket(socket.AF_INET, socket.SOCK_STREAM)
                sock.settimeout(6.0)
                log.debug("Connecting socket to %s:%d (timeout=6s)...", host, port)
                sock.connect((host, port))
                sock.settimeout(None)

                self.active_socket = sock
                self.peer_address_str = f"{host}:{port}"
                self.is_connected = True
                log.info("Successfully connected to peer: %s", self.peer_address_str)
                self.on_status(f"CONNECTED:{self.peer_address_str}")

                self.rx_thread = threading.Thread(target=self._receive_loop, daemon=True, name="P2P-Rx")
                self.rx_thread.start()
            except Exception as e:
                self.is_connected = False
                err = f"DISCONNECTED: Connection failed: {e}"
                log.error("Failed to connect to %s:%d: %s", host, port, e)
                self.on_status(err)

        threading.Thread(target=_connect, daemon=True, name="P2P-Connect").start()

    def _receive_loop(self):
        log.debug("Starting _receive_loop for peer: %s", self.peer_address_str)
        buf = ""
        while self.is_running and self.active_socket:
            try:
                data = self.active_socket.recv(4096)
                if not data:
                    log.info("Peer closed connection (0 bytes read)")
                    break
                decoded = data.decode("utf-8", errors="replace")
                buf += decoded
                while "\n" in buf:
                    line, buf = buf.split("\n", 1)
                    line = line.strip()
                    if line:
                        log.debug("Rx line: %s", line)
                        self._dispatch_line(line)
            except Exception as e:
                log.warning("Error in receive loop: %s", e)
                break

        self.is_connected = False
        log.info("Rx loop terminated. Disconnected.")
        self.on_status("DISCONNECTED")

    def _dispatch_line(self, line: str):
        # 1. Try parsing JSON GameTurnPacket
        try:
            obj = json.loads(line)
            if "action" in obj:
                action = obj.get("action", "")
                payload = obj.get("payload", "")
                log.debug("Parsed JSON packet: action=%s, payload=%s", action, payload)
                self.on_message(action, payload)
                return
        except Exception:
            pass

        # 2. Try parsing line commands (ACTION: payload)
        if ":" in line:
            action, payload = line.split(":", 1)
            action_clean = action.strip()
            payload_clean = payload.strip()
            log.debug("Parsed line packet: action=%s, payload=%s", action_clean, payload_clean)
            self.on_message(action_clean, payload_clean)
        else:
            log.debug("Parsed raw packet: %s", line)
            self.on_message("RAW", line)

    def send_packet(self, action: str, payload: str, sender_color: str = "WHITE") -> bool:
        if not self.active_socket or not self.is_connected:
            log.warning("Cannot send packet (%s): socket not connected", action)
            return False
        try:
            line_cmd = f"{action}: {payload}\n"
            log.debug("Tx packet: %s", line_cmd.strip())
            self.active_socket.sendall(line_cmd.encode("utf-8"))
            return True
        except Exception as e:
            log.error("Failed to send packet (%s): %s", action, e)
            self.is_connected = False
            self.on_status("DISCONNECTED")
            return False

    def stop(self):
        log.debug("Stopping P2PNetworkManager...")
        self.is_running = False
        self.is_connected = False
        if self.active_socket:
            try:
                self.active_socket.close()
            except Exception as e:
                log.debug("Error closing active_socket: %s", e)
            self.active_socket = None
        if self.server_socket:
            try:
                self.server_socket.close()
            except Exception as e:
                log.debug("Error closing server_socket: %s", e)
            self.server_socket = None
        log.debug("P2PNetworkManager stopped")
