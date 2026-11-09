"""
MadGammon Internet Radio Player — Enhanced Live Channels Edition
Stream live audio channels matching the Android RadioManager catalogue.
Expanded with DI.FM / Digitally Imported, Electronic, Synthwave, Cyberpunk, Ambient, Rock & World Streams.
Uses lightweight background ffplay process with PulseAudio / PipeWire integration and responsive volume control.
"""
import subprocess
import threading
import os
import signal
from typing import List, Optional, Callable
from dataclasses import dataclass
from logger import get_logger

log = get_logger("madgammon.radio")

@dataclass
class RadioStation:
    id: str
    name: str
    url: str
    category: str = "ALL"
    genre: str = ""

DEFAULT_STATIONS: List[RadioStation] = [
    # ─── CYBERPUNK / ELECTRONIC / SYNTHWAVE / DI.FM ───
    RadioStation("elec_synthwave", "⚡ Nightwave Plaza (Synthwave / Vaporwave)", "https://plaza.one/mp3", "ELECTRONIC", "Vaporwave"),
    RadioStation("elec_di_chill", "🎧 DI.FM Chillout Dreams (Ambient Beats)", "https://prem1.di.fm:443/chilloutdreams_hi", "ELECTRONIC", "Chillout"),
    RadioStation("elec_di_progressive", "🎧 DI.FM Progressive House (Club Mix)", "https://prem1.di.fm:443/progressive_hi", "ELECTRONIC", "House"),
    RadioStation("elec_di_trance", "🎧 DI.FM Vocal Trance (Uplifting)", "https://prem1.di.fm:443/vocaltrance_hi", "ELECTRONIC", "Trance"),
    RadioStation("elec_di_dnb", "🎧 DI.FM Drum & Bass (Neurofunk & Liquid)", "https://prem1.di.fm:443/drumandbass_hi", "ELECTRONIC", "D&B"),
    RadioStation("elec_di_psytrance", "🎧 DI.FM Psychedelic Trance (Goa/Psy)", "https://prem1.di.fm:443/goapsy_hi", "ELECTRONIC", "Psytrance"),
    RadioStation("elec_cyberfm", "⚡ Cyberpunk 2077 Radio (Darksynth / Industrial)", "https://ice1.somafm.com/cliqhop-128-mp3", "ELECTRONIC", "Industrial"),
    RadioStation("elec_groove", "🌊 SomaFM Groove Salad (Downtempo / Ambient)", "https://ice1.somafm.com/groovesalad-128-mp3", "ELECTRONIC", "Ambient"),
    RadioStation("elec_defcon", "🛡️ SomaFM DEF CON Radio (Hacker Beats)", "https://ice1.somafm.com/defcon-128-mp3", "ELECTRONIC", "Breakbeat"),
    RadioStation("elec_deepspace", "🚀 SomaFM Deep Space One (Deep Ambient)", "https://ice1.somafm.com/deepspaceone-128-mp3", "ELECTRONIC", "Space"),
    RadioStation("elec_dronezone", "🌌 SomaFM Drone Zone (Atmospheric)", "https://ice1.somafm.com/dronezone-128-mp3", "ELECTRONIC", "Drone"),

    # ─── LO-FI & CHILLOUT ───
    RadioStation("lofi_chill", "☕ Lofi Girl Stream (Beats to Relax/Play)", "https://streams.ilovemusic.de/iloveradio17.mp3", "LOFI", "Lo-Fi"),
    RadioStation("lofi_secret", "🕶️ SomaFM Secret Agent (Spy Jazz / Chill)", "https://ice1.somafm.com/secretagent-128-mp3", "LOFI", "Lounge"),
    RadioStation("lofi_cafecod", "☕ SomaFM Cafe del Mar / Beat Blender", "https://ice1.somafm.com/beatblender-128-mp3", "LOFI", "Downtempo"),

    # ─── RUSSIAN CHANNELS (RU) ───
    RadioStation("ru_record", "🇷🇺 [RU] Радио Рекорд (EDM / Dance)", "https://online.radiorecord.ru:8055/rr_128", "RU", "Dance"),
    RadioStation("ru_record_chill", "🇷🇺 [RU] Record Chill-Out (Релакс)", "https://online.radiorecord.ru:8055/chil_128", "RU", "Chillout"),
    RadioStation("ru_record_synth", "🇷🇺 [RU] Record Synthwave (Ретровейв)", "https://online.radiorecord.ru:8055/synth_128", "RU", "Synthwave"),
    RadioStation("ru_ep", "🇷🇺 [RU] Европа Плюс (Top 40 Hits)", "https://europaplus.hostingradio.ru:8014/ep128", "RU", "Pop"),
    RadioStation("ru_rusradio", "🇷🇺 [RU] Русское Радио (Хиты)", "https://rusradio.hostingradio.ru/rusradio128.mp3", "RU", "Hits"),
    RadioStation("ru_jazz", "🇷🇺 [RU] Радио Джаз (Smooth Jazz)", "https://jazzradio.hostingradio.ru/jazzradio128.mp3", "RU", "Jazz"),
    RadioStation("ru_dacha", "🇷🇺 [RU] Радио Дача", "https://dacha.hostingradio.ru/dacha128.mp3", "RU", "Pop"),
    RadioStation("ru_dorognoe", "🇷🇺 [RU] Дорожное Радио", "https://dorognoe.hostingradio.ru:8000/dorognoe128.mp3", "RU", "Retro"),
    RadioStation("ru_chanson", "🇷🇺 [RU] Радио Шансон", "https://chanson.hostingradio.ru:8041/chanson128.mp3", "RU", "Chanson"),
    RadioStation("ru_nashe", "🇷🇺 [RU] Наше Радио (Русский Рок)", "https://nashe1.hostingradio.ru/nashe-128.mp3", "RU", "Rock"),

    # ─── WORLD / INTERNATIONAL (EN / ES / DE / FR) ───
    RadioStation("en_bbcws", "🇬🇧 [EN] BBC World Service", "https://stream.live.vc.bbcmedia.co.uk/bbc_world_service", "WORLD", "News"),
    RadioStation("en_classic", "🇬🇧 [EN] Classic FM (Симфоническая классика)", "https://stream.global.com/classicfmmp3", "WORLD", "Classical"),
    RadioStation("es_ibiza", "🇪🇸 [ES] Ibiza Global Radio (Ibiza Club)", "https://live.ibizaglobalradio.com/stream", "WORLD", "Deep House"),
    RadioStation("es_los40", "🇪🇸 [ES] Los 40 Principales (Latin Pop)", "https://rstream.los40.com/los40.mp3", "WORLD", "Latin"),
    RadioStation("de_1live", "🇩🇪 [DE] 1LIVE WDR (Германия)", "https://wdr-1live-live.icecast.wdr.de/wdr/1live/live/mp3/128/stream.mp3", "WORLD", "Pop/Rock"),
    RadioStation("de_antenne", "🇩🇪 [DE] Antenne Bayern (Бавария)", "https://webradio.antenne.de/antenne", "WORLD", "Pop"),
    RadioStation("fr_fip", "🇫🇷 [FR] FIP Radio France (Эклектика/Джаз)", "https://stream.radiofrance.fr/fip/fip.mp3", "WORLD", "Eclectic"),
]

