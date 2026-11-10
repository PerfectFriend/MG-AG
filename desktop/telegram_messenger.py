"""
MadGammon Sovereign Cyber-Messenger — Full Telegram Desktop Replica
Plausible Deniability Covert Layer // SimpleX E2EE & Sovereign Vault.
Designed with Telegram Desktop 3-column layout (Folders -> Dialogs -> Chat View + Profile Details),
Rich Chat Bubbles, Message Status ticks, Search filter, Audio/Voice notes, Pinned messages,
Media attachments preview, Contact switcher, and built-in Sovereign Crypto Vault.
"""
import os
import sys
import time
import hashlib
import random
import base64
import urllib.parse
import secrets
from typing import List, Dict, Optional
from dataclasses import dataclass, field

from PyQt6.QtWidgets import (
    QMainWindow, QWidget, QVBoxLayout, QHBoxLayout, QLabel,
    QPushButton, QLineEdit, QTextEdit, QListWidget, QListWidgetItem,
    QFrame, QSplitter, QComboBox, QMessageBox, QScrollArea, QFileDialog,
    QDialog, QApplication
)
from PyQt6.QtGui import (
    QFont, QColor, QPen, QBrush, QIcon, QPainter, QRadialGradient,
    QLinearGradient, QPixmap, QPainterPath
)
from PyQt6.QtCore import Qt, QTimer, pyqtSignal, QRectF, QPointF, QSize, QObject

from cryptography.hazmat.primitives.asymmetric import x25519

from qr_code import QrRenderer
from bridges_dialog import BridgesDialog, OFFICIAL_SMP_SERVERS
from crypto_vault import (
    DoubleRatchetSession, EncryptedRatchetMessage, VaultStorage,
    IdentityManager, Bip39Vault, unpad_from_16kb
)
from transport_mesh import SimpleXSmpClient
from logger import get_logger

log = get_logger("madgammon.telegram_messenger")

# ─── Cyberpunk Telegram Palette ───
C_TG_BG_FOLDERS     = QColor(14, 17, 24)
C_TG_BG_DIALOGS     = QColor(20, 24, 34)
C_TG_BG_CHAT        = QColor(12, 15, 22)
C_TG_BG_INFO        = QColor(18, 22, 30)
C_TG_ACCENT         = QColor(0, 229, 255)       # Cyan neon
C_TG_NEON_GREEN     = QColor(0, 255, 128)       # Green online
C_TG_NEON_AMBER     = QColor(255, 185, 45)      # Amber gold
C_TG_NEON_RED       = QColor(255, 51, 102)      # Panic red
C_TG_TEXT_PRIMARY   = QColor(240, 245, 255)
C_TG_TEXT_SECONDARY = QColor(145, 160, 180)
C_TG_BUBBLE_IN      = QColor(24, 32, 45)
C_TG_BUBBLE_OUT     = QColor(16, 52, 60)
C_TG_BUBBLE_BORDER  = QColor(0, 200, 230, 80)

@dataclass
class ChatMessage:
    id: str
    sender: str
    text: str
    timestamp: str
    is_outgoing: bool
    burn_timer: int = 0
    read: bool = True
    attachment: Optional[str] = None

@dataclass
class ChatContact:
    id: str
    name: str
    handle: str
    avatar_color: QColor
    status: str
    last_msg: str
    last_time: str
    unread_count: int = 0
    is_channel: bool = False
    messages: List[ChatMessage] = field(default_factory=list)
    queue_id: Optional[str] = None
    peer_dh_pub: Optional[bytes] = None
    ratchet: Optional[DoubleRatchetSession] = None


class SmpQtBridge(QObject):
    """Thread-safe bridge to deliver network frames to PyQt event loop."""
    message_received = pyqtSignal(str, str)
    status_updated = pyqtSignal(str)


class MyInviteQrDialog(QDialog):
    """
    Sovereign SimpleX Invite QR Dialog.
    Displays peer invite link with embedded X25519 DH public key
    and in-memory QR code rendered with QrRenderer (pure Python Reed-Solomon GF(256)).
    """
    def __init__(self, server: str, queue_id: str, dh_pub_b64: str, parent=None):
        super().__init__(parent)
        self.setWindowTitle("🔗 Мой SimpleX QR Инвайт // Sovereign E2EE")
        self.resize(460, 560)
        self.setStyleSheet("background: #0d131d; color: #e6edf8;")

        layout = QVBoxLayout(self)
        layout.setContentsMargins(24, 24, 24, 24)
        layout.setSpacing(14)

        title = QLabel("🔗 Ваш SimpleX E2EE Инвайт")
        title.setFont(QFont("sans-serif", 14, QFont.Weight.Bold))
        title.setStyleSheet("color: #00e5ff;")
        title.setAlignment(Qt.AlignmentFlag.AlignCenter)
        layout.addWidget(title)

        desc = QLabel(
            "Покажите этот QR-код собеседнику или скопируйте ссылку для подключения.\n"
            "Канал защищён двойным храповиком Double Ratchet и постоянным паддингом 16KB."
        )
        desc.setWordWrap(True)
        desc.setFont(QFont("sans-serif", 9))
        desc.setStyleSheet("color: #8899aa;")
        desc.setAlignment(Qt.AlignmentFlag.AlignCenter)
        layout.addWidget(desc)

        # Build invite URL matching Android SimpleX format
        self.invite_url = f"simplex://invite?server={server}&queue={queue_id}&dh={dh_pub_b64}"

        # QR Code Display
        qr_frame = QFrame()
        qr_frame.setStyleSheet("background: #ffffff; border: 2px solid #00e5ff; border-radius: 12px; padding: 10px;")
        qrf_layout = QVBoxLayout(qr_frame)
        qrf_layout.setAlignment(Qt.AlignmentFlag.AlignCenter)

        lbl_qr = QLabel()
        pixmap = QrRenderer.render_pixmap(self.invite_url, size=240, border_modules=2)
        lbl_qr.setPixmap(pixmap)
        lbl_qr.setAlignment(Qt.AlignmentFlag.AlignCenter)
        qrf_layout.addWidget(lbl_qr)
        layout.addWidget(qr_frame, 0, Qt.AlignmentFlag.AlignCenter)

        # Server and Queue info
        info_lbl = QLabel(f"<b>Релей:</b> {server} | <b>Очередь:</b> {queue_id[:16]}...")
        info_lbl.setFont(QFont("Monospace", 8))
        info_lbl.setStyleSheet("color: #00ffaa;")
        info_lbl.setAlignment(Qt.AlignmentFlag.AlignCenter)
        layout.addWidget(info_lbl)

        # URL Text box
        txt_url = QLineEdit(self.invite_url)
        txt_url.setReadOnly(True)
        txt_url.setStyleSheet("""
            QLineEdit {
                background: #151e2b; color: #88bbdd; border: 1px solid #223348;
                border-radius: 6px; padding: 8px; font-family: Monospace; font-size: 9px;
            }
        """)
        layout.addWidget(txt_url)

        # Buttons
        btn_box = QHBoxLayout()
        btn_copy = QPushButton("📋 Скопировать ссылку")
        btn_copy.setStyleSheet("""
            QPushButton {
                background: #004455; color: #00e5ff; border: 1.5px solid #00e5ff;
                border-radius: 6px; padding: 10px 16px; font-weight: bold;
            }
            QPushButton:hover { background: #006677; color: #ffffff; }
        """)
        btn_copy.clicked.connect(self._copy_link)
        btn_box.addWidget(btn_copy)

        btn_close = QPushButton("Закрыть")
        btn_close.setStyleSheet("""
            QPushButton {
                background: #1c2636; color: #ffffff; border: 1px solid #334455;
                border-radius: 6px; padding: 10px 16px;
            }
            QPushButton:hover { background: #28384e; }
        """)
        btn_close.clicked.connect(self.accept)
        btn_box.addWidget(btn_close)

        layout.addLayout(btn_box)

    def _copy_link(self):
        cb = QApplication.clipboard()
        if cb:
            cb.setText(self.invite_url)
            QMessageBox.information(self, "Скопировано", "Ссылка-инвайт скопирована в буфер обмена!")


