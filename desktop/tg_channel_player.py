"""
MadGammon Telegram Channel Audio Player & Bot Streamer
Стриминг и парсинг аудиоконтента из Telegram каналов.
"""

import os
import re
import json
import time
import subprocess
import threading
import signal
import atexit
from datetime import datetime
from dataclasses import dataclass, field, asdict
from typing import List, Optional, Dict, Callable
from urllib.request import Request, urlopen
from urllib.error import URLError, HTTPError
from urllib.parse import quote

from PyQt6.QtWidgets import (
    QDialog, QVBoxLayout, QHBoxLayout, QLabel, QPushButton,
    QListWidget, QListWidgetItem, QComboBox, QSlider, QFrame,
    QLineEdit, QSplitter, QApplication, QProgressBar, QMessageBox,
    QCheckBox, QGroupBox, QTabWidget, QWidget, QInputDialog
)
from PyQt6.QtGui import QFont, QColor
from PyQt6.QtCore import Qt, QTimer, pyqtSignal, QThread, QObject

from logger import get_logger

log = get_logger("madgammon.tg_channel")

# ─── Data directory ───
def _get_data_dirs() -> tuple[str, list[str]]:
    home_dir = os.path.join(os.path.expanduser("~"), ".madgammon", "tg_channels")
    local_dir = os.path.join(os.path.dirname(os.path.dirname(os.path.abspath(__file__))), ".madgammon_data", "tg_channels")
    
    # Try writable directory
    writable = local_dir
    try:
        os.makedirs(home_dir, exist_ok=True)
        test = os.path.join(home_dir, ".test")
        with open(test, "w") as f:
            f.write("1")
        os.remove(test)
        writable = home_dir
    except Exception:
        os.makedirs(local_dir, exist_ok=True)
        writable = local_dir

    search_dirs = [writable]
    if home_dir not in search_dirs:
        search_dirs.append(home_dir)
    if local_dir not in search_dirs:
        search_dirs.append(local_dir)
    return writable, search_dirs

TG_DATA_DIR, TG_SEARCH_DIRS = _get_data_dirs()
TG_PLAYLISTS_FILE = os.path.join(TG_DATA_DIR, "playlists.json")
TG_CHANNELS_CACHE_DIR = os.path.join(TG_DATA_DIR, "cache")
TG_AUDIO_CACHE_DIR = os.path.join(TG_DATA_DIR, "audio_cache")
BOT_CONFIG_FILE = os.path.join(TG_DATA_DIR, "bot_config.json")

USER_AGENT = "Mozilla/5.0 (X11; Linux x86_64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36"

# Default configuration placeholders (actual credentials saved in ~/.madgammon/tg_channels/bot_config.json)
DEFAULT_BOT_TOKEN = ""
DEFAULT_USER_ID = 0


@dataclass
class TgAudioTrack:
    channel: str           # e.g., "radioarmageddonfm"
    post_id: int           # Telegram post ID
    title: str = ""        # Track title
    performer: str = ""    # Artist
    duration: int = 0      # Duration in seconds
    date: str = ""         # Post date ISO format
    file_id: str = ""      # Telegram file_id
    file_url: str = ""     # Direct stream URL (from getFile)
    local_path: str = ""   # Local cached file path
    mime_type: str = "audio/mpeg"
    file_size: int = 0     # bytes

    @property
    def display_name(self) -> str:
        if self.performer and self.title:
            return f"{self.performer} — {self.title}"
        if self.title:
            return self.title
        return f"Track #{self.post_id} ({self.date[:10] if self.date else 'Unknown'})"

    @property
    def duration_str(self) -> str:
        if self.duration <= 0:
            return ""
        m, s = divmod(self.duration, 60)
        return f"{m}:{s:02d}"

    def to_dict(self) -> dict:
        return asdict(self)

    @classmethod
    def from_dict(cls, d: dict) -> 'TgAudioTrack':
        return cls(**{k: v for k, v in d.items() if k in cls.__dataclass_fields__})


@dataclass
class TgPlaylist:
    name: str
    tracks: List[TgAudioTrack] = field(default_factory=list)
    created: str = ""
    channel: str = ""

    def to_dict(self) -> dict:
        return {
            "name": self.name,
            "channel": self.channel,
            "created": self.created,
            "tracks": [t.to_dict() for t in self.tracks],
        }

    @classmethod
    def from_dict(cls, d: dict) -> 'TgPlaylist':
        tracks = [TgAudioTrack.from_dict(t) for t in d.get("tracks", [])]
        return cls(
            name=d.get("name", "Untitled"),
            tracks=tracks,
            created=d.get("created", ""),
            channel=d.get("channel", ""),
        )


