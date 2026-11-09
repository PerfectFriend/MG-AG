"""
MadGammon AI Bot — Autonomous Intelligent Opponent
Supports three distinct strategic game modes:
  1. Long Nardy (Длинные нарды) — Prime construction, head clearing, blocking, pip-race.
  2. Short Backgammon (Короткие нарды) — Blot safety, hitting, anchor points, home trap board.
  3. Mad Gammon (Бешеные нарды) — Anti-stuck cascade flexibility (1..6 coverage), opponent choke traps, exact home distribution.

Includes three difficulty levels:
  - EASY: Heuristic evaluation with casual noise.
  - MEDIUM: Full expert positional evaluation.
  - HARD: Expectiminimax lookahead with roll probability weighting.
"""

import copy
import random
from typing import List, Optional, Tuple, Dict
from dataclasses import dataclass
from logger import get_logger
from engine import (
    Player, GameMode, CheckerStack, BackgammonRules,
    PathStep, ReachablePath, BOARD_POINTS
)

log = get_logger("madgammon.ai")


class AIDifficulty:
    EASY = "EASY"
    MEDIUM = "MEDIUM"
    HARD = "HARD"


# Direct hit probability table for Short Backgammon (out of 36 dice outcomes)
DIRECT_HIT_PROB: Dict[int, float] = {
    1: 11 / 36.0,  # 1 on either die or combo (1-x, x-1) -> 11 rolls
    2: 12 / 36.0,  # 12 rolls (2 on either die + 1:1)
    3: 14 / 36.0,  # 14 rolls (3 on die + 1:2, 2:1, 1:1)
    4: 15 / 36.0,  # 15 rolls
    5: 15 / 36.0,  # 15 rolls
    6: 17 / 36.0,  # 17 rolls
    7: 6 / 36.0,   # indirect combination
    8: 6 / 36.0,
    9: 5 / 36.0,
    10: 3 / 36.0,
    11: 2 / 36.0,
    12: 3 / 36.0,
}


@dataclass
class BoardStateSnapshot:
    board: List[CheckerStack]
    bar_white: int
    bar_black: int
    borne_off_white: int
    borne_off_black: int


def clone_board(board: List[CheckerStack]) -> List[CheckerStack]:
    return [CheckerStack(st.count, st.player) for st in board]


def apply_path_to_state(
    board: List[CheckerStack],
    bar_white: int,
    bar_black: int,
    borne_w: int,
    borne_b: int,
    player: str,
    path: ReachablePath,
    mode: str = GameMode.LONG
) -> Tuple[List[CheckerStack], int, int, int, int]:
    """Applies a path sequentially to a simulated board state."""
    sim_board = clone_board(board)
    bw, bb = bar_white, bar_black
    ow, ob = borne_w, borne_b

    for step in path.steps:
        s_from = step.from_abs
        s_to = step.to_abs

        # Remove from source
        if s_from == -1:
            if player == Player.WHITE:
                bw = max(0, bw - 1)
            else:
                bb = max(0, bb - 1)
        else:
            sim_board[s_from].count -= 1
            if sim_board[s_from].count == 0:
                sim_board[s_from].player = None

        # Add to destination
        if s_to == 24:
            if player == Player.WHITE:
                ow += 1
            else:
                ob += 1
        else:
            dest = sim_board[s_to]
            # Hit blot in Short Backgammon
            if mode == GameMode.SHORT and dest.count == 1 and dest.player and dest.player != player:
                if dest.player == Player.WHITE:
                    bw += 1
                else:
                    bb += 1
                dest.count = 1
                dest.player = player
            else:
                if dest.count == 0 or dest.player is None:
                    dest.player = player
                    dest.count = 1
                else:
                    dest.count += 1

    return sim_board, bw, bb, ow, ob


