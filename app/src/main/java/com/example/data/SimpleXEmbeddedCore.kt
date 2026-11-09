package com.example.data

import android.content.Context
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

class SimpleXEmbeddedCore(private val context: Context) {

    private var coreProcess: Process? = null

    suspend fun startDaemon(socks5Port: Int = 9050, rpcPort: Int = 5225): Boolean = withContext(Dispatchers.IO) {
        try {
            val nativeCore = File(context.applicationInfo.nativeLibraryDir, "libsimplex.so")
            val binaryPath = if (nativeCore.exists() && nativeCore.canExecute()) {
                nativeCore.absolutePath
            } else {
                Log.w("SimpleXCore", "Native libsimplex.so not found, using internal proxy mode")
                return@withContext false
            }

            val dataDir = File(context.filesDir, "simplex_data").apply { mkdirs() }

            val command = listOf(
                binaryPath,
                "--data-dir", dataDir.absolutePath,
                "--socks-proxy", "127.0.0.1:$socks5Port",
                "--ws-rpc-port", rpcPort.toString()
            )

            val pb = ProcessBuilder(command)
                .directory(dataDir)
                .redirectErrorStream(true)

            coreProcess = pb.start()
            Log.i("SimpleXCore", "SimpleX daemon successfully started on WS RPC port $rpcPort")
            true
        } catch (e: Exception) {
            Log.e("SimpleXCore", "Failed to start SimpleX daemon", e)
            false
        }
    }

    fun stopDaemon() {
        try {
            coreProcess?.destroy()
            coreProcess = null
        } catch (e: Exception) {
            Log.e("SimpleXCore", "Error stopping daemon", e)
        }
    }
}
