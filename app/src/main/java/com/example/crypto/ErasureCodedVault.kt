package com.example.crypto

import java.security.MessageDigest
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.PBEKeySpec
import javax.crypto.spec.SecretKeySpec
import kotlin.math.min

/**
 * AES-256-GCM payload encryption with Reed-Solomon GF(2^8) erasure coding.
 *
 * There is no zero-knowledge proof anywhere in this class; the previous name
 * claimed one. Confidentiality comes from the key the caller supplies.
 *
 * Implements:
 * 1. PBKDF2-HMAC-SHA256 Master Key Derivation from BIP-39 Seed Phrase.
 * 2. Authenticated AES-256-GCM Payload Encryption (12-byte IV, 128-bit Auth Tag).
 * 3. Systematic Reed-Solomon Erasure Coding over Galois Field GF(2^8) (polynomial 0x11D).
 * 4. Gauss-Jordan Matrix Inversion in GF(2^8) allowing exact byte-for-byte file reconstruction
 *    from ANY K out of (K + M) shards (even if multiple primary data shards are lost).
 */
class ErasureCodedVault {

    data class Shard(
        val index: Int,
        val isParity: Boolean,
        val totalDataShards: Int,
        val totalParityShards: Int,
        val originalFileLength: Int,
        val data: ByteArray,
        val shardHashHex: String
    ) {
        fun verifyIntegrity(): Boolean = sha256Hex(data).equals(shardHashHex, ignoreCase = true)
    }

    data class VaultFile(
        val fileName: String,
        val originalSize: Int,
        val shards: List<Shard>,
        val contentHashHex: String
    )

    companion object {
        private const val CIPHER_ALGO = "AES/GCM/NoPadding"
        private const val GCM_IV_LEN = 12
        private const val GCM_TAG_BITS = 128
        private const val PBKDF2_ALGO = "PBKDF2WithHmacSHA256"
        private const val PBKDF2_ITERATIONS = 10000

        // Galois Field GF(2^8) primitive polynomial: x^8 + x^4 + x^3 + x^2 + 1 (0x11D = 285)
        private const val GF_POLY = 0x11D
        private val EXP_TABLE = IntArray(512)
        private val LOG_TABLE = IntArray(256)

        init {
            var x = 1
            for (i in 0 until 255) {
                EXP_TABLE[i] = x
                LOG_TABLE[x] = i
                x = x shl 1
                if ((x and 0x100) != 0) {
                    x = x xor GF_POLY
                }
            }
            for (i in 255 until 512) {
                EXP_TABLE[i] = EXP_TABLE[i - 255]
            }
            LOG_TABLE[0] = 0
        }

        fun gfAdd(a: Int, b: Int): Int = (a xor b) and 0xFF

        fun gfMul(a: Int, b: Int): Int {
            val aa = a and 0xFF
            val bb = b and 0xFF
            if (aa == 0 || bb == 0) return 0
            return EXP_TABLE[LOG_TABLE[aa] + LOG_TABLE[bb]]
        }

        fun gfDiv(a: Int, b: Int): Int {
            val aa = a and 0xFF
            val bb = b and 0xFF
            if (bb == 0) throw ArithmeticException("Division by zero in GF(2^8)")
            if (aa == 0) return 0
            return EXP_TABLE[LOG_TABLE[aa] + 255 - LOG_TABLE[bb]]
        }

        fun gfInv(a: Int): Int {
            val aa = a and 0xFF
            if (aa == 0) throw ArithmeticException("Zero has no inverse in GF(2^8)")
            return EXP_TABLE[255 - LOG_TABLE[aa]]
        }

        fun gfPow(a: Int, n: Int): Int {
            val aa = a and 0xFF
            if (n == 0) return 1
            if (aa == 0) return 0
            val logA = LOG_TABLE[aa]
            val exp = (logA * n) % 255
            return EXP_TABLE[exp]
        }

        /**
         * Builds a Systematic Reed-Solomon Encoding Matrix of size (dataShards + parityShards) x dataShards.
         * Top dataShards rows = Identity Matrix I_K.
         * Bottom parityShards rows = Cauchy / Vandermonde matrix in GF(2^8) guaranteed to be invertible
         * for any K-row subset.
         */
        fun buildSystematicMatrix(dataShards: Int, parityShards: Int): Array<IntArray> {
            val totalShards = dataShards + parityShards
            val matrix = Array(totalShards) { IntArray(dataShards) }

            // Top K rows: Identity matrix
            for (r in 0 until dataShards) {
                matrix[r][r] = 1
            }

            // Bottom M rows: Cauchy matrix C[p, d] = 1 / (x_p xor y_d) in GF(2^8)
            // Every square submatrix of a Cauchy matrix over GF(2^8) is strictly non-singular!
            for (p in 0 until parityShards) {
                val xp = dataShards + p
                for (d in 0 until dataShards) {
                    val yd = d
                    matrix[dataShards + p][d] = gfInv(xp xor yd)
                }
            }
            return matrix
        }

        /**
         * Inverts an N x N square matrix over GF(2^8) using Gauss-Jordan elimination.
         */
        fun invertMatrixGF256(matrix: Array<IntArray>): Array<IntArray> {
            val n = matrix.size
            val work = Array(n) { r ->
                IntArray(2 * n) { c ->
                    if (c < n) matrix[r][c] and 0xFF
                    else if (c - n == r) 1 else 0
                }
            }

            for (col in 0 until n) {
                // Find non-zero pivot in column
                var pivotRow = col
                while (pivotRow < n && work[pivotRow][col] == 0) {
                    pivotRow++
                }
                if (pivotRow == n) {
                    throw IllegalStateException("Matrix is singular in GF(2^8) at column $col")
                }

                // Swap rows if needed
                if (pivotRow != col) {
                    val tmp = work[col]
                    work[col] = work[pivotRow]
                    work[pivotRow] = tmp
                }

                // Normalize pivot row so pivot becomes 1
                val pivotVal = work[col][col]
                if (pivotVal != 1) {
                    val invPivot = gfInv(pivotVal)
                    for (c in 0 until 2 * n) {
                        work[col][c] = gfMul(work[col][c], invPivot)
                    }
                }

                // Eliminate all other rows in this column
                for (row in 0 until n) {
                    if (row != col) {
                        val factor = work[row][col]
                        if (factor != 0) {
                            for (c in 0 until 2 * n) {
                                work[row][c] = gfAdd(work[row][c], gfMul(factor, work[col][c]))
                            }
                        }
                    }
                }
            }

            return Array(n) { r ->
                IntArray(n) { c -> work[r][n + c] }
            }
        }

        fun deriveVaultKey(bip39SeedHex: String, salt: String = "SimpleX-ZK-Vault-Salt-v2"): ByteArray {
            return try {
                val spec = PBEKeySpec(
                    bip39SeedHex.toCharArray(),
                    salt.toByteArray(Charsets.UTF_8),
                    PBKDF2_ITERATIONS,
                    256
                )
                val skf = SecretKeyFactory.getInstance(PBKDF2_ALGO)
                skf.generateSecret(spec).encoded
            } catch (_: Exception) {
                val md = MessageDigest.getInstance("SHA-256")
                md.update(bip39SeedHex.toByteArray(Charsets.UTF_8))
                md.update(salt.toByteArray(Charsets.UTF_8))
                md.digest()
            }
        }

        fun bytesToHex(bytes: ByteArray): String =
            bytes.joinToString("") { "%02x".format(it) }

        fun sha256Hex(bytes: ByteArray): String {
            val md = MessageDigest.getInstance("SHA-256")
            return bytesToHex(md.digest(bytes))
        }
    }

