use aes_gcm::{
    aead::Aead,
    Aes256Gcm, KeyInit, Nonce,
};
use hmac::{Hmac, Mac};
use rand::{rngs::OsRng, RngCore};
use sha2::Sha256;
use thiserror::Error;
use x25519_dalek::{PublicKey as EphemPublicKey, StaticSecret};
use zeroize::{Zeroize, ZeroizeOnDrop};
use std::collections::HashMap;

pub const PADDED_BLOCK_SIZE: usize = 16384; // 16 KB constant size block
pub const MAX_SKIP: usize = 256;

type HmacSha256 = Hmac<Sha256>;

#[derive(Error, Debug, PartialEq, Eq)]
pub enum RatchetError {
    #[error("Data length {0} exceeds max 16KB capacity")]
    DataTooLarge(usize),
    #[error("Padded block too short or malformed")]
    InvalidPadding,
    #[error("Decryption failed: AEAD authentication error")]
    DecryptionFailed,
    #[error("Key derivation error")]
    KdfError,
    #[error("Maximum skipped message keys exceeded ({0})")]
    TooManySkippedKeys(usize),
    #[error("Session not initialized for this direction")]
    NotInitialized,
}

/// Constant-time 16KB padding to prevent DPI packet size analysis.
pub fn pad_to_16kb(data: &[u8]) -> Result<[u8; PADDED_BLOCK_SIZE], RatchetError> {
    if data.len() > PADDED_BLOCK_SIZE - 4 {
        return Err(RatchetError::DataTooLarge(data.len()));
    }
    let mut block = [0u8; PADDED_BLOCK_SIZE];
    let len_bytes = (data.len() as u32).to_be_bytes();
    block[0..4].copy_from_slice(&len_bytes);
    block[4..4 + data.len()].copy_from_slice(data);

    // Fill remaining bytes with cryptographically secure random padding
    OsRng.fill_bytes(&mut block[4 + data.len()..]);
    Ok(block)
}

/// Unpadding 16KB block to recover original data.
pub fn unpad_from_16kb(padded: &[u8]) -> Result<Vec<u8>, RatchetError> {
    if padded.len() < 4 {
        return Err(RatchetError::InvalidPadding);
    }
    let mut len_bytes = [0u8; 4];
    len_bytes.copy_from_slice(&padded[0..4]);
    let len = u32::from_be_bytes(len_bytes) as usize;
    if len > padded.len() - 4 {
        return Err(RatchetError::InvalidPadding);
    }
    Ok(padded[4..4 + len].to_vec())
}

#[derive(Zeroize, ZeroizeOnDrop, Clone)]
pub struct SymmetricKdfKey(pub [u8; 32]);

/// Message header included with each ratchet ciphertext.
#[derive(Clone, Debug)]
pub struct MessageHeader {
    pub dh_public: [u8; 32],
    pub msg_num: u32,
    pub prev_chain_len: u32,
}

impl MessageHeader {
    /// Serialize header to 40 bytes: [32B DH pub] + [4B msg_num] + [4B prev_chain_len]
    pub fn to_bytes(&self) -> [u8; 40] {
        let mut buf = [0u8; 40];
        buf[0..32].copy_from_slice(&self.dh_public);
        buf[32..36].copy_from_slice(&self.msg_num.to_be_bytes());
        buf[36..40].copy_from_slice(&self.prev_chain_len.to_be_bytes());
        buf
    }

    pub fn from_bytes(data: &[u8; 40]) -> Self {
        let mut dh_public = [0u8; 32];
        dh_public.copy_from_slice(&data[0..32]);
        let msg_num = u32::from_be_bytes([data[32], data[33], data[34], data[35]]);
        let prev_chain_len = u32::from_be_bytes([data[36], data[37], data[38], data[39]]);
        Self { dh_public, msg_num, prev_chain_len }
    }
}

/// HMAC-SHA256 based KDF chain key derivation (matching Signal spec / Python crypto_vault.py).
fn hmac_sha256(key: &[u8], data: &[u8]) -> [u8; 32] {
    let mut mac = <HmacSha256 as Mac>::new_from_slice(key).expect("HMAC key len");
    mac.update(data);
    let result = mac.finalize();
    let mut out = [0u8; 32];
    out.copy_from_slice(&result.into_bytes());
    out
}

