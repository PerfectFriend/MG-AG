pub mod crypto;
pub mod engine;
pub mod net;
pub mod vault;

// Qualified re-exports to avoid name collisions between engine types and UniFFI wrapper types
pub use crypto::double_ratchet::{
    pad_to_16kb, unpad_from_16kb, DoubleRatchetSession, MessageHeader,
    RatchetError, PADDED_BLOCK_SIZE, MAX_SKIP,
};
pub use crypto::hybrid_pq::HybridKeyExchange;
pub use crypto::mental_dice::{MentalDiceEngine, DiceOutcome, MentalDiceError};
pub use engine::rules::{
    BoardState, CheckerPoint, GameMode, Move, Player,
    BOARD_POINTS, CHECKERS_PER_PLAYER,
};
pub use net::simplex::{SimpleXWireCodec, SmpCommand, SmpMessageFrame};
pub use net::tun2socks::{TunPacketRouter, TunRouterConfig};
pub use vault::erasure::ShardedVault;
pub use vault::mnemonic::{MnemonicManager, DerivedSeed, MnemonicError};

/// Version API
pub fn madgammon_core_version() -> String {
    "0.2.0-sovereign-rust".to_string()
}

pub fn generate_bip39_mnemonic_12() -> String {
    vault::mnemonic::MnemonicManager::generate_12_words()
}

pub fn generate_bip39_mnemonic_24() -> String {
    vault::mnemonic::MnemonicManager::generate_24_words()
}

pub fn validate_bip39_mnemonic(phrase: String) -> bool {
    vault::mnemonic::MnemonicManager::validate_phrase(&phrase)
}

pub fn generate_sas_emojis(shared_secret: Vec<u8>) -> Vec<String> {
    if shared_secret.len() < 32 {
        return vec!["🛡️".to_string(), "🔑".to_string(), "🎲".to_string(), "⚡".to_string()];
    }
    let mut secret_arr = [0u8; 32];
    secret_arr.copy_from_slice(&shared_secret[0..32]);
    crypto::hybrid_pq::HybridKeyExchange::generate_sas_emojis(&secret_arr).to_vec()
}

// ─── UniFFI Wrapper Types ───
#[derive(Debug, Clone, Copy, PartialEq, Eq)]
pub enum CorePlayer {
    White,
    Black,
}

#[derive(Debug, Clone, Copy, PartialEq, Eq)]
pub enum CoreGameMode {
    Long,
    Short,
    Mad,
}

#[derive(Debug, Clone, PartialEq, Eq)]
pub struct CoreMove {
    pub from_rel: i8,
    pub to_rel: i8,
    pub die_used: u8,
}

#[derive(Debug, Clone, PartialEq, Eq)]
pub struct CoreDiceOutcome {
    pub die1: u8,
    pub die2: u8,
    pub is_valid: bool,
    pub audit_proof: String,
}

// ─── NativeMentalDiceEngine (UniFFI interface) ───
pub struct NativeMentalDiceEngine {
    inner: std::sync::Mutex<crypto::mental_dice::MentalDiceEngine>,
}

impl NativeMentalDiceEngine {
    pub fn new() -> Self {
        Self {
            inner: std::sync::Mutex::new(crypto::mental_dice::MentalDiceEngine::new()),
        }
    }

    pub fn start_local_roll(&self) -> Vec<u8> {
        let mut engine = self.inner.lock().unwrap();
        engine.start_local_roll().map(|c| c.to_vec()).unwrap_or_default()
    }

    pub fn set_peer_commitment(&self, commitment: Vec<u8>) {
        if commitment.len() == 32 {
            let mut arr = [0u8; 32];
            arr.copy_from_slice(&commitment);
            let mut engine = self.inner.lock().unwrap();
            engine.set_peer_commitment(arr);
        }
    }

    pub fn reveal_local_seed(&self) -> Vec<u8> {
        let engine = self.inner.lock().unwrap();
        engine.reveal_local_secret().map(|(s, _)| s.to_vec()).unwrap_or_default()
    }

    pub fn reveal_local_value(&self) -> u8 {
        let engine = self.inner.lock().unwrap();
        engine.reveal_local_secret().map(|(_, v)| v).unwrap_or_default()
    }

    pub fn verify_and_compute(&self, peer_seed: Vec<u8>, peer_secret_value: u8) -> CoreDiceOutcome {
        if peer_seed.len() != 32 {
            return CoreDiceOutcome {
                die1: 1,
                die2: 1,
                is_valid: false,
                audit_proof: "INVALID_SEED_LENGTH".to_string(),
            };
        }
        let mut arr = [0u8; 32];
        arr.copy_from_slice(&peer_seed);
        let mut engine = self.inner.lock().unwrap();
        match engine.verify_and_compute(&arr, peer_secret_value) {
            Ok(outcome) => CoreDiceOutcome {
                die1: outcome.die1,
                die2: outcome.die2,
                is_valid: outcome.is_valid,
                audit_proof: outcome.audit_proof,
            },
            Err(e) => CoreDiceOutcome {
                die1: 1,
                die2: 1,
                is_valid: false,
                audit_proof: format!("VERIFICATION_FAILED: {:?}", e),
            },
        }
    }

