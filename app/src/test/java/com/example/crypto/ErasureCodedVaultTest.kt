package com.example.crypto

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test

class ErasureCodedVaultTest {

    @Test
    fun testEncryptionShardingAndReconstruction() {
        val vault = ErasureCodedVault()
        val masterSeed = "correct horse battery staple 12-word bip39 seed phrase test test"
        val masterKey = ErasureCodedVault.deriveVaultKey(masterSeed)

        val originalPlainText = "This is a confidential file stored inside SimpleX Zero-Knowledge Vault!".toByteArray(Charsets.UTF_8)

        // 1. Encrypt
        val cipherBytes = vault.encryptBytes(originalPlainText, masterKey)
        assertNotNull(cipherBytes)

        // 2. Shard into 4 Data Shards + 2 Parity Shards (Erasure Coding)
        val vaultFile = vault.shardFile("confidential.txt", cipherBytes, dataShardsCount = 4, parityShardsCount = 2)
        assertEquals(6, vaultFile.shards.size)
        assertEquals(4, vaultFile.shards.count { !it.isParity })
        assertEquals(2, vaultFile.shards.count { it.isParity })

        // 3. Reconstruct using only the 4 data shards
        val dataShards = vaultFile.shards.filter { !it.isParity }
        val reconstructedCipher = vault.reconstructFile(dataShards)
        assertArrayEquals(cipherBytes, reconstructedCipher)

        // 4. Decrypt reconstructed bytes with master key
        val decryptedText = vault.decryptBytes(reconstructedCipher, masterKey)
        assertArrayEquals(originalPlainText, decryptedText)
        assertEquals(String(originalPlainText), String(decryptedText))
    }
}