/// KDF-RK: derives new root key and chain key from current root key and DH output.
fn kdf_rk(rk: &[u8; 32], dh_out: &[u8; 32]) -> ([u8; 32], [u8; 32]) {
    let root_mac = hmac_sha256(rk, dh_out);
    let next_rk = hmac_sha256(&root_mac, &[0x01]);
    let next_ck = hmac_sha256(&root_mac, &[0x02]);
    (next_rk, next_ck)
}

/// KDF-CK: derives next chain key and message key from current chain key.
fn kdf_ck(ck: &[u8; 32]) -> ([u8; 32], [u8; 32]) {
    let next_ck = hmac_sha256(ck, &[0x02]);
    let msg_key = hmac_sha256(ck, &[0x01]);
    (next_ck, msg_key)
}

/// End-to-End Encryption Engine implementing full Signal Double Ratchet with AEAD AES-256-GCM.
///
/// Supports both initiator and responder roles with bidirectional encrypted messaging.
/// Each session uses 16KB constant-size padding to resist DPI traffic analysis.
pub struct DoubleRatchetSession {
    root_key: [u8; 32],
    sending_chain_key: Option<[u8; 32]>,
    receiving_chain_key: Option<[u8; 32]>,
    our_dh: StaticSecret,
    our_dh_public: EphemPublicKey,
    peer_dh_public: Option<[u8; 32]>,
    send_count: u32,
    recv_count: u32,
    prev_send_chain_len: u32,
    #[allow(dead_code)]
    is_initiator: bool,
    skipped_keys: HashMap<([u8; 32], u32), [u8; 32]>, // (dh_pub, msg_num) -> msg_key
}

impl DoubleRatchetSession {
    /// Create a new session as the INITIATOR.
    /// The initiator performs the first DH ratchet step immediately and can send first.
    pub fn new_initiator(initial_shared_secret: [u8; 32], peer_public_key: EphemPublicKey) -> Self {
        let our_dh = StaticSecret::random_from_rng(OsRng);
        let our_dh_public = EphemPublicKey::from(&our_dh);

        // DH agreement to derive initial sending chain
        let dh_shared = our_dh.diffie_hellman(&peer_public_key);

        let (next_rk, ck_send) = kdf_rk(&initial_shared_secret, dh_shared.as_bytes());

        Self {
            root_key: next_rk,
            sending_chain_key: Some(ck_send),
            receiving_chain_key: None,
            our_dh,
            our_dh_public,
            peer_dh_public: Some(*peer_public_key.as_bytes()),
            send_count: 0,
            recv_count: 0,
            prev_send_chain_len: 0,
            is_initiator: true,
            skipped_keys: HashMap::new(),
        }
    }

    /// Create a new session as the RESPONDER.
    /// The responder waits for the first message to perform the DH ratchet step.
    pub fn new_responder(initial_shared_secret: [u8; 32], our_dh_private: StaticSecret) -> Self {
        let our_dh_public = EphemPublicKey::from(&our_dh_private);
        Self {
            root_key: initial_shared_secret,
            sending_chain_key: None,
            receiving_chain_key: None,
            our_dh: our_dh_private,
            our_dh_public,
            peer_dh_public: None,
            send_count: 0,
            recv_count: 0,
            prev_send_chain_len: 0,
            is_initiator: false,
            skipped_keys: HashMap::new(),
        }
    }

    /// Returns our current DH public key bytes.
    pub fn public_key_bytes(&self) -> [u8; 32] {
        *self.our_dh_public.as_bytes()
    }

    /// Performs the DH ratchet step when receiving a new peer public key.
    fn dh_ratchet(&mut self, new_peer_dh_pub: &[u8; 32]) {
        self.prev_send_chain_len = self.send_count;
        self.send_count = 0;
        self.recv_count = 0;
        self.peer_dh_public = Some(*new_peer_dh_pub);

        // Derive receiving chain from current DH key + new peer key
        let new_peer_pub = EphemPublicKey::from(*new_peer_dh_pub);
        let dh_secret_recv = self.our_dh.diffie_hellman(&new_peer_pub);
        let (next_rk, ck_recv) = kdf_rk(&self.root_key, dh_secret_recv.as_bytes());
        self.root_key = next_rk;
        self.receiving_chain_key = Some(ck_recv);

        // Generate new DH key pair for sending chain
        self.our_dh = StaticSecret::random_from_rng(OsRng);
        self.our_dh_public = EphemPublicKey::from(&self.our_dh);

        let dh_secret_send = self.our_dh.diffie_hellman(&new_peer_pub);
        let (next_rk2, ck_send) = kdf_rk(&self.root_key, dh_secret_send.as_bytes());
        self.root_key = next_rk2;
        self.sending_chain_key = Some(ck_send);
    }

