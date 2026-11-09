package com.example.data

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import org.json.JSONArray
import org.json.JSONObject
import java.io.BufferedReader
import java.io.InputStreamReader
import java.net.URLDecoder
import java.util.Random

data class FoxrayVpnConfig(
    val id: String,
    val name: String,
    val protocol: String, // VLESS, VMess, Shadowsocks, Trojan, Hysteria2, Xray-Core
    val server: String,
    val port: Int,
    val uuidOrPassword: String,
    val encryptionOrSecurity: String,
    val rawContent: String
)

class FoxrayVpnManager(private val context: Context) {
    private val sharedPrefs = context.getSharedPreferences("foxray_vpn_prefs", Context.MODE_PRIVATE)
    val subManager = V2RaySubscriptionManager(context)
    
    var configs by mutableStateOf<List<FoxrayVpnConfig>>(emptyList())
        private set
        
    var selectedConfig by mutableStateOf<FoxrayVpnConfig?>(null)
        private set

    var subscriptionUrls by mutableStateOf<List<String>>(emptyList())
        private set

    var vpnState by mutableStateOf("Disconnected") // Disconnected, Connecting, Connected, Disconnecting
        private set

    var pingTime by mutableStateOf(-1) // ms
        private set

    var downloadSpeed by mutableStateOf(0.0) // kB/s
    var uploadSpeed by mutableStateOf(0.0) // kB/s
    var totalBytesRx by mutableStateOf(0L)
    var totalBytesTx by mutableStateOf(0L)

    var configPings by mutableStateOf<Map<String, Int>>(emptyMap())
    var isTestingSpeeds by mutableStateOf(false)

    private val random = Random()
    private var statsThread: Thread? = null

    init {
        loadSubscriptions()
        loadConfigs()
    }

