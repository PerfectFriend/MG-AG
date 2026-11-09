package com.example.crypto

import org.bouncycastle.jce.provider.BouncyCastleProvider
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.BeforeClass
import org.junit.Test
import java.security.Security

class DoubleRatchetVaultTest {

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
    fun testDoubleRatchetVaultEncryptionAndDecryption() {
        val sharedRootKey = ByteArray(32) { it.toByte() }
        val aliceKeyPair = DoubleRatchetVault.generateKeyPair()
        val bobKeyPair = DoubleRatchetVault.generateKeyPair()

        // Alice initializes with Bob's public key (initiator)
        val aliceVault = DoubleRatchetVault(sharedRootKey.clone(), aliceKeyPair, bobKeyPair.public)
        // Bob initializes as responder (waits for Alice's first ratchet key in header)
        val bobVault = DoubleRatchetVault(sharedRootKey.clone(), bobKeyPair, null)

        val message1 = "Hello Bob! This is an authenticated post-quantum secure message.".toByteArray(Charsets.UTF_8)
        val encrypted1 = aliceVault.ratchetEncrypt(message1)

        assertEquals(DoubleRatchetVault.PADDED_BLOCK_SIZE + 16, encrypted1.cipherText.size)
        val decrypted1 = bobVault.ratchetDecrypt(encrypted1)
        assertArrayEquals(message1, decrypted1)
        assertEquals(String(message1), String(decrypted1))

        // Bob responds to Alice (ratchet turns in both directions)
        val message2 = "Message received Alice! PQC handshake verified.".toByteArray(Charsets.UTF_8)
        val encrypted2 = bobVault.ratchetEncrypt(message2)
        val decrypted2 = aliceVault.ratchetDecrypt(encrypted2)
        assertArrayEquals(message2, decrypted2)
    }

    @Test
    fun testMessagePaddingTo16Kb() {
        val payload = "Short text".toByteArray(Charsets.UTF_8)
        val padded = DoubleRatchetVault.padTo16Kb(payload)
        assertEquals(16384, padded.size)

        val unpadded = DoubleRatchetVault.unpadFrom16Kb(padded)
        assertArrayEquals(payload, unpadded)
    }
}
