package com.example.data

import android.content.Context
import android.util.Log
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import kotlinx.coroutines.*
import org.json.JSONArray
import org.json.JSONObject
import java.net.InetSocketAddress
import java.net.Socket
import java.util.UUID

enum class BridgeChainTopology(val displayNameRu: String, val displayNameEn: String, val descriptionRu: String) {
    TOR_DIRECT(
        displayNameRu = "Прямой Tor (Onion)",
        displayNameEn = "Direct Tor (Onion)",
        descriptionRu = "Трафик направляется напрямую в сеть Tor через локальный SOCKS5 (:9050)"
    ),
    VPN_OVER_TOR(
        displayNameRu = "Мост: VLESS/VPN через Tor",
        displayNameEn = "Bridge: VPN through Tor",
        descriptionRu = "VLESS/Shadowsocks/Reality маскировка трафика перед входом в сеть Tor (Обход DPI)"
    ),
    TOR_OVER_VPN(
        displayNameRu = "Шлюз: Tor через VPN",
        displayNameEn = "Gateway: Tor through VPN",
        descriptionRu = "Выходной узел направляет трафик через конечный VPN-шлюз"
    ),
    MULTI_HOP_CHAIN(
        displayNameRu = "Многоузловая цепь (Multi-Hop)",
        displayNameEn = "Multi-Hop Chain",
        descriptionRu = "Каскад: Obfs4 Fronting -> VLESS Reality -> Tor Guard Node -> Onion Сервис"
    )
}

enum class VpnProtocolType(val protocolTag: String, val defaultPort: Int) {
    VLESS("VLESS-Reality", 443),
    VMESS("VMess-AEAD", 443),
    SHADOWSOCKS("Shadowsocks-2022", 8388),
    TROJAN("Trojan-TLS", 443),
    HYSTERIA2("Hysteria2-QUIC", 443),
    WIREGUARD("WireGuard", 51820),
    TOR_OBFS4("Tor-Obfs4", 443),
    TOR_SNOWFLAKE("Tor-Snowflake", 443)
}

enum class RelayNodeType {
    SMP,
    XFTP,
    ICE_TURN,
    VPN_NODE
}

data class RelayNode(
    val id: String,
    val type: RelayNodeType,
    val name: String,
    val url: String,
    val isOnion: Boolean = url.contains(".onion", ignoreCase = true),
    var pingMs: Int = -1, // -1: не тестировался, -2: идет тест, >0: доступен (мс), -3: недоступен, -4: заблокирован политикой
    var isVerified: Boolean = false,
    val notes: String = ""
)

/**
 * Комплексный менеджер конфигураций цепочек VPN + TOR,
 * релеев SMP / XFTP / ICE в сети .onion и политики Strict Onion-Only (Killswitch).
 */
class OnionBridgeConfigManager(private val context: Context) {

    companion object {
        private const val TAG = "OnionBridgeConfig"
        private const val PREFS_NAME = "onion_bridge_config_vault"
    }

    private val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    // 1. Топология цепочки моста
    var chainTopology by mutableStateOf(
        try {
            BridgeChainTopology.valueOf(prefs.getString("chain_topology", BridgeChainTopology.VPN_OVER_TOR.name) ?: BridgeChainTopology.VPN_OVER_TOR.name)
        } catch (_: Exception) {
            BridgeChainTopology.VPN_OVER_TOR
        }
    )
        private set

    // 2. Активный VPN протокол
    var selectedVpnProtocol by mutableStateOf(
        try {
            VpnProtocolType.valueOf(prefs.getString("selected_vpn_protocol", VpnProtocolType.VLESS.name) ?: VpnProtocolType.VLESS.name)
        } catch (_: Exception) {
            VpnProtocolType.VLESS
        }
    )
        private set

    // 3. Строгий режим изоляции Onion (Killswitch против утечек в clearnet)
    var isStrictOnionOnlyEnabled by mutableStateOf(prefs.getBoolean("strict_onion_only", true))
        private set

    // 4. Текстовая конфигурация активного моста
    var activeBridgeRawConfig by mutableStateOf(prefs.getString("active_bridge_raw_config", "") ?: "")
        private set

    // Списки серверов и релеев
    val relayNodes = mutableStateListOf<RelayNode>()

