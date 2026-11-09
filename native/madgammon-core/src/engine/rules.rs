use serde::{Deserialize, Serialize};

pub const BOARD_POINTS: usize = 24;
pub const CHECKERS_PER_PLAYER: u8 = 15;

#[derive(Debug, Clone, Copy, PartialEq, Eq, Hash, Serialize, Deserialize)]
pub enum Player {
    White,
    Black,
}

impl Player {
    pub fn opponent(self) -> Self {
        match self {
            Player::White => Player::Black,
            Player::Black => Player::White,
        }
    }
}

#[derive(Debug, Clone, Copy, PartialEq, Eq, Serialize, Deserialize)]
pub enum GameMode {
    Long,
    Short,
    Mad,
}

#[derive(Debug, Clone, Copy, PartialEq, Eq, Serialize, Deserialize)]
pub struct CheckerPoint {
    pub player: Option<Player>,
    pub count: u8,
}

impl Default for CheckerPoint {
    fn default() -> Self {
        Self {
            player: None,
            count: 0,
        }
    }
}

#[derive(Debug, Clone, PartialEq, Eq, Serialize, Deserialize)]
pub struct Move {
    pub from_rel: i8, // -1 for bar, 0..23 for board points
    pub to_rel: i8,   // 0..23 for board, 24 for borne off
    pub die_used: u8,
}

#[derive(Debug, Clone, PartialEq, Eq, Serialize, Deserialize)]
pub struct BoardState {
    pub points: [CheckerPoint; BOARD_POINTS],
    pub white_bar: u8,
    pub black_bar: u8,
    pub white_borne_off: u8,
    pub black_borne_off: u8,
    pub active_player: Player,
    pub mode: GameMode,
    pub is_first_turn_white: bool,
    pub is_first_turn_black: bool,
    pub head_takes_this_turn: u8,
}

impl BoardState {
    /// Long Backgammon: 15 checkers on head (White at 0, Black at 12).
    pub fn initial_long() -> Self {
        let mut points = [CheckerPoint::default(); BOARD_POINTS];
        points[0] = CheckerPoint {
            player: Some(Player::White),
            count: 15,
        };
        points[12] = CheckerPoint {
            player: Some(Player::Black),
            count: 15,
        };

        Self {
            points,
            white_bar: 0,
            black_bar: 0,
            white_borne_off: 0,
            black_borne_off: 0,
            active_player: Player::White,
            mode: GameMode::Long,
            is_first_turn_white: true,
            is_first_turn_black: true,
            head_takes_this_turn: 0,
        }
    }

    /// Short Backgammon (standard international): 5-3-5-2 setup.
    pub fn initial_short() -> Self {
        let mut points = [CheckerPoint::default(); BOARD_POINTS];
        // White setup: 2@23, 5@12, 3@7, 5@5
        points[23] = CheckerPoint { player: Some(Player::White), count: 2 };
        points[12] = CheckerPoint { player: Some(Player::White), count: 5 };
        points[7]  = CheckerPoint { player: Some(Player::White), count: 3 };
        points[5]  = CheckerPoint { player: Some(Player::White), count: 5 };
        // Black setup (mirrored): 2@0, 5@11, 3@16, 5@18
        points[0]  = CheckerPoint { player: Some(Player::Black), count: 2 };
        points[11] = CheckerPoint { player: Some(Player::Black), count: 5 };
        points[16] = CheckerPoint { player: Some(Player::Black), count: 3 };
        points[18] = CheckerPoint { player: Some(Player::Black), count: 5 };

        Self {
            points,
            white_bar: 0,
            black_bar: 0,
            white_borne_off: 0,
            black_borne_off: 0,
            active_player: Player::White,
            mode: GameMode::Short,
            is_first_turn_white: true,
            is_first_turn_black: true,
            head_takes_this_turn: 0,
        }
    }

    /// Mad Backgammon: 15 checkers each on the same head as Long, but with special capture rules.
    pub fn initial_mad() -> Self {
        let mut board = Self::initial_long();
        board.mode = GameMode::Mad;
        board
    }

    /// Converts relative point (0..23) for player into absolute board index (0..23).
    pub fn relative_to_absolute(&self, player: Player, rel: usize) -> usize {
        match self.mode {
            GameMode::Long | GameMode::Mad => match player {
                Player::White => rel % BOARD_POINTS,
                Player::Black => (rel + 12) % BOARD_POINTS,
            },
            GameMode::Short => match player {
                Player::White => rel,
                Player::Black => 23 - rel,
            },
        }
    }