# ═══════════════════════════════════════════════════════
#  LONG NARDY STRATEGY EVALUATOR
# ═══════════════════════════════════════════════════════
class LongNardyEvaluator:
    """
    Expert heuristic evaluation for Long Nardy (Длинные нарды):
      - Prime building (заборы из 3..6 пунктов подряд)
      - Opponent head blockade
      - Pip count minimization (racing lead)
      - Avoiding heavy clogging (> 5 checkers on one point)
      - Safe entry and bear-off acceleration
    """
    @staticmethod
    def evaluate(
        board: List[CheckerStack],
        player: str,
        bar_white: int,
        bar_black: int,
        borne_w: int,
        borne_b: int
    ) -> float:
        opponent = Player.opponent(player)
        score = 0.0

        my_borne = borne_w if player == Player.WHITE else borne_b
        opp_borne = borne_b if player == Player.WHITE else borne_w
        score += my_borne * 65.0 - opp_borne * 65.0

        if my_borne >= 15:
            return 999999.0
        if opp_borne >= 15:
            return -999999.0

        my_pips = 0
        opp_pips = 0

        my_occupied_points: List[int] = []
        opp_occupied_points: List[int] = []

        my_head_abs = 0 if player == Player.WHITE else 12
        opp_head_abs = 12 if player == Player.WHITE else 0

        # Pip-count & occupancy analysis
        for i in range(BOARD_POINTS):
            st = board[i]
            if st.player == player and st.count > 0:
                rel = BackgammonRules.absolute_to_relative(player, i, GameMode.LONG)
                my_pips += (24 - rel) * st.count
                my_occupied_points.append(rel)

                # Clogging penalty for > 5 checkers on non-head points
                if i != my_head_abs and st.count > 5:
                    score -= (st.count - 5) * 14.0

                # Bonus for having checkers in home (rel 18..23)
                if rel >= 18:
                    score += 18.0

            elif st.player == opponent and st.count > 0:
                rel = BackgammonRules.absolute_to_relative(opponent, i, GameMode.LONG)
                opp_pips += (24 - rel) * st.count
                opp_occupied_points.append(rel)

        # Pip race advantage (less distance remaining is better)
        score += (opp_pips - my_pips) * 2.2

        # Prime evaluation: consecutive occupied points
        my_prime_bonus = LongNardyEvaluator._calc_prime_score(my_occupied_points)
        opp_prime_bonus = LongNardyEvaluator._calc_prime_score(opp_occupied_points)
        score += my_prime_bonus * 1.5 - opp_prime_bonus * 1.5

        # Opponent Head Blockade:
        # Check if player occupies points right outside opponent's head
        # Opponent head in player's relative coords is rel=12
        for offset in range(1, 6):
            check_rel = 12 + offset
            if check_rel < 24 and check_rel in my_occupied_points:
                score += (6 - offset) * 12.0  # Closer to their head = stronger blockade

        # Head management: penalize having too many checkers trapped on head
        my_head_count = board[my_head_abs].count if board[my_head_abs].player == player else 0
        if my_head_count > 10:
            score -= (my_head_count - 10) * 8.0

        return score

    @staticmethod
    def _calc_prime_score(occupied_rel_points: List[int]) -> float:
        """Calculates bonus for contiguous blocks of points (prímes)."""
        if not occupied_rel_points:
            return 0.0
        sorted_pts = sorted(set(occupied_rel_points))
        max_streak = 1
        current_streak = 1
        for i in range(1, len(sorted_pts)):
            if sorted_pts[i] == sorted_pts[i - 1] + 1:
                current_streak += 1
                if current_streak > max_streak:
                    max_streak = current_streak
            else:
                current_streak = 1

        prime_rewards = {
            2: 10.0,
            3: 35.0,
            4: 80.0,
            5: 160.0,
            6: 350.0  # Full 6-prime trap!
        }
        return prime_rewards.get(min(max_streak, 6), 0.0)


