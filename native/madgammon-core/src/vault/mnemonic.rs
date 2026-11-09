use bip39::{Language, Mnemonic};
use rand::{rngs::OsRng, RngCore};
use thiserror::Error;
use zeroize::{Zeroize, ZeroizeOnDrop};

#[derive(Error, Debug, PartialEq, Eq)]
pub enum MnemonicError {
    #[error("Invalid mnemonic phrase: {0}")]
    InvalidPhrase(String),
    #[error("Entropy generation failed")]
    EntropyFailed,
}

#[derive(Zeroize, ZeroizeOnDrop)]
pub struct DerivedSeed(pub [u8; 64]);

/// Sovereign BIP-39 mnemonic generator and seed derivation engine.
/// Compatible with bip39 crate v2.x API.
pub struct MnemonicManager;

impl MnemonicManager {
    /// Generates a new cryptographically secure 12-word BIP-39 mnemonic phrase.
    pub fn generate_12_words() -> String {
        let mut entropy = [0u8; 16]; // 128 bits -> 12 words
        OsRng.fill_bytes(&mut entropy);
        let mnemonic = Mnemonic::from_entropy_in(Language::English, &entropy)
            .expect("Valid 128-bit entropy");
        mnemonic.to_string()
    }

    /// Generates a new cryptographically secure 24-word BIP-39 mnemonic phrase.
    pub fn generate_24_words() -> String {
        let mut entropy = [0u8; 32]; // 256 bits -> 24 words
        OsRng.fill_bytes(&mut entropy);
        let mnemonic = Mnemonic::from_entropy_in(Language::English, &entropy)
            .expect("Valid 256-bit entropy");
        mnemonic.to_string()
    }

    /// Validates a BIP-39 mnemonic phrase against wordlist and checksum.
    pub fn validate_phrase(phrase: &str) -> bool {
        Mnemonic::parse_in_normalized(Language::English, phrase).is_ok()
    }

    /// Derives 64-byte binary seed from mnemonic phrase with optional passphrase (PBKDF2-HMAC-SHA512).
    pub fn phrase_to_seed(phrase: &str, passphrase: &str) -> Result<DerivedSeed, MnemonicError> {
        let mnemonic = Mnemonic::parse_in_normalized(Language::English, phrase)
            .map_err(|e| MnemonicError::InvalidPhrase(e.to_string()))?;
        let seed_bytes = mnemonic.to_seed(passphrase);
        let mut result = [0u8; 64];
        result.copy_from_slice(&seed_bytes);
        Ok(DerivedSeed(result))
    }
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn test_generate_and_validate_12_words() {
        let phrase = MnemonicManager::generate_12_words();
        let words: Vec<&str> = phrase.split_whitespace().collect();
        assert_eq!(words.len(), 12);
        assert!(MnemonicManager::validate_phrase(&phrase));
    }

    #[test]
    fn test_invalid_mnemonic_detected() {
        let invalid = "abandon abandon abandon abandon abandon abandon abandon abandon abandon abandon abandon abandon";
        // 12 'abandon' words have invalid checksum!
        assert!(!MnemonicManager::validate_phrase(invalid));
    }

    #[test]
    fn test_seed_derivation() {
        let phrase = "legal winner thank year wave sausage worth useful legal winner thank yellow";
        assert!(MnemonicManager::validate_phrase(phrase));
        let seed = MnemonicManager::phrase_to_seed(phrase, "").unwrap();
        assert_ne!(seed.0, [0u8; 64]);
    }
}