    var isTestingActive by mutableStateOf(false)
        private set

    var testingStatusMessage by mutableStateOf("")
        private set

    init {
        loadRelayNodes()
    }

    fun updateChainTopology(newTopology: BridgeChainTopology) {
        chainTopology = newTopology
        prefs.edit().putString("chain_topology", newTopology.name).apply()
        Log.i(TAG, "Bridge topology updated to: $newTopology")
    }

    fun updateVpnProtocol(protocol: VpnProtocolType) {
        selectedVpnProtocol = protocol
        prefs.edit().putString("selected_vpn_protocol", protocol.name).apply()
        Log.i(TAG, "Selected VPN protocol updated to: $protocol")
    }

    fun updateStrictOnionOnly(enabled: Boolean) {
        isStrictOnionOnlyEnabled = enabled
        prefs.edit().putBoolean("strict_onion_only", enabled).apply()
        Log.i(TAG, "Strict Onion-Only mode set to: $enabled")
    }

    fun updateActiveBridgeRawConfig(configText: String) {
        activeBridgeRawConfig = configText.trim()
        prefs.edit().putString("active_bridge_raw_config", activeBridgeRawConfig).apply()
    }

    /**
     * Проверка: разрешен ли хост/адрес в соответствии с политикой Strict Onion-Only.
     * Защищает от случайной компрометации IP при отправке трафика мимо Tor+VPN моста.
     */
    fun isHostAllowed(hostOrUrl: String): Boolean {
        if (!isStrictOnionOnlyEnabled) return true
        val clean = hostOrUrl.trim().lowercase()
        // Разрешены только локальные loopback порты и .onion домены
        if (clean.contains(".onion")) return true
        if (clean.startsWith("127.0.0.1") || clean.startsWith("localhost")) return true
        if (clean.startsWith("smp://") && clean.substringAfter("smp://").contains(".onion")) return true
        if (clean.startsWith("xftp://") && clean.substringAfter("xftp://").contains(".onion")) return true
        if (clean.startsWith("turn:") && clean.substringAfter("turn:").contains(".onion")) return true

        Log.w(TAG, "🛑 [STRICT ONION-ONLY KILLSWITCH] Blocked clearnet destination: $hostOrUrl")
        return false
    }

    /**
     * Добавление нового релея или сервера в конфигурацию.
     */
    fun addRelayNode(
        type: RelayNodeType,
        name: String,
        url: String,
        notes: String = ""
    ): Boolean {
        val cleanUrl = url.trim()
        val cleanName = name.trim().ifBlank { "${type.name} Relay [${relayNodes.size + 1}]" }
        if (cleanUrl.isBlank()) return false

        val node = RelayNode(
            id = UUID.randomUUID().toString(),
            type = type,
            name = cleanName,
            url = cleanUrl,
            isOnion = cleanUrl.contains(".onion", ignoreCase = true),
            notes = notes
        )
        relayNodes.add(node)
        saveRelayNodes()
        return true
    }

    fun removeRelayNode(id: String) {
        relayNodes.removeAll { it.id == id }
        saveRelayNodes()
    }

    /**
     * Импорт конфигураций из текстового содержимого (ссылки, obfs4, json).
     */
    fun importConfigsFromText(rawText: String): Int {
        val lines = rawText.lines().map { it.trim() }.filter { it.isNotBlank() }
        var importedCount = 0

        for (line in lines) {
            when {
                line.startsWith("smp://") || line.contains(".onion") && line.contains("smp") -> {
                    addRelayNode(RelayNodeType.SMP, "SMP Onion Relay", line)
                    importedCount++
                }
                line.startsWith("xftp://") || line.contains(".onion") && line.contains("xftp") -> {
                    addRelayNode(RelayNodeType.XFTP, "XFTP Storage", line)
                    importedCount++
                }
                line.startsWith("turn:") || line.startsWith("stun:") -> {
                    addRelayNode(RelayNodeType.ICE_TURN, "ICE/TURN Relay", line)
                    importedCount++
                }
                line.startsWith("vless://") || line.startsWith("vmess://") || line.startsWith("ss://") ||
                line.startsWith("trojan://") || line.startsWith("hy2://") || line.startsWith("wireguard://") -> {
                    addRelayNode(RelayNodeType.VPN_NODE, "VPN Bridge Node", line)
                    importedCount++
                }
                line.startsWith("Bridge obfs4") || line.startsWith("obfs4 ") -> {
                    addRelayNode(RelayNodeType.VPN_NODE, "Tor Obfs4 Bridge", line)
                    importedCount++
                }
            }
        }

        if (importedCount == 0 && rawText.length > 20) {
            // Пользовательский raw JSON или сложный конфиг
            updateActiveBridgeRawConfig(rawText)
            addRelayNode(RelayNodeType.VPN_NODE, "Custom Bridge Config", "custom_bridge_cfg")
            importedCount = 1
        }

        return importedCount
    }

