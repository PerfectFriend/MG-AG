"""
MadGammon Internet Radio Player & Winamp AVS Visualizer Suite
Stream live audio channels matching the sovereign cyber aesthetic.
Features:
- Working high-reliability live audio streams (Cyberpunk/Synthwave, Chillout, RU Hits/Rock, World).
- Robust background ffplay playback with auto-reconnect, user-agent, and watchdog supervision.
- Dynamic volume adjustment via PulseAudio/PipeWire sink-input control.
- Full Winamp AVS Spectrum Analyzer & Visualizer embedded with Screensaver Fullscreen Mode (F11).
- Interactive AVS Preset Studio for combining and customizing visual effects.
"""

import subprocess
import threading
import os
import signal
import time
import atexit
from typing import List, Optional, Callable
from dataclasses import dataclass

from PyQt6.QtWidgets import (
    QDialog, QVBoxLayout, QHBoxLayout, QLabel, QPushButton,
    QListWidget, QListWidgetItem, QComboBox, QSlider, QFrame,
    QLineEdit, QSplitter, QApplication
)
from PyQt6.QtGui import QFont, QColor, QKeySequence, QShortcut
from PyQt6.QtCore import Qt, QTimer, pyqtSignal

from avs_visualizer import AVSCanvasWidget, AVSPresetEditorDialog
from logger import get_logger

log = get_logger("madgammon.radio")


@dataclass
class RadioStation:
    id: str
    name: str
    url: str
    category: str = "ALL"
    genre: str = ""


# ─── VERIFIED LIVE RADIO STREAMS ───
DEFAULT_STATIONS: List[RadioStation] = [
    # ─── CYBERPUNK / SYNTHWAVE / ELECTRONIC ───
    RadioStation("elec_plaza", "⚡ Nightwave Plaza (Synthwave / Vaporwave)", "https://plaza.one/mp3", "ELECTRONIC", "Synthwave"),
    RadioStation("elec_defcon", "🛡️ SomaFM DEF CON Radio (Hacker Beats)", "http://ice2.somafm.com/defcon-128-mp3", "ELECTRONIC", "Cyberpunk"),
    RadioStation("elec_groove", "🌊 SomaFM Groove Salad (Downtempo / Chill)", "http://ice2.somafm.com/groovesalad-128-mp3", "ELECTRONIC", "Ambient"),
    RadioStation("elec_cliqhop", "⚡ SomaFM Cliqhop (IDM / Industrial Cyber)", "http://ice2.somafm.com/cliqhop-128-mp3", "ELECTRONIC", "Industrial"),
    RadioStation("elec_secret", "🕶️ SomaFM Secret Agent (Spy Jazz / Beats)", "http://ice2.somafm.com/secretagent-128-mp3", "ELECTRONIC", "Lounge"),
    RadioStation("elec_drone", "🌌 SomaFM Drone Zone (Atmospheric Drone)", "http://ice2.somafm.com/dronezone-128-mp3", "ELECTRONIC", "Ambient"),
    RadioStation("elec_deepspace", "🚀 SomaFM Deep Space One (Cosmic Ambient)", "http://ice2.somafm.com/deepspaceone-128-mp3", "ELECTRONIC", "Space"),
    RadioStation("elec_record", "🎧 Radio Record Club (Dance / EDM)", "https://hls-01-radiorecord.hostingradio.ru/record/playlist.m3u8", "ELECTRONIC", "EDM"),

    # ─── LO-FI & CHILLOUT ───
    RadioStation("lofi_chillsky", "☕ Chillsky Lo-Fi (Beats to Relax & Play)", "https://stream.zeno.fm/f3wvbbqmdg8uv", "LOFI", "Lo-Fi"),
    RadioStation("lofi_blender", "🍹 SomaFM Beat Blender (Downtempo Fusion)", "http://ice2.somafm.com/beatblender-128-mp3", "LOFI", "Downtempo"),
    RadioStation("lofi_lush", "🌸 SomaFM Lush (Sensuous Female Vocals)", "http://ice2.somafm.com/lush-128-mp3", "LOFI", "Chillout"),

    # ─── RUSSIAN CHANNELS (RU) ───
    RadioStation("ru_europaplus", "🇷🇺 Европа Плюс (Top 40 / Dance)", "http://ep128.hostingradio.ru:8030/ep128", "RU", "Pop/Dance"),
    RadioStation("ru_dfm", "🇷🇺 DFM Club (Dance & Electro)", "https://dfm.hostingradio.ru/dfm128.mp3", "RU", "Dance"),
    RadioStation("ru_montecarlo", "🇷🇺 Радио Monte Carlo (Lounge & Pop)", "https://montecarlo.hostingradio.ru/montecarlo128.mp3", "RU", "Lounge"),
    RadioStation("ru_maximum", "🇷🇺 Радио MAXIMUM (Rock & Alternative)", "https://maximum.hostingradio.ru/maximum128.mp3", "RU", "Rock"),
    RadioStation("ru_nashe", "🇷🇺 Наше Радио (Русский Рок)", "http://nashe1.hostingradio.ru/nashe-128.mp3", "RU", "Rock"),
    RadioStation("ru_rusradio", "🇷🇺 Русское Радио (Хиты)", "https://rusradio.hostingradio.ru/rusradio128.mp3", "RU", "Pop"),
    RadioStation("ru_chanson", "🇷🇺 Радио Шансон", "http://chanson.hostingradio.ru:8041/chanson128.mp3", "RU", "Chanson"),

    # ─── WORLD / INTERNATIONAL ───
    RadioStation("world_kexp", "🇺🇸 KEXP 90.3 FM Seattle (Indie / Alternative)", "https://kexp.streamguys1.com/kexp128.mp3", "WORLD", "Indie"),
    RadioStation("world_fip", "🇫🇷 FIP Radio France (Eclectic & Jazz)", "https://icecast.radiofrance.fr/fip-midfi.mp3", "WORLD", "Eclectic"),
    RadioStation("world_bbc", "🇬🇧 BBC World Service (News & Culture)", "http://stream.live.vc.bbcmedia.co.uk/bbc_world_service", "WORLD", "News"),
]