# ═══════════════════════════════════════════════════════════════════
#  TELEGRAM BOT API ENGINE
# ═══════════════════════════════════════════════════════════════════
class TelegramBotEngine:
    """Handles communication with Telegram Bot API for media extraction and streaming."""

    def __init__(self, token: str = "", user_id: int = 0):
        self.token = token or DEFAULT_BOT_TOKEN
        self.user_id = user_id or DEFAULT_USER_ID
        self._load_config()

    def _load_config(self):
        for d in TG_SEARCH_DIRS:
            p = os.path.join(d, "bot_config.json")
            try:
                if os.path.exists(p):
                    with open(p) as f:
                        data = json.load(f)
                    tok = data.get("bot_token")
                    uid = data.get("user_id")
                    if tok:
                        self.token = tok
                    if uid:
                        self.user_id = uid
                    if self.token and self.user_id:
                        break
            except Exception:
                pass

    def save_config(self):
        try:
            os.makedirs(os.path.dirname(BOT_CONFIG_FILE), exist_ok=True)
            with open(BOT_CONFIG_FILE, "w") as f:
                json.dump({"bot_token": self.token, "user_id": self.user_id}, f, indent=2)
        except Exception as e:
            log.warning("Failed to save bot config: %s", e)

    def _api_call(self, method: str, params: Optional[dict] = None, timeout: float = 12.0) -> Optional[dict]:
        if not self.token:
            return None
        url = f"https://api.telegram.org/bot{self.token}/{method}"
        if params:
            query = "&".join(f"{k}={quote(str(v))}" for k, v in params.items())
            url = f"{url}?{query}"
        req = Request(url, headers={"User-Agent": USER_AGENT})
        try:
            with urlopen(req, timeout=timeout) as resp:
                data = json.loads(resp.read().decode("utf-8"))
                if data.get("ok"):
                    return data.get("result")
                return None
        except Exception as e:
            log.debug("Bot API error on %s: %s", method, e)
            return None

    def fetch_track_metadata(self, channel: str, post_id: int) -> Optional[TgAudioTrack]:
        """
        Extracts audio metadata from a channel post by forwarding to user and immediately deleting.
        """
        ch_name = channel if channel.startswith("@") else f"@{channel}"
        res = self._api_call("forwardMessage", {
            "chat_id": self.user_id,
            "from_chat_id": ch_name,
            "message_id": post_id
        })
        if not res:
            return None

        msg_id = res.get("message_id")
        audio = res.get("audio") or res.get("document") or res.get("voice")
        date_raw = res.get("date")
        date_str = datetime.fromtimestamp(date_raw).isoformat() if date_raw else ""

        track = None
        if audio:
            title = audio.get("title") or audio.get("file_name") or f"Track #{post_id}"
            performer = audio.get("performer") or ""
            duration = audio.get("duration", 0)
            file_id = audio.get("file_id", "")
            file_size = audio.get("file_size", 0)
            mime_type = audio.get("mime_type", "audio/mpeg")

            track = TgAudioTrack(
                channel=channel.lstrip("@"),
                post_id=post_id,
                title=title,
                performer=performer,
                duration=duration,
                date=date_str,
                file_id=file_id,
                file_size=file_size,
                mime_type=mime_type
            )

        # Immediate cleanup in user chat
        if msg_id:
            try:
                self._api_call("deleteMessage", {"chat_id": self.user_id, "message_id": msg_id})
            except Exception:
                pass

        return track

    def get_direct_stream_url(self, file_id: str) -> Optional[str]:
        """Resolves file_id into direct streaming URL via getFile."""
        res = self._api_call("getFile", {"file_id": file_id})
        if res and res.get("file_path"):
            return f"https://api.telegram.org/file/bot{self.token}/{res['file_path']}"
        return None

    def resolve_playable_source(self, track: TgAudioTrack) -> Optional[str]:
        """
        Robustly resolves an audio track to a playable stream URL or local file path:
        1. Checks local cache file
        2. Tries existing file_id via getFile
        3. If expired/invalid, re-fetches metadata via forwardMessage to get fresh file_id
        4. Starts background download to local cache
        """
        # 1. Check local cache
        safe_name = f"{track.channel}_{track.post_id}.mp3"
        local_target = os.path.join(TG_AUDIO_CACHE_DIR, safe_name)
        if os.path.exists(local_target) and os.path.getsize(local_target) > 10000:
            track.local_path = local_target
            return local_target

        url = None
        # 2. Try current file_id
        if track.file_id:
            url = self.get_direct_stream_url(track.file_id)

        # 3. If failed, re-fetch metadata from channel post
        if not url:
            log.info("Re-fetching fresh metadata for %s / post %d...", track.channel, track.post_id)
            fresh = self.fetch_track_metadata(track.channel, track.post_id)
            if fresh and fresh.file_id:
                track.file_id = fresh.file_id
                if not track.title or track.title.startswith("Track #"):
                    track.title = fresh.title
                if fresh.performer:
                    track.performer = fresh.performer
                url = self.get_direct_stream_url(fresh.file_id)

        if url:
            track.file_url = url
            # Start background caching
            threading.Thread(
                target=self._cache_audio_file,
                args=(url, local_target, track),
                daemon=True,
                name=f"CacheAudio-{track.post_id}"
            ).start()
            return url

        return None

    def _cache_audio_file(self, url: str, target_path: str, track: TgAudioTrack):
        """Background worker to download audio file into cache."""
        try:
            os.makedirs(os.path.dirname(target_path), exist_ok=True)
            temp_path = f"{target_path}.part"
            req = Request(url, headers={"User-Agent": USER_AGENT})
            with urlopen(req, timeout=30) as resp:
                with open(temp_path, "wb") as f:
                    while True:
                        chunk = resp.read(16384)
                        if not chunk:
                            break
                        f.write(chunk)
            if os.path.exists(temp_path) and os.path.getsize(temp_path) > 10000:
                os.replace(temp_path, target_path)
                track.local_path = target_path
                log.info("Cached audio track to %s (%d bytes)", target_path, os.path.getsize(target_path))
        except Exception as e:
            log.debug("Background audio cache failed for %s: %s", target_path, e)
            try:
                if os.path.exists(temp_path):
                    os.remove(temp_path)
            except Exception:
                pass


