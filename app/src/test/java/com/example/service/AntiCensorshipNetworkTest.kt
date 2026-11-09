package com.example.service

import com.example.data.AntiCensorshipConfigBuilder
import com.example.data.MultiPathTransportRacer
import com.example.data.TrafficChaffingEngine
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.json.JSONObject
import kotlinx.coroutines.runBlocking

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class AntiCensorshipNetworkTest {

    @Test
    fun testUniversalConfigBuilderGeneratesValidJsonWithRouting() {
        val jsonString = AntiCensorshipConfigBuilder.buildXrayConfigJson(
            localSocksPort = 10808,
            localHttpPort = 10809,
            torSocksPort = 9050,
            upstreamServer = AntiCensorshipConfigBuilder.ServerNode(
                protocol = "vless",
                address = "1.1.1.1",
                port = 443,
                uuidOrPassword = "[REDACTED]",
                transport = "tcp",
                security = "reality",
                sni = "www.cloudflare.com",
                publicKey = "m_oB9Ea6d2n1n8-G...",
                shortId = "1234abcd",
                uTlsFingerprint = "chrome"
            )
        )

        assertNotNull(jsonString)
        val json = JSONObject(jsonString)

        // Verify inbounds
        val inbounds = json.getJSONArray("inbounds")
        assertTrue(inbounds.length() >= 2)
        assertEquals(10808, inbounds.getJSONObject(0).getInt("port"))
        assertEquals("socks", inbounds.getJSONObject(0).getString("protocol"))

        // Verify outbounds
        val outbounds = json.getJSONArray("outbounds")
        assertTrue(outbounds.length() >= 3)
        assertEquals("proxy", outbounds.getJSONObject(0).getString("tag"))
        assertEquals("vless", outbounds.getJSONObject(0).getString("protocol"))

        // Verify streamSettings for VLESS Reality & uTLS fingerprint
        val streamSettings = outbounds.getJSONObject(0).getJSONObject("streamSettings")
        assertEquals("reality", streamSettings.getString("security"))
        val reality = streamSettings.getJSONObject("realitySettings")
        assertEquals("www.cloudflare.com", reality.getJSONArray("serverNames").getString(0))
        assertEquals("chrome", reality.getString("fingerprint"))

        // Verify Tor outbound
        val torOutbound = outbounds.getJSONObject(1)
        assertEquals("tor-out", torOutbound.getString("tag"))

        // Verify routing rules redirecting .onion to Tor
        val routing = json.getJSONObject("routing")
        val rules = routing.getJSONArray("rules")
        assertTrue(rules.length() >= 1)
        val onionRule = rules.getJSONObject(0)
        assertEquals("tor-out", onionRule.getString("outboundTag"))
    }

    @Test
    fun testSplitHttpXhttpConfigGeneration() {
        val jsonString = AntiCensorshipConfigBuilder.buildXrayConfigJson(
            upstreamServer = AntiCensorshipConfigBuilder.ServerNode(
                protocol = "vless",
                address = "cdn.example.com",
                port = 443,
                uuidOrPassword = "sample-uuid-token",
                transport = "xhttp",
                security = "reality",
                sni = "cdn.example.com",
                pathOrServiceName = "split-tunnel-api",
                xhttpMode = "stream-up"
            )
        )

        val json = JSONObject(jsonString)
        val outbounds = json.getJSONArray("outbounds")
        val streamSettings = outbounds.getJSONObject(0).getJSONObject("streamSettings")
        assertEquals("xhttp", streamSettings.getString("network"))

        val xhttp = streamSettings.getJSONObject("xhttpSettings")
        assertEquals("/split-tunnel-api", xhttp.getString("path"))
        assertEquals("stream-up", xhttp.getString("mode"))
    }

    @Test
    fun testTrafficChaffingEngineIdentifiesDummyPackets() = runBlocking {
        var dummySentCount = 0
        val engine = TrafficChaffingEngine(this) { packet ->
            dummySentCount++
        }

        val testRealMessage = "Hello World".toByteArray()
        assertFalse(engine.isChaffPacket(testRealMessage))

        val chaffFrame = ByteArray(512)
        System.arraycopy(TrafficChaffingEngine.MAGIC_CHAFF_HEADER, 0, chaffFrame, 0, TrafficChaffingEngine.MAGIC_CHAFF_HEADER.size)
        assertTrue(engine.isChaffPacket(chaffFrame))
    }
}
