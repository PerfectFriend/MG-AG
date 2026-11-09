package com.example.crypto

import android.util.Base64
import java.security.SecureRandom
import java.util.Arrays
import javax.crypto.Cipher
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.PBEKeySpec
import javax.crypto.spec.SecretKeySpec

/**
 * Secure cryptographic vault implementing AES-256-GCM encryption with
 * PBKDF2WithHmacSHA256 key derivation from the user's master PIN code.
 *
 * Implements Stage 1 of the SimpleX Evolution Roadmap (Vault & Profiles).
 */
object SecureCryptoVault {

    // Upgraded to OWASP mobile recommendation for PBKDF2-HMAC-SHA256 (210,000 iterations)
    private const val ITERATIONS = 210000
    private const val KEY_LENGTH_BITS = 256
    private const val SALT_LENGTH_BYTES = 16
    private const val IV_LENGTH_BYTES = 12
    private const val GCM_TAG_LENGTH_BITS = 128
    private const val CIPHER_ALGORITHM = "AES/GCM/NoPadding"
    private const val KDF_ALGORITHM = "PBKDF2WithHmacSHA256"

    private val secureRandom = SecureRandom()

    /**
     * Generates a cryptographically strong random salt.
     */
    fun generateSalt(): ByteArray {
        val salt = ByteArray(SALT_LENGTH_BYTES)
        secureRandom.nextBytes(salt)
        return salt
    }

    /**
     * Generates salt as a hex string.
     */
    fun generateSaltHex(): String {
        return bytesToHex(generateSalt())
    }

    /**
     * Derives a 256-bit AES key from a PIN code and salt using PBKDF2.
     */
    private fun deriveKey(pin: String, salt: ByteArray): SecretKeySpec {
        val keySpec = PBEKeySpec(pin.toCharArray(), salt, ITERATIONS, KEY_LENGTH_BITS)
        val factory = SecretKeyFactory.getInstance(KDF_ALGORITHM)
        val keyBytes = factory.generateSecret(keySpec).encoded
        val secretKey = SecretKeySpec(keyBytes, "AES")
        Arrays.fill(keyBytes, 0.toByte())
        keySpec.clearPassword()
        return secretKey
    }

    /**
     * Encrypts plaintext bytes using AES-256-GCM with a newly generated IV and salt.
     * Returns a packed byte array: [SALT (16B)] + [IV (12B)] + [CIPHERTEXT + GCM_TAG].
     */
    fun encrypt(plainBytes: ByteArray, pin: String, customSalt: ByteArray? = null): ByteArray {
        val salt = customSalt ?: generateSalt()
        val key = deriveKey(pin, salt)

        val iv = ByteArray(IV_LENGTH_BYTES)
        secureRandom.nextBytes(iv)

        val cipher = Cipher.getInstance(CIPHER_ALGORITHM)
        val gcmSpec = GCMParameterSpec(GCM_TAG_LENGTH_BITS, iv)
        cipher.init(Cipher.ENCRYPT_MODE, key, gcmSpec)

        val cipherTextWithTag = cipher.doFinal(plainBytes)

        val result = ByteArray(salt.size + iv.size + cipherTextWithTag.size)
        System.arraycopy(salt, 0, result, 0, salt.size)
        System.arraycopy(iv, 0, result, salt.size, iv.size)
        System.arraycopy(cipherTextWithTag, 0, result, salt.size + iv.size, cipherTextWithTag.size)

        return result
    }

    /**
     * Decrypts a packed byte array produced by [encrypt].
     */
    fun decrypt(packedData: ByteArray, pin: String): ByteArray {
        if (packedData.size < SALT_LENGTH_BYTES + IV_LENGTH_BYTES + 16) {
            throw IllegalArgumentException("Data envelope too short or corrupted")
        }

        val salt = ByteArray(SALT_LENGTH_BYTES)
        val iv = ByteArray(IV_LENGTH_BYTES)
        val cipherTextWithTag = ByteArray(packedData.size - SALT_LENGTH_BYTES - IV_LENGTH_BYTES)

        System.arraycopy(packedData, 0, salt, 0, SALT_LENGTH_BYTES)
        System.arraycopy(packedData, SALT_LENGTH_BYTES, iv, 0, IV_LENGTH_BYTES)
        System.arraycopy(packedData, SALT_LENGTH_BYTES + IV_LENGTH_BYTES, cipherTextWithTag, 0, cipherTextWithTag.size)

        val key = deriveKey(pin, salt)
        val cipher = Cipher.getInstance(CIPHER_ALGORITHM)
        val gcmSpec = GCMParameterSpec(GCM_TAG_LENGTH_BITS, iv)
        cipher.init(Cipher.DECRYPT_MODE, key, gcmSpec)

        return cipher.doFinal(cipherTextWithTag)
    }

    /**
     * Helper to encrypt a UTF-8 String to a Base64-encoded envelope.
     */
    fun encryptString(plainText: String, pin: String, customSalt: ByteArray? = null): String {
        val encrypted = encrypt(plainText.toByteArray(Charsets.UTF_8), pin, customSalt)
        return Base64.encodeToString(encrypted, Base64.NO_WRAP)
    }

    /**
     * Helper to decrypt a Base64-encoded envelope into a UTF-8 String.
     */
    fun decryptString(base64Payload: String, pin: String): String {
        val packed = Base64.decode(base64Payload, Base64.NO_WRAP)
        val decryptedBytes = decrypt(packed, pin)
        return String(decryptedBytes, Charsets.UTF_8)
    }

    /**
     * Verifies if a given PIN can decrypt the payload envelope.
     */
    fun verifyPin(base64Payload: String, pin: String): Boolean {
        return try {
            decryptString(base64Payload, pin)
            true
        } catch (e: Exception) {
            false
        }
    }

    fun hexToBytes(hex: String): ByteArray {
        val result = ByteArray(hex.length / 2)
        for (i in result.indices) {
            val index = i * 2
            val j = hex.substring(index, index + 2).toInt(16)
            result[i] = j.toByte()
        }
        return result
    }

    fun bytesToHex(bytes: ByteArray): String {
        val sb = StringBuilder()
        for (b in bytes) {
            sb.append(String.format("%02x", b))
        }
        return sb.toString()
    }
}
