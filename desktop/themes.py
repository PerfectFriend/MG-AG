"""
MadGammon Visual Theme Engine
7 Signature Themes: Classic Wood, Malachite, Amber, Psychedelic, Gaudi, Van Gogh, Neon Cyberpunk.
Includes palettes, gradients, and custom procedural background ornaments.
"""
from dataclasses import dataclass
from typing import List, Tuple, Dict
from PyQt6.QtGui import QColor

@dataclass
class BoardTheme:
    id: str
    name: str
    desc: str
    bg_dark: QColor
    bg_panel: QColor
    board_felt: QColor
    board_frame: QColor
    board_border: QColor
    bar_bg: QColor
    tri_light: QColor
    tri_dark: QColor
    tri_light_border: QColor
    tri_dark_border: QColor
    checker_white_start: QColor
    checker_white_end: QColor
    checker_white_outline: QColor
    checker_black_start: QColor
    checker_black_end: QColor
    checker_black_outline: QColor
    text_gold: QColor
    text_cyan: QColor
    pattern_type: str

# ═══════════════════════════════════════════════════════
# 7 MASTER THEMES DEFINITION
# ═══════════════════════════════════════════════════════

THEMES: Dict[str, BoardTheme] = {
    "classic_wood": BoardTheme(
        id="classic_wood",
        name="🪵 Классическое дерево",
        desc="Резной орех, дубовые инкрустации, полированная латунь и теплый бархат",
        bg_dark=QColor(24, 16, 12),
        bg_panel=QColor(36, 24, 18),
        board_felt=QColor(46, 32, 22),
        board_frame=QColor(95, 52, 26),
        board_border=QColor(215, 170, 85),
        bar_bg=QColor(32, 20, 14),
        tri_light=QColor(215, 175, 120),
        tri_dark=QColor(70, 38, 20),
        tri_light_border=QColor(180, 140, 90),
        tri_dark_border=QColor(50, 25, 12),
        checker_white_start=QColor(255, 248, 230),
        checker_white_end=QColor(215, 195, 160),
        checker_white_outline=QColor(180, 150, 110),
        checker_black_start=QColor(60, 35, 22),
        checker_black_end=QColor(25, 14, 8),
        checker_black_outline=QColor(215, 170, 85),
        text_gold=QColor(230, 185, 95),
        text_cyan=QColor(220, 190, 130),
        pattern_type="wood_grain",
    ),

    "malachite": BoardTheme(
        id="malachite",
        name="❇️ Уральский малахит",
        desc="Изумрудные каменные разводы, узорная филигрань и уральская бронза",
        bg_dark=QColor(8, 22, 16),
        bg_panel=QColor(14, 34, 24),
        board_felt=QColor(12, 38, 28),
        board_frame=QColor(18, 56, 38),
        board_border=QColor(230, 195, 75),
        bar_bg=QColor(8, 26, 18),
        tri_light=QColor(0, 185, 120),
        tri_dark=QColor(12, 85, 55),
        tri_light_border=QColor(0, 230, 150),
        tri_dark_border=QColor(8, 60, 38),
        checker_white_start=QColor(235, 255, 245),
        checker_white_end=QColor(185, 225, 205),
        checker_white_outline=QColor(230, 195, 75),
        checker_black_start=QColor(20, 58, 42),
        checker_black_end=QColor(6, 22, 15),
        checker_black_outline=QColor(0, 230, 150),
        text_gold=QColor(235, 200, 80),
        text_cyan=QColor(80, 255, 180),
        pattern_type="malachite_bands",
    ),

    "amber": BoardTheme(
        id="amber",
        name="🍯 Балтийский янтарь",
        desc="Солнечные медовые смолы, карамельные глубины и золотой огонь",
        bg_dark=QColor(28, 14, 8),
        bg_panel=QColor(42, 22, 12),
        board_felt=QColor(52, 26, 12),
        board_frame=QColor(110, 50, 18),
        board_border=QColor(255, 185, 45),
        bar_bg=QColor(36, 16, 8),
        tri_light=QColor(245, 180, 50),
        tri_dark=QColor(140, 55, 15),
        tri_light_border=QColor(255, 215, 110),
        tri_dark_border=QColor(95, 35, 10),
        checker_white_start=QColor(255, 245, 210),
        checker_white_end=QColor(240, 195, 110),
        checker_white_outline=QColor(255, 215, 80),
        checker_black_start=QColor(75, 25, 10),
        checker_black_end=QColor(30, 10, 4),
        checker_black_outline=QColor(255, 160, 40),
        text_gold=QColor(255, 205, 70),
        text_cyan=QColor(255, 225, 140),
        pattern_type="amber_sunburst",
    ),

    "psychedelic": BoardTheme(
        id="psychedelic",
        name="🔮 Психоделика 60-х",
        desc="Кислотные радужные вихри, оптические иллюзии и космический индиго",
        bg_dark=QColor(22, 8, 38),
        bg_panel=QColor(36, 12, 60),
        board_felt=QColor(32, 10, 52),
        board_frame=QColor(85, 15, 120),
        board_border=QColor(255, 0, 150),
        bar_bg=QColor(18, 6, 30),
        tri_light=QColor(180, 255, 0),
        tri_dark=QColor(255, 0, 130),
        tri_light_border=QColor(220, 255, 80),
        tri_dark_border=QColor(255, 60, 180),
        checker_white_start=QColor(250, 240, 255),
        checker_white_end=QColor(210, 180, 245),
        checker_white_outline=QColor(0, 255, 230),
        checker_black_start=QColor(55, 15, 85),
        checker_black_end=QColor(15, 4, 25),
        checker_black_outline=QColor(255, 0, 160),
        text_gold=QColor(255, 220, 0),
        text_cyan=QColor(0, 255, 240),
        pattern_type="psychedelic_waves",
    ),

    "gaudi": BoardTheme(
        id="gaudi",
        name="🦎 Антонио Гауди",
        desc="Каталонская мозаика Тренкадис, Парк Гуэль, керамические осколки и арки",
        bg_dark=QColor(26, 20, 22),
        bg_panel=QColor(38, 30, 32),
        board_felt=QColor(28, 36, 42),
        board_frame=QColor(75, 52, 42),
        board_border=QColor(230, 140, 60),
        bar_bg=QColor(20, 24, 28),
        tri_light=QColor(235, 165, 45),
        tri_dark=QColor(20, 150, 165),
        tri_light_border=QColor(255, 200, 90),
        tri_dark_border=QColor(40, 195, 215),
        checker_white_start=QColor(250, 248, 242),
        checker_white_end=QColor(215, 220, 215),
        checker_white_outline=QColor(230, 140, 60),
        checker_black_start=QColor(30, 50, 75),
        checker_black_end=QColor(12, 22, 38),
        checker_black_outline=QColor(40, 195, 215),
        text_gold=QColor(245, 175, 60),
        text_cyan=QColor(70, 215, 235),
        pattern_type="gaudi_trencadis",
    ),

    "van_gogh": BoardTheme(
        id="van_gogh",
        name="🌌 Винсент Ван Гог",
        desc="Звёздная ночь, мазки масляной охры, вихри ультрамарина и кипарисы",
        bg_dark=QColor(10, 18, 38),
        bg_panel=QColor(16, 28, 55),
        board_felt=QColor(15, 26, 50),
        board_frame=QColor(32, 48, 75),
        board_border=QColor(245, 205, 55),
        bar_bg=QColor(10, 18, 35),
        tri_light=QColor(240, 195, 45),
        tri_dark=QColor(25, 70, 130),
        tri_light_border=QColor(255, 225, 95),
        tri_dark_border=QColor(45, 105, 185),
        checker_white_start=QColor(255, 252, 235),
        checker_white_end=QColor(230, 215, 160),
        checker_white_outline=QColor(245, 205, 55),
        checker_black_start=QColor(25, 35, 70),
        checker_black_end=QColor(8, 12, 28),
        checker_black_outline=QColor(90, 160, 245),
        text_gold=QColor(255, 215, 65),
        text_cyan=QColor(110, 195, 255),
        pattern_type="van_gogh_swirl",
    ),

    "neon": BoardTheme(
        id="neon",
        name="⚡ Киберпанк Неон",
        desc="Флагманский Cyber Neo-Tactic: темный обсидиан, лазерный циан и золотой HUD",
        bg_dark=QColor(30, 30, 42),
        bg_panel=QColor(38, 38, 55),
        board_felt=QColor(45, 42, 38),
        board_frame=QColor(60, 55, 48),
        board_border=QColor(0, 255, 255),
        bar_bg=QColor(22, 22, 30),
        tri_light=QColor(212, 175, 55),
        tri_dark=QColor(0, 168, 107),
        tri_light_border=QColor(160, 125, 30),
        tri_dark_border=QColor(0, 110, 70),
        checker_white_start=QColor(255, 255, 255),
        checker_white_end=QColor(200, 190, 170),
        checker_white_outline=QColor(212, 175, 55),
        checker_black_start=QColor(80, 80, 110),
        checker_black_end=QColor(20, 20, 35),
        checker_black_outline=QColor(80, 80, 120),
        text_gold=QColor(212, 175, 55),
        text_cyan=QColor(0, 229, 255),
        pattern_type="neon_circuit",
    ),
}

DEFAULT_THEME_ID = "neon"

def get_theme(theme_id: str) -> BoardTheme:
    return THEMES.get(theme_id, THEMES[DEFAULT_THEME_ID])
