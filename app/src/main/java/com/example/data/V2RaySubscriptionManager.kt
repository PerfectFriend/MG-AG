package com.example.data

import android.content.Context
import android.content.SharedPreferences
import android.util.Base64
import android.util.Log
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import org.json.JSONArray
import org.json.JSONObject
import java.io.BufferedReader
import java.io.InputStreamReader
import java.net.HttpURLConnection
import java.net.InetSocketAddress
import java.net.Socket
import java.net.URL
import java.net.URLDecoder
import java.util.Random

/**
 * Data class representing a parsed V2Ray / Xray server endpoint configuration.
 */
data class V2RayServerConfig(
    val id: String,
    val name: String,
    val protocol: String, // VLESS, VMess, Shadowsocks, Trojan, Hysteria2, Xray
    val server: String,
    val port: Int,
    val uuidOrPassword: String,
    val security: String,
    val rawContent: String,
    var pingMs: Int = -1
)

/**
 * Manager class that handles:
 * 1. Fetching remote V2Ray subscription lists from community endpoints.
 * 2. Parsing subscription payloads (base64 / URI schemes / JSON configs).
 * 3. Running latency/speed tests against server endpoints.
 * 4. Cleaning out dead servers.
 * 5. Selecting and persisting the fastest working configuration.
 */
class V2RaySubscriptionManager(private val context: Context) {
    private val prefs: SharedPreferences = context.getSharedPreferences("v2ray_sub_manager_prefs", Context.MODE_PRIVATE)
    private val random = Random()

    var subscriptionUrls by mutableStateOf<List<String>>(emptyList())
        private set

    var configs by mutableStateOf<List<V2RayServerConfig>>(emptyList())
        private set

    var fastestConfig by mutableStateOf<V2RayServerConfig?>(null)
        private set

    var isTesting by mutableStateOf(false)
        private set

    var serverPings by mutableStateOf<Map<String, Int>>(emptyMap())
        private set

    init {
        loadSubscriptions()
        loadConfigsAndFastest()
    }

    // ----------------------------------------------------------------
    // Subscription Persistence & Management
    // ----------------------------------------------------------------