# ═══════════════════════════════════════════════════════════════════
#  PLAYLIST & CACHE MANAGER
# ═══════════════════════════════════════════════════════════════════
class TgPlaylistManager:
    def __init__(self):
        os.makedirs(TG_DATA_DIR, exist_ok=True)
        os.makedirs(TG_CHANNELS_CACHE_DIR, exist_ok=True)
        self.playlists: List[TgPlaylist] = []
        self.channels: List[str] = []
        self._load()

    def _load(self):
        try:
            if os.path.exists(TG_PLAYLISTS_FILE):
                with open(TG_PLAYLISTS_FILE, "r") as f:
                    data = json.load(f)
                self.playlists = [TgPlaylist.from_dict(p) for p in data.get("playlists", [])]
                self.channels = data.get("channels", [])
        except Exception as e:
            log.warning("Playlist load failed: %s", e)

        if not self.channels:
            self.channels = ["RadioArmageddonFM"]

    def save(self):
        try:
            os.makedirs(TG_DATA_DIR, exist_ok=True)
            data = {
                "channels": self.channels,
                "playlists": [p.to_dict() for p in self.playlists],
            }
            with open(TG_PLAYLISTS_FILE, "w") as f:
                json.dump(data, f, ensure_ascii=False, indent=2)
        except Exception as e:
            log.warning("Playlist save failed: %s", e)

    def add_channel(self, channel: str):
        ch = channel.lstrip("@").strip()
        if ch and ch not in self.channels:
            self.channels.append(ch)
            self.save()

    def remove_channel(self, channel: str):
        ch = channel.lstrip("@").strip()
        if ch in self.channels:
            self.channels.remove(ch)
            self.save()

    def create_playlist(self, name: str, tracks: List[TgAudioTrack], channel: str = "") -> TgPlaylist:
        pl = TgPlaylist(name=name, tracks=tracks, created=datetime.now().isoformat(), channel=channel)
        self.playlists.append(pl)
        self.save()
        return pl

    def delete_playlist(self, index: int):
        if 0 <= index < len(self.playlists):
            self.playlists.pop(index)
            self.save()

    def get_channel_cache(self, channel: str) -> List[TgAudioTrack]:
        for d in TG_SEARCH_DIRS:
            cache_file = os.path.join(d, "cache", f"{channel.lower()}.json")
            try:
                if os.path.exists(cache_file):
                    with open(cache_file) as f:
                        data = json.load(f)
                    tracks = [TgAudioTrack.from_dict(t) for t in data.get("tracks", [])]
                    if tracks:
                        return tracks
            except Exception:
                pass
        return []

    def save_channel_cache(self, channel: str, tracks: List[TgAudioTrack]):
        cache_file = os.path.join(TG_CHANNELS_CACHE_DIR, f"{channel.lower()}.json")
        try:
            os.makedirs(TG_CHANNELS_CACHE_DIR, exist_ok=True)
            with open(cache_file, "w") as f:
                json.dump({
                    "channel": channel,
                    "updated_at": datetime.now().isoformat(),
                    "tracks": [t.to_dict() for t in tracks]
                }, f, ensure_ascii=False, indent=2)
        except Exception as e:
            log.warning("Cache save failed: %s", e)


