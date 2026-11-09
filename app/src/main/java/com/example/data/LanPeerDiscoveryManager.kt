package com.example.data

import android.content.Context
import android.net.nsd.NsdManager
import android.net.nsd.NsdServiceInfo
import android.net.wifi.WifiManager
import android.os.Build
import android.util.Log
import kotlinx.coroutines.*
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress
import java.net.InetSocketAddress
import java.util.concurrent.ConcurrentHashMap

/**
 * Zero-config Hybrid Local Network Discovery:
 * 1. UDP Multicast / Broadcast (port 8888)
 * 2. Android Network Service Discovery (NSD / mDNS "_madgammon._tcp.")
 *
 * This dual approach ensures peer discovery works reliably even on Wi-Fi networks
 * where AP isolation or router firewalls drop standard UDP broadcasts.
 */
class LanPeerDiscoveryManager(
    private val context: Context,
    private val onPeerDiscovered: (name: String, address: String) -> Unit
) {
    companion object {
        private const val TAG = "LanPeerDiscovery"
        private const val DISCOVERY_PORT = 8888
        private const val MAGIC_PREFIX = "MADGAMMON_ROOM"
        private const val NSD_SERVICE_TYPE = "_madgammon._tcp."
        private const val DEDUPLICATION_WINDOW_MS = 3000L
    }

    private var broadcastJob: Job? = null
    private var listenJob: Job? = null
    private var multicastLock: WifiManager.MulticastLock? = null
    private var isRunning = false

    private val nsdManager: NsdManager? by lazy {
        context.applicationContext.getSystemService(Context.NSD_SERVICE) as? NsdManager
    }
    private var registrationListener: NsdManager.RegistrationListener? = null
    private var discoveryListener: NsdManager.DiscoveryListener? = null

    // Cache to prevent duplicate triggers from UDP + mDNS concurrent discovery
    private val recentlyDiscoveredPeers = ConcurrentHashMap<String, Long>()

    data class DiscoveredRoom(
        val hostName: String,
        val hostAddress: String,
        val lastSeenMillis: Long = System.currentTimeMillis()
    )

    fun startListening() {
        if (isRunning) return
        isRunning = true

        acquireMulticastLock()
        startUdpListening()
        startNsdDiscovery()
    }

    private fun startUdpListening() {
        listenJob = CoroutineScope(Dispatchers.IO).launch {
            var socket: DatagramSocket? = null
            try {
                socket = DatagramSocket(null).apply {
                    reuseAddress = true
                    bind(InetSocketAddress(DISCOVERY_PORT))
                    broadcast = true
                }
                val buffer = ByteArray(1024)
                val packet = DatagramPacket(buffer, buffer.size)

                Log.i(TAG, "LAN Discovery listener active on UDP port $DISCOVERY_PORT")

                while (isActive && isRunning) {
                    try {
                        socket.receive(packet)
                        val text = String(packet.data, 0, packet.length, Charsets.UTF_8).trim()
                        if (text.startsWith(MAGIC_PREFIX)) {
                            val parts = text.split(":")
                            if (parts.size >= 3) {
                                val hostName = parts[1]
                                val endpoint = parts.drop(2).joinToString(":")
                                notifyPeerDiscovered(hostName, endpoint, source = "UDP-Broadcast")
                            }
                        }
                    } catch (e: Exception) {
                        if (!isRunning) break
                    }
                }
            } catch (e: Exception) {
                Log.w(TAG, "Error in LAN UDP discovery listener: ${e.message}")
            } finally {
                socket?.close()
            }
        }
    }

    private fun startNsdDiscovery() {
        try {
            val listener = object : NsdManager.DiscoveryListener {
                override fun onStartDiscoveryFailed(serviceType: String?, errorCode: Int) {
                    Log.w(TAG, "NSD discovery start failed: $errorCode")
                }

                override fun onStopDiscoveryFailed(serviceType: String?, errorCode: Int) {
                    Log.w(TAG, "NSD discovery stop failed: $errorCode")
                }

                override fun onDiscoveryStarted(serviceType: String?) {
                    Log.i(TAG, "NSD discovery started for $serviceType")
                }

                override fun onDiscoveryStopped(serviceType: String?) {
                    Log.i(TAG, "NSD discovery stopped")
                }

                override fun onServiceFound(serviceInfo: NsdServiceInfo?) {
                    if (serviceInfo == null) return
                    Log.i(TAG, "NSD Service found: ${serviceInfo.serviceName}")
                    if (serviceInfo.serviceType.contains("madgammon")) {
                        try {
                            nsdManager?.resolveService(serviceInfo, object : NsdManager.ResolveListener {
                                override fun onResolveFailed(serviceInfo: NsdServiceInfo?, errorCode: Int) {
                                    Log.w(TAG, "NSD resolve failed: $errorCode")
                                }

                                override fun onServiceResolved(resolvedInfo: NsdServiceInfo?) {
                                    if (resolvedInfo == null) return
                                    val host = resolvedInfo.host?.hostAddress ?: return
                                    val port = resolvedInfo.port
                                    val peerName = resolvedInfo.serviceName
                                    val endpoint = "$host:$port"
                                    notifyPeerDiscovered(peerName, endpoint, source = "NSD-mDNS")
                                }
                            })
                        } catch (e: Exception) {
                            Log.w(TAG, "Error resolving NSD service: ${e.message}")
                        }
                    }
                }

                override fun onServiceLost(serviceInfo: NsdServiceInfo?) {
                    Log.i(TAG, "NSD Service lost: ${serviceInfo?.serviceName}")
                }
            }
            discoveryListener = listener
            nsdManager?.discoverServices(NSD_SERVICE_TYPE, NsdManager.PROTOCOL_DNS_SD, listener)
        } catch (e: Exception) {
            Log.w(TAG, "Failed to start NSD discovery: ${e.message}")
        }
    }

    fun startBroadcasting(hostName: String, localEndpoint: String) {
        stopBroadcasting()
        startUdpBroadcasting(hostName, localEndpoint)
        startNsdRegistration(hostName, localEndpoint)
    }

    private fun startUdpBroadcasting(hostName: String, localEndpoint: String) {
        broadcastJob = CoroutineScope(Dispatchers.IO).launch {
            var socket: DatagramSocket? = null
            try {
                socket = DatagramSocket().apply {
                    broadcast = true
                }
                val broadcastPayload = "$MAGIC_PREFIX:$hostName:$localEndpoint".toByteArray(Charsets.UTF_8)
                val broadcastAddress = InetAddress.getByName("255.255.255.255")
                val packet = DatagramPacket(broadcastPayload, broadcastPayload.size, broadcastAddress, DISCOVERY_PORT)

                Log.i(TAG, "LAN Discovery broadcasting host: $hostName on $localEndpoint")

                while (isActive) {
                    try {
                        socket.send(packet)
                    } catch (e: Exception) {
                        Log.w(TAG, "Error sending broadcast packet: ${e.message}")
                    }
                    delay(2000)
                }
            } catch (e: Exception) {
                Log.w(TAG, "Error in LAN broadcast: ${e.message}")
            } finally {
                socket?.close()
            }
        }
    }

    private fun startNsdRegistration(hostName: String, localEndpoint: String) {
        try {
            val port = localEndpoint.substringAfterLast(":", "8888").toIntOrNull() ?: 8888
            val serviceInfo = NsdServiceInfo().apply {
                serviceName = hostName
                serviceType = NSD_SERVICE_TYPE
                setPort(port)
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                    setAttribute("endpoint", localEndpoint)
                }
            }

            val listener = object : NsdManager.RegistrationListener {
                override fun onServiceRegistered(NsdServiceInfo: NsdServiceInfo?) {
                    Log.i(TAG, "NSD Service registered successfully: ${NsdServiceInfo?.serviceName}")
                }

                override fun onRegistrationFailed(serviceInfo: NsdServiceInfo?, errorCode: Int) {
                    Log.w(TAG, "NSD Service registration failed: $errorCode")
                }

                override fun onServiceUnregistered(arg0: NsdServiceInfo?) {
                    Log.i(TAG, "NSD Service unregistered")
                }

                override fun onUnregistrationFailed(serviceInfo: NsdServiceInfo?, errorCode: Int) {
                    Log.w(TAG, "NSD Service unregistration failed: $errorCode")
                }
            }
            registrationListener = listener
            nsdManager?.registerService(serviceInfo, NsdManager.PROTOCOL_DNS_SD, listener)
        } catch (e: Exception) {
            Log.w(TAG, "Failed to register NSD service: ${e.message}")
        }
    }

    private fun notifyPeerDiscovered(hostName: String, endpoint: String, source: String) {
        val key = "$hostName|$endpoint"
        val now = System.currentTimeMillis()
        val lastSeen = recentlyDiscoveredPeers[key] ?: 0L
        if (now - lastSeen < DEDUPLICATION_WINDOW_MS) {
            return // Skip rapid duplicate
        }
        recentlyDiscoveredPeers[key] = now
        Log.i(TAG, "Discovered LAN peer via $source: '$hostName' at $endpoint")
        CoroutineScope(Dispatchers.Main).launch {
            onPeerDiscovered(hostName, endpoint)
        }
    }

    fun stopBroadcasting() {
        broadcastJob?.cancel()
        broadcastJob = null
        try {
            registrationListener?.let { nsdManager?.unregisterService(it) }
        } catch (_: Exception) {}
        registrationListener = null
    }

    fun stop() {
        isRunning = false
        stopBroadcasting()
        listenJob?.cancel()
        listenJob = null
        try {
            discoveryListener?.let { nsdManager?.stopServiceDiscovery(it) }
        } catch (_: Exception) {}
        discoveryListener = null
        releaseMulticastLock()
        recentlyDiscoveredPeers.clear()
    }

    private fun acquireMulticastLock() {
        try {
            val wifi = context.applicationContext.getSystemService(Context.WIFI_SERVICE) as? WifiManager
            multicastLock = wifi?.createMulticastLock("MadGammonMulticastLock")?.apply {
                setReferenceCounted(true)
                acquire()
            }
        } catch (e: Exception) {
            Log.w(TAG, "Could not acquire multicast lock: ${e.message}")
        }
    }

    private fun releaseMulticastLock() {
        try {
            multicastLock?.let {
                if (it.isHeld) it.release()
            }
        } catch (_: Exception) {}
        multicastLock = null
    }
}
