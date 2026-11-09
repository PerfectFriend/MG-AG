package com.example.crypto

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class MentalDiceEngineTest {

    @Test
    fun testProvablyFairConsensusDiceOutcome() {
        val playerA = MentalDiceEngine()
        val playerB = MentalDiceEngine()

        // 1. Both players create secret commitments
        val commitA = playerA.createLocalCommitment()
        val commitB = playerB.createLocalCommitment()

        assertNotNull(commitA.commitmentHashHex)
        assertNotNull(commitB.commitmentHashHex)
        assertEquals(64, commitA.commitmentHashHex.length) // SHA-256 is 32 bytes = 64 hex chars

        // 2. Exchange commitments
        playerA.receivePeerCommitment(commitB)
        playerB.receivePeerCommitment(commitA)

        // 3. Create reveals
        val revealA = playerA.createLocalReveal()
        val revealB = playerB.createLocalReveal()

        // 4. Verify outcomes independently on both clients
        val outcomeA = playerA.verifyAndComputeOutcome(revealB)
        val outcomeB = playerB.verifyAndComputeOutcome(revealA)

        // 5. Consensus checks
        assertTrue("Player A verification should pass", outcomeA.isValid)
        assertTrue("Player B verification should pass", outcomeB.isValid)

        // Both players must reach the EXACT SAME dice values
        assertEquals("Both players agree on Die 1", outcomeA.die1, outcomeB.die1)
        assertEquals("Both players agree on Die 2", outcomeA.die2, outcomeB.die2)

        // Values must be within legal Backgammon dice bounds [1..6]
        assertTrue("Die 1 in 1..6", outcomeA.die1 in 1..6)
        assertTrue("Die 2 in 1..6", outcomeA.die2 in 1..6)
    }

    @Test
    fun testFraudDetectionWhenPeerAltersSecretRollValue() {
        val playerA = MentalDiceEngine()
        val playerB = MentalDiceEngine()

        val commitA = playerA.createLocalCommitment()
        val commitB = playerB.createLocalCommitment()

        playerA.receivePeerCommitment(commitB)
        playerB.receivePeerCommitment(commitA)

        // Attacker B attempts to lie about their secret value during reveal
        val honestRevealB = playerB.createLocalReveal()
        val fraudulentRevealB = honestRevealB.copy(
            secretValue = (honestRevealB.secretValue + 3) % 6 // Tampered!
        )

        val outcomeA = playerA.verifyAndComputeOutcome(fraudulentRevealB)

        // Tampered reveal must fail HMAC check
        assertFalse("Tampered reveal must be rejected", outcomeA.isValid)
        assertTrue(outcomeA.auditProof.contains("FRAUD_DETECTED"))
    }

    @Test
    fun testSimulateFairConsensusRollWithAuditFields() {
        val engine = MentalDiceEngine()
        val outcome = engine.simulateFairConsensusRoll()

        assertTrue(outcome.isValid)
        assertTrue(outcome.die1 in 1..6)
        assertTrue(outcome.die2 in 1..6)
        assertTrue(outcome.localCommitmentHex.isNotBlank())
        assertTrue(outcome.peerCommitmentHex.isNotBlank())
        assertTrue(outcome.localSeedHex.isNotBlank())
        assertTrue(outcome.peerSeedHex.isNotBlank())
        assertTrue(outcome.auditProof.contains("VERIFIED"))
    }
}