# ═══════════════════════════════════════════════════════════════════
#  TRACK PLAYER (ffplay streaming engine)
# ═══════════════════════════════════════════════════════════════════
class TgTrackPlayer:
    """Plays audio streams via ffplay with auto-reconnect and watchdog."""

    def __init__(self, bot_engine: TelegramBotEngine, on_track_changed: Optional[Callable[[TgAudioTrack, bool], None]] = None):
        self.bot_engine = bot_engine
        self.on_track_changed = on_track_changed
        self.process: Optional[subprocess.Popen] = None
        self.current_track: Optional[TgAudioTrack] = None
        self.current_playlist: List[TgAudioTrack] = []
        self.playlist_index: int = -1
        self.is_playing = False
        self.volume: int = 80
        self.repeat: bool = False
        self._lock = threading.Lock()
        self._stop_requested = False
        atexit.register(self._stop_process)

    def set_playlist(self, tracks: List[TgAudioTrack], start_index: int = 0):
        self.current_playlist = list(tracks)
        self.playlist_index = start_index

    def play_track(self, track: TgAudioTrack):
        with self._lock:
            self._stop_process()
            self._stop_requested = False
            self.current_track = track

            # Resolve streaming URL if missing or expired
            url = track.file_url or track.local_path
            if not url and track.file_id:
                log.info("Resolving live streaming URL for %s...", track.display_name)
                url = self.bot_engine.get_direct_stream_url(track.file_id)
                track.file_url = url or ""

            if not url:
                log.warning("No direct stream URL available for %s", track.display_name)
                return

            log.info("Streaming track via ffplay: %s", track.display_name)
            cmd = [
                "ffplay",
                "-nodisp",
                "-nostats",
                "-loglevel", "warning",
                "-autoexit",
                "-volume", str(self.volume),
                url
            ]
            if url.startswith("http"):
                cmd.extend(["-user_agent", USER_AGENT, "-reconnect", "1", "-reconnect_delay_max", "5"])

            try:
                self.process = subprocess.Popen(
                    cmd,
                    stdin=subprocess.DEVNULL,
                    stdout=subprocess.DEVNULL,
                    stderr=subprocess.DEVNULL,
                    preexec_fn=os.setsid
                )
                self.is_playing = True

                t = threading.Thread(
                    target=self._watchdog, args=(self.process,),
                    daemon=True, name=f"TgTrackWatchdog-{self.process.pid}"
                )
                t.start()

                if self.on_track_changed:
                    self.on_track_changed(track, True)

            except Exception as e:
                log.exception("ffplay spawn failed: %s", e)
                self.is_playing = False

    def _watchdog(self, proc: subprocess.Popen):
        proc.wait()
        with self._lock:
            if not self._stop_requested and self.process == proc:
                self.is_playing = False
                # Auto advance to next track
                if self.current_playlist and self.playlist_index >= 0:
                    next_idx = self.playlist_index + 1
                    if next_idx < len(self.current_playlist):
                        self.playlist_index = next_idx
                        threading.Thread(target=self.play_track, args=(self.current_playlist[next_idx],), daemon=True).start()
                    elif self.repeat and self.current_playlist:
                        self.playlist_index = 0
                        threading.Thread(target=self.play_track, args=(self.current_playlist[0],), daemon=True).start()
                    else:
                        if self.on_track_changed:
                            self.on_track_changed(self.current_track, False)

    def play_index(self, index: int):
        if 0 <= index < len(self.current_playlist):
            self.playlist_index = index
            self.play_track(self.current_playlist[index])

    def next_track(self):
        if not self.current_playlist:
            return
        next_idx = (self.playlist_index + 1) % len(self.current_playlist)
        self.play_index(next_idx)

    def prev_track(self):
        if not self.current_playlist:
            return
        prev_idx = (self.playlist_index - 1) % len(self.current_playlist)
        self.play_index(prev_idx)

    def stop(self):
        with self._lock:
            self._stop_process()
            self.is_playing = False
            if self.on_track_changed and self.current_track:
                self.on_track_changed(self.current_track, False)

    def _stop_process(self):
        self._stop_requested = True
        if self.process:
            try:
                os.killpg(os.getpgid(self.process.pid), signal.SIGTERM)
                self.process.wait(timeout=0.5)
            except Exception:
                try:
                    os.killpg(os.getpgid(self.process.pid), signal.SIGKILL)
                except Exception:
                    pass
            self.process = None

    def set_volume(self, vol: int):
        self.volume = max(0, min(100, vol))


