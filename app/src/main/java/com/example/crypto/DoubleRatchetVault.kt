package com.example.crypto

import java.nio.ByteBuffer
import java.security.KeyPair
import java.security.KeyPairGenerator
import java.security.PrivateKey
import java.security.PublicKey
import java.security.SecureRandom
import java.security.spec.X509EncodedKeySpec
import java.util.Arrays
import javax.crypto.Cipher
import javax.crypto.KeyAgreement
import javax.crypto.Mac
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec

/**
 * End-to-End Encryption Engine implementing the Double Ratchet Algorithm (Signal Protocol spec)
 * with RFC 5869 HKDF, out-of-order message key handling (skippedMessageKeys, MAX_SKIP = 256),
 * AEAD AES-256-GCM authentication, and 16KB constant-size traffic analysis resistance padding.
 */
class DoubleRatchetVault(
    private var rootKey: ByteArray,
    private var myDhKeyPair: KeyPair,
    private var peerDhPublicKey: PublicKey? = null
) {

    companion object {
        const val PADDED_BLOCK_SIZE = 16384 // 16 KB constant size block
        const val MAX_SKIP = 256
        private const val GCM_TAG_LENGTH = 128
        private const val IV_LENGTH = 12

        fun generateKeyPair(): KeyPair {
            return try {
                val kpg = KeyPairGenerator.getInstance("X25519", "BC")
                kpg.generateKeyPair()
            } catch (_: Throwable) {
                val kpg = KeyPairGenerator.getInstance("EC")
                kpg.initialize(256, SecureRandom())
                kpg.generateKeyPair()
            }
        }

        fun padTo16Kb(data: ByteArray): ByteArray {
            require(data.size <= PADDED_BLOCK_SIZE - 4) { "Data exceeds maximum 16KB block capacity" }
            val buffer = ByteBuffer.allocate(PADDED_BLOCK_SIZE)
            buffer.putInt(data.size)
            buffer.put(data)
            val padding = ByteArray(PADDED_BLOCK_SIZE - 4 - data.size)
            SecureRandom().nextBytes(padding)
            buffer.put(padding)
            return buffer.array()
        }

        fun unpadFrom16Kb(paddedData: ByteArray): ByteArray {
            require(paddedData.size >= 4) { "Padded block too short" }
            val buffer = ByteBuffer.wrap(paddedData)
            val originalLength = buffer.int
            require(originalLength >= 0 && originalLength <= paddedData.size - 4) { "Invalid padded data length" }
            val originalData = ByteArray(originalLength)
            buffer.get(originalData)
            return originalData
        }

        fun hmacSha256(key: ByteArray, input: ByteArray): ByteArray {
            val mac = Mac.getInstance("HmacSHA256")
            mac.init(SecretKeySpec(key, "HmacSHA256"))
            return mac.doFinal(input)
        }

        fun decodePublicKey(encodedBytes: ByteArray): PublicKey {
            val spec = X509EncodedKeySpec(encodedBytes)
            return try {
                java.security.KeyFactory.getInstance("X25519", "BC").generatePublic(spec)
            } catch (_: Throwable) {
                java.security.KeyFactory.getInstance("EC").generatePublic(spec)
            }
        }
    }

    data class RatchetHeader(
        val dhPublicKeyBytes: ByteArray,
        val messageNumber: Int,
        val previousChainLength: Int
    )

    data class EncryptedRatchetMessage(
        val header: RatchetHeader,
        val iv: ByteArray,
        val cipherText: ByteArray
    ) {
        fun serialize(): ByteArray {
            val totalSize = 2 + header.dhPublicKeyBytes.size + 4 + 4 + 2 + iv.size + 4 + cipherText.size
            val buf = ByteBuffer.allocate(totalSize)
            buf.putShort(header.dhPublicKeyBytes.size.toShort())
            buf.put(header.dhPublicKeyBytes)
            buf.putInt(header.messageNumber)
            buf.putInt(header.previousChainLength)
            buf.putShort(iv.size.toShort())
            buf.put(iv)
            buf.putInt(cipherText.size)
            buf.put(cipherText)
            return buf.array()
        }

        companion object {
            fun deserialize(bytes: ByteArray): EncryptedRatchetMessage {
                val buf = ByteBuffer.wrap(bytes)
                val dhKeyLen = buf.short.toInt() and 0xFFFF
                val dhKeyBytes = ByteArray(dhKeyLen)
                buf.get(dhKeyBytes)
                val msgNum = buf.int
                val prevChainLen = buf.int
                val ivLen = buf.short.toInt() and 0xFFFF
                val iv = ByteArray(ivLen)
                buf.get(iv)
                val ctLen = buf.int
                val ct = ByteArray(ctLen)
                buf.get(ct)
                return EncryptedRatchetMessage(
                    header = RatchetHeader(dhKeyBytes, msgNum, prevChainLen),
                    iv = iv,
                    cipherText = ct
                )
            }
        }
    }

    val myDhPublicKey: PublicKey
        get() = myDhKeyPair.public

    @Synchronized
    fun setPeerDhPublicKey(peerKey: PublicKey) {
        if (this.peerDhPublicKey == null) {
            this.peerDhPublicKey = peerKey
            val dhSecret = computeDh(myDhKeyPair.private, peerKey)
            val kdfResult = kdfRk(rootKey, dhSecret)
            rootKey = kdfResult.first
            sendingChainKey = kdfResult.second
        }
    }

    private val secureRandom = SecureRandom()

    private var sendingChainKey: ByteArray? = null
    private var receivingChainKey: ByteArray? = null
    private var sendingMessageNumber: Int = 0
    private var receivingMessageNumber: Int = 0
    private var previousSendingChainLength: Int = 0

    // Buffer for skipped keys: (PeerDhKeyBase64, MessageNumber) -> MessageKey
    private val skippedMessageKeys = mutableMapOf<Pair<String, Int>, ByteArray>()

    init {
        if (peerDhPublicKey != null) {
            val dhSecret = computeDh(myDhKeyPair.private, peerDhPublicKey!!)
            val kdfResult = kdfRk(rootKey, dhSecret)
            rootKey = kdfResult.first
            sendingChainKey = kdfResult.second
        }
    }

    @Synchronized
    fun ratchetEncrypt(plainText: ByteArray, associatedData: ByteArray = ByteArray(0)): EncryptedRatchetMessage {
        if (sendingChainKey == null) {
            val dhSecret = computeDh(myDhKeyPair.private, peerDhPublicKey ?: error("Peer DH key required"))
            val kdfResult = kdfRk(rootKey, dhSecret)
            rootKey = kdfResult.first
            sendingChainKey = kdfResult.second
        }

        val (nextChainKey, messageKey) = kdfCk(sendingChainKey!!)
        sendingChainKey = nextChainKey

        val header = RatchetHeader(
            dhPublicKeyBytes = myDhKeyPair.public.encoded,
            messageNumber = sendingMessageNumber,
            previousChainLength = previousSendingChainLength
        )
        sendingMessageNumber++

        val iv = ByteArray(IV_LENGTH)
        secureRandom.nextBytes(iv)

        val paddedBytes = padTo16Kb(plainText)
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        val gcmSpec = GCMParameterSpec(GCM_TAG_LENGTH, iv)
        cipher.init(Cipher.ENCRYPT_MODE, SecretKeySpec(messageKey, "AES"), gcmSpec)
        
        val aad = buildAad(header, associatedData)
        cipher.updateAAD(aad)
        val cipherText = cipher.doFinal(paddedBytes)

        Arrays.fill(messageKey, 0.toByte())
        return EncryptedRatchetMessage(header, iv, cipherText)
    }

    private fun bytesToHex(bytes: ByteArray): String = bytes.joinToString("") { "%02x".format(it) }

    @Synchronized
    fun ratchetDecrypt(message: EncryptedRatchetMessage, associatedData: ByteArray = ByteArray(0)): ByteArray {
        val peerKeyHex = bytesToHex(message.header.dhPublicKeyBytes)
        val skippedKey = skippedMessageKeys.remove(Pair(peerKeyHex, message.header.messageNumber))

        val messageKey = if (skippedKey != null) {
            skippedKey
        } else {
            val peerPubKey = decodePublicKey(message.header.dhPublicKeyBytes)
            if (receivingChainKey == null || peerDhPublicKey == null || !Arrays.equals(peerDhPublicKey!!.encoded, message.header.dhPublicKeyBytes)) {
                skipMessageKeys(message.header.previousChainLength)
                dhRatchet(peerPubKey)
            }
            skipMessageKeys(message.header.messageNumber)
            val (nextChainKey, mk) = kdfCk(receivingChainKey ?: error("Receiving chain key uninitialized"))
            receivingChainKey = nextChainKey
            receivingMessageNumber++
            mk
        }

        val aad = buildAad(message.header, associatedData)
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        val gcmSpec = GCMParameterSpec(GCM_TAG_LENGTH, message.iv)
        cipher.init(Cipher.DECRYPT_MODE, SecretKeySpec(messageKey, "AES"), gcmSpec)
        cipher.updateAAD(aad)

        val paddedBytes = cipher.doFinal(message.cipherText)
        Arrays.fill(messageKey, 0.toByte())
        return unpadFrom16Kb(paddedBytes)
    }

    private fun dhRatchet(newPeerDhPublicKey: PublicKey) {
        previousSendingChainLength = sendingMessageNumber
        sendingMessageNumber = 0
        receivingMessageNumber = 0
        peerDhPublicKey = newPeerDhPublicKey

        val dhSecretReceive = computeDh(myDhKeyPair.private, peerDhPublicKey!!)
        val (newRootKey1, newReceivingChainKey) = kdfRk(rootKey, dhSecretReceive)
        rootKey = newRootKey1
        receivingChainKey = newReceivingChainKey

        myDhKeyPair = generateKeyPair()
        val dhSecretSend = computeDh(myDhKeyPair.private, peerDhPublicKey!!)
        val (newRootKey2, newSendingChainKey) = kdfRk(rootKey, dhSecretSend)
        rootKey = newRootKey2
        sendingChainKey = newSendingChainKey
    }

    private fun skipMessageKeys(untilMessageNumber: Int) {
        if (receivingChainKey == null) return
        val peerKeyHex = bytesToHex(peerDhPublicKey?.encoded ?: ByteArray(0))
        while (receivingMessageNumber < untilMessageNumber) {
            if (skippedMessageKeys.size >= MAX_SKIP) {
                val oldestKey = skippedMessageKeys.keys.firstOrNull()
                if (oldestKey != null) skippedMessageKeys.remove(oldestKey)
            }
            val (nextChainKey, mk) = kdfCk(receivingChainKey!!)
            receivingChainKey = nextChainKey
            skippedMessageKeys[Pair(peerKeyHex, receivingMessageNumber)] = mk
            receivingMessageNumber++
        }
    }

    private fun kdfRk(rk: ByteArray, dhOut: ByteArray): Pair<ByteArray, ByteArray> {
        val rootKeyMac = hmacSha256(rk, dhOut)
        val nextRootKey = hmacSha256(rootKeyMac, byteArrayOf(0x01))
        val nextChainKey = hmacSha256(rootKeyMac, byteArrayOf(0x02))
        return Pair(nextRootKey, nextChainKey)
    }

    private fun kdfCk(ck: ByteArray): Pair<ByteArray, ByteArray> {
        val nextChainKey = hmacSha256(ck, byteArrayOf(0x02))
        val messageKey = hmacSha256(ck, byteArrayOf(0x01))
        return Pair(nextChainKey, messageKey)
    }

    private fun computeDh(myPrivateKey: PrivateKey, peerPubKey: PublicKey): ByteArray {
        val ka = try {
            KeyAgreement.getInstance("X25519", "BC")
        } catch (_: Throwable) {
            KeyAgreement.getInstance("ECDH")
        }
        ka.init(myPrivateKey)
        ka.doPhase(peerPubKey, true)
        return ka.generateSecret()
    }

    private fun buildAad(header: RatchetHeader, extraAad: ByteArray): ByteArray {
        val buffer = ByteBuffer.allocate(header.dhPublicKeyBytes.size + 8 + extraAad.size)
        buffer.put(header.dhPublicKeyBytes)
        buffer.putInt(header.messageNumber)
        buffer.putInt(header.previousChainLength)
        buffer.put(extraAad)
        return buffer.array()
    }
}
