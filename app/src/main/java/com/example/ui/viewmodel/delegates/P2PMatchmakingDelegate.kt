package com.example.ui.viewmodel.delegates

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.example.crypto.MentalDiceEngine
import com.example.model.Player
import kotlinx.coroutines.CoroutineScope

/**
 * Делегат P2P онлайн-матчмейкинга, сессий через Tor/LAN и протокола костей Mental Dice.
 */
class P2PMatchmakingDelegate(
    private val scope: CoroutineScope
) {
    var isOnlinePlayActive by mutableStateOf(false)
    var isMatchmaking by mutableStateOf(false)

    var isRealTorP2PMode by mutableStateOf(false)
    var isTorP2PHost by mutableStateOf(false)
    var isTorP2PConnected by mutableStateOf(false)

    var onlineOpponentName by mutableStateOf("Tor Peer")
    var onlineOpponentRating by mutableIntStateOf(2600)
    var localPlayerColor by mutableStateOf(Player.WHITE)

    val matchmakingLogs = mutableStateListOf<String>()

    var p2pTelemetryRttMs by mutableLongStateOf(0L)

    // Mental Dice Integration State
    val mentalDiceEngine = MentalDiceEngine()
    var isMentalDiceExchanging by mutableStateOf(false)
    var mentalDiceHandshakeStatus by mutableStateOf("IDLE")
    var lastDiceOutcome by mutableStateOf<MentalDiceEngine.DiceOutcome?>(null)
    var mentalDiceAuditLog by mutableStateOf("")

    fun log(text: String) {
        matchmakingLogs.add(text)
    }

    fun resetMatch() {
        isOnlinePlayActive = false
        isMatchmaking = false
        matchmakingLogs.clear()
        mentalDiceEngine.reset()
        mentalDiceHandshakeStatus = "IDLE"
        lastDiceOutcome = null
    }
}
