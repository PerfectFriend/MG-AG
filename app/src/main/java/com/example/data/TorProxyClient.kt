package com.example.data

import java.io.InputStream
import java.io.OutputStream
import java.net.InetSocketAddress
import java.net.Socket
import java.nio.charset.StandardCharsets

/**
 * A pure Kotlin implementation of a SOCKS5 client connector.
 * This allows establishing a real TCP connection through the Tor daemon's local SOCKS5 port (default 9050).
 * Bypasses the need for heavy external CLI binaries (like Xray/V2Ray) and avoids Android's SELinux ProcessBuilder restrictions.
 */
class TorProxyClient(
    private val torHost: String = "127.0.0.1",
    private val torPort: Int = 9050
) {

    /**
     * Establishes a TCP connection to a target address (e.g., an .onion domain or normal IP/domain)
     * routing all packets securely through the Tor SOCKS5 proxy.
     */
    fun connectThroughTor(targetHost: String, targetPort: Int, timeoutMs: Int = 15000): Socket {
        val socket = Socket()
        socket.connect(InetSocketAddress(torHost, torPort), timeoutMs)
        
        val outputStream = socket.getOutputStream()
        val inputStream = socket.getInputStream()

        // 1. SOCKS5 Handshake
        // Byte 1: SOCKS Version (0x05)
        // Byte 2: Number of authentication methods supported (1)
        // Byte 3: Auth method - 0x00 (No authentication required)
        outputStream.write(byteArrayOf(0x05.toByte(), 0x01.toByte(), 0x00.toByte()))
        outputStream.flush()

        // Read response from SOCKS server (2 bytes)
        // Response Byte 1: Version (must be 0x05)
        // Response Byte 2: Chosen method (0x00 is No Auth, 0xFF is No acceptable methods)
        val handshakeResponse = ByteArray(2)
        val bytesRead = inputStream.read(handshakeResponse)
        if (bytesRead < 2 || handshakeResponse[0] != 0x05.toByte()) {
            socket.close()
            throw IllegalStateException("Tor SOCKS5 Handshake failed: Invalid response version")
        }
        if (handshakeResponse[1] != 0x00.toByte()) {
            socket.close()
            throw IllegalStateException("Tor SOCKS5 Handshake failed: Authentication required or unsupported")
        }

        // 2. Send Connection Request
        // Byte 1: SOCKS Version (0x05)
        // Byte 2: Command (0x01 = CONNECT)
        // Byte 3: Reserved (0x00)
        // Byte 4: Address Type (0x03 = Domain Name / Onion address)
        val requestHeader = byteArrayOf(0x05.toByte(), 0x01.toByte(), 0x00.toByte(), 0x03.toByte())
        outputStream.write(requestHeader)

        // Send Domain length + Domain name bytes
        val hostBytes = targetHost.toByteArray(StandardCharsets.US_ASCII)
        if (hostBytes.size > 255) {
            socket.close()
            throw IllegalArgumentException("Target host name is too long for SOCKS5")
        }
        outputStream.write(hostBytes.size)
        outputStream.write(hostBytes)

        // Send Port (2 bytes in network byte order / Big Endian)
        outputStream.write((targetPort shr 8) and 0xFF)
        outputStream.write(targetPort and 0xFF)
        outputStream.flush()

        // 3. Read Connection Response
        // Byte 1: SOCKS Version (0x05)
        // Byte 2: Reply Field (0x00 = success, other values indicate errors)
        // Byte 3: Reserved (0x00)
        // Byte 4: Address Type
        val responseHeader = ByteArray(4)
        val headerBytesRead = readFully(inputStream, responseHeader)
        if (headerBytesRead < 4 || responseHeader[0] != 0x05.toByte()) {
            socket.close()
            throw IllegalStateException("Tor connection request failed: Invalid response header")
        }

        val replyStatus = responseHeader[1]
        if (replyStatus != 0x00.toByte()) {
            socket.close()
            val errorMsg = when (replyStatus.toInt()) {
                1 -> "general SOCKS server failure"
                2 -> "connection not allowed by ruleset"
                3 -> "Network unreachable"
                4 -> "Host unreachable"
                5 -> "Connection refused"
                6 -> "TTL expired"
                7 -> "Command not supported"
                8 -> "Address type not supported"
                else -> "unknown error $replyStatus"
            }
            throw IllegalStateException("Tor SOCKS5 Connection refused: $errorMsg")
        }

        // Read remaining bound address and port (usually discarded by client)
        val boundAddressType = responseHeader[3]
        when (boundAddressType.toInt()) {
            1 -> { // IPv4 (4 bytes bound address + 2 bytes port)
                val dummy = ByteArray(6)
                readFully(inputStream, dummy)
            }
            3 -> { // Domain (1 byte length + length bytes + 2 bytes port)
                val len = inputStream.read()
                val dummy = ByteArray(len + 2)
                readFully(inputStream, dummy)
            }
            4 -> { // IPv6 (16 bytes address + 2 bytes port)
                val dummy = ByteArray(18)
                readFully(inputStream, dummy)
            }
        }

        // Socket is now successfully established! All subsequent reads/writes through this socket
        // are securely tunneled through the Tor network.
        return socket
    }

    private fun readFully(inputStream: InputStream, buffer: ByteArray): Int {
        var bytesReadTotal = 0
        while (bytesReadTotal < buffer.size) {
            val result = inputStream.read(buffer, bytesReadTotal, buffer.size - bytesReadTotal)
            if (result == -1) break
            bytesReadTotal += result
        }
        return bytesReadTotal
    }
}
