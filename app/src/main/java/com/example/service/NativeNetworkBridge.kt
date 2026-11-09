package com.example.service

import android.util.Log

/**
 * Native JNI Network Bridge for High-Performance Tun2Socks IP Routing and Xray-Core.
 *
 * Provides direct JNI bindings to native C/Go binaries compiled into shared objects (.so):
 * - libtun2socks.so: Converts Android TUN file descriptor packets into user-space SOCKS5 streams.
 * - libxray.so: Embedded V2Ray/Xray proxy daemon executing within the Android app process.
 *
 * Implements graceful fallback with dynamic link detection.
 */
object NativeNetworkBridge {

    private const val TAG = "NativeNetworkBridge"

    var isTun2SocksAvailable = false
        private set

    var isXrayAvailable = false
        private set

    init {
        try {
            System.loadLibrary("tun2socks")
            isTun2SocksAvailable = true
            Log.i(TAG, "Native libtun2socks.so loaded successfully!")
        } catch (e: UnsatisfiedLinkError) {
            isTun2SocksAvailable = false
            Log.w(TAG, "Native libtun2socks.so not found in APK jniLibs; using user-space IP router fallback.")
        }

        try {
            System.loadLibrary("xray")
            isXrayAvailable = true
            Log.i(TAG, "Native libxray.so loaded successfully!")
        } catch (e: UnsatisfiedLinkError) {
            isXrayAvailable = false
            Log.w(TAG, "Native libxray.so not found in APK jniLibs; using embedded controller fallback.")
        }
    }

    /**
     * Connects an open TUN interface file descriptor directly to a SOCKS5 proxy port.
     */
    fun startTun2Socks(tunFd: Int, socksHost: String = "127.0.0.1", socksPort: Int = 10808): Boolean {
        if (!isTun2SocksAvailable) {
            Log.w(TAG, "Cannot start native tun2socks: library not linked.")
            return false
        }
        return try {
            nativeStartTun2Socks(tunFd, socksHost, socksPort) == 0
        } catch (e: Throwable) {
            Log.e(TAG, "Error invoking nativeStartTun2Socks", e)
            false
        }
    }

    /**
     * Launches embedded Xray core with the provided JSON configuration.
     */
    fun startXray(configJson: String): Boolean {
        if (!isXrayAvailable) {
            Log.w(TAG, "Cannot start native xray: library not linked.")
            return false
        }
        return try {
            nativeStartXray(configJson) == 0
        } catch (e: Throwable) {
            Log.e(TAG, "Error invoking nativeStartXray", e)
            false
        }
    }

    /**
     * Stops the native tun2socks loop.
     */
    fun stopTun2Socks() {
        if (isTun2SocksAvailable) {
            try {
                nativeStopTun2Socks()
            } catch (e: Throwable) {
                Log.e(TAG, "Error stopping native tun2socks", e)
            }
        }
    }

    /**
     * Stops native Xray core.
     */
    fun stopXray() {
        if (isXrayAvailable) {
            try {
                nativeStopXray()
            } catch (e: Throwable) {
                Log.e(TAG, "Error stopping native xray", e)
            }
        }
    }

    // JNI External Declarations
    private external fun nativeStartTun2Socks(tunFd: Int, socksHost: String, socksPort: Int): Int
    private external fun nativeStopTun2Socks(): Int
    private external fun nativeStartXray(configJson: String): Int
    private external fun nativeStopXray(): Int
}
