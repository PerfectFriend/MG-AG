#!/usr/bin/env python3
import sys
import os
import time

# Headless offscreen mode
os.environ["QT_QPA_PLATFORM"] = "offscreen"
sys.path.insert(0, os.path.join(os.path.dirname(os.path.abspath(__file__)), ".."))

from PyQt6.QtWidgets import QApplication
from PyQt6.QtCore import QPointF, Qt, QTimer
from PyQt6.QtGui import QPixmap, QPainter, QColor

from madgammon_desktop import MadGammonWindow, BoardWidget, GameMode, Player
from engine import ReachablePath, PathStep

def main():
    app = QApplication(sys.argv)
    
    # Mock JrebiyDialog to avoid modal dialogs
    from jrebiy import JrebiyDialog
    JrebiyDialog.exec = lambda self: 1

    win = MadGammonWindow()
    win.resize(1280, 800)
    win.show()

    # Start game
    win._start_local_game()
    board_w = win.board_widget

    # Set dice (e.g. 3, 5)
    win.dice = (3, 5)
    win.remaining_dice = [3, 5]
    win.dice_used = [False, False]
    win.current_player = Player.WHITE
    win._update_board()

    # 1. Click point 0 (White Head)
    win._on_point_click(0)
    print("Selected source:", win.selected_source)
    print("Reachable paths count:", len(win.reachable_paths))
    for pth in win.reachable_paths:
        print(f"Path to: {pth.final_to_abs}, multi: {pth.is_multi_step}, dice: {pth.total_dice_used}")

    # Render board widget to pixmap to verify red outline rendering
    pm_highlight = QPixmap(board_w.size())
    board_w.render(pm_highlight)
    out_dir = "/home/thomas/.gemini/antigravity/brain/32d6ed13-c614-4edf-8b49-c992a581b6a0"
    os.makedirs(out_dir, exist_ok=True)
    pm_highlight.save(f"{out_dir}/test_red_highlight.png")
    print("Saved test_red_highlight.png")

    # 2. Test animation: Execute move to destination (e.g. destination of die 3, which is point 3)
    target_path = win.reachable_paths[0]
    dst = target_path.final_to_abs
    print(f"Executing move to {dst} with path: {target_path}")

    anim_done = [False]
    def on_done():
        anim_done[0] = True
        print("Move animation finished callback fired!")

    # Start animation
    win.board_widget.start_checker_animation(target_path, Player.WHITE, on_done)
    print("is_checker_animating:", win.board_widget.is_checker_animating)
    print("steps count:", len(win.board_widget.checker_anim_steps))

    # Advance 7 frames (mid-flight apex)
    for frame in range(7):
        win.board_widget._on_checker_anim_tick()
    
    print("Mid-flight frame:", win.board_widget.checker_anim_frame)
    print("Mid-flight elevation:", win.board_widget.checker_anim_elevation)
    print("Mid-flight current pos:", win.board_widget.checker_anim_current_pos)
    assert win.board_widget.checker_anim_elevation > 30.0, "Elevation should be near peak at mid-flight!"

    pm_flight = QPixmap(board_w.size())
    board_w.render(pm_flight)
    pm_flight.save(f"{out_dir}/test_checker_midflight.png")
    print("Saved test_checker_midflight.png")

    # Finish remaining frames to complete animation
    while win.board_widget.is_checker_animating:
        win.board_widget._on_checker_anim_tick()

    assert anim_done[0], "Animation callback must be triggered!"
    print("Animation completed successfully!")

    # 3. Test full execution through _execute_move
    win._execute_move(0, dst)
    print("Executed through _execute_move, waiting for animation to finish...")
    while win.board_widget.is_checker_animating:
        win.board_widget._on_checker_anim_tick()

    print("Post-move last_move_src:", win.board_widget.last_move_src)
    print("Post-move last_move_dst:", win.board_widget.last_move_dst)
    assert win.board_widget.last_move_src == 0
    assert win.board_widget.last_move_dst == dst

    pm_landed = QPixmap(board_w.size())
    board_w.render(pm_landed)
    pm_landed.save(f"{out_dir}/test_checker_landed.png")
    print("Saved test_checker_landed.png")

    # Render entire window
    pm_win = QPixmap(win.size())
    win.render(pm_win)
    pm_win.save(f"{out_dir}/test_window_full.png")
    print("Saved test_window_full.png")

    print("ALL TESTS PASSED SUCCESSFULLY!")

if __name__ == "__main__":
    main()