# ═══════════════════════════════════════════════════════
#  SHORT BACKGAMMON STRATEGY EVALUATOR
# ═══════════════════════════════════════════════════════
class ShortBackgammonEvaluator:
    """
    Expert heuristic evaluation for Short Backgammon (Короткие нарды):
      - Blot safety (penalize single checkers based on direct/indirect hit probability)
      - Blot hitting (reward putting opponent on bar)
      - Anchor holding in opponent home board
      - Home board strength (points made in home to trap opponent on bar)
      - Pip count race
    """
    @staticmethod
    def evaluate(
        board: List[CheckerStack],
        player: str,
        bar_white: int,
        bar_black: int,
        borne_w: int,
        borne_b: int
    ) -> float:
        opponent = Player.opponent(player)
        score = 0.0

        my_bar = bar_white if player == Player.WHITE else bar_black
        opp_bar = bar_black if player == Player.WHITE else bar_white
        my_borne = borne_w if player == Player.WHITE else borne_b
        opp_borne = borne_b if player == Player.WHITE else borne_w

        if my_borne >= 15:
            return 999999.0
        if opp_borne >= 15:
            return -999999.0

        # Bar control
        score += opp_bar * 170.0  # Huge reward for opponent on bar
        score -= my_bar * 190.0   # Severe penalty for being on bar

        score += my_borne * 55.0 - opp_borne * 55.0

        my_pips = my_bar * 25
        opp_pips = opp_bar * 25

        my_blots_rel: List[int] = []
        opp_positions_rel: List[int] = []
        my_home_points_made = 0

        for i in range(BOARD_POINTS):
            st = board[i]
            if st.player == player and st.count > 0:
                rel = BackgammonRules.absolute_to_relative(player, i, GameMode.SHORT)
                my_pips += (24 - rel) * st.count

                if st.count == 1:
                    my_blots_rel.append(rel)
                elif st.count >= 2:
                    # Anchor in opponent home (rel 0..5)
                    if rel < 6:
                        score += 35.0
                    # Made point in home board (rel 18..23)
                    elif rel >= 18:
                        my_home_points_made += 1
                        score += 28.0

            elif st.player == opponent and st.count > 0:
                opp_rel = BackgammonRules.absolute_to_relative(opponent, i, GameMode.SHORT)
                opp_pips += (24 - opp_rel) * st.count
                opp_positions_rel.append(opp_rel)

        # Pip race
        score += (opp_pips - my_pips) * 1.8

        # Home board strength multiplier (each made point in home increases danger for opponent on bar)
        if opp_bar > 0:
            score += my_home_points_made * 45.0

        # Blot danger analysis: calculate hit probability for each blot
        for blot_rel in my_blots_rel:
            blot_danger = 0.0
            for opp_rel in opp_positions_rel:
                # In Short Backgammon, opponent moves in reverse relative direction
                # Distance from opponent to my blot:
                # My blot is at my rel, which is (23 - opp_rel_of_same_point)
                dist = (23 - blot_rel) - opp_rel
                if 1 <= dist <= 12:
                    prob = DIRECT_HIT_PROB.get(dist, 0.0)
                    blot_danger = max(blot_danger, prob)

            # Extra penalty if blot is inside our home (opponent entry hits us deeper)
            pos_weight = 1.6 if blot_rel >= 18 else 1.0
            score -= blot_danger * 160.0 * pos_weight

        return score


