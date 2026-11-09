#!/usr/bin/env python3
"""
MadGammon Desktop — PyQt6 Backgammon with P2P Network Play
Sovereign Crypto-Privacy Backgammon | Шеш-Беш / BG-AG
Compatible with Android MadGammon via TCP P2P protocol.
"""
import sys
import os

# ─── Robust Environment & PyQt6 Fallback ───
try:
    import PyQt6
except ModuleNotFoundError:
    for fallback in ["/usr/lib/python3/dist-packages", "/usr/local/lib/python3/dist-packages"]:
        if os.path.exists(fallback) and fallback not in sys.path:
            sys.path.append(fallback)

# Ensure script directory is in sys.path
script_dir = os.path.dirname(os.path.abspath(__file__))
if script_dir not in sys.path:
    sys.path.insert(0, script_dir)

import math
import random
from dataclasses import dataclass
from typing import Optional, List, Tuple

from logger import setup_logging, get_logger
log = setup_logging()

log.info("=" * 60)
log.info("MadGammon Desktop Launching")
log.info("Python: %s (version: %s)", sys.executable, sys.version.replace('\n', ' '))
log.info("PATH: %s", os.environ.get("PATH", ""))
log.info("WAYLAND_DISPLAY: %s", os.environ.get("WAYLAND_DISPLAY", "<unset>"))
log.info("DISPLAY: %s", os.environ.get("DISPLAY", "<unset>"))
log.info("XDG_SESSION_TYPE: %s", os.environ.get("XDG_SESSION_TYPE", "<unset>"))
log.info("=" * 60)

try:
    from PyQt6.QtWidgets import (
        QApplication, QMainWindow, QWidget, QVBoxLayout, QHBoxLayout,
        QPushButton, QLabel, QStatusBar, QSystemTrayIcon, QMenu,
        QDialog, QLineEdit, QTextEdit, QSpinBox, QComboBox,
        QSplitter, QFrame, QMessageBox, QGroupBox, QRadioButton,
        QButtonGroup
    )
    from PyQt6.QtGui import (
        QPainter, QColor, QPen, QBrush, QFont, QIcon, QPixmap,
        QPainterPath, QLinearGradient, QRadialGradient, QAction
    )
    from PyQt6.QtCore import (
        Qt, QRect, QRectF, QPoint, QPointF, QTimer, QSize, pyqtSignal, QObject
    )
    log.info("PyQt6 loaded successfully.")
except Exception as e:
    log.critical("Failed to import PyQt6 components: %s", e, exc_info=True)
    raise

from engine import (
    Player, GameMode, MentalDiceEngine, BackgammonRules,
    CheckerStack, BOARD_POINTS, ReachablePath
)
from ai_bot import BackgammonAI, AIDifficulty
from network import P2PNetworkManager, get_local_lan_ip
from radio import RadioPlayer, RadioDialog
from jrebiy import JrebiyDialog
from secret_layer import PinUnlockDialog, SecretCovertMessengerWindow
from telegram_messenger import CyberTelegramMessenger
from audio_sfx import SoundManager
from themes import THEMES, get_theme, BoardTheme, DEFAULT_THEME_ID
from PyQt6.QtGui import QKeySequence, QShortcut

# ─── Color Palette: Cyberpunk Neo-Tactic ───
C_BG_DARK       = QColor(30, 30, 42)
C_BG_PANEL      = QColor(38, 38, 55)
C_BOARD_DARK     = QColor(45, 42, 38)
C_BOARD_FRAME    = QColor(60, 55, 48)
C_TRI_GOLD       = QColor(212, 175, 55)
C_TRI_EMERALD    = QColor(0, 168, 107)
C_CHECKER_WHITE  = QColor(240, 235, 220)
C_CHECKER_BLACK  = QColor(35, 35, 50)
C_CHECKER_OUTLINE= QColor(200, 180, 120, 180)
C_BAR_BG         = QColor(50, 48, 44)
C_HIGHLIGHT      = QColor(0, 255, 200, 100)
C_VALID_MOVE     = QColor(0, 255, 180, 80)
C_TEXT_GOLD      = QColor(212, 175, 55)
C_TEXT_CYAN      = QColor(0, 230, 255)
C_TEXT_WHITE     = QColor(220, 220, 220)
C_DICE_BG        = QColor(245, 240, 225)
C_DICE_DOT       = QColor(30, 30, 30)
C_DICE_GLOW      = QColor(0, 255, 200, 60)
C_BUTTON_BG      = QColor(50, 50, 70)
C_BUTTON_HOVER   = QColor(60, 60, 85)
C_CHAT_BG        = QColor(25, 25, 38)
C_STATUS_ONLINE  = QColor(0, 230, 120)
C_STATUS_OFFLINE = QColor(200, 60, 60)
C_NEON_GREEN     = QColor(0, 255, 102)
C_NEON_RED       = QColor(255, 51, 102)


# ═══════════════════════════════════════════════════════
#  ARTISTIC BANNER: CYBERPUNK + VAN GOGH + GAUDÍ
# ═══════════════════════════════════════════════════════
class CyberGoghGaudiBannerWidget(QWidget):
    """
    Renders an artistic banner fusing Cyberpunk + Van Gogh + Antoni Gaudí aesthetics.
    Features Starry Night golden vortex spirals, Sagrada Família catenary arches,
    trencadís mosaic fragments, and glowing cyberpunk circuits with dynamic HUD badges.
    """
    def __init__(self, parent=None):
        super().__init__(parent)
        self.setFixedHeight(125)
        self.setMinimumWidth(240)
        self.banner_pixmap: Optional[QPixmap] = None

        banner_path = os.path.join(os.path.dirname(__file__), "assets", "banner_cyber_gogh_gaudi.png")
        if os.path.exists(banner_path):
            self.banner_pixmap = QPixmap(banner_path)

        self.mode_str = "LONG (Длинные)"
        self.opponent_str = "AI Bot: Medium"
        self.current_player_str = "WHITE"

    def set_status(self, mode: str, opponent: str, player: str):
        self.mode_str = mode
        self.opponent_str = opponent
        self.current_player_str = player
        self.update()

    def paintEvent(self, event):
        p = QPainter(self)
        p.setRenderHint(QPainter.RenderHint.Antialiasing)
        w, h = self.width(), self.height()
        rect = QRectF(0, 0, w, h)

        if self.banner_pixmap and not self.banner_pixmap.isNull():
            scaled = self.banner_pixmap.scaled(
                self.size(),
                Qt.AspectRatioMode.KeepAspectRatioByExpanding,
                Qt.TransformationMode.SmoothTransformation
            )
            sx = max(0, int((scaled.width() - w) / 2))
            sy = max(0, int((scaled.height() - h) / 2))
            p.drawPixmap(0, 0, scaled, sx, sy, w, h)
        else:
            grad = QLinearGradient(0, 0, w, h)
            grad.setColorAt(0, QColor(15, 18, 35))
            grad.setColorAt(1, QColor(25, 35, 60))
            p.fillRect(rect, grad)

        # Dark gradient at bottom for badge contrast
        vignette = QLinearGradient(0, h * 0.45, 0, h)
        vignette.setColorAt(0, QColor(0, 0, 0, 0))
        vignette.setColorAt(1, QColor(10, 15, 25, 215))
        p.fillRect(rect, vignette)

        # Dynamic overlay badges
        # Bottom left: Mode pill
        mode_pill_w = 78
        mode_pill_h = 20
        p.setBrush(QBrush(QColor(15, 30, 45, 230)))
        p.setPen(QPen(QColor(0, 230, 255), 1.2))
        p.drawRoundedRect(QRectF(8, h - 26, mode_pill_w, mode_pill_h), 4, 4)
        p.setFont(QFont("Monospace", 7, QFont.Weight.Bold))
        p.setPen(QColor(0, 230, 255))
        short_mode = self.mode_str.split()[0]
        p.drawText(QRectF(8, h - 26, mode_pill_w, mode_pill_h), Qt.AlignmentFlag.AlignCenter, short_mode)

        # Bottom right: Opponent / Turn status
        status_w = 135
        status_h = 20
        p.setBrush(QBrush(QColor(35, 25, 15, 230)))
        p.setPen(QPen(QColor(245, 185, 55), 1.2))
        p.drawRoundedRect(QRectF(w - status_w - 8, h - 26, status_w, status_h), 4, 4)
        p.setFont(QFont("Monospace", 7, QFont.Weight.Bold))
        p.setPen(QColor(245, 185, 55))
        p.drawText(QRectF(w - status_w - 8, h - 26, status_w, status_h), Qt.AlignmentFlag.AlignCenter, self.opponent_str)

        # Frame border with glowing outline
        p.setBrush(Qt.BrushStyle.NoBrush)
        p.setPen(QPen(QColor(0, 220, 255, 180), 1.5))
        p.drawRoundedRect(rect.adjusted(1, 1, -1, -1), 4, 4)


# ═══════════════════════════════════════════════════════
#  PHYSICAL ZARIKI (DICE) SIMULATION
# ═══════════════════════════════════════════════════════
class PhysicsDie:
    """
    Simulates a 3D physical zarik (die) tumbling, jumping, and bouncing across the board.
    Calculates 2D translation (x, y), height bounce (z) under gravity with restitution,
    tumbling angular spin, and dynamic drop shadow.
    """
    def __init__(self, target_val: int, sx: float, sy: float, tx: float, ty: float):
        self.target_val = target_val
        self.current_val = random.randint(1, 6)
        self.x = sx
        self.y = sy
        self.tx = tx
        self.ty = ty

        steps = 42.0
        # Translational velocity towards destination with natural lateral scatter
        self.vx = (tx - sx) / steps + random.uniform(-1.8, 1.8)
        self.vy = (ty - sy) / steps + random.uniform(-1.8, 1.8)

        # 3D Vertical bounce physics
        self.z = random.uniform(60.0, 95.0)
        self.vz = random.uniform(1.8, 4.5)
        self.gravity = 0.95
        self.elasticity = 0.58

        # Tumbling rotation angle & angular velocity
        self.angle = random.uniform(0, 360)
        self.v_rot = random.uniform(22.0, 38.0) * (1 if random.random() > 0.5 else -1)
        self.settled = False

    def update(self, frame_idx: int, total_frames: int):
        progress = frame_idx / total_frames
        if progress >= 1.0:
            self.x = self.tx
            self.y = self.ty
            self.z = 0
            self.current_val = self.target_val
            self.settled = True
            return

        # 2D motion with surface/air friction
        self.x += self.vx
        self.y += self.vy
        self.vx *= 0.94
        self.vy *= 0.94

        # Height motion under gravity
        self.vz -= self.gravity
        self.z += self.vz
        if self.z <= 0:
            self.z = 0
            if abs(self.vz) > 1.2:
                self.vz = -self.vz * self.elasticity
                self.v_rot += random.uniform(-6.0, 6.0)
            else:
                self.vz = 0

        # Tumbling rotation
        self.angle += self.v_rot
        self.v_rot *= 0.94

        # Face switching while rolling/bouncing
        if progress < 0.72:
            if frame_idx % 3 == 0:
                self.current_val = random.randint(1, 6)
        else:
            self.current_val = self.target_val
            t = (progress - 0.72) / 0.28
            self.x = self.x * (1 - t * 0.35) + self.tx * (t * 0.35)
            self.y = self.y * (1 - t * 0.35) + self.ty * (t * 0.35)


# ═══════════════════════════════════════════════════════
#  CHECKER ANIMATION STEP
# ═══════════════════════════════════════════════════════
@dataclass
class CheckerAnimStep:
    from_abs: int
    to_abs: int
    die_used: int
    start_pos: QPointF
    end_pos: QPointF


