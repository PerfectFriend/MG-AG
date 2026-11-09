package com.example.data

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.net.NetworkInfo
import android.net.wifi.p2p.WifiP2pConfig
import android.net.wifi.p2p.WifiP2pDevice
import android.net.wifi.p2p.WifiP2pDeviceList
import android.net.wifi.p2p.WifiP2pInfo
import android.net.wifi.p2p.WifiP2pManager
import android.os.Looper
import android.util.Log
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Off-Grid Autonomous Mesh Transport using Android Wi-Fi Direct (P2P).
 *
 * Operates without an external Wi-Fi router, cellular network, or internet connection.
 * Allows two or more devices within ~100 meters to discover each other, form a high-speed
 * ad-hoc group, and establish raw E2EE sockets.
 */
class WifiDirectMeshManager(private val context: Context) {

    companion object {
        private const val TAG = "WifiDirectMeshManager"
        const val DEFAULT_MESH_PORT = 8988
    }

    private val p2pManager: WifiP2pManager? by lazy {
        context.getSystemService(Context.WIFI_P2P_SERVICE) as? WifiP2pManager
    }

    private var channel: WifiP2pManager.Channel? = null
    private var isReceiverRegistered = false

    private val _isP2pSupported = MutableStateFlow(false)
    val isP2pSupported: StateFlow<Boolean> = _isP2pSupported.asStateFlow()

    private val _isP2pEnabled = MutableStateFlow(false)
    val isP2pEnabled: StateFlow<Boolean> = _isP2pEnabled.asStateFlow()

    private val _meshStatus = MutableStateFlow("IDLE")
    val meshStatus: StateFlow<String> = _meshStatus.asStateFlow()

    private val _discoveredDevices = MutableStateFlow<List<WifiP2pDevice>>(emptyList())
    val discoveredDevices: StateFlow<List<WifiP2pDevice>> = _discoveredDevices.asStateFlow()

    private val _connectionInfo = MutableStateFlow<WifiP2pInfo?>(null)
    val connectionInfo: StateFlow<WifiP2pInfo?> = _connectionInfo.asStateFlow()

    private var connectionCallback: ((groupOwnerAddress: String, isHost: Boolean) -> Unit)? = null