class RadioPlayer:
    """
    Subprocess-based streaming audio player using ffplay.
    Features:
    - User-agent spoofing & stream auto-reconnect
    - Background watchdog thread for process monitoring
    - Dynamic volume adjustment via pactl sink-input volume control
    """
    def __init__(self, on_state_changed: Optional[Callable[[bool, Optional[RadioStation]], None]] = None):
        self.on_state_changed = on_state_changed
        self.process: Optional[subprocess.Popen] = None
        self.current_station: Optional[RadioStation] = None
        self.volume: int = 80  # 0..100
        self.is_playing = False
        self._lock = threading.Lock()
        self._watchdog_thread: Optional[threading.Thread] = None
        self._stop_requested = False
        # Ensure cleanup on python interpreter exit
        atexit.register(self._stop_process)

    def get_stations(self, cat_filter: str = "ALL") -> List[RadioStation]:
        if cat_filter == "ALL":
            return DEFAULT_STATIONS
        return [s for s in DEFAULT_STATIONS if s.category == cat_filter]

    def play(self, station: RadioStation):
        with self._lock:
            self._stop_process()
            self._stop_requested = False
            self.current_station = station
            log.info("Starting radio playback: %s (%s)", station.name, station.url)

            cmd = [
                "ffplay",
                "-nodisp",
                "-nostats",
                "-loglevel", "warning",
                "-user_agent", "Mozilla/5.0 (X11; Linux x86_64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36",
                "-reconnect", "1",
                "-reconnect_at_eof", "1",
                "-reconnect_streamed", "1",
                "-reconnect_delay_max", "5",
                "-volume", str(self.volume),
                station.url
            ]

            try:
                self.process = subprocess.Popen(
                    cmd,
                    stdin=subprocess.DEVNULL,
                    stdout=subprocess.DEVNULL,
                    stderr=subprocess.DEVNULL,
                    preexec_fn=os.setsid
                )
                self.is_playing = True
                log.info("ffplay spawned with PID %d", self.process.pid)

                # Launch watchdog thread to detect stream crashes / disconnects
                self._watchdog_thread = threading.Thread(
                    target=self._watchdog_loop,
                    args=(self.process,),
                    daemon=True,
                    name=f"RadioWatchdog-{self.process.pid}"
                )
                self._watchdog_thread.start()

                if self.on_state_changed:
                    self.on_state_changed(True, self.current_station)

                # Set volume in PulseAudio/PipeWire for this sink-input
                threading.Timer(0.8, self._apply_pactl_volume, args=(self.process.pid, self.volume)).start()

            except Exception as e:
                log.exception("Failed to spawn ffplay for radio: %s", e)
                self.is_playing = False
                if self.on_state_changed:
                    self.on_state_changed(False, None)

    def _watchdog_loop(self, proc: subprocess.Popen):
        proc.wait()
        with self._lock:
            if not self._stop_requested and self.process == proc:
                log.warning("ffplay process %d exited unexpectedly (returncode: %s)", proc.pid, proc.returncode)
                self.is_playing = False
                if self.on_state_changed:
                    self.on_state_changed(False, self.current_station)

    def stop(self):
        with self._lock:
            self._stop_process()
            self.is_playing = False
            if self.on_state_changed:
                self.on_state_changed(False, self.current_station)

    def _stop_process(self):
        self._stop_requested = True
        if self.process:
            pid = self.process.pid
            log.info("Terminating radio process (PID %d)", pid)
            try:
                os.killpg(os.getpgid(pid), signal.SIGTERM)
                self.process.wait(timeout=0.5)
            except Exception:
                try:
                    os.killpg(os.getpgid(pid), signal.SIGKILL)
                except Exception:
                    pass
            self.process = None

    def toggle(self, station: Optional[RadioStation] = None):
        if self.is_playing:
            self.stop()
        else:
            target = station or self.current_station or DEFAULT_STATIONS[0]
            self.play(target)

    def next_station(self):
        """Switches to the next station in the catalogue."""
        if not DEFAULT_STATIONS:
            return
        if not self.current_station:
            self.play(DEFAULT_STATIONS[0])
            return
        idx = 0
        for i, s in enumerate(DEFAULT_STATIONS):
            if s.id == self.current_station.id:
                idx = i
                break
        next_idx = (idx + 1) % len(DEFAULT_STATIONS)
        self.play(DEFAULT_STATIONS[next_idx])

    def prev_station(self):
        """Switches to the previous station in the catalogue."""
        if not DEFAULT_STATIONS:
            return
        if not self.current_station:
            self.play(DEFAULT_STATIONS[0])
            return
        idx = 0
        for i, s in enumerate(DEFAULT_STATIONS):
            if s.id == self.current_station.id:
                idx = i
                break
        prev_idx = (idx - 1) % len(DEFAULT_STATIONS)
        self.play(DEFAULT_STATIONS[prev_idx])

    def set_volume(self, vol: int):
        self.volume = max(0, min(100, vol))
        log.debug("Radio volume set to %d%%", self.volume)
        if self.is_playing and self.process:
            self._apply_pactl_volume(self.process.pid, self.volume)

    def _apply_pactl_volume(self, pid: int, vol_percent: int):
        """Adjusts PulseAudio / PipeWire volume of ffplay's sink-input dynamically."""
        try:
            out = subprocess.check_output(["pactl", "list", "sink-inputs"], text=True, stderr=subprocess.DEVNULL)
            current_input = None
            for line in out.splitlines():
                line_s = line.strip()
                if line_s.startswith("Sink Input #"):
                    current_input = line_s.replace("Sink Input #", "").strip()
                elif (f'application.process.id = "{pid}"' in line_s or 'application.name = "ffplay"' in line_s) and current_input:
                    subprocess.run(["pactl", "set-sink-input-volume", current_input, f"{vol_percent}%"],
                                   check=True, stderr=subprocess.DEVNULL)
                    log.debug("Adjusted pactl sink-input #%s to %d%%", current_input, vol_percent)
                    return True
        except Exception:
            pass
        return False


