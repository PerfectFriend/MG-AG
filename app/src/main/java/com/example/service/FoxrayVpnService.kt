package com.example.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import android.net.VpnService
import android.os.Build
import android.os.ParcelFileDescriptor
import android.util.Log
import androidx.core.app.NotificationCompat

class FoxrayVpnService : VpnService(), Runnable {

    private var vpnThread: Thread? = null
    private var vpnInterface: ParcelFileDescriptor? = null
    private var tunPacketRouter: Tun2SocksPacketRouter? = null
    private var hevProcess: Process? = null

    // Seamless Network Handover & Underlying Network Tracking (Android 24+)
    private var connectivityManager: ConnectivityManager? = null
    private var networkCallback: ConnectivityManager.NetworkCallback? = null
    private var currentActiveNetwork: Network? = null

    companion object {
        const val ACTION_CONNECT = "com.example.vpn.CONNECT"
        const val ACTION_DISCONNECT = "com.example.vpn.DISCONNECT"
        const val EXTRA_ROUTE_ALL_DEVICE = "extra_route_all_device"
        const val EXTRA_PERSISTENT_BACKGROUND = "extra_persistent_background"
        const val CHANNEL_ID = "foxray_vpn_channel"
        const val NOTIFICATION_ID = 4050
        
        var isServiceRunning = false
            private set
        var isFullDeviceRoutingActive = true
            private set
        var isPersistentBackgroundActive = true
            private set
    }

    override fun onCreate() {
        super.onCreate()
        registerNetworkCallback()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val action = intent?.action
        if (ACTION_DISCONNECT == action) {
            stopVpn()
            return START_NOT_STICKY
        } else {
            isFullDeviceRoutingActive = intent?.getBooleanExtra(EXTRA_ROUTE_ALL_DEVICE, true) ?: true
            isPersistentBackgroundActive = intent?.getBooleanExtra(EXTRA_PERSISTENT_BACKGROUND, true) ?: true
            isServiceRunning = true
            startVpn()
        }
        return if (isPersistentBackgroundActive) START_STICKY else START_NOT_STICKY
    }

    override fun onDestroy() {
        stopVpn()
        unregisterNetworkCallback()
        super.onDestroy()
    }

