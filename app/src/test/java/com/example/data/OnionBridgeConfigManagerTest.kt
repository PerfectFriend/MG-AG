package com.example.data

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class OnionBridgeConfigManagerTest {

    private lateinit var context: Context
    private lateinit var manager: OnionBridgeConfigManager

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        manager = OnionBridgeConfigManager(context)
    }

    @Test
    fun testDefaultTopologiesAndProtocols() {
        // Assert initial or fallback defaults
        assertNotNull(manager.chainTopology)
        assertNotNull(manager.selectedVpnProtocol)
        assertTrue(manager.relayNodes.isNotEmpty())

        // Update topology
        manager.updateChainTopology(BridgeChainTopology.MULTI_HOP_CHAIN)
        assertEquals(BridgeChainTopology.MULTI_HOP_CHAIN, manager.chainTopology)

        // Update VPN protocol
        manager.updateVpnProtocol(VpnProtocolType.HYSTERIA2)
        assertEquals(VpnProtocolType.HYSTERIA2, manager.selectedVpnProtocol)
    }

    @Test
    fun testStrictOnionOnlyKillswitchEnforcement() {
        manager.updateStrictOnionOnly(true)
        assertTrue(manager.isStrictOnionOnlyEnabled)

        // .onion destinations must be allowed
        assertTrue(manager.isHostAllowed("http://armageddonxyz123456789abcdef.onion:8080"))
        assertTrue(manager.isHostAllowed("smp://chatnode267onionurl.onion:5222"))
        assertTrue(manager.isHostAllowed("xftp://vaultnode345onionurl.onion:8022"))
        assertTrue(manager.isHostAllowed("turn:turnrelayonionurl.onion:3478"))

        // Local loopback proxies must be allowed
        assertTrue(manager.isHostAllowed("127.0.0.1:9050"))
        assertTrue(manager.isHostAllowed("localhost:10808"))

        // Clearnet domains/IPs must be BLOCKED
        assertFalse(manager.isHostAllowed("google.com"))
        assertFalse(manager.isHostAllowed("1.1.1.1:443"))
        assertFalse(manager.isHostAllowed("https://api.telegram.org/bot123/getUpdates"))
        assertFalse(manager.isHostAllowed("smp://clearnet-relay.example.com"))

        // When strict onion mode is disabled, clearnet is permitted
        manager.updateStrictOnionOnly(false)
        assertFalse(manager.isStrictOnionOnlyEnabled)
        assertTrue(manager.isHostAllowed("google.com"))
        assertTrue(manager.isHostAllowed("1.1.1.1:443"))
    }

    @Test
    fun testImportConfigsFromText() {
        val sampleConfigs = """
            vless://uuid-vless-123@vless.example.onion:443?security=reality#OnionVless
            vmess://uuid-vmess-456@vmess.example.onion:443#OnionVmess
            ss://YWVzLTI1Ni1nY206cGFzc3dvcmQ=@ss.example.onion:8388#OnionShadowsocks
            trojan://password@trojan.example.onion:443#OnionTrojan
            hy2://user:pass@hy2.example.onion:443#OnionHy2
            wireguard://wg.example.onion:51820#OnionWireguard
            Bridge obfs4 192.0.2.1:443 cert=xyz iat-mode=0
        """.trimIndent()

        val initialCount = manager.relayNodes.size
        val imported = manager.importConfigsFromText(sampleConfigs)
        assertTrue("Expected imported nodes to be > 0", imported > 0)
        assertEquals(initialCount + imported, manager.relayNodes.size)
    }

    @Test
    fun testCommitVerifiedServersOnly() {
        // Add one online, one offline, one blocked
        manager.addRelayNode(RelayNodeType.SMP, "Verified Node", "smp://verified.onion:5222")
        val verifiedNode = manager.relayNodes.last()
        verifiedNode.pingMs = 120
        verifiedNode.isVerified = true

        manager.addRelayNode(RelayNodeType.XFTP, "Dead Node", "xftp://offline.onion:8022")
        val deadNode = manager.relayNodes.last()
        deadNode.pingMs = -3
        deadNode.isVerified = false

        // Commit verified servers
        manager.commitVerifiedServersOnly()

        // verifiedNode must still be present, deadNode must be pruned
        assertTrue(manager.relayNodes.any { it.name == "Verified Node" && it.isVerified })
        assertFalse(manager.relayNodes.any { it.name == "Dead Node" })
    }
}
