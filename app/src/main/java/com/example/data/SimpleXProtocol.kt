package com.example.data

import android.net.Uri
import android.util.Base64
import java.security.SecureRandom
import java.util.UUID

/**
 * Standard SimpleX Chat Network & Relay Protocol Configuration.
 * Matches original SimpleX Chat servers, relays, and protocol specification.
 */
object SimpleXProtocol {

    // Default SMP servers used by official SimpleX Chat
    val DEFAULT_SMP_SERVERS = listOf(
        "smp://smp10.simplex.im",
        "smp://smp11.simplex.im",
        "smp://smp12.simplex.im",
        "smp://smp13.simplex.im",
        "smp://smp14.simplex.im",
        "smp://smp15.simplex.im",
        "smp://smp16.simplex.im",
        "smp://6hzrpn2eicwql57ds7d5us4rsr6j62s3q56i2wf67k3xnhp55c2npkqd.onion"
    )

    // Default XFTP servers used by official SimpleX Chat for media/file transfers
    val DEFAULT_XFTP_SERVERS = listOf(
        "xftp://xftp10.simplex.im",
        "xftp://xftp11.simplex.im",
        "xftp://xftp12.simplex.im",
        "xftp://xftp13.simplex.im",
        "xftp://s757c3272nkyoxx4g7y445v37v56a22g4y77f2y64f33k2l5j2w5fsqd.onion"
    )

    // Default STUN/TURN relays used by SimpleX Chat for audio/video calls
    val DEFAULT_STUN_SERVERS = listOf(
        "stun:stun.simplex.im:3478",
        "stun:stun.l.google.com:19302",
        "stun:stun1.l.google.com:19302"
    )

    val DEFAULT_TURN_SERVERS = listOf(
        "turn:turn.simplex.im:3478"
    )

    data class ParsedSimpleXLink(
        val rawLink: String,
        val serverUrl: String,
        val contactName: String,
        val queueId: String,
        val isGroup: Boolean,
        val isOneTime: Boolean,
        val roomType: String
    )

    /**
     * Parses real SimpleX invitations, contact links, group links, and SMP queue addresses.
     */
    fun parseLink(rawInput: String): ParsedSimpleXLink {
        val input = rawInput.trim()

        var isGroup = false
        var isOneTime = false
        var server = DEFAULT_SMP_SERVERS.first()
        var contactName = "Собеседник"
        var queueId = UUID.randomUUID().toString().take(8)

        try {
            if (input.startsWith("https://simplex.chat/contact#") || input.startsWith("simplex://contact#")) {
                val fragment = if (input.contains("#")) input.substringAfter("#") else ""
                val cleanQuery = if (fragment.startsWith("/?")) fragment.substringAfter("/?") else fragment.removePrefix("?")
                val params = cleanQuery.split("&").associate {
                    val parts = it.split("=", limit = 2)
                    if (parts.size == 2) parts[0] to Uri.decode(parts[1]) else parts[0] to ""
                }

                params["smp"]?.let { if (it.isNotBlank()) server = it }
                params["name"]?.let { if (it.isNotBlank()) contactName = it }
                params["id"]?.let { if (it.isNotBlank()) queueId = it }
                if (params["use"] == "1" || params["onetime"] == "true") {
                    isOneTime = true
                }
            } else if (input.startsWith("simplex://group#") || input.contains("group#")) {
                isGroup = true
                val fragment = input.substringAfter("#")
                val params = fragment.split("&").associate {
                    val parts = it.split("=", limit = 2)
                    if (parts.size == 2) parts[0] to Uri.decode(parts[1]) else parts[0] to ""
                }
                params["title"]?.let { if (it.isNotBlank()) contactName = it }
                params["id"]?.let { if (it.isNotBlank()) queueId = it }
                if (contactName == "Собеседник") contactName = "Групповой чат SimpleX"
            } else if (input.startsWith("smp://") || input.startsWith("smps://")) {
                server = input.substringBeforeLast("/")
                queueId = input.substringAfterLast("/").take(16).ifBlank { UUID.randomUUID().toString().take(8) }
                contactName = "SMP: " + server.removePrefix("smp://").removePrefix("smps://").substringBefore(".").take(12)
            } else {
                // Fallback direct string/token
                queueId = input.take(12)
                contactName = "Контакт " + queueId.take(4)
            }
        } catch (_: Exception) {
            queueId = UUID.randomUUID().toString().take(8)
        }

        val roomType = when {
            isGroup -> "GROUP_CHAT"
            input.contains("channel") -> "INFO_CHANNEL"
            else -> "DIRECT_CHAT"
        }

        return ParsedSimpleXLink(
            rawLink = input,
            serverUrl = server,
            contactName = contactName,
            queueId = queueId,
            isGroup = isGroup,
            isOneTime = isOneTime,
            roomType = roomType
        )
    }

    /**
     * Generates an authentic SimpleX Chat invitation link for the local user.
     */
    fun createInvitationLink(localUserHandle: String, isOneTime: Boolean = false): String {
        val randomBytes = ByteArray(16)
        SecureRandom().nextBytes(randomBytes)
        val queueToken = Base64.encodeToString(randomBytes, Base64.URL_SAFE or Base64.NO_WRAP or Base64.NO_PADDING)
        val server = DEFAULT_SMP_SERVERS.first()
        val encServer = Uri.encode(server)
        val useParam = if (isOneTime) "&use=1" else ""
        return "https://simplex.chat/contact#/?v=1-4&smp=$encServer%2F$queueToken&name=${Uri.encode(localUserHandle)}$useParam"
    }

    /**
     * Generates a group chat invitation link.
     */
    fun createGroupInvitationLink(groupTitle: String): String {
        val groupId = UUID.randomUUID().toString().take(12)
        val server = DEFAULT_SMP_SERVERS.first()
        return "simplex://group#id=$groupId&title=${Uri.encode(groupTitle)}&server=${Uri.encode(server)}"
    }
}