# ═══════════════════════════════════════════════════════
#  MAD GAMMON STRATEGY EVALUATOR (Бешеные нарды)
# ═══════════════════════════════════════════════════════
class MadGammonEvaluator:
    """
    Expert heuristic evaluation for Mad Gammon (Бешеные нарды):
      - ANTI-STUCK MOBILITY (Flexibility): Evaluates legal move availability for
        ALL dice values 1..6. Prevents getting stuck on crazy kush cascades.
      - OPPONENT CHOKE TRAPS: Rewards blocking opponent on key dice so their cascade
        transfers to the AI!
      - EXACT HOME DISTRIBUTION: Since moves within home are forbidden and bear-off
        requires exact pip match, spread checkers across 18..23.
      - Strictly 1 head take management.
    """
    @staticmethod
    def evaluate(
        board: List[CheckerStack],
        player: str,
        bar_white: int,
        bar_black: int,
        borne_w: int,
        borne_b: int
    ) -> float:
        opponent = Player.opponent(player)
        score = 0.0

        my_borne = borne_w if player == Player.WHITE else borne_b
        opp_borne = borne_b if player == Player.WHITE else borne_w

        if my_borne >= 15:
            return 999999.0
        if opp_borne >= 15:
            return -999999.0

        score += my_borne * 75.0 - opp_borne * 75.0

        my_pips = 0
        opp_pips = 0
        home_occupied_pips = set()
        home_clogged_count = 0

        for i in range(BOARD_POINTS):
            st = board[i]
            if st.player == player and st.count > 0:
                rel = BackgammonRules.absolute_to_relative(player, i, GameMode.MAD)
                my_pips += (24 - rel) * st.count

                if rel >= 18:
                    home_occupied_pips.add(rel)
                    if st.count > 2:
                        home_clogged_count += (st.count - 2)

            elif st.player == opponent and st.count > 0:
                opp_rel = BackgammonRules.absolute_to_relative(opponent, i, GameMode.MAD)
                opp_pips += (24 - opp_rel) * st.count

        score += (opp_pips - my_pips) * 1.5

        # ─── 1. Home Distribution in Mad Gammon ───
        # Checkers cannot move inside home! Reward wide coverage across 18..23
        score += len(home_occupied_pips) * 35.0
        # Heavy penalty for stacking too many checkers on one home point
        score -= home_clogged_count * 30.0

        # ─── 2. Anti-Stuck Mobility Test (1..6 coverage) ───
        # Check how many distinct dice values 1..6 have at least one legal step
        my_playable_dice = 0
        for test_die in range(1, 7):
            has_move = False
            for p_idx in range(BOARD_POINTS):
                if board[p_idx].player == player and board[p_idx].count > 0:
                    rel_p = BackgammonRules.absolute_to_relative(player, p_idx, GameMode.MAD)
                    if BackgammonRules.is_single_step_legal(
                        player, rel_p, test_die, board, 0,
                        cur_head_takes=0, is_first_turn=False, mode=GameMode.MAD
                    ):
                        has_move = True
                        break
            if has_move:
                my_playable_dice += 1

        # Massive reward for full 6-die coverage (immunity to getting stuck)
        if my_playable_dice == 6:
            score += 220.0
        elif my_playable_dice == 5:
            score += 130.0
        elif my_playable_dice <= 3:
            score -= (4 - my_playable_dice) * 90.0  # Dangerously low mobility!

        # ─── 3. Opponent Choke Trap ───
        # Calculate how restricted the opponent is across dice 1..6
        opp_playable_dice = 0
        for test_die in range(1, 7):
            has_move = False
            for p_idx in range(BOARD_POINTS):
                if board[p_idx].player == opponent and board[p_idx].count > 0:
                    rel_p = BackgammonRules.absolute_to_relative(opponent, p_idx, GameMode.MAD)
                    if BackgammonRules.is_single_step_legal(
                        opponent, rel_p, test_die, board, 0,
                        cur_head_takes=0, is_first_turn=False, mode=GameMode.MAD
                    ):
                        has_move = True
                        break
            if has_move:
                opp_playable_dice += 1

        # Reward choking opponent (each blocked die makes them hand over their cascade!)
        score += (6 - opp_playable_dice) * 45.0

        return score


