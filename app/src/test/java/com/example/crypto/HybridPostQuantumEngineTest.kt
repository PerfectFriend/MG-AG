package com.example.crypto

import org.bouncycastle.jce.provider.BouncyCastleProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.BeforeClass
import org.junit.Test
import java.security.Security

class HybridPostQuantumEngineTest {

    companion object {
        @BeforeClass
        @JvmStatic
        fun setupSecurity() {
            try {
                Security.removeProvider("BC")
                Security.insertProviderAt(BouncyCastleProvider(), 1)
            } catch (_: Throwable) {}
        }
    }

    @Test
    fun testMlKem1024EncapsulationAndDecapsulation() {
        val engine = HybridPostQuantumEngine()
        val keyPair = engine.generateMlKemKeyPair()
        assertNotNull(keyPair.publicKey)
        assertNotNull(keyPair.privateKey)

        val (secretSender, cipherText) = engine.encapsulateMlKem(keyPair.publicKey)
        assertNotNull(secretSender)
        assertTrue(secretSender.isNotEmpty())
        assertTrue(cipherText.isNotEmpty())

        val secretReceiver = engine.decapsulateMlKem(keyPair.privateKey, cipherText)
        org.junit.Assert.assertArrayEquals(secretSender, secretReceiver)
    }

    @Test
    fun testRingLweLatticeArithmetic() {
        val engine = HybridPostQuantumEngine()
        val keyPair = engine.generateRingLweKeyPair()
        val (secretSender, cipherPoly) = engine.encapsulateRingLwe(keyPair.publicKeyPoly)
        val secretReceiver = engine.decapsulateRingLwe(keyPair.privateKeyPoly, cipherPoly)
        assertNotNull(secretSender)
        assertNotNull(secretReceiver)
    }

    @Test
    fun testSasEmojiCodeGeneration() {
        val secret = ByteArray(32) { (it * 7).toByte() }
        val emojis = HybridPostQuantumEngine.generateSasEmojiCode(secret)
        assertEquals(4, emojis.size)
        emojis.forEach { emoji ->
            assertTrue(emoji.isNotEmpty())
        }
    }
}
