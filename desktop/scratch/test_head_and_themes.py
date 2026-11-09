import sys
import os

sys.path.insert(0, os.path.abspath(os.path.join(os.path.dirname(__file__), "..")))

from PyQt6.QtWidgets import QApplication
from PyQt6.QtCore import QPointF
from engine import Player, GameMode, BackgammonRules
from themes import THEMES, get_theme
from audio_sfx import SoundManager
from madgammon_desktop import BoardWidget, MadGammonWindow

def test_head_player_logic():
    print("Testing head_player logic...")
    # 1. head_player = WHITE
    b_white = BackgammonRules.create_initial_board(GameMode.LONG, head_player=Player.WHITE)
    assert b_white[0].player == Player.WHITE and b_white[0].count == 15, "Point 0 must be 15 WHITE"
    assert b_white[12].player == Player.BLACK and b_white[12].count == 15, "Point 12 must be 15 BLACK"

    # White moves from 0 forward
    rel0_w = BackgammonRules.absolute_to_relative(Player.WHITE, 0, GameMode.LONG, head_player=Player.WHITE)
    assert rel0_w == 0, f"Expected 0, got {rel0_w}"
    dest_w = BackgammonRules.relative_to_absolute(Player.WHITE, 4, GameMode.LONG, head_player=Player.WHITE)
    assert dest_w == 4, f"Expected 4, got {dest_w}"

    # 2. head_player = BLACK
    b_black = BackgammonRules.create_initial_board(GameMode.LONG, head_player=Player.BLACK)
    assert b_black[0].player == Player.BLACK and b_black[0].count == 15, "Point 0 must be 15 BLACK"
    assert b_black[12].player == Player.WHITE and b_black[12].count == 15, "Point 12 must be 15 WHITE"

    # Black moves from 0 forward
    rel0_b = BackgammonRules.absolute_to_relative(Player.BLACK, 0, GameMode.LONG, head_player=Player.BLACK)
    assert rel0_b == 0, f"Expected 0, got {rel0_b}"
    dest_b = BackgammonRules.relative_to_absolute(Player.BLACK, 4, GameMode.LONG, head_player=Player.BLACK)
    assert dest_b == 4, f"Expected 4, got {dest_b}"

    # White moves from 12 forward
    rel0_w2 = BackgammonRules.absolute_to_relative(Player.WHITE, 12, GameMode.LONG, head_player=Player.BLACK)
    assert rel0_w2 == 0, f"Expected 0, got {rel0_w2}"
    dest_w2 = BackgammonRules.relative_to_absolute(Player.WHITE, 4, GameMode.LONG, head_player=Player.BLACK)
    assert dest_w2 == 16, f"Expected 16, got {dest_w2}"

    # Reachable paths for black with head_player=BLACK
    paths = BackgammonRules.find_reachable_paths_from(
        player=Player.BLACK,
        from_abs=0,
        remaining_dice=[4, 2],
        board=b_black,
        bar_count=0,
        cur_head_takes=0,
        is_first_turn=True,
        mode=GameMode.LONG,
        head_player=Player.BLACK
    )
    dest_set = {p.final_to_abs for p in paths}
    print(f"Reachable destinations for Black from point 0: {dest_set}")
    assert 4 in dest_set or 2 in dest_set, "Destinations 4 or 2 should be reachable"

    print("✓ Head player geometry test passed!")

def test_sfx():
    print("Testing SoundManager...")
    sm = SoundManager.get_instance()
    sm.play_dice_tumble()
    sm.play_dice_bounce()
    sm.play_checker_slide()
    sm.play_checker_land()
    print("✓ SoundManager methods executed cleanly!")

def test_themes_and_rendering():
    print("Testing themes and offscreen rendering...")
    os.environ["QT_QPA_PLATFORM"] = "offscreen"
    app = QApplication.instance() or QApplication(sys.argv)

    bw = BoardWidget()
    bw.resize(800, 560)
    bw.head_player = Player.BLACK
    bw.board = BackgammonRules.create_initial_board(GameMode.LONG, head_player=Player.BLACK)
    bw.dice = (5, 3)

    artifact_dir = "/home/thomas/.gemini/antigravity/brain/32d6ed13-c614-4edf-8b49-c992a581b6a0"
    for th_id in THEMES.keys():
        bw.set_theme(th_id)
        pix = bw.grab()
        out_path = os.path.join(artifact_dir, f"theme_{th_id}.png")
        pix.save(out_path)
        print(f"  ✓ Rendered theme '{th_id}' -> {out_path}")

    print("✓ All themes rendered successfully!")

if __name__ == "__main__":
    test_head_player_logic()
    test_sfx()
    test_themes_and_rendering()