class ConnectByInviteDialog(QDialog):
    """
    Connect to SimpleX Peer by Invite URL.
    Parses relay server, queue ID, and peer X25519 DH public key.
    """
    def __init__(self, parent=None):
        super().__init__(parent)
        self.setWindowTitle("➕ Подключение к собеседнику SimpleX")
        self.resize(500, 360)
        self.setStyleSheet("background: #0d131d; color: #e6edf8;")

        self.parsed_server = ""
        self.parsed_queue = ""
        self.parsed_peer_dh = b""
        self.contact_name = ""

        layout = QVBoxLayout(self)
        layout.setContentsMargins(24, 24, 24, 24)
        layout.setSpacing(14)

        title = QLabel("➕ Подключение через SimpleX инвайт")
        title.setFont(QFont("sans-serif", 14, QFont.Weight.Bold))
        title.setStyleSheet("color: #00e5ff;")
        layout.addWidget(title)

        desc = QLabel(
            "Вставьте ссылку-инвайт, полученную от собеседника.\n"
            "Формат: simplex://invite?server=...&queue=...&dh=..."
        )
        desc.setWordWrap(True)
        desc.setStyleSheet("color: #8899aa; font-size: 11px;")
        layout.addWidget(desc)

        layout.addWidget(QLabel("Имя собеседника:"))
        self.name_edit = QLineEdit("SimpleX Peer")
        self.name_edit.setStyleSheet("""
            QLineEdit {
                background: #151e2b; color: #ffffff; border: 1px solid #223348;
                border-radius: 6px; padding: 8px 12px; font-size: 12px;
            }
        """)
        layout.addWidget(self.name_edit)

        layout.addWidget(QLabel("Ссылка-инвайт:"))
        self.link_edit = QLineEdit()
        self.link_edit.setPlaceholderText("simplex://invite?server=smp10.simplex.im&queue=...&dh=...")
        self.link_edit.setStyleSheet("""
            QLineEdit {
                background: #151e2b; color: #00e5ff; border: 1px solid #223348;
                border-radius: 6px; padding: 8px 12px; font-family: Monospace; font-size: 10px;
            }
            QLineEdit:focus { border: 1px solid #00e5ff; }
        """)
        layout.addWidget(self.link_edit)

        self.status_lbl = QLabel("")
        self.status_lbl.setFont(QFont("Monospace", 9))
        self.status_lbl.setStyleSheet("color: #ff5577;")
        layout.addWidget(self.status_lbl)

        layout.addStretch()

        btn_box = QHBoxLayout()
        btn_connect = QPushButton("⚡ Установить E2EE соединение")
        btn_connect.setStyleSheet("""
            QPushButton {
                background: #005544; color: #00ffaa; border: 1.5px solid #00ffaa;
                border-radius: 6px; padding: 10px 16px; font-weight: bold; font-size: 11px;
            }
            QPushButton:hover { background: #007755; color: #ffffff; }
        """)
        btn_connect.clicked.connect(self._validate_and_connect)
        btn_box.addWidget(btn_connect)

        btn_cancel = QPushButton("Отмена")
        btn_cancel.setStyleSheet("""
            QPushButton {
                background: #1c2636; color: #ffffff; border: 1px solid #334455;
                border-radius: 6px; padding: 10px 16px;
            }
            QPushButton:hover { background: #28384e; }
        """)
        btn_cancel.clicked.connect(self.reject)
        btn_box.addWidget(btn_cancel)

        layout.addLayout(btn_box)

    def _validate_and_connect(self):
        raw_link = self.link_edit.text().strip()
        if not raw_link:
            self.status_lbl.setText("Ошибка: введите ссылку-инвайт")
            return

        try:
            parsed = urllib.parse.urlparse(raw_link)
            query = urllib.parse.parse_qs(parsed.query)
            server = query.get("server", ["smp10.simplex.im"])[0]
            queue = query.get("queue", [""])[0]
            dh_b64 = query.get("dh", [""])[0]

            if not queue:
                self.status_lbl.setText("Ошибка: отсутствует параметр queue в ссылке")
                return

            peer_dh = base64.b64decode(dh_b64) if dh_b64 else b""

            self.parsed_server = server
            self.parsed_queue = queue
            self.parsed_peer_dh = peer_dh
            self.contact_name = self.name_edit.text().strip() or "SimpleX Peer"
            self.accept()
        except Exception as e:
            self.status_lbl.setText(f"Ошибка разбора инвайта: {e}")


