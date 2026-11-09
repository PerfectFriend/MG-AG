package com.example.data

import android.util.Log
import java.io.BufferedReader
import java.io.BufferedWriter
import java.io.InputStreamReader
import java.io.OutputStreamWriter
import java.net.Inet4Address
import java.net.NetworkInterface
import java.net.ServerSocket
import java.net.Socket
import kotlin.concurrent.thread

/**
 * Manages real peer-to-peer TCP communication over the Tor network and local LAN/VPN.
 * Supports:
 * 1. Anonymous P2P over Tor Onion (.onion) Services via local Tor SOCKS5 proxy.
 * 2. High-speed Direct P2P over local Wi-Fi / Hotspot / WireGuard LAN IPs.
 */
class TorP2PManager(
    private val socksHost: String = "127.0.0.1",
    private val socksPort: Int = 9050,
    private val onLog: (String) -> Unit,
    private val onConnectionStateChanged: (Boolean) -> Unit,
    private val onMessageReceived: (String) -> Unit
) {
    private var serverSocket: ServerSocket? = null
    private var activeSocket: Socket? = null
    private var reader: BufferedReader? = null
    private var writer: BufferedWriter? = null
    private var isRunning = false

    var connectedPeerAddress: String? = null
        private set

    companion object {
        fun getLocalIpAddress(): String? {
            try {
                val interfaces = NetworkInterface.getNetworkInterfaces()
                while (interfaces.hasMoreElements()) {
                    val intf = interfaces.nextElement()
                    val addrs = intf.inetAddresses
                    while (addrs.hasMoreElements()) {
                        val addr = addrs.nextElement()
                        if (!addr.isLoopbackAddress && addr is Inet4Address) {
                            return addr.hostAddress
                        }
                    }
                }
            } catch (_: Exception) {}
            return null
        }
    }

    /**
     * Starts listening on the local port mapped to the Tor Onion Service or direct LAN.
     */
    fun startHostServer(localPort: Int = 8080) {
        if (isRunning) return
        isRunning = true
        val localIp = getLocalIpAddress() ?: "127.0.0.1"
        onLog("[P2P] Starting host server on port $localPort (LAN IP: $localIp:$localPort)...")
        
        thread(name = "TorP2PHostServerThread") {
            try {
                serverSocket = ServerSocket(localPort)
                onLog("[P2P] Server socket listening. Waiting for remote peer (via Tor Onion or LAN Wi-Fi)...")
                
                while (isRunning) {
                    val socket = serverSocket?.accept() ?: break
                    val remoteAddr = socket.inetAddress?.hostAddress ?: "unknown"
                    onLog("[P2P] Remote peer connected from $remoteAddr!")
                    connectedPeerAddress = remoteAddr
                    handleConnectedSocket(socket)
                }
            } catch (e: Exception) {
                onLog("[P2P] Server error: ${e.message}")
            } finally {
                cleanup()
            }
        }
    }

    /**
     * Connects to a remote peer's .onion address through Tor SOCKS5,
     * or directly to an IP/host if given a standard address (e.g. 192.168.1.15:8080).
     */
    fun connectToRemoteHost(rawAddress: String, defaultPort: Int = 8080) {
        if (isRunning) return
        isRunning = true

        val clean = rawAddress.trim()
        val targetHost = if (clean.contains(":")) clean.substringBefore(":") else clean
        val targetPort = if (clean.contains(":")) clean.substringAfter(":").toIntOrNull() ?: defaultPort else defaultPort

        thread(name = "TorP2PClientConnectThread") {
            try {
                val socket = if (targetHost.endsWith(".onion", ignoreCase = true)) {
                    onLog("[TorP2P] Connecting to $targetHost:$targetPort through Tor SOCKS5 proxy on $socksHost:$socksPort...")
                    val proxyClient = TorProxyClient(socksHost, socksPort)
                    proxyClient.connectThroughTor(targetHost, targetPort)
                } else {
                    onLog("[P2P LAN] Connecting directly to peer $targetHost:$targetPort...")
                    Socket(targetHost, targetPort)
                }
                connectedPeerAddress = targetHost
                onLog("[P2P] Connected to remote host $targetHost:$targetPort successfully!")
                handleConnectedSocket(socket)
            } catch (e: Exception) {
                onLog("[P2P] Connection failed: ${e.message}")
                onConnectionStateChanged(false)
                cleanup()
            }
        }
    }

    /**
     * Sends a raw message (chat or game action) to the connected peer.
     */
    fun sendMessage(payload: String): Boolean {
        val currentWriter = writer
        if (currentWriter == null) {
            onLog("[TorP2P] Error: Cannot send message, no active peer connection.")
            return false
        }
        thread(name = "TorP2PWriterThread") {
            try {
                synchronized(this) {
                    currentWriter.write(payload)
                    currentWriter.newLine()
                    currentWriter.flush()
                }
                Log.d("TorP2P", "Sent packet: $payload")
            } catch (e: Exception) {
                onLog("[TorP2P] Error sending packet: ${e.message}")
                disconnect()
            }
        }
        return true
    }

    /**
     * Gracefully closes all sockets and threads.
     */
    fun disconnect() {
        onLog("[TorP2P] Disconnecting peer session...")
        cleanup()
    }

    private fun handleConnectedSocket(socket: Socket) {
        activeSocket = socket
        reader = BufferedReader(InputStreamReader(socket.getInputStream()))
        writer = BufferedWriter(OutputStreamWriter(socket.getOutputStream()))
        onConnectionStateChanged(true)

        thread(name = "TorP2PReaderThread") {
            try {
                val currentReader = reader ?: return@thread
                var line = currentReader.readLine()
                while (line != null) {
                    val received = line.trim()
                    if (received.isNotEmpty()) {
                        Log.d("TorP2P", "Received packet: $received")
                        onMessageReceived(received)
                    }
                    line = currentReader.readLine()
                }
            } catch (e: Exception) {
                onLog("[TorP2P] Connection lost or read interrupted: ${e.message}")
            } finally {
                onLog("[TorP2P] Peer connection closed.")
                onConnectionStateChanged(false)
                cleanup()
            }
        }
    }

    private fun cleanup() {
        isRunning = false
        try { activeSocket?.close() } catch (_: Exception) {}
        try { serverSocket?.close() } catch (_: Exception) {}
        try { reader?.close() } catch (_: Exception) {}
        try { writer?.close() } catch (_: Exception) {}
        activeSocket = null
        serverSocket = null
        reader = null
        writer = null
    }
}
