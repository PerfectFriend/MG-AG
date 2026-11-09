package com.example.data

import android.util.Log
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.Channel
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.net.InetSocketAddress
import java.net.Socket
import java.nio.charset.StandardCharsets
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Happy Eyeballs v2 (RFC 8305) Multi-Path Transport Racing Engine.
 *
 * Rather than waiting for a blocked or censored transport to time out (which causes 15-30s UI hangs),
 * this engine launches parallel/staggered connection attempts across:
 * 1. Direct TCP (fastest when unblocked, T = 0ms)
 * 2. V2Ray / Xray Reality SOCKS5 (:10808, anti-DPI, T = 250ms)
 * 3. Tor SOCKS5 (:9050, high-anonymity onion routing, T = 500ms)
 *
 * The fastest working transport wins the race, and losing sockets are immediately
 * closed to conserve network sockets and battery.
 */
object MultiPathTransportRacer {

    private const val TAG = "MultiPathTransportRacer"

    enum class TransportType {
        DIRECT,
        V2RAY_REALITY,
        TOR_ONION
    }

    data class RaceResult(
        val socket: Socket,
        val transportType: TransportType,
        val rttMillis: Long
    )

    /**
     * Races multiple transport pathways to connect to [targetHost]:[targetPort].
     */
    suspend fun raceConnection(
        targetHost: String,
        targetPort: Int,
        v2raySocksPort: Int = 10808,
        torSocksPort: Int = 9050,
        isTorOnly: Boolean = false,
        isStrictOnionOnly: Boolean = false,
        staggerDelayMs: Long = 250L,
        overallTimeoutMs: Long = 12000L
    ): RaceResult = withContext(Dispatchers.IO) {
        val startTime = System.currentTimeMillis()

        if (isStrictOnionOnly && !targetHost.endsWith(".onion") && targetHost != "127.0.0.1" && targetHost != "localhost") {
            Log.w(TAG, "🛑 [STRICT ONION-ONLY POLICY] Connection blocked to clearnet host: $targetHost")
            throw IOException("BLOCKED: Strict Onion-Only policy rejects clearnet host $targetHost")
        }

        // Fast-path: .onion domains MUST use Tor for DNS anonymity and routing
        if (targetHost.endsWith(".onion") || isTorOnly) {
            Log.i(TAG, "Target is Onion v3 or Tor-enforced ($targetHost), routing strictly via Tor SOCKS5 :$torSocksPort")
            val torSocket = connectSocks5("127.0.0.1", torSocksPort, targetHost, targetPort, timeoutMs = overallTimeoutMs.toInt())
            val rtt = System.currentTimeMillis() - startTime
            return@withContext RaceResult(torSocket, TransportType.TOR_ONION, rtt)
        }

        val resultChannel = Channel<RaceResult>(capacity = Channel.CONFLATED)
        val raceWon = AtomicBoolean(false)
        val candidateErrors = mutableListOf<String>()

        val raceScope = CoroutineScope(Dispatchers.IO + SupervisorJob())

        try {
            withTimeout(overallTimeoutMs) {
                // Candidate 1: Direct TCP (T = 0ms)
                raceScope.launch {
                    try {
                        val candidateStart = System.currentTimeMillis()
                        val socket = Socket()
                        socket.tcpNoDelay = true
                        socket.connect(InetSocketAddress(targetHost, targetPort), 4000)

                        if (raceWon.compareAndSet(false, true)) {
                            val rtt = System.currentTimeMillis() - candidateStart
                            Log.i(TAG, "DIRECT connection won race in ${rtt}ms to $targetHost:$targetPort")
                            resultChannel.send(RaceResult(socket, TransportType.DIRECT, rtt))
                        } else {
                            socket.close()
                        }
                    } catch (e: Exception) {
                        synchronized(candidateErrors) {
                            candidateErrors.add("Direct: ${e.message ?: e.javaClass.simpleName}")
                        }
                    }
                }

                // Candidate 2: V2Ray / Xray Reality SOCKS5 (T = 250ms)
                raceScope.launch {
                    delay(staggerDelayMs)
                    if (raceWon.get()) return@launch
                    try {
                        val candidateStart = System.currentTimeMillis()
                        val socket = connectSocks5("127.0.0.1", v2raySocksPort, targetHost, targetPort, timeoutMs = 5000)

                        if (raceWon.compareAndSet(false, true)) {
                            val rtt = System.currentTimeMillis() - candidateStart
                            Log.i(TAG, "V2RAY_REALITY won race in ${rtt}ms to $targetHost:$targetPort")
                            resultChannel.send(RaceResult(socket, TransportType.V2RAY_REALITY, rtt))
                        } else {
                            socket.close()
                        }
                    } catch (e: Exception) {
                        synchronized(candidateErrors) {
                            candidateErrors.add("V2Ray: ${e.message ?: e.javaClass.simpleName}")
                        }
                    }
                }

                // Candidate 3: Tor SOCKS5 (T = 500ms)
                raceScope.launch {
                    delay(staggerDelayMs * 2)
                    if (raceWon.get()) return@launch
                    try {
                        val candidateStart = System.currentTimeMillis()
                        val socket = connectSocks5("127.0.0.1", torSocksPort, targetHost, targetPort, timeoutMs = 7000)

                        if (raceWon.compareAndSet(false, true)) {
                            val rtt = System.currentTimeMillis() - candidateStart
                            Log.i(TAG, "TOR_ONION won race in ${rtt}ms to $targetHost:$targetPort")
                            resultChannel.send(RaceResult(socket, TransportType.TOR_ONION, rtt))
                        } else {
                            socket.close()
                        }
                    } catch (e: Exception) {
                        synchronized(candidateErrors) {
                            candidateErrors.add("Tor: ${e.message ?: e.javaClass.simpleName}")
                        }
                    }
                }

                // Wait for the winning candidate
                val winner = resultChannel.receive()
                winner
            }
        } catch (e: Exception) {
            val failureSummary = synchronized(candidateErrors) { candidateErrors.joinToString("; ") }
            Log.w(TAG, "Happy Eyeballs race failed for $targetHost:$targetPort after ${System.currentTimeMillis() - startTime}ms. Errors: $failureSummary")
            throw IOException("MultiPathTransportRacer failed all candidates: $failureSummary", e)
        } finally {
            raceScope.cancel()
        }
    }