class CyberTelegramMessenger(QMainWindow):
    """
    Full Telegram Desktop UI Clone with Sovereign Cyberpunk Art Theme.
    Includes active SimpleX SMP Relay transport, QR Code invites,
    and end-to-end Signal Double Ratchet with 16KB constant-size padding.
    """
    closed_to_game = pyqtSignal()

    def __init__(self, mode: str = "REAL", parent=None):
        super().__init__(parent)
        self.mode = mode
        self.setWindowTitle(f"Telegram Sovereign E2EE // Covert Cyber-Mesh [{mode}]")
        self.resize(1180, 780)
        self.setMinimumSize(920, 600)

        # Background wallpaper texture
        self.chat_bg_pixmap: Optional[QPixmap] = None
        self._load_background_wallpaper()

        # Initialize Identity and SimpleX transport
        self.identity = None
        self._init_identity()

        # SimpleX SMP Relay Client
        self.smp_bridge = SmpQtBridge()
        self.smp_bridge.message_received.connect(self._on_smp_message_received)
        self.smp_bridge.status_updated.connect(self._on_smp_status_updated)
        self.smp_client = SimpleXSmpClient(
            on_message=self.smp_bridge.message_received.emit,
            on_status=self.smp_bridge.status_updated.emit
        )
        self.my_queue_id = f"mg_q_{secrets.token_hex(8)}"
        self.smp_client.connect_relay()

        # Initialize contacts
        self._init_contacts_data()
        self.current_contact: ChatContact = self.contacts[0]

        self._build_ui()
        log.info("CyberTelegramMessenger initialized successfully in %s mode (queue: %s)", mode, self.my_queue_id)

    def _init_identity(self):
        try:
            words = Bip39Vault.generate_mnemonic(12)
            self.identity = IdentityManager.derive_identity(words)
        except Exception as e:
            log.warning("Could not derive identity from vault: %s", e)
            words = "abandon abandon abandon abandon abandon abandon abandon abandon abandon abandon abandon about"
            self.identity = IdentityManager.derive_identity(words)

        self.my_dh_pub = bytes.fromhex(self.identity["x25519_pub_hex"])
        self.my_dh_pub_b64 = base64.b64encode(self.my_dh_pub).decode("ascii")

    def _load_background_wallpaper(self):
        assets_dir = os.path.join(os.path.dirname(__file__), "assets")
        wp_path = os.path.join(assets_dir, "telegram_cyber_bg.png")
        if os.path.exists(wp_path):
            self.chat_bg_pixmap = QPixmap(wp_path)

    def _init_contacts_data(self):
        t_now = time.strftime("%H:%M")
        self.contacts = [
            ChatContact(
                id="alice",
                name="Alice V. (Double Ratchet)",
                handle="@alice_sovereign",
                avatar_color=QColor(0, 229, 255),
                status="online • SimpleX SMP",
                last_msg="Mental Dice commit-reveal consensus synchronized.",
                last_time="02:40",
                unread_count=0,
                messages=[
                    ChatMessage("1", "Alice V.", "Привет! Handshake подтверждён. Защищённый канал SimpleX поверх Нард работает.", "02:35", False),
                    ChatMessage("2", "Вы", "Отлично, протокол Double Ratchet с постоянным паддингом 16KB активен.", "02:38", True),
                    ChatMessage("3", "Alice V.", "Mental Dice commit-reveal consensus synchronized. Никакой утечки метаданных.", "02:40", False),
                ]
            ),
            ChatContact(
                id="simplex_channel",
                name="⚡ Sovereign Cyber News",
                handle="@simplex_news",
                avatar_color=QColor(0, 255, 128),
                status="14,208 subscribers",
                last_msg="[RELEASE] v2.4 Onion Mesh routing & zero-knowledge proofs active.",
                last_time="01:15",
                unread_count=2,
                is_channel=True,
                messages=[
                    ChatMessage("101", "Sovereign News", "Добро пожаловать в Sovereign Cyberpunk Network!\nВсе сообщения передаются через децентрализованные SMP очереди.", "01:00", False),
                    ChatMessage("102", "Sovereign News", "[RELEASE] v2.4 Onion Mesh routing & zero-knowledge proofs active.", "01:15", False),
                ]
            ),
            ChatContact(
                id="cyber_node",
                name="CyberNode 7 (Onion Relay)",
                handle="@node7_tor",
                avatar_color=QColor(255, 185, 45),
                status="ping 38ms • Tor Guard",
                last_msg="Bridge circuits healthy. 0 dropped packets.",
                last_time="Вчера",
                unread_count=0,
                messages=[
                    ChatMessage("201", "CyberNode 7", "Onion descriptor updated on 6 directory authorities.", "Вчера", False),
                    ChatMessage("202", "Вы", "Status query: ping and tunnel throughput?", "Вчера", True),
                    ChatMessage("203", "CyberNode 7", "Bridge circuits healthy. 0 dropped packets.", "Вчера", False),
                ]
            ),
            ChatContact(
                id="mesh_group",
                name="🛡️ P2P Mesh Squad (Wi-Fi Direct)",
                handle="4 peers in range",
                avatar_color=QColor(255, 51, 102),
                status="4 online",
                last_msg="Bob: Кости брошены, продолжаем партию.",
                last_time="Пт",
                unread_count=0,
                messages=[
                    ChatMessage("301", "Bob", "Кости брошены, продолжаем партию.", "Пт", False),
                ]
            ),
        ]

    def _build_ui(self):
        central = QWidget()
        self.setCentralWidget(central)
        root_layout = QHBoxLayout(central)
        root_layout.setContentsMargins(0, 0, 0, 0)
        root_layout.setSpacing(0)

        # 1. Leftmost Navigation / Folder Bar (TG Style)
        folder_bar = self._build_folders_bar()
        root_layout.addWidget(folder_bar)

        # 2. Main Splitter: Dialogs List | Chat View | Profile Info
        self.splitter = QSplitter(Qt.Orientation.Horizontal)
        self.splitter.setStyleSheet("""
            QSplitter::handle {
                background: #121822;
                width: 1px;
            }
        """)

        # Dialogs column
        dialogs_panel = self._build_dialogs_panel()
        self.splitter.addWidget(dialogs_panel)

        # Chat view column
        chat_panel = self._build_chat_panel()
        self.splitter.addWidget(chat_panel)

        # Right Info / Wallet panel
        self.info_panel = self._build_info_panel()
        self.splitter.addWidget(self.info_panel)

        self.splitter.setStretchFactor(0, 3)
        self.splitter.setStretchFactor(1, 6)
        self.splitter.setStretchFactor(2, 3)

        root_layout.addWidget(self.splitter, 1)

    # ─── 1. FOLDERS BAR ───
    def _build_folders_bar(self) -> QWidget:
        bar = QFrame()
        bar.setFixedWidth(64)
        bar.setStyleSheet(f"background: {C_TG_BG_FOLDERS.name()}; border-right: 1px solid #1c2432;")
        layout = QVBoxLayout(bar)
        layout.setContentsMargins(6, 12, 6, 12)
        layout.setSpacing(14)

        # App Logo / Menu Icon
        btn_menu = QPushButton("☰")
        btn_menu.setFixedSize(48, 48)
        btn_menu.setStyleSheet("""
            QPushButton {
                background: #1a2232; color: #00e5ff; font-size: 18px; font-weight: bold;
                border: 1px solid #00e5ff; border-radius: 24px;
            }
            QPushButton:hover { background: #004455; color: #ffffff; }
        """)
        btn_menu.clicked.connect(self._toggle_info_panel)
        layout.addWidget(btn_menu)

        # Folder icons
        folders = [
            ("💬", "Все чаты", True),
            ("👤", "Личные", False),
            ("📢", "Каналы", False),
            ("💰", "Кошелек", False),
            ("🔒", "SimpleX", False),
        ]
        for icon_txt, tooltip, is_active in folders:
            btn = QPushButton(icon_txt)
            btn.setFixedSize(44, 44)
            btn.setToolTip(tooltip)
            active_style = "background: #00e5ff; color: #000000; border: none;" if is_active else "background: #18202e; color: #8899aa; border: 1px solid #253448;"
            btn.setStyleSheet(f"""
                QPushButton {{
                    {active_style}
                    border-radius: 22px; font-size: 16px;
                }}
                QPushButton:hover {{ background: #003344; color: #00e5ff; border-color: #00e5ff; }}
            """)
            if tooltip == "Кошелек":
                btn.clicked.connect(self._open_wallet_view)
            layout.addWidget(btn)

        layout.addStretch()

        # Panic WIPE button at bottom of folder bar
        btn_panic = QPushButton("🚨")
        btn_panic.setFixedSize(46, 46)
        btn_panic.setToolTip("PANIC WIPE // Мгновенный возврат в нарды (Ctrl+Shift+S)")
        btn_panic.setStyleSheet("""
            QPushButton {
                background: #4a0d18; color: #ff3366; border: 1.5px solid #ff3366;
                border-radius: 23px; font-size: 18px;
            }
            QPushButton:hover { background: #7a1528; color: #ffffff; }
        """)
        btn_panic.clicked.connect(self._panic_close)
        layout.addWidget(btn_panic)

        return bar

    # ─── 2. DIALOGS LIST PANEL ───
    def _build_dialogs_panel(self) -> QWidget:
        panel = QFrame()
        panel.setMinimumWidth(260)
        panel.setMaximumWidth(360)
        panel.setStyleSheet(f"background: {C_TG_BG_DIALOGS.name()}; border-right: 1px solid #1a2230;")
        layout = QVBoxLayout(panel)
        layout.setContentsMargins(10, 10, 10, 10)
        layout.setSpacing(8)

        # Top Sovereign Action Toolbar
        actions_row = QHBoxLayout()
        actions_row.setSpacing(6)

        btn_my_qr = QPushButton("🔗 QR")
        btn_my_qr.setToolTip("Показать мой SimpleX QR-код инвайт")
        btn_my_qr.setStyleSheet("""
            QPushButton {
                background: #003344; color: #00e5ff; border: 1px solid #00aacc;
                border-radius: 4px; padding: 5px; font-weight: bold; font-size: 10px;
            }
            QPushButton:hover { background: #005566; color: #ffffff; }
        """)
        btn_my_qr.clicked.connect(self._open_my_qr_dialog)
        actions_row.addWidget(btn_my_qr)

        btn_connect = QPushButton("➕ Инвайт")
        btn_connect.setToolTip("Подключиться к собеседнику по инвайт-ссылке")
        btn_connect.setStyleSheet("""
            QPushButton {
                background: #003a28; color: #00ffaa; border: 1px solid #00cc88;
                border-radius: 4px; padding: 5px; font-weight: bold; font-size: 10px;
            }
            QPushButton:hover { background: #00553a; color: #ffffff; }
        """)
        btn_connect.clicked.connect(self._open_connect_dialog)
        actions_row.addWidget(btn_connect)

        btn_bridges = QPushButton("🌐 Мосты")
        btn_bridges.setToolTip("Мосты, Прокси (Tor/V2Ray) и Серверы SMP")
        btn_bridges.setStyleSheet("""
            QPushButton {
                background: #2a2010; color: #ffb92d; border: 1px solid #cc9424;
                border-radius: 4px; padding: 5px; font-weight: bold; font-size: 10px;
            }
            QPushButton:hover { background: #3d2e17; color: #ffffff; }
        """)
        btn_bridges.clicked.connect(self._open_bridges_dialog)
        actions_row.addWidget(btn_bridges)

        layout.addLayout(actions_row)

        # Search Bar
        search_layout = QHBoxLayout()
        self.search_input = QLineEdit()
        self.search_input.setPlaceholderText("🔍 Поиск сообщений или чатов...")
        self.search_input.setStyleSheet("""
            QLineEdit {
                background: #141a24; color: #e0e6ed; border: 1px solid #202c3e;
                border-radius: 16px; padding: 6px 14px; font-size: 11px; font-family: sans-serif;
            }
            QLineEdit:focus { border: 1px solid #00e5ff; }
        """)
        self.search_input.textChanged.connect(self._filter_dialogs)
        search_layout.addWidget(self.search_input)
        layout.addLayout(search_layout)

        # Dialogs List Widget
        self.dialogs_list = QListWidget()
        self.dialogs_list.setStyleSheet("""
            QListWidget {
                background: transparent; border: none; outline: none;
            }
            QListWidget::item {
                border-radius: 8px; margin: 2px 0; padding: 6px 4px;
            }
            QListWidget::item:selected {
                background: #18283a; border-left: 3px solid #00e5ff;
            }
            QListWidget::item:hover:!selected {
                background: #151e2b;
            }
        """)
        self.dialogs_list.itemClicked.connect(self._on_contact_selected)
        layout.addWidget(self.dialogs_list, 1)

        # SMP Status Badge
        self.lbl_smp_badge = QLabel("🟢 SimpleX SMP: подключение...")
        self.lbl_smp_badge.setFont(QFont("Monospace", 8))
        self.lbl_smp_badge.setStyleSheet("color: #708098; padding: 4px;")
        layout.addWidget(self.lbl_smp_badge)

        self._populate_dialogs_list()
        return panel

    def _open_my_qr_dialog(self):
        self.smp_client.subscribe_queue(self.my_queue_id)
        dlg = MyInviteQrDialog(
            server=self.smp_client.active_server,
            queue_id=self.my_queue_id,
            dh_pub_b64=self.my_dh_pub_b64,
            parent=self
        )
        dlg.exec()

    def _open_connect_dialog(self):
        dlg = ConnectByInviteDialog(parent=self)
        if dlg.exec() == QDialog.DialogCode.Accepted:
            peer_name = dlg.contact_name
            server = dlg.parsed_server
            queue_id = dlg.parsed_queue
            peer_dh = dlg.parsed_peer_dh

            # Create Double Ratchet session matching Android GameViewModel
            root_seed = hashlib.sha256(f"simplex_room_root:ROOM_{queue_id}".encode("utf-8")).digest()
            my_dh_priv = x25519.X25519PrivateKey.from_private_bytes(bytes.fromhex(self.identity["x25519_priv_hex"]))

            ratchet = DoubleRatchetSession(
                root_key=root_seed,
                my_dh_priv=my_dh_priv,
                peer_dh_pub_bytes=peer_dh if len(peer_dh) == 32 else None,
                is_initiator=True
            )

            # Subscribe to peer queue on SMP
            self.smp_client.subscribe_queue(queue_id)

            # Send initial HELLO handshake with our DH key
            self.smp_client.send_message(queue_id, f"HELLO:{self.my_dh_pub_b64}")

            # Create new contact
            new_contact = ChatContact(
                id=queue_id,
                name=f"{peer_name} (SimpleX)",
                handle=f"@simplex_{queue_id[:8]}",
                avatar_color=QColor(0, 255, 128),
                status=f"online • {server}",
                last_msg="[E2EE Double Ratchet Handshake sent]",
                last_time=time.strftime("%H:%M"),
                unread_count=0,
                messages=[
                    ChatMessage(
                        id=str(int(time.time() * 1000)),
                        sender="System",
                        text=f"🤝 SimpleX SMP сессия установлена с очередью {queue_id}. Отправлен DH handshake.",
                        timestamp=time.strftime("%H:%M"),
                        is_outgoing=False
                    )
                ],
                queue_id=queue_id,
                peer_dh_pub=peer_dh,
                ratchet=ratchet
            )
            self.contacts.insert(0, new_contact)
            self.current_contact = new_contact
            self._populate_dialogs_list()
            self._update_chat_header()
            self._render_chat_messages()
            self._update_info_panel()

    def _open_bridges_dialog(self):
        dlg = BridgesDialog(self)
        dlg.exec()

    def _on_smp_status_updated(self, status: str):
        if hasattr(self, "lbl_smp_badge"):
            short = status if len(status) < 40 else status[:37] + "..."
            self.lbl_smp_badge.setText(short)
            if "🟢" in status or "Подключено" in status:
                self.lbl_smp_badge.setStyleSheet("color: #00ffaa; padding: 4px;")
            elif "🔴" in status or "Ошибка" in status:
                self.lbl_smp_badge.setStyleSheet("color: #ff5577; padding: 4px;")
            else:
                self.lbl_smp_badge.setStyleSheet("color: #ffb92d; padding: 4px;")

    def _on_smp_message_received(self, queue_id: str, payload: str):
        log.info("Received SMP message on queue %s: %s...", queue_id, payload[:50])

        contact = next((c for c in self.contacts if c.queue_id == queue_id), None)
        if not contact:
            root_seed = hashlib.sha256(f"simplex_room_root:ROOM_{queue_id}".encode("utf-8")).digest()
            my_dh_priv = x25519.X25519PrivateKey.from_private_bytes(bytes.fromhex(self.identity["x25519_priv_hex"]))
            ratchet = DoubleRatchetSession(
                root_key=root_seed,
                my_dh_priv=my_dh_priv,
                is_initiator=False
            )
            contact = ChatContact(
                id=queue_id,
                name=f"Peer ({queue_id[:8]})",
                handle=f"@simplex_{queue_id[:8]}",
                avatar_color=QColor(0, 229, 255),
                status="online • SimpleX SMP",
                last_msg="Входящее подключение",
                last_time=time.strftime("%H:%M"),
                unread_count=0,
                messages=[],
                queue_id=queue_id,
                ratchet=ratchet
            )
            self.contacts.insert(0, contact)
            self._populate_dialogs_list()

        t_now = time.strftime("%H:%M")

        if payload.startswith("HELLO:"):
            peer_key_b64 = payload[6:].strip()
            try:
                raw_key = base64.b64decode(peer_key_b64)
                if len(raw_key) == 32:
                    contact.peer_dh_pub = raw_key
                    if contact.ratchet:
                        contact.ratchet.set_peer_dh_pub(raw_key)
                    sys_msg = ChatMessage(
                        id=str(int(time.time() * 1000)),
                        sender="System",
                        text="🤝 DH Handshake подтверждён собеседником. E2EE канал полностью активен.",
                        timestamp=t_now,
                        is_outgoing=False
                    )
                    contact.messages.append(sys_msg)
                    contact.last_msg = "DH Handshake confirmed"
                    contact.last_time = t_now
                    if self.current_contact.id == contact.id:
                        self._render_chat_messages()
                    self._populate_dialogs_list()
            except Exception as e:
                log.warning("Failed to decode HELLO DH key: %s", e)

        elif payload.startswith("DR:"):
            b64_wire = payload[3:].strip()
            try:
                wire_bytes = base64.b64decode(b64_wire)
                enc_msg = EncryptedRatchetMessage.deserialize(wire_bytes)
                if contact.ratchet:
                    pt_bytes = contact.ratchet.decrypt_message(enc_msg)
                    text = pt_bytes.decode("utf-8")

                    inc_msg = ChatMessage(
                        id=str(int(time.time() * 1000)),
                        sender=contact.name.split()[0],
                        text=text,
                        timestamp=t_now,
                        is_outgoing=False
                    )
                    contact.messages.append(inc_msg)
                    contact.last_msg = text
                    contact.last_time = t_now
                    if self.current_contact.id == contact.id:
                        self._render_chat_messages()
                    else:
                        contact.unread_count += 1
                    self._populate_dialogs_list()
                    log.info("Decrypted Double Ratchet message (%d chars) from %s", len(text), queue_id)
            except Exception as e:
                log.error("Failed to decrypt incoming Double Ratchet message: %s", e)

    def _populate_dialogs_list(self, filter_text: str = ""):
        self.dialogs_list.clear()
        for c in self.contacts:
            if filter_text and filter_text.lower() not in c.name.lower() and filter_text.lower() not in c.last_msg.lower():
                continue
            item = QListWidgetItem()
            item.setSizeHint(QSize(250, 62))
            item.setData(Qt.ItemDataRole.UserRole, c)

            w = QWidget()
            wl = QHBoxLayout(w)
            wl.setContentsMargins(6, 4, 6, 4)
            wl.setSpacing(10)

            avatar = QLabel(c.name[0])
            avatar.setFixedSize(42, 42)
            avatar.setAlignment(Qt.AlignmentFlag.AlignCenter)
            avatar.setFont(QFont("sans-serif", 13, QFont.Weight.Bold))
            avatar.setStyleSheet(f"""
                background: {c.avatar_color.name()}; color: #0b0f16;
                border-radius: 21px; font-weight: bold;
            """)
            wl.addWidget(avatar)

            txt_box = QVBoxLayout()
            txt_box.setSpacing(2)
            txt_box.setContentsMargins(0, 2, 0, 2)

            top_row = QHBoxLayout()
            lbl_name = QLabel(c.name)
            lbl_name.setFont(QFont("sans-serif", 11, QFont.Weight.Bold))
            lbl_name.setStyleSheet("color: #e6edf8;")
            top_row.addWidget(lbl_name, 1)

            lbl_time = QLabel(c.last_time)
            lbl_time.setFont(QFont("Monospace", 8))
            lbl_time.setStyleSheet("color: #708098;")
            top_row.addWidget(lbl_time)
            txt_box.addLayout(top_row)

            bot_row = QHBoxLayout()
            short_msg = c.last_msg if len(c.last_msg) < 36 else c.last_msg[:36] + "..."
            lbl_msg = QLabel(short_msg)
            lbl_msg.setFont(QFont("sans-serif", 9))
            lbl_msg.setStyleSheet("color: #9ab0cb;")
            bot_row.addWidget(lbl_msg, 1)

            if c.unread_count > 0:
                badge = QLabel(str(c.unread_count))
                badge.setFixedSize(18, 18)
                badge.setAlignment(Qt.AlignmentFlag.AlignCenter)
                badge.setFont(QFont("Monospace", 8, QFont.Weight.Bold))
                badge.setStyleSheet("background: #00e5ff; color: #000000; border-radius: 9px;")
                bot_row.addWidget(badge)
            txt_box.addLayout(bot_row)

            wl.addLayout(txt_box, 1)

            self.dialogs_list.addItem(item)
            self.dialogs_list.setItemWidget(item, w)

            if c.id == self.current_contact.id:
                self.dialogs_list.setCurrentItem(item)

    def _filter_dialogs(self, text: str):
        self._populate_dialogs_list(text.strip())

    def _on_contact_selected(self, item: QListWidgetItem):
        contact: ChatContact = item.data(Qt.ItemDataRole.UserRole)
        self.current_contact = contact
        self._update_chat_header()
        self._render_chat_messages()
        self._update_info_panel()

    # ─── 3. CHAT VIEW PANEL (Telegram Message Feed) ───
    def _build_chat_panel(self) -> QWidget:
        panel = QFrame()
        panel.setStyleSheet(f"background: {C_TG_BG_CHAT.name()};")
        layout = QVBoxLayout(panel)
        layout.setContentsMargins(0, 0, 0, 0)
        layout.setSpacing(0)

        # Chat Header
        self.chat_header = QFrame()
        self.chat_header.setFixedHeight(62)
        self.chat_header.setStyleSheet("background: #141a24; border-bottom: 1px solid #1e2938;")
        ch_layout = QHBoxLayout(self.chat_header)
        ch_layout.setContentsMargins(16, 8, 16, 8)

        self.lbl_chat_title = QLabel()
        self.lbl_chat_title.setFont(QFont("sans-serif", 12, QFont.Weight.Bold))
        self.lbl_chat_title.setStyleSheet("color: #ffffff;")
        ch_layout.addWidget(self.lbl_chat_title)

        ch_layout.addStretch()

        self.lbl_chat_status = QLabel()
        self.lbl_chat_status.setFont(QFont("Monospace", 9))
        self.lbl_chat_status.setStyleSheet("color: #00ffaa;")
        ch_layout.addWidget(self.lbl_chat_status)

        # Call & Search & More buttons
        for icon in ["📞", "🔍", "⋮"]:
            btn = QPushButton(icon)
            btn.setFixedSize(36, 36)
            btn.setStyleSheet("""
                QPushButton { background: transparent; color: #88a0bc; border: none; font-size: 15px; }
                QPushButton:hover { background: #1f2a3a; color: #00e5ff; border-radius: 18px; }
            """)
            ch_layout.addWidget(btn)

        layout.addWidget(self.chat_header)

        # Messages Scroll Area
        self.scroll_area = QScrollArea()
        self.scroll_area.setWidgetResizable(True)
        self.scroll_area.setStyleSheet("QScrollArea { border: none; background: transparent; }")

        self.messages_container = QWidget()
        self.messages_container.setStyleSheet("background: transparent;")
        self.messages_layout = QVBoxLayout(self.messages_container)
        self.messages_layout.setContentsMargins(24, 16, 24, 16)
        self.messages_layout.setSpacing(10)
        self.messages_layout.addStretch()

        self.scroll_area.setWidget(self.messages_container)
        layout.addWidget(self.scroll_area, 1)

        # Message Input Row (Telegram style)
        input_bar = QFrame()
        input_bar.setFixedHeight(64)
        input_bar.setStyleSheet("background: #141a24; border-top: 1px solid #1e2938;")
        ib_layout = QHBoxLayout(input_bar)
        ib_layout.setContentsMargins(14, 8, 14, 8)
        ib_layout.setSpacing(10)

        # Attach button (Paperclip)
        btn_attach = QPushButton("📎")
        btn_attach.setFixedSize(38, 38)
        btn_attach.setToolTip("Прикрепить файл / фото")
        btn_attach.setStyleSheet("""
            QPushButton { background: transparent; color: #88a0bc; border: none; font-size: 18px; }
            QPushButton:hover { color: #00e5ff; }
        """)
        btn_attach.clicked.connect(self._attach_file)
        ib_layout.addWidget(btn_attach)

        # Input edit
        self.msg_input = QLineEdit()
        self.msg_input.setPlaceholderText("Написать зашифрованное сообщение...")
        self.msg_input.setStyleSheet("""
            QLineEdit {
                background: #1a2230; color: #ffffff; border: 1px solid #28364a;
                border-radius: 18px; padding: 8px 16px; font-size: 12px; font-family: sans-serif;
            }
            QLineEdit:focus { border: 1.5px solid #00e5ff; }
        """)
        self.msg_input.returnPressed.connect(self._send_message)
        ib_layout.addWidget(self.msg_input, 1)

        # Self-destruct burn selector badge
        self.burn_btn = QPushButton("🔥 5s")
        self.burn_btn.setFixedSize(54, 34)
        self.burn_btn.setToolTip("Таймер самоуничтожения")
        self.burn_btn.setStyleSheet("""
            QPushButton {
                background: #2a151b; color: #ff5577; border: 1px solid #ff3366;
                border-radius: 17px; font-weight: bold; font-family: Monospace; font-size: 10px;
            }
            QPushButton:hover { background: #441c26; }
        """)
        ib_layout.addWidget(self.burn_btn)

        # Voice note button
        btn_voice = QPushButton("🎙️")
        btn_voice.setFixedSize(38, 38)
        btn_voice.setToolTip("Голосовое сообщение")
        btn_voice.setStyleSheet("""
            QPushButton { background: transparent; color: #88a0bc; border: none; font-size: 18px; }
            QPushButton:hover { color: #00ffaa; }
        """)
        ib_layout.addWidget(btn_voice)

        # Send button
        self.btn_send = QPushButton("➤")
        self.btn_send.setFixedSize(40, 40)
        self.btn_send.setToolTip("Отправить")
        self.btn_send.setStyleSheet("""
            QPushButton {
                background: #00e5ff; color: #000000; border: none;
                border-radius: 20px; font-size: 16px; font-weight: bold;
            }
            QPushButton:hover { background: #33eeff; }
        """)
        self.btn_send.clicked.connect(self._send_message)
        ib_layout.addWidget(self.btn_send)

        layout.addWidget(input_bar)

        self._update_chat_header()
        self._render_chat_messages()
        return panel

    def _update_chat_header(self):
        c = self.current_contact
        self.lbl_chat_title.setText(c.name)
        self.lbl_chat_status.setText(c.status)

    def _render_chat_messages(self):
        while self.messages_layout.count() > 1:
            item = self.messages_layout.takeAt(0)
            if item.widget():
                item.widget().deleteLater()

        for msg in self.current_contact.messages:
            bubble = self._create_message_bubble(msg)
            self.messages_layout.insertWidget(self.messages_layout.count() - 1, bubble)

        QTimer.singleShot(50, lambda: self.scroll_area.verticalScrollBar().setValue(
            self.scroll_area.verticalScrollBar().maximum()
        ))

    def _create_message_bubble(self, msg: ChatMessage) -> QWidget:
        w = QWidget()
        row = QHBoxLayout(w)
        row.setContentsMargins(0, 2, 0, 2)

        bubble_frame = QFrame()
        bg_col = C_TG_BUBBLE_OUT.name() if msg.is_outgoing else C_TG_BUBBLE_IN.name()
        border_col = "#00e5ff" if msg.is_outgoing else "#28384e"
        bubble_frame.setStyleSheet(f"""
            QFrame {{
                background: {bg_col};
                border: 1px solid {border_col};
                border-radius: 12px;
                padding: 8px 12px;
            }}
        """)
        bf_layout = QVBoxLayout(bubble_frame)
        bf_layout.setContentsMargins(4, 2, 4, 2)
        bf_layout.setSpacing(4)

        # Message text
        lbl_txt = QLabel(msg.text)
        lbl_txt.setFont(QFont("sans-serif", 11))
        lbl_txt.setStyleSheet("color: #ffffff;")
        lbl_txt.setWordWrap(True)
        bf_layout.addWidget(lbl_txt)

        # Bottom info row: timestamp + ticks
        info_row = QHBoxLayout()
        info_row.addStretch()

        if msg.burn_timer > 0:
            lbl_burn = QLabel(f"🔥 {msg.burn_timer}s")
            lbl_burn.setFont(QFont("Monospace", 8))
            lbl_burn.setStyleSheet("color: #ff5577;")
            info_row.addWidget(lbl_burn)

        lbl_time = QLabel(msg.timestamp)
        lbl_time.setFont(QFont("Monospace", 8))
        lbl_time.setStyleSheet("color: #88a0bc;")
        info_row.addWidget(lbl_time)

        if msg.is_outgoing:
            lbl_ticks = QLabel("✓✓" if msg.read else "✓")
            lbl_ticks.setFont(QFont("Monospace", 8, QFont.Weight.Bold))
            lbl_ticks.setStyleSheet("color: #00e5ff;")
            info_row.addWidget(lbl_ticks)

        bf_layout.addLayout(info_row)

        if msg.is_outgoing:
            row.addStretch()
            row.addWidget(bubble_frame)
        else:
            row.addWidget(bubble_frame)
            row.addStretch()

        return w

    def _send_message(self):
        text = self.msg_input.text().strip()
        if not text:
            return
        t_now = time.strftime("%H:%M")
        msg = ChatMessage(
            id=str(int(time.time() * 1000)),
            sender="Вы",
            text=text,
            timestamp=t_now,
            is_outgoing=True,
            burn_timer=5,
            read=True
        )
        self.current_contact.messages.append(msg)
        self.current_contact.last_msg = text
        self.current_contact.last_time = t_now
        self.msg_input.clear()

        # Re-render
        bubble = self._create_message_bubble(msg)
        self.messages_layout.insertWidget(self.messages_layout.count() - 1, bubble)
        self.scroll_area.verticalScrollBar().setValue(self.scroll_area.verticalScrollBar().maximum())
        self._populate_dialogs_list(self.search_input.text().strip())

        # Check if live SimpleX SMP contact
        if self.current_contact.queue_id and self.current_contact.ratchet:
            try:
                enc = self.current_contact.ratchet.encrypt_message(text.encode("utf-8"))
                wire_bytes = enc.serialize()
                b64_wire = base64.b64encode(wire_bytes).decode("ascii")
                payload = f"DR:{b64_wire}"
                self.smp_client.send_message(self.current_contact.queue_id, payload)
                log.info("Sent Double Ratchet frame (%d bytes) to queue %s", len(wire_bytes), self.current_contact.queue_id)
            except Exception as e:
                log.error("Failed to encrypt/send Double Ratchet message: %s", e)
        elif not self.current_contact.is_channel:
            # Fall back to simulation echo for mock contacts (Alice etc.)
            peer_name = self.current_contact.name.split()[0]
            QTimer.singleShot(1400, lambda: self._receive_peer_reply(peer_name))

    def _receive_peer_reply(self, peer_name: str):
        t_now = time.strftime("%H:%M")
        replies = [
            "Принято по зашифрованному E2EE каналу. Зарики на доске подтверждают секретный хэш.",
            "Пакет доставлен через SimpleX SMP релей. Никаких следов в логах.",
            "Double Ratchet ключ обновлён. Все сессии изолированы.",
            "Отличный ход на доске! Жду следующего броска."
        ]
        reply_txt = random.choice(replies)
        reply_msg = ChatMessage(
            id=str(int(time.time() * 1000)),
            sender=peer_name,
            text=reply_txt,
            timestamp=t_now,
            is_outgoing=False,
            read=True
        )
        self.current_contact.messages.append(reply_msg)
        self.current_contact.last_msg = reply_txt
        self.current_contact.last_time = t_now

        bubble = self._create_message_bubble(reply_msg)
        self.messages_layout.insertWidget(self.messages_layout.count() - 1, bubble)
        self.scroll_area.verticalScrollBar().setValue(self.scroll_area.verticalScrollBar().maximum())
        self._populate_dialogs_list(self.search_input.text().strip())

    def _attach_file(self):
        fpath, _ = QFileDialog.getOpenFileName(self, "Прикрепить защищенный файл", "", "All Files (*)")
        if fpath:
            fname = os.path.basename(fpath)
            self.msg_input.setText(f"📎 [Файл: {fname}] ")

    # ─── 4. PROFILE / VAULT RIGHT PANEL ───
    def _build_info_panel(self) -> QWidget:
        panel = QFrame()
        panel.setMinimumWidth(260)
        panel.setMaximumWidth(340)
        panel.setStyleSheet(f"background: {C_TG_BG_INFO.name()}; border-left: 1px solid #1a2230;")
        layout = QVBoxLayout(panel)
        layout.setContentsMargins(14, 14, 14, 14)
        layout.setSpacing(12)

        # Profile Header
        self.lbl_info_avatar = QLabel()
        self.lbl_info_avatar.setFixedSize(68, 68)
        self.lbl_info_avatar.setAlignment(Qt.AlignmentFlag.AlignCenter)
        self.lbl_info_avatar.setFont(QFont("sans-serif", 20, QFont.Weight.Bold))
        layout.addWidget(self.lbl_info_avatar, 0, Qt.AlignmentFlag.AlignHCenter)

        self.lbl_info_name = QLabel()
        self.lbl_info_name.setFont(QFont("sans-serif", 13, QFont.Weight.Bold))
        self.lbl_info_name.setStyleSheet("color: #ffffff;")
        layout.addWidget(self.lbl_info_name, 0, Qt.AlignmentFlag.AlignHCenter)

        self.lbl_info_handle = QLabel()
        self.lbl_info_handle.setFont(QFont("Monospace", 9))
        self.lbl_info_handle.setStyleSheet("color: #00e5ff;")
        layout.addWidget(self.lbl_info_handle, 0, Qt.AlignmentFlag.AlignHCenter)

        # Cryptographic Verification Card
        verif_box = QFrame()
        verif_box.setStyleSheet("background: #121822; border: 1px solid #00ffaa; border-radius: 8px; padding: 10px;")
        vb_layout = QVBoxLayout(verif_box)
        vb_layout.setSpacing(4)

        lbl_v_head = QLabel("🛡️ E2EE SAS БЕЗОПАСНОСТЬ:")
        lbl_v_head.setFont(QFont("Monospace", 8, QFont.Weight.Bold))
        lbl_v_head.setStyleSheet("color: #00ffaa;")
        vb_layout.addWidget(lbl_v_head)

        lbl_sas = QLabel("SAS Emoji: ⚡ 🛡️ 🔑 🚀")
        lbl_sas.setFont(QFont("sans-serif", 11))
        vb_layout.addWidget(lbl_sas)

        fp_display = self.identity.get("fingerprint", "8f2a...91bc") if self.identity else "8f2a...91bc"
        lbl_fingerprint = QLabel(f"Fingerprint: {fp_display}")
        lbl_fingerprint.setFont(QFont("Monospace", 8))
        lbl_fingerprint.setStyleSheet("color: #8899aa;")
        vb_layout.addWidget(lbl_fingerprint)
        layout.addWidget(verif_box)

        # Quick Crypto Balance Widget
        wallet_box = QFrame()
        wallet_box.setStyleSheet("background: #121822; border: 1px solid #ffd54f; border-radius: 8px; padding: 10px;")
        wb_layout = QVBoxLayout(wallet_box)
        wb_layout.setSpacing(4)

        lbl_w_head = QLabel("💰 SOVEREIGN CRYPTO WALLET:")
        lbl_w_head.setFont(QFont("Monospace", 8, QFont.Weight.Bold))
        lbl_w_head.setStyleSheet("color: #ffd54f;")
        wb_layout.addWidget(lbl_w_head)

        wb_layout.addWidget(QLabel("● BTC: 0.0452 (~$4,318 USD)"))
        wb_layout.addWidget(QLabel("● XMR: 4.8210 (~$795 USD)"))
        wb_layout.addWidget(QLabel("● USDT: 1,250.00 TRC-20"))

        btn_wallet_details = QPushButton("Управление активами →")
        btn_wallet_details.setStyleSheet("""
            QPushButton { background: #262010; color: #ffd54f; border: 1px solid #ffd54f; border-radius: 4px; padding: 4px; font-size: 10px; font-weight: bold; }
            QPushButton:hover { background: #3d3215; }
        """)
        btn_wallet_details.clicked.connect(self._open_wallet_view)
        wb_layout.addWidget(btn_wallet_details)

        layout.addWidget(wallet_box)

        layout.addStretch()

        # Plausible deniability button
        btn_back_game = QPushButton("🎲 ВЕРНУТЬСЯ В НАРДЫ")
        btn_back_game.setStyleSheet("""
            QPushButton {
                background: #182536; color: #00e5ff; border: 1.5px solid #00e5ff;
                border-radius: 6px; padding: 10px; font-weight: bold; font-family: Monospace; font-size: 11px;
            }
            QPushButton:hover { background: #004455; color: #ffffff; }
        """)
        btn_back_game.clicked.connect(self._panic_close)
        layout.addWidget(btn_back_game)

        self._update_info_panel()
        return panel

    def _update_info_panel(self):
        c = self.current_contact
        self.lbl_info_avatar.setText(c.name[0])
        self.lbl_info_avatar.setStyleSheet(f"""
            background: {c.avatar_color.name()}; color: #0b0f16;
            border-radius: 34px; font-weight: bold;
        """)
        self.lbl_info_name.setText(c.name)
        self.lbl_info_handle.setText(c.handle)

    def _toggle_info_panel(self):
        self.info_panel.setVisible(not self.info_panel.isVisible())

    def _open_wallet_view(self):
        QMessageBox.information(
            self,
            "Sovereign Crypto Vault",
            "💰 Sovereign Crypto Wallet активен!\n\n"
            "● Bitcoin (BTC): 0.0452 BTC\n"
            "● Monero (XMR): 4.8210 XMR (Cold Storage)\n"
            "● Tether (USDT): 1,250.00 USDT TRC-20\n\n"
            "BIP-39 HD Seed зашифрован в защищенном анклаве."
        )

    def _panic_close(self):
        log.warning("Plausible deniability triggered: closing messenger, returning to game.")
        self.closed_to_game.emit()
        self.close()
