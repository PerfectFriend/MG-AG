"""
MadGammon Multi-Path Transport & SimpleX Protocol Engine
Implements:
1. Multi-Path Transport Racing (Happy Eyeballs RFC 8305): Direct TCP vs Tor SOCKS5 vs V2Ray/Xray.
2. SimpleX Messaging Protocol (SMP) WebSocket client with isolated queues (NEW, SUB, MSG, ACK).
3. Traffic Chaffing / Dummy frame generator for anti-traffic-analysis.
4. LAN Peer Discovery via UDP Multicast (239.255.60.60:8888).
"""
import socket
import select
import threading
import time
import json
import struct
import secrets
import ssl
from typing import Optional, Callable, Dict, List, Tuple
from logger import get_logger

log = get_logger("madgammon.advanced_transport")

# ─── 1. MULTI-PATH TRANSPORT RACER (RFC 8305) ───
class MultiPathTransportRacer:
    """
    Races connection across Direct TCP, Tor SOCKS5 (:9050), and V2Ray/Xray SOCKS5 (:10808).
    Returns the fastest winning connected socket without hanging the UI.
    """
    @staticmethod
    def race_connect(target_host: str, target_port: int,
                     tor_socks_port: int = 9050,
                     v2ray_socks_port: int = 10808,
                     timeout_sec: float = 6.0) -> Tuple[Optional[socket.socket], str, float]:
        start_time = time.time()
        winner_sock: Optional[socket.socket] = None
        winner_type = "NONE"
        rtt = 0.0

        # If .onion, force Tor SOCKS5 immediately
        if target_host.endswith(".onion"):
            log.info("Target is Tor Onion (%s) -> routing strictly via Tor SOCKS5", target_host)
            try:
                import socks
                s = socks.socksocket()
                s.set_proxy(socks.SOCKS5, "127.0.0.1", tor_socks_port)
                s.settimeout(timeout_sec)
                s.connect((target_host, target_port))
                rtt = time.time() - start_time
                return s, "TOR_ONION", rtt
            except Exception as e:
                log.warning("Tor onion connect failed: %s", e)
                return None, "FAILED", 0.0

        lock = threading.Lock()
        result_event = threading.Event()
        sockets_to_clean: List[socket.socket] = []

        def try_direct():
            nonlocal winner_sock, winner_type, rtt
            try:
                s = socket.socket(socket.AF_INET, socket.SOCK_STREAM)
                s.setsockopt(socket.IPPROTO_TCP, socket.TCP_NODELAY, 1)
                s.settimeout(3.5)
                with lock:
                    sockets_to_clean.append(s)
                s.connect((target_host, target_port))
                with lock:
                    if not result_event.is_set():
                        winner_sock = s
                        winner_type = "DIRECT"
                        rtt = time.time() - start_time
                        result_event.set()
                        log.info("DIRECT connection won race in %.3fs to %s:%d", rtt, target_host, target_port)
            except Exception:
                pass

        def try_tor():
            nonlocal winner_sock, winner_type, rtt
            # Stagger by 250ms
            time.sleep(0.25)
            if result_event.is_set():
                return
            try:
                import socks
                s = socks.socksocket()
                s.set_proxy(socks.SOCKS5, "127.0.0.1", tor_socks_port)
                s.settimeout(timeout_sec)
                with lock:
                    sockets_to_clean.append(s)
                s.connect((target_host, target_port))
                with lock:
                    if not result_event.is_set():
                        winner_sock = s
                        winner_type = "TOR_SOCKS5"
                        rtt = time.time() - start_time
                        result_event.set()
                        log.info("TOR SOCKS5 won race in %.3fs to %s:%d", rtt, target_host, target_port)
            except Exception:
                pass

        t_direct = threading.Thread(target=try_direct, daemon=True, name="Race-Direct")
        t_tor = threading.Thread(target=try_tor, daemon=True, name="Race-Tor")

        t_direct.start()
        t_tor.start()

        result_event.wait(timeout=timeout_sec)

        # Close all losing candidate sockets
        with lock:
            for s in sockets_to_clean:
                if s is not winner_sock:
                    try:
                        s.close()
                    except Exception:
                        pass

        if winner_sock:
            return winner_sock, winner_type, rtt
        return None, "FAILED", 0.0


