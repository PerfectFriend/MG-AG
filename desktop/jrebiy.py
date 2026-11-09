"""
MadGammon Jrebiy (Lot/Draw) System
Full two-stage pre-match toss matching Android JrebiyDialog.kt:
Stage 1: Choose checker color (higher roll gets White).
Stage 2: Determine first move (higher roll moves first).
"""
import random
from typing import Tuple, Optional
from PyQt6.QtWidgets import (
    QDialog, QVBoxLayout, QHBoxLayout, QLabel, QPushButton,
    QFrame, QGraphicsDropShadowEffect
)
from PyQt6.QtGui import QFont, QColor, QPainter, QBrush, QPen, QRadialGradient
from PyQt6.QtCore import Qt, QTimer, pyqtSignal, QRectF, QPointF

from logger import get_logger

log = get_logger("madgammon.jrebiy")

C_BG_CARD = QColor(25, 27, 36)
C_GOLD = QColor(212, 175, 55)
C_EMERALD = QColor(0, 255, 128)
C_CYAN = QColor(0, 230, 255)
C_TEXT_WHITE = QColor(240, 240, 240)
C_TEXT_DIM = QColor(160, 160, 175)
C_DICE_BG = QColor(245, 240, 225)
C_DICE_DOT = QColor(30, 30, 30)

class SingleDieWidget(QFrame):
    def __init__(self, size: int = 56, parent=None):
        super().__init__(parent)
        self.size = size
        self.value = 1
        self.rolling = False
        self.setFixedSize(size, size)

    def setValue(self, val: int):
        self.value = max(1, min(6, val))
        self.update()

    def paintEvent(self, event):
        p = QPainter(self)
        p.setRenderHint(QPainter.RenderHint.Antialiasing)

        rect = QRectF(2, 2, self.width() - 4, self.height() - 4)
        p.setBrush(QBrush(C_DICE_BG))
        p.setPen(QPen(C_GOLD if not self.rolling else C_CYAN, 2))
        p.drawRoundedRect(rect, 8, 8)

        # Draw dots
        cx, cy = rect.center().x(), rect.center().y()
        w4 = rect.width() / 4
        h4 = rect.height() / 4
        r = 3.5

        p.setBrush(QBrush(C_DICE_DOT))
        p.setPen(Qt.PenStyle.NoPen)

        positions = {
            1: [(cx, cy)],
            2: [(cx - w4, cy - h4), (cx + w4, cy + h4)],
            3: [(cx - w4, cy - h4), (cx, cy), (cx + w4, cy + h4)],
            4: [(cx - w4, cy - h4), (cx + w4, cy - h4), (cx - w4, cy + h4), (cx + w4, cy + h4)],
            5: [(cx - w4, cy - h4), (cx + w4, cy - h4), (cx, cy), (cx - w4, cy + h4), (cx + w4, cy + h4)],
            6: [(cx - w4, cy - h4), (cx + w4, cy - h4), (cx - w4, cy), (cx + w4, cy), (cx - w4, cy + h4), (cx + w4, cy + h4)]
        }

        for dx, dy in positions.get(self.value, []):
            p.drawEllipse(QPointF(dx, dy), r, r)

        p.end()