    /// Checks if all checkers of a player are in the home quadrant (relative points 18..23) or borne off.
    pub fn can_bear_off(&self, player: Player) -> bool {
        let bar_count = match player {
            Player::White => self.white_bar,
            Player::Black => self.black_bar,
        };
        if bar_count > 0 {
            return false;
        }

        for r in 0..18 {
            let abs = self.relative_to_absolute(player, r);
            if self.points[abs].player == Some(player) && self.points[abs].count > 0 {
                return false;
            }
        }
        true
    }

    /// Checks if a player has won (all 15 borne off).
    pub fn has_won(&self, player: Player) -> bool {
        match player {
            Player::White => self.white_borne_off >= CHECKERS_PER_PLAYER,
            Player::Black => self.black_borne_off >= CHECKERS_PER_PLAYER,
        }
    }

    /// Returns the number of checkers on bar for a player.
    pub fn bar_count(&self, player: Player) -> u8 {
        match player {
            Player::White => self.white_bar,
            Player::Black => self.black_bar,
        }
    }

    /// Returns the number of borne off checkers for a player.
    pub fn borne_off_count(&self, player: Player) -> u8 {
        match player {
            Player::White => self.white_borne_off,
            Player::Black => self.black_borne_off,
        }
    }

    /// Returns remaining pip count for a player.
    pub fn pip_count(&self, player: Player) -> u32 {
        let bar = self.bar_count(player) as u32;
        let mut pips = bar * 25; // bar counts as distance 25 from home
        for r in 0..24 {
            let abs = self.relative_to_absolute(player, r);
            if self.points[abs].player == Some(player) {
                pips += self.points[abs].count as u32 * (24 - r as u32);
            }
        }
        pips
    }

    /// Single step validation for a move.
    pub fn is_single_step_legal(
        &self,
        player: Player,
        from_rel: i8,
        die: u8,
        last_rolled_kush: bool,
    ) -> bool {
        if die == 0 {
            return false;
        }

        let bar_count = match player {
            Player::White => self.white_bar,
            Player::Black => self.black_bar,
        };

        // In Short backgammon, if player has checkers on bar, must move from bar (-1)
        if self.mode == GameMode::Short && bar_count > 0 && from_rel != -1 {
            return false;
        }

        // In Long/Mad, no bar exists (checkers never captured to bar)
        if (self.mode == GameMode::Long || self.mode == GameMode::Mad) && from_rel == -1 {
            return false;
        }

        // Check source has our checker
        if from_rel >= 0 {
            let abs_from = self.relative_to_absolute(player, from_rel as usize);
            if self.points[abs_from].player != Some(player) || self.points[abs_from].count == 0 {
                return false;
            }
        } else if from_rel == -1 {
            if bar_count == 0 {
                return false;
            }
        } else {
            return false;
        }

        let to_rel = if from_rel == -1 {
            die as i8 - 1
        } else {
            from_rel + die as i8
        };

        // Bearing off check
        if to_rel >= 24 {
            if !self.can_bear_off(player) {
                return false;
            }
            if to_rel == 24 {
                return true;
            }
            // If overshoot (> 24), allowed only if no checkers exist on higher points
            for r in (from_rel as usize + 1)..24 {
                let abs = self.relative_to_absolute(player, r);
                if self.points[abs].player == Some(player) && self.points[abs].count > 0 {
                    return false;
                }
            }
            return true;
        }

        let abs_to = self.relative_to_absolute(player, to_rel as usize);
        let dest = &self.points[abs_to];

        // Opponent checker blocking check
        if let Some(occupant) = dest.player {
            if occupant != player {
                match self.mode {
                    GameMode::Short => {
                        if dest.count >= 2 {
                            return false; // Point made by opponent
                        }
                    }
                    GameMode::Long | GameMode::Mad => {
                        return false; // In Long/Mad, 1 checker blocks completely
                    }
                }
            }
        }

        // GROK-G01: Head takes rule (Long/Mad):
        // Only 1 checker from head per turn, unless first turn doubles 3:3, 4:4, 6:6
        if self.mode != GameMode::Short && from_rel == 0 {
            let is_first = match player {
                Player::White => self.is_first_turn_white,
                Player::Black => self.is_first_turn_black,
            };
            let is_first_turn_kush346 = is_first
                && self.mode == GameMode::Long
                && last_rolled_kush
                && (die == 3 || die == 4 || die == 6);

            let max_head_takes = if is_first_turn_kush346 { 2 } else { 1 };
            if self.head_takes_this_turn >= max_head_takes {
                return false;
            }
        }

        true
    }