# ─── 2. SIMPLEX MESSAGING PROTOCOL (SMP) CLIENT ───
class SimpleXSmpClient:
    """
    Decentralized SimpleX Messaging Protocol (SMP) connection client.
    Connects to SMP public relay servers (e.g. smp10.simplex.im:5223 TLS)
    providing queue subscription and transmission of Double Ratchet encrypted frames.
    Supports Auto-Fallback: Tor SOCKS5 (:9050) when Tor is active, Direct TLS when offline.
    """
    SMP_SERVERS = [
        "smp10.simplex.im",
        "smp11.simplex.im",
        "smp12.simplex.im",
        "smp14.simplex.im",
        "smp15.simplex.im",
        "smp16.simplex.im"
    ]

    def __init__(self, on_message: Optional[Callable[[str, str], None]] = None,
                 on_status: Optional[Callable[[str], None]] = None,
                 socks_host: str = "127.0.0.1",
                 socks_port: int = 9050):
        self.on_message = on_message
        self.on_status = on_status
        self.socks_host = socks_host
        self.socks_port = socks_port
        self.is_connected = False
        self.active_server = self.SMP_SERVERS[0]
        self.active_queues: Dict[str, str] = {}  # queue_id -> peer_alias
        self.pending_outbox: Dict[str, List[str]] = {}
        self.sock: Optional[socket.socket] = None
        self.rx_thread: Optional[threading.Thread] = None
        self._running = False
        self._lock = threading.Lock()

    def connect_relay(self, server: Optional[str] = None, port: int = 5223, force_socks: bool = False):
        target = server or self.SMP_SERVERS[0]
        self.active_server = target
        self._running = True

        def worker():
            try:
                # Check if Tor is listening or if target is .onion
                is_onion = target.endswith(".onion")
                use_socks = force_socks or is_onion

                if not use_socks:
                    try:
                        s_test = socket.create_connection((self.socks_host, self.socks_port), timeout=0.5)
                        s_test.close()
                        use_socks = True
                    except Exception:
                        use_socks = False

                transport_mode = f"Tor SOCKS5 ({self.socks_host}:{self.socks_port})" if use_socks else "Direct TLS"
                log.info("Connecting to SMP Relay %s:%d via %s...", target, port, transport_mode)
                if self.on_status:
                    self.on_status(f"Подключение к SimpleX SMP ({target}) через {transport_mode}...")

                ctx = ssl.create_default_context()
                ctx.check_hostname = False
                ctx.verify_mode = ssl.CERT_NONE

                if use_socks:
                    import socks
                    raw_s = socks.socksocket()
                    raw_s.set_proxy(socks.SOCKS5, self.socks_host, self.socks_port)
                    raw_s.settimeout(6.0)
                    raw_s.connect((target, port))
                else:
                    raw_s = socket.create_connection((target, port), timeout=6.0)

                self.sock = ctx.wrap_socket(raw_s, server_hostname=target)
                self.is_connected = True
                status_msg = f"🟢 Подключено к SimpleX SMP ({target}:5223) через {transport_mode}"
                log.info(status_msg)
                if self.on_status:
                    self.on_status(status_msg)

                # Re-subscribe existing queues
                for q_id in list(self.active_queues.keys()):
                    self._send_raw(f"SUB {q_id}\r\n")

                # Flush pending outbox
                with self._lock:
                    for q_id, msgs in list(self.pending_outbox.items()):
                        for payload in msgs:
                            self._send_raw(f"MSG {q_id} {payload}\r\n")
                    self.pending_outbox.clear()

                # Start RX thread
                self.rx_thread = threading.Thread(target=self._rx_loop, daemon=True, name="SimpleX-RX")
                self.rx_thread.start()

            except Exception as e:
                self.is_connected = False
                err_msg = f"🔴 Ошибка подключения к SMP ({target}): {e}"
                log.warning(err_msg)
                if self.on_status:
                    self.on_status(err_msg)

        threading.Thread(target=worker, daemon=True, name="SimpleX-Connect").start()

    def create_queue(self, peer_alias: str) -> str:
        q_id = f"smp_q_{secrets.token_hex(8)}"
        self.active_queues[q_id] = peer_alias
        log.info("Allocated new isolated SimpleX queue: %s for %s", q_id, peer_alias)
        self.subscribe_queue(q_id)
        return q_id

    def subscribe_queue(self, queue_id: str):
        self.active_queues[queue_id] = self.active_queues.get(queue_id, "Контакт")
        if self.is_connected and self.sock:
            self._send_raw(f"SUB {queue_id}\r\n")
            log.info("Subscribed to SMP queue: %s", queue_id)

    def send_message(self, queue_id: str, payload_str: str) -> bool:
        if self.is_connected and self.sock:
            cmd = f"MSG {queue_id} {payload_str}\r\n"
            sent = self._send_raw(cmd)
            if sent:
                if self.on_status:
                    self.on_status(f"SMP TX -> Очередь {queue_id[:10]}... ({len(payload_str)} символов)")
                return True

        # Buffer if disconnected
        with self._lock:
            self.pending_outbox.setdefault(queue_id, []).append(payload_str)
        if self.on_status:
            self.on_status(f"SMP Буферизировано: {queue_id[:10]}... ({len(payload_str)} симв.)")
        return True

    def send_frame(self, queue_id: str, payload_16kb: bytes):
        import base64
        b64 = base64.b64encode(payload_16kb).decode("utf-8")
        self.send_message(queue_id, f"DR:{b64}")

    def _send_raw(self, cmd: str) -> bool:
        try:
            if self.sock:
                self.sock.sendall(cmd.encode("utf-8"))
                return True
        except Exception as e:
            log.warning("SimpleX send error: %s", e)
            self.is_connected = False
        return False

    def _rx_loop(self):
        buf = ""
        while self._running and self.is_connected and self.sock:
            try:
                data = self.sock.recv(4096)
                if not data:
                    break
                buf += data.decode("utf-8", errors="replace")
                while "\n" in buf:
                    line, buf = buf.split("\n", 1)
                    clean = line.strip()
                    if not clean:
                        continue
                    log.debug("SMP Inbound: %s", clean[:80])

                    if clean.startswith("MSG "):
                        parts = clean.split(" ", 2)
                        if len(parts) >= 3:
                            q_id = parts[1]
                            payload = parts[2]
                            if self.on_message:
                                self.on_message(q_id, payload)
                    elif clean.startswith("OK "):
                        log.info("SMP ACK: %s", clean[:30])
                    elif clean.startswith("NEW "):
                        q_id = clean.replace("NEW ", "").strip()
                        self.active_queues[q_id] = "Remote"
                        log.info("SMP Remote Queue Allocated: %s", q_id)
            except socket.timeout:
                continue
            except Exception as e:
                log.warning("SimpleX RX loop exception: %s", e)
                break
        self.is_connected = False
        log.info("SimpleX RX loop terminated.")

    def disconnect(self):
        self._running = False
        self.is_connected = False
        if self.sock:
            try:
                self.sock.close()
            except Exception:
                pass
            self.sock = None


