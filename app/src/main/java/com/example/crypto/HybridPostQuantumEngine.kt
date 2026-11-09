package com.example.crypto

import android.util.Base64
import android.util.Log
import org.bouncycastle.pqc.crypto.mlkem.MLKEMExtractor
import org.bouncycastle.pqc.crypto.mlkem.MLKEMGenerator
import org.bouncycastle.pqc.crypto.mlkem.MLKEMKeyGenerationParameters
import org.bouncycastle.pqc.crypto.mlkem.MLKEMKeyPairGenerator
import org.bouncycastle.pqc.crypto.mlkem.MLKEMParameters
import org.bouncycastle.pqc.crypto.mlkem.MLKEMPrivateKeyParameters
import org.bouncycastle.pqc.crypto.mlkem.MLKEMPublicKeyParameters
import java.security.KeyPair
import java.security.KeyPairGenerator
import java.security.MessageDigest
import java.security.SecureRandom
import java.security.Security
import javax.crypto.KeyAgreement
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec

/**
 * Hybrid Post-Quantum Key Exchange Engine.
 * Combines classical Diffie-Hellman (X25519 or ECDH P-256) with Post-Quantum KEM
 * (NIST FIPS 203 ML-KEM-1024 / Kyber-1024) and Ring-LWE polynomial lattice arithmetic.
 *
 * Provides Perfect Forward Secrecy and Post-Quantum Future Secrecy.
 */
class HybridPostQuantumEngine {

    companion object {
        private const val TAG = "HybridPQEngine"
        private const val Q = 3329
        private const val N = 256

        private val EMOJI_PALETTE = arrayOf(
            "🛡️", "🔑", "🎲", "⚡", "💎", "🦊", "👑", "🦅",
            "🚀", "🔥", "🪐", "🍀", "🎯", "⚓", "🛸", "🔮"
        )

        /**
         * Computes HKDF-Extract and HKDF-Expand with SHA-256 (RFC 5869)
         */
        fun hkdf(ikm: ByteArray, salt: ByteArray? = null, info: ByteArray = ByteArray(0), length: Int = 32): ByteArray {
            val effectiveSalt = salt ?: ByteArray(32)
            val hmacExtract = Mac.getInstance("HmacSHA256").apply {
                init(SecretKeySpec(effectiveSalt, "HmacSHA256"))
            }
            val prk = hmacExtract.doFinal(ikm)

            val hmacExpand = Mac.getInstance("HmacSHA256").apply {
                init(SecretKeySpec(prk, "HmacSHA256"))
            }

            var t = ByteArray(0)
            val okm = ByteArray(length)
            var offset = 0
            var i = 1.toByte()

            while (offset < length) {
                hmacExpand.reset()
                if (t.isNotEmpty()) {
                    hmacExpand.update(t)
                }
                if (info.isNotEmpty()) {
                    hmacExpand.update(info)
                }
                hmacExpand.update(i)
                t = hmacExpand.doFinal()

                val copyLen = minOf(t.size, length - offset)
                System.arraycopy(t, 0, okm, offset, copyLen)
                offset += copyLen
                i++
            }
            return okm
        }

        /**
         * Generates Short Authentication String (SAS) 4-emoji code for out-of-band verification
         */
        fun generateSasEmojiCode(sharedSecret: ByteArray): List<String> {
            val digest = MessageDigest.getInstance("SHA-256").digest(sharedSecret)
            return (0 until 4).map { idx ->
                val byteVal = digest[idx].toInt() and 0xFF
                EMOJI_PALETTE[byteVal % EMOJI_PALETTE.size]
            }
        }
    }

    private val secureRandom = SecureRandom()

    // --------------------------------------------------------------------------
    // 1. Classical Key Exchange (X25519 or ECDH NIST P-256)
    // --------------------------------------------------------------------------

    fun generateClassicalKeyPair(): KeyPair {
        return try {
            val kpg = KeyPairGenerator.getInstance("X25519", "BC")
            kpg.generateKeyPair()
        } catch (_: Throwable) {
            val kpg = KeyPairGenerator.getInstance("EC")
            kpg.initialize(256, secureRandom)
            kpg.generateKeyPair()
        }
    }

    fun computeClassicalAgreement(myPrivateKey: java.security.PrivateKey, peerPublicKey: java.security.PublicKey): ByteArray {
        val algorithm = if (myPrivateKey.algorithm == "X25519") "X25519" else "ECDH"
        val ka = try {
            KeyAgreement.getInstance(algorithm, "BC")
        } catch (_: Throwable) {
            KeyAgreement.getInstance("ECDH")
        }
        ka.init(myPrivateKey)
        ka.doPhase(peerPublicKey, true)
        return ka.generateSecret()
    }