# ═════════════════════════════════════════════════════════════════
#  RADIO DIALOG WITH EMBEDDED AVS SPECTRUM & SCREENSAVER
# ═════════════════════════════════════════════════════════════════
class RadioDialog(QDialog):
    """
    Sovereign Cyberpunk Radio Player with Embedded Winamp AVS Spectrum Analyzer.
    Includes:
    - Fullscreen Screensaver Mode (F11 / Double-Click)
    - Winamp AVS Visualizer Studio
    - Category filtering & Search
    - Live play/pause/prev/next & responsive volume slider
    """
    def __init__(self, radio_player: RadioPlayer, parent=None):
        super().__init__(parent)
        self.player = radio_player
        self.setWindowTitle("📻 Киберпанк Радио // Sovereign Stream & AVS Visualizer")
        self.resize(720, 680)
        self.setMinimumSize(600, 560)

        self.setStyleSheet("""
            QDialog {
                background: #0d111a;
                border: 2px solid #00e5ff;
                border-radius: 10px;
            }
            QLabel {
                color: #e0e6ed;
                font-family: Monospace;
            }
            QListWidget {
                background: #090c13;
                color: #00ff88;
                border: 1px solid #1c2b3c;
                border-radius: 6px;
                font-family: Monospace;
                font-size: 11px;
                padding: 4px;
            }
            QListWidget::item {
                padding: 6px 8px;
                border-bottom: 1px solid #141b24;
            }
            QListWidget::item:selected {
                background: #003344;
                color: #00e5ff;
                font-weight: bold;
                border-left: 3px solid #00e5ff;
            }
            QPushButton {
                background: #141d28;
                color: #00e5ff;
                border: 1px solid #00aacc;
                border-radius: 4px;
                padding: 6px 14px;
                font-weight: bold;
                font-family: Monospace;
                font-size: 11px;
            }
            QPushButton:hover {
                background: #004455;
                color: #ffffff;
            }
            QComboBox {
                background: #141d28;
                color: #00e5ff;
                border: 1px solid #00aacc;
                border-radius: 4px;
                padding: 5px;
                font-family: Monospace;
                font-size: 11px;
            }
            QSlider::groove:horizontal {
                height: 6px;
                background: #1c2636;
                border-radius: 3px;
            }
            QSlider::sub-page:horizontal {
                background: #00e5ff;
                border-radius: 3px;
            }
            QSlider::handle:horizontal {
                background: #ffffff;
                width: 14px;
                margin-top: -4px;
                margin-bottom: -4px;
                border-radius: 7px;
            }
        """)

        # Connect player callback
        self._orig_callback = self.player.on_state_changed
        self.player.on_state_changed = self._on_player_state_changed

        self._build_ui()
        self._load_stations("ALL")

        # Shortcuts
        self.shortcut_fs = QShortcut(QKeySequence("F11"), self)
        self.shortcut_fs.activated.connect(self._toggle_fullscreen_screensaver)

        self.shortcut_space = QShortcut(QKeySequence("Space"), self)
        self.shortcut_space.activated.connect(self._toggle_play)

    def _build_ui(self):
        layout = QVBoxLayout(self)
        layout.setContentsMargins(14, 14, 14, 14)
        layout.setSpacing(10)

        # ─── Top Header Bar ───
        top_bar = QHBoxLayout()
        lbl_head = QLabel("📻 SOVEREIGN CYBER-RADIO // AVS VISUALIZER")
        lbl_head.setFont(QFont("Monospace", 12, QFont.Weight.Bold))
        lbl_head.setStyleSheet("color: #00e5ff;")
        top_bar.addWidget(lbl_head)
        top_bar.addStretch()

        btn_studio = QPushButton("🎨 AVS Студия")
        btn_studio.setToolTip("Открыть конструктор плагинов и пресетов Winamp AVS")
        btn_studio.clicked.connect(self._open_avs_studio)
        top_bar.addWidget(btn_studio)

        btn_fs = QPushButton("🔲 Заставка (F11)")
        btn_fs.setStyleSheet("background: #004455; color: #00e5ff; border: 1.5px solid #00e5ff;")
        btn_fs.setToolTip("Развернуть анализатор спектра во весь экран как заставку")
        btn_fs.clicked.connect(self._toggle_fullscreen_screensaver)
        top_bar.addWidget(btn_fs)

        layout.addLayout(top_bar)

        # ─── Center: Winamp AVS Canvas Visualizer ───
        self.avs_widget = AVSCanvasWidget(self)
        self.avs_widget.setFixedHeight(220)
        self.avs_widget.setStyleSheet("border: 1px solid #1c2b3c; border-radius: 6px;")
        layout.addWidget(self.avs_widget)

        # ─── Station Filter & Category Tabs ───
        filter_bar = QHBoxLayout()
        filter_bar.addWidget(QLabel("Категория:"))

        self.combo_cat = QComboBox()
        self.combo_cat.addItems([
            "🌐 Все каналы (ALL)",
            "⚡ Киберпанк / Электроника (ELECTRONIC)",
            "☕ Lo-Fi & Chillout (LOFI)",
            "🇷🇺 Русские радиостанции (RU)",
            "🌍 Мировые станции (WORLD)"
        ])
        self.combo_cat.currentIndexChanged.connect(self._on_category_changed)
        filter_bar.addWidget(self.combo_cat, 1)

        self.search_edit = QLineEdit()
        self.search_edit.setPlaceholderText("🔍 Поиск станции...")
        self.search_edit.setStyleSheet("""
            QLineEdit {
                background: #141d28; color: #ffffff; border: 1px solid #1c2b3c;
                border-radius: 4px; padding: 4px 8px; font-size: 11px;
            }
            QLineEdit:focus { border-color: #00e5ff; }
        """)
        self.search_edit.textChanged.connect(self._filter_stations_list)
        filter_bar.addWidget(self.search_edit, 1)

        layout.addLayout(filter_bar)

        # ─── Station List ───
        self.list_widget = QListWidget()
        self.list_widget.itemDoubleClicked.connect(self._play_selected)
        layout.addWidget(self.list_widget, 1)

        # ─── Status Ticker ───
        self.lbl_status = QLabel(self._get_status_text())
        self.lbl_status.setFont(QFont("Monospace", 9, QFont.Weight.Bold))
        self.lbl_status.setStyleSheet("color: #00ffaa; padding: 2px;")
        layout.addWidget(self.lbl_status)

        # ─── Volume Control Row ───
        vol_row = QHBoxLayout()
        vol_row.addWidget(QLabel("Громкость:"))

        self.slider_vol = QSlider(Qt.Orientation.Horizontal)
        self.slider_vol.setRange(0, 100)
        self.slider_vol.setValue(self.player.volume)
        self.slider_vol.valueChanged.connect(self._on_vol_changed)
        vol_row.addWidget(self.slider_vol, 1)

        self.lbl_vol_val = QLabel(f"{self.player.volume}%")
        self.lbl_vol_val.setFixedWidth(40)
        self.lbl_vol_val.setStyleSheet("color: #00ffaa; font-weight: bold;")
        vol_row.addWidget(self.lbl_vol_val)

        self.btn_mute = QPushButton("🔇 Mute")
        self.btn_mute.setFixedWidth(70)
        self.btn_mute.clicked.connect(self._toggle_mute)
        vol_row.addWidget(self.btn_mute)
        layout.addLayout(vol_row)

        # ─── Playback Control Buttons ───
        btn_row = QHBoxLayout()

        self.btn_prev = QPushButton("⏮ ПРЕД")
        self.btn_prev.setToolTip("Предыдущая станция")
        self.btn_prev.clicked.connect(self._on_prev)
        btn_row.addWidget(self.btn_prev)

        self.btn_play = QPushButton("▶ ИГРАТЬ")
        self.btn_play.setStyleSheet("background: #003318; border-color: #00ff66; color: #00ffaa; font-size: 12px;")
        self.btn_play.clicked.connect(self._toggle_play)
        btn_row.addWidget(self.btn_play)

        self.btn_stop = QPushButton("⏹ СТОП")
        self.btn_stop.setStyleSheet("background: #331518; border-color: #ff3366; color: #ff5577;")
        self.btn_stop.clicked.connect(self._stop_playback)
        btn_row.addWidget(self.btn_stop)

        self.btn_next = QPushButton("СЛЕД ⏭")
        self.btn_next.setToolTip("Следующая станция")
        self.btn_next.clicked.connect(self._on_next)
        btn_row.addWidget(self.btn_next)

        btn_close = QPushButton("ЗАКРЫТЬ")
        btn_close.setStyleSheet("background: #1c2230; color: #aaa; border-color: #555;")
        btn_close.clicked.connect(self.accept)
        btn_row.addWidget(btn_close)

        layout.addLayout(btn_row)

    def _get_status_text(self) -> str:
        if self.player.is_playing and self.player.current_station:
            return f"🟢 В ЭФИРЕ: {self.player.current_station.name}"
        return "⏹️ ЭФИР ОСТАНОВЛЕН (Выберите станцию и нажмите «Играть»)"

    def _on_category_changed(self, idx: int):
        cats = ["ALL", "ELECTRONIC", "LOFI", "RU", "WORLD"]
        cat = cats[idx] if idx < len(cats) else "ALL"
        self._load_stations(cat)

    def _filter_stations_list(self, text: str):
        search_txt = text.strip().lower()
        for i in range(self.list_widget.count()):
            item = self.list_widget.item(i)
            station: RadioStation = item.data(Qt.ItemDataRole.UserRole)
            if not search_txt or search_txt in station.name.lower() or search_txt in station.genre.lower():
                item.setHidden(False)
            else:
                item.setHidden(True)

    def _load_stations(self, cat_tag: str):
        self.list_widget.clear()
        stations = self.player.get_stations(cat_tag)
        for s in stations:
            item = QListWidgetItem(f"● {s.name}")
            item.setData(Qt.ItemDataRole.UserRole, s)
            self.list_widget.addItem(item)
            if self.player.current_station and self.player.current_station.id == s.id:
                item.setSelected(True)
        if self.list_widget.count() > 0 and not self.list_widget.currentItem():
            self.list_widget.setCurrentRow(0)

    def _play_selected(self):
        item = self.list_widget.currentItem()
        if not item and self.list_widget.count() > 0:
            item = self.list_widget.item(0)
            self.list_widget.setCurrentItem(item)
        if not item:
            return

        station: RadioStation = item.data(Qt.ItemDataRole.UserRole)
        self.player.play(station)
        self._update_ui_state()

    def _toggle_play(self):
        if self.player.is_playing:
            self._stop_playback()
        else:
            self._play_selected()

    def _stop_playback(self):
        self.player.stop()
        self._update_ui_state()

    def _on_next(self):
        self.player.next_station()
        self._sync_selected_item()
        self._update_ui_state()

    def _on_prev(self):
        self.player.prev_station()
        self._sync_selected_item()
        self._update_ui_state()

    def _sync_selected_item(self):
        if not self.player.current_station:
            return
        for i in range(self.list_widget.count()):
            item = self.list_widget.item(i)
            st = item.data(Qt.ItemDataRole.UserRole)
            if st and st.id == self.player.current_station.id:
                self.list_widget.setCurrentItem(item)
                break

    def _on_vol_changed(self, val: int):
        self.player.set_volume(val)
        self.lbl_vol_val.setText(f"{val}%")

    def _toggle_mute(self):
        if self.slider_vol.value() > 0:
            self._prev_vol = self.slider_vol.value()
            self.slider_vol.setValue(0)
            self.btn_mute.setText("🔊 Unmute")
        else:
            prev = getattr(self, "_prev_vol", 80)
            self.slider_vol.setValue(prev)
            self.btn_mute.setText("🔇 Mute")

    def _update_ui_state(self):
        st_text = self._get_status_text()
        self.lbl_status.setText(st_text)
        station_name = self.player.current_station.name if self.player.current_station else "MadGammon Radio"
        self.avs_widget.set_playing_state(self.player.is_playing, station_name)

        if self.player.is_playing:
            self.btn_play.setText("⏸ ПАУЗА")
            self.btn_play.setStyleSheet("background: #003344; border-color: #00e5ff; color: #00e5ff; font-size: 12px;")
        else:
            self.btn_play.setText("▶ ИГРАТЬ")
            self.btn_play.setStyleSheet("background: #003318; border-color: #00ff66; color: #00ffaa; font-size: 12px;")

    def _on_player_state_changed(self, is_playing: bool, station: Optional[RadioStation]):
        # Call original handler if attached (e.g. main window toolbar button)
        if self._orig_callback:
            try:
                self._orig_callback(is_playing, station)
            except Exception:
                pass
        # Update local UI via Qt thread safe timer
        QTimer.singleShot(0, self._update_ui_state)

    def _toggle_fullscreen_screensaver(self):
        self.avs_widget.toggle_fullscreen_screensaver()

    def _open_avs_studio(self):
        dlg = AVSPresetEditorDialog(self.avs_widget, self)
        dlg.exec()

    def closeEvent(self, event):
        # Restore original callback
        self.player.on_state_changed = self._orig_callback
        super().closeEvent(event)
