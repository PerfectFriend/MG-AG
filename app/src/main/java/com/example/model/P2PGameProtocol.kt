package com.example.model

import org.json.JSONObject
import java.security.MessageDigest

/**
 * Peer-to-Peer Protocol for Backgammon Game State Synchronization over Tor Onion and SimpleX.
 * Includes SHA-256 Board Consensus Hashing, Mental Dice Commit-Reveal, Lottery Stage 1/2,
 * Doubling Cube, Undo Consensus, and AES-256-GCM Audio Frame Tunneling.
 */
object P2PGameProtocol {

    sealed class P2PMessage {
        data class Handshake(
            val peerId: String,
            val gameMode: String,
            val onionAddress: String = ""
        ) : P2PMessage()

        data class LotStage1Roll(
            val dieValue: Int
        ) : P2PMessage()

        data class LotStage2Roll(
            val playerColor: String,
            val dieValue: Int
        ) : P2PMessage()

        data class DiceCommitment(
            val hashHex: String
        ) : P2PMessage()

        data class DiceReveal(
            val seedHex: String,
            val value: Int
        ) : P2PMessage()

        data class MoveStep(
            val fromIndex: Int,
            val toIndex: Int,
            val diceUsed: Int,
            val isBearOff: Boolean,
            val moveNumber: Int
        ) : P2PMessage()

        data class TurnFinished(
            val playerColor: String,
            val boardStateHash: String
        ) : P2PMessage()

        data class Resign(
            val playerColor: String
        ) : P2PMessage()

        data class UndoPreference(
            val playerColor: String,
            val allowUndo: Boolean
        ) : P2PMessage()

        data class DoublingOffer(
            val proposerColor: String,
            val multiplier: Int
        ) : P2PMessage()

        data class DoublingResponse(
            val responderColor: String,
            val accept: Boolean
        ) : P2PMessage()

        data class AudioFrameTunnel(
            val base64EncryptedFrame: String
        ) : P2PMessage()
    }

    /**
     * Computes a deterministic SHA-256 consensus hash of the entire 24-point Backgammon board,
     * Bar, and Borne-Off counters to detect desynchronization or tampering in P2P matches.
     */
    fun computeBoardStateHash(
        board: List<CheckerStack>,
        bar: Map<Player, Int>,
        borneOff: Map<Player, Int>
    ): String {
        val sb = StringBuilder()
        board.forEachIndexed { idx, stack ->
            val pCode = when (stack.player) {
                Player.WHITE -> "W"
                Player.BLACK -> "B"
                null -> "N"
            }
            sb.append("$idx:$pCode${stack.count};")
        }
        sb.append("BAR_W:${bar[Player.WHITE] ?: 0};BAR_B:${bar[Player.BLACK] ?: 0};")
        sb.append("OFF_W:${borneOff[Player.WHITE] ?: 0};OFF_B:${borneOff[Player.BLACK] ?: 0}")

        val md = MessageDigest.getInstance("SHA-256")
        val digest = md.digest(sb.toString().toByteArray(Charsets.UTF_8))
        return digest.joinToString("") { "%02x".format(it) }.take(16)
    }

