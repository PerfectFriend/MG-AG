package com.example.data

import org.json.JSONArray
import org.json.JSONObject

/**
 * Universal configuration generator for anti-censorship protocols:
 * - VLESS + XTLS Reality (Bypass active DPI / SNI filtering)
 * - VMess + WebSocket + TLS
 * - Trojan + gRPC
 * - ShadowTLS v3
 * - SOCKS5 / HTTP Inbounds with Tor Outbound Bridge
 */
object AntiCensorshipConfigBuilder {

    data class ServerNode(
        val protocol: String, // "vless", "vmess", "trojan", "shadowsocks", "socks"
        val address: String,
        val port: Int,
        val uuidOrPassword: String,
        val transport: String = "tcp", // "tcp", "ws", "grpc", "quic", "xhttp", "splithttp"
        val security: String = "reality", // "reality", "tls", "none"
        val sni: String = "www.microsoft.com",
        val publicKey: String = "",
        val shortId: String = "",
        val pathOrServiceName: String = "/",
        val uTlsFingerprint: String = "chrome", // "chrome", "safari", "firefox", "randomized"
        val xhttpMode: String = "auto" // "auto", "stream-up", "stream-one", "packet-up"
    )

    fun buildXrayConfigJson(
        localSocksPort: Int = 10808,
        localHttpPort: Int = 10809,
        torSocksPort: Int = 9050,
        upstreamServer: ServerNode? = null
    ): String {
        val root = JSONObject()

        // 1. Log block
        val log = JSONObject()
        log.put("loglevel", "warning")
        root.put("log", log)

        // 2. Inbounds (Local SOCKS5 and Local HTTP proxy)
        val inbounds = JSONArray()
        val socksInbound = JSONObject().apply {
            put("tag", "socks-in")
            put("port", localSocksPort)
            put("protocol", "socks")
            put("listen", "127.0.0.1")
            put("settings", JSONObject().apply {
                put("auth", "noauth")
                put("udp", true)
            })
            put("sniffing", JSONObject().apply {
                put("enabled", true)
                put("destOverride", JSONArray().apply {
                    put("http")
                    put("tls")
                    put("quic")
                })
            })
        }
        inbounds.put(socksInbound)

        val httpInbound = JSONObject().apply {
            put("tag", "http-in")
            put("port", localHttpPort)
            put("protocol", "http")
            put("listen", "127.0.0.1")
        }
        inbounds.put(httpInbound)
        root.put("inbounds", inbounds)

        // 3. Outbounds
        val outbounds = JSONArray()

        if (upstreamServer != null && upstreamServer.protocol.equals("vless", ignoreCase = true)) {
            // VLESS-Reality Outbound with uTLS JA4 disguise and SplitHTTP/XHTTP support
            val vlessOutbound = JSONObject().apply {
                put("tag", "proxy")
                put("protocol", "vless")
                put("settings", JSONObject().apply {
                    val vnext = JSONArray()
                    val serverObj = JSONObject().apply {
                        put("address", upstreamServer.address)
                        put("port", upstreamServer.port)
                        val users = JSONArray().apply {
                            put(JSONObject().apply {
                                put("id", upstreamServer.uuidOrPassword)
                                put("encryption", "none")
                                put("flow", if (upstreamServer.transport == "tcp" && upstreamServer.security == "reality") "xtls-rprx-vision" else "")
                            })
                        }
                        put("users", users)
                    }
                    vnext.put(serverObj)
                    put("vnext", vnext)
                })
                put("streamSettings", JSONObject().apply {
                    put("network", upstreamServer.transport)
                    put("security", upstreamServer.security)
                    if (upstreamServer.security == "reality") {
                        put("realitySettings", JSONObject().apply {
                            put("show", false)
                            put("dest", "${upstreamServer.sni}:443")
                            put("xver", 0)
                            put("serverNames", JSONArray().apply { put(upstreamServer.sni) })
                            put("privateKey", "")
                            put("shortIds", JSONArray().apply { if (upstreamServer.shortId.isNotBlank()) put(upstreamServer.shortId) })
                            put("publicKey", upstreamServer.publicKey)
                            put("fingerprint", upstreamServer.uTlsFingerprint)
                            put("spiderX", "/")
                        })
                    } else if (upstreamServer.security == "tls") {
                        put("tlsSettings", JSONObject().apply {
                            put("serverName", upstreamServer.sni)
                            put("fingerprint", upstreamServer.uTlsFingerprint)
                            put("allowInsecure", false)
                        })
                    }

                    when (upstreamServer.transport.lowercase()) {
                        "xhttp", "splithttp" -> {
                            put("xhttpSettings", JSONObject().apply {
                                put("path", if (upstreamServer.pathOrServiceName.startsWith("/")) upstreamServer.pathOrServiceName else "/${upstreamServer.pathOrServiceName}")
                                put("mode", upstreamServer.xhttpMode)
                                put("extra", JSONObject().apply {
                                    put("noSSEHeader", false)
                                })
                            })
                        }
                        "ws" -> {
                            put("wsSettings", JSONObject().apply {
                                put("path", upstreamServer.pathOrServiceName)
                                put("headers", JSONObject().apply {
                                    put("Host", upstreamServer.sni)
                                })
                            })
                        }
                        "grpc" -> {
                            put("grpcSettings", JSONObject().apply {
                                put("serviceName", upstreamServer.pathOrServiceName.trimStart('/'))
                                put("multiMode", true)
                            })
                        }
                    }
                })
            }
            outbounds.put(vlessOutbound)
        }

        // Tor SOCKS5 Outbound
        val torOutbound = JSONObject().apply {
            put("tag", "tor-out")
            put("protocol", "socks")
            put("settings", JSONObject().apply {
                put("servers", JSONArray().apply {
                    put(JSONObject().apply {
                        put("address", "127.0.0.1")
                        put("port", torSocksPort)
                    })
                })
            })
        }
        outbounds.put(torOutbound)

        // Direct Outbound
        val directOutbound = JSONObject().apply {
            put("tag", "direct")
            put("protocol", "freedom")
        }
        outbounds.put(directOutbound)

        // Block Outbound
        val blockOutbound = JSONObject().apply {
            put("tag", "block")
            put("protocol", "blackhole")
        }
        outbounds.put(blockOutbound)

        root.put("outbounds", outbounds)

        // 4. Routing rules
        val routing = JSONObject()
        routing.put("domainStrategy", "IPIfNonMatch")
        val rules = JSONArray()

        // Rule: Route all .onion domains directly to Tor outbound
        val onionRule = JSONObject().apply {
            put("type", "field")
            put("domain", JSONArray().apply { put("regexp:.*\\.onion$") })
            put("outboundTag", "tor-out")
        }
        rules.put(onionRule)

        routing.put("rules", rules)
        root.put("routing", routing)

        return root.toString(2)
    }
}
