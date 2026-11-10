"""
MadGammon Desktop — Bridges & SMP Relays Manager Dialog
Mirrors Android OnionBridgeConfigManager & SimpleXProtocol:
- Bridge Topology Selector (ClearNet Direct TLS, Tor SOCKS5, V2Ray/Xray, Strict Onion Killswitch)
- Local Daemons Port Probe (Tor :9050, V2Ray :10808)
- Real Live SMP Relay Latency & Health Check (smp10 .. smp16 port 5223 TLS)
"""
import socket
import ssl
import time
import threading
from typing import List, Dict, Optional, Tuple

from PyQt6.QtWidgets import (
    QDialog, QWidget, QVBoxLayout, QHBoxLayout, QLabel,
    QPushButton, QRadioButton, QButtonGroup, QTableWidget,
    QTableWidgetItem, QHeaderView, QFrame, QProgressBar, QMessageBox
)
from PyQt6.QtGui import QFont, QColor, QIcon
from PyQt6.QtCore import Qt, QTimer, pyqtSignal

from logger import get_logger

log = get_logger("madgammon.bridges")

C_BG = QColor(14, 18, 26)
C_PANEL = QColor(22, 28, 40)
C_CYAN = QColor(0, 229, 255)
C_GREEN = QColor(0, 255, 128)
C_AMBER = QColor(255, 185, 45)
C_RED = QColor(255, 51, 102)
C_TEXT_PRIMARY = QColor(240, 245, 255)
C_TEXT_MUTED = QColor(140, 155, 175)

OFFICIAL_SMP_SERVERS = [
    "smp10.simplex.im",
    "smp11.simplex.im",
    "smp12.simplex.im",
    "smp14.simplex.im",
    "smp15.simplex.im",
    "smp16.simplex.im"
]


class RelayHealthChecker:
    @staticmethod
    def check_daemon_port(host: str = "127.0.0.1", port: int = 9050, timeout: float = 1.0) -> Tuple[bool, float]:
        t0 = time.time()
        try:
            s = socket.create_connection((host, port), timeout=timeout)
            s.close()
            rtt = (time.time() - t0) * 1000
            return True, rtt
        except Exception:
            return False, 0.0

    @staticmethod
    def check_smp_server(server: str, port: int = 5223, timeout: float = 3.0) -> Tuple[bool, float, str]:
        t0 = time.time()
        try:
            ctx = ssl.create_default_context()
            ctx.check_hostname = False
            ctx.verify_mode = ssl.CERT_NONE
            s = socket.create_connection((server, port), timeout=timeout)
            ss = ctx.wrap_socket(s, server_hostname=server)
            rtt = (time.time() - t0) * 1000
            ss.close()
            return True, rtt, "ONLINE"
        except socket.timeout:
            return False, 0.0, "TIMEOUT"
        except Exception as e:
            return False, 0.0, str(e)[:30]


