import hmac
import hashlib
import os
import random
from typing import List, Optional, Tuple, Dict
from logger import get_logger

log = get_logger("madgammon.engine")

from dataclasses import dataclass

@dataclass
class PathStep:
    from_abs: int   # -1 for bar, 0..23
    to_abs: int     # 0..23, or 24 for bear off
    from_rel: int
    to_rel: int
    die_used: int

@dataclass
class ReachablePath:
    final_to_abs: int   # 0..23 or 24 for bear off
    final_to_rel: int   # 0..23 or 24
    steps: List[PathStep]

    @property
    def total_dice_used(self) -> List[int]:
        return [s.die_used for s in self.steps]

    @property
    def is_multi_step(self) -> bool:
        return len(self.steps) > 1

    @property
    def intermediate_points(self) -> List[int]:
        # intermediate points before final destination
        return [s.to_abs for s in self.steps[:-1]]

BOARD_POINTS = 24

class Player:
    WHITE = "WHITE"
    BLACK = "BLACK"

    @staticmethod
    def opponent(player: str) -> str:
        return Player.BLACK if player == Player.WHITE else Player.WHITE

class GameMode:
    LONG = "LONG"
    SHORT = "SHORT"
    MAD = "MAD"

class MentalDiceEngine:
    """
    Provably fair cryptographic dice roll engine using HMAC-SHA256 commit-reveal.
    Interoperable with Android Kotlin and Rust core implementations.
    """
    def __init__(self):
        self.local_seed: Optional[bytes] = None
        self.local_secret_val: int = 0
        self.local_commitment_hex: Optional[str] = None
        self.peer_commitment_hex: Optional[str] = None

    def start_local_roll(self) -> str:
        self.local_seed = os.urandom(32)
        self.local_secret_val = random.randint(0, 5)
        h = hmac.new(self.local_seed, bytes([self.local_secret_val]), hashlib.sha256)
        self.local_commitment_hex = h.hexdigest()
        log.debug("Generated local roll commitment: val=%d, hash=%s", self.local_secret_val, self.local_commitment_hex[:16])
        return self.local_commitment_hex

    def set_peer_commitment(self, commitment_hex: str):
        self.peer_commitment_hex = commitment_hex.strip().lower()
        log.debug("Set peer dice commitment: %s", self.peer_commitment_hex[:16])

    def reveal_local_secret(self) -> Tuple[str, int]:
        if not self.local_seed:
            log.error("Cannot reveal secret: local commitment not initialized")
            raise ValueError("Local commitment not initialized")
        log.debug("Revealing local secret: val=%d", self.local_secret_val)
        return self.local_seed.hex(), self.local_secret_val

    def verify_and_compute(self, peer_seed_hex: str, peer_secret_val: int) -> Tuple[int, int, bool, str]:
        if not self.peer_commitment_hex or not self.local_seed:
            log.error("verify_and_compute called with incomplete commitments (peer=%s, local=%s)",
                      bool(self.peer_commitment_hex), bool(self.local_seed))
            raise ValueError("Commitment exchange incomplete")

        peer_seed = bytes.fromhex(peer_seed_hex)
        computed_h = hmac.new(peer_seed, bytes([peer_secret_val]), hashlib.sha256).hexdigest()

        if computed_h.lower() != self.peer_commitment_hex.lower():
            fraud_msg = f"FRAUD: Computed {computed_h[:12]} != Expected {self.peer_commitment_hex[:12]}"
            log.critical(fraud_msg)
            return 1, 1, False, fraud_msg

        # Deterministic formula:
        # Die 1 = ((local_val + peer_val) % 6) + 1
        # Die 2 = ((local_seed[0] ^ peer_seed[0]) % 6) + 1
        die1 = ((self.local_secret_val + peer_secret_val) % 6) + 1
        die2 = ((self.local_seed[0] ^ peer_seed[0]) % 6) + 1

        proof = f"VERIFIED: Local(v={self.local_secret_val}) ^ Peer(v={peer_secret_val}) => [{die1}:{die2}]"
        log.info("Mental Dice success: %s", proof)
        self.reset()
        return die1, die2, True, proof

    def reset(self):
        self.local_seed = None
        self.local_secret_val = 0
        self.local_commitment_hex = None
        self.peer_commitment_hex = None

class CheckerStack:
    def __init__(self, count: int = 0, player: Optional[str] = None):
        self.count = count
        self.player = player