# ═══════════════════════════════════════════════════════
#  BOARD WIDGET — custom QPainter rendering
# ═══════════════════════════════════════════════════════
class BoardWidget(QWidget):
    """Renders the full backgammon board with interactive point selection."""

    point_clicked = pyqtSignal(int)   # -1 = bar, 24 = bear-off
    dice_roll_requested = pyqtSignal()

    MARGIN = 20
    BAR_WIDTH_RATIO = 0.06
    BEAR_OFF_WIDTH = 40
    CHECKER_RATIO = 0.85     # checker diameter vs point width

    def __init__(self, parent=None):
        super().__init__(parent)
        self.setMinimumSize(700, 500)
        self.board: List[CheckerStack] = BackgammonRules.create_initial_board(GameMode.LONG)
        self.bar_white = 0
        self.bar_black = 0
        self.borne_off_white = 0
        self.borne_off_black = 0
        self.dice: Tuple[int, int] = (0, 0)
        self.dice_used: List[bool] = []
        self.current_player: str = Player.WHITE
        self.selected_point: int = -2   # -2 = nothing, -1 = bar, 0..23 = point
        self.reachable_paths: List[ReachablePath] = []
        self.game_mode: str = GameMode.LONG
        self.last_move_src: int = -2
        self.last_move_dst: int = -2
        self.last_move_player: Optional[str] = None
        self.setMouseTracking(True)
        self._hover_point = -2

        # Theme Configuration
        self.theme_id = DEFAULT_THEME_ID
        self.theme = get_theme(self.theme_id)
        self.head_player = Player.WHITE
        self.theme_pixmaps: dict = {}
        self._load_theme_pixmaps()

        # Checker Move Animation
        self.is_checker_animating = False
        self.checker_anim_steps: List[CheckerAnimStep] = []
        self.checker_anim_step_idx = 0
        self.checker_anim_frame = 0
        self.checker_anim_total_frames = 14  # ~224ms per hop at 60fps
        self.checker_anim_player = Player.WHITE
        self.checker_anim_current_pos = QPointF(0, 0)
        self.checker_anim_elevation = 0.0
        self.checker_anim_trail: List[Tuple[QPointF, float, float]] = []  # (pos, scale, alpha)
        self.checker_anim_timer = QTimer(self)
        self.checker_anim_timer.setInterval(16)
        self.checker_anim_timer.timeout.connect(self._on_checker_anim_tick)
        self._checker_anim_callback = None

        # Physical Zariki Animation
        self.is_dice_rolling = False
        self.dice_anim_dice: List[PhysicsDie] = []
        self.dice_anim_frame = 0
        self.dice_anim_total_frames = 52  # ~830ms at 60fps
        self.dice_anim_timer = QTimer(self)
        self.dice_anim_timer.setInterval(16)
        self.dice_anim_timer.timeout.connect(self._on_dice_anim_tick)
        self._dice_anim_callback = None

        # Roll Prompt State (pulsing badge when waiting for player to roll)
        self.can_roll_dice = False
        self.roll_prompt_pulse = 0.0
        self.roll_prompt_timer = QTimer(self)
        self.roll_prompt_timer.setInterval(50)
        self.roll_prompt_timer.timeout.connect(self._on_prompt_pulse_tick)
        self.roll_prompt_timer.start()

        log.debug("BoardWidget initialized with LONG mode and Zariki physics")

    def _load_theme_pixmaps(self):
        assets_dir = os.path.join(os.path.dirname(__file__), "assets", "board_themes")
        for tid in THEMES.keys():
            path = os.path.join(assets_dir, f"board_bg_{tid}.png")
            if os.path.exists(path):
                self.theme_pixmaps[tid] = QPixmap(path)

    def _on_prompt_pulse_tick(self):
        if self.can_roll_dice:
            self.roll_prompt_pulse = (self.roll_prompt_pulse + 0.15) % (math.pi * 2)
            self.update()

    def set_theme(self, theme_id: str):
        self.theme_id = theme_id
        self.theme = get_theme(theme_id)
        self.update()

    def start_dice_animation(self, d1: int, d2: int, player: str, callback=None):
        layout = self._layout()
        br = layout["board_rect"]
        cx = br.center().x()
        cy = br.center().y()

        # Destination resting spots (separated by 64px)
        t1 = (cx - 32.0, cy)
        t2 = (cx + 32.0, cy)

        # Launch origin based on active player
        if player == Player.WHITE:
            # White throws from right/bottom quadrant towards center
            sx1 = cx + random.uniform(120, 180)
            sy1 = cy + random.uniform(80, 140)
            sx2 = cx + random.uniform(160, 220)
            sy2 = cy + random.uniform(60, 120)
        else:
            # Black throws from left/top quadrant towards center
            sx1 = cx - random.uniform(120, 180)
            sy1 = cy - random.uniform(80, 140)
            sx2 = cx - random.uniform(160, 220)
            sy2 = cy - random.uniform(60, 120)

        die1 = PhysicsDie(d1, sx1, sy1, t1[0], t1[1])
        die2 = PhysicsDie(d2, sx2, sy2, t2[0], t2[1])

        self.dice_anim_dice = [die1, die2]
        self.dice_anim_frame = 0
        self.is_dice_rolling = True
        self.can_roll_dice = False
        self._dice_anim_callback = callback
        self.dice_anim_timer.start()

        # SFX: Dice tumbling along felt
        SoundManager.get_instance().play_dice_tumble()
        self.update()

    def _on_dice_anim_tick(self):
        self.dice_anim_frame += 1
        for die in self.dice_anim_dice:
            die.update(self.dice_anim_frame, self.dice_anim_total_frames)

        # SFX: Dice bounce knock as dice settle on surface
        if self.dice_anim_frame == 36:
            SoundManager.get_instance().play_dice_bounce()

        self.update()

        if self.dice_anim_frame >= self.dice_anim_total_frames:
            self.dice_anim_timer.stop()
            self.is_dice_rolling = False
            cb = self._dice_anim_callback
            self._dice_anim_callback = None
            if cb:
                cb()
            self.update()

    @property
    def valid_destinations(self) -> List[int]:
        return [p.final_to_abs for p in self.reachable_paths]

    def set_board_state(self, board, bar_w, bar_b, borne_w, borne_b, dice, current_player):
        self.board = board
        self.bar_white = bar_w
        self.bar_black = bar_b
        self.borne_off_white = borne_w
        self.borne_off_black = borne_b
        self.dice = dice
        self.current_player = current_player
        self.update()

    def _layout(self):
        """Compute layout rectangles."""
        w, h = self.width(), self.height()
        m = self.MARGIN
        bar_w = int(w * self.BAR_WIDTH_RATIO)
        bear_w = self.BEAR_OFF_WIDTH

        board_x = m + bear_w
        board_w = w - 2 * m - 2 * bear_w
        board_h = h - 2 * m

        half_w = (board_w - bar_w) // 2
        point_w = half_w / 6
        point_h = board_h * 0.42

        return {
            "board_rect": QRectF(board_x, m, board_w, board_h),
            "bar_x": board_x + half_w,
            "bar_w": bar_w,
            "half_w": half_w,
            "point_w": point_w,
            "point_h": point_h,
            "board_x": board_x,
            "board_h": board_h,
            "m": m,
            "bear_left": QRectF(m, m, bear_w, board_h),
            "bear_right": QRectF(w - m - bear_w, m, bear_w, board_h),
        }

    def _point_rect(self, point_idx: int, layout: dict) -> Tuple[QRectF, bool]:
        """
        Returns (triangle_rect, is_top_half) for a point index 0..23.
        Canonical Long Nardy geometry:
          - Points 0..5: Top Right (White Head is point 0 at top right)
          - Points 6..11: Top Left (moving right-to-left)
          - Points 12..17: Bottom Left (Black Head is point 12 at bottom left, moving left-to-right)
          - Points 18..23: Bottom Right (White Home is points 18..23 at bottom right)
        """
        bx = layout["board_rect"].x()
        by = layout["board_rect"].y()
        bw = layout["board_rect"].width()
        bh = layout["board_rect"].height()
        pw = layout["point_w"]
        ph = layout["point_h"]
        bar_w = layout["bar_w"]
        half_w = layout["half_w"]

        if point_idx < 6:
            # Top Right: points 0..5 (0 = far right corner, 5 = near bar)
            col = 5 - point_idx
            x = bx + half_w + bar_w + col * pw
            y = by
            return QRectF(x, y, pw, ph), True
        elif point_idx < 12:
            # Top Left: points 6..11 (6 = near bar, 11 = far left corner)
            col = 11 - point_idx
            x = bx + col * pw
            y = by
            return QRectF(x, y, pw, ph), True
        elif point_idx < 18:
            # Bottom Left: points 12..17 (12 = far left corner, 17 = near bar)
            col = point_idx - 12
            x = bx + col * pw
            y = by + bh - ph
            return QRectF(x, y, pw, ph), False
        else:
            # Bottom Right: points 18..23 (18 = near bar, 23 = far right corner, White Home)
            col = point_idx - 18
            x = bx + half_w + bar_w + col * pw
            y = by + bh - ph
            return QRectF(x, y, pw, ph), False

    def get_point_pos(self, point_idx: int, player: str, layout: dict, is_source: bool = False) -> QPointF:
        """Calculate pixel center for a checker at point_idx (-1=bar, 0..23=points, 24=bear-off)."""
        if point_idx == -1:  # BAR
            bar_x = layout["bar_x"]
            bar_w = layout["bar_w"]
            bh = layout["board_rect"].height()
            by = layout["board_rect"].y()
            cd = bar_w * 0.7
            cx = bar_x + bar_w / 2
            count = self.bar_white if player == Player.WHITE else self.bar_black
            if is_source:
                j = max(0, min(count, 5) - 1)
            else:
                j = min(count, 4)
            if player == Player.WHITE:
                cy = by + bh / 2 + 20 + j * cd * 0.9
            else:
                cy = by + bh / 2 - 20 - j * cd * 0.9
            return QPointF(cx, cy)
        elif point_idx == 24:  # BEAR OFF
            rect = layout["bear_right"] if player == self.head_player else layout["bear_left"]
            count = self.borne_off_white if player == Player.WHITE else self.borne_off_black
            cd = rect.width() * 0.6
            cx = rect.center().x()
            cy = rect.bottom() - 10 - min(count, 14) * (cd * 0.35)
            return QPointF(cx, cy)
        else:  # 0 <= point_idx < 24
            rect, is_top = self._point_rect(point_idx, layout)
            cx = rect.center().x()
            pw = layout["point_w"]
            checker_d = pw * self.CHECKER_RATIO
            stack = self.board[point_idx]
            count = stack.count
            if is_source:
                j = max(0, min(count, 5) - 1)
                max_show = max(1, min(count, 5))
            else:
                j = min(count, 4)
                max_show = min(count + 1, 5)
            spacing = min(checker_d * 0.9, rect.height() / max(max_show, 1))
            if is_top:
                cy = rect.top() + checker_d / 2 + j * spacing
            else:
                cy = rect.bottom() - checker_d / 2 - j * spacing
            return QPointF(cx, cy)

    def start_checker_animation(self, path: ReachablePath, player: str, callback=None):
        """Initiates a smooth 3D elevation hop animation along the given ReachablePath steps."""
        if not path or not path.steps:
            if callback:
                callback()
            return

        layout = self._layout()
        self.checker_anim_steps = []
        self.checker_anim_player = player
        self._checker_anim_callback = callback
        self.checker_anim_trail = []

        for step in path.steps:
            start_pos = self.get_point_pos(step.from_abs, player, layout, is_source=True)
            end_pos = self.get_point_pos(step.to_abs, player, layout, is_source=False)
            self.checker_anim_steps.append(CheckerAnimStep(
                from_abs=step.from_abs,
                to_abs=step.to_abs,
                die_used=step.die_used,
                start_pos=start_pos,
                end_pos=end_pos
            ))

        self.checker_anim_step_idx = 0
        self.checker_anim_frame = 0
        first_step = self.checker_anim_steps[0]
        self.checker_anim_current_pos = first_step.start_pos
        self.checker_anim_elevation = 0.0
        self.is_checker_animating = True
        self.checker_anim_timer.start()

        # SFX: Checker sliding
        SoundManager.get_instance().play_checker_slide()
        self.update()

    def _on_checker_anim_tick(self):
        """Updates checker physics position, 3D hop arc, and motion trail."""
        if not self.is_checker_animating or not self.checker_anim_steps:
            self.checker_anim_timer.stop()
            self.is_checker_animating = False
            return

        self.checker_anim_frame += 1
        curr_step = self.checker_anim_steps[self.checker_anim_step_idx]

        t = min(1.0, self.checker_anim_frame / float(self.checker_anim_total_frames))
        # Ease-out cubic for smooth deceleration into landing point
        ease = 1.0 - math.pow(1.0 - t, 3)

        curr_x = curr_step.start_pos.x() + (curr_step.end_pos.x() - curr_step.start_pos.x()) * ease
        curr_y = curr_step.start_pos.y() + (curr_step.end_pos.y() - curr_step.start_pos.y()) * ease
        elevation = math.sin(t * math.pi) * 38.0

        render_pos = QPointF(curr_x, curr_y - elevation)
        self.checker_anim_current_pos = render_pos
        self.checker_anim_elevation = elevation

        # Motion trail
        scale = 1.0 + (elevation / 40.0) * 0.18
        self.checker_anim_trail.append((render_pos, scale, 60.0))
        if len(self.checker_anim_trail) > 4:
            self.checker_anim_trail.pop(0)

        self.update()

        if self.checker_anim_frame >= self.checker_anim_total_frames:
            # SFX: Checker landing impact
            SoundManager.get_instance().play_checker_land()

            if self.checker_anim_step_idx + 1 < len(self.checker_anim_steps):
                # Next hop in combo sequence
                self.checker_anim_step_idx += 1
                self.checker_anim_frame = 0
                self.checker_anim_trail.clear()
            else:
                # Sequence complete!
                self.is_checker_animating = False
                self.checker_anim_timer.stop()
                self.checker_anim_trail.clear()
                cb = self._checker_anim_callback
                self._checker_anim_callback = None
                self.update()
                if cb:
                    cb()

    def _draw_theme_ornament(self, p: QPainter, layout: dict, theme: BoardTheme):
        """Draws custom procedural decorative artwork and texture on the board surface."""
        br = layout["board_rect"]
        bx, by = br.x(), br.y()
        bw, bh = br.width(), br.height()
        cx, cy = br.center().x(), br.center().y()
        bar_w = layout["bar_w"]
        pt = theme.pattern_type

        p.save()
        p.setClipRect(br)

        if pt == "wood_grain":
            p.setBrush(Qt.BrushStyle.NoBrush)
            for k in range(8):
                rad = 120 + k * 65
                pen = QPen(QColor(255, 210, 150, 15 + (k % 3) * 6), 1.2)
                p.setPen(pen)
                p.drawEllipse(QPointF(cx - 180, cy), rad * 1.8, rad * 0.9)
                p.drawEllipse(QPointF(cx + 180, cy), rad * 1.8, rad * 0.9)
            for rx in [bx + layout["half_w"] / 2, bx + layout["half_w"] + bar_w + layout["half_w"] / 2]:
                p.setPen(QPen(theme.board_border, 1.2, Qt.PenStyle.DashDotLine))
                p.drawEllipse(QPointF(rx, cy), 36, 36)
                p.drawEllipse(QPointF(rx, cy), 18, 18)

        elif pt == "malachite_bands":
            p.setBrush(Qt.BrushStyle.NoBrush)
            for k in range(12):
                r = 40 + k * 35
                pen = QPen(QColor(0, 255, 150, 14 + (k % 4) * 8), 2.0)
                p.setPen(pen)
                p.drawEllipse(QPointF(bx + 80, by + 80), r * 2.2, r * 1.5)
                p.drawEllipse(QPointF(bx + bw - 80, by + bh - 80), r * 2.2, r * 1.5)
            p.setPen(QPen(theme.board_border, 1.5))
            corner_len = 32
            for cx_c, cy_c, dx, dy in [(bx+8, by+8, 1, 1), (bx+bw-8, by+8, -1, 1), (bx+8, by+bh-8, 1, -1), (bx+bw-8, by+bh-8, -1, -1)]:
                p.drawLine(QPointF(cx_c, cy_c), QPointF(cx_c + dx * corner_len, cy_c))
                p.drawLine(QPointF(cx_c, cy_c), QPointF(cx_c, cy_c + dy * corner_len))

        elif pt == "amber_sunburst":
            p.setBrush(Qt.BrushStyle.NoBrush)
            for deg in range(0, 360, 18):
                rad = math.radians(deg)
                p.setPen(QPen(QColor(255, 200, 50, 14), 1.5))
                p.drawLine(QPointF(cx, cy), QPointF(cx + math.cos(rad) * bw, cy + math.sin(rad) * bh))
            p.setBrush(QBrush(QColor(255, 220, 100, 20)))
            p.setPen(QPen(QColor(255, 240, 150, 40), 1.0))
            for bubble_x, bubble_y, b_r in [(cx - 140, cy - 60, 16), (cx + 120, cy + 70, 22), (cx - 220, cy + 80, 12), (cx + 200, cy - 70, 18)]:
                p.drawEllipse(QPointF(bubble_x, bubble_y), b_r, b_r)

        elif pt == "psychedelic_waves":
            p.setBrush(Qt.BrushStyle.NoBrush)
            for row in range(-4, 18):
                y_base = by + row * 38
                path = QPainterPath()
                path.moveTo(bx, y_base)
                for step in range(0, int(bw) + 1, 30):
                    x = bx + step
                    y = y_base + math.sin(step * 0.035 + row * 0.5) * 16
                    path.lineTo(x, y)
                pen = QPen(QColor(255, 0, 180, 22) if row % 2 == 0 else QColor(0, 255, 230, 22), 1.8)
                p.setPen(pen)
                p.drawPath(path)

        elif pt == "gaudi_trencadis":
            p.setBrush(Qt.BrushStyle.NoBrush)
            p.setPen(QPen(QColor(255, 190, 90, 25), 1.2))
            for s in [-1, 1]:
                arc_cx = bx + layout["half_w"] / 2 if s == -1 else bx + layout["half_w"] + bar_w + layout["half_w"] / 2
                path = QPainterPath()
                path.moveTo(arc_cx - 90, by + bh - 10)
                path.quadTo(arc_cx, by + 30, arc_cx + 90, by + bh - 10)
                p.drawPath(path)
            for fx in range(int(bx) + 40, int(bx + bw) - 30, 75):
                p.drawLine(QPointF(fx, by + 20), QPointF(fx + 25, cy))
                p.drawLine(QPointF(fx + 25, cy), QPointF(fx - 15, by + bh - 20))

        elif pt == "van_gogh_swirl":
            p.setBrush(Qt.BrushStyle.NoBrush)
            for center_x, center_y in [(bx + bw * 0.28, cy), (bx + bw * 0.72, cy)]:
                for ring in range(3, 10):
                    r_span = ring * 16.0
                    path = QPainterPath()
                    for ang_deg in range(0, 360, 20):
                        ang = math.radians(ang_deg)
                        cur_r = r_span + math.sin(ang * 3.0) * 8.0
                        px_cur = center_x + math.cos(ang) * cur_r * 1.5
                        py_cur = center_y + math.sin(ang) * cur_r
                        if ang_deg == 0:
                            path.moveTo(px_cur, py_cur)
                        else:
                            path.lineTo(px_cur, py_cur)
                    path.closeSubpath()
                    pen = QPen(QColor(255, 215, 60, 28) if ring % 2 == 0 else QColor(40, 120, 220, 25), 2.2)
                    p.setPen(pen)
                    p.drawPath(path)

        elif pt == "neon_circuit":
            p.setBrush(Qt.BrushStyle.NoBrush)
            p.setPen(QPen(QColor(0, 255, 255, 18), 1.0, Qt.PenStyle.DotLine))
            p.drawLine(QPointF(bx, cy), QPointF(bx + bw, cy))
            p.drawLine(QPointF(bx + bw * 0.25, by), QPointF(bx + bw * 0.25, by + bh))
            p.drawLine(QPointF(bx + bw * 0.75, by), QPointF(bx + bw * 0.75, by + bh))
            p.setPen(QPen(QColor(0, 255, 255, 70), 1.5))
            for kx, ky, ddx, ddy in [(bx + 10, by + 10, 1, 1), (bx + bw - 10, by + 10, -1, 1), (bx + 10, by + bh - 10, 1, -1), (bx + bw - 10, by + bh - 10, -1, -1)]:
                p.drawLine(QPointF(kx, ky), QPointF(kx + ddx * 20, ky))
                p.drawLine(QPointF(kx, ky), QPointF(kx, ky + ddy * 20))

        p.restore()

    def paintEvent(self, event):
        try:
            p = QPainter(self)
            p.setRenderHint(QPainter.RenderHint.Antialiasing)
            layout = self._layout()
            th = self.theme

            # Background
            p.fillRect(self.rect(), th.bg_dark)

            # Board surface
            br = layout["board_rect"]
            p.setPen(QPen(th.board_frame, 3.5))
            p.setBrush(QBrush(th.board_felt))
            p.drawRoundedRect(br, 8, 8)

            # Raster background art underlay if available for theme
            bg_pix = self.theme_pixmaps.get(self.theme_id)
            if bg_pix and not bg_pix.isNull():
                p.save()
                p.setClipRect(br.adjusted(2, 2, -2, -2))
                p.setOpacity(0.55)
                p.drawPixmap(br.toRect(), bg_pix)
                p.restore()

            # Procedural theme ornament
            self._draw_theme_ornament(p, layout, th)

            # Inner accent frame border
            p.setPen(QPen(th.board_border, 1.2))
            p.setBrush(Qt.BrushStyle.NoBrush)
            p.drawRoundedRect(br.adjusted(4, 4, -4, -4), 6, 6)

            # Bar zone
            bar_rect = QRectF(layout["bar_x"], br.y(), layout["bar_w"], br.height())
            p.setBrush(QBrush(th.bar_bg))
            p.setPen(Qt.PenStyle.NoPen)
            p.drawRect(bar_rect)

            # Draw triangles
            for i in range(24):
                rect, is_top = self._point_rect(i, layout)
                color = th.tri_light if i % 2 == 0 else th.tri_dark
                # Make triangles semi-transparent so the intricate art underlay shines through!
                alpha_base = 185
                color = QColor(color.red(), color.green(), color.blue(), alpha_base)
                pen = QPen(th.tri_light_border if i % 2 == 0 else th.tri_dark_border, 1.2)

                # Check if this point is a valid destination path
                path_to_point = next((pth for pth in self.reachable_paths if pth.final_to_abs == i), None)

                if i == self._hover_point:
                    color = color.lighter(130)

                if i == self.selected_point:
                    color = QColor(0, 255, 200, 220)
                    pen = QPen(QColor(0, 255, 255), 2.5)

                if path_to_point is not None:
                    # RED LINE OUTLINE as requested by user ("подсветку ходов надо делать обводкой полей доступных для ходов выбраной фишкой красной линией")
                    if path_to_point.is_multi_step:
                        # Multi-step combo move: thick intense bright neon red outline
                        pen = QPen(QColor(255, 25, 55), 3.5)
                        color = QColor(140, 20, 35, 120)
                    else:
                        # Single-step move: sharp bright red line outline
                        pen = QPen(QColor(255, 45, 65), 3.0)
                        color = QColor(120, 15, 25, 95)

                path = QPainterPath()
                if is_top:
                    path.moveTo(rect.left(), rect.top())
                    path.lineTo(rect.right(), rect.top())
                    path.lineTo(rect.center().x(), rect.bottom())
                else:
                    path.moveTo(rect.left(), rect.bottom())
                    path.lineTo(rect.right(), rect.bottom())
                    path.lineTo(rect.center().x(), rect.top())
                path.closeSubpath()
                p.setBrush(QBrush(color))
                p.setPen(pen)
                p.drawPath(path)

                # Point number
                font_small = QFont("Monospace", 7)
                p.setFont(font_small)
                p.setPen(th.text_gold)
                num_y = rect.bottom() + 12 if not is_top else rect.top() - 4
                p.drawText(QRectF(rect.x(), num_y - 10, rect.width(), 14),
                           Qt.AlignmentFlag.AlignCenter, str(i + 1))

                # If this is a destination, draw glowing move badge with dice info
                if path_to_point is not None:
                    badge_w = 46
                    badge_h = 16
                    bx = rect.center().x() - badge_w / 2
                    by = rect.bottom() - 26 if not is_top else rect.top() + 10

                    is_multi = path_to_point.is_multi_step
                    p.setBrush(QBrush(QColor(40, 10, 15, 235)))
                    p.setPen(QPen(QColor(255, 40, 60), 1.5))
                    p.drawRoundedRect(QRectF(bx, by, badge_w, badge_h), 4, 4)

                    p.setFont(QFont("Monospace", 7, QFont.Weight.Bold))
                    p.setPen(QColor(255, 200, 200) if is_multi else QColor(255, 230, 230))
                    if is_multi:
                        badge_txt = f"⚡+{sum(path_to_point.total_dice_used)}"
                    else:
                        badge_txt = f"+{path_to_point.steps[0].die_used}"
                    p.drawText(QRectF(bx, by, badge_w, badge_h), Qt.AlignmentFlag.AlignCenter, badge_txt)

            # Draw checkers on points
            pw = layout["point_w"]
            checker_d = pw * self.CHECKER_RATIO
            for i in range(24):
                stack = self.board[i]
                eff_count = stack.count
                if self.is_checker_animating and self.checker_anim_steps:
                    curr_step = self.checker_anim_steps[self.checker_anim_step_idx]
                    if curr_step.from_abs == i and stack.player == self.checker_anim_player:
                        eff_count = max(0, eff_count - 1)

                if eff_count == 0:
                    continue
                rect, is_top = self._point_rect(i, layout)
                eff_stack = CheckerStack(count=eff_count, player=stack.player)
                self._draw_checker_stack(p, rect, is_top, eff_stack, checker_d)

            # Draw bar checkers
            self._draw_bar_checkers(p, layout)

            # Draw bear-off zones
            self._draw_bear_off(p, layout)

            # Draw last move indicators (where checker came from and where it landed)
            self._draw_last_move_markers(p, layout)

            # Draw flying animated checker
            if self.is_checker_animating:
                self._draw_flying_checker(p, layout)

            # Draw dice
            self._draw_dice(p, layout)

            # Player indicator
            self._draw_player_indicator(p, layout)

            p.end()
        except Exception as e:
            log.exception("Error in paintEvent: %s", e)

    def _draw_checker_stack(self, p: QPainter, tri_rect: QRectF, is_top: bool,
                            stack: CheckerStack, checker_d: float):
        th = self.theme
        if stack.player == Player.WHITE:
            outline = th.checker_white_outline
            start_col = th.checker_white_start
            end_col = th.checker_white_end
        else:
            outline = th.checker_black_outline
            start_col = th.checker_black_start
            end_col = th.checker_black_end

        cx = tri_rect.center().x()
        max_show = min(stack.count, 5)
        spacing = min(checker_d * 0.9, tri_rect.height() / max(max_show, 1))

        for j in range(max_show):
            if is_top:
                cy = tri_rect.top() + checker_d / 2 + j * spacing
            else:
                cy = tri_rect.bottom() - checker_d / 2 - j * spacing

            grad = QRadialGradient(cx - checker_d * 0.15, cy - checker_d * 0.15, checker_d * 0.6)
            grad.setColorAt(0, start_col)
            grad.setColorAt(1, end_col)

            p.setBrush(QBrush(grad))
            p.setPen(QPen(outline, 1.5))
            p.drawEllipse(QPointF(cx, cy), checker_d / 2, checker_d / 2)

        if stack.count > 5:
            p.setFont(QFont("Monospace", 9, QFont.Weight.Bold))
            p.setPen(th.text_cyan)
            text_y = tri_rect.top() + checker_d / 2 if is_top else tri_rect.bottom() - checker_d / 2
            p.drawText(QRectF(cx - 15, text_y - 8, 30, 16),
                       Qt.AlignmentFlag.AlignCenter, str(stack.count))

    def _draw_bar_checkers(self, p: QPainter, layout: dict):
        bar_x = layout["bar_x"]
        bar_w = layout["bar_w"]
        bh = layout["board_rect"].height()
        by = layout["board_rect"].y()
        cd = bar_w * 0.7
        th = self.theme

        eff_w = self.bar_white
        eff_b = self.bar_black
        if self.is_checker_animating and self.checker_anim_steps:
            curr_step = self.checker_anim_steps[self.checker_anim_step_idx]
            if curr_step.from_abs == -1:
                if self.checker_anim_player == Player.WHITE:
                    eff_w = max(0, eff_w - 1)
                else:
                    eff_b = max(0, eff_b - 1)

        for count, player, y_start, down in [
            (eff_w, Player.WHITE, by + bh / 2 + 20, True),
            (eff_b, Player.BLACK, by + bh / 2 - 20, False)
        ]:
            if player == Player.WHITE:
                outline = th.checker_white_outline
                start_c = th.checker_white_start
                end_c = th.checker_white_end
            else:
                outline = th.checker_black_outline
                start_c = th.checker_black_start
                end_c = th.checker_black_end

            for j in range(min(count, 5)):
                cy = y_start + (j * cd * 0.9 if down else -j * cd * 0.9)
                cx = bar_x + bar_w / 2
                grad = QRadialGradient(cx - cd * 0.15, cy - cd * 0.15, cd * 0.6)
                grad.setColorAt(0, start_c)
                grad.setColorAt(1, end_c)
                p.setBrush(QBrush(grad))
                p.setPen(QPen(outline, 1.5))
                p.drawEllipse(QPointF(cx, cy), cd / 2, cd / 2)
            if count > 0:
                p.setFont(QFont("Monospace", 8, QFont.Weight.Bold))
                p.setPen(th.text_cyan)
                ty = y_start + 5 if down else y_start - 15
                p.drawText(QRectF(bar_x, ty, bar_w, 16),
                           Qt.AlignmentFlag.AlignCenter, f"×{count}")

    def _draw_bear_off(self, p: QPainter, layout: dict):
        bear_path = next((pth for pth in self.reachable_paths if pth.final_to_abs == 24), None)
        th = self.theme

        opp_player = Player.opponent(self.head_player)
        borne_head = self.borne_off_white if self.head_player == Player.WHITE else self.borne_off_black
        borne_opp = self.borne_off_black if self.head_player == Player.WHITE else self.borne_off_white

        for count, player, rect_key in [
            (borne_head, self.head_player, "bear_right"),
            (borne_opp, opp_player, "bear_left")
        ]:
            rect = layout[rect_key]
            is_active_bear_off = (bear_path is not None and player == self.current_player)

            if is_active_bear_off:
                # RED LINE OUTLINE for bear-off zone as requested
                p.setPen(QPen(QColor(255, 30, 60), 3.5))
                p.setBrush(QBrush(QColor(60, 15, 20, 220)))
            else:
                p.setPen(QPen(th.board_frame, 2))
                p.setBrush(QBrush(th.bar_bg))

            p.drawRoundedRect(rect, 6, 6)

            p.setFont(QFont("Monospace", 9, QFont.Weight.Bold))
            p.setPen(QColor(255, 100, 120) if is_active_bear_off else th.text_gold)
            label = "W" if player == Player.WHITE else "B"

            if is_active_bear_off:
                dice_sum = sum(bear_path.total_dice_used)
                top_txt = f"👑СБРОС\n+{dice_sum}\n({count}/15)"
            else:
                top_txt = f"OFF\n{label}:{count}"

            p.drawText(rect, Qt.AlignmentFlag.AlignTop | Qt.AlignmentFlag.AlignHCenter, top_txt)

            if player == Player.WHITE:
                outline = th.checker_white_outline
                start_c = th.checker_white_start
                end_c = th.checker_white_end
            else:
                outline = th.checker_black_outline
                start_c = th.checker_black_start
                end_c = th.checker_black_end

            cd = rect.width() * 0.6
            for j in range(min(count, 15)):
                cy = rect.bottom() - 10 - j * (cd * 0.35)
                cx = rect.center().x()
                grad = QRadialGradient(cx - cd * 0.15, cy - cd * 0.1, cd * 0.6)
                grad.setColorAt(0, start_c)
                grad.setColorAt(1, end_c)
                p.setBrush(QBrush(grad))
                p.setPen(QPen(outline, 1))
                p.drawEllipse(QPointF(cx, cy), cd / 2, cd * 0.2)

    def _draw_last_move_markers(self, p: QPainter, layout: dict):
        """Highlights where the checker came from and where it landed so moved checkers are immediately noticed."""
        if self.last_move_src == -2 or self.last_move_dst == -2:
            return

        player = self.last_move_player or self.current_player
        pw = layout["point_w"]
        checker_d = pw * self.CHECKER_RATIO

        # 1. Source departure marker (dashed ring showing origin)
        p_src = self.get_point_pos(self.last_move_src, player, layout, is_source=True)
        p.setPen(QPen(QColor(255, 190, 40, 200), 2.0, Qt.PenStyle.DashLine))
        p.setBrush(QBrush(QColor(255, 190, 40, 28)))
        p.drawEllipse(p_src, checker_d * 0.44, checker_d * 0.44)

        # 2. Destination landing marker (radiant neon ring around arrived checker)
        p_dst = self.get_point_pos(self.last_move_dst, player, layout, is_source=True)
        if self.last_move_dst != 24:
            p.setPen(QPen(QColor(0, 255, 220, 230), 2.5))
            p.setBrush(Qt.BrushStyle.NoBrush)
            p.drawEllipse(p_dst, checker_d * 0.52, checker_d * 0.52)

            p.setPen(QPen(QColor(0, 255, 220, 110), 1.5, Qt.PenStyle.DotLine))
            p.drawEllipse(p_dst, checker_d * 0.60, checker_d * 0.60)

    def _draw_flying_checker(self, p: QPainter, layout: dict):
        """Draws the animated checker in 3D parabolic elevation hop with drop shadow and motion blur."""
        if not self.is_checker_animating or not self.checker_anim_steps:
            return

        pw = layout["point_w"]
        base_d = pw * self.CHECKER_RATIO
        curr_step = self.checker_anim_steps[self.checker_anim_step_idx]

        pos = self.checker_anim_current_pos
        elevation = self.checker_anim_elevation
        scale = 1.0 + (elevation / 40.0) * 0.18
        d = base_d * scale
        r = d / 2.0

        # Motion trail
        for t_pos, t_scale, t_alpha in self.checker_anim_trail:
            t_d = base_d * t_scale
            trail_color = QColor(255, 255, 255, int(t_alpha * 0.35)) if self.checker_anim_player == Player.WHITE else QColor(40, 40, 70, int(t_alpha * 0.35))
            p.setBrush(QBrush(trail_color))
            p.setPen(Qt.PenStyle.NoPen)
            p.drawEllipse(t_pos, t_d / 2.0, t_d / 2.0)

        # Dynamic Drop Shadow projected onto board surface
        shadow_x = pos.x() + 4.0 + elevation * 0.08
        shadow_y = pos.y() + elevation + 6.0 + elevation * 0.12
        shadow_alpha = max(15, int(110 - elevation * 1.5))
        shadow_r = (base_d / 2.0) * (1.0 - elevation * 0.005)
        p.setBrush(QBrush(QColor(0, 0, 0, shadow_alpha)))
        p.setPen(Qt.PenStyle.NoPen)
        p.drawEllipse(QPointF(shadow_x, shadow_y), shadow_r, shadow_r * 0.8)

        # Checker body
        cx = pos.x()
        cy = pos.y()
        th = self.theme
        grad = QRadialGradient(cx - d * 0.15, cy - d * 0.15, d * 0.6)
        if self.checker_anim_player == Player.WHITE:
            grad.setColorAt(0.0, th.checker_white_start)
            grad.setColorAt(0.7, th.checker_white_end)
            grad.setColorAt(1.0, th.checker_white_end.darker(120))
            outline_color = th.checker_white_outline
        else:
            grad.setColorAt(0.0, th.checker_black_start)
            grad.setColorAt(0.7, th.checker_black_end)
            grad.setColorAt(1.0, th.checker_black_end.darker(130))
            outline_color = th.checker_black_outline

        p.setBrush(QBrush(grad))
        p.setPen(QPen(outline_color, 2.2))
        p.drawEllipse(QPointF(cx, cy), r, r)

        # 3D edge gloss
        p.setBrush(Qt.BrushStyle.NoBrush)
        p.setPen(QPen(QColor(255, 255, 255, 140), 1.0))
        p.drawEllipse(QPointF(cx - 1, cy - 1), r * 0.85, r * 0.85)

        # Floating die number tag above checker if die is known
        if curr_step.die_used > 0:
            badge_w = 28
            badge_h = 14
            bx = cx - badge_w / 2
            by = cy - r - 12
            p.setBrush(QBrush(QColor(20, 20, 30, 225)))
            p.setPen(QPen(outline_color, 1.2))
            p.drawRoundedRect(QRectF(bx, by, badge_w, badge_h), 3, 3)
            p.setFont(QFont("Monospace", 7, QFont.Weight.Bold))
            p.setPen(outline_color)
            p.drawText(QRectF(bx, by, badge_w, badge_h), Qt.AlignmentFlag.AlignCenter, f"+{curr_step.die_used}")

    def _draw_tumbling_die(self, p: QPainter, die: PhysicsDie):
        base_size = 42.0
        scale = 1.0 + die.z * 0.0045
        size = base_size * scale

        # 1. Soft Dynamic Shadow projected onto board felt
        shadow_dx = 5.0 + die.z * 0.35
        shadow_dy = 7.0 + die.z * 0.55
        shadow_alpha = max(15, int(120 - die.z * 0.95))
        shadow_rect = QRectF(-size / 2, -size / 2, size, size)

        p.save()
        p.translate(die.x + shadow_dx, die.y + shadow_dy)
        p.rotate(die.angle * 0.45)
        p.setBrush(QBrush(QColor(0, 0, 0, shadow_alpha)))
        p.setPen(Qt.PenStyle.NoPen)
        p.drawRoundedRect(shadow_rect, 8 * scale, 8 * scale)
        p.restore()

        # 2. 3D Tumbling Cube
        p.save()
        p.translate(die.x, die.y - die.z)
        p.rotate(die.angle)

        cube_rect = QRectF(-size / 2, -size / 2, size, size)

        # Ivory gradient with specular highlight
        grad = QLinearGradient(cube_rect.topLeft(), cube_rect.bottomRight())
        grad.setColorAt(0.0, QColor(255, 255, 252))
        grad.setColorAt(0.35, QColor(248, 244, 232))
        grad.setColorAt(0.85, QColor(228, 218, 198))
        grad.setColorAt(1.0, QColor(205, 192, 170))

        p.setBrush(QBrush(grad))
        p.setPen(QPen(QColor(60, 42, 25, 230), 1.8 * scale))
        p.drawRoundedRect(cube_rect, 7.5 * scale, 7.5 * scale)

        # Inner highlight border
        inner_rect = cube_rect.adjusted(1.5 * scale, 1.5 * scale, -1.5 * scale, -1.5 * scale)
        p.setPen(QPen(QColor(255, 255, 255, 170), 1.0 * scale))
        p.setBrush(Qt.BrushStyle.NoBrush)
        p.drawRoundedRect(inner_rect, 6.0 * scale, 6.0 * scale)

        # Draw Pips
        self._draw_die_dots(p, cube_rect, die.current_val, scale)
        p.restore()

    def _draw_roll_prompt(self, p: QPainter, layout: dict):
        br = layout["board_rect"]
        cx, cy = br.center().x(), br.center().y()
        w, h = 280, 52
        rect = QRectF(cx - w / 2, cy - h / 2, w, h)

        pulse = (math.sin(self.roll_prompt_pulse) + 1.0) / 2.0  # 0.0 to 1.0
        alpha_glow = int(140 + 80 * pulse)
        border_col = QColor(0, 230, 255, alpha_glow)
        bg_col = QColor(10, 25, 38, int(190 + 50 * pulse))

        # Soft backdrop glow
        glow = QRadialGradient(rect.center(), w * 0.7)
        glow.setColorAt(0, QColor(0, 230, 255, int(45 * pulse)))
        glow.setColorAt(1, QColor(0, 0, 0, 0))
        p.setBrush(QBrush(glow))
        p.setPen(Qt.PenStyle.NoPen)
        p.drawEllipse(rect.adjusted(-25, -25, 25, 25))

        # Glowing badge
        p.setBrush(QBrush(bg_col))
        p.setPen(QPen(border_col, 2.2))
        p.drawRoundedRect(rect, 10, 10)

        # Prompt text
        p.setFont(QFont("Monospace", 10, QFont.Weight.Bold))
        p.setPen(border_col)
        txt = "🎲 КЛИКНИТЕ ДЛЯ БРОСКА"
        p.drawText(QRectF(rect.x(), rect.y() + 7, rect.width(), 19),
                   Qt.AlignmentFlag.AlignCenter, txt)

        p.setFont(QFont("Monospace", 8))
        p.setPen(QColor(160, 220, 240, int(160 + 75 * pulse)))
        txt_sub = "Кликните в любом месте доски"
        p.drawText(QRectF(rect.x(), rect.y() + 28, rect.width(), 16),
                   Qt.AlignmentFlag.AlignCenter, txt_sub)

    def _draw_dice(self, p: QPainter, layout: dict):
        if self.is_dice_rolling:
            for die in self.dice_anim_dice:
                self._draw_tumbling_die(p, die)
            return

        if self.can_roll_dice:
            self._draw_roll_prompt(p, layout)
            return

        if self.dice[0] == 0 and self.dice[1] == 0:
            return

        # Draw landed dice at their resting positions
        br = layout["board_rect"]
        dice_size = 42.0
        gap = 16.0
        total_w = dice_size * 2 + gap
        dx = br.center().x() - total_w / 2
        dy = br.center().y() - dice_size / 2

        for idx, val in enumerate(self.dice):
            x = dx + idx * (dice_size + gap)
            rect = QRectF(x, dy, dice_size, dice_size)

            # Drop shadow
            p.setBrush(QBrush(QColor(0, 0, 0, 110)))
            p.setPen(Qt.PenStyle.NoPen)
            p.drawRoundedRect(rect.translated(3, 5), 7, 7)

            # Neon aura glow
            glow = QRadialGradient(rect.center(), dice_size * 1.1)
            glow.setColorAt(0, C_DICE_GLOW)
            glow.setColorAt(1, QColor(0, 0, 0, 0))
            p.setBrush(QBrush(glow))
            p.drawEllipse(rect.adjusted(-10, -10, 10, 10))

            # Ivory / Cyberpunk gradient
            grad = QLinearGradient(rect.topLeft(), rect.bottomRight())
            grad.setColorAt(0.0, QColor(255, 255, 252))
            grad.setColorAt(0.4, QColor(248, 244, 232))
            grad.setColorAt(1.0, QColor(220, 210, 190))

            p.setBrush(QBrush(grad))
            p.setPen(QPen(QColor(60, 42, 25, 220), 1.8))
            p.drawRoundedRect(rect, 7, 7)

            # Highlight border
            inner_rect = rect.adjusted(1.5, 1.5, -1.5, -1.5)
            p.setPen(QPen(QColor(255, 255, 255, 180), 1.0))
            p.setBrush(Qt.BrushStyle.NoBrush)
            p.drawRoundedRect(inner_rect, 6, 6)

            used = idx < len(self.dice_used) and self.dice_used[idx]
            if used:
                # Dimmed overlay for used die
                p.setBrush(QBrush(QColor(20, 20, 30, 175)))
                p.setPen(Qt.PenStyle.NoPen)
                p.drawRoundedRect(rect, 7, 7)

            self._draw_die_dots(p, rect, val, 1.0)

    def _draw_die_dots(self, p: QPainter, rect: QRectF, val: int, scale: float = 1.0):
        dot_r = 3.6 * scale
        cx, cy = rect.center().x(), rect.center().y()
        w4 = rect.width() / 4.0
        h4 = rect.height() / 4.0
        p.setPen(Qt.PenStyle.NoPen)

        positions = {
            1: [(cx, cy)],
            2: [(cx - w4, cy - h4), (cx + w4, cy + h4)],
            3: [(cx - w4, cy - h4), (cx, cy), (cx + w4, cy + h4)],
            4: [(cx - w4, cy - h4), (cx + w4, cy - h4),
                (cx - w4, cy + h4), (cx + w4, cy + h4)],
            5: [(cx - w4, cy - h4), (cx + w4, cy - h4), (cx, cy),
                (cx - w4, cy + h4), (cx + w4, cy + h4)],
            6: [(cx - w4, cy - h4), (cx + w4, cy - h4),
                (cx - w4, cy), (cx + w4, cy),
                (cx - w4, cy + h4), (cx + w4, cy + h4)],
        }

        dot_list = positions.get(val, [])
        for (dx, dy) in dot_list:
            if val == 1:
                # Oriental Zariki: single ace dot is crimson ruby
                p.setBrush(QBrush(QColor(215, 35, 45)))
                p.drawEllipse(QPointF(dx, dy), dot_r * 1.35, dot_r * 1.35)
            else:
                p.setBrush(QBrush(QColor(25, 25, 35)))
                p.drawEllipse(QPointF(dx, dy), dot_r, dot_r)

    def _draw_player_indicator(self, p: QPainter, layout: dict):
        br = layout["board_rect"]
        p.setFont(QFont("Monospace", 11, QFont.Weight.Bold))
        color = self.theme.checker_white_start if self.current_player == Player.WHITE else self.theme.text_cyan
        p.setPen(color)
        text = f"⬤ {'White / Белые' if self.current_player == Player.WHITE else 'Black / Чёрные'}"
        p.drawText(QRectF(br.x(), br.bottom() + 4, br.width(), 20),
                   Qt.AlignmentFlag.AlignCenter, text)

    def mousePressEvent(self, event):
        if event.button() != Qt.MouseButton.LeftButton:
            return
        if self.is_dice_rolling or self.is_checker_animating:
            return

        layout = self._layout()
        mx, my = event.position().x(), event.position().y()
        click_pos = QPointF(mx, my)

        bar_rect = QRectF(layout["bar_x"], layout["board_rect"].y(),
                          layout["bar_w"], layout["board_rect"].height())

        # If waiting to roll dice, ANY click on the board (felt, triangles, bar, bear zone) rolls!
        if self.can_roll_dice:
            if (layout["board_rect"].contains(click_pos) or
                layout["bear_left"].contains(click_pos) or
                layout["bear_right"].contains(click_pos)):
                if bar_rect.contains(click_pos):
                    # Check knock-code first
                    self.point_clicked.emit(-1)
                else:
                    self.dice_roll_requested.emit()
                return

        # Normal interaction:
        if bar_rect.contains(click_pos):
            log.debug("Clicked BAR")
            self.point_clicked.emit(-1)
            return

        for i in range(24):
            rect, is_top = self._point_rect(i, layout)
            if rect.contains(click_pos):
                log.debug("Clicked point %d", i)
                self.point_clicked.emit(i)
                return

        # Check bear-off zones (Right for head_player, Left for opponent)
        if layout["bear_right"].contains(click_pos) and self.current_player == self.head_player:
            log.debug("Clicked head player bear-off zone")
            self.point_clicked.emit(24)
            return
        elif layout["bear_left"].contains(click_pos) and self.current_player != self.head_player:
            log.debug("Clicked opponent bear-off zone")
            self.point_clicked.emit(24)
            return

        # Click on empty felt / center
        if layout["board_rect"].contains(click_pos):
            self.dice_roll_requested.emit()

    def mouseMoveEvent(self, event):
        layout = self._layout()
        mx, my = event.position().x(), event.position().y()
        click_pos = QPointF(mx, my)

        if self.can_roll_dice:
            if layout["board_rect"].contains(click_pos):
                self.setCursor(Qt.CursorShape.PointingHandCursor)
                return

        old_hover = self._hover_point
        self._hover_point = -2
        has_hand = False
        for i in range(24):
            rect, _ = self._point_rect(i, layout)
            if rect.contains(click_pos):
                self._hover_point = i
                if (self.board[i].player == self.current_player and self.board[i].count > 0) or i in self.valid_destinations:
                    has_hand = True
                break

        if has_hand:
            self.setCursor(Qt.CursorShape.PointingHandCursor)
        else:
            self.setCursor(Qt.CursorShape.ArrowCursor)

        if self._hover_point != old_hover:
            self.update()