    private val p2pReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            val action = intent?.action ?: return
            when (action) {
                WifiP2pManager.WIFI_P2P_STATE_CHANGED_ACTION -> {
                    val state = intent.getIntExtra(WifiP2pManager.EXTRA_WIFI_STATE, -1)
                    val isEnabled = state == WifiP2pManager.WIFI_P2P_STATE_ENABLED
                    _isP2pEnabled.value = isEnabled
                    Log.i(TAG, "Wi-Fi Direct state changed: isEnabled=$isEnabled")
                }
                WifiP2pManager.WIFI_P2P_PEERS_CHANGED_ACTION -> {
                    requestPeers()
                }
                WifiP2pManager.WIFI_P2P_CONNECTION_CHANGED_ACTION -> {
                    val networkInfo = intent.getParcelableExtra<NetworkInfo>(WifiP2pManager.EXTRA_NETWORK_INFO)
                    if (networkInfo?.isConnected == true) {
                        requestConnectionInfo()
                    } else {
                        _meshStatus.value = "DISCONNECTED"
                        _connectionInfo.value = null
                    }
                }
                WifiP2pManager.WIFI_P2P_THIS_DEVICE_CHANGED_ACTION -> {
                    val device = intent.getParcelableExtra<WifiP2pDevice>(WifiP2pManager.EXTRA_WIFI_P2P_DEVICE)
                    Log.i(TAG, "Local P2P Device: ${device?.deviceName} (${device?.deviceAddress})")
                }
            }
        }
    }

    init {
        try {
            if (p2pManager != null) {
                channel = p2pManager?.initialize(context, Looper.getMainLooper(), null)
                _isP2pSupported.value = channel != null
            }
        } catch (e: Exception) {
            Log.w(TAG, "Failed to initialize Wi-Fi P2P: ${e.message}")
            _isP2pSupported.value = false
        }
    }

    fun startDiscovery(onSuccess: () -> Unit = {}, onFailure: (Int) -> Unit = {}) {
        if (!_isP2pSupported.value || channel == null) {
            Log.w(TAG, "Wi-Fi Direct not supported or channel uninitialized")
            onFailure(-1)
            return
        }

        registerReceiver()
        _meshStatus.value = "DISCOVERING"

        try {
            p2pManager?.discoverPeers(channel, object : WifiP2pManager.ActionListener {
                override fun onSuccess() {
                    Log.i(TAG, "Wi-Fi Direct peer discovery started successfully")
                    onSuccess()
                }

                override fun onFailure(reasonCode: Int) {
                    Log.w(TAG, "Wi-Fi Direct peer discovery failed with reason: $reasonCode")
                    _meshStatus.value = "DISCOVERY_FAILED ($reasonCode)"
                    onFailure(reasonCode)
                }
            })
        } catch (e: SecurityException) {
            Log.w(TAG, "SecurityException during discoverPeers: ${e.message}")
            onFailure(-2)
        }
    }

    fun stopDiscovery() {
        try {
            p2pManager?.stopPeerDiscovery(channel, null)
            _meshStatus.value = "IDLE"
        } catch (_: Exception) {}
    }

    private fun requestPeers() {
        try {
            p2pManager?.requestPeers(channel) { peers: WifiP2pDeviceList? ->
                val deviceList = peers?.deviceList?.toList() ?: emptyList()
                Log.i(TAG, "Discovered ${deviceList.size} nearby Wi-Fi Direct devices")
                _discoveredDevices.value = deviceList
            }
        } catch (e: SecurityException) {
            Log.w(TAG, "SecurityException during requestPeers: ${e.message}")
        }
    }

    fun connectToPeer(
        device: WifiP2pDevice,
        onConnected: (groupOwnerAddress: String, isHost: Boolean) -> Unit
    ) {
        val config = WifiP2pConfig().apply {
            deviceAddress = device.deviceAddress
        }
        this.connectionCallback = onConnected
        _meshStatus.value = "CONNECTING to ${device.deviceName}"

        try {
            p2pManager?.connect(channel, config, object : WifiP2pManager.ActionListener {
                override fun onSuccess() {
                    Log.i(TAG, "Connection handshake initiated with ${device.deviceName}")
                }

                override fun onFailure(reason: Int) {
                    Log.w(TAG, "Connection failed to ${device.deviceName}, reason: $reason")
                    _meshStatus.value = "CONNECT_FAILED"
                }
            })
        } catch (e: SecurityException) {
            Log.w(TAG, "SecurityException connecting to peer: ${e.message}")
        }
    }

    private fun requestConnectionInfo() {
        try {
            p2pManager?.requestConnectionInfo(channel) { info ->
                if (info == null || !info.groupFormed) return@requestConnectionInfo
                _connectionInfo.value = info
                val isHost = info.isGroupOwner
                val hostAddress = info.groupOwnerAddress?.hostAddress ?: "192.168.49.1"
                _meshStatus.value = if (isHost) "CONNECTED_AS_HOST" else "CONNECTED_AS_CLIENT"
                Log.i(TAG, "Wi-Fi Direct group formed! isGroupOwner=$isHost, hostAddress=$hostAddress")
                connectionCallback?.invoke(hostAddress, isHost)
            }
        } catch (_: Exception) {}
    }

    fun disconnect() {
        try {
            p2pManager?.removeGroup(channel, null)
            _meshStatus.value = "DISCONNECTED"
            _connectionInfo.value = null
        } catch (_: Exception) {}
        unregisterReceiver()
    }

    private fun registerReceiver() {
        if (isReceiverRegistered) return
        val filter = IntentFilter().apply {
            addAction(WifiP2pManager.WIFI_P2P_STATE_CHANGED_ACTION)
            addAction(WifiP2pManager.WIFI_P2P_PEERS_CHANGED_ACTION)
            addAction(WifiP2pManager.WIFI_P2P_CONNECTION_CHANGED_ACTION)
            addAction(WifiP2pManager.WIFI_P2P_THIS_DEVICE_CHANGED_ACTION)
        }
        try {
            context.registerReceiver(p2pReceiver, filter)
            isReceiverRegistered = true
        } catch (e: Exception) {
            Log.w(TAG, "Error registering P2P receiver: ${e.message}")
        }
    }

    private fun unregisterReceiver() {
        if (!isReceiverRegistered) return
        try {
            context.unregisterReceiver(p2pReceiver)
        } catch (_: Exception) {}
        isReceiverRegistered = false
    }
}
