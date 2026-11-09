package com.example.data

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow

/**
 * Transport delivery acknowledgment status.
 */
enum class TransportAckStatus {
    DELIVERED,
    PENDING_RELAY,
    QUEUED_OFFLINE,
    FAILED
}

data class TransportAck(
    val messageId: String,
    val status: TransportAckStatus,
    val timestamp: Long = System.currentTimeMillis(),
    val errorDescription: String? = null
)

data class TransportPacket(
    val id: String,
    val senderId: String,
    val recipientId: String,
    val payload: ByteArray,
    val timestamp: Long = System.currentTimeMillis()
)

data class TransportConfig(
    val endpointUrl: String,
    val socksProxyHost: String? = null,
    val socksProxyPort: Int? = null,
    val timeoutMs: Long = 10000L
)

/**
 * Abstract interface isolating the SimpleX messaging layer from raw network sockets.
 * Allows transparent switching between Tor Hidden Services, direct LAN/Wi-Fi mesh,
 * and test mock engines.
 */
interface ChatTransport {
    val transportType: String
    val isConnected: Boolean

    suspend fun connect(config: TransportConfig): Boolean
    suspend fun disconnect()
    suspend fun sendMessage(packet: TransportPacket): TransportAck
    fun incomingPackets(): Flow<TransportPacket>
}

/**
 * Local simulation transport for UI/Practice mode testing without network dependencies.
 */
class SimulatedChatTransport : ChatTransport {
    override val transportType: String = "SIMULATION"
    private var _isConnected: Boolean = false
    override val isConnected: Boolean get() = _isConnected

    private val packetFlow = MutableSharedFlow<TransportPacket>(extraBufferCapacity = 64)

    override suspend fun connect(config: TransportConfig): Boolean {
        _isConnected = true
        return true
    }

    override suspend fun disconnect() {
        _isConnected = false
    }

    override suspend fun sendMessage(packet: TransportPacket): TransportAck {
        if (!_isConnected) {
            return TransportAck(packet.id, TransportAckStatus.FAILED, errorDescription = "Transport disconnected")
        }
        return TransportAck(packet.id, TransportAckStatus.DELIVERED)
    }

    override fun incomingPackets(): Flow<TransportPacket> = packetFlow.asSharedFlow()

    suspend fun emitTestPacket(packet: TransportPacket) {
        packetFlow.emit(packet)
    }
}