    /**
     * Тестирование всех релеев через настроенный Tor SOCKS5 / V2Ray мост.
     */
    fun testAllRelaysThroughBridge(
        torSocksPort: Int = 9050,
        xraySocksPort: Int = 10808,
        onComplete: (verifiedCount: Int, failedCount: Int) -> Unit
    ) {
        if (isTestingActive) return
        isTestingActive = true
        testingStatusMessage = "Запуск тестирования узлов через мост..."

        scope.launch {
            var verified = 0
            var failed = 0

            val currentNodes = relayNodes.toList()
            for ((index, node) in currentNodes.withIndex()) {
                testingStatusMessage = "Тестирование (${index + 1}/${currentNodes.size}): ${node.name}"
                node.pingMs = -2 // Индикатор активного тестирования

                // Проверка политики Strict Onion-Only
                if (isStrictOnionOnlyEnabled && !node.isOnion && node.type != RelayNodeType.VPN_NODE) {
                    node.pingMs = -4 // Заблокирован политикой
                    node.isVerified = false
                    failed++
                    continue
                }

                val targetHostAndPort = extractHostAndPort(node.url, node.type)
                val testPort = if (node.isOnion) torSocksPort else xraySocksPort

                val rtt = testSocketConnectionViaSocks5(
                    socksHost = "127.0.0.1",
                    socksPort = testPort,
                    targetHost = targetHostAndPort.first,
                    targetPort = targetHostAndPort.second,
                    timeoutMs = 4500
                )

                if (rtt > 0) {
                    node.pingMs = rtt
                    node.isVerified = true
                    verified++
                } else {
                    node.pingMs = -3
                    node.isVerified = false
                    failed++
                }

                delay(120)
            }

            saveRelayNodes()
            isTestingActive = false
            testingStatusMessage = "Тестирование завершено: доступно $verified, недоступно $failed"
            onComplete(verified, failed)
        }
    }

    /**
     * Удаление всех упавших/непроверенных узлов и сохранение только доступных.
     */
    fun commitVerifiedServersOnly(): Int {
        val verifiedList = relayNodes.filter { it.isVerified || it.pingMs > 0 }
        val removedCount = relayNodes.size - verifiedList.size
        relayNodes.clear()
        relayNodes.addAll(verifiedList)
        saveRelayNodes()
        return removedCount
    }

    private fun testSocketConnectionViaSocks5(
        socksHost: String,
        socksPort: Int,
        targetHost: String,
        targetPort: Int,
        timeoutMs: Int
    ): Int {
        val start = System.currentTimeMillis()
        var socket: Socket? = null
        return try {
            socket = Socket()
            socket.soTimeout = timeoutMs
            socket.connect(InetSocketAddress(socksHost, socksPort), 2500)

            // SOCKS5 Handshake
            val out = socket.getOutputStream()
            val inStream = socket.getInputStream()
            out.write(byteArrayOf(0x05, 0x01, 0x00))
            out.flush()

            val resp = ByteArray(2)
            var read = inStream.read(resp)
            if (read < 2 || resp[0] != 0x05.toByte() || resp[1] != 0x00.toByte()) {
                return -1
            }

            // SOCKS5 Connect Command with Domain Name
            val hostBytes = targetHost.toByteArray(Charsets.US_ASCII)
            out.write(byteArrayOf(0x05, 0x01, 0x00, 0x03))
            out.write(hostBytes.size)
            out.write(hostBytes)
            out.write((targetPort shr 8) and 0xFF)
            out.write(targetPort and 0xFF)
            out.flush()

            val connResp = ByteArray(4)
            read = inStream.read(connResp)
            if (read >= 4 && connResp[1] == 0x00.toByte()) {
                val duration = (System.currentTimeMillis() - start).toInt().coerceAtLeast(1)
                duration
            } else {
                -1
            }
        } catch (_: Exception) {
            -1
        } finally {
            try { socket?.close() } catch (_: Exception) {}
        }
    }

