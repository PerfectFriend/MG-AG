package com.example.data

import android.util.Log
import kotlinx.coroutines.*
import java.io.OutputStream
import java.security.SecureRandom
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicLong

/**
 * Defensive Traffic Shaping and Chaffing Engine.
 *
 * Protects against Side-Channel Traffic Analysis and Correlation Attacks:
 * Sophisticated deep packet inspection (DPI) can identify active conversation bursts
 * or keystroke rhythms by observing packet timing and sizes even over Tor / E2EE.
 *
 * This engine generates randomized dummy frames (chaff) using a Poisson-like distribution,
 * flattening transmission peaks into a continuous, indistinguishable entropy stream.
 */
class TrafficChaffingEngine(
    private val scope: CoroutineScope,
    private val sendRawBytes: suspend (ByteArray) -> Unit
) {
    companion object {
        private const val TAG = "TrafficChaffingEngine"
        val MAGIC_CHAFF_HEADER = byteArrayOf(0xDF.toByte(), 0xCA.toByte(), 0xFF.toByte(), 0x01.toByte())
        const val STANDARD_CHAFF_SIZE = 512
        const val MIN_INTERVAL_MS = 4000L
        const val MAX_JITTER_MS = 6000L
    }

    private val random = SecureRandom()
    private val isChaffingActive = AtomicBoolean(false)
    private val lastRealPacketTime = AtomicLong(System.currentTimeMillis())
    private var chaffingJob: Job? = null

    /**
     * Checks if a received packet payload is a dummy chaff packet and should be discarded.
     */
    fun isChaffPacket(payload: ByteArray): Boolean {
        if (payload.size < MAGIC_CHAFF_HEADER.size) return false
        for (i in MAGIC_CHAFF_HEADER.indices) {
            if (payload[i] != MAGIC_CHAFF_HEADER[i]) return false
        }
        return true
    }

    /**
     * Notifies the engine that a real message was transmitted, resetting the quiet period.
     */
    fun notifyRealTrafficSent() {
        lastRealPacketTime.set(System.currentTimeMillis())
    }

    /**
     * Starts background traffic masking.
     */
    fun start() {
        if (isChaffingActive.getAndSet(true)) return

        chaffingJob = scope.launch(Dispatchers.IO) {
            Log.i(TAG, "Traffic Chaffing / Masking engine started (Poisson jitter defense active)")
            while (isActive && isChaffingActive.get()) {
                val nextDelay = MIN_INTERVAL_MS + (random.nextFloat() * MAX_JITTER_MS).toLong()
                delay(nextDelay)

                val elapsedSinceReal = System.currentTimeMillis() - lastRealPacketTime.get()
                // Only send chaff dummy if channel has been quiet for a moment
                if (elapsedSinceReal >= MIN_INTERVAL_MS) {
                    try {
                        val chaffPacket = generateChaffPacket()
                        sendRawBytes(chaffPacket)
                    } catch (e: Exception) {
                        Log.d(TAG, "Chaff transmission deferred: ${e.message}")
                    }
                }
            }
        }
    }

    /**
     * Stops traffic masking.
     */
    fun stop() {
        isChaffingActive.set(false)
        chaffingJob?.cancel()
        chaffingJob = null
        Log.i(TAG, "Traffic Chaffing stopped")
    }

    private fun generateChaffPacket(): ByteArray {
        val chaff = ByteArray(STANDARD_CHAFF_SIZE)
        // Insert magic header
        System.arraycopy(MAGIC_CHAFF_HEADER, 0, chaff, 0, MAGIC_CHAFF_HEADER.size)
        // Fill remainder with cryptographically secure random bytes
        val randomPadding = ByteArray(STANDARD_CHAFF_SIZE - MAGIC_CHAFF_HEADER.size)
        random.nextBytes(randomPadding)
        System.arraycopy(randomPadding, 0, chaff, MAGIC_CHAFF_HEADER.size, randomPadding.size)
        return chaff
    }
}