    /// Skip message keys when receiving out-of-order messages.
    fn skip_message_keys(&mut self, until: u32) -> Result<(), RatchetError> {
        if let Some(ref mut ck) = self.receiving_chain_key {
            while self.recv_count < until {
                if self.skipped_keys.len() >= MAX_SKIP {
                    // Evict oldest
                    if let Some(key) = self.skipped_keys.keys().next().cloned() {
                        self.skipped_keys.remove(&key);
                    }
                }
                let (next_ck, msg_key) = kdf_ck(ck);
                let peer_pub = self.peer_dh_public.unwrap_or([0u8; 32]);
                self.skipped_keys.insert((peer_pub, self.recv_count), msg_key);
                *ck = next_ck;
                self.recv_count += 1;
            }
        }
        Ok(())
    }

    /// Encrypts plaintext message into 16KB constant-size padded ciphertext.
    /// Returns: (header_bytes: [u8; 40], ciphertext: Vec<u8>)
    pub fn encrypt(&mut self, plaintext: &[u8]) -> Result<Vec<u8>, RatchetError> {
        let ck = self.sending_chain_key.as_mut()
            .ok_or(RatchetError::NotInitialized)?;

        let (next_ck, msg_key) = kdf_ck(ck);
        *ck = next_ck;

        let header = MessageHeader {
            dh_public: *self.our_dh_public.as_bytes(),
            msg_num: self.send_count,
            prev_chain_len: self.prev_send_chain_len,
        };
        self.send_count += 1;

        // Pad plaintext to 16KB
        let padded = pad_to_16kb(plaintext)?;

        // AES-256-GCM encryption
        let cipher = Aes256Gcm::new_from_slice(&msg_key)
            .map_err(|_| RatchetError::KdfError)?;
        let mut nonce_bytes = [0u8; 12];
        OsRng.fill_bytes(&mut nonce_bytes);
        let nonce = Nonce::from_slice(&nonce_bytes);

        let ciphertext = cipher
            .encrypt(nonce, padded.as_ref())
            .map_err(|_| RatchetError::DecryptionFailed)?;

        // Envelope: [40B Header] + [12B Nonce] + [Ciphertext]
        let header_bytes = header.to_bytes();
        let mut envelope = Vec::with_capacity(40 + 12 + ciphertext.len());
        envelope.extend_from_slice(&header_bytes);
        envelope.extend_from_slice(&nonce_bytes);
        envelope.extend_from_slice(&ciphertext);

        Ok(envelope)
    }

    /// Decrypts a received envelope, performing DH ratchet step if needed.
    pub fn decrypt(&mut self, envelope: &[u8]) -> Result<Vec<u8>, RatchetError> {
        if envelope.len() < 40 + 12 + 16 {
            return Err(RatchetError::InvalidPadding);
        }

        // Parse header
        let mut header_bytes = [0u8; 40];
        header_bytes.copy_from_slice(&envelope[0..40]);
        let header = MessageHeader::from_bytes(&header_bytes);

        let nonce_bytes = &envelope[40..52];
        let ciphertext = &envelope[52..];

        // Check skipped keys first
        let skip_key = (header.dh_public, header.msg_num);
        if let Some(msg_key) = self.skipped_keys.remove(&skip_key) {
            return self.decrypt_with_key(&msg_key, nonce_bytes, ciphertext);
        }

        // Do we need a DH ratchet step?
        let need_ratchet = match &self.peer_dh_public {
            None => true,
            Some(current_peer) => *current_peer != header.dh_public,
        };

        if need_ratchet {
            // Skip remaining messages in the old receiving chain
            if self.receiving_chain_key.is_some() {
                self.skip_message_keys(header.prev_chain_len)?;
            }
            self.dh_ratchet(&header.dh_public);
        }

        // Skip ahead if msg_num > recv_count
        self.skip_message_keys(header.msg_num)?;

        // Derive message key from receiving chain
        let ck = self.receiving_chain_key.as_mut()
            .ok_or(RatchetError::NotInitialized)?;
        let (next_ck, msg_key) = kdf_ck(ck);
        *ck = next_ck;
        self.recv_count += 1;

        self.decrypt_with_key(&msg_key, nonce_bytes, ciphertext)
    }