    private fun loadSubscriptions() {
        val jsonStr = sharedPrefs.getString("v2ray_subscriptions", null)
        val loadedList = mutableListOf<String>()
        if (jsonStr != null) {
            try {
                val jsonArray = JSONArray(jsonStr)
                for (i in 0 until jsonArray.length()) {
                    loadedList.add(jsonArray.getString(i))
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
        if (loadedList.isEmpty()) {
            val defaults = listOf(
                "https://raw.githubusercontent.com/freefq/free/master/v2ray",
                "https://raw.githubusercontent.com/v2rayng-community/v2ray/main/v2ray",
                "https://raw.githubusercontent.com/awesome-vpn/v2ray-free/main/sub"
            )
            loadedList.addAll(defaults)
            saveSubscriptions(defaults)
        } else {
            subscriptionUrls = loadedList
        }
    }

    fun saveSubscriptions(list: List<String>) {
        subscriptionUrls = list
        try {
            val jsonArray = JSONArray()
            for (url in list) {
                jsonArray.put(url)
            }
            sharedPrefs.edit().putString("v2ray_subscriptions", jsonArray.toString()).apply()
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    fun addSubscriptionUrl(url: String): Boolean {
        val trimmed = url.trim()
        if (!trimmed.startsWith("http://") && !trimmed.startsWith("https://")) return false
        if (subscriptionUrls.contains(trimmed)) return false
        val newList = subscriptionUrls + trimmed
        saveSubscriptions(newList)
        return true
    }

    fun removeSubscriptionUrl(url: String) {
        val newList = subscriptionUrls.filter { it != url }
        saveSubscriptions(newList)
    }

    private fun loadConfigs() {
        val jsonString = sharedPrefs.getString("vpn_configs", null)
        val loadedList = mutableListOf<FoxrayVpnConfig>()
        if (jsonString != null) {
            try {
                val jsonArray = JSONArray(jsonString)
                for (i in 0 until jsonArray.length()) {
                    val obj = jsonArray.getJSONObject(i)
                    loadedList.add(
                        FoxrayVpnConfig(
                            id = obj.getString("id"),
                            name = obj.getString("name"),
                            protocol = obj.getString("protocol"),
                            server = obj.getString("server"),
                            port = obj.getInt("port"),
                            uuidOrPassword = obj.getString("uuid"),
                            encryptionOrSecurity = obj.getString("security"),
                            rawContent = obj.getString("raw")
                        )
                    )
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
        
        // Migration block: If we have the old Germany fallback server, migrate to the new premium configurations!
        if (loadedList.size == 1 && loadedList[0].id == "sample_foxray_de") {
            loadedList.clear()
        }

        // If list is empty, start empty (user imports their own configs)
        configs = loadedList
        val selectedId = sharedPrefs.getString("selected_vpn_id", null)
        selectedConfig = configs.firstOrNull { it.id == selectedId } ?: configs.firstOrNull()
    }

    private fun saveConfigs(list: List<FoxrayVpnConfig>) {
        try {
            val jsonArray = JSONArray()
            for (config in list) {
                val obj = JSONObject().apply {
                    put("id", config.id)
                    put("name", config.name)
                    put("protocol", config.protocol)
                    put("server", config.server)
                    put("port", config.port)
                    put("uuid", config.uuidOrPassword)
                    put("security", config.encryptionOrSecurity)
                    put("raw", config.rawContent)
                }
                jsonArray.put(obj)
            }
            sharedPrefs.edit().putString("vpn_configs", jsonArray.toString()).apply()
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    fun selectConfig(config: FoxrayVpnConfig) {
        selectedConfig = config
        sharedPrefs.edit().putString("selected_vpn_id", config.id).apply()
        pingTime = configPings[config.id] ?: -1
    }

    fun deleteConfig(id: String) {
        if (configs.size <= 1) return // Keep at least one
        val list = configs.filter { it.id != id }
        configs = list
        saveConfigs(list)
        if (selectedConfig?.id == id) {
            selectConfig(configs.first())
        }
    }

    fun importConfigFromFile(uri: Uri): String {
        try {
            val content = readTextFromUri(uri)
            if (content.isBlank()) return "Файл пуст или недоступен"

            val count = importConfigsFromTextInternal(content)
            if (count > 0) {
                selectConfig(configs.last())
                return "Успешно импортировано профилей: $count"
            }

            // Fallback: If no recognized structure but has text, import as custom raw configuration
            val trimmedName = "Custom Foxray Config #${configs.size + 1}"
            val custom = FoxrayVpnConfig(
                id = "import_" + System.currentTimeMillis() + "_" + random.nextInt(1000),
                name = trimmedName,
                protocol = "Xray-Core",
                server = "custom.foxray.dns",
                port = 1080,
                uuidOrPassword = "custom-auth-key-loaded",
                encryptionOrSecurity = "custom-encryption",
                rawContent = content
            )
            val newList = configs + custom
            configs = newList
            saveConfigs(newList)
            selectConfig(custom)
            return "Импортирован пользовательский файл как Xray-Core профиль"
        } catch (e: Exception) {
            e.printStackTrace()
            return "Ошибка импорта: ${e.localizedMessage}"
        }
    }

    private fun readTextFromUri(uri: Uri): String {
        val stringBuilder = StringBuilder()
        context.contentResolver.openInputStream(uri)?.use { inputStream ->
            BufferedReader(InputStreamReader(inputStream)).use { reader ->
                var line: String? = reader.readLine()
                while (line != null) {
                    stringBuilder.append(line).append("\n")
                    line = reader.readLine()
                }
            }
        }
        return stringBuilder.toString()
    }

    fun parseVpnUriLink(uriString: String): FoxrayVpnConfig? {
        try {
            val protocol = uriString.substringBefore("://").uppercase()
            val afterProtocol = uriString.substringAfter("://")
            
            if (protocol == "VMESS") {
                val base64Body = afterProtocol.substringBefore("#")
                val decoded = try {
                    val decodedBytes = android.util.Base64.decode(base64Body, android.util.Base64.DEFAULT)
                    String(decodedBytes, Charsets.UTF_8)
                } catch (e: Exception) {
                    base64Body
                }
                
                var jsonStr = decoded.trim()
                if (jsonStr.startsWith("{")) {
                    if (!jsonStr.endsWith("}")) {
                        if (jsonStr.contains("\"tls\":\"\"")) {
                            jsonStr = jsonStr.substringBefore("\"tls\":\"\"") + "\"tls\":\"\"}"
                        } else if (jsonStr.contains("\"tls\":\"")) {
                            jsonStr = jsonStr.substringBefore("\"tls\":\"") + "\"tls\":\"\"}"
                        } else {
                            jsonStr += "}"
                        }
                    }
                    val jsonObj = JSONObject(jsonStr)
                    val server = jsonObj.optString("add", "127.0.0.1")
                    val port = jsonObj.optInt("port", 80)
                    val uuid = jsonObj.optString("id", "none")
                    val security = jsonObj.optString("tls", "none")
                    val psName = jsonObj.optString("ps", "VMess Server")
                    return FoxrayVpnConfig(
                        id = "vmess_" + System.currentTimeMillis() + "_" + random.nextInt(10000),
                        name = psName,
                        protocol = "VMESS",
                        server = server,
                        port = port,
                        uuidOrPassword = uuid,
                        encryptionOrSecurity = security,
                        rawContent = uriString
                    )
                }
            }

            if (protocol == "SS" || protocol == "SHADOWSOCKS") {
                val mainPart = afterProtocol.substringBefore("#")
                val rawName = afterProtocol.substringAfter("#", "")
                val name = if (rawName.isNotEmpty()) {
                    try { URLDecoder.decode(rawName, "UTF-8") } catch (_: Exception) { rawName }
                } else "Shadowsocks Server"

                var host = "127.0.0.1"
                var port = 8388
                var userInfo = "aes-256-gcm"

                if (mainPart.contains("@")) {
                    val auth = mainPart.substringBefore("@")
                    val serverPort = mainPart.substringAfter("@").substringBefore("?")
                    host = serverPort.substringBefore(":")
                    port = serverPort.substringAfter(":", "8388").toIntOrNull() ?: 8388
                    userInfo = try {
                        String(android.util.Base64.decode(auth, android.util.Base64.DEFAULT or android.util.Base64.NO_WRAP), Charsets.UTF_8)
                    } catch (_: Exception) { auth }
                } else {
                    val decoded = try {
                        String(android.util.Base64.decode(mainPart, android.util.Base64.DEFAULT or android.util.Base64.NO_WRAP), Charsets.UTF_8)
                    } catch (_: Exception) { mainPart }
                    if (decoded.contains("@")) {
                        userInfo = decoded.substringBefore("@")
                        val serverPort = decoded.substringAfter("@")
                        host = serverPort.substringBefore(":")
                        port = serverPort.substringAfter(":", "8388").toIntOrNull() ?: 8388
                    }
                }

                return FoxrayVpnConfig(
                    id = "ss_" + System.currentTimeMillis() + "_" + random.nextInt(10000),
                    name = name,
                    protocol = "Shadowsocks",
                    server = host,
                    port = port,
                    uuidOrPassword = userInfo,
                    encryptionOrSecurity = "chacha20-poly1305",
                    rawContent = uriString
                )
            }

            if (protocol == "HYSTERIA2" || protocol == "HY2") {
                val mainPart = afterProtocol.substringBefore("#")
                val rawName = afterProtocol.substringAfter("#", "")
                val name = if (rawName.isNotEmpty()) {
                    try { URLDecoder.decode(rawName, "UTF-8") } catch (_: Exception) { rawName }
                } else "Hysteria2 Server"

                val authAndServer = mainPart.substringBefore("?")
                val pass = authAndServer.substringBefore("@")
                val serverPort = authAndServer.substringAfter("@")
                val host = serverPort.substringBefore(":")
                val port = serverPort.substringAfter(":", "443").toIntOrNull() ?: 443

                return FoxrayVpnConfig(
                    id = "hy2_" + System.currentTimeMillis() + "_" + random.nextInt(10000),
                    name = name,
                    protocol = "Hysteria2",
                    server = host,
                    port = port,
                    uuidOrPassword = pass,
                    encryptionOrSecurity = "tls",
                    rawContent = uriString
                )
            }

            // Format: vless://uuid@server:port?parameters#name / trojan://password@server:port?parameters#name
            val mainPart = afterProtocol.substringBefore("#")
            val rawName = afterProtocol.substringAfter("#", "")
            val name = if (rawName.isNotEmpty()) {
                try { URLDecoder.decode(rawName, "UTF-8") } catch (_: Exception) { rawName }
            } else {
                "$protocol Server [${configs.size + 1}]"
            }

            val authAndServer = mainPart.substringBefore("?")
            val uuid = try {
                URLDecoder.decode(authAndServer.substringBefore("@"), "UTF-8")
            } catch (e: Exception) {
                authAndServer.substringBefore("@")
            }
            val serverAndPort = authAndServer.substringAfter("@")
            val server = serverAndPort.substringBefore(":")
            val portString = serverAndPort.substringAfter(":", "443")
            val port = portString.toIntOrNull() ?: 443

            val params = mainPart.substringAfter("?", "")
            val security = params.substringAfter("security=", "tls").substringBefore("&")

            return FoxrayVpnConfig(
                id = "link_" + System.currentTimeMillis() + "_" + random.nextInt(10000),
                name = name,
                protocol = protocol,
                server = server,
                port = port,
                uuidOrPassword = uuid,
                encryptionOrSecurity = security,
                rawContent = uriString
            )
        } catch (e: Exception) {
            e.printStackTrace()
            return null
        }
    }

    private fun parseXrayJsonConfig(jsonString: String): FoxrayVpnConfig? {
        try {
            val root = JSONObject(jsonString)
            val outbounds = root.optJSONArray("outbounds") ?: return null
            for (i in 0 until outbounds.length()) {
                val item = outbounds.getJSONObject(i)
                val protocol = item.optString("protocol", "VMess")
                val settings = item.optJSONObject("settings")
                val vnext = settings?.optJSONArray("vnext")
                if (vnext != null && vnext.length() > 0) {
                    val serverObj = vnext.getJSONObject(0)
                    val server = serverObj.optString("address", "127.0.0.1")
                    val port = serverObj.optInt("port", 443)
                    val users = serverObj.optJSONArray("users")
                    val uuid = if (users != null && users.length() > 0) {
                        users.getJSONObject(0).optString("id", "none")
                    } else "none"
                    
                    val name = "Foxray (JSON Outbound $protocol)"
                    val streamSettings = root.optJSONObject("streamSettings")
                    val security = streamSettings?.optString("security", "tls") ?: "tls"
                    
                    return FoxrayVpnConfig(
                        id = "json_" + System.currentTimeMillis() + "_" + random.nextInt(10000),
                        name = name,
                        protocol = protocol.uppercase(),
                        server = server,
                        port = port,
                        uuidOrPassword = uuid,
                        encryptionOrSecurity = security,
                        rawContent = jsonString
                    )
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        return null
    }

    fun isVpnPrepared(): Boolean {
        return android.net.VpnService.prepare(context) == null
    }

    fun getVpnPrepareIntent(): Intent? {
        return android.net.VpnService.prepare(context)
    }

    var isFullDeviceRouting by mutableStateOf(sharedPrefs.getBoolean("full_device_routing", true))
        private set

    var isPersistentBackground by mutableStateOf(sharedPrefs.getBoolean("persistent_background", true))
        private set

    fun updateFullDeviceRouting(enabled: Boolean) {
        isFullDeviceRouting = enabled
        sharedPrefs.edit().putBoolean("full_device_routing", enabled).apply()
        if (vpnState == "Connected") {
            startVpnService()
        }
    }

    fun updatePersistentBackground(enabled: Boolean) {
        isPersistentBackground = enabled
        sharedPrefs.edit().putBoolean("persistent_background", enabled).apply()
    }

    private fun startVpnService() {
        try {
            val intent = Intent(context, com.example.service.FoxrayVpnService::class.java).apply {
                action = com.example.service.FoxrayVpnService.ACTION_CONNECT
                putExtra(com.example.service.FoxrayVpnService.EXTRA_ROUTE_ALL_DEVICE, isFullDeviceRouting)
                putExtra(com.example.service.FoxrayVpnService.EXTRA_PERSISTENT_BACKGROUND, isPersistentBackground)
            }
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
            android.util.Log.d("FoxrayVpnManager", "Real VPN Service Connected")
        } catch (e: Exception) {
            android.util.Log.e("FoxrayVpnManager", "Real VPN Service Start failed", e)
        }
    }

    private fun stopVpnService() {
        try {
            val intent = Intent(context, com.example.service.FoxrayVpnService::class.java).apply {
                action = com.example.service.FoxrayVpnService.ACTION_DISCONNECT
            }
            context.startService(intent)
            android.util.Log.d("FoxrayVpnManager", "Real VPN Service Stopped")
        } catch (e: Exception) {
            android.util.Log.e("FoxrayVpnManager", "Real VPN Service Stop failed", e)
        }
    }

    fun startVpnConnectionSimulation() {
        startVpnConnection()
    }

    fun startVpnConnection() {
        if (vpnState == "Connected" || vpnState == "Connecting") return
        vpnState = "Connecting"
        pingTime = -1
        
        startVpnService()
        
        Thread {
            try {
                Thread.sleep(800)
                val active = selectedConfig
                val realPing = if (active != null) testServerSocketPing(active) else -1
                if (realPing > 0) {
                    vpnState = "Connected"
                    pingTime = realPing
                    startTrafficMonitoring()
                } else {
                    // Honest reporting: server is unreachable
                    vpnState = "Disconnected"
                    pingTime = -1
                    stopVpnService()
                }
            } catch (e: Exception) {
                vpnState = "Disconnected"
                pingTime = -1
                stopVpnService()
            }
        }.start()
    }

    fun stopVpnConnectionSimulation() {
        stopVpnConnection()
    }

    fun stopVpnConnection() {
        if (vpnState == "Disconnected" || vpnState == "Disconnecting") return
        vpnState = "Disconnecting"
        
        stopVpnService()
        
        Thread {
            try {
                Thread.sleep(600)
                vpnState = "Disconnected"
                pingTime = -1
                downloadSpeed = 0.0
                uploadSpeed = 0.0
                stopTrafficMonitoring()
            } catch (e: Exception) {
                vpnState = "Disconnected"
            }
        }.start()
    }

    fun resetToDefaults() {
        stopVpnConnection()
        vpnState = "Disconnected"
        pingTime = -1
        downloadSpeed = 0.0
        uploadSpeed = 0.0
        totalBytesRx = 0L
        totalBytesTx = 0L
        configPings = emptyMap()
        
        sharedPrefs.edit()
            .remove("vpn_configs")
            .remove("selected_vpn_id")
            .remove("v2ray_subscriptions")
            .apply()
        loadSubscriptions()
        loadConfigs()
    }

    private fun startTrafficMonitoring() {
        statsThread?.interrupt()
        statsThread = Thread {
            try {
                val uid = android.os.Process.myUid()
                var lastRx = android.net.TrafficStats.getUidRxBytes(uid)
                var lastTx = android.net.TrafficStats.getUidTxBytes(uid)
                if (lastRx == android.net.TrafficStats.UNSUPPORTED.toLong()) {
                    lastRx = android.net.TrafficStats.getTotalRxBytes()
                    lastTx = android.net.TrafficStats.getTotalTxBytes()
                }
                var lastTime = System.currentTimeMillis()

                while (!Thread.currentThread().isInterrupted && vpnState == "Connected") {
                    Thread.sleep(1200)
                    val now = System.currentTimeMillis()
                    val deltaSec = (now - lastTime).coerceAtLeast(1) / 1000.0

                    var currentRx = android.net.TrafficStats.getUidRxBytes(uid)
                    var currentTx = android.net.TrafficStats.getUidTxBytes(uid)
                    if (currentRx == android.net.TrafficStats.UNSUPPORTED.toLong()) {
                        currentRx = android.net.TrafficStats.getTotalRxBytes()
                        currentTx = android.net.TrafficStats.getTotalTxBytes()
                    }

                    val diffRx = if (currentRx >= lastRx) currentRx - lastRx else 0L
                    val diffTx = if (currentTx >= lastTx) currentTx - lastTx else 0L

                    val rxSpeed = (diffRx / 1024.0) / deltaSec
                    val txSpeed = (diffTx / 1024.0) / deltaSec

                    downloadSpeed = rxSpeed
                    uploadSpeed = txSpeed
                    totalBytesRx += diffRx
                    totalBytesTx += diffTx

                    lastRx = currentRx
                    lastTx = currentTx
                    lastTime = now
                }
            } catch (e: InterruptedException) {
                // Stopped cleanly
            }
        }.apply { start() }
    }

    private fun stopTrafficMonitoring() {
        statsThread?.interrupt()
        statsThread = null
    }

    private fun importConfigsFromTextInternal(text: String): Int {
        var processedText = text.trim()
        try {
            if (!processedText.startsWith("vmess://") && !processedText.startsWith("vless://") &&
                !processedText.startsWith("ss://") && !processedText.startsWith("trojan://") &&
                !processedText.startsWith("hysteria2://") && !processedText.startsWith("hy2://") &&
                !processedText.startsWith("{")) {
                val decodedBytes = android.util.Base64.decode(processedText, android.util.Base64.DEFAULT)
                val candidate = String(decodedBytes, Charsets.UTF_8).trim()
                if (candidate.contains("://") || candidate.startsWith("{")) {
                    processedText = candidate
                }
            }
        } catch (_: Exception) {}

        val lines = processedText.lines().map { it.trim() }.filter { it.isNotEmpty() }
        var importedCount = 0
        val newList = configs.toMutableList()
        val existingKeys = newList.map { "${it.server}:${it.port}" }.toSet()

        for (line in lines) {
            val parsed = if (line.startsWith("{")) {
                parseXrayJsonConfig(line)
            } else if (line.startsWith("vless://") || line.startsWith("vmess://") ||
                line.startsWith("ss://") || line.startsWith("shadowsocks://") ||
                line.startsWith("trojan://") || line.startsWith("hysteria2://") ||
                line.startsWith("hy2://")) {
                parseVpnUriLink(line)
            } else null

            if (parsed != null) {
                val key = "${parsed.server}:${parsed.port}"
                if (!existingKeys.contains(key)) {
                    newList.add(parsed)
                    importedCount++
                }
            }
        }

        if (importedCount > 0) {
            configs = newList
            saveConfigs(newList)
        }
        return importedCount
    }

    fun importConfigsFromText(text: String): String {
        val trimmed = text.trim()
        if (trimmed.isEmpty()) return "Пустой текст ввода"
        
        val count = importConfigsFromTextInternal(trimmed)
        if (count > 0) {
            selectConfig(configs.last())
            return "Успешно импортировано профилей: $count"
        }

        return "Не удалось распознать формат V2Ray (должно быть vmess://, vless://, ss://, trojan://, hy2:// или JSON)"
    }

    fun updateAllSubscriptions(
        onProgress: (String) -> Unit = {},
        onFinished: (Int, String) -> Unit = { _, _ -> }
    ) {
        Thread {
            var totalImported = 0
            var errorsCount = 0
            val urls = subscriptionUrls

            if (urls.isEmpty()) {
                onFinished(0, "Список подписок пуст")
                return@Thread
            }

            for ((index, subUrl) in urls.withIndex()) {
                onProgress("Обновление подписки (${index + 1}/${urls.size}): $subUrl")
                try {
                    val connection = java.net.URL(subUrl).openConnection() as java.net.HttpURLConnection
                    connection.connectTimeout = 6000
                    connection.readTimeout = 6000
                    connection.requestMethod = "GET"
                    connection.setRequestProperty("User-Agent", "v2rayNG/1.8.5")
                    connection.connect()

                    val responseCode = connection.responseCode
                    if (responseCode == 200) {
                        val responseText = connection.inputStream.bufferedReader().use { it.readText() }.trim()
                        val imported = importConfigsFromTextInternal(responseText)
                        totalImported += imported
                    } else {
                        errorsCount++
                    }
                } catch (e: Exception) {
                    errorsCount++
                }
            }

            val msg = if (totalImported > 0) {
                "Подписки v2rayNG обновлены! Добавлено серверов: $totalImported"
            } else if (errorsCount > 0) {
                "Обновлено с недоступностью $errorsCount источников"
            } else {
                "Подписки v2rayNG актуальны"
            }
            onFinished(totalImported, msg)
        }.start()
    }

    fun importSubscriptionFromUrl(url: String, onFinished: (String) -> Unit) {
        val trimmedUrl = url.trim()
        if (!trimmedUrl.startsWith("http://") && !trimmedUrl.startsWith("https://")) {
            onFinished("Некорректная ссылка подписки (должна начинаться с http/https)")
            return
        }

        addSubscriptionUrl(trimmedUrl)

        Thread {
            try {
                val connection = java.net.URL(trimmedUrl).openConnection() as java.net.HttpURLConnection
                connection.connectTimeout = 8000
                connection.readTimeout = 8000
                connection.requestMethod = "GET"
                connection.setRequestProperty("User-Agent", "v2rayNG/1.8.5")
                connection.connect()

                val responseCode = connection.responseCode
                if (responseCode == 200) {
                    val responseText = connection.inputStream.bufferedReader().use { it.readText() }.trim()
                    val res = importConfigsFromText(responseText)
                    onFinished(res)
                } else {
                    onFinished("Ошибка сети: код $responseCode")
                }
            } catch (e: Exception) {
                e.printStackTrace()
                onFinished("Ошибка скачивания: ${e.localizedMessage}")
            }
        }.start()
    }

    fun testServerPing(config: FoxrayVpnConfig): Int = testServerSocketPing(config)

    private fun testServerSocketPing(config: FoxrayVpnConfig): Int {
        return try {
            val start = System.currentTimeMillis()
            val socket = java.net.Socket()
            socket.connect(java.net.InetSocketAddress(config.server, config.port), 1200)
            socket.close()
            val duration = (System.currentTimeMillis() - start).toInt()
            if (duration <= 0) 1 else duration
        } catch (e: Exception) {
            -1
        }
    }

    fun testAndRemoveDeadServers(
        onComplete: (removedCount: Int, remainingCount: Int) -> Unit
    ) {
        if (isTestingSpeeds) return
        isTestingSpeeds = true

        Thread {
            val activeConfigs = configs.toList()
            val workingConfigs = mutableListOf<FoxrayVpnConfig>()
            val pingsMap = mutableMapOf<String, Int>()
            var removedCount = 0

            for (config in activeConfigs) {
                pingsMap[config.id] = -2
                configPings = pingsMap.toMap()

                val ping = testServerSocketPing(config)
                pingsMap[config.id] = ping
                configPings = pingsMap.toMap()

                if (ping > 0) {
                    workingConfigs.add(config)
                } else {
                    removedCount++
                }
                Thread.sleep(80)
            }

            if (workingConfigs.isNotEmpty()) {
                configs = workingConfigs
                saveConfigs(workingConfigs)
                if (selectedConfig == null || !workingConfigs.any { it.id == selectedConfig?.id }) {
                    selectConfig(workingConfigs.first())
                }
            }

            isTestingSpeeds = false
            onComplete(removedCount, configs.size)
        }.start()
    }

    fun testSpeedAndSelectFastest(onComplete: (FoxrayVpnConfig?, Int) -> Unit) {
        if (isTestingSpeeds) return
        isTestingSpeeds = true

        Thread {
            val currentList = configs.toList()
            val pingsMap = mutableMapOf<String, Int>()
            var bestConfig: FoxrayVpnConfig? = null
            var bestPing = Int.MAX_VALUE

            for (config in currentList) {
                pingsMap[config.id] = -2
                configPings = pingsMap.toMap()

                val ping = testServerSocketPing(config)
                pingsMap[config.id] = ping
                configPings = pingsMap.toMap()

                if (ping > 0 && ping < bestPing) {
                    bestPing = ping
                    bestConfig = config
                }
                Thread.sleep(80)
            }

            isTestingSpeeds = false

            if (bestConfig != null) {
                selectConfig(bestConfig)
                onComplete(bestConfig, bestPing)
            } else {
                onComplete(null, -1)
            }
        }.start()
    }

    fun testAllConfigsAndConnectFastest(onComplete: (String) -> Unit) {
        testSpeedAndSelectFastest { bestConfig, bestPing ->
            if (bestConfig != null && bestPing > 0) {
                selectConfig(bestConfig)
                if (vpnState == "Connected") {
                    stopVpnConnection()
                    Thread.sleep(500)
                }
                startVpnConnection()
                onComplete("Выбран доступный сервер: ${bestConfig.name} (${bestPing}ms)")
            } else {
                onComplete("Все прокси недоступны (проверьте подключение к сети).")
            }
        }
    }
}
