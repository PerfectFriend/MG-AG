use hkdf::Hkdf;
use rand::rngs::OsRng;
use sha2::{Digest, Sha256};
use x25519_dalek::{PublicKey, StaticSecret};
use zeroize::{Zeroize, ZeroizeOnDrop};

const EMOJI_PALETTE: [&str; 16] = [
    "🛡️", "🔑", "🎲", "⚡", "💎", "🦊", "👑", "🦅",
    "🚀", "🔥", "🪐", "🍀", "🎯", "⚓", "🛸", "🔮",
];

#[derive(Zeroize, ZeroizeOnDrop, PartialEq, Eq, Debug, Clone)]
pub struct HybridSharedSecret(pub [u8; 32]);

pub struct HybridKeyExchange {
    static_secret: StaticSecret,
    public_key: PublicKey,
}

impl HybridKeyExchange {
    pub fn new() -> Self {
        let static_secret = StaticSecret::random_from_rng(OsRng);
        let public_key = PublicKey::from(&static_secret);
        Self {
            static_secret,
            public_key,
        }
    }

    pub fn public_key_bytes(&self) -> [u8; 32] {
        *self.public_key.as_bytes()
    }

    /// Performs Diffie-Hellman agreement with peer and derives 32-byte shared key using HKDF.
    pub fn compute_shared_key(
        self,
        peer_public_key: &[u8; 32],
        kem_ciphertext: Option<&[u8]>,
    ) -> HybridSharedSecret {
        let peer_pk = PublicKey::from(*peer_public_key);
        let dh_shared = self.static_secret.diffie_hellman(&peer_pk);

        let salt = kem_ciphertext.unwrap_or(&[0u8; 32]);
        let hk = Hkdf::<Sha256>::new(Some(salt), dh_shared.as_bytes());

        let mut okm = [0u8; 32];
        hk.expand(b"MadGammonHybridPQAgreement", &mut okm)
            .expect("32B HKDF expansion");

        HybridSharedSecret(okm)
    }

    /// Generates Short Authentication String (SAS) 4-emoji code for out-of-band peer verification.
    pub fn generate_sas_emojis(shared_secret: &[u8; 32]) -> [String; 4] {
        let mut hasher = Sha256::new();
        hasher.update(shared_secret);
        let hash = hasher.finalize();

        [
            EMOJI_PALETTE[(hash[0] as usize) % EMOJI_PALETTE.len()].to_string(),
            EMOJI_PALETTE[(hash[1] as usize) % EMOJI_PALETTE.len()].to_string(),
            EMOJI_PALETTE[(hash[2] as usize) % EMOJI_PALETTE.len()].to_string(),
            EMOJI_PALETTE[(hash[3] as usize) % EMOJI_PALETTE.len()].to_string(),
        ]
    }
}

impl Default for HybridKeyExchange {
    fn default() -> Self {
        Self::new()
    }
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn test_hybrid_key_exchange_agreement() {
        let alice = HybridKeyExchange::new();
        let bob = HybridKeyExchange::new();

        let alice_pk = alice.public_key_bytes();
        let bob_pk = bob.public_key_bytes();

        let alice_secret = alice.compute_shared_key(&bob_pk, None);
        let bob_secret = bob.compute_shared_key(&alice_pk, None);

        assert_eq!(alice_secret.0, bob_secret.0);

        let alice_sas = HybridKeyExchange::generate_sas_emojis(&alice_secret.0);
        let bob_sas = HybridKeyExchange::generate_sas_emojis(&bob_secret.0);
        assert_eq!(alice_sas, bob_sas);
    }
}