class BackgammonRules:
    @staticmethod
    def create_initial_board(mode: str = GameMode.LONG, head_player: str = Player.WHITE) -> List[CheckerStack]:
        board = [CheckerStack() for _ in range(BOARD_POINTS)]
        opp_player = Player.opponent(head_player)
        if mode in (GameMode.LONG, GameMode.MAD):
            board[0] = CheckerStack(15, head_player)
            board[12] = CheckerStack(15, opp_player)
        else: # SHORT
            board[0] = CheckerStack(2, opp_player)
            board[11] = CheckerStack(5, head_player)
            board[16] = CheckerStack(3, head_player)
            board[18] = CheckerStack(5, head_player)

            board[23] = CheckerStack(2, head_player)
            board[12] = CheckerStack(5, opp_player)
            board[7] = CheckerStack(3, opp_player)
            board[5] = CheckerStack(5, opp_player)
        log.debug("Created initial board for mode %s with head_player %s", mode, head_player)
        return board

    @staticmethod
    def relative_to_absolute(player: str, rel_pos: int, mode: str = GameMode.LONG, head_player: str = Player.WHITE) -> int:
        if mode in (GameMode.LONG, GameMode.MAD):
            if player == head_player:
                return rel_pos % BOARD_POINTS
            else:
                return (rel_pos + 12) % BOARD_POINTS
        else: # SHORT
            return rel_pos if player == head_player else (23 - rel_pos)

    @staticmethod
    def absolute_to_relative(player: str, abs_pos: int, mode: str = GameMode.LONG, head_player: str = Player.WHITE) -> int:
        if mode in (GameMode.LONG, GameMode.MAD):
            if player == head_player:
                return abs_pos % BOARD_POINTS
            else:
                return (abs_pos - 12) % BOARD_POINTS
        else: # SHORT
            return abs_pos if player == head_player else (23 - abs_pos)

    @staticmethod
    def is_all_in_home(player: str, board: List[CheckerStack], bar_count: int, mode: str = GameMode.LONG, head_player: str = Player.WHITE) -> bool:
        if bar_count > 0:
            return False
        for i in range(BOARD_POINTS):
            st = board[i]
            if st.player == player and st.count > 0:
                rel = BackgammonRules.absolute_to_relative(player, i, mode, head_player)
                if rel < 18:
                    return False
        return True

    @staticmethod
    def is_single_step_legal(
        player: str, from_rel: int, dice: int,
        board: List[CheckerStack], bar_count: int,
        cur_head_takes: int, is_first_turn: bool,
        mode: str = GameMode.LONG,
        dice_pool: Optional[List[int]] = None,
        head_player: str = Player.WHITE
    ) -> bool:
        # 1. Head takes restriction
        if mode == GameMode.MAD and from_rel == 0:
            if cur_head_takes >= 1:
                return False
        elif mode == GameMode.LONG and from_rel == 0:
            allowed_head = 2 if (is_first_turn and dice_pool and len(dice_pool) >= 2 and dice_pool[0] == dice_pool[1] and dice_pool[0] in (3, 4, 6)) else 1
            if cur_head_takes >= allowed_head:
                return False

        to_rel = dice - 1 if from_rel == -1 else from_rel + dice

        # 2. Mad Gammon: Strictly NO movement within home (checkers freeze upon entering home)
        if mode == GameMode.MAD and from_rel >= 18 and to_rel < 24:
            return False

        if to_rel < 24:
            to_abs = BackgammonRules.relative_to_absolute(player, to_rel, mode, head_player)
            target = board[to_abs]
            if mode in (GameMode.LONG, GameMode.MAD):
                # In Long Nardy & Mad Gammon, cannot step on point occupied by opponent
                if target.count > 0 and target.player != player:
                    return False
            else: # SHORT
                # In Short backgammon, cannot step on block of 2+ opponent checkers
                if target.count > 1 and target.player != player:
                    return False
            return True
        else:
            # Bear off
            if not BackgammonRules.is_all_in_home(player, board, bar_count, mode, head_player):
                return False
            if to_rel == 24:
                return True
            # In Mad Gammon, only exact match exit is allowed!
            if mode == GameMode.MAD:
                return False
            # Higher die bearing off: only if no checkers on points behind from_rel
            for r in range(18, from_rel):
                p_abs = BackgammonRules.relative_to_absolute(player, r, mode, head_player)
                if board[p_abs].player == player and board[p_abs].count > 0:
                    return False
            return True

    @staticmethod
    def find_reachable_paths_from(
        player: str,
        from_abs: int,  # -1 for bar, 0..23
        remaining_dice: List[int],
        board: List[CheckerStack],
        bar_count: int,
        cur_head_takes: int,
        is_first_turn: bool,
        mode: str = GameMode.LONG,
        head_player: str = Player.WHITE
    ) -> List[ReachablePath]:
        """
        Calculates all legally reachable destinations (including multi-step combo paths)
        from a starting point, without mutating the live board state.
        Matches Android GROK-G06 findReachablePathsFrom algorithm.
        """
        results: List[ReachablePath] = []
        current_steps: List[PathStep] = []

        sim_board = [CheckerStack(st.count, st.player) for st in board]
        sim_dice = list(remaining_dice)
        sim_head_takes = cur_head_takes
        sim_bar = bar_count

        start_rel = -1 if from_abs == -1 else BackgammonRules.absolute_to_relative(player, from_abs, mode, head_player)

        def search(curr_rel: int):
            nonlocal sim_head_takes, sim_bar

            if current_steps:
                final_to_rel = current_steps[-1].to_rel
                final_to_abs = 24 if final_to_rel >= 24 else BackgammonRules.relative_to_absolute(player, final_to_rel, mode, head_player)

                existing = next((r for r in results if r.final_to_rel == final_to_rel), None)
                if existing is None:
                    results.append(ReachablePath(final_to_abs, final_to_rel, list(current_steps)))
                elif len(current_steps) < len(existing.steps):
                    results.remove(existing)
                    results.append(ReachablePath(final_to_abs, final_to_rel, list(current_steps)))

            if not sim_dice or curr_rel >= 24:
                return

            distinct_dice = list(set(sim_dice))
            for dice in distinct_dice:
                if BackgammonRules.is_single_step_legal(
                    player, curr_rel, dice, sim_board, sim_bar,
                    sim_head_takes, is_first_turn, mode, remaining_dice, head_player
                ):
                    to_rel = dice - 1 if curr_rel == -1 else curr_rel + dice
                    from_abs_cur = -1 if curr_rel == -1 else BackgammonRules.relative_to_absolute(player, curr_rel, mode, head_player)
                    to_abs_cur = 24 if to_rel >= 24 else BackgammonRules.relative_to_absolute(player, to_rel, mode, head_player)

                    # Snapshot state for rollback
                    prev_from = CheckerStack(sim_board[from_abs_cur].count, sim_board[from_abs_cur].player) if from_abs_cur != -1 else None
                    prev_to = CheckerStack(sim_board[to_abs_cur].count, sim_board[to_abs_cur].player) if to_abs_cur < 24 else None
                    prev_bar = sim_bar
                    prev_head_takes = sim_head_takes

                    # Apply move to simulation
                    if curr_rel == -1:
                        sim_bar -= 1
                    else:
                        new_from_count = sim_board[from_abs_cur].count - 1
                        sim_board[from_abs_cur].count = new_from_count
                        if new_from_count == 0:
                            sim_board[from_abs_cur].player = None

                    if to_rel < 24:
                        if mode == GameMode.SHORT and sim_board[to_abs_cur].player and sim_board[to_abs_cur].player != player:
                            # Hit blot in Short backgammon
                            sim_board[to_abs_cur].count = 1
                            sim_board[to_abs_cur].player = player
                        else:
                            sim_board[to_abs_cur].count += 1
                            sim_board[to_abs_cur].player = player

                    if curr_rel == 0:
                        sim_head_takes += 1

                    sim_dice.remove(dice)
                    current_steps.append(PathStep(from_abs_cur, to_abs_cur, curr_rel, to_rel, dice))

                    # Recursive step
                    search(to_rel)

                    # Backtrack (Rollback)
                    current_steps.pop()
                    sim_dice.append(dice)
                    sim_head_takes = prev_head_takes
                    sim_bar = prev_bar

                    if from_abs_cur != -1 and prev_from is not None:
                        sim_board[from_abs_cur].count = prev_from.count
                        sim_board[from_abs_cur].player = prev_from.player
                    if to_abs_cur < 24 and prev_to is not None:
                        sim_board[to_abs_cur].count = prev_to.count
                        sim_board[to_abs_cur].player = prev_to.player

        search(start_rel)
        return results

    @staticmethod
    def compute_board_hash(board: List[CheckerStack], bar_white: int = 0, bar_black: int = 0) -> str:
        s = []
        for i, st in enumerate(board):
            p = "W" if st.player == Player.WHITE else ("B" if st.player == Player.BLACK else "N")
            s.append(f"{i}:{p}{st.count};")
        s.append(f"BAR:W{bar_white}B{bar_black}")
        h = hashlib.sha256("".join(s).encode()).hexdigest()[:16]
        return h
