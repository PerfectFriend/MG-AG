package com.example.ui.viewmodel.delegates

import android.content.Context
import android.content.SharedPreferences
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.example.data.FoxrayVpnManager
import com.example.data.MultiPathTransportRacer
import com.example.data.TorEmbeddedController
import com.example.data.V2RayEmbeddedController
import com.example.data.WifiDirectMeshManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Делегат сетевых сервисов, анонимизирующих мостов (Tor, V2Ray/Xray) и VPN-туннеля.
 *
 * Отвечает за:
 * - Управление встроенным демоном Tor (`TorEmbeddedController`) и SOCKS5-прокси.
 * - Управление V2Ray транспортом (`V2RayEmbeddedController`) для обхода DPI-блокировок.
 * - Фоновый режим без отключения (аналог Orbot) и полную маршрутизацию всего системного трафика устройства.
 * - Логирование сетевой активности и перегенерацию Onion v3 адресов.
 *
 * @param context Контекст приложения Android.
 * @param scope CoroutineScope ViewModel для асинхронных операций запуска демонов.
 */
class NetworkServiceDelegate(
    private val context: Context,
    private val scope: CoroutineScope
) {
    private val prefs: SharedPreferences =
        context.getSharedPreferences("crazy_backgammon_prefs", Context.MODE_PRIVATE)

    val vpnManager = FoxrayVpnManager(context)
    val wifiDirectMeshManager by lazy { WifiDirectMeshManager(context) }

    var isBackgroundBridgePersistent by mutableStateOf(prefs.getBoolean("bg_bridge_persistent", true))
        private set

    var isFullDeviceRoutingEnabled by mutableStateOf(prefs.getBoolean("full_device_routing", true))
        private set

    var isTorEnabled by mutableStateOf(prefs.getBoolean("tor_enabled", false))
        private set

    var isTransportRacingActive by mutableStateOf(prefs.getBoolean("transport_racing_active", true))
        private set

    var torStatus by mutableStateOf("INACTIVE")
    var torSocksPort by mutableIntStateOf(prefs.getInt("tor_socks_port", 9050))
        private set

    var serverStatus by mutableStateOf("DISCONNECTED")
    var isConnectingToServer by mutableStateOf(false)

    val torLogsList = mutableStateListOf<String>()
    val v2RayTorSyncLogs = mutableStateListOf<String>()

    private var torController: TorEmbeddedController? = null
    private var v2rayController: V2RayEmbeddedController? = null

    /**
     * Подключается к целевому хосту через самый быстрый доступный транспорт (Happy Eyeballs v2).
     */
    suspend fun connectFastestTransport(targetHost: String, targetPort: Int): com.example.data.MultiPathTransportRacer.RaceResult {
        torLogsList.add("[Racing] Happy Eyeballs v2: запуск параллельного опроса (Direct / V2Ray / Tor) -> $targetHost:$targetPort")
        val result = com.example.data.MultiPathTransportRacer.raceConnection(
            targetHost = targetHost,
            targetPort = targetPort,
            v2raySocksPort = 10808,
            torSocksPort = torSocksPort
        )
        torLogsList.add("[Racing] Победитель: ${result.transportType} (RTT: ${result.rttMillis} мс)")
        return result
    }

    fun setTransportRacingState(enabled: Boolean) {
        isTransportRacingActive = enabled
        prefs.edit().putBoolean("transport_racing_active", enabled).apply()
    }

    /**
     * Включает или выключает постоянную фоновую работу сетевого моста (подобно Orbot).
     */
    fun updateBackgroundBridgePersistent(enabled: Boolean) {
        isBackgroundBridgePersistent = enabled
        prefs.edit().putBoolean("bg_bridge_persistent", enabled).apply()
        vpnManager.updatePersistentBackground(enabled)
    }

    /**
     * Включает или выключает перенаправление всего системного трафика устройства через VPN-туннель.
     */
    fun updateFullDeviceRouting(enabled: Boolean) {
        isFullDeviceRoutingEnabled = enabled
        prefs.edit().putBoolean("full_device_routing", enabled).apply()
        vpnManager.updateFullDeviceRouting(enabled)
    }

    fun setTorEnabledState(enabled: Boolean) {
        isTorEnabled = enabled
        prefs.edit().putBoolean("tor_enabled", enabled).apply()
        if (enabled) {
            startTor()
        } else {
            stopTor()
            torStatus = "INACTIVE"
            torLogsList.add("[Tor] Остановлен встроенный сервис Tor.")
            if (serverStatus == "CONNECTED") {
                serverStatus = "DISCONNECTED"
            }
        }
    }

    fun setSocksPort(port: Int) {
        torSocksPort = port
        prefs.edit().putInt("tor_socks_port", port).apply()
    }

    fun startTor() {
        if (torController == null) {
            torController = TorEmbeddedController(
                context = context,
                socksPort = torSocksPort,
                onLog = { line ->
                    scope.launch(Dispatchers.Main) {
                        torLogsList.add(line)
                    }
                },
                onStatusChange = { status ->
                    scope.launch(Dispatchers.Main) {
                        torStatus = status
                    }
                }
            )
        }
        torController?.start()
    }

    fun stopTor() {
        torController?.stop()
    }

    fun startV2Ray() {
        if (v2rayController == null) {
            v2rayController = V2RayEmbeddedController(
                context = context,
                localPort = 10808,
                torSocksPort = torSocksPort,
                onLog = { line ->
                    scope.launch(Dispatchers.Main) {
                        v2RayTorSyncLogs.add(line)
                    }
                },
                onStatusChange = { _ -> }
            )
        }
        v2rayController?.start()
    }

    fun stopV2Ray() {
        v2rayController?.stop()
    }

    fun regenerateOnionAddress(onComplete: () -> Unit = {}) {
        scope.launch(Dispatchers.IO) {
            torController?.stop()
            delay(1000)
            val hsDir = java.io.File(context.filesDir, "tor_hs")
            if (hsDir.exists()) {
                hsDir.deleteRecursively()
            }
            withContext(Dispatchers.Main) {
                torLogsList.add("[Tor] Ключи очищены. Создание нового Onion v3 адреса...")
                startTor()
                onComplete()
            }
        }
    }

    fun addTorLog(line: String) {
        torLogsList.add("[System] $line")
    }

    fun stopAllServices() {
        try {
            stopTor()
            torStatus = "INACTIVE"
        } catch (e: Exception) {
            e.printStackTrace()
        }
        try {
            stopV2Ray()
        } catch (e: Exception) {
            e.printStackTrace()
        }
        try {
            vpnManager.stopVpnConnectionSimulation()
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
}