# ═══════════════════════════════════════════════════════
#  P2P CONNECTION DIALOG
# ═══════════════════════════════════════════════════════
class P2PDialog(QDialog):
    """Dialog for hosting or joining a P2P game."""
    connect_requested = pyqtSignal(str, int, bool)

    def __init__(self, parent=None):
        super().__init__(parent)
        log.debug("Initializing P2PDialog")
        self.setWindowTitle("🌐 P2P Network Game — MadGammon")
        self.setMinimumWidth(420)
        self.setStyleSheet(f"""
            QDialog {{ background: {C_BG_DARK.name()}; color: {C_TEXT_WHITE.name()}; }}
            QGroupBox {{ border: 1px solid {C_TRI_GOLD.name()}; margin-top: 10px;
                         padding-top: 14px; color: {C_TEXT_GOLD.name()}; }}
            QGroupBox::title {{ subcontrol-origin: margin; left: 10px; padding: 0 3px; }}
            QLineEdit, QSpinBox {{ background: {C_BG_PANEL.name()};
                                   border: 1px solid {C_TRI_EMERALD.name()};
                                   color: {C_TEXT_WHITE.name()}; padding: 4px; }}
            QPushButton {{ background: {C_BUTTON_BG.name()}; color: {C_TEXT_GOLD.name()};
                           border: 1px solid {C_TRI_GOLD.name()}; padding: 8px 16px;
                           border-radius: 4px; }}
            QPushButton:hover {{ background: {C_BUTTON_HOVER.name()}; }}
            QTextEdit {{ background: {C_CHAT_BG.name()}; color: {C_TEXT_CYAN.name()};
                         border: 1px solid #333; }}
        """)

        layout = QVBoxLayout(self)

        mode_group = QGroupBox("Connection Mode / Режим подключения")
        mode_layout = QVBoxLayout()
        self.radio_host = QRadioButton("🖥️  Host Game (Создать игру)")
        self.radio_join = QRadioButton("🔗  Join Game (Присоединиться)")
        self.radio_host.setChecked(True)
        self.radio_host.setStyleSheet(f"color: {C_TEXT_WHITE.name()};")
        self.radio_join.setStyleSheet(f"color: {C_TEXT_WHITE.name()};")
        mode_layout.addWidget(self.radio_host)
        mode_layout.addWidget(self.radio_join)
        mode_group.setLayout(mode_layout)
        layout.addWidget(mode_group)

        params_group = QGroupBox("Parameters / Параметры")
        params_layout = QVBoxLayout()

        lan_ip = get_local_lan_ip()
        self.ip_label = QLabel(f"Your LAN IP / Ваш IP: {lan_ip}")
        self.ip_label.setStyleSheet(f"color: {C_TEXT_CYAN.name()}; font-weight: bold;")
        params_layout.addWidget(self.ip_label)

        h_layout = QHBoxLayout()
        h_layout.addWidget(QLabel("Host IP:"))
        self.ip_input = QLineEdit(lan_ip)
        h_layout.addWidget(self.ip_input)
        h_layout.addWidget(QLabel("Port:"))
        self.port_input = QSpinBox()
        self.port_input.setRange(1024, 65535)
        self.port_input.setValue(8080)
        h_layout.addWidget(self.port_input)
        params_layout.addLayout(h_layout)
        params_group.setLayout(params_layout)
        layout.addWidget(params_group)

        self.status_log = QTextEdit()
        self.status_log.setReadOnly(True)
        self.status_log.setMaximumHeight(120)
        layout.addWidget(self.status_log)

        btn_layout = QHBoxLayout()
        self.btn_connect = QPushButton("🚀 Connect / Подключиться")
        self.btn_connect.clicked.connect(self._on_connect)
        self.btn_cancel = QPushButton("Cancel")
        self.btn_cancel.clicked.connect(self.reject)
        btn_layout.addWidget(self.btn_connect)
        btn_layout.addWidget(self.btn_cancel)
        layout.addLayout(btn_layout)

        self.radio_host.toggled.connect(self._mode_changed)
        self._mode_changed()

    def _mode_changed(self):
        is_host = self.radio_host.isChecked()
        self.ip_input.setEnabled(not is_host)
        if is_host:
            self.ip_input.setText(get_local_lan_ip())

    def _on_connect(self):
        is_host = self.radio_host.isChecked()
        host = self.ip_input.text().strip()
        port = self.port_input.value()
        log.info("P2P Dialog Connect clicked: is_host=%s, host=%s, port=%d", is_host, host, port)
        self.status_log.append(f"→ {'Hosting' if is_host else 'Joining'} on {host}:{port}...")
        self.connect_requested.emit(host, port, is_host)

    def append_status(self, msg: str):
        self.status_log.append(msg)