    /// Internal: decrypt ciphertext with a specific message key.
    fn decrypt_with_key(&self, msg_key: &[u8; 32], nonce_bytes: &[u8], ciphertext: &[u8]) -> Result<Vec<u8>, RatchetError> {
        let cipher = Aes256Gcm::new_from_slice(msg_key)
            .map_err(|_| RatchetError::KdfError)?;
        let nonce = Nonce::from_slice(nonce_bytes);
        let padded = cipher
            .decrypt(nonce, ciphertext)
            .map_err(|_| RatchetError::DecryptionFailed)?;
        unpad_from_16kb(&padded)
    }
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn test_padding_roundtrip() {
        let data = b"Hello, sovereign world!";
        let padded = pad_to_16kb(data).unwrap();
        assert_eq!(padded.len(), PADDED_BLOCK_SIZE);
        let recovered = unpad_from_16kb(&padded).unwrap();
        assert_eq!(recovered, data);
    }

    #[test]
    fn test_double_ratchet_bidirectional() {
        let shared_secret = [0x42u8; 32];

        // Bob's initial DH key (the responder)
        let bob_dh = StaticSecret::random_from_rng(OsRng);
        let bob_dh_pub = EphemPublicKey::from(&bob_dh);

        // Alice is initiator, Bob is responder
        let mut alice = DoubleRatchetSession::new_initiator(shared_secret, bob_dh_pub);
        let mut bob = DoubleRatchetSession::new_responder(shared_secret, bob_dh);

        // Alice -> Bob (first message)
        let msg1 = b"Secret backgammon move: 24->OFF";
        let envelope1 = alice.encrypt(msg1).unwrap();
        let decrypted1 = bob.decrypt(&envelope1).unwrap();
        assert_eq!(decrypted1, msg1);

        // Bob -> Alice (reverse direction triggers DH ratchet)
        let msg2 = b"Acknowledged. My turn: 6->1";
        let envelope2 = bob.encrypt(msg2).unwrap();
        let decrypted2 = alice.decrypt(&envelope2).unwrap();
        assert_eq!(decrypted2, msg2);

        // Alice -> Bob again (another DH ratchet step)
        let msg3 = b"Moving checker from bar to 24";
        let envelope3 = alice.encrypt(msg3).unwrap();
        let decrypted3 = bob.decrypt(&envelope3).unwrap();
        assert_eq!(decrypted3, msg3);
    }

    #[test]
    fn test_multiple_messages_same_direction() {
        let shared_secret = [0xABu8; 32];
        let bob_dh = StaticSecret::random_from_rng(OsRng);
        let bob_dh_pub = EphemPublicKey::from(&bob_dh);

        let mut alice = DoubleRatchetSession::new_initiator(shared_secret, bob_dh_pub);
        let mut bob = DoubleRatchetSession::new_responder(shared_secret, bob_dh);

        // Send 5 messages in a row Alice -> Bob
        for i in 0..5 {
            let msg = format!("Message #{}", i);
            let envelope = alice.encrypt(msg.as_bytes()).unwrap();
            let decrypted = bob.decrypt(&envelope).unwrap();
            assert_eq!(decrypted, msg.as_bytes());
        }
    }

    #[test]
    fn test_constant_envelope_size() {
        let shared_secret = [0x77u8; 32];
        let bob_dh = StaticSecret::random_from_rng(OsRng);
        let bob_dh_pub = EphemPublicKey::from(&bob_dh);
        let mut alice = DoubleRatchetSession::new_initiator(shared_secret, bob_dh_pub);

        let env_short = alice.encrypt(b"Hi").unwrap();
        let env_long = alice.encrypt(b"A very long message that spans many bytes of text content for testing traffic analysis resistance properties").unwrap();

        // Both envelopes must be the same size (traffic analysis resistance)
        assert_eq!(env_short.len(), env_long.len());
    }
}