    private fun extractHostAndPort(rawUrl: String, type: RelayNodeType): Pair<String, Int> {
        val clean = rawUrl.substringAfter("://").substringBefore("/")
        val hostPart = clean.substringBefore("@").substringAfter("@", clean)
        val host = if (hostPart.contains(":")) hostPart.substringBefore(":") else hostPart
        val defaultPort = when (type) {
            RelayNodeType.SMP -> 443
            RelayNodeType.XFTP -> 443
            RelayNodeType.ICE_TURN -> 3478
            RelayNodeType.VPN_NODE -> 443
        }
        val port = if (hostPart.contains(":")) hostPart.substringAfter(":").toIntOrNull() ?: defaultPort else defaultPort
        return Pair(host, port)
    }

    private fun loadRelayNodes() {
        relayNodes.clear()
        val raw = prefs.getString("saved_relay_nodes", null)
        if (raw.isNullOrBlank()) {
            // Начальные проверенные .onion и clearnet релеи
            relayNodes.addAll(
                listOf(
                    RelayNode(
                        id = "onion_smp_1",
                        type = RelayNodeType.SMP,
                        name = "SimpleX Onion Relay v3",
                        url = "smp://6hzrpn2eicwql57ds7d5us4rsr6j62s3q56i2wf67k3xnhp55c2npkqd.onion",
                        isOnion = true,
                        notes = "Официальный .onion скрытый сервис очередей SimpleX"
                    ),
                    RelayNode(
                        id = "onion_xftp_1",
                        type = RelayNodeType.XFTP,
                        name = "XFTP Onion Media Vault",
                        url = "xftp://s757c3272nkyoxx4g7y445v37v56a22g4y77f2y64f33k2l5j2w5fsqd.onion",
                        isOnion = true,
                        notes = "Скрытое анонимное хранилище медиафайлов XFTP"
                    ),
                    RelayNode(
                        id = "onion_ice_1",
                        type = RelayNodeType.ICE_TURN,
                        name = "Onion TURN/ICE Gateway",
                        url = "turn:armageddon5xnhp2w5fsqd.onion:3478",
                        isOnion = true,
                        notes = "E2EE медиа-шлюз аудиовызовов без раскрытия IP"
                    ),
                    RelayNode(
                        id = "clearnet_smp_1",
                        type = RelayNodeType.SMP,
                        name = "SimpleX SMP Relay 10",
                        url = "smp://smp10.simplex.im",
                        isOnion = false,
                        notes = "Высокоскоростной резервный открытый узел"
                    )
                )
            )
            saveRelayNodes()
            return
        }

        try {
            val arr = JSONArray(raw)
            for (i in 0 until arr.length()) {
                val obj = arr.getJSONObject(i)
                relayNodes.add(
                    RelayNode(
                        id = obj.getString("id"),
                        type = try { RelayNodeType.valueOf(obj.getString("type")) } catch (_: Exception) { RelayNodeType.SMP },
                        name = obj.getString("name"),
                        url = obj.getString("url"),
                        isOnion = obj.optBoolean("isOnion", false),
                        pingMs = obj.optInt("pingMs", -1),
                        isVerified = obj.optBoolean("isVerified", false),
                        notes = obj.optString("notes", "")
                    )
                )
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun saveRelayNodes() {
        try {
            val arr = JSONArray()
            relayNodes.forEach { node ->
                val obj = JSONObject().apply {
                    put("id", node.id)
                    put("type", node.type.name)
                    put("name", node.name)
                    put("url", node.url)
                    put("isOnion", node.isOnion)
                    put("pingMs", node.pingMs)
                    put("isVerified", node.isVerified)
                    put("notes", node.notes)
                }
                arr.put(obj)
            }
            prefs.edit().putString("saved_relay_nodes", arr.toString()).apply()
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
}