class RadioPlayer:
    """Controls stream playback using system ffplay."""
    def __init__(self, on_state_changed: Optional[Callable[[bool, Optional[RadioStation]], None]] = None):
        self.on_state_changed = on_state_changed
        self.process: Optional[subprocess.Popen] = None
        self.current_station: Optional[RadioStation] = None
        self.volume: int = 80  # 0..100
        self.is_playing = False
        self._lock = threading.Lock()

    def get_stations(self, cat_filter: str = "ALL") -> List[RadioStation]:
        if cat_filter == "ALL":
            return DEFAULT_STATIONS
        return [s for s in DEFAULT_STATIONS if s.category == cat_filter]

    def play(self, station: RadioStation):
        with self._lock:
            self.stop()
            self.current_station = station
            log.info("Starting radio playback: %s (%s)", station.name, station.url)

            cmd = [
                "ffplay",
                "-nodisp",
                "-nostats",
                "-autoexit",
                "-loglevel", "quiet",
                "-volume", str(self.volume),
                station.url
            ]

            try:
                self.process = subprocess.Popen(
                    cmd,
                    stdout=subprocess.DEVNULL,
                    stderr=subprocess.DEVNULL,
                    preexec_fn=os.setsid
                )
                self.is_playing = True
                if self.on_state_changed:
                    self.on_state_changed(True, self.current_station)
                log.info("ffplay spawned with PID %d", self.process.pid)
            except Exception as e:
                log.exception("Failed to spawn ffplay for radio: %s", e)
                self.is_playing = False
                if self.on_state_changed:
                    self.on_state_changed(False, None)

    def stop(self):
        if self.process:
            log.info("Stopping radio playback (PID %d)", self.process.pid)
            try:
                os.killpg(os.getpgid(self.process.pid), signal.SIGTERM)
                self.process.wait(timeout=0.6)
            except Exception:
                try:
                    os.killpg(os.getpgid(self.process.pid), signal.SIGKILL)
                except Exception:
                    pass
            self.process = None

        self.is_playing = False
        if self.on_state_changed:
            self.on_state_changed(False, self.current_station)

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