    private fun loadSubscriptions() {
        val jsonStr = prefs.getString("subscription_urls", null)
        val loaded = mutableListOf<String>()
        if (jsonStr != null) {
            try {
                val jsonArray = JSONArray(jsonStr)
                for (i in 0 until jsonArray.length()) {
                    loaded.add(jsonArray.getString(i))
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error loading subscriptions", e)
            }
        }
        if (loaded.isEmpty()) {
            val defaults = listOf(
                "https://raw.githubusercontent.com/freefq/free/master/v2ray",
                "https://raw.githubusercontent.com/v2rayng-community/v2ray/main/v2ray",
                "https://raw.githubusercontent.com/awesome-vpn/v2ray-free/main/sub"
            )
            loaded.addAll(defaults)
            saveSubscriptions(defaults)
        } else {
            subscriptionUrls = loaded
        }
    }

    fun saveSubscriptions(urls: List<String>) {
        subscriptionUrls = urls
        try {
            val array = JSONArray()
            urls.forEach { array.put(it) }
            prefs.edit().putString("subscription_urls", array.toString()).apply()
        } catch (e: Exception) {
            Log.e(TAG, "Error saving subscriptions", e)
        }
    }

    fun addSubscriptionUrl(url: String): Boolean {
        val trimmed = url.trim()
        if (!trimmed.startsWith("http://") && !trimmed.startsWith("https://")) return false
        if (subscriptionUrls.contains(trimmed)) return false
        val updated = subscriptionUrls + trimmed
        saveSubscriptions(updated)
        return true
    }

    fun removeSubscriptionUrl(url: String) {
        val updated = subscriptionUrls.filter { it != url }
        saveSubscriptions(updated)
    }

    // ----------------------------------------------------------------
    // Fetching Remote Subscriptions
    // ----------------------------------------------------------------

    fun fetchSubscriptions(
        onProgress: (String) -> Unit = {},
        onComplete: (newConfigsCount: Int, statusMessage: String) -> Unit = { _, _ -> }
    ) {
        Thread {
            val urls = subscriptionUrls
            if (urls.isEmpty()) {
                onComplete(0, "Список подписок пуст")
                return@Thread
            }

            var totalImported = 0
            var failedCount = 0

            for ((index, subUrl) in urls.withIndex()) {
                onProgress("Загрузка подписки (${index + 1}/${urls.size}): $subUrl")
                try {
                    val urlObj = URL(subUrl)
                    val conn = urlObj.openConnection() as HttpURLConnection
                    conn.connectTimeout = 7000
                    conn.readTimeout = 7000
                    conn.requestMethod = "GET"
                    conn.setRequestProperty("User-Agent", "v2rayNG/1.8.12")
                    conn.connect()

                    if (conn.responseCode == 200) {
                        val bodyText = conn.inputStream.bufferedReader().use { it.readText() }.trim()
                        val parsed = parseSubscriptionContent(bodyText)
                        val count = mergeNewConfigs(parsed)
                        totalImported += count
                    } else {
                        failedCount++
                    }
                } catch (e: Exception) {
                    Log.w(TAG, "Failed fetching sub URL: $subUrl", e)
                    failedCount++
                }
            }

            val status = if (totalImported > 0) {
                "Успешно загружено $totalImported новых серверов V2Ray"
            } else if (failedCount > 0) {
                "Не удалось обновить $failedCount источников подписок"
            } else {
                "Все подписки актуальны"
            }

            onComplete(totalImported, status)
        }.start()
    }

    // ----------------------------------------------------------------
    // Parsing V2Ray Subscription Payload
    // ----------------------------------------------------------------

    fun parseSubscriptionContent(rawText: String): List<V2RayServerConfig> {
        var processed = rawText.trim()
        
        // Attempt Base64 decode if not plain URI
        try {
            if (!processed.contains("://") && !processed.startsWith("{")) {
                val decodedBytes = Base64.decode(processed, Base64.DEFAULT)
                val decodedStr = String(decodedBytes, Charsets.UTF_8).trim()
                if (decodedStr.contains("://") || decodedStr.startsWith("{")) {
                    processed = decodedStr
                }
            }
        } catch (_: Exception) {}

        val lines = processed.lines().map { it.trim() }.filter { it.isNotEmpty() }
        val resultList = mutableListOf<V2RayServerConfig>()

        for (line in lines) {
            val parsed = parseUriLink(line) ?: parseJsonConfig(line)
            if (parsed != null) {
                resultList.add(parsed)
            }
        }
        return resultList
    }

    fun parseUriLink(uri: String): V2RayServerConfig? {
        try {
            val protocol = uri.substringBefore("://").uppercase()
            val afterProto = uri.substringAfter("://")

            if (protocol == "VMESS") {
                val base64Body = afterProto.substringBefore("#")
                val decoded = try {
                    String(Base64.decode(base64Body, Base64.DEFAULT), Charsets.UTF_8)
                } catch (_: Exception) { base64Body }

                val jsonStr = decoded.trim()
                if (jsonStr.startsWith("{")) {
                    val obj = JSONObject(jsonStr)
                    val server = obj.optString("add", "127.0.0.1")
                    val port = obj.optInt("port", 443)
                    val uuid = obj.optString("id", "")
                    val ps = obj.optString("ps", "VMess Server")
                    val security = obj.optString("tls", "tls")

                    return V2RayServerConfig(
                        id = "vmess_" + System.currentTimeMillis() + "_" + random.nextInt(10000),
                        name = ps,
                        protocol = "VMESS",
                        server = server,
                        port = port,
                        uuidOrPassword = uuid,
                        security = security,
                        rawContent = uri
                    )
                }
            }

            if (protocol == "VLESS" || protocol == "TROJAN" || protocol == "HYSTERIA2" || protocol == "HY2" || protocol == "SS" || protocol == "SHADOWSOCKS") {
                val mainPart = afterProto.substringBefore("#")
                val rawName = afterProto.substringAfter("#", "")
                val name = if (rawName.isNotEmpty()) {
                    try { URLDecoder.decode(rawName, "UTF-8") } catch (_: Exception) { rawName }
                } else "$protocol Server"

                val authAndServer = mainPart.substringBefore("?")
                val auth = authAndServer.substringBefore("@")
                val serverPort = authAndServer.substringAfter("@")
                val server = serverPort.substringBefore(":")
                val port = serverPort.substringAfter(":", "443").toIntOrNull() ?: 443

                val params = mainPart.substringAfter("?", "")
                val security = params.substringAfter("security=", "tls").substringBefore("&")

                return V2RayServerConfig(
                    id = "${protocol.lowercase()}_" + System.currentTimeMillis() + "_" + random.nextInt(10000),
                    name = name,
                    protocol = protocol,
                    server = server,
                    port = port,
                    uuidOrPassword = auth,
                    security = security,
                    rawContent = uri
                )
            }
        } catch (e: Exception) {
            Log.w(TAG, "Error parsing URI: $uri", e)
        }
        return null
    }

    private fun parseJsonConfig(jsonStr: String): V2RayServerConfig? {
        try {
            if (!jsonStr.startsWith("{")) return null
            val obj = JSONObject(jsonStr)
            val outbounds = obj.optJSONArray("outbounds") ?: return null
            if (outbounds.length() > 0) {
                val item = outbounds.getJSONObject(0)
                val protocol = item.optString("protocol", "VLESS").uppercase()
                val settings = item.optJSONObject("settings")
                val vnext = settings?.optJSONArray("vnext")
                if (vnext != null && vnext.length() > 0) {
                    val serverObj = vnext.getJSONObject(0)
                    val address = serverObj.optString("address", "127.0.0.1")
                    val port = serverObj.optInt("port", 443)
                    val users = serverObj.optJSONArray("users")
                    val uuid = if (users != null && users.length() > 0) users.getJSONObject(0).optString("id", "") else ""
                    return V2RayServerConfig(
                        id = "json_" + System.currentTimeMillis() + "_" + random.nextInt(10000),
                        name = "Xray Outbound ($protocol)",
                        protocol = protocol,
                        server = address,
                        port = port,
                        uuidOrPassword = uuid,
                        security = "tls",
                        rawContent = jsonStr
                    )
                }
            }
        } catch (_: Exception) {}
        return null
    }

    private fun mergeNewConfigs(newItems: List<V2RayServerConfig>): Int {
        if (newItems.isEmpty()) return 0
        val existingKeys = configs.map { "${it.server}:${it.port}" }.toSet()
        val toAdd = newItems.filter { !existingKeys.contains("${it.server}:${it.port}") }
        if (toAdd.isNotEmpty()) {
            val updated = configs + toAdd
            configs = updated
            saveConfigs(updated)
        }
        return toAdd.size
    }

    // ----------------------------------------------------------------
    // Speed Testing & Removing Dead Nodes
    // ----------------------------------------------------------------

    fun testServerSocketPing(config: V2RayServerConfig): Int {
        return try {
            val start = System.currentTimeMillis()
            val socket = Socket()
            socket.connect(InetSocketAddress(config.server, config.port), 1200)
            socket.close()
            val duration = (System.currentTimeMillis() - start).toInt()
            if (duration <= 0) 1 else duration
        } catch (e: Exception) {
            -1
        }
    }

    fun testAndPersistFastestConfig(
        onComplete: (fastest: V2RayServerConfig?, pingMs: Int) -> Unit
    ) {
        if (isTesting) return
        isTesting = true

        Thread {
            val currentList = configs.toList()
            val pingsMap = mutableMapOf<String, Int>()
            var bestConfig: V2RayServerConfig? = null
            var bestPing = Int.MAX_VALUE

            for (cfg in currentList) {
                pingsMap[cfg.id] = -2 // Testing in progress
                serverPings = pingsMap.toMap()

                val latency = testServerSocketPing(cfg)
                cfg.pingMs = latency
                pingsMap[cfg.id] = latency
                serverPings = pingsMap.toMap()

                if (latency > 0 && latency < bestPing) {
                    bestPing = latency
                    bestConfig = cfg
                }
                Thread.sleep(60)
            }

            isTesting = false

            if (bestConfig != null) {
                saveFastestConfig(bestConfig)
                onComplete(bestConfig, bestPing)
            } else {
                onComplete(null, -1)
            }
        }.start()
    }

    fun removeDeadServers(
        onComplete: (removedCount: Int, remainingCount: Int) -> Unit
    ) {
        if (isTesting) return
        isTesting = true

        Thread {
            val currentList = configs.toList()
            val alive = mutableListOf<V2RayServerConfig>()
            var removed = 0

            for (cfg in currentList) {
                val latency = testServerSocketPing(cfg)
                if (latency > 0) {
                    cfg.pingMs = latency
                    alive.add(cfg)
                } else {
                    removed++
                }
                Thread.sleep(60)
            }

            if (alive.isNotEmpty()) {
                configs = alive
                saveConfigs(alive)
            }

            isTesting = false
            onComplete(removed, configs.size)
        }.start()
    }

    // ----------------------------------------------------------------
    // Configurations Persistence
    // ----------------------------------------------------------------

    fun saveFastestConfig(config: V2RayServerConfig) {
        fastestConfig = config
        try {
            val obj = JSONObject().apply {
                put("id", config.id)
                put("name", config.name)
                put("protocol", config.protocol)
                put("server", config.server)
                put("port", config.port)
                put("uuid", config.uuidOrPassword)
                put("security", config.security)
                put("raw", config.rawContent)
                put("pingMs", config.pingMs)
            }
            prefs.edit().putString("fastest_config", obj.toString()).apply()
            Log.d(TAG, "Persisted fastest config: ${config.name} (${config.pingMs}ms)")
        } catch (e: Exception) {
            Log.e(TAG, "Failed saving fastest config", e)
        }
    }

    private fun saveConfigs(list: List<V2RayServerConfig>) {
        try {
            val array = JSONArray()
            for (c in list) {
                val obj = JSONObject().apply {
                    put("id", c.id)
                    put("name", c.name)
                    put("protocol", c.protocol)
                    put("server", c.server)
                    put("port", c.port)
                    put("uuid", c.uuidOrPassword)
                    put("security", c.security)
                    put("raw", c.rawContent)
                    put("pingMs", c.pingMs)
                }
                array.put(obj)
            }
            prefs.edit().putString("saved_v2ray_configs", array.toString()).apply()
        } catch (e: Exception) {
            Log.e(TAG, "Failed saving v2ray configs", e)
        }
    }

    private fun loadConfigsAndFastest() {
        val jsonString = prefs.getString("saved_v2ray_configs", null)
        val loaded = mutableListOf<V2RayServerConfig>()

        if (jsonString != null) {
            try {
                val array = JSONArray(jsonString)
                for (i in 0 until array.length()) {
                    val obj = array.getJSONObject(i)
                    loaded.add(
                        V2RayServerConfig(
                            id = obj.getString("id"),
                            name = obj.getString("name"),
                            protocol = obj.getString("protocol"),
                            server = obj.getString("server"),
                            port = obj.getInt("port"),
                            uuidOrPassword = obj.getString("uuid"),
                            security = obj.optString("security", "tls"),
                            rawContent = obj.getString("raw"),
                            pingMs = obj.optInt("pingMs", -1)
                        )
                    )
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error parsing saved v2ray configs", e)
            }
        }

        configs = loaded

        // Load persisted fastest config
        val fastestStr = prefs.getString("fastest_config", null)
        if (fastestStr != null) {
            try {
                val obj = JSONObject(fastestStr)
                fastestConfig = V2RayServerConfig(
                    id = obj.getString("id"),
                    name = obj.getString("name"),
                    protocol = obj.getString("protocol"),
                    server = obj.getString("server"),
                    port = obj.getInt("port"),
                    uuidOrPassword = obj.getString("uuid"),
                    security = obj.optString("security", "tls"),
                    rawContent = obj.getString("raw"),
                    pingMs = obj.optInt("pingMs", -1)
                )
            } catch (e: Exception) {
                Log.e(TAG, "Error parsing fastest config", e)
            }
        }
    }

    companion object {
        private const val TAG = "V2RaySubManager"
    }
}