    /// Apply a validated move to the board, mutating state.
    /// Returns Ok(captured) if a hit occurred in Short backgammon, otherwise Ok(false).
    pub fn apply_move(&mut self, mv: &Move) -> Result<bool, &'static str> {
        let player = self.active_player;
        let from = mv.from_rel;
        let to = mv.to_rel;
        let mut captured = false;

        // Remove checker from source
        if from == -1 {
            // Moving from bar
            match player {
                Player::White => {
                    if self.white_bar == 0 { return Err("No checker on White bar"); }
                    self.white_bar -= 1;
                }
                Player::Black => {
                    if self.black_bar == 0 { return Err("No checker on Black bar"); }
                    self.black_bar -= 1;
                }
            }
        } else {
            let abs_from = self.relative_to_absolute(player, from as usize);
            if self.points[abs_from].count == 0 {
                return Err("No checker at source point");
            }
            self.points[abs_from].count -= 1;
            if self.points[abs_from].count == 0 {
                self.points[abs_from].player = None;
            }
            // Track head takes
            if from == 0 && self.mode != GameMode::Short {
                self.head_takes_this_turn += 1;
            }
        }

        // Place checker at destination
        if to >= 24 {
            // Bearing off
            match player {
                Player::White => self.white_borne_off += 1,
                Player::Black => self.black_borne_off += 1,
            }
        } else {
            let abs_to = self.relative_to_absolute(player, to as usize);
            let dest = &mut self.points[abs_to];

            // Check for hit (Short backgammon only)
            if self.mode == GameMode::Short {
                if dest.player == Some(player.opponent()) && dest.count == 1 {
                    // Hit! Send opponent's blot to bar
                    captured = true;
                    match player.opponent() {
                        Player::White => self.white_bar += 1,
                        Player::Black => self.black_bar += 1,
                    }
                    dest.count = 0;
                    dest.player = None;
                }
            }

            dest.count += 1;
            dest.player = Some(player);
        }