# ═══════════════════════════════════════════════════════════════════
#  CHANNEL SCAN WORKER (QThread)
# ═══════════════════════════════════════════════════════════════════
class ChannelScanWorker(QObject):
    progress = pyqtSignal(float, str)
    track_found = pyqtSignal(object)
    finished = pyqtSignal(list)
    error = pyqtSignal(str)

    def __init__(self, bot_engine: TelegramBotEngine, channel: str, max_tracks: int = 150):
        super().__init__()
        self.bot = bot_engine
        self.channel = channel.lstrip("@").strip()
        self.max_tracks = max_tracks
        self._cancelled = False

    def cancel(self):
        self._cancelled = True

    def run(self):
        try:
            self.progress.emit(0.05, "Сканирование последних публикаций...")
            latest_id = 1948
            tracks: List[TgAudioTrack] = []

            scan_ids = range(latest_id, max(1, latest_id - 350), -1)
            total = len(scan_ids)

            for idx, post_id in enumerate(scan_ids):
                if self._cancelled or len(tracks) >= self.max_tracks:
                    break

                try:
                    t = self.bot.fetch_track_metadata(self.channel, post_id)
                    if t:
                        tracks.append(t)
                        self.track_found.emit(t)
                        self.progress.emit(
                            (idx + 1) / total,
                            f"Найдено: {len(tracks)} треков (пост #{post_id}: {t.display_name[:25]}...)"
                        )
                except Exception:
                    pass

                time.sleep(0.12)  # Respect rate limits

            self.finished.emit(tracks)
        except Exception as e:
            self.error.emit(str(e))