# ═══════════════════════════════════════════════════════
#  UNIFIED AI BOT ENGINE
# ═══════════════════════════════════════════════════════
class BackgammonAI:
    """
    Main AI engine managing decision-making, move generation, and evaluation
    across Long, Short, and Mad Backgammon with customizable difficulty.
    """

    @staticmethod
    def get_evaluator(mode: str):
        if mode == GameMode.SHORT:
            return ShortBackgammonEvaluator
        elif mode == GameMode.MAD:
            return MadGammonEvaluator
        else:
            return LongNardyEvaluator

    @staticmethod
    def evaluate_position(
        board: List[CheckerStack],
        player: str,
        bar_white: int,
        bar_black: int,
        borne_w: int,
        borne_b: int,
        mode: str
    ) -> float:
        evaluator = BackgammonAI.get_evaluator(mode)
        return evaluator.evaluate(board, player, bar_white, bar_black, borne_w, borne_b)

    @staticmethod
    def select_best_move(
        board: List[CheckerStack],
        player: str,
        remaining_dice: List[int],
        bar_white: int = 0,
        bar_black: int = 0,
        borne_w: int = 0,
        borne_b: int = 0,
        cur_head_takes: int = 0,
        is_first_turn: bool = False,
        mode: str = GameMode.LONG,
        difficulty: str = AIDifficulty.MEDIUM,
        head_player: str = Player.WHITE
    ) -> Optional[ReachablePath]:
        """
        Finds all legal candidate paths and selects the optimal path based on
        game mode and difficulty setting.
        """
        if not remaining_dice:
            return None

        bar_c = bar_white if player == Player.WHITE else bar_black

        # Collect all reachable paths from all valid source positions
        candidate_paths: List[ReachablePath] = []

        # If on bar, must enter from bar
        if bar_c > 0:
            paths = BackgammonRules.find_reachable_paths_from(
                player=player,
                from_abs=-1,
                remaining_dice=remaining_dice,
                board=board,
                bar_count=bar_c,
                cur_head_takes=cur_head_takes,
                is_first_turn=is_first_turn,
                mode=mode,
                head_player=head_player
            )
            candidate_paths.extend(paths)
        else:
            for src_abs in range(BOARD_POINTS):
                stack = board[src_abs]
                if stack.player == player and stack.count > 0:
                    paths = BackgammonRules.find_reachable_paths_from(
                        player=player,
                        from_abs=src_abs,
                        remaining_dice=remaining_dice,
                        board=board,
                        bar_count=bar_c,
                        cur_head_takes=cur_head_takes,
                        is_first_turn=is_first_turn,
                        mode=mode,
                        head_player=head_player
                    )
                    candidate_paths.extend(paths)

        if not candidate_paths:
            log.info("AI has no legal candidate paths.")
            return None

        log.debug("AI considering %d candidate paths for %s in %s mode", len(candidate_paths), player, mode)

        # Evaluate candidates
        best_path: Optional[ReachablePath] = None
        best_score = -float("inf")

        for path in candidate_paths:
            sim_board, sim_bw, sim_bb, sim_ow, sim_ob = apply_path_to_state(
                board, bar_white, bar_black, borne_w, borne_b, player, path, mode
            )

            score = BackgammonAI.evaluate_position(
                sim_board, player, sim_bw, sim_bb, sim_ow, sim_ob, mode
            )

            # Combo bonus: prefer consuming more dice in one turn if advantageous
            score += len(path.steps) * 4.0

            if difficulty == AIDifficulty.HARD:
                # 2-ply mini expectimax lookahead: sample 3 representative opponent rolls
                opp = Player.opponent(player)
                sample_rolls = [(3, 4), (1, 6), (5, 5)]
                opp_eval_sum = 0.0
                for d1, d2 in sample_rolls:
                    dice_sample = [d1] * 4 if d1 == d2 else [d1, d2]
                    opp_bar = sim_bb if opp == Player.BLACK else sim_bw
                    opp_paths = []
                    for opp_src in range(BOARD_POINTS):
                        if sim_board[opp_src].player == opp and sim_board[opp_src].count > 0:
                            opp_paths.extend(BackgammonRules.find_reachable_paths_from(
                                opp, opp_src, dice_sample, sim_board, opp_bar, 0, False, mode, head_player
                            ))
                    if opp_paths:
                        # Opponent picks their best response
                        best_opp_resp = max(
                            BackgammonAI.evaluate_position(
                                apply_path_to_state(sim_board, sim_bw, sim_bb, sim_ow, sim_ob, opp, p, mode)[0],
                                opp, sim_bw, sim_bb, sim_ow, sim_ob, mode
                            )
                            for p in opp_paths[:6]
                        )
                        opp_eval_sum += best_opp_resp

                score -= (opp_eval_sum / len(sample_rolls)) * 0.45

            elif difficulty == AIDifficulty.EASY:
                # Add natural human-like noise to candidate evaluation
                score += random.gauss(0, 35.0)

            if score > best_score:
                best_score = score
                best_path = path

        if best_path:
            log.info("AI selected path to %d with score %.1f (dice: %s)",
                     best_path.final_to_abs, best_score, best_path.total_dice_used)

        return best_path
