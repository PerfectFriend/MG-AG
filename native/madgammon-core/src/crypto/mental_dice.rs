use hmac::{Hmac, Mac};
use rand::{rngs::OsRng, RngCore};
use sha2::Sha256;
use subtle::ConstantTimeEq;
use thiserror::Error;
use zeroize::{Zeroize, ZeroizeOnDrop};

type HmacSha256 = Hmac<Sha256>;

#[derive(Error, Debug, PartialEq, Eq)]
pub enum MentalDiceError {
    #[error("Invalid secret roll value: expected 0..5, got {0}")]
    InvalidSecretValue(u8),
    #[error("Commitment mismatch: reveal does not match committed hash")]
    CommitmentMismatch,
    #[error("Both commitments must be set before reveal phase")]
    CommitmentsNotReady,
    #[error("Cryptographic MAC initialization error")]
    MacError,
}

/// Cryptographically zeroized secret container for a player's dice roll commitment.
#[derive(Clone, Zeroize, ZeroizeOnDrop)]
pub struct LocalDiceSecret {
    pub seed: [u8; 32],
    pub secret_value: u8,
}

impl LocalDiceSecret {
    /// Generates a cryptographically secure random seed and random secret value (0..5).
    pub fn generate() -> Self {
        let mut seed = [0u8; 32];
        OsRng.fill_bytes(&mut seed);
        let secret_value = (OsRng.next_u32() % 6) as u8;
        Self { seed, secret_value }
    }

    /// Computes HMAC-SHA256(seed, secret_value) commitment hash.
    pub fn compute_commitment(&self) -> Result<[u8; 32], MentalDiceError> {
        let mut mac = HmacSha256::new_from_slice(&self.seed)
            .map_err(|_| MentalDiceError::MacError)?;
        mac.update(&[self.secret_value]);
        let result = mac.finalize().into_bytes();
        let mut hash = [0u8; 32];
        hash.copy_from_slice(&result);
        Ok(hash)
    }
}

/// Verification proof and resulting dice roll outcome.
#[derive(Debug, Clone, PartialEq, Eq)]
pub struct DiceOutcome {
    pub die1: u8, // 1..6
    pub die2: u8, // 1..6
    pub is_valid: bool,
    pub audit_proof: String,
}

/// Mental Dice Protocol Engine using HMAC-SHA256 Commit-Reveal Scheme.
/// Ensures provably fair dice rolls in peer-to-peer multiplayer without a trusted referee.
pub struct MentalDiceEngine {
    local_secret: Option<LocalDiceSecret>,
    local_commitment: Option<[u8; 32]>,
    peer_commitment: Option<[u8; 32]>,
}

impl MentalDiceEngine {
    pub fn new() -> Self {
        Self {
            local_secret: None,
            local_commitment: None,
            peer_commitment: None,
        }
    }

    /// Step 1: Initialize local roll, generates seed & value, returns commitment hash.
    pub fn start_local_roll(&mut self) -> Result<[u8; 32], MentalDiceError> {
        let secret = LocalDiceSecret::generate();
        let commitment = secret.compute_commitment()?;
        self.local_secret = Some(secret);
        self.local_commitment = Some(commitment);
        Ok(commitment)
    }

    /// Step 2: Store received peer commitment.
    pub fn set_peer_commitment(&mut self, commitment: [u8; 32]) {
        self.peer_commitment = Some(commitment);
    }

    /// Step 3: Reveal local secret (seed and secret value) once peer commitment is received.
    pub fn reveal_local_secret(&self) -> Result<([u8; 32], u8), MentalDiceError> {
        let secret = self.local_secret.as_ref().ok_or(MentalDiceError::CommitmentsNotReady)?;
        Ok((secret.seed, secret.secret_value))
    }