        Ok(captured)
    }

    /// End the current turn: switch active player, reset head_takes, update first_turn flags.
    pub fn end_turn(&mut self) {
        match self.active_player {
            Player::White => self.is_first_turn_white = false,
            Player::Black => self.is_first_turn_black = false,
        }
        self.head_takes_this_turn = 0;
        self.active_player = self.active_player.opponent();
    }

    /// Maximal playout generator (GROK-G05):
    /// Returns all legal starting moves that lead to using the maximum possible number of dice.
    pub fn get_all_legal_first_moves(&self, dice: &[u8], is_kush: bool) -> Vec<Move> {
        if dice.is_empty() {
            return Vec::new();
        }

        let player = self.active_player;
        let mut valid_first_moves = Vec::new();
        let mut distinct_dice = dice.to_vec();
        distinct_dice.sort_unstable();
        distinct_dice.dedup();

        let bar_count = match player {
            Player::White => self.white_bar,
            Player::Black => self.black_bar,
        };

        let candidate_froms: Vec<i8> = if self.mode == GameMode::Short && bar_count > 0 {
            vec![-1]
        } else {
            (0..24)
                .filter(|&r| {
                    let abs = self.relative_to_absolute(player, r);
                    self.points[abs].player == Some(player) && self.points[abs].count > 0
                })
                .map(|r| r as i8)
                .collect()
        };

        for &d in &distinct_dice {
            for &from in &candidate_froms {
                if self.is_single_step_legal(player, from, d, is_kush) {
                    let to = if from == -1 { d as i8 - 1 } else { from + d as i8 };
                    valid_first_moves.push(Move {
                        from_rel: from,
                        to_rel: to.min(24),
                        die_used: d,
                    });
                }
            }
        }

        valid_first_moves
    }

    /// Serialize board state to JSON string.
    pub fn to_json(&self) -> String {
        serde_json::to_string(self).unwrap_or_default()
    }

    /// Deserialize board state from JSON string.
    pub fn from_json(json: &str) -> Result<Self, serde_json::Error> {
        serde_json::from_str(json)
    }
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn test_head_takes_limit() {
        let mut board = BoardState::initial_long();
        // First move from head with die 4 (not kush)
        assert!(board.is_single_step_legal(Player::White, 0, 4, false));

        // After 1 take from head
        board.head_takes_this_turn = 1;
        assert!(!board.is_single_step_legal(Player::White, 0, 4, false));

        // But with kush 4:4 on first turn -> 2 takes allowed!
        board.is_first_turn_white = true;
        assert!(board.is_single_step_legal(Player::White, 0, 4, true));

        // After 2 takes -> blocked
        board.head_takes_this_turn = 2;
        assert!(!board.is_single_step_legal(Player::White, 0, 4, true));
    }

    #[test]
    fn test_bearing_off_prerequisites() {
        let mut board = BoardState::initial_long();
        // Cannot bear off initially because checkers are at head
        assert!(!board.can_bear_off(Player::White));

        // Move all checkers to home quadrant (points 18..23)
        board.points[0] = CheckerPoint::default();
        board.points[18] = CheckerPoint {
            player: Some(Player::White),
            count: 15,
        };
        assert!(board.can_bear_off(Player::White));
    }

    #[test]
    fn test_short_backgammon_bar_priority() {
        let mut board = BoardState::initial_short();
        // Place a White checker on the bar
        board.white_bar = 1;

        // Player must enter from bar (-1), normal point moves must be blocked
        assert!(board.is_single_step_legal(Player::White, -1, 3, false));
        assert!(!board.is_single_step_legal(Player::White, 0, 3, false));
    }

    #[test]
    fn test_maximal_first_moves_generation() {
        let board = BoardState::initial_long();
        let moves = board.get_all_legal_first_moves(&[3, 5], false);
        // Only 1 point can be moved initially (the head at point 0)
        assert_eq!(moves.len(), 2);
        assert!(moves.iter().any(|m| m.from_rel == 0 && m.die_used == 3));
        assert!(moves.iter().any(|m| m.from_rel == 0 && m.die_used == 5));
    }

    #[test]
    fn test_apply_move_basic() {
        let mut board = BoardState::initial_long();
        let mv = Move { from_rel: 0, to_rel: 3, die_used: 3 };
        let hit = board.apply_move(&mv).unwrap();
        assert!(!hit);
        assert_eq!(board.points[0].count, 14);
        assert_eq!(board.points[3].count, 1);
        assert_eq!(board.points[3].player, Some(Player::White));
    }

    #[test]
    fn test_has_won() {
        let mut board = BoardState::initial_long();
        assert!(!board.has_won(Player::White));
        board.white_borne_off = 15;
        assert!(board.has_won(Player::White));
    }

    #[test]
    fn test_short_hit_sends_to_bar() {
        let mut board = BoardState::initial_short();
        // Place a White blot at point 10
        board.points[10] = CheckerPoint { player: Some(Player::White), count: 1 };
        // Place a Black checker to hit from point 7
        board.points[7] = CheckerPoint { player: Some(Player::Black), count: 1 };
        board.active_player = Player::Black;

        // Black moves from abs 7 (Black rel = 23-7=16) to abs 10 (Black rel = 23-10=13)
        // Actually let's set up for Black directly
        board.active_player = Player::White;
        // White at point 8, Black blot at point 11
        board.points[8] = CheckerPoint { player: Some(Player::White), count: 2 };
        board.points[11] = CheckerPoint { player: Some(Player::Black), count: 1 };
        let mv = Move { from_rel: 8, to_rel: 11, die_used: 3 };
        let hit = board.apply_move(&mv).unwrap();
        assert!(hit);
        assert_eq!(board.black_bar, 1);
        assert_eq!(board.points[11].player, Some(Player::White));
        assert_eq!(board.points[11].count, 1);
    }

    #[test]
    fn test_bearing_off_move() {
        let mut board = BoardState::initial_long();
        // Clear board, put all White in home quadrant
        for p in board.points.iter_mut() { *p = CheckerPoint::default(); }
        board.points[23] = CheckerPoint { player: Some(Player::White), count: 2 };
        board.points[22] = CheckerPoint { player: Some(Player::White), count: 13 };

        assert!(board.can_bear_off(Player::White));
        let mv = Move { from_rel: 23, to_rel: 24, die_used: 1 };
        let hit = board.apply_move(&mv).unwrap();
        assert!(!hit);
        assert_eq!(board.white_borne_off, 1);
        assert_eq!(board.points[23].count, 1);
    }

    #[test]
    fn test_pip_count() {
        let board = BoardState::initial_long();
        // White has 15 checkers at rel 0 -> distance = 24 each
        assert_eq!(board.pip_count(Player::White), 15 * 24);
    }

    #[test]
    fn test_end_turn() {
        let mut board = BoardState::initial_long();
        assert_eq!(board.active_player, Player::White);
        board.end_turn();
        assert_eq!(board.active_player, Player::Black);
        assert!(!board.is_first_turn_white);
    }

    #[test]
    fn test_json_roundtrip() {
        let board = BoardState::initial_long();
        let json = board.to_json();
        let restored = BoardState::from_json(&json).unwrap();
        assert_eq!(board, restored);
    }
}
