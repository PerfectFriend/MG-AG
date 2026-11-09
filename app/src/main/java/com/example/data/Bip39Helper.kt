package com.example.data

import android.content.Context
import java.security.MessageDigest
import java.security.SecureRandom

object Bip39Helper {
    private var cachedWordlist: List<String>? = null

    /**
     * Loads the BIP-39 English wordlist from assets.
     */
    fun loadWordlist(context: Context): List<String> {
        cachedWordlist?.let { return it }
        return try {
            context.assets.open("bip39_english.txt").bufferedReader().use { reader ->
                val words = reader.readLines().map { it.trim() }.filter { it.isNotEmpty() }
                if (words.size == 2048) {
                    cachedWordlist = words
                    words
                } else {
                    emptyList()
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
            emptyList()
        }
    }

    /**
     * Generates a random 12-word seed phrase.
     */
    fun generateMnemonic(context: Context): String {
        val wordlist = loadWordlist(context)
        if (wordlist.size != 2048) {
            // Returning a fixed phrase here would hand every affected install the same
            // publicly known seed, so fail loudly instead.
            throw IllegalStateException(
                "BIP-39 wordlist is missing or truncated (${wordlist.size} of 2048 words); refusing to generate a predictable seed phrase"
            )
        }

        val entropy = ByteArray(16)
        SecureRandom().nextBytes(entropy)

        // Compute SHA-256 for checksum
        val md = MessageDigest.getInstance("SHA-256")
        val hash = md.digest(entropy)
        
        // 128 / 32 = 4 bits checksum
        val checksumByte = hash[0]
        
        // Convert entropy bytes and 4-bit checksum to a continuous list of bits (132 bits)
        val bits = BooleanArray(132)
        var bitIndex = 0
        for (b in entropy) {
            for (i in 7 downTo 0) {
                bits[bitIndex++] = ((b.toInt() ushr i) and 1) == 1
            }
        }
        // Append 4 bits of checksum (from the first byte of SHA-256)
        for (i in 7 downTo 4) {
            bits[bitIndex++] = ((checksumByte.toInt() ushr i) and 1) == 1
        }

        // Convert 132 bits to 12 words (12 * 11 bits)
        val mnemonicWords = mutableListOf<String>()
        for (w in 0 until 12) {
            var index = 0
            for (i in 0 until 11) {
                index = index shl 1
                if (bits[w * 11 + i]) {
                    index = index or 1
                }
            }
            mnemonicWords.add(wordlist[index])
        }

        return mnemonicWords.joinToString(" ")
    }

    /**
     * Validates a 12-word seed phrase.
     */
    fun validateMnemonic(context: Context, mnemonic: String): Boolean {
        val wordlist = loadWordlist(context)
        if (wordlist.size != 2048) return false

        val words = mnemonic.trim().lowercase().split("\\s+".toRegex())
        if (words.size != 12) return false

        // 1. Verify words exist in wordlist
        for (w in words) {
            if (!wordlist.contains(w)) return false
        }

        // 2. Perform checksum verification
        try {
            val bits = BooleanArray(132)
            for (w in 0 until 12) {
                val index = wordlist.indexOf(words[w])
                if (index < 0) return false
                for (i in 10 downTo 0) {
                    bits[w * 11 + (10 - i)] = ((index ushr i) and 1) == 1
                }
            }

            // Extract entropy bytes (16 bytes = 128 bits)
            val entropy = ByteArray(16)
            for (currByte in 0 until 16) {
                var value = 0
                for (b in 0 until 8) {
                    value = value shl 1
                    if (bits[currByte * 8 + b]) {
                        value = value or 1
                    }
                }
                entropy[currByte] = value.toByte()
            }

            // Extract checksum (last 4 bits)
            var expectedChecksumVal = 0
            for (b in 128 until 132) {
                expectedChecksumVal = expectedChecksumVal shl 1
                if (bits[b]) {
                    expectedChecksumVal = expectedChecksumVal or 1
                }
            }

            // Compute actual checksum from entropy
            val md = MessageDigest.getInstance("SHA-256")
            val hash = md.digest(entropy)
            val actualChecksumVal = (hash[0].toInt() ushr 4) and 0x0F

            return expectedChecksumVal == actualChecksumVal
        } catch (e: Exception) {
            e.printStackTrace()
            return false
        }
    }

    /**
     * Converts a 12-word BIP-39 mnemonic into a 512-bit (64-byte) binary seed
     * using standard PBKDF2 with HMAC-SHA512 (2048 iterations, salt "mnemonic" + passphrase).
     */
    fun mnemonicToSeed(mnemonic: String, passphrase: String = ""): ByteArray {
        val normalizedMnemonic = mnemonic.trim().lowercase().split("\\s+".toRegex()).joinToString(" ")
        val password = normalizedMnemonic.toByteArray(Charsets.UTF_8)
        val salt = ("mnemonic$passphrase").toByteArray(Charsets.UTF_8)
        return pbkdf2HmacSha512(password, salt, 2048, 64)
    }

    /**
     * Derives full cryptographic identity keys and fingerprints from the 12-word seed phrase.
     */
    fun deriveCryptoIdentity(mnemonic: String): CryptoIdentity {
        val seedBytes = mnemonicToSeed(mnemonic)
        val seedBytesHex = seedBytes.joinToString("") { "%02x".format(it) }

        // Master Private Key: first 32 bytes (256 bits)
        val masterPrivateKeyBytes = seedBytes.copyOfRange(0, 32)

        // There is no asymmetric identity key in this build. What was previously called
        // "master public key" was SHA-256(private key) — anyone holding it could rebuild
        // the private half from the (recoverable) seed. Expose an explicit fingerprint
        // instead, domain-separated, and never a value derived from the private key alone.
        val sha256 = MessageDigest.getInstance("SHA-256")
        sha256.update("madgammon-identity-fingerprint".toByteArray(Charsets.UTF_8))
        val fingerprintBytes = sha256.digest(masterPrivateKeyBytes)
        val masterKeyFingerprintHex = fingerprintBytes.joinToString("") { "%02x".format(it) }

        // Identity Fingerprint: e.g. "A1B2:C3D4:E5F6:7890:1234:5678:90AB:CDEF"
        val fingerprint = fingerprintBytes.take(16)
            .chunked(2)
            .joinToString(":") { chunk -> chunk.joinToString("") { "%02X".format(it) } }

        val words = mnemonic.trim().lowercase().split("\\s+".toRegex())

        return CryptoIdentity(
            seedPhrase = words.joinToString(" "),
            words = words,
            seedBytesHex = seedBytesHex,
            masterKeyFingerprintHex = masterKeyFingerprintHex,
            identityFingerprint = fingerprint
        )
    }

    private fun pbkdf2HmacSha512(password: ByteArray, salt: ByteArray, iterations: Int, keyLengthBytes: Int): ByteArray {
        val mac = javax.crypto.Mac.getInstance("HmacSHA512")
        mac.init(javax.crypto.spec.SecretKeySpec(password, "HmacSHA512"))
        val hLen = mac.macLength // 64 bytes
        val l = (keyLengthBytes + hLen - 1) / hLen
        val r = keyLengthBytes - (l - 1) * hLen
        val result = ByteArray(keyLengthBytes)
        var offset = 0
        val block = ByteArray(salt.size + 4)
        System.arraycopy(salt, 0, block, 0, salt.size)

        for (i in 1..l) {
            block[salt.size] = (i ushr 24).toByte()
            block[salt.size + 1] = (i ushr 16).toByte()
            block[salt.size + 2] = (i ushr 8).toByte()
            block[salt.size + 3] = i.toByte()

            var u = mac.doFinal(block)
            val t = u.clone()

            for (c in 2..iterations) {
                u = mac.doFinal(u)
                for (k in t.indices) {
                    t[k] = (t[k].toInt() xor u[k].toInt()).toByte()
                }
            }

            val bytesToCopy = if (i == l) r else hLen
            System.arraycopy(t, 0, result, offset, bytesToCopy)
            offset += bytesToCopy
        }
        return result
    }
}

/**
 * Encapsulates the derived cryptographic identity.
 */
data class CryptoIdentity(
    val seedPhrase: String,
    val words: List<String>,
    val seedBytesHex: String,
    val masterKeyFingerprintHex: String,
    val identityFingerprint: String,
    val createdAt: Long = System.currentTimeMillis(),
    val onionPeerId: String = "anon-" + identityFingerprint.take(12),
    val masterPublicKeyHex: String = masterKeyFingerprintHex,
    val masterPrivateKeyHex: String = seedBytesHex
)

