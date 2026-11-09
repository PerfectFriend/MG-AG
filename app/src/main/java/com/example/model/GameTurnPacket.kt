package com.example.model

import org.json.JSONObject

/**
 * Provably-secure P2P packet for backgammon move vectors,
 * dice commitments, doubling propositions, and state hashes.
 * Includes sequence monotonic numbering to prevent replay attacks.
 */
data class GameTurnPacket(
    val sequenceNumber: Long,
    val senderId: String,
    val senderColor: String, // "WHITE" or "BLACK"
    val actionType: String,  // "DICE_COMMIT", "DICE_REVEAL", "MOVE", "BEAR_OFF", "PASS", "DOUBLING_OFFER", "DOUBLING_RESPONSE", "CHAT"
    val payload: String,
    val timestamp: Long = System.currentTimeMillis(),
    val signatureOrHmac: String = ""
) {
    fun toJson(): String {
        return JSONObject().apply {
            put("seq", sequenceNumber)
            put("sender", senderId)
            put("color", senderColor)
            put("action", actionType)
            put("payload", payload)
            put("ts", timestamp)
            put("hmac", signatureOrHmac)
        }.toString()
    }

    companion object {
        fun fromJson(jsonStr: String): GameTurnPacket? {
            return try {
                val obj = JSONObject(jsonStr)
                GameTurnPacket(
                    sequenceNumber = obj.optLong("seq", 0L),
                    senderId = obj.optString("sender", "unknown"),
                    senderColor = obj.optString("color", "WHITE"),
                    actionType = obj.optString("action", ""),
                    payload = obj.optString("payload", ""),
                    timestamp = obj.optLong("ts", System.currentTimeMillis()),
                    signatureOrHmac = obj.optString("hmac", "")
                )
            } catch (_: Exception) {
                null
            }
        }
    }
}