class BridgesDialog(QDialog):
    topology_changed = pyqtSignal(str)       # "DIRECT", "TOR_DIRECT", "V2RAY_BRIDGE", "STRICT_ONION"
    active_relay_changed = pyqtSignal(str)   # e.g. "smp10.simplex.im"

    def __init__(self, parent=None, initial_topology: str = "DIRECT", initial_relay: str = "smp10.simplex.im"):
        super().__init__(parent)
        self.setWindowTitle("🌐 Мосты, Прокси и Серверы SimpleX SMP")
        self.resize(680, 560)
        self.current_topology = initial_topology
        self.active_relay = initial_relay
        self.probe_results: Dict[str, Tuple[bool, float, str]] = {}
        self._is_probing = False

        self.setStyleSheet(f"""
            QDialog {{ background: {C_BG.name()}; color: {C_TEXT_PRIMARY.name()}; }}
            QFrame {{ background: {C_PANEL.name()}; border-radius: 8px; }}
            QLabel {{ color: {C_TEXT_PRIMARY.name()}; }}
            QPushButton {{
                background: #1b2636; border: 1px solid #2a3c54;
                color: {C_CYAN.name()}; font-weight: bold; border-radius: 6px; padding: 8px 16px;
            }}
            QPushButton:hover {{ background: #263850; border-color: {C_CYAN.name()}; }}
            QRadioButton {{ color: {C_TEXT_PRIMARY.name()}; font-size: 13px; spacing: 8px; }}
            QRadioButton::indicator {{ width: 16px; height: 16px; }}
            QTableWidget {{
                background: #121822; border: 1px solid #243042; gridline-color: #1e2838;
                color: {C_TEXT_PRIMARY.name()}; selection-background-color: #1b3548;
            }}
            QHeaderView::section {{ background: #182230; color: {C_TEXT_MUTED.name()}; padding: 6px; font-weight: bold; }}
        """)

        self._build_ui()
        QTimer.singleShot(200, self.probe_all)

    def _build_ui(self):
        root = QVBoxLayout(self)
        root.setContentsMargins(18, 18, 18, 18)
        root.setSpacing(14)

        # Title Header
        h_box = QHBoxLayout()
        lbl_icon = QLabel("🛡️")
        lbl_icon.setFont(QFont("Segoe UI Emoji", 22))
        h_box.addWidget(lbl_icon)

        v_titles = QVBoxLayout()
        lbl_title = QLabel("МАРШРУТИЗАЦИЯ И СЕТЬ СИМПЛЕКС")
        lbl_title.setFont(QFont("Monospace", 13, QFont.Weight.Bold))
        lbl_title.setStyleSheet(f"color: {C_CYAN.name()};")
        lbl_sub = QLabel("Управление луковыми мостами, V2Ray туннелями и децентрализованными реле SMP")
        lbl_sub.setFont(QFont("Sans", 9))
        lbl_sub.setStyleSheet(f"color: {C_TEXT_MUTED.name()};")
        v_titles.addWidget(lbl_title)
        v_titles.addWidget(lbl_sub)
        h_box.addLayout(v_titles)
        h_box.addStretch()
        root.addLayout(h_box)

        # ─── 1. TOPOLOGY SELECTOR ───
        top_frame = QFrame()
        tf_layout = QVBoxLayout(top_frame)
        tf_layout.setContentsMargins(14, 12, 14, 12)

        lbl_top_hdr = QLabel("ТОПОЛОГИЯ ТРАФИКА:")
        lbl_top_hdr.setFont(QFont("Monospace", 10, QFont.Weight.Bold))
        lbl_top_hdr.setStyleSheet(f"color: {C_AMBER.name()};")
        tf_layout.addWidget(lbl_top_hdr)

        self.btn_group_top = QButtonGroup(self)
        self.rb_direct = QRadioButton("Direct TLS (Прямое шифрованное подключение)")
        self.rb_tor = QRadioButton("Tor SOCKS5 Daemon (127.0.0.1:9050)")
        self.rb_v2ray = QRadioButton("V2Ray / Xray Proxy Bridge (127.0.0.1:10808)")
        self.rb_strict = QRadioButton("🔒 Strict Onion-Only Policy (Блокировка клирнета, только .onion)")

        self.btn_group_top.addButton(self.rb_direct, 0)
        self.btn_group_top.addButton(self.rb_tor, 1)
        self.btn_group_top.addButton(self.rb_v2ray, 2)
        self.btn_group_top.addButton(self.rb_strict, 3)

        if self.current_topology == "TOR_DIRECT":
            self.rb_tor.setChecked(True)
        elif self.current_topology == "V2RAY_BRIDGE":
            self.rb_v2ray.setChecked(True)
        elif self.current_topology == "STRICT_ONION":
            self.rb_strict.setChecked(True)
        else:
            self.rb_direct.setChecked(True)

        self.btn_group_top.idClicked.connect(self._on_topology_selected)

        tf_layout.addWidget(self.rb_direct)
        tf_layout.addWidget(self.rb_tor)
        tf_layout.addWidget(self.rb_v2ray)
        tf_layout.addWidget(self.rb_strict)
        root.addWidget(top_frame)

        # ─── 2. LOCAL DAEMONS STATUS ───
        status_row = QHBoxLayout()
        self.lbl_tor_status = QLabel("Tor: Проверка...")
        self.lbl_tor_status.setFont(QFont("Monospace", 9, QFont.Weight.Bold))
        self.lbl_v2ray_status = QLabel("V2Ray: Проверка...")
        self.lbl_v2ray_status.setFont(QFont("Monospace", 9, QFont.Weight.Bold))

        status_row.addWidget(self.lbl_tor_status)
        status_row.addSpacing(20)
        status_row.addWidget(self.lbl_v2ray_status)
        status_row.addStretch()
        root.addLayout(status_row)

        # ─── 3. SMP SERVERS TABLE ───
        lbl_tbl_hdr = QLabel("ДЕЦЕНТРАЛИЗОВАННЫЕ РЕЛЕ SIMPLEX SMP (PORT 5223 TLS):")
        lbl_tbl_hdr.setFont(QFont("Monospace", 10, QFont.Weight.Bold))
        lbl_tbl_hdr.setStyleSheet(f"color: {C_CYAN.name()};")
        root.addWidget(lbl_tbl_hdr)

        self.table = QTableWidget()
        self.table.setColumnCount(4)
        self.table.setHorizontalHeaderLabels(["Реле (SMP Server)", "Порт", "RTT (Пинг)", "Статус"])
        self.table.horizontalHeader().setSectionResizeMode(0, QHeaderView.ResizeMode.Stretch)
        self.table.horizontalHeader().setSectionResizeMode(1, QHeaderView.ResizeMode.ResizeToContents)
        self.table.horizontalHeader().setSectionResizeMode(2, QHeaderView.ResizeMode.ResizeToContents)
        self.table.horizontalHeader().setSectionResizeMode(3, QHeaderView.ResizeMode.ResizeToContents)
        self.table.setSelectionBehavior(QTableWidget.SelectionBehavior.SelectRows)
        self.table.setEditTriggers(QTableWidget.EditTrigger.NoEditTriggers)
        self.table.itemDoubleClicked.connect(self._on_table_row_activated)
        root.addWidget(self.table)

        self._populate_table()

        # ─── 4. BOTTOM ACTIONS ───
        bottom_row = QHBoxLayout()
        self.btn_probe = QPushButton("⚡ ПРОВЕРИТЬ ВСЕ РЕЛЕ")
        self.btn_probe.clicked.connect(self.probe_all)
        bottom_row.addWidget(self.btn_probe)

        self.btn_set_active = QPushButton("ВЫБРАТЬ АКТИВНЫМ")
        self.btn_set_active.clicked.connect(self._apply_selected_relay)
        bottom_row.addWidget(self.btn_set_active)

        bottom_row.addStretch()

        self.btn_close = QPushButton("ЗАКРЫТЬ")
        self.btn_close.clicked.connect(self.accept)
        bottom_row.addWidget(self.btn_close)

        root.addLayout(bottom_row)

    def _populate_table(self):
        self.table.setRowCount(len(OFFICIAL_SMP_SERVERS))
        for row, srv in enumerate(OFFICIAL_SMP_SERVERS):
            active_marker = " 🟢 (Активно)" if srv == self.active_relay else ""
            item_name = QTableWidgetItem(f"{srv}{active_marker}")
            item_port = QTableWidgetItem("5223")
            item_rtt = QTableWidgetItem("—")
            item_status = QTableWidgetItem("Ожидание...")

            self.table.setItem(row, 0, item_name)
            self.table.setItem(row, 1, item_port)
            self.table.setItem(row, 2, item_rtt)
            self.table.setItem(row, 3, item_status)

    def probe_all(self):
        if self._is_probing:
            return
        self._is_probing = True
        self.btn_probe.setEnabled(False)
        self.btn_probe.setText("Опрос серверов...")

        def worker():
            # Check Daemons
            tor_ok, tor_rtt = RelayHealthChecker.check_daemon_port("127.0.0.1", 9050)
            v2ray_ok, v2ray_rtt = RelayHealthChecker.check_daemon_port("127.0.0.1", 10808)

            # Check SMP Servers
            smp_results = {}
            for srv in OFFICIAL_SMP_SERVERS:
                ok, rtt, status = RelayHealthChecker.check_smp_server(srv, 5223, timeout=3.0)
                smp_results[srv] = (ok, rtt, status)

            # Update UI on main thread
            QTimer.singleShot(0, lambda: self._on_probe_complete(tor_ok, tor_rtt, v2ray_ok, v2ray_rtt, smp_results))

        threading.Thread(target=worker, daemon=True, name="Bridges-Probe").start()

    def _on_probe_complete(self, tor_ok: bool, tor_rtt: float, v2ray_ok: bool, v2ray_rtt: float, smp_results: dict):
        self._is_probing = False
        self.btn_probe.setEnabled(True)
        self.btn_probe.setText("⚡ ПРОВЕРИТЬ ВСЕ РЕЛЕ")

        # Daemon Badges
        if tor_ok:
            self.lbl_tor_status.setText(f"Tor Daemon :9050: 🟢 ONLINE ({tor_rtt:.1f}ms)")
            self.lbl_tor_status.setStyleSheet(f"color: {C_GREEN.name()};")
        else:
            self.lbl_tor_status.setText("Tor Daemon :9050: 🔴 OFFLINE")
            self.lbl_tor_status.setStyleSheet(f"color: {C_RED.name()};")

        if v2ray_ok:
            self.lbl_v2ray_status.setText(f"V2Ray Bridge :10808: 🟢 ONLINE ({v2ray_rtt:.1f}ms)")
            self.lbl_v2ray_status.setStyleSheet(f"color: {C_GREEN.name()};")
        else:
            self.lbl_v2ray_status.setText("V2Ray Bridge :10808: ⚪ INACTIVE")
            self.lbl_v2ray_status.setStyleSheet(f"color: {C_TEXT_MUTED.name()};")

        # Table update
        for row, srv in enumerate(OFFICIAL_SMP_SERVERS):
            if srv in smp_results:
                ok, rtt, status = smp_results[srv]
                item_rtt = self.table.item(row, 2)
                item_status = self.table.item(row, 3)

                if ok:
                    item_rtt.setText(f"{rtt:.1f} ms")
                    item_rtt.setForeground(C_GREEN if rtt < 400 else C_AMBER)
                    item_status.setText("🟢 ONLINE")
                    item_status.setForeground(C_GREEN)
                else:
                    item_rtt.setText("—")
                    item_status.setText(f"🔴 {status}")
                    item_status.setForeground(C_RED)

    def _on_topology_selected(self, btn_id: int):
        top_map = {0: "DIRECT", 1: "TOR_DIRECT", 2: "V2RAY_BRIDGE", 3: "STRICT_ONION"}
        self.current_topology = top_map.get(btn_id, "DIRECT")
        log.info("Selected Bridge Topology: %s", self.current_topology)
        self.topology_changed.emit(self.current_topology)

    def _on_table_row_activated(self, item: QTableWidgetItem):
        self._apply_selected_relay()

    def _apply_selected_relay(self):
        row = self.table.currentRow()
        if row < 0 or row >= len(OFFICIAL_SMP_SERVERS):
            return
        selected = OFFICIAL_SMP_SERVERS[row]
        self.active_relay = selected
        self._populate_table()
        log.info("Active SMP relay updated to: %s", selected)
        self.active_relay_changed.emit(selected)
        QMessageBox.information(self, "SMP Relay", f"Активный сервер SimpleX SMP переключен на:\n{selected}")
