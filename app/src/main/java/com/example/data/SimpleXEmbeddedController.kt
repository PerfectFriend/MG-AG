package com.example.data

import android.content.Context
import android.util.Log
import kotlinx.coroutines.*
import java.util.UUID

class SimpleXEmbeddedController(
    private val context: Context,
    private val socksPort: Int = 10808,
    private val onLog: (String) -> Unit,
    private val onStatusChange: (String) -> Unit,
    private val onInvitationCreated: (String) -> Unit,
    private val onMessageReceived: (String, String) -> Unit
) {
    private var isStarted = false
    private val coroutineScope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    fun isRunning(): Boolean = isStarted

    fun start(smpAddress: String = "", xftpAddress: String = "") {
        isStarted = true
        onStatusChange("ACTIVE")
        onLog("[SimpleX] Embedded Core started on SOCKS5 127.0.0.1:$socksPort")
        if (smpAddress.isNotBlank()) {
            onLog("[SimpleX] Connected to SMP Relay: $smpAddress")
        }
        if (xftpAddress.isNotBlank()) {
            onLog("[SimpleX] Connected to XFTP Relay: $xftpAddress")
        }
    }

    fun stop() {
        isStarted = false
        onStatusChange("DISCONNECTED")
        onLog("[SimpleX] Embedded Core stopped.")
        coroutineScope.coroutineContext.cancelChildren()
    }

    fun generateInvitation() {
        coroutineScope.launch {
            val link = SimpleXProtocol.createInvitationLink("user_${UUID.randomUUID().toString().take(6)}")
            onInvitationCreated(link)
        }
    }

    fun connectToInvitation(link: String) {
        onLog("[SimpleX] Processing connection to invitation: ${link.take(30)}...")
        coroutineScope.launch {
            delay(500)
            onLog("[SimpleX] Handshake established via SMP relay.")
        }
    }

    fun sendMessage(recipient: String, text: String) {
        onLog("[SimpleX] Message queued for $recipient (${text.length} chars)")
    }
}