    /**
     * Encrypts raw bytes with derived master key using AES-256-GCM (12-byte random IV + 128-bit auth tag).
     */
    fun encryptBytes(plainBytes: ByteArray, key: ByteArray): ByteArray {
        val iv = ByteArray(GCM_IV_LEN)
        SecureRandom().nextBytes(iv)
        val cipher = Cipher.getInstance(CIPHER_ALGO)
        cipher.init(Cipher.ENCRYPT_MODE, SecretKeySpec(key, "AES"), GCMParameterSpec(GCM_TAG_BITS, iv))
        val encrypted = cipher.doFinal(plainBytes)
        val result = ByteArray(iv.size + encrypted.size)
        System.arraycopy(iv, 0, result, 0, iv.size)
        System.arraycopy(encrypted, 0, result, iv.size, encrypted.size)
        return result
    }

    /**
     * Decrypts AES-256-GCM encrypted bytes with master key, reading the 12-byte IV prefix.
     */
    fun decryptBytes(cipherBytes: ByteArray, key: ByteArray): ByteArray {
        if (cipherBytes.size < GCM_IV_LEN + 16) throw IllegalArgumentException("Ciphertext too short")
        val iv = ByteArray(GCM_IV_LEN)
        System.arraycopy(cipherBytes, 0, iv, 0, GCM_IV_LEN)
        val cipher = Cipher.getInstance(CIPHER_ALGO)
        cipher.init(Cipher.DECRYPT_MODE, SecretKeySpec(key, "AES"), GCMParameterSpec(GCM_TAG_BITS, iv))
        return cipher.doFinal(cipherBytes, GCM_IV_LEN, cipherBytes.size - GCM_IV_LEN)
    }