# ═══════════════════════════════════════════════════════
#  RADIO DIALOG (Futuristic Cyberpunk Player UI)
# ═══════════════════════════════════════════════════════
from PyQt6.QtWidgets import (
    QDialog, QVBoxLayout, QHBoxLayout, QLabel, QPushButton,
    QListWidget, QListWidgetItem, QComboBox, QSlider, QFrame
)
from PyQt6.QtGui import QFont, QColor, QPainter, QLinearGradient
from PyQt6.QtCore import Qt, QTimer, QRectF

class RadioDialog(QDialog):
    def __init__(self, radio_player: RadioPlayer, parent=None):
        super().__init__(parent)
        self.player = radio_player
        self.setWindowTitle("📻 Киберпанк Радио // Sovereign Audio Stream")
        self.resize(540, 560)
        self.setMinimumSize(480, 500)

        self.setStyleSheet("""
            QDialog {
                background: #10141d;
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
                padding: 6px;
            }
            QListWidget::item {
                padding: 5px 8px;
                border-bottom: 1px solid #141b24;
            }
            QListWidget::item:selected {
                background: #003322;
                color: #00ffaa;
                border: 1px solid #00ff88;
                border-radius: 4px;
            }
            QComboBox {
                background: #182030;
                color: #00e5ff;
                border: 1.5px solid #00e5ff;
                border-radius: 4px;
                padding: 5px 10px;
                font-family: Monospace;
                font-weight: bold;
                font-size: 11px;
            }
            QPushButton {
                background: #182030;
                color: #00ff88;
                border: 1.5px solid #00ff88;
                padding: 8px 14px;
                border-radius: 5px;
                font-weight: bold;
                font-family: Monospace;
                font-size: 11px;
            }
            QPushButton:hover {
                background: #004422;
                border-color: #00ffbb;
                color: #ffffff;
            }
            QSlider::groove:horizontal {
                height: 6px;
                background: #151d28;
                border-radius: 3px;
                border: 1px solid #203040;
            }
            QSlider::sub-page:horizontal {
                background: qlineargradient(x1:0, y1:0, x2:1, y2:0, stop:0 #00aa66, stop:1 #00ffaa);
                border-radius: 3px;
            }
            QSlider::handle:horizontal {
                background: #00e5ff;
                border: 2px solid #ffffff;
                width: 16px;
                margin-top: -5px;
                margin-bottom: -5px;
                border-radius: 8px;
            }
        """)

        self._build_ui()
        self._load_stations("ALL")

        # Visual spectrum pulse timer
        self._pulse_timer = QTimer(self)
        self._pulse_timer.setInterval(100)
        self._pulse_timer.timeout.connect(self._update_spectrum)
        self._pulse_timer.start()

    def _build_ui(self):
        layout = QVBoxLayout(self)
        layout.setSpacing(10)
        layout.setContentsMargins(18, 18, 18, 18)

        # Header HUD
        head_box = QFrame()
        head_box.setStyleSheet("background: #151b27; border: 1px solid #00e5ff; border-radius: 6px; padding: 6px;")
        hb_layout = QHBoxLayout(head_box)
        hb_layout.setContentsMargins(8, 4, 8, 4)

        lbl_head = QLabel("📻 КИБЕРПАНК СТРИМ-РАДИО // DI.FM & SOMA")
        lbl_head.setFont(QFont("Monospace", 11, QFont.Weight.Black))
        lbl_head.setStyleSheet("color: #00e5ff; letter-spacing: 1px;")
        hb_layout.addWidget(lbl_head)

        hb_layout.addStretch()

        self.lbl_vumeter = QLabel("||||||||||||||||")
        self.lbl_vumeter.setFont(QFont("Monospace", 9, QFont.Weight.Bold))
        self.lbl_vumeter.setStyleSheet("color: #00ff88;")
        hb_layout.addWidget(self.lbl_vumeter)

        layout.addWidget(head_box)

        # Category Filter Row
        f_row = QHBoxLayout()
        lbl_cat = QLabel("Жанр / Категория:")
        lbl_cat.setFont(QFont("Monospace", 9, QFont.Weight.Bold))
        lbl_cat.setStyleSheet("color: #ffd54f;")
        f_row.addWidget(lbl_cat)

        self.combo_lang = QComboBox()
        self.combo_lang.addItems([
            "ALL (Все 30+ каналов)",
            "ELECTRONIC (DI.FM / Synthwave / Trance)",
            "LOFI (Lofi Girl / Secret Agent / Chill)",
            "RU (Рекорд / Джаз / Рок / Поп)",
            "WORLD (BBC / Ibiza / Европа)"
        ])
        self.combo_lang.currentIndexChanged.connect(self._on_category_changed)
        f_row.addWidget(self.combo_lang, 1)
        layout.addLayout(f_row)

        # Station List
        self.list_widget = QListWidget()
        self.list_widget.itemDoubleClicked.connect(self._play_selected)
        layout.addWidget(self.list_widget, 1)

        # Status text card
        stat_card = QFrame()
        stat_card.setStyleSheet("background: #0d121b; border: 1px solid #1f2e42; border-radius: 6px; padding: 6px;")
        sc_layout = QHBoxLayout(stat_card)
        self.lbl_status = QLabel(self._get_status_text())
        self.lbl_status.setStyleSheet("color: #00e5ff; font-weight: bold; font-size: 11px;")
        sc_layout.addWidget(self.lbl_status)
        layout.addWidget(stat_card)

        # Volume slider & mute
        vol_row = QHBoxLayout()
        lbl_vol = QLabel("🔊 Громкость:")
        lbl_vol.setFont(QFont("Monospace", 9, QFont.Weight.Bold))
        vol_row.addWidget(lbl_vol)

        self.slider_vol = QSlider(Qt.Orientation.Horizontal)
        self.slider_vol.setRange(0, 100)
        self.slider_vol.setValue(self.player.volume)
        self.slider_vol.valueChanged.connect(self._on_vol_changed)
        vol_row.addWidget(self.slider_vol, 1)

        self.lbl_vol_val = QLabel(f"{self.player.volume}%")
        self.lbl_vol_val.setFixedWidth(40)
        self.lbl_vol_val.setStyleSheet("color: #00ffaa; font-weight: bold;")
        vol_row.addWidget(self.lbl_vol_val)

        btn_mute = QPushButton("🔇 Mute")
        btn_mute.setFixedWidth(75)
        btn_mute.clicked.connect(self._toggle_mute)
        vol_row.addWidget(btn_mute)
        layout.addLayout(vol_row)

        # Control Buttons Row
        btn_row = QHBoxLayout()

        self.btn_prev = QPushButton("⏮ ПРЕД")
        self.btn_prev.setToolTip("Предыдущая станция в списке")
        self.btn_prev.clicked.connect(self._on_prev)
        btn_row.addWidget(self.btn_prev)

        self.btn_play = QPushButton("▶ ИГРАТЬ")
        self.btn_play.setStyleSheet("background: #003318; border-color: #00ff66; color: #00ffaa;")
        self.btn_play.clicked.connect(self._play_selected)
        btn_row.addWidget(self.btn_play)

        self.btn_stop = QPushButton("⏹ СТОП")
        self.btn_stop.setStyleSheet("background: #331518; border-color: #ff3366; color: #ff5577;")
        self.btn_stop.clicked.connect(self._stop_playback)
        btn_row.addWidget(self.btn_stop)

        self.btn_next = QPushButton("СЛЕД ⏭")
        self.btn_next.setToolTip("Следующая станция в списке")
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

    def _load_stations(self, cat_tag: str):
        self.list_widget.clear()
        stations = self.player.get_stations(cat_tag)
        for s in stations:
            item = QListWidgetItem(f"● {s.name}")
            item.setData(Qt.ItemDataRole.UserRole, s)
            self.list_widget.addItem(item)
            if self.player.current_station and self.player.current_station.id == s.id:
                item.setSelected(True)

    def _play_selected(self):
        item = self.list_widget.currentItem()
        if not item:
            if self.list_widget.count() > 0:
                item = self.list_widget.item(0)
                self.list_widget.setCurrentItem(item)
            else:
                return
        station: RadioStation = item.data(Qt.ItemDataRole.UserRole)
        self.player.play(station)
        self.lbl_status.setText(self._get_status_text())

    def _stop_playback(self):
        self.player.stop()
        self.lbl_status.setText(self._get_status_text())

    def _on_next(self):
        self.player.next_station()
        self._sync_selected_item()
        self.lbl_status.setText(self._get_status_text())

    def _on_prev(self):
        self.player.prev_station()
        self._sync_selected_item()
        self.lbl_status.setText(self._get_status_text())

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
        else:
            self.slider_vol.setValue(getattr(self, '_prev_vol', 80))

    def _update_spectrum(self):
        if self.player.is_playing:
            import random
            bars = ["|", "/", "-", "\\"]
            count = random.randint(8, 16)
            self.lbl_vumeter.setText("".join(random.choice(bars) for _ in range(count)))
            self.lbl_vumeter.setStyleSheet("color: #00ffaa;")
        else:
            self.lbl_vumeter.setText("[OFFLINE]")
            self.lbl_vumeter.setStyleSheet("color: #667788;")