# ═══════════════════════════════════════════════════════
#  NETWORK SIGNAL BRIDGE
# ═══════════════════════════════════════════════════════
class NetworkBridge(QObject):
    message_received = pyqtSignal(str, str)
    status_changed = pyqtSignal(str)


# ═══════════════════════════════════════════════════════
#  MAIN WINDOW
# ═══════════════════════════════════════════════════════
class MadGammonWindow(QMainWindow):
    def __init__(self):
        super().__init__()
        log.info("Initializing MadGammonWindow...")
        self.setWindowTitle("MadGammon — Шеш-Беш / BG-AG")
        self.setMinimumSize(900, 650)
        self.resize(1100, 750)

        icon_path = os.path.join(os.path.dirname(__file__), "icons", "madgammon.png")
        if os.path.exists(icon_path):
            log.info("Loaded window icon: %s", icon_path)
            self.setWindowIcon(QIcon(icon_path))
            self._icon_path = icon_path
        else:
            log.warning("Window icon NOT found at: %s", icon_path)
            self._icon_path = None

        self.setStyleSheet(f"""
            QMainWindow {{ background: {C_BG_DARK.name()}; }}
            QStatusBar {{ background: {C_BG_PANEL.name()}; color: {C_TEXT_GOLD.name()};
                          border-top: 1px solid {C_TRI_GOLD.name()}; }}
            QPushButton {{ background: {C_BUTTON_BG.name()}; color: {C_TEXT_GOLD.name()};
                           border: 1px solid {C_TRI_GOLD.name()}; padding: 6px 12px;
                           border-radius: 3px; font-weight: bold; }}
            QPushButton:hover {{ background: {C_BUTTON_HOVER.name()}; }}
            QLabel {{ color: {C_TEXT_WHITE.name()}; }}
        """)

        # Game State
        self.game_mode = GameMode.LONG
        self.head_player = Player.WHITE
        self.board = BackgammonRules.create_initial_board(self.game_mode, head_player=self.head_player)
        self.bar_white = 0
        self.bar_black = 0
        self.borne_off_white = 0
        self.borne_off_black = 0
        self.current_player = Player.WHITE
        self.dice = (0, 0)
        self.dice_used = [False, False]
        self.remaining_dice: List[int] = []
        self.selected_source = -2
        self.is_network_game = False
        self.local_color = Player.WHITE
        self.game_started = False
        self.game_over = False

        # AI Opponent State
        self.vs_bot = True
        self.bot_color = Player.BLACK
        self.bot_difficulty = AIDifficulty.MEDIUM
        self.double_waterfall_queue: List[List[int]] = []
        self.kush_roller: Optional[str] = None

        # Network
        self.net_bridge = NetworkBridge()
        self.net_bridge.message_received.connect(self._on_net_message)
        self.net_bridge.status_changed.connect(self._on_net_status)
        self.network = P2PNetworkManager(
            on_message=lambda a, p: self.net_bridge.message_received.emit(a, p),
            on_status=lambda s: self.net_bridge.status_changed.emit(s)
        )
        self.mental_dice = MentalDiceEngine()
        self.p2p_dialog: Optional[P2PDialog] = None

        # Audio Radio Player & Secret Layer
        self.radio_player = RadioPlayer(on_state_changed=self._on_radio_state_changed)
        self.radio_dialog: Optional[RadioDialog] = None
        self.secret_window: Optional[SecretCovertMessengerWindow] = None
        self.bar_click_timestamps = []

        # Secret Hotkey: Ctrl+Shift+S triggers covert layer
        self.secret_shortcut = QShortcut(QKeySequence("Ctrl+Shift+S"), self)
        self.secret_shortcut.activated.connect(self._open_secret_pin_dialog)

        # Build UI
        self._build_ui()
        self._setup_tray()
        self._update_board()
        log.info("MadGammonWindow initialized successfully.")

    def _build_ui(self):
        log.debug("Building UI layout...")
        central = QWidget()
        self.setCentralWidget(central)
        main_layout = QHBoxLayout(central)
        main_layout.setContentsMargins(4, 4, 4, 4)

        splitter = QSplitter(Qt.Orientation.Horizontal)

        board_panel = QWidget()
        board_layout = QVBoxLayout(board_panel)
        board_layout.setContentsMargins(0, 0, 0, 0)

        self.board_widget = BoardWidget()
        self.board_widget.point_clicked.connect(self._on_point_click)
        self.board_widget.dice_roll_requested.connect(self._on_roll_dice)
        board_layout.addWidget(self.board_widget, 1)

        # Retain internal state controllers
        self.btn_roll = QPushButton("🎲 Roll Dice")
        self.btn_roll.clicked.connect(self._on_roll_dice)
        self.btn_end_turn = QPushButton("✓ End Turn")
        self.btn_end_turn.clicked.connect(self._on_end_turn)
        self.btn_undo = QPushButton("↩ Undo")
        self.btn_undo.clicked.connect(self._on_undo)
        self.mode_combo = QComboBox()
        self.mode_combo.addItems(["LONG (Длинные)", "SHORT (Короткие)", "MAD (Бешеные)"])
        self.mode_combo.currentIndexChanged.connect(self._on_mode_changed)
        self.opponent_combo = QComboBox()
        self.opponent_combo.addItems(["🤖 vs AI Bot", "👥 Hotseat (2P)"])
        self.opponent_combo.currentIndexChanged.connect(self._on_opponent_mode_changed)
        self.difficulty_combo = QComboBox()
        self.difficulty_combo.addItems(["🟡 Medium (Мастер)", "🔴 Hard (Гроссмейстер)", "🟢 Easy (Новичок)"])
        self.difficulty_combo.currentIndexChanged.connect(self._on_difficulty_changed)

        splitter.addWidget(board_panel)

        # ─── Right Info & Chat Panel ───
        chat_panel = QWidget()
        chat_panel.setMinimumWidth(260)
        chat_panel.setMaximumWidth(360)
        chat_layout = QVBoxLayout(chat_panel)
        chat_layout.setContentsMargins(4, 4, 4, 4)
        chat_layout.setSpacing(6)

        # Header with Gear Menu button
        header_widget = QWidget()
        header_layout = QHBoxLayout(header_widget)
        header_layout.setContentsMargins(2, 2, 2, 2)

        lbl_title = QLabel("💬 ЧАТ И ИГРА")
        lbl_title.setStyleSheet(f"color: {C_TEXT_GOLD.name()}; font-weight: bold; font-size: 12px; font-family: Monospace;")
        header_layout.addWidget(lbl_title)

        header_layout.addStretch()

        self.btn_gear = QPushButton("⚙️ МЕНЮ")
        self.btn_gear.setToolTip("Меню всех команд: бросок, ход, смена режима, выбор бота, радио и секретный слой")
        self.btn_gear.setStyleSheet(f"""
            QPushButton {{
                background: {C_BG_PANEL.name()};
                color: {C_TEXT_GOLD.name()};
                border: 1.5px solid {C_TRI_GOLD.name()};
                padding: 4px 10px;
                border-radius: 4px;
                font-weight: bold;
                font-size: 11px;
            }}
            QPushButton:hover {{
                background: {C_BUTTON_HOVER.name()};
                border-color: #00FFFF;
                color: #00FFFF;
            }}
        """)
        self.btn_gear.clicked.connect(self._show_gear_menu)
        header_layout.addWidget(self.btn_gear)

        chat_layout.addWidget(header_widget)

        # Artistic Banner Widget (Cyberpunk + Van Gogh + Gaudí)
        self.art_banner = CyberGoghGaudiBannerWidget()
        chat_layout.addWidget(self.art_banner)

        self.chat_display = QTextEdit()
        self.chat_display.setReadOnly(True)
        self.chat_display.setStyleSheet(f"""
            QTextEdit {{ background: {C_CHAT_BG.name()}; color: {C_TEXT_CYAN.name()};
                         border: 1px solid #333; font-family: Monospace; font-size: 11px; }}
        """)
        chat_layout.addWidget(self.chat_display, 1)

        chat_input_layout = QHBoxLayout()
        self.chat_input = QLineEdit()
        self.chat_input.setPlaceholderText("Message...")
        self.chat_input.setStyleSheet(f"""
            QLineEdit {{ background: {C_BG_PANEL.name()}; color: {C_TEXT_WHITE.name()};
                         border: 1px solid {C_TRI_EMERALD.name()}; padding: 5px; }}
        """)
        self.chat_input.returnPressed.connect(self._send_chat)
        chat_input_layout.addWidget(self.chat_input)

        btn_send = QPushButton("→")
        btn_send.setFixedWidth(36)
        btn_send.clicked.connect(self._send_chat)
        chat_input_layout.addWidget(btn_send)
        chat_layout.addLayout(chat_input_layout)

        self.net_status_label = QLabel("⭕ Offline")
        self.net_status_label.setStyleSheet(f"color: {C_STATUS_OFFLINE.name()}; font-size: 11px;")
        chat_layout.addWidget(self.net_status_label)

        self.hash_label = QLabel("Hash: —")
        self.hash_label.setStyleSheet(f"color: {C_TEXT_GOLD.name()}; font-size: 10px;")
        chat_layout.addWidget(self.hash_label)

        splitter.addWidget(chat_panel)
        splitter.setStretchFactor(0, 3)
        splitter.setStretchFactor(1, 1)
        main_layout.addWidget(splitter)

        self.status_bar = QStatusBar()
        self.setStatusBar(self.status_bar)
        self.status_bar.showMessage("MadGammon Desktop v1.0 — Ready / Готов")

    def _setup_tray(self):
        tray_avail = QSystemTrayIcon.isSystemTrayAvailable()
        log.info("System Tray available: %s", tray_avail)
        if not tray_avail:
            log.warning("System Tray is not available in current environment. Running without tray icon.")
            return

        try:
            self.tray_icon = QSystemTrayIcon(self)
            if self._icon_path and os.path.exists(self._icon_path):
                self.tray_icon.setIcon(QIcon(self._icon_path))
            else:
                self.tray_icon.setIcon(self.style().standardIcon(
                    self.style().StandardPixmap.SP_ComputerIcon))
            self.tray_icon.setToolTip("MadGammon — Шеш-Беш")

            tray_menu = QMenu()
            tray_menu.setStyleSheet(f"""
                QMenu {{ background: {C_BG_PANEL.name()}; color: {C_TEXT_WHITE.name()};
                         border: 1px solid {C_TRI_GOLD.name()}; }}
                QMenu::item:selected {{ background: {C_BUTTON_HOVER.name()}; }}
            """)

            act_show = tray_menu.addAction("📋 Show / Показать")
            act_show.triggered.connect(self._show_window)

            tray_menu.addSeparator()

            act_new = tray_menu.addAction("🎲 New Local Game / Новая игра")
            act_new.triggered.connect(self._start_local_game)

            act_host = tray_menu.addAction("🖥️ Host P2P Game / Создать")
            act_host.triggered.connect(lambda: self._quick_p2p(True))

            act_join = tray_menu.addAction("🔗 Join P2P Game / Подключиться")
            act_join.triggered.connect(lambda: self._quick_p2p(False))

            tray_menu.addSeparator()

            act_radio = tray_menu.addAction("📻 Cyberpunk Radio / Радио")
            act_radio.triggered.connect(self._open_radio_dialog)

            act_secret = tray_menu.addAction("🛡️ Security Vault / Секретный слой (Ctrl+Shift+S)")
            act_secret.triggered.connect(self._open_secret_pin_dialog)

            tray_menu.addSeparator()

            act_hash = tray_menu.addAction("📊 Board Hash Audit / Хэш доски")
            act_hash.triggered.connect(self._show_board_hash)

            tray_menu.addSeparator()

            act_mode_long = tray_menu.addAction("  LONG (Длинные нарды)")
            act_mode_long.triggered.connect(lambda: self._set_mode(0))
            act_mode_short = tray_menu.addAction("  SHORT (Короткие нарды)")
            act_mode_short.triggered.connect(lambda: self._set_mode(1))
            act_mode_mad = tray_menu.addAction("  MAD (Бешеные нарды)")
            act_mode_mad.triggered.connect(lambda: self._set_mode(2))

            tray_menu.addSeparator()

            act_quit = tray_menu.addAction("❌ Quit / Выход")
            act_quit.triggered.connect(self._quit_app)

            self.tray_icon.setContextMenu(tray_menu)
            self.tray_icon.activated.connect(self._tray_activated)
            self.tray_icon.show()
            log.info("System Tray configured and displayed.")
        except Exception as e:
            log.exception("Error configuring system tray: %s", e)

    def _tray_activated(self, reason):
        log.debug("Tray icon activated with reason: %s", reason)
        if reason == QSystemTrayIcon.ActivationReason.Trigger:
            self._show_window()

    def _show_window(self):
        log.debug("Showing main window")
        self.showNormal()
        self.activateWindow()
        self.raise_()

    # ─── Game Logic ───

    def _start_local_game(self):
        modes = [GameMode.LONG, GameMode.SHORT, GameMode.MAD]
        self.game_mode = modes[self.mode_combo.currentIndex()]
        log.info("Initiating local game with Jrebiy toss in mode %s", self.game_mode)

        # Launch two-stage Jrebiy (Lot/Toss)
        jrebiy = JrebiyDialog(p1_name="Игрок (Вы)", p2_name="Соперник (Бот)", parent=self)
        assigned_white = Player.WHITE
        first_mover = Player.WHITE

        if jrebiy.exec() == QDialog.DialogCode.Accepted:
            assigned_white = jrebiy.assigned_white or Player.WHITE
            first_mover = jrebiy.first_mover or Player.WHITE
            log.info("Jrebiy results: AssignedWhite=%s, FirstMover=%s", assigned_white, first_mover)
        else:
            log.info("Jrebiy cancelled, defaulting to standard assignment.")

        self.local_color = assigned_white
        self.bot_color = Player.opponent(self.local_color)
        self.head_player = self.local_color
        self.board = BackgammonRules.create_initial_board(self.game_mode, head_player=self.head_player)
        self.board_widget.head_player = self.head_player
        self.bar_white = 0
        self.bar_black = 0
        self.borne_off_white = 0
        self.borne_off_black = 0
        self.current_player = first_mover
        self.double_waterfall_queue.clear()
        self.kush_roller = None
        self.dice = (0, 0)
        self.remaining_dice = []
        self.selected_source = -2
        self.is_network_game = False
        self.game_started = True
        self.game_over = False
        self.dice_used = [False, False]
        self.head_taken_this_turn = 0
        self.is_first_turn = True
        self._update_board()

        p_white = "Игрок (Вы)" if assigned_white == Player.WHITE else "Соперник (Бот)"
        p_black = "Соперник (Бот)" if assigned_white == Player.WHITE else "Игрок (Вы)"
        first_txt = "Белые" if first_mover == Player.WHITE else "Чёрные"
        opp_label = f"AI Бот ({self.bot_difficulty})" if self.vs_bot else "Hotseat (2P)"

        # Rich Cyberpunk + Gold HTML match announcement (replaces skewed ASCII box)
        card_html = f"""
        <div style="background: qlineargradient(x1:0, y1:0, x2:1, y2:1, stop:0 #151a2e, stop:1 #1c2738); border: 1.5px solid #00E6FF; border-radius: 6px; padding: 10px; margin: 6px 0; font-family: sans-serif;">
            <div style="font-weight: bold; color: #00E6FF; font-size: 11px; margin-bottom: 6px; letter-spacing: 1px;">
                🎲 ЖЕРЕБЬЁВКА «ШЕШ-БЕШ» ЗАВЕРШЕНА
            </div>
            <div style="color: #FFFFFF; font-size: 11px; line-height: 1.45;">
                ⚪ <b>Белые:</b> {p_white}<br/>
                ⚫ <b>Чёрные:</b> {p_black}<br/>
                ⚡ <b>Первый ход:</b> <span style="color: #FFD54F;"><b>{first_txt}</b></span><br/>
                🎯 <b>Режим:</b> <span style="color: #00FF99;"><b>{self.game_mode}</b></span><br/>
                🤖 <b>Соперник:</b> <span style="color: #00E6FF;"><b>{opp_label}</b></span>
            </div>
        </div>
        """
        self._log_chat(card_html)

        self.status_bar.showMessage(f"Матч начат! Белые: {p_white} | Ход: {first_txt}")
        self._check_bot_turn()

    def _show_gear_menu(self):
        """Pops up the comprehensive game controls and settings menu from the gear button."""
        menu = QMenu(self)
        menu.setStyleSheet(f"""
            QMenu {{
                background: {C_BG_PANEL.name()};
                color: {C_TEXT_WHITE.name()};
                border: 1.5px solid {C_TRI_GOLD.name()};
                font-family: Monospace;
                font-size: 12px;
                padding: 4px;
            }}
            QMenu::item {{
                padding: 6px 22px 6px 14px;
                border-radius: 3px;
            }}
            QMenu::item:selected {{
                background: {C_BUTTON_HOVER.name()};
                color: #00FFFF;
            }}
            QMenu::separator {{
                height: 1px;
                background: #444;
                margin: 4px 8px;
            }}
        """)

        # 1. Game Actions
        act_roll = menu.addAction("🎲 Бросить зарики (Roll Dice)")
        act_roll.triggered.connect(self._on_roll_dice)
        can_roll = (self.game_started and not self.game_over and
                    (not self.is_network_game or self.current_player == self.local_color) and
                    len(self.remaining_dice) == 0 and not self.board_widget.is_dice_rolling)
        act_roll.setEnabled(can_roll)

        act_end = menu.addAction("✓ Завершить ход (End Turn)")
        act_end.triggered.connect(self._on_end_turn)
        act_end.setEnabled(self.game_started and not self.game_over and bool(self.remaining_dice))

        act_undo = menu.addAction("↩ Отменить ход (Undo)")
        act_undo.triggered.connect(self._on_undo)
        act_undo.setEnabled(False)

        menu.addSeparator()

        # 2. Match Setup
        act_new = menu.addAction("🆕 Новая игра (с жеребьёвкой)")
        act_new.triggered.connect(self._start_local_game)

        act_p2p = menu.addAction("🌐 P2P Сетевая игра")
        act_p2p.triggered.connect(self._open_p2p_dialog)

        menu.addSeparator()

        # 3. Game Mode Submenu
        menu_mode = menu.addMenu("🎯 Режим игры")
        menu_mode.setStyleSheet(menu.styleSheet())
        act_m_long = menu_mode.addAction("  LONG (Длинные нарды)" + ("  ✓" if self.game_mode == GameMode.LONG else ""))
        act_m_long.triggered.connect(lambda: self._set_mode_via_menu(GameMode.LONG))
        act_m_short = menu_mode.addAction("  SHORT (Короткие нарды)" + ("  ✓" if self.game_mode == GameMode.SHORT else ""))
        act_m_short.triggered.connect(lambda: self._set_mode_via_menu(GameMode.SHORT))
        act_m_mad = menu_mode.addAction("  MAD (Бешеные нарды)" + ("  ✓" if self.game_mode == GameMode.MAD else ""))
        act_m_mad.triggered.connect(lambda: self._set_mode_via_menu(GameMode.MAD))

        # 4. Opponent Submenu
        menu_opp = menu.addMenu("🤖 Соперник")
        menu_opp.setStyleSheet(menu.styleSheet())
        act_opp_bot = menu_opp.addAction("  🤖 Против AI Бота" + ("  ✓" if self.vs_bot else ""))
        act_opp_bot.triggered.connect(lambda: self._set_opponent_via_menu(True))
        act_opp_hot = menu_opp.addAction("  👥 Hotseat (вдвоём)" + ("  ✓" if not self.vs_bot else ""))
        act_opp_hot.triggered.connect(lambda: self._set_opponent_via_menu(False))

        # 5. Difficulty Submenu
        menu_diff = menu.addMenu("🎚️ Сложность Бота")
        menu_diff.setStyleSheet(menu.styleSheet())
        act_d_easy = menu_diff.addAction("  🟢 Easy (Новичок)" + ("  ✓" if self.bot_difficulty == AIDifficulty.EASY else ""))
        act_d_easy.triggered.connect(lambda: self._set_difficulty_via_menu(AIDifficulty.EASY))
        act_d_med = menu_diff.addAction("  🟡 Medium (Мастер)" + ("  ✓" if self.bot_difficulty == AIDifficulty.MEDIUM else ""))
        act_d_med.triggered.connect(lambda: self._set_difficulty_via_menu(AIDifficulty.MEDIUM))
        act_d_hard = menu_diff.addAction("  🔴 Hard (Гроссмейстер)" + ("  ✓" if self.bot_difficulty == AIDifficulty.HARD else ""))
        act_d_hard.triggered.connect(lambda: self._set_difficulty_via_menu(AIDifficulty.HARD))

        # 6. Visual Theme Submenu
        menu_theme = menu.addMenu("🎨 Визуальная тема")
        menu_theme.setStyleSheet(menu.styleSheet())
        for th_id, th_obj in THEMES.items():
            mark = "  ✓" if self.board_widget.theme_id == th_id else ""
            act_th = menu_theme.addAction(f"  {th_obj.name}{mark}")
            act_th.triggered.connect(lambda checked=False, tid=th_id: self._set_theme_via_menu(tid))

        menu.addSeparator()

        # 7. Radio & Security
        radio_state = "Вкл" if self.radio_player.is_playing else "Выкл"
        act_radio = menu.addAction(f"📻 Cyberpunk Radio ({radio_state})")
        act_radio.triggered.connect(self._open_radio_dialog)

        act_vault = menu.addAction("🛡️ Секретный слой / Vault (Ctrl+Shift+S)")
        act_vault.triggered.connect(self._open_secret_pin_dialog)

        act_hash = menu.addAction("📊 Аудит хэша доски (SHA-256)")
        act_hash.triggered.connect(self._show_board_hash)

        # Popup directly beneath gear button
        menu.exec(self.btn_gear.mapToGlobal(QPoint(0, self.btn_gear.height() + 2)))

    def _set_mode_via_menu(self, mode: str):
        self.game_mode = mode
        modes = [GameMode.LONG, GameMode.SHORT, GameMode.MAD]
        if mode in modes:
            self.mode_combo.setCurrentIndex(modes.index(mode))
        log.info("Mode changed via menu: %s", mode)
        self._log_chat(f"🎯 Режим игры переключён: <b>{mode}</b>")
        self.status_bar.showMessage(f"Режим: {mode}")
        self._update_board()

    def _set_opponent_via_menu(self, vs_bot: bool):
        self.vs_bot = vs_bot
        self.opponent_combo.setCurrentIndex(0 if vs_bot else 1)
        label = "🤖 Против AI Бота" if vs_bot else "👥 Hotseat (вдвоём)"
        log.info("Opponent changed via menu: %s", label)
        self._log_chat(f"Соперник: <b>{label}</b>")
        self.status_bar.showMessage(f"Соперник: {label}")
        self._update_board()
        self._check_bot_turn()

    def _set_difficulty_via_menu(self, diff: str):
        self.bot_difficulty = diff
        diffs = [AIDifficulty.MEDIUM, AIDifficulty.HARD, AIDifficulty.EASY]
        if diff in diffs:
            self.difficulty_combo.setCurrentIndex(diffs.index(diff))
        log.info("Difficulty changed via menu: %s", diff)
        self._log_chat(f"🎚️ Сложность Бота: <b>{diff}</b>")
        self.status_bar.showMessage(f"Сложность Бота: {diff}")
        self._update_board()

    def _set_theme_via_menu(self, theme_id: str):
        self.board_widget.set_theme(theme_id)
        th = get_theme(theme_id)
        log.info("Theme switched via menu: %s (%s)", th.name, theme_id)
        self._log_chat(f"🎨 Визуальная тема изменена: <b>{th.name}</b>")
        self.status_bar.showMessage(f"Тема: {th.name}")
        self._update_board()

    def _on_opponent_mode_changed(self, idx: int):
        self.vs_bot = (idx == 0)
        mode_str = "🤖 Против AI Бота" if self.vs_bot else "👥 Hotseat (вдвоём)"
        self._log_chat(f"[MODE] {mode_str}")
        self.status_bar.showMessage(f"Режим оппонента: {mode_str}")
        self._update_board()
        self._check_bot_turn()

    def _on_difficulty_changed(self, idx: int):
        diffs = [AIDifficulty.MEDIUM, AIDifficulty.HARD, AIDifficulty.EASY]
        self.bot_difficulty = diffs[idx]
        self._log_chat(f"[AI] Сложность Бота установлена: {self.bot_difficulty}")
        self.status_bar.showMessage(f"Сложность Бота: {self.bot_difficulty}")
        self._update_board()

    def _on_mode_changed(self, idx):
        modes = [GameMode.LONG, GameMode.SHORT, GameMode.MAD]
        self.game_mode = modes[idx]
        log.info("Game mode changed via combo: %s", self.game_mode)
        self._update_board()

    def _set_mode(self, idx):
        self.mode_combo.setCurrentIndex(idx)
        self._update_board()

    def _on_roll_dice(self):
        if self.game_over:
            return
        if not self.game_started:
            self._start_local_game()
            return
        if self.board_widget.is_dice_rolling:
            return
        if self.dice[0] != 0 and self.dice[1] != 0 and self.remaining_dice:
            self.status_bar.showMessage("Dice already rolled — make your moves!")
            return
        if self.is_network_game and self.current_player != self.local_color:
            self.status_bar.showMessage("Not your turn / Не ваш ход")
            return

        if self.is_network_game:
            commitment = self.mental_dice.start_local_roll()
            log.info("Network roll: sending DICE_COMMIT=%s", commitment[:16])
            self.network.send_packet("DICE_COMMIT", commitment)
            self._log_chat(f"[DICE] Sent commitment: {commitment[:16]}...")
            self.status_bar.showMessage("Waiting for peer dice commitment...")
        else:
            d1 = random.randint(1, 6)
            d2 = random.randint(1, 6)
            log.info("Local roll: %d:%d, initiating zariki tumbling animation", d1, d2)
            self.btn_roll.setEnabled(False)
            self.btn_end_turn.setEnabled(False)
            self.status_bar.showMessage("🎲 Зарики кувыркаются по доске...")

            def finish_roll():
                self.dice = (d1, d2)
                self.kush_roller = self.current_player

                if d1 == d2:
                    if self.game_mode == GameMode.MAD:
                        self.remaining_dice = [d1] * 4
                        self.dice_used = [False] * 4
                        self.double_waterfall_queue = [[sz] * 4 for sz in range(d1 + 1, 7)]
                        chain_str = " -> ".join([f"4x{x}" for x in range(d1, 7)])
                        self._log_chat(f"🔥 КРЕЙЗИ КУШ {d1}:{d1}! Каскадная цепочка: {chain_str}")
                    else:
                        self.remaining_dice = [d1] * 4
                        self.dice_used = [False] * 4
                        self.double_waterfall_queue.clear()
                else:
                    self.remaining_dice = [d1, d2]
                    self.dice_used = [False, False]
                    self.double_waterfall_queue.clear()

                log.info("Roll finished: %d:%d, remaining=%s", d1, d2, self.remaining_dice)
                self.btn_roll.setEnabled(True)
                self.btn_end_turn.setEnabled(True)
                self._update_board()
                kush_txt = " (КУШ!)" if d1 == d2 else ""
                self._log_chat(f"[DICE] {self.current_player} выбросил {d1}:{d2}{kush_txt}")
                self.status_bar.showMessage(f"Выпало {d1}:{d2}{kush_txt}")

                # Check if roller has any legal moves
                bar_c = self.bar_white if self.current_player == Player.WHITE else self.bar_black
                avail_paths = []
                if bar_c > 0:
                    avail_paths = BackgammonRules.find_reachable_paths_from(
                        self.current_player, -1, self.remaining_dice, self.board, bar_c,
                        getattr(self, 'head_taken_this_turn', 0), getattr(self, 'is_first_turn', False),
                        mode=self.game_mode, head_player=self.head_player
                    )
                else:
                    for i in range(BOARD_POINTS):
                        if self.board[i].player == self.current_player and self.board[i].count > 0:
                            avail_paths.extend(BackgammonRules.find_reachable_paths_from(
                                self.current_player, i, self.remaining_dice, self.board, 0,
                                getattr(self, 'head_taken_this_turn', 0), getattr(self, 'is_first_turn', False),
                                mode=self.game_mode, head_player=self.head_player
                            ))

                if not avail_paths:
                    if self.game_mode == GameMode.MAD:
                        opp = Player.opponent(self.current_player)
                        # Check if opponent can move with transferred dice
                        opp_bar = self.bar_white if opp == Player.WHITE else self.bar_black
                        opp_paths = []
                        if opp_bar > 0:
                            opp_paths = BackgammonRules.find_reachable_paths_from(
                                opp, -1, self.remaining_dice, self.board, opp_bar, 0, False,
                                mode=self.game_mode, head_player=self.head_player
                            )
                        else:
                            for i in range(BOARD_POINTS):
                                if self.board[i].player == opp and self.board[i].count > 0:
                                    opp_paths.extend(BackgammonRules.find_reachable_paths_from(
                                        opp, i, self.remaining_dice, self.board, 0, 0, False,
                                        mode=self.game_mode, head_player=self.head_player
                                    ))
                        if opp_paths:
                            self._log_chat(f"🔴 У игрока {self.current_player} нет ходов! Остаток {self.remaining_dice} передан {opp}!")
                            self.current_player = opp
                            self.status_bar.showMessage(f"Ходы переданы игроку {opp}!")
                            self._update_board()
                            self._check_bot_turn()
                            return

                    self._log_chat(f"⚠️ У игрока {self.current_player} нет ходов. Кости сгорают.")
                    self._check_cascade_or_end_turn()
                    return

                # If current player is bot, trigger bot move
                self._check_bot_turn()

            self.board_widget.start_dice_animation(d1, d2, self.current_player, finish_roll)

    def _on_point_click(self, point_idx: int):
        if not self.game_started or self.game_over:
            if not self.game_started:
                self._start_local_game()
            return
        if self.board_widget.is_dice_rolling:
            return
        if self.is_network_game and self.current_player != self.local_color:
            return

        # If waiting to roll dice, ANY click on the board triggers dice roll!
        if not self.remaining_dice:
            if point_idx == -1:
                # Covert knock-code check on central bar (3 clicks in 1.5s)
                now = time.time()
                self.bar_click_timestamps.append(now)
                self.bar_click_timestamps = [t for t in self.bar_click_timestamps if now - t < 1.5]
                if len(self.bar_click_timestamps) >= 3:
                    log.info("Knock-code triggered on central bar! Opening PIN dialog.")
                    self.bar_click_timestamps.clear()
                    self._open_secret_pin_dialog()
                    return
            log.info("Board clicked while waiting to roll -> rolling dice!")
            self._on_roll_dice()
            return

        if self.selected_source == -2:
            if point_idx == -1:
                # Knock-code covert trigger on central bar (3 clicks in 1.5s)
                now = time.time()
                self.bar_click_timestamps.append(now)
                self.bar_click_timestamps = [t for t in self.bar_click_timestamps if now - t < 1.5]
                if len(self.bar_click_timestamps) >= 3:
                    log.info("Knock-code triggered on central bar! Opening PIN dialog.")
                    self.bar_click_timestamps.clear()
                    self._open_secret_pin_dialog()
                    return

                bar_count = self.bar_white if self.current_player == Player.WHITE else self.bar_black
                if bar_count > 0:
                    self.selected_source = -1
                    self.board_widget.selected_point = -1
                    self._compute_valid_destinations()
                    self._update_board()
                    self.status_bar.showMessage("Bar selected — click destination")
            elif 0 <= point_idx < 24:
                stack = self.board[point_idx]
                if stack.count > 0 and stack.player == self.current_player:
                    bar_count = self.bar_white if self.current_player == Player.WHITE else self.bar_black
                    if bar_count > 0:
                        self.status_bar.showMessage("Must enter from bar first! / Сначала введите с бара!")
                        return
                    self.selected_source = point_idx
                    self.board_widget.selected_point = point_idx
                    self._compute_valid_destinations()
                    self._update_board()
                    self.status_bar.showMessage(f"Point {point_idx + 1} selected — click destination")
        else:
            if point_idx == self.selected_source:
                self._clear_selection()
                return
            if point_idx in self.board_widget.valid_destinations or point_idx == 24:
                self._execute_move(self.selected_source, point_idx)
            else:
                if 0 <= point_idx < 24:
                    stack = self.board[point_idx]
                    if stack.count > 0 and stack.player == self.current_player:
                        self.selected_source = point_idx
                        self.board_widget.selected_point = point_idx
                        self._compute_valid_destinations()
                        self._update_board()
                        return
                self.status_bar.showMessage("Invalid destination / Недопустимый ход")

    def _compute_valid_destinations(self):
        src = self.selected_source
        player = self.current_player
        mode = self.game_mode

        # GROK-G06 Reachable Paths computation (Single & Combo multi-step moves)
        bar_c = self.bar_white if player == Player.WHITE else self.bar_black
        self.reachable_paths = BackgammonRules.find_reachable_paths_from(
            player=player,
            from_abs=src,
            remaining_dice=self.remaining_dice,
            board=self.board,
            bar_count=bar_c,
            cur_head_takes=getattr(self, 'head_taken_this_turn', 0),
            is_first_turn=getattr(self, 'is_first_turn', True),
            mode=mode,
            head_player=self.head_player
        )
        self.board_widget.reachable_paths = self.reachable_paths

        log.debug("Computed %d reachable paths from %d for %s", len(self.reachable_paths), src, player)
        combo_count = sum(1 for p in self.reachable_paths if p.is_multi_step)
        if combo_count > 0:
            self.status_bar.showMessage(f"Доступно {len(self.reachable_paths)} путей (включая {combo_count} составных ходов на ОБА кубика)!")

    def _execute_move(self, src: int, dst: int):
        player = self.current_player
        mode = self.game_mode

        target_path = next((p for p in getattr(self, 'reachable_paths', []) if p.final_to_abs == dst), None)
        if target_path is None:
            log.warning("No reachable path found from %d to %d!", src, dst)
            self.status_bar.showMessage("Invalid destination / Недопустимый ход")
            return

        log.info("Executing path to %d with %d steps: %s", dst, len(target_path.steps), target_path.total_dice_used)

        # Clear selection highlights so red outlines clear as checker takes flight
        self._clear_selection()

        # Animate checker hop in 3D arc, then apply move steps to board and game state
        def finish_move_apply():
            self._apply_move_steps(target_path, player, src, dst)

        self.board_widget.start_checker_animation(target_path, player, finish_move_apply)

    def _apply_move_steps(self, target_path: ReachablePath, player: str, src: int, dst: int):
        mode = self.game_mode

        # Set last move indicators so moved checkers are immediately noticed
        self.board_widget.last_move_src = target_path.steps[0].from_abs
        self.board_widget.last_move_dst = target_path.final_to_abs
        self.board_widget.last_move_player = player
        self.last_move_src = self.board_widget.last_move_src
        self.last_move_dst = self.board_widget.last_move_dst

        # Sequentially execute all steps in the path (handles single and multi-step combo automatically)
        for step in target_path.steps:
            s_from = step.from_abs
            s_to = step.to_abs
            d_used = step.die_used

            # Deduct checker from source
            if s_from == -1:
                if player == Player.WHITE:
                    self.bar_white = max(0, self.bar_white - 1)
                else:
                    self.bar_black = max(0, self.bar_black - 1)
            else:
                self.board[s_from].count -= 1
                if self.board[s_from].count == 0:
                    self.board[s_from].player = None

            # Add checker to destination
            if s_to == 24:
                if player == Player.WHITE:
                    self.borne_off_white += 1
                else:
                    self.borne_off_black += 1
            else:
                target_cell = self.board[s_to]
                if mode == GameMode.SHORT and target_cell.count == 1 and target_cell.player and target_cell.player != player:
                    if target_cell.player == Player.WHITE:
                        self.bar_white += 1
                    else:
                        self.bar_black += 1
                    target_cell.count = 0
                    target_cell.player = None

                if target_cell.count == 0 or target_cell.player is None:
                    target_cell.player = player
                    target_cell.count = 1
                else:
                    target_cell.count += 1

            # Head take accounting in Long Nardy
            if step.from_rel == 0:
                self.head_taken_this_turn = getattr(self, 'head_taken_this_turn', 0) + 1

            # Consume used die
            if d_used in self.remaining_dice:
                self.remaining_dice.remove(d_used)

            step_str = f"{'BAR' if s_from == -1 else s_from + 1}->{'OFF' if s_to == 24 else s_to + 1}"
            log.info("Step executed: %s by %s (die=%d, remaining=%s)", step_str, player, d_used, self.remaining_dice)

            if self.is_network_game:
                self.network.send_packet("MOVE", f"{s_from}:{s_to}")

        # Human-readable user feedback
        total_used = target_path.total_dice_used
        from_label = "BAR" if src == -1 else str(src + 1)
        to_label = "OFF" if dst == 24 else str(dst + 1)

        if target_path.is_multi_step:
            combo_msg = f"[⚡ДВОЙНОЙ ХОД] {player}: {from_label} -> {to_label} (кости: {'+'.join(map(str, total_used))} = +{sum(total_used)})"
            self._log_chat(combo_msg)
            self.status_bar.showMessage(f"Сделан двойной ход {from_label} -> {to_label} сразу двумя костями ({'+'.join(map(str, total_used))})")
        else:
            single_msg = f"[MOVE] {player}: {from_label} -> {to_label} (кость: {total_used[0]})"
            self._log_chat(single_msg)
            self.status_bar.showMessage(f"Ход {from_label} -> {to_label} костью {total_used[0]}")

        if self._check_win(player):
            return

        self._update_dice_used()
        self._update_board()

        if not self.remaining_dice:
            self._check_cascade_or_end_turn()
        else:
            # Check if current player can continue moving with remaining dice
            if not self.is_network_game:
                bar_c = self.bar_white if self.current_player == Player.WHITE else self.bar_black
                has_next_moves = False
                if bar_c > 0:
                    has_next_moves = bool(BackgammonRules.find_reachable_paths_from(
                        self.current_player, -1, self.remaining_dice, self.board, bar_c,
                        self.head_taken_this_turn, self.is_first_turn, self.game_mode,
                        head_player=self.head_player
                    ))
                else:
                    for i in range(BOARD_POINTS):
                        if self.board[i].player == self.current_player and self.board[i].count > 0:
                            if BackgammonRules.find_reachable_paths_from(
                                self.current_player, i, self.remaining_dice, self.board, 0,
                                self.head_taken_this_turn, self.is_first_turn, self.game_mode,
                                head_player=self.head_player
                            ):
                                has_next_moves = True
                                break

                if not has_next_moves:
                    if self.game_mode == GameMode.MAD:
                        opp = Player.opponent(self.current_player)
                        opp_bar = self.bar_white if opp == Player.WHITE else self.bar_black
                        opp_can = False
                        if opp_bar > 0:
                            opp_can = bool(BackgammonRules.find_reachable_paths_from(
                                opp, -1, self.remaining_dice, self.board, opp_bar, 0, False, self.game_mode,
                                head_player=self.head_player
                            ))
                        else:
                            for i in range(BOARD_POINTS):
                                if self.board[i].player == opp and self.board[i].count > 0:
                                    if BackgammonRules.find_reachable_paths_from(
                                        opp, i, self.remaining_dice, self.board, 0, 0, False, self.game_mode,
                                        head_player=self.head_player
                                    ):
                                        opp_can = True
                                        break
                        if opp_can:
                            self._log_chat(f"🔴 У {self.current_player} кончились ходы! Остаток {self.remaining_dice} передан игроку {opp}!")
                            self.current_player = opp
                            self.status_bar.showMessage(f"Ходы переданы игроку {opp}!")
                            self._update_board()
                            self._check_bot_turn()
                            return

                    self._log_chat(f"⚠️ У игрока {self.current_player} больше нет ходов. Кости сгорают.")
                    self._check_cascade_or_end_turn()
                    return

                # If current player is bot, schedule next bot move
                if self.vs_bot and self.current_player == self.bot_color:
                    QTimer.singleShot(700, self._bot_do_move)

    def _update_dice_used(self):
        d1, d2 = self.dice
        total = 4 if d1 == d2 else 2
        used_count = total - len(self.remaining_dice)
        self.dice_used = [i < used_count for i in range(total)]
        self.board_widget.dice_used = self.dice_used

    def _check_win(self, player: str) -> bool:
        target = 15
        if player == Player.WHITE and self.borne_off_white >= target:
            self._game_over(player)
            return True
        if player == Player.BLACK and self.borne_off_black >= target:
            self._game_over(player)
            return True
        return False

    def _game_over(self, winner: str):
        self.game_over = True
        label = "White / Белые" if winner == Player.WHITE else "Black / Чёрные"
        log.info("Game Over! Winner: %s", winner)
        self._log_chat(f"[WIN] 🏆 {label} wins! / Победа!")
        self.status_bar.showMessage(f"🏆 Game Over — {label} wins!")
        if self.is_network_game:
            self.network.send_packet("GAME_OVER", winner)
        QMessageBox.information(self, "Game Over / Конец игры",
                                f"🏆 {label} wins!\n🏆 {label} победили!")

    def _check_cascade_or_end_turn(self):
        if self.game_over:
            return
        if self.game_mode == GameMode.MAD and self.double_waterfall_queue:
            next_cascade = self.double_waterfall_queue.pop(0)
            self.remaining_dice = list(next_cascade)
            sz = next_cascade[0]
            self.dice = (sz, sz)
            self.dice_used = [False] * 4
            if self.kush_roller:
                self.current_player = self.kush_roller
            self._log_chat(f"⚡ Каскад куша: переход к фазе 4x{sz} для игрока {self.current_player}!")
            self.status_bar.showMessage(f"Каскад куша: 4x{sz}")
            self._update_board()
            self._check_bot_turn()
        else:
            self._on_end_turn()

    def _check_bot_turn(self):
        if not self.game_started or self.game_over:
            return
        if self.is_network_game or not self.vs_bot:
            return
        if self.current_player != self.bot_color:
            return

        # It is bot's turn
        if not self.remaining_dice:
            if not self.board_widget.is_dice_rolling:
                log.info("Scheduling bot roll...")
                self.status_bar.showMessage(f"🤖 Бот ({self.bot_color}) бросает кости...")
                QTimer.singleShot(750, self._bot_do_roll)
        else:
            log.info("Scheduling bot move with dice %s...", self.remaining_dice)
            self.status_bar.showMessage(f"🤖 Бот ({self.bot_color}) выбирает ход...")
            QTimer.singleShot(800, self._bot_do_move)

    def _bot_do_roll(self):
        if not self.game_started or self.game_over or self.current_player != self.bot_color:
            return
        if self.board_widget.is_dice_rolling:
            return
        self._on_roll_dice()

    def _bot_do_move(self):
        if not self.game_started or self.game_over or self.current_player != self.bot_color:
            return
        if not self.remaining_dice:
            self._check_cascade_or_end_turn()
            return

        best_path = BackgammonAI.select_best_move(
            board=self.board,
            player=self.bot_color,
            remaining_dice=self.remaining_dice,
            bar_white=self.bar_white,
            bar_black=self.bar_black,
            borne_w=self.borne_off_white,
            borne_b=self.borne_off_black,
            cur_head_takes=getattr(self, 'head_taken_this_turn', 0),
            is_first_turn=getattr(self, 'is_first_turn', False),
            mode=self.game_mode,
            difficulty=self.bot_difficulty,
            head_player=self.head_player
        )

        if best_path is None:
            log.info("Bot has no legal moves remaining.")
            if self.game_mode == GameMode.MAD:
                human_p = Player.opponent(self.bot_color)
                human_bar = self.bar_white if human_p == Player.WHITE else self.bar_black
                human_paths = []
                if human_bar > 0:
                    human_paths = BackgammonRules.find_reachable_paths_from(
                        human_p, -1, self.remaining_dice, self.board, human_bar, 0, False, self.game_mode,
                        head_player=self.head_player
                    )
                else:
                    for i in range(BOARD_POINTS):
                        if self.board[i].player == human_p and self.board[i].count > 0:
                            human_paths.extend(BackgammonRules.find_reachable_paths_from(
                                human_p, i, self.remaining_dice, self.board, 0, 0, False, self.game_mode,
                                head_player=self.head_player
                            ))
                if human_paths:
                    self._log_chat(f"🔴 Бот застрял! Несыгранные кости {self.remaining_dice} ПЕРЕДАНЫ ВАМ!")
                    self.current_player = human_p
                    self.status_bar.showMessage("Бот передал вам свои несыгранные ходы!")
                    self._update_board()
                    return

            self._log_chat(f"⚠️ У Бота нет ходов. Оставшиеся ходы сгорают.")
            self._check_cascade_or_end_turn()
            return

        src = best_path.steps[0].from_abs
        dst = best_path.final_to_abs
        self.reachable_paths = [best_path]
        self._execute_move(src, dst)

    def _on_end_turn(self):
        if self.game_over:
            return
        board_hash = BackgammonRules.compute_board_hash(
            self.board, self.bar_white, self.bar_black)
        self.hash_label.setText(f"Hash: {board_hash}")
        log.info("Ending turn for %s. Board hash: %s", self.current_player, board_hash)
        if self.is_network_game:
            self.network.send_packet("BOARD_HASH", board_hash)
            self.network.send_packet("END_TURN", self.current_player)

        self.double_waterfall_queue.clear()
        self.kush_roller = None
        self.current_player = Player.opponent(self.current_player)
        self.head_taken_this_turn = 0
        self.dice = (0, 0)
        self.remaining_dice = []
        self.dice_used = []
        self._clear_selection()
        self.btn_end_turn.setEnabled(False)
        self._update_board()
        label = "White / Белые" if self.current_player == Player.WHITE else "Black / Чёрные"
        self.status_bar.showMessage(f"{label} to roll")
        self._check_bot_turn()

    def _on_undo(self):
        self.status_bar.showMessage("Undo not yet implemented")

    def _clear_selection(self):
        self.selected_source = -2
        self.board_widget.selected_point = -2
        self.reachable_paths = []
        self.board_widget.reachable_paths = []
        self._update_board()

    def _update_board(self):
        self.board_widget.set_board_state(
            self.board, self.bar_white, self.bar_black,
            self.borne_off_white, self.borne_off_black,
            self.dice, self.current_player
        )
        self.board_widget.dice_used = self.dice_used
        self.board_widget.game_mode = self.game_mode

        # Update can_roll_dice flag on board widget
        waiting_for_roll = (
            self.game_started and not self.game_over and
            (not self.is_network_game or self.current_player == self.local_color) and
            len(self.remaining_dice) == 0 and
            not self.board_widget.is_dice_rolling
        )
        self.board_widget.can_roll_dice = waiting_for_roll

        # Update art banner dynamic badges
        if hasattr(self, 'art_banner') and self.art_banner is not None:
            opp_text = f"AI: {self.bot_difficulty}" if self.vs_bot else "Hotseat (2P)"
            self.art_banner.set_status(self.game_mode, opp_text, self.current_player)

    # ─── P2P Network ───

    def _open_p2p_dialog(self):
        if self.p2p_dialog is None:
            self.p2p_dialog = P2PDialog(self)
            self.p2p_dialog.connect_requested.connect(self._on_p2p_connect)
        self.p2p_dialog.show()

    def _quick_p2p(self, is_host: bool):
        self._open_p2p_dialog()
        if is_host:
            self.p2p_dialog.radio_host.setChecked(True)
        else:
            self.p2p_dialog.radio_join.setChecked(True)

    def _on_p2p_connect(self, host: str, port: int, is_host: bool):
        log.info("P2P Connect requested: is_host=%s, target=%s:%d", is_host, host, port)
        if is_host:
            self.network.start_host(port)
            self.local_color = Player.WHITE
        else:
            self.network.connect_to_peer(host, port)
            self.local_color = Player.BLACK

        self.is_network_game = True
        self.status_bar.showMessage(f"P2P {'hosting' if is_host else 'connecting'}...")

    def _on_net_status(self, status: str):
        log.info("Network status changed: %s", status)
        self._log_chat(f"[NET] {status}")
        if self.p2p_dialog:
            self.p2p_dialog.append_status(status)

        if status.startswith("CONNECTED"):
            self.net_status_label.setText(f"🟢 Connected: {status.split(':', 1)[-1]}")
            self.net_status_label.setStyleSheet(f"color: {C_STATUS_ONLINE.name()}; font-size: 11px;")
            self._start_network_game()
            self._update_tray_tooltip("Connected")
        elif status.startswith("DISCONNECTED"):
            self.net_status_label.setText("🔴 Disconnected")
            self.net_status_label.setStyleSheet(f"color: {C_STATUS_OFFLINE.name()}; font-size: 11px;")
            self.is_network_game = False
            self._update_tray_tooltip("Disconnected")
        elif status.startswith("HOST_LISTENING"):
            self.net_status_label.setText(f"🟡 Listening: {status.split(':', 1)[-1]}")
            self._update_tray_tooltip("Waiting for peer...")

    def _start_network_game(self):
        modes = [GameMode.LONG, GameMode.SHORT, GameMode.MAD]
        self.game_mode = modes[self.mode_combo.currentIndex()]
        log.info("Starting network game. Mode=%s, LocalColor=%s", self.game_mode, self.local_color)
        self.head_player = self.local_color
        self.board = BackgammonRules.create_initial_board(self.game_mode, head_player=self.head_player)
        self.board_widget.head_player = self.head_player
        self.bar_white = 0
        self.bar_black = 0
        self.borne_off_white = 0
        self.borne_off_black = 0
        self.current_player = Player.WHITE
        self.dice = (0, 0)
        self.remaining_dice = []
        self.game_started = True
        self.game_over = False
        self._update_board()

        self.network.send_packet("GAME_START", f"MODE:{self.game_mode}")
        self._log_chat(f"[SYS] Network game started / Сетевая игра начата")
        self._log_chat(f"[SYS] You play as {'White / Белые' if self.local_color == Player.WHITE else 'Black / Чёрные'}")
        self.status_bar.showMessage(f"Network game — {self.game_mode} — White to roll")

    def _on_net_message(self, action: str, payload: str):
        action = action.upper().strip()
        payload = payload.strip()
        log.debug("Net message received: action=%s, payload=%s", action, payload[:80])

        self._log_chat(f"[RX] {action}: {payload[:80]}")

        if action == "CHAT":
            self._log_chat(f"[PEER] {payload}")

        elif action == "DICE_COMMIT":
            self.mental_dice.set_peer_commitment(payload)
            self._log_chat(f"[DICE] Peer commitment received: {payload[:16]}...")
            if self.mental_dice.local_commitment_hex:
                seed_hex, secret_val = self.mental_dice.reveal_local_secret()
                self.network.send_packet("DICE_REVEAL", f"{seed_hex}:{secret_val}")
                self._log_chat("[DICE] Sent our reveal")

        elif action == "DICE_REVEAL":
            parts = payload.rsplit(":", 1)
            if len(parts) == 2:
                peer_seed_hex = parts[0]
                peer_val = int(parts[1])
                if self.mental_dice.local_seed:
                    seed_hex, secret_val = self.mental_dice.reveal_local_secret()
                    self.network.send_packet("DICE_REVEAL", f"{seed_hex}:{secret_val}")

                die1, die2, valid, proof = self.mental_dice.verify_and_compute(
                    peer_seed_hex, peer_val)
                self._log_chat(f"[DICE] {proof}")
                if valid:
                    self.btn_roll.setEnabled(False)
                    self.btn_end_turn.setEnabled(False)
                    self.status_bar.showMessage("🎲 Зарики кувыркаются по доске...")

                    def finish_net_roll():
                        self.dice = (die1, die2)
                        if die1 == die2:
                            self.remaining_dice = [die1] * 4
                            self.dice_used = [False] * 4
                        else:
                            self.remaining_dice = [die1, die2]
                            self.dice_used = [False, False]
                        self.btn_roll.setEnabled(True)
                        self.btn_end_turn.setEnabled(True)
                        self._update_board()
                        kush_txt = " (КУШ!)" if die1 == die2 else ""
                        self._log_chat(f"[DICE] {self.current_player} выбросил {die1}:{die2}{kush_txt}")
                        self.status_bar.showMessage(f"Выпало {die1}:{die2}{kush_txt} — сделайте ход")

                    self.board_widget.start_dice_animation(die1, die2, self.current_player, finish_net_roll)
                else:
                    self._log_chat("[!!! FRAUD DETECTED !!!]")

        elif action == "MOVE":
            parts = payload.split(":")
            if len(parts) == 2:
                src, dst = int(parts[0]), int(parts[1])
                self._apply_remote_move(src, dst)

        elif action == "END_TURN":
            self.current_player = Player.opponent(self.current_player)
            self.dice = (0, 0)
            self.remaining_dice = []
            self.dice_used = []
            self._update_board()
            if self.current_player == self.local_color:
                self.status_bar.showMessage("Your turn! Roll dice / Ваш ход!")
            else:
                self.status_bar.showMessage("Opponent's turn / Ход противника")

        elif action == "BOARD_HASH":
            local_hash = BackgammonRules.compute_board_hash(
                self.board, self.bar_white, self.bar_black)
            self.hash_label.setText(f"Hash: L={local_hash} | R={payload[:16]}")
            if local_hash != payload.strip():
                log.warning("Hash mismatch! Local=%s, Peer=%s", local_hash, payload)
                self._log_chat("[⚠️ HASH MISMATCH — board states diverged!]")
            else:
                log.info("Board hash verified: %s", local_hash)
                self._log_chat("[✓ Board hash verified]")

        elif action == "GAME_OVER":
            self._game_over(payload)

        elif action == "ROLL":
            parts = payload.split(":")
            if len(parts) >= 2:
                self._log_chat(f"[DICE] Peer rolled: {parts[0]}:{parts[1]}")

        elif action == "GAME_START":
            self._log_chat(f"[SYS] Peer game config: {payload}")

    def _apply_remote_move(self, src: int, dst: int):
        peer_color = Player.opponent(self.local_color)
        log.info("Applying remote move from %s: %d -> %d", peer_color, src, dst)

        from engine import PathStep
        step = PathStep(
            from_abs=src,
            to_abs=dst,
            from_rel=BackgammonRules.absolute_to_relative(peer_color, src, self.game_mode, self.head_player) if src != -1 else 0,
            to_rel=BackgammonRules.absolute_to_relative(peer_color, dst, self.game_mode, self.head_player) if dst != 24 else 24,
            die_used=0
        )
        synth_path = ReachablePath(steps=[step], total_dice_used=[0], final_to_abs=dst)

        def finish_remote_apply():
            self.board_widget.last_move_src = src
            self.board_widget.last_move_dst = dst
            self.board_widget.last_move_player = peer_color
            self.last_move_src = src
            self.last_move_dst = dst

            if src == -1:
                if peer_color == Player.WHITE:
                    self.bar_white = max(0, self.bar_white - 1)
                else:
                    self.bar_black = max(0, self.bar_black - 1)
            elif 0 <= src < 24:
                if self.board[src].count > 0:
                    self.board[src].count -= 1
                    if self.board[src].count == 0:
                        self.board[src].player = None

            if dst == 24:
                if peer_color == Player.WHITE:
                    self.borne_off_white += 1
                else:
                    self.borne_off_black += 1
            elif 0 <= dst < 24:
                target = self.board[dst]
                if self.game_mode == GameMode.SHORT and target.count == 1 and target.player == self.local_color:
                    if self.local_color == Player.WHITE:
                        self.bar_white += 1
                    else:
                        self.bar_black += 1
                    target.count = 0
                    target.player = None

                if target.count == 0 or target.player is None:
                    target.player = peer_color
                    target.count = 1
                else:
                    target.count += 1

            self._log_chat(f"[MOVE] Peer: {src}->{dst}")
            self._check_win(peer_color)
            self._update_board()

        self.board_widget.start_checker_animation(synth_path, peer_color, finish_remote_apply)

    # ─── Chat ───

    def _send_chat(self):
        text = self.chat_input.text().strip()
        if not text:
            return
        log.debug("User sent chat message: %s", text)
        self._log_chat(f"[YOU] {text}")
        if self.is_network_game:
            self.network.send_packet("CHAT", text)
        self.chat_input.clear()

    def _log_chat(self, msg: str):
        self.chat_display.append(msg)
        sb = self.chat_display.verticalScrollBar()
        sb.setValue(sb.maximum())

    # ─── Board Hash Audit ───

    def _show_board_hash(self):
        board_hash = BackgammonRules.compute_board_hash(
            self.board, self.bar_white, self.bar_black)
        self.hash_label.setText(f"Hash: {board_hash}")
        log.info("Board Hash Audit triggered. Hash=%s", board_hash)
        self._log_chat(f"[AUDIT] Board SHA-256 hash: {board_hash}")
        QMessageBox.information(self, "Board Hash Audit",
                                f"SHA-256 Board Hash:\n{board_hash}\n\n"
                                f"Bar: W={self.bar_white} B={self.bar_black}\n"
                                f"Off: W={self.borne_off_white} B={self.borne_off_black}")

    # ─── Tray helpers ───

    def _update_tray_tooltip(self, status: str):
        if hasattr(self, 'tray_icon'):
            self.tray_icon.setToolTip(f"MadGammon — {status}")

    # ─── Radio & Secret Layer Handlers ───

    def _open_radio_dialog(self):
        log.info("Opening Radio Dialog")
        if self.radio_dialog is None:
            self.radio_dialog = RadioDialog(self.radio_player, parent=self)
        self.radio_dialog.show()
        self.radio_dialog.activateWindow()

    def _on_radio_state_changed(self, is_playing: bool, station):
        if is_playing and station:
            self.btn_radio.setText(f"📻 {station.name[:14]}...")
            self.btn_radio.setStyleSheet(f"background: #113322; color: {C_NEON_GREEN.name()}; border-color: {C_NEON_GREEN.name()};")
            self.status_bar.showMessage(f"Радио играет: {station.name}")
        else:
            self.btn_radio.setText("📻 Radio: Off")
            self.btn_radio.setStyleSheet(f"background: {C_BUTTON_BG.name()}; color: {C_TEXT_GOLD.name()}; border-color: {C_TRI_GOLD.name()};")
            self.status_bar.showMessage("Радио выключено")

    def _open_secret_pin_dialog(self):
        log.info("Opening PIN Unlock Dialog for Secret Layer")
        dlg = PinUnlockDialog(parent=self)
        dlg.unlocked_success.connect(self._on_secret_unlocked)
        dlg.panic_triggered.connect(self._on_panic_triggered)
        dlg.exec()

    def _on_secret_unlocked(self, profile_mode: str):
        log.info("Opening Sovereign Cyber Telegram Messenger in mode: %s", profile_mode)
        if getattr(self, 'telegram_window', None) is None:
            self.telegram_window = CyberTelegramMessenger(mode=profile_mode, parent=None)
            self.telegram_window.closed_to_game.connect(self._on_secret_closed_to_game)
        else:
            self.telegram_window.mode = profile_mode
        self.telegram_window.show()
        self.telegram_window.activateWindow()

    def _on_secret_closed_to_game(self):
        log.info("Returned from secret layer to backgammon.")
        self.showNormal()
        self.activateWindow()
        self.status_bar.showMessage("Plausible Deniability: Back to Backgammon session")

    def _on_panic_triggered(self):
        log.critical("PANIC 48-HOUR LOCKDOWN ACTIVE! Fake profile engaged.")
        self._log_chat("[⚠️ PANIC LOCKDOWN: Encryption keys purged. Decoy engaged.]")
        self.status_bar.showMessage("⚠️ PANIC LOCKDOWN ENGAGED")

    def _quit_app(self):
        log.info("Application quit requested via tray or menu.")
        self.network.stop()
        if hasattr(self, 'tray_icon'):
            self.tray_icon.hide()
        QApplication.quit()

    def closeEvent(self, event):
        log.debug("closeEvent received")
        if hasattr(self, 'tray_icon') and self.tray_icon.isVisible():
            log.info("Window closeEvent: hiding to system tray.")
            self.hide()
            self.tray_icon.showMessage(
                "MadGammon",
                "Running in system tray / Работает в трее",
                QSystemTrayIcon.MessageIcon.Information, 2000)
            event.ignore()
        else:
            log.info("Window closeEvent: quitting application.")
            self.network.stop()
            event.accept()


# ═══════════════════════════════════════════════════════
#  ENTRY POINT
# ═══════════════════════════════════════════════════════
def main():
    log.info("Starting main() loop...")
    app = QApplication(sys.argv)
    app.setApplicationName("MadGammon")
    app.setOrganizationName("BG-AG")
    app.setQuitOnLastWindowClosed(False)

    app.setStyle("Fusion")
    log.info("QApplication created: platform=%s", app.platformName())

    window = MadGammonWindow()
    window.show()
    log.info("Window shown. Entering app.exec()...")

    exit_code = app.exec()
    log.info("Application exited with code: %d", exit_code)
    sys.exit(exit_code)


if __name__ == "__main__":
    main()