class JrebiyDialog(QDialog):
    """
    Two-stage toss dialog:
    Stage 1: Color Draw (Player 1 vs Player 2)
    Stage 2: First Move Draw (White vs Black)
    """
    # Returns (human_color: str, first_mover: str)
    jrebiy_finished = pyqtSignal(str, str)

    def __init__(self, p1_name: str = "Игрок 1", p2_name: str = "Игрок 2", parent=None):
        super().__init__(parent)
        self.p1_name = p1_name
        self.p2_name = p2_name

        self.stage = 1  # 1 = COLOR, 2 = FIRST_MOVE
        self.p1_val = 0
        self.p2_val = 0
        self.assigned_white = ""
        self.assigned_black = ""
        self.first_mover = ""

        self.setWindowTitle("🎲 Предстартовая жеребьёвка / Pre-Match Toss")
        self.setFixedSize(500, 420)
        self.setStyleSheet(f"""
            QDialog {{ background: {C_BG_CARD.name()}; border: 2px solid {C_GOLD.name()}; border-radius: 12px; }}
            QLabel {{ color: {C_TEXT_WHITE.name()}; }}
            QPushButton {{
                background: #2a2a3e; color: {C_GOLD.name()};
                border: 1px solid {C_GOLD.name()}; padding: 8px 18px;
                border-radius: 6px; font-weight: bold; font-size: 13px;
            }}
            QPushButton:hover {{ background: #3c3c58; color: #fff; }}
        """)

        self._build_ui()
        self._anim_timer = QTimer(self)
        self._anim_timer.timeout.connect(self._on_roll_tick)
        self._anim_ticks = 0

    def _build_ui(self):
        layout = QVBoxLayout(self)
        layout.setSpacing(12)
        layout.setContentsMargins(20, 20, 20, 20)

        # Title
        self.lbl_title = QLabel("🎲 ЖЕРЕБЬЁВКА «ШЕШ-БЕШ»")
        self.lbl_title.setAlignment(Qt.AlignmentFlag.AlignCenter)
        self.lbl_title.setFont(QFont("Monospace", 14, QFont.Weight.Black))
        self.lbl_title.setStyleSheet(f"color: {C_GOLD.name()}; letter-spacing: 2px;")
        layout.addWidget(self.lbl_title)

        # Stage subtitle
        self.lbl_stage = QLabel("ЭТАП 1 ИЗ 2: ВЫБОР ЦВЕТА ФИШЕК")
        self.lbl_stage.setAlignment(Qt.AlignmentFlag.AlignCenter)
        self.lbl_stage.setFont(QFont("Monospace", 11, QFont.Weight.Bold))
        self.lbl_stage.setStyleSheet(f"color: {C_CYAN.name()};")
        layout.addWidget(self.lbl_stage)

        # Description
        self.lbl_desc = QLabel("Каждый бросает по одному кубику.\nВыбросивший больше играет Белыми!")
        self.lbl_desc.setAlignment(Qt.AlignmentFlag.AlignCenter)
        self.lbl_desc.setFont(QFont("Sans", 10))
        self.lbl_desc.setStyleSheet(f"color: {C_TEXT_DIM.name()};")
        layout.addWidget(self.lbl_desc)

        # Dice row
        dice_frame = QFrame()
        dice_frame.setStyleSheet("background: #1b1c26; border-radius: 8px; padding: 10px;")
        dice_layout = QHBoxLayout(dice_frame)

        # P1 Box
        p1_box = QVBoxLayout()
        self.lbl_p1 = QLabel(self.p1_name)
        self.lbl_p1.setAlignment(Qt.AlignmentFlag.AlignCenter)
        self.lbl_p1.setFont(QFont("Monospace", 10, QFont.Weight.Bold))
        self.die_p1 = SingleDieWidget(size=64)
        p1_box.addWidget(self.lbl_p1)
        p1_box.addWidget(self.die_p1, alignment=Qt.AlignmentFlag.AlignCenter)
        dice_layout.addLayout(p1_box)

        # VS label
        lbl_vs = QLabel("VS")
        lbl_vs.setFont(QFont("Monospace", 16, QFont.Weight.Black))
        lbl_vs.setStyleSheet(f"color: {C_GOLD.name()};")
        dice_layout.addWidget(lbl_vs, alignment=Qt.AlignmentFlag.AlignCenter)

        # P2 Box
        p2_box = QVBoxLayout()
        self.lbl_p2 = QLabel(self.p2_name)
        self.lbl_p2.setAlignment(Qt.AlignmentFlag.AlignCenter)
        self.lbl_p2.setFont(QFont("Monospace", 10, QFont.Weight.Bold))
        self.die_p2 = SingleDieWidget(size=64)
        p2_box.addWidget(self.lbl_p2)
        p2_box.addWidget(self.die_p2, alignment=Qt.AlignmentFlag.AlignCenter)
        dice_layout.addLayout(p2_box)

        layout.addWidget(dice_frame)

        # Status text
        self.lbl_result = QLabel("Нажмите «Бросить жребий»")
        self.lbl_result.setAlignment(Qt.AlignmentFlag.AlignCenter)
        self.lbl_result.setFont(QFont("Monospace", 11, QFont.Weight.Bold))
        self.lbl_result.setStyleSheet("color: #ffb300;")
        layout.addWidget(self.lbl_result)

        # Buttons
        btn_layout = QHBoxLayout()
        self.btn_roll = QPushButton("🎲 Бросить жребий")
        self.btn_roll.clicked.connect(self._start_roll)
        btn_layout.addWidget(self.btn_roll)

        self.btn_next = QPushButton("Далее →")
        self.btn_next.setEnabled(False)
        self.btn_next.clicked.connect(self._on_next)
        btn_layout.addWidget(self.btn_next)

        layout.addLayout(btn_layout)

    def _start_roll(self):
        self.btn_roll.setEnabled(False)
        self.btn_next.setEnabled(False)
        self.die_p1.rolling = True
        self.die_p2.rolling = True
        self.lbl_result.setText("Кубики крутятся...")
        self.lbl_result.setStyleSheet("color: #00e5ff;")
        self._anim_ticks = 0
        self._anim_timer.start(50)

    def _on_roll_tick(self):
        self._anim_ticks += 1
        self.die_p1.setValue(random.randint(1, 6))
        self.die_p2.setValue(random.randint(1, 6))

        if self._anim_ticks >= 18:
            self._anim_timer.stop()
            self.die_p1.rolling = False
            self.die_p2.rolling = False

            # Final values
            v1 = random.randint(1, 6)
            v2 = random.randint(1, 6)
            self.die_p1.setValue(v1)
            self.die_p2.setValue(v2)
            self.p1_val = v1
            self.p2_val = v2

            self._process_roll_result(v1, v2)

    def _process_roll_result(self, v1: int, v2: int):
        if v1 == v2:
            self.lbl_result.setText(f"Ничья ({v1}:{v2})! Бросьте снова.")
            self.lbl_result.setStyleSheet("color: #ff5252;")
            self.btn_roll.setEnabled(True)
            return

        if self.stage == 1:
            # Color toss
            if v1 > v2:
                winner = self.p1_name
                self.assigned_white = "WHITE"
                self.assigned_black = "BLACK"
                res_text = f"👑 {winner} выбросил {v1} против {v2} и играет БЕЛЫМИ!"
            else:
                winner = self.p2_name
                self.assigned_white = "BLACK"
                self.assigned_black = "WHITE"
                res_text = f"👑 {winner} выбросил {v2} против {v1} и играет БЕЛЫМИ!"

            self.lbl_result.setText(res_text)
            self.lbl_result.setStyleSheet("color: #00ff66;")
            self.btn_next.setEnabled(True)
            self.btn_next.setText("Перейти к этапу 2 →")

        elif self.stage == 2:
            # First move toss
            if v1 > v2:
                winner = "Белые"
                self.first_mover = "WHITE"
                res_text = f"⚡ {winner} выбросили {v1} против {v2} и ХОДЯТ ПЕРВЫМИ!"
            else:
                winner = "Чёрные"
                self.first_mover = "BLACK"
                res_text = f"⚡ {winner} выбросили {v2} против {v1} и ХОДЯТ ПЕРВЫМИ!"

            self.lbl_result.setText(res_text)
            self.lbl_result.setStyleSheet("color: #00ff66;")
            self.btn_next.setEnabled(True)
            self.btn_next.setText("Начать игру 🚀")

    def _on_next(self):
        if self.stage == 1:
            # Move to stage 2
            self.stage = 2
            self.lbl_stage.setText("ЭТАП 2 ИЗ 2: ОПРЕДЕЛЕНИЕ ПЕРВОГО ХОДА")
            self.lbl_desc.setText("Белые и Чёрные бросают по одному кубику.\nВыбросивший больше число ходит первым!")
            self.lbl_p1.setText(f"⚪ Белые ({self.p1_name if self.assigned_white == 'WHITE' else self.p2_name})")
            self.lbl_p2.setText(f"⚫ Чёрные ({self.p2_name if self.assigned_white == 'WHITE' else self.p1_name})")
            self.lbl_result.setText("Нажмите «Бросить жребий»")
            self.lbl_result.setStyleSheet("color: #ffb300;")
            self.btn_roll.setEnabled(True)
            self.btn_next.setEnabled(False)
        else:
            # Complete
            log.info("Jrebiy complete! assigned_white=%s, first_mover=%s", self.assigned_white, self.first_mover)
            self.jrebiy_finished.emit(self.assigned_white, self.first_mover)
            self.accept()
