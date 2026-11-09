package com.example.data

import android.content.Context
import android.os.Build
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.torproject.android.binary.TorResourceInstaller
import java.io.BufferedReader
import java.io.File
import java.io.InputStreamReader
import java.util.concurrent.TimeUnit
import kotlin.concurrent.thread

/**
 * State of the Xray-Tor sequential Bridge execution.
 */
enum class BridgeState {
    STOPPED,
    PREPARING_BINARIES,
    STARTING_TOR,
    TOR_RUNNING,
    STARTING_XRAY,
    BRIDGE_ACTIVE,
    STOPPING,
    ERROR
}

/**
 * BridgeManager manages the sequential execution, process lifecycle, state tracking,
 * and automatic cleanup of Xray (V2Ray) and Tor subprocesses.
 */
class BridgeManager(
    private val context: Context,
    private val xrayLocalPort: Int = 10808,
    private val torSocksPort: Int = 9050
) : AutoCloseable {

    companion object {
        private const val TAG = "BridgeManager"
    }

    private val scope = CoroutineScope(Dispatchers.IO + Job())

    private val _state = MutableStateFlow(BridgeState.STOPPED)
    val state: StateFlow<BridgeState> = _state.asStateFlow()

    private val _logs = MutableStateFlow<List<String>>(emptyList())
    val logs: StateFlow<List<String>> = _logs.asStateFlow()

    @Volatile
    private var xrayProcess: Process? = null

    @Volatile
    private var torProcess: Process? = null

    private var torLogThread: Thread? = null
    private var xrayLogThread: Thread? = null

    /**
     * Appends a log line to the log StateFlow and system logcat.
     */
    private fun log(message: String) {
        Log.d(TAG, message)
        _logs.value = _logs.value + message
    }

    /**
     * Starts the sequential bridge process:
     * Step 1: Prepare Xray & Tor binaries & configs
     * Step 2: Launch Tor subprocess and wait for initialization
     * Step 3: Launch Xray (V2Ray) subprocess chained to Tor SOCKS5
     */
    fun startBridge(onComplete: ((Boolean) -> Unit)? = null) {
        if (_state.value != BridgeState.STOPPED && _state.value != BridgeState.ERROR) {
            log("[BridgeManager] Bridge is already running or initializing (state: ${_state.value})")
            onComplete?.invoke(true)
            return
        }

        scope.launch {
            try {
                updateState(BridgeState.PREPARING_BINARIES)
                log("[BridgeManager] Starting sequential execution setup for Xray + Tor bridge...")

                // 1. Prepare Xray binary and configuration
                val xrayFile = prepareXrayBinary()
                val xrayConfigFile = createXrayConfigFile()

                // 2. Prepare Tor binary and configuration
                val (torFile, torrcFile, torDataDir, torHsDir) = prepareTorFiles()

                // 3. Launch Tor process first
                updateState(BridgeState.STARTING_TOR)
                log("[BridgeManager] Step 1/2: Launching Tor subprocess on SOCKS5 port $torSocksPort...")
                val torStarted = startTorProcess(torFile, torrcFile, torDataDir, torHsDir)

                if (!torStarted) {
                    log("[BridgeManager] ERROR: Tor subprocess failed to start.")
                    updateState(BridgeState.ERROR)
                    cleanupProcesses()
                    onComplete?.invoke(false)
                    return@launch
                }

                updateState(BridgeState.TOR_RUNNING)
                log("[BridgeManager] Tor subprocess is running.")

                // 4. Launch Xray process chained to Tor
                updateState(BridgeState.STARTING_XRAY)
                log("[BridgeManager] Step 2/2: Launching Xray (V2Ray) subprocess on SOCKS5 port $xrayLocalPort -> Tor port $torSocksPort...")
                val xrayStarted = startXrayProcess(xrayFile, xrayConfigFile)

                if (!xrayStarted) {
                    log("[BridgeManager] ERROR: Xray subprocess failed to start.")
                    updateState(BridgeState.ERROR)
                    cleanupProcesses()
                    onComplete?.invoke(false)
                    return@launch
                }

                updateState(BridgeState.BRIDGE_ACTIVE)
                log("[BridgeManager] ✅ Sequential bridge active! [Local SOCKS5 :$xrayLocalPort] -> [Xray] -> [Tor SOCKS5 :$torSocksPort] -> [Onion Network]")
                onComplete?.invoke(true)

            } catch (e: Exception) {
                log("[BridgeManager] Exception during bridge startup: ${e.message}")
                Log.e(TAG, "Error starting bridge", e)
                updateState(BridgeState.ERROR)
                cleanupProcesses()
                onComplete?.invoke(false)
            }
        }
    }

    /**
     * Gracefully stops both Xray and Tor subprocesses and releases resources.
     */
    fun stopBridge(onComplete: (() -> Unit)? = null) {
        scope.launch {
            log("[BridgeManager] Stopping Xray and Tor bridge subprocesses...")
            updateState(BridgeState.STOPPING)
            cleanupProcesses()
            updateState(BridgeState.STOPPED)
            log("[BridgeManager] All bridge processes stopped.")
            onComplete?.invoke()
        }
    }

    /**
     * Checks if both subprocesses are active and running.
     */
    fun isBridgeActive(): Boolean {
        val xrayAlive = xrayProcess?.isAlive == true
        val torAlive = torProcess?.isAlive == true
        return xrayAlive && torAlive && _state.value == BridgeState.BRIDGE_ACTIVE
    }

    private fun updateState(newState: BridgeState) {
        _state.value = newState
        log("[BridgeState] -> $newState")
    }

    private suspend fun prepareXrayBinary(): File = withContext(Dispatchers.IO) {
        val arch = if (Build.SUPPORTED_ABIS.contains("arm64-v8a")) "arm64" else "x86_64"
        val binDir = context.getDir("bin", Context.MODE_PRIVATE)
        val xrayFile = File(binDir, "xray")

        if (!xrayFile.exists()) {
            try {
                context.assets.open("bin/xray-$arch").use { input ->
                    xrayFile.outputStream().use { output ->
                        input.copyTo(output)
                    }
                }
                log("[BridgeManager] Unpacked Xray binary for $arch.")
            } catch (e: Exception) {
                log("[BridgeManager] Asset unpack notice: ${e.message}")
            }
        }

        if (!xrayFile.exists()) {
            // Never substitute a sleeping shell script for the real daemon: the process
            // stays alive, the port never opens, and the UI reports the tunnel as active.
            throw IllegalStateException(
                "Xray binary for $arch is not bundled and could not be unpacked; the bridge cannot start"
            )
        }

        xrayFile.setExecutable(true, true)
        return@withContext xrayFile
    }

    private suspend fun createXrayConfigFile(): File = withContext(Dispatchers.IO) {
        val configFile = File(context.filesDir, "xray_bridge_config.json")
        val configJson = """
            {
              "log": { "loglevel": "warning" },
              "inbounds": [
                {
                  "port": $xrayLocalPort,
                  "protocol": "socks",
                  "settings": { "auth": "noauth", "udp": true, "ip": "127.0.0.1" }
                }
              ],
              "outbounds": [
                {
                  "protocol": "socks",
                  "settings": {
                    "servers": [
                      { "address": "127.0.0.1", "port": $torSocksPort }
                    ]
                  }
                }
              ]
            }
        """.trimIndent()

        configFile.writeText(configJson)
        log("[BridgeManager] Xray config file created: ${configFile.absolutePath}")
        return@withContext configFile
    }

    private data class TorFiles(
        val torFile: File,
        val torrcFile: File,
        val dataDir: File,
        val hsDir: File
    )

    private suspend fun prepareTorFiles(): TorFiles = withContext(Dispatchers.IO) {
        val binDir = context.getDir("tor_bin", Context.MODE_PRIVATE)
        val dataDir = context.getDir("tor_data", Context.MODE_PRIVATE)
        val hsDir = File(context.filesDir, "tor_hs").apply {
            mkdirs()
            setReadable(true, true)
            setWritable(true, true)
            setExecutable(true, true)
        }

        var torFile = File(binDir, "tor")
        var torrcFile = File(binDir, "torrc")

        try {
            val installer = TorResourceInstaller(context, binDir)
            installer.installResources()
            torFile = installer.getTorFile()
            torrcFile = installer.getTorrcFile()
            log("[BridgeManager] Unpacked Tor binary: ${torFile.absolutePath}")
        } catch (e: Exception) {
            log("[BridgeManager] Tor installer notice: ${e.message}")
        }

        if (!torFile.exists()) {
            throw IllegalStateException(
                "Tor binary is not bundled and could not be unpacked; the bridge cannot start"
            )
        }
        if (!torrcFile.exists()) {
            torrcFile.writeText("SocksPort 127.0.0.1:$torSocksPort\nDataDirectory ${dataDir.absolutePath}\n")
        }

        torFile.setExecutable(true, true)

        return@withContext TorFiles(torFile, torrcFile, dataDir, hsDir)
    }

    private fun startTorProcess(
        torFile: File,
        torrcFile: File,
        dataDir: File,
        hsDir: File
    ): Boolean {
        return try {
            val builder = ProcessBuilder(
                torFile.absolutePath,
                "-f", torrcFile.absolutePath,
                "--SocksPort", "127.0.0.1:$torSocksPort",
                "--DataDirectory", dataDir.absolutePath,
                "--AvoidDiskWrites", "1",
                "--HiddenServiceDir", hsDir.absolutePath,
                "--HiddenServicePort", "8080 127.0.0.1:8080"
            )
            builder.redirectErrorStream(true)

            val proc = builder.start()
            torProcess = proc

            torLogThread = thread(name = "BridgeTorLogThread") {
                try {
                    val reader = BufferedReader(InputStreamReader(proc.inputStream))
                    var line: String? = reader.readLine()
                    while (line != null) {
                        log("[TorSubprocess] $line")
                        line = reader.readLine()
                    }
                } catch (_: Exception) {}
            }

            // Short pause to ensure process stays alive
            Thread.sleep(300)
            proc.isAlive
        } catch (e: Exception) {
            log("[BridgeManager] Failed to launch Tor process: ${e.message}")
            false
        }
    }

    private fun startXrayProcess(xrayFile: File, configFile: File): Boolean {
        return try {
            val builder = ProcessBuilder(
                xrayFile.absolutePath,
                "run",
                "-c", configFile.absolutePath
            )
            builder.redirectErrorStream(true)

            val proc = builder.start()
            xrayProcess = proc

            xrayLogThread = thread(name = "BridgeXrayLogThread") {
                try {
                    val reader = BufferedReader(InputStreamReader(proc.inputStream))
                    var line: String? = reader.readLine()
                    while (line != null) {
                        log("[XraySubprocess] $line")
                        line = reader.readLine()
                    }
                } catch (_: Exception) {}
            }

            Thread.sleep(300)
            proc.isAlive
        } catch (e: Exception) {
            log("[BridgeManager] Failed to launch Xray process: ${e.message}")
            false
        }
    }

    /**
     * Cleans up all running processes and log threads safely.
     */
    private fun cleanupProcesses() {
        // Destroy Xray process
        xrayProcess?.let { proc ->
            try {
                log("[BridgeManager] Terminating Xray subprocess...")
                proc.destroy()
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    proc.destroyForcibly()
                }
            } catch (e: Exception) {
                log("[BridgeManager] Error destroying Xray process: ${e.message}")
            }
        }
        xrayProcess = null

        // Destroy Tor process
        torProcess?.let { proc ->
            try {
                log("[BridgeManager] Terminating Tor subprocess...")
                proc.destroy()
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    proc.destroyForcibly()
                }
            } catch (e: Exception) {
                log("[BridgeManager] Error destroying Tor process: ${e.message}")
            }
        }
        torProcess = null

        // Interrupt reader threads
        try {
            torLogThread?.interrupt()
            xrayLogThread?.interrupt()
        } catch (_: Exception) {}

        torLogThread = null
        xrayLogThread = null
    }

    override fun close() {
        stopBridge()
    }
}