    /// Step 4: Verify peer reveal against stored peer commitment, combine secrets, and compute final dice.
    pub fn verify_and_compute(
        &mut self,
        peer_seed: &[u8; 32],
        peer_secret_value: u8,
    ) -> Result<DiceOutcome, MentalDiceError> {
        if peer_secret_value > 5 {
            return Err(MentalDiceError::InvalidSecretValue(peer_secret_value));
        }

        let peer_comm = self.peer_commitment.ok_or(MentalDiceError::CommitmentsNotReady)?;
        let local_sec = self.local_secret.as_ref().ok_or(MentalDiceError::CommitmentsNotReady)?;

        // Verify peer commitment HMAC-SHA256(peer_seed, peer_secret_value) == peer_comm
        let mut mac = HmacSha256::new_from_slice(peer_seed)
            .map_err(|_| MentalDiceError::MacError)?;
        mac.update(&[peer_secret_value]);
        let computed_peer_comm = mac.finalize().into_bytes();

        if computed_peer_comm.ct_eq(&peer_comm).unwrap_u8() != 1 {
            return Err(MentalDiceError::CommitmentMismatch);
        }

        // Deterministic dice derivation formula:
        // Die 1 = ((local_value + peer_value) % 6) + 1
        // Die 2 = ((local_seed[0] ^ peer_seed[0]) % 6) + 1
        let die1 = ((local_sec.secret_value as u16 + peer_secret_value as u16) % 6 + 1) as u8;
        let die2 = ((local_sec.seed[0] ^ peer_seed[0]) % 6 + 1) as u8;

        let proof = format!(
            "VERIFIED_HMAC_SHA256: Local(val={}, seed_prefix={:02x}{:02x}) ^ Peer(val={}, seed_prefix={:02x}{:02x}) => [{}:{}]",
            local_sec.secret_value,
            local_sec.seed[0],
            local_sec.seed[1],
            peer_secret_value,
            peer_seed[0],
            peer_seed[1],
            die1,
            die2
        );

        // Reset state for next roll
        self.reset();

        Ok(DiceOutcome {
            die1,
            die2,
            is_valid: true,
            audit_proof: proof,
        })
    }

    pub fn reset(&mut self) {
        self.local_secret = None;
        self.local_commitment = None;
        self.peer_commitment = None;
    }
}

impl Default for MentalDiceEngine {
    fn default() -> Self {
        Self::new()
    }
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn test_provably_fair_dice_roll() {
        let mut alice = MentalDiceEngine::new();
        let mut bob = MentalDiceEngine::new();

        // 1. Both start rolls and exchange commitments
        let alice_comm = alice.start_local_roll().unwrap();
        let bob_comm = bob.start_local_roll().unwrap();

        alice.set_peer_commitment(bob_comm);
        bob.set_peer_commitment(alice_comm);

        // 2. Both reveal secrets
        let (alice_seed, alice_val) = alice.reveal_local_secret().unwrap();
        let (bob_seed, bob_val) = bob.reveal_local_secret().unwrap();

        // 3. Both verify and compute dice
        let alice_outcome = alice.verify_and_compute(&bob_seed, bob_val).unwrap();
        let bob_outcome = bob.verify_and_compute(&alice_seed, alice_val).unwrap();

        assert_eq!(alice_outcome.die1, bob_outcome.die1);
        assert_eq!(alice_outcome.die2, bob_outcome.die2);
        assert!(alice_outcome.die1 >= 1 && alice_outcome.die1 <= 6);
        assert!(alice_outcome.die2 >= 1 && alice_outcome.die2 <= 6);
        assert!(alice_outcome.is_valid);
    }

    #[test]
    fn test_fraud_commitment_mismatch_detected() {
        let mut alice = MentalDiceEngine::new();
        let mut bob = MentalDiceEngine::new();

        let alice_comm = alice.start_local_roll().unwrap();
        let bob_comm = bob.start_local_roll().unwrap();

        alice.set_peer_commitment(bob_comm);
        bob.set_peer_commitment(alice_comm);

        let (_bob_seed, bob_val) = bob.reveal_local_secret().unwrap();
        let fake_bob_seed = [0xAAu8; 32]; // Tampered seed!

        let result = alice.verify_and_compute(&fake_bob_seed, bob_val);
        assert_eq!(result, Err(MentalDiceError::CommitmentMismatch));
    }
}