    pub fn reset(&self) {
        let mut engine = self.inner.lock().unwrap();
        engine.reset();
    }
}

impl Default for NativeMentalDiceEngine {
    fn default() -> Self {
        Self::new()
    }
}

// ─── NativeShardedVault (UniFFI interface) ───
pub struct NativeShardedVault {
    inner: vault::erasure::ShardedVault,
}

impl NativeShardedVault {
    pub fn new(data_shards: u32, parity_shards: u32) -> Self {
        Self {
            inner: vault::erasure::ShardedVault::new(data_shards as usize, parity_shards as usize)
                .expect("Valid shard parameters"),
        }
    }

    pub fn encode(&self, payload: Vec<u8>) -> Vec<Vec<u8>> {
        self.inner.encode(&payload).unwrap_or_default()
    }

    pub fn reconstruct(&self, mut shards: Vec<Option<Vec<u8>>>, original_length: u32) -> Vec<u8> {
        self.inner
            .reconstruct(&mut shards, original_length as usize)
            .unwrap_or_default()
    }
}

// ─── NativeTunPacketRouter (UniFFI interface) ───
pub struct NativeTunPacketRouter {
    inner: std::sync::Mutex<net::tun2socks::TunPacketRouter>,
}

impl NativeTunPacketRouter {
    pub fn new() -> Self {
        Self {
            inner: std::sync::Mutex::new(net::tun2socks::TunPacketRouter::new()),
        }
    }

    pub fn is_active(&self) -> bool {
        let router = self.inner.lock().unwrap();
        router.is_active()
    }

    pub fn start(&self, tun_fd: i32, socks5_host: String, socks5_port: u16, mtu: u16) {
        let mut router = self.inner.lock().unwrap();
        let _ = router.start(net::tun2socks::TunRouterConfig {
            tun_fd,
            socks5_host,
            socks5_port,
            mtu,
        });
    }

    pub fn stop(&self) {
        let mut router = self.inner.lock().unwrap();
        router.stop();
    }
}

impl Default for NativeTunPacketRouter {
    fn default() -> Self {
        Self::new()
    }
}

// ─── NativeBoardEngine (UniFFI interface) ───
pub struct NativeBoardEngine {
    state: std::sync::Mutex<engine::rules::BoardState>,
}

impl NativeBoardEngine {
    pub fn new_long() -> Self {
        Self { state: std::sync::Mutex::new(BoardState::initial_long()) }
    }

    pub fn new_short() -> Self {
        Self { state: std::sync::Mutex::new(BoardState::initial_short()) }
    }

    pub fn new_mad() -> Self {
        Self { state: std::sync::Mutex::new(BoardState::initial_mad()) }
    }

    pub fn get_state_json(&self) -> String {
        let state = self.state.lock().unwrap();
        state.to_json()
    }

    pub fn load_state_json(&self, json: String) -> bool {
        match BoardState::from_json(&json) {
            Ok(new_state) => {
                let mut state = self.state.lock().unwrap();
                *state = new_state;
                true
            }
            Err(_) => false,
        }
    }

    pub fn get_legal_moves(&self, dice: Vec<u8>, is_kush: bool) -> Vec<CoreMove> {
        let state = self.state.lock().unwrap();
        state.get_all_legal_first_moves(&dice, is_kush)
            .into_iter()
            .map(|m| CoreMove { from_rel: m.from_rel, to_rel: m.to_rel, die_used: m.die_used })
            .collect()
    }

    pub fn apply_move(&self, from_rel: i8, to_rel: i8, die_used: u8) -> bool {
        let mut state = self.state.lock().unwrap();
        let mv = Move { from_rel, to_rel, die_used };
        state.apply_move(&mv).is_ok()
    }

    pub fn end_turn(&self) {
        let mut state = self.state.lock().unwrap();
        state.end_turn();
    }

    pub fn has_won(&self, white: bool) -> bool {
        let state = self.state.lock().unwrap();
        let player = if white { Player::White } else { Player::Black };
        state.has_won(player)
    }

    pub fn pip_count(&self, white: bool) -> u32 {
        let state = self.state.lock().unwrap();
        let player = if white { Player::White } else { Player::Black };
        state.pip_count(player)
    }
}

// Scaffolding generation for UniFFI
uniffi::include_scaffolding!("madgammon_core");
