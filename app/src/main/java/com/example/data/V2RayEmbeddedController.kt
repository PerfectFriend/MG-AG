package com.example.data

import android.content.Context
import android.os.Build
import android.util.Log
import java.io.File
import kotlin.concurrent.thread

class V2RayEmbeddedController(
    private val context: Context,
    private val localPort: Int = 10808,
    private val torSocksPort: Int = 9050,
    private val onLog: (String) -> Unit,
    private val onStatusChange: (String) -> Unit // "INACTIVE", "INITIALIZING", "ACTIVE"
) {
    private var xrayProcess: Process? = null

    fun start() {
        if (xrayProcess != null) {
            onLog("[V2RayEmbedded] Process is already running.")
            return
        }

        thread(name = "V2RayEmbeddedStartThread") {
            try {
                onStatusChange("INITIALIZING")
                onLog("[V2RayEmbedded] Preparing V2Ray/Xray binary installation...")

                val nativeXray = File(context.applicationInfo.nativeLibraryDir, "libxray.so")
                val binDir = context.getDir("bin", Context.MODE_PRIVATE)
                val fallbackXray = File(binDir, "xray")

                val xrayFile: File = if (nativeXray.exists() && nativeXray.canExecute()) {
                    onLog("[V2RayEmbedded] Found native libxray.so in nativeLibraryDir: ${nativeXray.absolutePath}")
                    nativeXray
                } else {
                    val arch = if (Build.SUPPORTED_ABIS.contains("arm64-v8a")) "arm64" else "x86_64"
                    onLog("[V2RayEmbedded] Extracting xray binary for architecture: $arch")
                    try {
                        context.assets.open("bin/xray-$arch").use { input ->
                            fallbackXray.outputStream().use { output ->
                                input.copyTo(output)
                            }
                        }
                        fallbackXray.setExecutable(true, true)
                        onLog("[V2RayEmbedded] Unpacked xray binary successfully.")
                    } catch (e: Exception) {
                        onLog("[V2RayEmbedded] Warning unzipping asset directly: ${e.message}")
                    }
                    fallbackXray
                }

                if (!xrayFile.exists()) {
                    onLog("[V2RayEmbedded] ERROR: Xray binary is missing and could not be loaded!")
                    onStatusChange("INACTIVE")
                    return@thread
                }

                xrayFile.setExecutable(true, true)

                // Create advanced Anti-Censorship Config JSON (VLESS-Reality / SOCKS5 / Tor Bridge)
                val configJsonFile = File(context.filesDir, "v2ray_config.json")
                val configContent = AntiCensorshipConfigBuilder.buildXrayConfigJson(
                    localSocksPort = localPort,
                    localHttpPort = 10809,
                    torSocksPort = torSocksPort,
                    upstreamServer = null
                )

                configJsonFile.writeText(configContent)
                onLog("[V2RayEmbedded] Config JSON written at: ${configJsonFile.absolutePath}")

                if (com.example.service.NativeNetworkBridge.isXrayAvailable) {
                    onLog("[V2RayEmbedded] Starting Xray core via Native JNI (libxray.so in-process)...")
                    val res = com.example.service.NativeNetworkBridge.startXray(configContent)
                    if (res) {
                        onStatusChange("ACTIVE")
                        onLog("[V2RayEmbedded] Native Xray core active and routing traffic!")
                        return@thread
                    }
                }

                onLog("[V2RayEmbedded] Starting V2Ray daemon, bridging SOCKS5 Port $localPort -> Tor $torSocksPort...")
                val builder = ProcessBuilder(
                    xrayFile.absolutePath,
                    "run",
                    "-c", configJsonFile.absolutePath
                )
                builder.redirectErrorStream(true)

                val process = builder.start()
                xrayProcess = process
                onStatusChange("ACTIVE")

                thread(name = "V2RayLogReaderThread") {
                    val reader = process.inputStream.bufferedReader()
                    try {
                        var line = reader.readLine()
                        while (line != null) {
                            val cleanLine = line.trim()
                            if (cleanLine.isNotEmpty()) {
                                Log.d("V2RayEmbedded", cleanLine)
                                onLog("[V2Ray] $cleanLine")
                            }
                            line = reader.readLine()
                        }
                    } catch (e: Exception) {
                        onLog("[V2RayEmbedded] Log reader encountered error: ${e.message}")
                    } finally {
                        try { reader.close() } catch (_: Exception) {}
                        onStatusChange("INACTIVE")
                        onLog("[V2RayEmbedded] V2Ray daemon process ended.")
                    }
                }

            } catch (e: Exception) {
                onLog("[V2RayEmbedded] Exception launching V2Ray daemon: ${e.message}")
                e.printStackTrace()
                onStatusChange("INACTIVE")
            }
        }
    }

    fun stop() {
        if (com.example.service.NativeNetworkBridge.isXrayAvailable) {
            com.example.service.NativeNetworkBridge.stopXray()
        }
        if (xrayProcess != null) {
            onLog("[V2RayEmbedded] Stopping V2Ray daemon...")
            try {
                xrayProcess?.destroy()
            } catch (e: Exception) {
                onLog("[V2RayEmbedded] Error destroying process: ${e.message}")
            }
            xrayProcess = null
        }
        onStatusChange("INACTIVE")
    }

    fun isRunning(): Boolean {
        return xrayProcess != null
    }
}