    /**
     * Splits an encrypted byte array into K data shards + M parity shards
     * using Systematic Cauchy Reed-Solomon Erasure Coding over GF(2^8).
     */
    fun shardFile(
        fileName: String,
        encryptedBytes: ByteArray,
        dataShardsCount: Int = 4,
        parityShardsCount: Int = 2
    ): VaultFile {
        val originalLen = encryptedBytes.size
        val shardSize = ((originalLen + dataShardsCount - 1) / dataShardsCount).coerceAtLeast(1)
        val matrix = buildSystematicMatrix(dataShardsCount, parityShardsCount)

        val dataShards = ArrayList<ByteArray>(dataShardsCount)
        for (i in 0 until dataShardsCount) {
            val shard = ByteArray(shardSize)
            val srcPos = i * shardSize
            if (srcPos < originalLen) {
                val copyLen = min(shardSize, originalLen - srcPos)
                System.arraycopy(encryptedBytes, srcPos, shard, 0, copyLen)
            }
            dataShards.add(shard)
        }

        // Compute Parity Shards via GF(2^8) Matrix Multiplication
        val parityShards = ArrayList<ByteArray>(parityShardsCount)
        for (p in 0 until parityShardsCount) {
            val parity = ByteArray(shardSize)
            val matrixRow = matrix[dataShardsCount + p]
            for (d in 0 until dataShardsCount) {
                val coeff = matrixRow[d]
                val dShard = dataShards[d]
                for (i in 0 until shardSize) {
                    val prod = gfMul(coeff, dShard[i].toInt() and 0xFF)
                    parity[i] = (parity[i].toInt() xor prod).toByte()
                }
            }
            parityShards.add(parity)
        }

        val allShards = mutableListOf<Shard>()
        for (i in 0 until dataShardsCount) {
            val sData = dataShards[i]
            allShards.add(
                Shard(
                    index = i,
                    isParity = false,
                    totalDataShards = dataShardsCount,
                    totalParityShards = parityShardsCount,
                    originalFileLength = originalLen,
                    data = sData,
                    shardHashHex = sha256Hex(sData)
                )
            )
        }
        for (p in 0 until parityShardsCount) {
            val sData = parityShards[p]
            allShards.add(
                Shard(
                    index = dataShardsCount + p,
                    isParity = true,
                    totalDataShards = dataShardsCount,
                    totalParityShards = parityShardsCount,
                    originalFileLength = originalLen,
                    data = sData,
                    shardHashHex = sha256Hex(sData)
                )
            )
        }

        return VaultFile(
            fileName = fileName,
            originalSize = originalLen,
            shards = allShards,
            contentHashHex = sha256Hex(encryptedBytes)
        )
    }

    /**
     * Reconstructs original encrypted bytes from ANY K valid shards (data or parity)
     * using GF(2^8) Gauss-Jordan Matrix Inversion.
     */
    fun reconstructFile(availableShards: List<Shard>): ByteArray {
        if (availableShards.isEmpty()) throw IllegalArgumentException("No shards provided")
        val validShards = availableShards
            .filter { it.verifyIntegrity() }
            .distinctBy { it.index }
            .sortedBy { it.index }

        val meta = validShards.firstOrNull()
            ?: throw IllegalStateException("All provided shards failed SHA-256 integrity verification")
        val dataCount = meta.totalDataShards
        val parityCount = meta.totalParityShards
        val origLen = meta.originalFileLength
        val shardSize = meta.data.size

        if (validShards.size < dataCount) {
            throw IllegalStateException("Insufficient valid shards: have ${validShards.size}, need at least $dataCount")
        }

        // Take the first K available valid shards (can be a mix of data and parity shards)
        val selectedShards = validShards.take(dataCount)
        val fullMatrix = buildSystematicMatrix(dataCount, parityCount)

        // Extract the K x K submatrix corresponding to the surviving shard indices
        val subMatrix = Array(dataCount) { r ->
            fullMatrix[selectedShards[r].index].clone()
        }

        // Invert the K x K submatrix in GF(2^8)
        val decodeMatrix = invertMatrixGF256(subMatrix)

        // Recover all K original data shards by multiplying decodeMatrix * selectedShards
        val recoveredDataShards = Array(dataCount) { ByteArray(shardSize) }
        for (d in 0 until dataCount) {
            val row = decodeMatrix[d]
            val targetShard = recoveredDataShards[d]
            for (s in 0 until dataCount) {
                val coeff = row[s]
                if (coeff == 0) continue
                val inputShard = selectedShards[s].data
                if (coeff == 1) {
                    for (i in 0 until shardSize) {
                        targetShard[i] = (targetShard[i].toInt() xor (inputShard[i].toInt() and 0xFF)).toByte()
                    }
                } else {
                    for (i in 0 until shardSize) {
                        val prod = gfMul(coeff, inputShard[i].toInt() and 0xFF)
                        targetShard[i] = (targetShard[i].toInt() xor prod).toByte()
                    }
                }
            }
        }

        // Reassemble original byte array of length origLen
        val reconstructed = ByteArray(origLen)
        for (d in 0 until dataCount) {
            val destPos = d * shardSize
            if (destPos < origLen) {
                val copyLen = min(shardSize, origLen - destPos)
                System.arraycopy(recoveredDataShards[d], 0, reconstructed, destPos, copyLen)
            }
        }
        return reconstructed
    }
}