    private fun registerNetworkCallback() {
        if (networkCallback != null) return
        try {
            connectivityManager = getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
            val callback = object : ConnectivityManager.NetworkCallback() {
                override fun onAvailable(network: Network) {
                    currentActiveNetwork = network
                    Log.i("FoxrayVpnService", "Physical network available: $network, applying underlying network")
                    applyUnderlyingNetwork(network)
                }

                override fun onCapabilitiesChanged(network: Network, networkCapabilities: NetworkCapabilities) {
                    val hasInternet = networkCapabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
                    val isWifi = networkCapabilities.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)
                    val isCellular = networkCapabilities.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR)
                    Log.i("FoxrayVpnService", "Network caps changed: network=$network, wifi=$isWifi, cell=$isCellular, internet=$hasInternet")
                    if (hasInternet) {
                        currentActiveNetwork = network
                        applyUnderlyingNetwork(network)
                    }
                }

                override fun onLost(network: Network) {
                    Log.i("FoxrayVpnService", "Physical network lost: $network")
                    if (currentActiveNetwork == network) {
                        currentActiveNetwork = null
                        applyUnderlyingNetwork(null)
                    }
                }
            }
            networkCallback = callback
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                connectivityManager?.registerDefaultNetworkCallback(callback)
            } else {
                val request = NetworkRequest.Builder()
                    .addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
                    .build()
                connectivityManager?.registerNetworkCallback(request, callback)
            }
        } catch (e: Exception) {
            Log.w("FoxrayVpnService", "Error registering NetworkCallback: ${e.message}")
        }
    }

    private fun unregisterNetworkCallback() {
        try {
            networkCallback?.let { connectivityManager?.unregisterNetworkCallback(it) }
        } catch (_: Exception) {}
        networkCallback = null
    }

    private fun applyUnderlyingNetwork(network: Network?) {
        try {
            if (network != null) {
                setUnderlyingNetworks(arrayOf(network))
            } else {
                setUnderlyingNetworks(null)
            }
        } catch (e: Exception) {
            Log.w("FoxrayVpnService", "Failed setUnderlyingNetworks: ${e.message}")
        }
    }

    private fun startVpn() {
        registerNetworkCallback()
        createNotificationChannel()
        val notification = createNotification()
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                startForeground(NOTIFICATION_ID, notification, android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE)
            } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                startForeground(NOTIFICATION_ID, notification, android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_SYSTEM_EXEMPTED)
            } else {
                startForeground(NOTIFICATION_ID, notification)
            }
        } catch (e: Throwable) {
            Log.e("FoxrayVpnService", "startForeground with type failed: ${e.message}, retrying fallback", e)
            try {
                startForeground(NOTIFICATION_ID, notification)
            } catch (t: Throwable) {
                Log.e("FoxrayVpnService", "Fallback startForeground failed: ${t.message}", t)
            }
        }

        stopVpnThread()
        vpnThread = Thread(this, "FoxrayVpnThread").apply {
            start()
        }
    }

    private fun stopVpn() {
        isServiceRunning = false
        stopVpnThread()
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                stopForeground(STOP_FOREGROUND_REMOVE)
            } else {
                @Suppress("DEPRECATION")
                stopForeground(true)
            }
        } catch (_: Throwable) {}
        closeVpnInterface()
    }

    private fun stopVpnThread() {
        vpnThread?.interrupt()
        vpnThread = null
    }

    private fun closeVpnInterface() {
        try {
            hevProcess?.destroy()
            hevProcess = null
            if (NativeNetworkBridge.isTun2SocksAvailable) {
                NativeNetworkBridge.stopTun2Socks()
            }
            tunPacketRouter?.stop()
            tunPacketRouter = null
            vpnInterface?.close()
        } catch (e: Exception) {
            Log.e("FoxrayVpnService", "Error closing VPN interface", e)
        }
        vpnInterface = null
    }

    override fun run() {
        try {
            // Check if VPN is prepared/authorized by user before establishing interface
            val isPrepared = prepare(this) == null
            if (!isPrepared) {
                Log.w("FoxrayVpnService", "VpnService permission is not authorized by user. Halting.")
                isServiceRunning = false
                stopSelf()
                return
            }

            // Configure local VPN virtual network interface (TUN)
            val builder = Builder()
                .setSession(if (isFullDeviceRoutingActive) "SheshBesh-Full-Mesh" else "SheshBesh-App-Mesh")
                .setMtu(1500)
                .addAddress("10.255.0.1", 24)
                .addDnsServer("1.1.1.1")

            if (isFullDeviceRoutingActive) {
                builder.addRoute("0.0.0.0", 0)
                // Routing loop protection: isolate our own transport socket so Tor/Xray raw outbound sockets don't loop
                try {
                    builder.addDisallowedApplication(packageName)
                } catch (e: Exception) {
                    Log.w("FoxrayVpnService", "Could not add disallowed application: ${e.message}")
                }
            } else {
                builder.addRoute("10.255.0.0", 24)
                try {
                    builder.addAllowedApplication(packageName)
                } catch (e: Exception) {
                    builder.addRoute("0.0.0.0", 0)
                }
            }

            vpnInterface = builder.establish()
            if (vpnInterface == null) {
                Log.e("FoxrayVpnService", "Failed to establish VPN interface ParcelFileDescriptor")
                isServiceRunning = false
                stopSelf()
                return
            }
            applyUnderlyingNetwork(currentActiveNetwork)

            val fd = vpnInterface!!.fd
            val nativeHev = java.io.File(applicationInfo.nativeLibraryDir, "libhev-socks5-tunnel.so")
            if (nativeHev.exists() && nativeHev.canExecute()) {
                val tproxyConfigFile = java.io.File(filesDir, "tproxy.yml")
                val tproxyYaml = """
                    tunnel:
                      mtu: 1500
                      ipv4: 10.255.0.1
                    socks5:
                      port: 10808
                      address: 127.0.0.1
                      udp: 'udp'
                """.trimIndent()
                tproxyConfigFile.writeText(tproxyYaml)
                Log.i("FoxrayVpnService", "Starting native hev-socks5-tunnel on fd=$fd with config=${tproxyConfigFile.absolutePath}...")
                try {
                    val pb = ProcessBuilder(nativeHev.absolutePath, tproxyConfigFile.absolutePath, fd.toString())
                    pb.redirectErrorStream(true)
                    hevProcess = pb.start()
                } catch (e: Exception) {
                    Log.w("FoxrayVpnService", "hev process start exception, falling back: ${e.message}")
                }
            } else if (NativeNetworkBridge.isTun2SocksAvailable) {
                Log.i("FoxrayVpnService", "Engaging Native JNI libtun2socks.so engine (fd=$fd)...")
                NativeNetworkBridge.startTun2Socks(fd, "127.0.0.1", 10808)
            } else {
                val router = Tun2SocksPacketRouter(vpnInterface!!, "127.0.0.1", 10808) { logLine ->
                    Log.d("FoxrayVpnService", logLine)
                }
                tunPacketRouter = router
                Thread(router, "Tun2SocksRouterThread").start()
            }
            
            // Active lifecycle loop
            while (!Thread.currentThread().isInterrupted && isServiceRunning) {
                Thread.sleep(1000)
            }
        } catch (e: Exception) {
            Log.e("FoxrayVpnService", "VPN Error", e)
        } finally {
            closeVpnInterface()
            isServiceRunning = false
        }
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val serviceChannel = NotificationChannel(
                CHANNEL_ID,
                "Foxray VPN Service Channel",
                NotificationManager.IMPORTANCE_LOW
            )
            val manager = getSystemService(NotificationManager::class.java)
            manager?.createNotificationChannel(serviceChannel)
        }
    }

    private fun createNotification(): Notification {
        val intent = packageManager.getLaunchIntentForPackage(packageName)
        val pendingIntent = if (intent != null) {
            PendingIntent.getActivity(
                this,
                0,
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
        } else null

        val title = if (isFullDeviceRoutingActive) "Шеш-Беш: Полный VPN-шлюз активен" else "Шеш-Беш: Защищенный мост активен"
        val text = if (isPersistentBackgroundActive) {
            if (isFullDeviceRoutingActive) "Маршрутизация всего трафика устройства через мост (режим Orbot)"
            else "Фоновый E2EE мост активен без отключения"
        } else {
            "Защищенная сессия VLESS / Tor / SimpleX"
        }

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle(title)
            .setContentText(text)
            .setSmallIcon(android.R.drawable.ic_lock_lock)
            .setContentIntent(pendingIntent)
            .setOngoing(isPersistentBackgroundActive)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()
    }
}