# ═══════════════════════════════════════════════════════════════════
#  TELEGRAM CHANNEL PLAYER DIALOG (PyQt6 UI)
# ═══════════════════════════════════════════════════════════════════
class TgChannelPlayerDialog(QDialog):
    def __init__(self, parent=None):
        super().__init__(parent)
        self.setWindowTitle("📡 Telegram Музыкальный Канал // Sovereign Audio Streamer")
        self.resize(860, 720)
        self.setMinimumSize(700, 580)

        self.bot_engine = TelegramBotEngine()
        self.playlist_mgr = TgPlaylistManager()
        self.track_player = TgTrackPlayer(self.bot_engine, on_track_changed=self._on_track_state_changed)
        self._scan_thread: Optional[QThread] = None
        self._scan_worker: Optional[ChannelScanWorker] = None
        self._current_tracks: List[TgAudioTrack] = []

        self.setStyleSheet("""
            QDialog {
                background: #0b0f19;
                border: 2px solid #ff7700;
                border-radius: 10px;
            }
            QLabel {
                color: #e0e6ed;
                font-family: Monospace;
            }
            QTabWidget::pane {
                background: #0d121c;
                border: 1px solid #223348;
                border-radius: 6px;
            }
            QTabBar::tab {
                background: #141b27;
                color: #ff9933;
                padding: 8px 18px;
                border: 1px solid #223348;
                border-top-left-radius: 6px;
                border-top-right-radius: 6px;
                font-family: Monospace;
                font-size: 11px;
                font-weight: bold;
            }
            QTabBar::tab:selected {
                background: #2a1805;
                color: #ff7700;
                border-bottom: 2px solid #ff7700;
            }
            QListWidget {
                background: #070a10;
                color: #00ffaa;
                border: 1px solid #1c2b3c;
                border-radius: 6px;
                font-family: Monospace;
                font-size: 11px;
                padding: 4px;
            }
            QListWidget::item {
                padding: 6px 10px;
                border-bottom: 1px solid #111a26;
            }
            QListWidget::item:selected {
                background: #331c05;
                color: #ffaa00;
                font-weight: bold;
                border-left: 3px solid #ff7700;
            }
            QPushButton {
                background: #151e2b;
                color: #ff9933;
                border: 1px solid #ff7700;
                border-radius: 4px;
                padding: 6px 14px;
                font-weight: bold;
                font-family: Monospace;
                font-size: 11px;
            }
            QPushButton:hover {
                background: #3a2200;
                color: #ffffff;
            }
            QLineEdit, QComboBox {
                background: #121824;
                color: #00e5ff;
                border: 1px solid #223348;
                border-radius: 4px;
                padding: 5px 8px;
                font-family: Monospace;
                font-size: 11px;
            }
            QSlider::groove:horizontal {
                height: 6px;
                background: #1c2636;
                border-radius: 3px;
            }
            QSlider::sub-page:horizontal {
                background: #ff7700;
                border-radius: 3px;
            }
            QSlider::handle:horizontal {
                background: #ffffff;
                width: 14px;
                margin: -4px 0;
                border-radius: 7px;
            }
            QProgressBar {
                background: #141c28;
                border: 1px solid #2a3d54;
                border-radius: 4px;
                text-align: center;
                color: #ffaa00;
                font-family: Monospace;
                font-size: 11px;
            }
            QProgressBar::chunk {
                background: #ff7700;
                border-radius: 3px;
            }
        """)

        self._build_ui()
        self._load_cached_tracks()

    def _build_ui(self):
        layout = QVBoxLayout(self)
        layout.setContentsMargins(14, 14, 14, 14)
        layout.setSpacing(10)

        # ─── Top Bar ───
        top_bar = QHBoxLayout()
        lbl_head = QLabel("📡 TELEGRAM CHANNEL PLAYER // @RadioArmageddonFM")
        lbl_head.setFont(QFont("Monospace", 12, QFont.Weight.Bold))
        lbl_head.setStyleSheet("color: #ff7700;")
        top_bar.addWidget(lbl_head)
        top_bar.addStretch()

        self.btn_scan = QPushButton("🔄 Сканировать треки")
        self.btn_scan.clicked.connect(self._toggle_scan)
        top_bar.addWidget(self.btn_scan)

        layout.addLayout(top_bar)

        # ─── Progress bar ───
        self.progress_bar = QProgressBar()
        self.progress_bar.setRange(0, 100)
        self.progress_bar.setFixedHeight(20)
        self.progress_bar.setVisible(False)
        layout.addWidget(self.progress_bar)

        # ─── Tabs ───
        self.tabs = QTabWidget()

        # Tab 1: Channel Tracks
        tab_tracks = QWidget()
        vbox_tracks = QVBoxLayout(tab_tracks)

        search_row = QHBoxLayout()
        self.search_edit = QLineEdit()
        self.search_edit.setPlaceholderText("🔍 Поиск по названию или исполнителю...")
        self.search_edit.textChanged.connect(self._filter_tracks)
        search_row.addWidget(self.search_edit, 1)

        btn_all = QPushButton("☑ Выбрать все")
        btn_all.clicked.connect(self.track_list_widget.selectAll if hasattr(self, 'track_list_widget') else lambda: None)
        search_row.addWidget(btn_all)

        vbox_tracks.addLayout(search_row)

        self.track_list_widget = QListWidget()
        self.track_list_widget.setSelectionMode(QListWidget.SelectionMode.ExtendedSelection)
        self.track_list_widget.itemDoubleClicked.connect(self._play_selected_from_list)
        vbox_tracks.addWidget(self.track_list_widget, 1)

        bottom_row = QHBoxLayout()
        btn_add_to_pl = QPushButton("📋 Создать плейлист из выбранных")
        btn_add_to_pl.clicked.connect(self._create_playlist_from_selection)
        bottom_row.addWidget(btn_add_to_pl)

        self.lbl_count = QLabel("Треков: 0")
        self.lbl_count.setStyleSheet("color: #8899aa;")
        bottom_row.addWidget(self.lbl_count)
        vbox_tracks.addLayout(bottom_row)

        self.tabs.addTab(tab_tracks, "📡 Каталог треков (@RadioArmageddonFM)")

        # Tab 2: Custom Playlists
        tab_pl = QWidget()
        vbox_pl = QVBoxLayout(tab_pl)

        pl_select_row = QHBoxLayout()
        pl_select_row.addWidget(QLabel("Плейлист:"))
        self.combo_playlists = QComboBox()
        self.combo_playlists.currentIndexChanged.connect(self._on_playlist_combo_changed)
        pl_select_row.addWidget(self.combo_playlists, 1)

        btn_del_pl = QPushButton("🗑️ Удалить")
        btn_del_pl.clicked.connect(self._delete_current_playlist)
        pl_select_row.addWidget(btn_del_pl)
        vbox_pl.addLayout(pl_select_row)

        self.playlist_list_widget = QListWidget()
        self.playlist_list_widget.itemDoubleClicked.connect(self._play_from_custom_playlist)
        vbox_pl.addWidget(self.playlist_list_widget, 1)

        btn_play_pl = QPushButton("▶ Воспроизвести весь плейлист")
        btn_play_pl.clicked.connect(self._play_entire_custom_playlist)
        vbox_pl.addWidget(btn_play_pl)

        self.tabs.addTab(tab_pl, "📋 Мои Плейлисты")
        layout.addWidget(self.tabs, 1)

        # ─── Now Playing Status ───
        self.lbl_status = QLabel("⏹ ОЖИДАНИЕ (Двойной клик на треке для воспроизведения)")
        self.lbl_status.setFont(QFont("Monospace", 10, QFont.Weight.Bold))
        self.lbl_status.setStyleSheet("color: #00ffaa; padding: 4px;")
        layout.addWidget(self.lbl_status)

        # ─── Volume Row ───
        vol_row = QHBoxLayout()
        vol_row.addWidget(QLabel("🔊 Громкость:"))
        self.slider_vol = QSlider(Qt.Orientation.Horizontal)
        self.slider_vol.setRange(0, 100)
        self.slider_vol.setValue(80)
        self.slider_vol.valueChanged.connect(self._on_vol_changed)
        vol_row.addWidget(self.slider_vol, 1)
        self.lbl_vol = QLabel("80%")
        self.lbl_vol.setFixedWidth(40)
        self.lbl_vol.setStyleSheet("color: #ffaa00; font-weight: bold;")
        vol_row.addWidget(self.lbl_vol)
        layout.addLayout(vol_row)

        # ─── Controls ───
        ctrl_row = QHBoxLayout()
        self.btn_prev = QPushButton("⏮ ПРЕД")
        self.btn_prev.clicked.connect(self.track_player.prev_track)
        ctrl_row.addWidget(self.btn_prev)

        self.btn_play = QPushButton("▶ ИГРАТЬ")
        self.btn_play.setStyleSheet("background: #003318; border-color: #00ff66; color: #00ffaa; font-size: 12px;")
        self.btn_play.clicked.connect(self._toggle_playback)
        ctrl_row.addWidget(self.btn_play)

        self.btn_stop = QPushButton("⏹ СТОП")
        self.btn_stop.setStyleSheet("background: #331518; border-color: #ff3366; color: #ff5577;")
        self.btn_stop.clicked.connect(self.track_player.stop)
        ctrl_row.addWidget(self.btn_stop)

        self.btn_next = QPushButton("СЛЕД ⏭")
        self.btn_next.clicked.connect(self.track_player.next_track)
        ctrl_row.addWidget(self.btn_next)

        self.btn_repeat = QPushButton("🔁 Повтор")
        self.btn_repeat.setCheckable(True)
        self.btn_repeat.toggled.connect(lambda v: setattr(self.track_player, 'repeat', v))
        ctrl_row.addWidget(self.btn_repeat)

        btn_close = QPushButton("ЗАКРЫТЬ")
        btn_close.setStyleSheet("background: #1c2230; color: #aaa; border-color: #555;")
        btn_close.clicked.connect(self.accept)
        ctrl_row.addWidget(btn_close)

        layout.addLayout(ctrl_row)

        self._refresh_playlists_ui()

    def _load_cached_tracks(self):
        cached = self.playlist_mgr.get_channel_cache("RadioArmageddonFM")
        if cached:
            self._current_tracks = cached
            self._display_tracks(cached)

    def _display_tracks(self, tracks: List[TgAudioTrack]):
        self.track_list_widget.clear()
        for idx, t in enumerate(tracks):
            dur = f" [{t.duration_str}]" if t.duration_str else ""
            item = QListWidgetItem(f"♪ {t.display_name}{dur}")
            item.setData(Qt.ItemDataRole.UserRole, idx)
            self.track_list_widget.addItem(item)
        self.lbl_count.setText(f"Треков: {len(tracks)}")

    def _filter_tracks(self, text: str):
        query = text.strip().lower()
        for i in range(self.track_list_widget.count()):
            item = self.track_list_widget.item(i)
            item.setHidden(bool(query and query not in item.text().lower()))

    def _toggle_scan(self):
        if self._scan_thread and self._scan_thread.isRunning():
            if self._scan_worker:
                self._scan_worker.cancel()
            self.btn_scan.setText("🔄 Сканировать треки")
            self.progress_bar.setVisible(False)
            return

        self.btn_scan.setText("⏹ Остановить")
        self.progress_bar.setVisible(True)
        self.progress_bar.setValue(0)

        self._scan_thread = QThread()
        self._scan_worker = ChannelScanWorker(self.bot_engine, "RadioArmageddonFM", max_tracks=200)
        self._scan_worker.moveToThread(self._scan_thread)

        self._scan_worker.progress.connect(self._on_scan_progress)
        self._scan_worker.track_found.connect(self._on_track_discovered)
        self._scan_worker.finished.connect(self._on_scan_finished)
        self._scan_worker.error.connect(lambda e: log.error("Scan error: %s", e))

        self._scan_thread.started.connect(self._scan_worker.run)
        self._scan_thread.start()

    def _on_scan_progress(self, val: float, text: str):
        self.progress_bar.setValue(int(val * 100))
        self.progress_bar.setFormat(text)

    def _on_track_discovered(self, track: TgAudioTrack):
        if not any(t.post_id == track.post_id for t in self._current_tracks):
            self._current_tracks.insert(0, track)
            dur = f" [{track.duration_str}]" if track.duration_str else ""
            item = QListWidgetItem(f"♪ {track.display_name}{dur}")
            item.setData(Qt.ItemDataRole.UserRole, 0)
            self.track_list_widget.insertItem(0, item)
            self.lbl_count.setText(f"Треков: {len(self._current_tracks)}")

    def _on_scan_finished(self, tracks: list):
        self.progress_bar.setVisible(False)
        self.btn_scan.setText("🔄 Сканировать треки")
        if tracks:
            existing_ids = {t.post_id for t in tracks}
            for t in self._current_tracks:
                if t.post_id not in existing_ids:
                    tracks.append(t)
            self._current_tracks = tracks
            self.playlist_mgr.save_channel_cache("RadioArmageddonFM", self._current_tracks)
            self._display_tracks(self._current_tracks)

        if self._scan_thread:
            self._scan_thread.quit()
            self._scan_thread.wait()

    def _play_selected_from_list(self, item: QListWidgetItem):
        idx = item.data(Qt.ItemDataRole.UserRole)
        if 0 <= idx < len(self._current_tracks):
            self.track_player.set_playlist(self._current_tracks, idx)
            self.track_player.play_track(self._current_tracks[idx])

    def _create_playlist_from_selection(self):
        selected_items = self.track_list_widget.selectedItems()
        if not selected_items:
            QMessageBox.information(self, "Выбор", "Выберите хотя бы один трек из списка!")
            return

        tracks = [self._current_tracks[item.data(Qt.ItemDataRole.UserRole)] for item in selected_items]
        name, ok = QInputDialog.getText(
            self, "Новый плейлист",
            f"Название плейлиста ({len(tracks)} треков):",
            QLineEdit.EchoMode.Normal,
            f"Armageddon Selection {len(self.playlist_mgr.playlists) + 1}"
        )
        if ok and name.strip():
            self.playlist_mgr.create_playlist(name.strip(), tracks, "RadioArmageddonFM")
            self._refresh_playlists_ui()
            self.tabs.setCurrentIndex(1)

    def _refresh_playlists_ui(self):
        self.combo_playlists.clear()
        for idx, pl in enumerate(self.playlist_mgr.playlists):
            self.combo_playlists.addItem(f"📋 {pl.name} ({len(pl.tracks)} треков)", idx)

    def _on_playlist_combo_changed(self, idx: int):
        self.playlist_list_widget.clear()
        if 0 <= idx < len(self.playlist_mgr.playlists):
            pl = self.playlist_mgr.playlists[idx]
            for t_idx, t in enumerate(pl.tracks):
                dur = f" [{t.duration_str}]" if t.duration_str else ""
                item = QListWidgetItem(f"♪ {t.display_name}{dur}")
                item.setData(Qt.ItemDataRole.UserRole, t_idx)
                self.playlist_list_widget.addItem(item)

    def _play_from_custom_playlist(self, item: QListWidgetItem):
        pl_idx = self.combo_playlists.currentIndex()
        if 0 <= pl_idx < len(self.playlist_mgr.playlists):
            pl = self.playlist_mgr.playlists[pl_idx]
            t_idx = item.data(Qt.ItemDataRole.UserRole)
            if 0 <= t_idx < len(pl.tracks):
                self.track_player.set_playlist(pl.tracks, t_idx)
                self.track_player.play_track(pl.tracks[t_idx])

    def _play_entire_custom_playlist(self):
        pl_idx = self.combo_playlists.currentIndex()
        if 0 <= pl_idx < len(self.playlist_mgr.playlists):
            pl = self.playlist_mgr.playlists[pl_idx]
            if pl.tracks:
                self.track_player.set_playlist(pl.tracks, 0)
                self.track_player.play_track(pl.tracks[0])

    def _delete_current_playlist(self):
        pl_idx = self.combo_playlists.currentIndex()
        if 0 <= pl_idx < len(self.playlist_mgr.playlists):
            self.playlist_mgr.delete_playlist(pl_idx)
            self._refresh_playlists_ui()

    def _toggle_playback(self):
        if self.track_player.is_playing:
            self.track_player.stop()
        else:
            if self.track_player.current_playlist and self.track_player.playlist_index >= 0:
                self.track_player.play_index(self.track_player.playlist_index)
            elif self._current_tracks:
                self.track_player.set_playlist(self._current_tracks, 0)
                self.track_player.play_track(self._current_tracks[0])

    def _on_vol_changed(self, val: int):
        self.track_player.set_volume(val)
        self.lbl_vol.setText(f"{val}%")

    def _on_track_state_changed(self, track: TgAudioTrack, is_playing: bool):
        QTimer.singleShot(0, lambda: self._update_ui_state(track, is_playing))

    def _update_ui_state(self, track: TgAudioTrack, is_playing: bool):
        if is_playing and track:
            self.lbl_status.setText(f"🟢 В ЭФИРЕ: {track.display_name}")
            self.btn_play.setText("⏸ ПАУЗА")
            self.btn_play.setStyleSheet("background: #003344; border-color: #00e5ff; color: #00e5ff; font-size: 12px;")
        else:
            self.lbl_status.setText("⏹ ЭФИР ОСТАНОВЛЕН")
            self.btn_play.setText("▶ ИГРАТЬ")
            self.btn_play.setStyleSheet("background: #003318; border-color: #00ff66; color: #00ffaa; font-size: 12px;")

    def closeEvent(self, event):
        if self._scan_worker:
            self._scan_worker.cancel()
        if self._scan_thread and self._scan_thread.isRunning():
            self._scan_thread.quit()
            self._scan_thread.wait(1000)
        self.track_player.stop()
        super().closeEvent(event)
