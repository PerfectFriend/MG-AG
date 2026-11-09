package com.example.crypto

import java.security.SecureRandom
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec

/**
 * Mental Dice Protocol Engine using Cryptographic Commit-Reveal Scheme.
 * Ensures provably fair dice rolls in peer-to-peer decentralized multiplayer
 * without requiring any central trusted server or referee.
 *
 * Algorithm:
 * 1. Player A generates random seed S_A (32 bytes) and secret roll value D_A in 0..5.
 *    Computes Commitment H_A = HMAC-SHA256(S_A, D_A) and sends H_A to Player B.
 * 2. Player B does the same: generates S_B (32 bytes), D_B in 0..5.
 *    Computes Commitment H_B = HMAC-SHA256(S_B, D_B) and sends H_B to Player A.
 * 3. Once both commitments are securely exchanged, each player reveals (S_A, D_A) and (S_B, D_B).
 * 4. Both verify HMAC integrity. If mismatch, fraud is immediately flagged.
 * 5. Final dice values:
 *    Die 1 = ((D_A + D_B) mod 6) + 1
 *    Die 2 = ((S_A[0] xor S_B[0]) mod 6) + 1
 */
class MentalDiceEngine {

    data class Commitment(
        val commitmentHashHex: String
    )

    data class Reveal(
        val seedHex: String,
        val secretValue: Int
    )

    data class DiceOutcome(
        val die1: Int,
        val die2: Int,
        val isValid: Boolean,
        val auditProof: String,
        val localSeedHex: String = "",
        val localCommitmentHex: String = "",
        val localSecretValue: Int = 0,
        val peerSeedHex: String = "",
        val peerCommitmentHex: String = "",
        val peerSecretValue: Int = 0,
        val timestamp: Long = System.currentTimeMillis()
    )

    private val secureRandom = SecureRandom()

    // Local state for active roll
    var localSeed: ByteArray? = null
        private set
    var localValue: Int = 0
        private set
    var localCommitment: Commitment? = null
        private set

    var peerCommitment: Commitment? = null
        private set

    /**
     * Step 1: Create local commitment to send to peer.
     */
    fun createLocalCommitment(): Commitment {
        val seed = ByteArray(32)
        secureRandom.nextBytes(seed)
        localSeed = seed
        localValue = secureRandom.nextInt(6)

        val hash = calculateHmac(seed, localValue)
        val commitment = Commitment(bytesToHex(hash))
        localCommitment = commitment
        return commitment
    }

    /**
     * Step 2: Store commitment received from peer.
     */
    fun receivePeerCommitment(commitment: Commitment) {
        this.peerCommitment = commitment
    }

    /**
     * Step 3: Reveal local secret once peer commitment is received.
     */
    fun createLocalReveal(): Reveal {
        val seed = localSeed ?: throw IllegalStateException("Local commitment not initialized")
        return Reveal(bytesToHex(seed), localValue)
    }

    /**
     * Step 4: Verify peer reveal against previously stored peer commitment,
     * and compute consensus dice values.
     */
    fun verifyAndComputeOutcome(peerReveal: Reveal): DiceOutcome {
        val storedPeerCommitment = peerCommitment
            ?: return DiceOutcome(0, 0, false, "ERROR: Missing peer commitment")
        val mySeed = localSeed
            ?: return DiceOutcome(0, 0, false, "ERROR: Missing local seed")

        val peerSeedBytes = hexToBytes(peerReveal.seedHex)
        val computedPeerHash = bytesToHex(calculateHmac(peerSeedBytes, peerReveal.secretValue))

        val mySeedHex = bytesToHex(mySeed)
        val myCommitmentHex = localCommitment?.commitmentHashHex ?: ""

        if (!computedPeerHash.equals(storedPeerCommitment.commitmentHashHex, ignoreCase = true)) {
            return DiceOutcome(
                die1 = 0,
                die2 = 0,
                isValid = false,
                auditProof = "FRAUD_DETECTED: Peer reveal HMAC does not match commitment!",
                localSeedHex = mySeedHex,
                localCommitmentHex = myCommitmentHex,
                localSecretValue = localValue,
                peerSeedHex = peerReveal.seedHex,
                peerCommitmentHex = storedPeerCommitment.commitmentHashHex,
                peerSecretValue = peerReveal.secretValue
            )
        }

        // Consensus computation:
        // Die 1 = ((localValue + peerValue) mod 6) + 1
        // Die 2 = ((mySeed[0] xor peerSeed[0]) mod 6) + 1
        val die1 = ((localValue + peerReveal.secretValue) % 6) + 1
        val seedXor = (mySeed[0].toInt() and 0xFF) xor (peerSeedBytes[0].toInt() and 0xFF)
        val die2 = (seedXor % 6) + 1

        val audit = "VERIFIED [Die1: $die1 ((ValA: $localValue + ValB: ${peerReveal.secretValue}) % 6 + 1), Die2: $die2 (SeedXor: $seedXor % 6 + 1)]"
        return DiceOutcome(
            die1 = die1,
            die2 = die2,
            isValid = true,
            auditProof = audit,
            localSeedHex = mySeedHex,
            localCommitmentHex = myCommitmentHex,
            localSecretValue = localValue,
            peerSeedHex = peerReveal.seedHex,
            peerCommitmentHex = storedPeerCommitment.commitmentHashHex,
            peerSecretValue = peerReveal.secretValue
        )
    }

    /**
     * Executes a fully self-contained cryptographic roll simulation
     * between local entropy and simulated peer entropy.
     * Both sides follow the exact commit-reveal scheme with HMAC-SHA256 verification.
     */
    fun simulateFairConsensusRoll(forcedPeerValue: Int? = null): DiceOutcome {
        createLocalCommitment()

        val peerSeed = ByteArray(32)
        secureRandom.nextBytes(peerSeed)
        val peerVal = forcedPeerValue ?: secureRandom.nextInt(6)
        val peerHmac = calculateHmac(peerSeed, peerVal)
        val peerCommitment = Commitment(bytesToHex(peerHmac))

        receivePeerCommitment(peerCommitment)

        val peerReveal = Reveal(bytesToHex(peerSeed), peerVal)
        return verifyAndComputeOutcome(peerReveal)
    }

    fun reset() {
        localSeed = null
        localValue = 0
        localCommitment = null
        peerCommitment = null
    }

    companion object {
        fun calculateHmac(seed: ByteArray, value: Int): ByteArray {
            val mac = Mac.getInstance("HmacSHA256")
            val secretKey = SecretKeySpec(seed, "HmacSHA256")
            mac.init(secretKey)
            mac.update(byteArrayOf(value.toByte()))
            return mac.doFinal()
        }

        fun bytesToHex(bytes: ByteArray): String =
            bytes.joinToString("") { "%02x".format(it) }

        fun hexToBytes(hex: String): ByteArray {
            val len = hex.length
            val data = ByteArray(len / 2)
            var i = 0
            while (i < len) {
                data[i / 2] = ((Character.digit(hex[i], 16) shl 4) +
                        Character.digit(hex[i + 1], 16)).toByte()
                i += 2
            }
            return data
        }
    }
}