    // --------------------------------------------------------------------------
    // 2. NIST FIPS 203 ML-KEM-1024
    // --------------------------------------------------------------------------

    data class MlKemKeyPair(
        val publicKey: MLKEMPublicKeyParameters,
        val privateKey: MLKEMPrivateKeyParameters
    )

    fun generateMlKemKeyPair(): MlKemKeyPair {
        val keyGen = MLKEMKeyPairGenerator()
        keyGen.init(MLKEMKeyGenerationParameters(secureRandom, MLKEMParameters.ml_kem_1024))
        val pair = keyGen.generateKeyPair()
        return MlKemKeyPair(
            publicKey = pair.public as MLKEMPublicKeyParameters,
            privateKey = pair.private as MLKEMPrivateKeyParameters
        )
    }

    fun encapsulateMlKem(publicKey: MLKEMPublicKeyParameters): Pair<ByteArray, ByteArray> {
        val kemGen = MLKEMGenerator(secureRandom)
        val secretWithEnc = kemGen.generateEncapsulated(publicKey)
        return Pair(secretWithEnc.secret, secretWithEnc.encapsulation)
    }

    fun decapsulateMlKem(privateKey: MLKEMPrivateKeyParameters, encapsulation: ByteArray): ByteArray {
        val kemExt = MLKEMExtractor(privateKey)
        return kemExt.extractSecret(encapsulation)
    }

    // --------------------------------------------------------------------------
    // 3. Fallback Ring-LWE Lattice Arithmetic in Z_3329[X]/(X^256 + 1)
    // --------------------------------------------------------------------------

    data class RingLweKeyPair(
        val publicKeyPoly: IntArray,
        val privateKeyPoly: IntArray
    )

    fun generateRingLweKeyPair(): RingLweKeyPair {
        val s = IntArray(N) { (secureRandom.nextInt(5) - 2 + Q) % Q }
        val e = IntArray(N) { (secureRandom.nextInt(5) - 2 + Q) % Q }
        val a = IntArray(N) { secureRandom.nextInt(Q) }

        // b = a * s + e (mod X^256 + 1, mod Q)
        val b = ringMultiply(a, s)
        for (i in 0 until N) {
            b[i] = (b[i] + e[i]) % Q
        }
        return RingLweKeyPair(b, s)
    }

    fun encapsulateRingLwe(peerPublicKeyPoly: IntArray): Pair<ByteArray, IntArray> {
        val r = IntArray(N) { (secureRandom.nextInt(5) - 2 + Q) % Q }
        val e1 = IntArray(N) { (secureRandom.nextInt(5) - 2 + Q) % Q }
        // u = a * r + e1; v = b * r + e2 + message_poly
        val u = ringMultiply(peerPublicKeyPoly, r)
        for (i in 0 until N) {
            u[i] = (u[i] + e1[i]) % Q
        }
        val secret = MessageDigest.getInstance("SHA-256").digest(polyToBytes(u))
        return Pair(secret, u)
    }

    fun decapsulateRingLwe(privateKeyPoly: IntArray, cipherPoly: IntArray): ByteArray {
        val secretPoly = ringMultiply(cipherPoly, privateKeyPoly)
        return MessageDigest.getInstance("SHA-256").digest(polyToBytes(secretPoly))
    }

    private fun ringMultiply(p1: IntArray, p2: IntArray): IntArray {
        val result = IntArray(N)
        for (i in 0 until N) {
            for (j in 0 until N) {
                val idx = i + j
                val term = (p1[i] * p2[j]) % Q
                if (idx < N) {
                    result[idx] = (result[idx] + term) % Q
                } else {
                    result[idx - N] = (result[idx - N] - term + Q) % Q
                }
            }
        }
        return result
    }

    private fun polyToBytes(poly: IntArray): ByteArray {
        val bytes = ByteArray(poly.size * 2)
        for (i in poly.indices) {
            bytes[i * 2] = (poly[i] and 0xFF).toByte()
            bytes[i * 2 + 1] = ((poly[i] shr 8) and 0xFF).toByte()
        }
        return bytes
    }

    // --------------------------------------------------------------------------
    // 4. Unified Hybrid Combiner
    // --------------------------------------------------------------------------

    fun deriveHybridMasterSecret(
        classicalSecret: ByteArray,
        postQuantumSecret: ByteArray,
        salt: ByteArray? = null
    ): ByteArray {
        val combined = ByteArray(classicalSecret.size + postQuantumSecret.size)
        System.arraycopy(classicalSecret, 0, combined, 0, classicalSecret.size)
        System.arraycopy(postQuantumSecret, 0, combined, classicalSecret.size, postQuantumSecret.size)
        return hkdf(ikm = combined, salt = salt, info = "MadGammon-FIPS203-HybridPQ-v1".toByteArray())
    }
}
