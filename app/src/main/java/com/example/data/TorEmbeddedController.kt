package com.example.data

import android.content.Context
import android.util.Log
import org.torproject.android.binary.TorResourceInstaller
import java.io.File
import kotlin.concurrent.thread

class TorEmbeddedController(
    private val context: Context,
    private val socksPort: Int = 9050,
    private val onLog: (String) -> Unit,
    private val onStatusChange: (String) -> Unit // "INACTIVE", "INITIALIZING", "ACTIVE"
) {
    private var torProcess: Process? = null

    fun getOnionHostname(): String? {
        return try {
            val hostnameFile = File(context.filesDir, "tor_hs/hostname")
            if (hostnameFile.exists()) {
                hostnameFile.readText().trim()
            } else {
                null
            }
        } catch (e: Exception) {
            Log.e("TorEmbedded", "Error reading onion hostname: ${e.message}")
            null
        }
    }

    fun start() {
        if (torProcess != null) {
            onLog("[TorEmbedded] Daemon is already running.")
            return
        }

        thread(name = "TorEmbeddedStartThread") {
            try {
                onStatusChange("INITIALIZING")
                onLog("[TorEmbedded] Preparing Tor binary installation...")

                val nativeTor = File(context.applicationInfo.nativeLibraryDir, "libtor.so")
                val binDir = context.getDir("tor_bin", Context.MODE_PRIVATE)
                val installer = TorResourceInstaller(context, binDir)
                val torrcFile = try { installer.getTorrcFile() } catch (_: Exception) { File(context.filesDir, "torrc") }

                val torFile: File = if (nativeTor.exists() && nativeTor.canExecute()) {
                    onLog("[TorEmbedded] Found native libtor.so in nativeLibraryDir: ${nativeTor.absolutePath}")
                    nativeTor
                } else {
                    onLog("[TorEmbedded] Native libtor.so not executable yet, attempting resource install...")
                    try {
                        installer.installResources()
                    } catch (e: Exception) {
                        onLog("[TorEmbedded] Warning unpacking resources: ${e.message}")
                    }
                    val unpackedTor = installer.getTorFile()
                    unpackedTor.setExecutable(true, true)
                    unpackedTor
                }
                
                onLog("[TorEmbedded] Tor binary matches: ${torFile.absolutePath}")
                
                if (!torFile.exists()) {
                    onLog("[TorEmbedded] ERROR: Tor executable was not found!")
                    onStatusChange("INACTIVE")
                    return@thread
                }

                // Grant execution privileges explicitly
                torFile.setExecutable(true, true)

                val dataDir = context.getDir("tor_data", Context.MODE_PRIVATE)
                onLog("[TorEmbedded] Directory: ${dataDir.absolutePath}")

                // Configure Onion Service Directory
                val hsDir = File(context.filesDir, "tor_hs")
                hsDir.mkdirs()
                hsDir.setReadable(true, true)
                hsDir.setWritable(true, true)
                hsDir.setExecutable(true, true)

                onLog("[TorEmbedded] Starting process on port $socksPort with Onion Service enabled...")
                
                val cmd = mutableListOf(
                    torFile.absolutePath,
                    "--SocksPort", "127.0.0.1:$socksPort",
                    "--ControlPort", "127.0.0.1:9051",
                    "--CookieAuthentication", "1",
                    "--DataDirectory", dataDir.absolutePath,
                    "--AvoidDiskWrites", "1",
                    "--HiddenServiceDir", hsDir.absolutePath,
                    "--HiddenServicePort", "8080 127.0.0.1:8080"
                )
                if (torrcFile.exists()) {
                    cmd.add(1, "-f")
                    cmd.add(2, torrcFile.absolutePath)
                }

                val builder = ProcessBuilder(cmd)
                builder.redirectErrorStream(true)

                val process = builder.start()
                torProcess = process

                thread(name = "TorLogReaderThread") {
                    val reader = process.inputStream.bufferedReader()
                    try {
                        var line = reader.readLine()
                        while (line != null) {
                            val cleanLine = line.trim()
                            if (cleanLine.isNotEmpty()) {
                                Log.d("TorEmbedded", cleanLine)
                                onLog("[Tor] $cleanLine")
                                
                                if (cleanLine.contains("Bootstrapped 100%")) {
                                    onStatusChange("ACTIVE")
                                }
                            }
                            line = reader.readLine()
                        }
                    } catch (e: Exception) {
                        onLog("[TorEmbedded] Log reader encountered error: ${e.message}")
                    } finally {
                        try { reader.close() } catch (_: Exception) {}
                        onStatusChange("INACTIVE")
                        onLog("[TorEmbedded] Tor process finished.")
                    }
                }

            } catch (e: Exception) {
                onLog("[TorEmbedded] Exception launching Tor daemon: ${e.message}")
                e.printStackTrace()
                onStatusChange("INACTIVE")
            }
        }
    }

    fun stop() {
        if (torProcess == null) return
        onLog("[TorEmbedded] Stopping Tor daemon process...")
        try {
            torProcess?.destroy()
        } catch (e: Exception) {
            onLog("[TorEmbedded] Error destroying process: ${e.message}")
        }
        torProcess = null
        onStatusChange("INACTIVE")
    }

    fun isRunning(): Boolean {
        return torProcess != null
    }
}
