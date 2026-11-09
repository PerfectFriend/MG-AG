import sys
import os

sys.path.insert(0, os.path.abspath(os.path.join(os.path.dirname(__file__), "..")))

from PyQt6.QtWidgets import QApplication
from PyQt6.QtCore import Qt, QPoint
from engine import Player, GameMode, BackgammonRules
from madgammon_desktop import MadGammonWindow

def test_full_window_lifecycle():
    os.environ["QT_QPA_PLATFORM"] = "offscreen"
    app = QApplication.instance() or QApplication(sys.argv)

    window = MadGammonWindow()
    window.resize(1100, 750)
    window.show()

    # Verify initial theme
    assert window.board_widget.theme_id == "neon"
    print("✓ Window initialized with default theme 'neon'")

    # Change theme via menu method
    for th in ["classic_wood", "malachite", "amber", "psychedelic", "gaudi", "van_gogh", "neon"]:
        window._set_theme_via_menu(th)
        assert window.board_widget.theme_id == th
        print(f"  ✓ Theme switched to '{th}'")

    # Start local game with head_player=BLACK
    window.local_color = Player.BLACK
    window.bot_color = Player.WHITE
    window.head_player = Player.BLACK
    window.board = BackgammonRules.create_initial_board(GameMode.LONG, head_player=Player.BLACK)
    window.board_widget.head_player = Player.BLACK
    window.game_started = True
    window.game_over = False
    window.current_player = Player.BLACK

    # Verify local player's 15 checkers are at point 0 (top-right)
    assert window.board[0].player == Player.BLACK and window.board[0].count == 15
    assert window.board[12].player == Player.WHITE and window.board[12].count == 15
    print("✓ Head player is BLACK, 15 black checkers at top-right point 0!")

    # Simulate roll dice
    window.dice = (4, 2)
    window.remaining_dice = [4, 2]
    window.dice_used = [False, False]
    window._update_board()

    # Click point 0 to select
    window._on_point_click(0)
    assert window.selected_source == 0
    assert len(window.reachable_paths) > 0
    print(f"✓ Point 0 selected, found {len(window.reachable_paths)} valid paths with red line outlines")

    # Move to destination
    dest = window.reachable_paths[0].final_to_abs
    window._on_point_click(dest)
    print(f"✓ Move initiated to {dest}!")

    # Verify screenshot of window
    artifact_dir = "/home/thomas/.gemini/antigravity/brain/32d6ed13-c614-4edf-8b49-c992a581b6a0"
    pix = window.grab()
    pix.save(os.path.join(artifact_dir, "test_window_theme_and_head.png"))
    print("✓ Window screenshot saved!")

if __name__ == "__main__":
    test_full_window_lifecycle()