    fun serialize(msg: P2PMessage): String {
        val json = JSONObject()
        when (msg) {
            is P2PMessage.Handshake -> {
                json.put("type", "HANDSHAKE")
                json.put("peerId", msg.peerId)
                json.put("gameMode", msg.gameMode)
                json.put("onionAddress", msg.onionAddress)
            }
            is P2PMessage.LotStage1Roll -> {
                json.put("type", "LOT_STAGE1")
                json.put("dieValue", msg.dieValue)
            }
            is P2PMessage.LotStage2Roll -> {
                json.put("type", "LOT_STAGE2")
                json.put("playerColor", msg.playerColor)
                json.put("dieValue", msg.dieValue)
            }
            is P2PMessage.DiceCommitment -> {
                json.put("type", "DICE_COMMIT")
                json.put("hashHex", msg.hashHex)
            }
            is P2PMessage.DiceReveal -> {
                json.put("type", "DICE_REVEAL")
                json.put("seedHex", msg.seedHex)
                json.put("value", msg.value)
            }
            is P2PMessage.MoveStep -> {
                json.put("type", "MOVE")
                json.put("fromIndex", msg.fromIndex)
                json.put("toIndex", msg.toIndex)
                json.put("diceUsed", msg.diceUsed)
                json.put("isBearOff", msg.isBearOff)
                json.put("moveNumber", msg.moveNumber)
            }
            is P2PMessage.TurnFinished -> {
                json.put("type", "TURN_FINISHED")
                json.put("playerColor", msg.playerColor)
                json.put("boardStateHash", msg.boardStateHash)
            }
            is P2PMessage.Resign -> {
                json.put("type", "RESIGN")
                json.put("playerColor", msg.playerColor)
            }
            is P2PMessage.UndoPreference -> {
                json.put("type", "UNDO_PREF")
                json.put("playerColor", msg.playerColor)
                json.put("allowUndo", msg.allowUndo)
            }
            is P2PMessage.DoublingOffer -> {
                json.put("type", "DOUBLING_OFFER")
                json.put("proposerColor", msg.proposerColor)
                json.put("multiplier", msg.multiplier)
            }
            is P2PMessage.DoublingResponse -> {
                json.put("type", "DOUBLING_RESPONSE")
                json.put("responderColor", msg.responderColor)
                json.put("accept", msg.accept)
            }
            is P2PMessage.AudioFrameTunnel -> {
                json.put("type", "AUDIO_FRAME")
                json.put("base64EncryptedFrame", msg.base64EncryptedFrame)
            }
        }
        return json.toString()
    }

    fun deserialize(raw: String): P2PMessage? {
        return try {
            val json = JSONObject(raw)
            when (json.optString("type")) {
                "HANDSHAKE" -> P2PMessage.Handshake(
                    json.getString("peerId"),
                    json.getString("gameMode"),
                    json.optString("onionAddress", "")
                )
                "LOT_STAGE1" -> P2PMessage.LotStage1Roll(
                    json.getInt("dieValue")
                )
                "LOT_STAGE2" -> P2PMessage.LotStage2Roll(
                    json.getString("playerColor"),
                    json.getInt("dieValue")
                )
                "DICE_COMMIT" -> P2PMessage.DiceCommitment(
                    json.getString("hashHex")
                )
                "DICE_REVEAL" -> P2PMessage.DiceReveal(
                    json.getString("seedHex"),
                    json.getInt("value")
                )
                "MOVE" -> P2PMessage.MoveStep(
                    json.getInt("fromIndex"),
                    json.getInt("toIndex"),
                    json.getInt("diceUsed"),
                    json.optBoolean("isBearOff", false),
                    json.optInt("moveNumber", 0)
                )
                "TURN_FINISHED" -> P2PMessage.TurnFinished(
                    json.getString("playerColor"),
                    json.getString("boardStateHash")
                )
                "RESIGN" -> P2PMessage.Resign(
                    json.getString("playerColor")
                )
                "UNDO_PREF" -> P2PMessage.UndoPreference(
                    json.getString("playerColor"),
                    json.getBoolean("allowUndo")
                )
                "DOUBLING_OFFER" -> P2PMessage.DoublingOffer(
                    json.getString("proposerColor"),
                    json.getInt("multiplier")
                )
                "DOUBLING_RESPONSE" -> P2PMessage.DoublingResponse(
                    json.getString("responderColor"),
                    json.getBoolean("accept")
                )
                "AUDIO_FRAME" -> P2PMessage.AudioFrameTunnel(
                    json.getString("base64EncryptedFrame")
                )
                else -> null
            }
        } catch (_: Exception) {
            null
        }
    }
}
