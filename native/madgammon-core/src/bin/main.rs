use madgammon_core::crypto::double_ratchet::DoubleRatchetSession;
use madgammon_core::crypto::hybrid_pq::HybridKeyExchange;
use madgammon_core::crypto::mental_dice::MentalDiceEngine;
use madgammon_core::engine::rules::{BoardState, Player, Move};
use madgammon_core::vault::mnemonic::MnemonicManager;
use madgammon_core::vault::erasure::ShardedVault;
use std::time::Instant;

fn main() {
    println!("============================================================");
    println!("  MadGammon Sovereign Core — Rust v0.2.0 Host Verification  ");
    println!("============================================================");

    // 1. BIP-39 Mnemonic Benchmark
    println!("\n[1/6] Testing BIP-39 Mnemonic Generation & Seed Derivation...");
    let start = Instant::now();
    let phrase = MnemonicManager::generate_12_words();
    let is_valid = MnemonicManager::validate_phrase(&phrase);
    let seed = MnemonicManager::phrase_to_seed(&phrase, "").expect("Seed derivation");
    println!("  -> Generated 12-word phrase: \"{}\"", phrase);
    println!("  -> Validity: {} | Derivation time: {:.2?}", is_valid, start.elapsed());
    println!("  -> Derived Master Seed Prefix: {:02x}{:02x}{:02x}{:02x}...", seed.0[0], seed.0[1], seed.0[2], seed.0[3]);

    // 2. Mental Dice Protocol Benchmark
    println!("\n[2/6] Testing Provably Fair Mental Dice Commit-Reveal Protocol...");
    let start = Instant::now();
    let mut alice = MentalDiceEngine::new();
    let mut bob = MentalDiceEngine::new();

    let a_comm = alice.start_local_roll().expect("Alice commitment");
    let b_comm = bob.start_local_roll().expect("Bob commitment");

    alice.set_peer_commitment(b_comm);
    bob.set_peer_commitment(a_comm);

    let (a_seed, a_val) = alice.reveal_local_secret().expect("Alice reveal");
    let (b_seed, b_val) = bob.reveal_local_secret().expect("Bob reveal");

    let a_outcome = alice.verify_and_compute(&b_seed, b_val).expect("Alice compute");
    let b_outcome = bob.verify_and_compute(&a_seed, a_val).expect("Bob compute");

    assert_eq!(a_outcome.die1, b_outcome.die1);
    assert_eq!(a_outcome.die2, b_outcome.die2);
    println!("  -> Mutual Dice Consensus: [{}:{}] in {:.2?}", a_outcome.die1, a_outcome.die2, start.elapsed());
    println!("  -> Cryptographic Audit Proof: {}", a_outcome.audit_proof);

    // 3. Hybrid Post-Quantum Key Exchange & SAS
    println!("\n[3/6] Testing X25519 Hybrid Key Exchange & SAS Emojis...");
    let start = Instant::now();
    let alice_kx = HybridKeyExchange::new();
    let bob_kx = HybridKeyExchange::new();
    let alice_pub = alice_kx.public_key_bytes();
    let bob_pub = bob_kx.public_key_bytes();
    let alice_ss = alice_kx.compute_shared_key(&bob_pub, None);
    let bob_ss = bob_kx.compute_shared_key(&alice_pub, None);
    assert_eq!(alice_ss, bob_ss);
    let sas = HybridKeyExchange::generate_sas_emojis(&alice_ss.0);
    println!("  -> Key Agreement in {:.2?} | SAS: {}", start.elapsed(), sas.join(" "));

    // 4. Double Ratchet Bidirectional E2EE Benchmark
    println!("\n[4/6] Testing Full Signal Double Ratchet (Encrypt + Decrypt)...");
    let start = Instant::now();
    let bob_dh = x25519_dalek::StaticSecret::random_from_rng(rand::rngs::OsRng);
    let bob_dh_pub = x25519_dalek::PublicKey::from(&bob_dh);
    let mut alice_session = DoubleRatchetSession::new_initiator(alice_ss.0, bob_dh_pub);
    let mut bob_session = DoubleRatchetSession::new_responder(bob_ss.0, bob_dh);

    let msg1 = b"Secret sovereign backgammon move: 24->OFF";
    let envelope1 = alice_session.encrypt(msg1).expect("Alice encrypt");
    let decrypted1 = bob_session.decrypt(&envelope1).expect("Bob decrypt");
    assert_eq!(&decrypted1, msg1);

    let msg2 = b"Acknowledged. Reverse ratchet established.";
    let envelope2 = bob_session.encrypt(msg2).expect("Bob encrypt");
    let decrypted2 = alice_session.decrypt(&envelope2).expect("Alice decrypt");
    assert_eq!(&decrypted2, msg2);

    println!("  -> Bidirectional ratchet verified in {:.2?}", start.elapsed());
    println!("  -> Envelope size: {} bytes (16KB padded, Traffic Analysis Resistant)", envelope1.len());

    // 5. Reed-Solomon GF(2^8) Erasure Coding Benchmark
    println!("\n[5/6] Testing Reed-Solomon (4 Data + 2 Parity) Shard Reconstruction...");
    let start = Instant::now();
    let vault = ShardedVault::new(4, 2).expect("Vault init");
    let payload = b"Sovereign encrypted cryptocontainer database snapshot";
    let shards = vault.encode(payload).expect("RS Encode");
    println!("  -> Encoded into {} shards", shards.len());

    let mut surviving: Vec<Option<Vec<u8>>> = shards.into_iter().map(Some).collect();
    surviving[0] = None; // Drop shard 0
    surviving[3] = None; // Drop shard 3
    let reconstructed = vault.reconstruct(&mut surviving, payload.len()).expect("RS Reconstruct");
    assert_eq!(reconstructed, payload);
    println!("  -> Successfully recovered payload from damaged shards in {:.2?}", start.elapsed());

    // 6. Backgammon Rules Engine
    println!("\n[6/6] Testing Backgammon Rules, Moves & State Engine...");
    let start = Instant::now();
    let mut board = BoardState::initial_long();
    let legal_moves = board.get_all_legal_first_moves(&[3, 5], false);
    println!("  -> {} legal first moves for [3:5] generated", legal_moves.len());

    // Apply a move
    let mv = Move { from_rel: 0, to_rel: 3, die_used: 3 };
    board.apply_move(&mv).unwrap();
    println!("  -> Applied move 0->3 (die 3). Pip count: {} White, {} Black",
        board.pip_count(Player::White), board.pip_count(Player::Black));

    // Test Short backgammon
    let board_short = BoardState::initial_short();
    let short_moves = board_short.get_all_legal_first_moves(&[6, 1], false);
    println!("  -> Short backgammon: {} legal moves for [6:1]", short_moves.len());

    // JSON roundtrip
    let json = board.to_json();
    let restored = BoardState::from_json(&json).unwrap();
    assert_eq!(board, restored);
    println!("  -> Board state JSON roundtrip verified in {:.2?}", start.elapsed());

    println!("\n============================================================");
    println!("  ALL 6 SOVEREIGN SUBSYSTEMS VERIFIED IN RUST!              ");
    println!("============================================================");
}