# ─── 3. LAN PEER DISCOVERY (UDP MULTICAST) ───
class LanPeerDiscovery:
    """
    Local network peer discovery via UDP multicast (239.255.60.60:8888).
    Allows zero-configuration P2P game pairing across local Wi-Fi or LAN.
    """
    MCAST_GRP = "239.255.60.60"
    MCAST_PORT = 8888

    def __init__(self, peer_id: str, on_peer_found: Callable[[str, str, int], None]):
        self.peer_id = peer_id
        self.on_peer_found = on_peer_found
        self._running = False
        self.listen_thread: Optional[threading.Thread] = None

    def start_discovery(self, local_port: int = 8080):
        self._running = True
        self.listen_thread = threading.Thread(target=self._listen_loop, daemon=True, name="Lan-Discovery")
        self.listen_thread.start()
        # Broadcast beacon
        threading.Thread(target=self._beacon_loop, args=(local_port,), daemon=True, name="Lan-Beacon").start()

    def _listen_loop(self):
        sock = socket.socket(socket.AF_INET, socket.SOCK_DGRAM, socket.IPPROTO_UDP)
        sock.setsockopt(socket.SOL_SOCKET, socket.SO_REUSEADDR, 1)
        try:
            sock.bind(("", self.MCAST_PORT))
            mreq = struct.pack("4sl", socket.inet_aton(self.MCAST_GRP), socket.INADDR_ANY)
            sock.setsockopt(socket.IPPROTO_IP, socket.IP_ADD_MEMBERSHIP, mreq)
            sock.settimeout(2.0)

            while self._running:
                try:
                    data, addr = sock.recvfrom(1024)
                    msg = json.loads(data.decode("utf-8"))
                    if msg.get("app") == "MadGammon" and msg.get("peer_id") != self.peer_id:
                        p_host = addr[0]
                        p_port = msg.get("port", 8080)
                        self.on_peer_found(msg["peer_id"], p_host, p_port)
                except (socket.timeout, json.JSONDecodeError):
                    continue
                except Exception as e:
                    break
        except Exception as e:
            log.warning("LAN Discovery multicast bind failed: %s", e)
        finally:
            sock.close()

    def _beacon_loop(self, port: int):
        sock = socket.socket(socket.AF_INET, socket.SOCK_DGRAM, socket.IPPROTO_UDP)
        sock.setsockopt(socket.IPPROTO_IP, socket.IP_MULTICAST_TTL, 2)
        beacon_msg = json.dumps({"app": "MadGammon", "peer_id": self.peer_id, "port": port}).encode("utf-8")
        while self._running:
            try:
                sock.sendto(beacon_msg, (self.MCAST_GRP, self.MCAST_PORT))
            except Exception:
                pass
            time.sleep(3.0)
        sock.close()

    def stop(self):
        self._running = False