    /**
     * RFC 1928 Compliant SOCKS5 connection helper.
     */
    fun connectSocks5(
        proxyHost: String,
        proxyPort: Int,
        targetHost: String,
        targetPort: Int,
        timeoutMs: Int = 10000
    ): Socket {
        val socket = Socket()
        socket.tcpNoDelay = true
        socket.connect(InetSocketAddress(proxyHost, proxyPort), timeoutMs)
        socket.soTimeout = timeoutMs

        val outStream: OutputStream = socket.getOutputStream()
        val inStream: InputStream = socket.getInputStream()

        // 1. Handshake
        outStream.write(byteArrayOf(0x05, 0x01, 0x00))
        outStream.flush()

        val handshakeResp = ByteArray(2)
        readExact(inStream, handshakeResp)
        if (handshakeResp[0] != 0x05.toByte() || handshakeResp[1] != 0x00.toByte()) {
            socket.close()
            throw IOException("SOCKS5 handshake failed: ${handshakeResp.joinToString { it.toString() }}")
        }

        // 2. Connect command with DOMAINNAME (0x03) to ensure remote DNS resolution
        val hostBytes = targetHost.toByteArray(StandardCharsets.US_ASCII)
        if (hostBytes.size > 255) {
            socket.close()
            throw IllegalArgumentException("Host name too long for SOCKS5: $targetHost")
        }

        val requestHeader = byteArrayOf(0x05, 0x01, 0x00, 0x03)
        outStream.write(requestHeader)
        outStream.write(hostBytes.size)
        outStream.write(hostBytes)
        outStream.write((targetPort shr 8) and 0xFF)
        outStream.write(targetPort and 0xFF)
        outStream.flush()

        // 3. Response
        val responseHeader = ByteArray(4)
        readExact(inStream, responseHeader)
        if (responseHeader[0] != 0x05.toByte()) {
            socket.close()
            throw IOException("Invalid SOCKS5 response version: ${responseHeader[0]}")
        }
        val status = responseHeader[1]
        if (status != 0x00.toByte()) {
            socket.close()
            throw IOException("SOCKS5 proxy rejected connection with status code: $status")
        }

        // Discard bound address info from response
        val addrType = responseHeader[3].toInt()
        when (addrType) {
            0x01 -> readExact(inStream, ByteArray(4 + 2)) // IPv4 + port
            0x03 -> {
                val len = inStream.read()
                if (len > 0) readExact(inStream, ByteArray(len + 2)) // domain + port
            }
            0x04 -> readExact(inStream, ByteArray(16 + 2)) // IPv6 + port
        }

        return socket
    }

    private fun readExact(inputStream: InputStream, buffer: ByteArray) {
        var offset = 0
        while (offset < buffer.size) {
            val read = inputStream.read(buffer, offset, buffer.size - offset)
            if (read < 0) throw IOException("Unexpected EOF while reading SOCKS5 response")
            offset += read
        }
    }
}
