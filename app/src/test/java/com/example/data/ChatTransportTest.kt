package com.example.data

import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ChatTransportTest {

    @Test
    fun testSimulatedTransportConnectAndSend() = runBlocking {
        val transport = SimulatedChatTransport()
        assertFalse(transport.isConnected)

        val config = TransportConfig(endpointUrl = "smp://test.onion")
        val connected = transport.connect(config)
        assertTrue(connected)
        assertTrue(transport.isConnected)

        val packet = TransportPacket(
            id = "msg_1",
            senderId = "alice",
            recipientId = "bob",
            payload = "Hello P2P".toByteArray(Charsets.UTF_8)
        )

        val ack = transport.sendMessage(packet)
        assertEquals(TransportAckStatus.DELIVERED, ack.status)
        assertEquals("msg_1", ack.messageId)

        transport.disconnect()
        assertFalse(transport.isConnected)
    }

    @Test
    fun testSimulatedTransportIncomingPackets() = runBlocking {
        val transport = SimulatedChatTransport()
        transport.connect(TransportConfig(endpointUrl = "smp://test.onion"))

        val incoming = TransportPacket(
            id = "msg_2",
            senderId = "bob",
            recipientId = "alice",
            payload = "Ack from Bob".toByteArray(Charsets.UTF_8)
        )

        transport.emitTestPacket(incoming)
        val received = transport.incomingPackets().first()

        assertEquals("msg_2", received.id)
        assertEquals("bob", received.senderId)
        assertEquals("Ack from Bob", String(received.payload, Charsets.UTF_8))
    }
}
