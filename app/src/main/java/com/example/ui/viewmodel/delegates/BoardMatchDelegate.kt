package com.example.ui.viewmodel.delegates

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.example.model.CheckerStack
import com.example.model.GameEndResult
import com.example.model.GameEngine
import com.example.model.GameLogEntry
import com.example.model.GameMode
import com.example.model.GameStatus
import com.example.model.Move
import com.example.model.Player
import kotlinx.coroutines.CoroutineScope

/**
 * Делегат игрового процесса длинных нард и взаимодействия с чистым JVM движком [GameEngine].
 *
 * Отвечает за:
 * - Управление состоянием доски, активным игроком и оставшимися кубиками.
 * - Проверку легальности перемещения фишек (`isMoveLegal`, `getAllLegalMoves`).
 * - Преобразование относительных и абсолютных индексов пунктов на доске.
 * - Логику куба удвоения ставки (Даве: предложение, принятие, отказ).
 * - Расчёт исхода партии (Оин, Марс, Кокс).
 *
 * @param engine Экземпляр чистого детерминированного ядра [GameEngine].
 * @param scope CoroutineScope ViewModel для выполнения игровых корутин.
 */
class BoardMatchDelegate(
    val engine: GameEngine,
    private val scope: CoroutineScope
) {
    var boardState by mutableStateOf<List<CheckerStack>>(emptyList())
        private set

    var activePlayer by mutableStateOf(Player.WHITE)
        private set

    var gameStatus by mutableStateOf(GameStatus.BEFORE_ROLL)
        private set

    var winner by mutableStateOf<Player?>(null)
        private set

    var remainingDice by mutableStateOf<List<Int>>(emptyList())
        private set

    var stakeMultiplier by mutableIntStateOf(1)
        private set

    var doublingOwner by mutableStateOf<Player?>(null)
        private set

    var isDoublingOffered by mutableStateOf(false)
        private set

    var doublingProposer by mutableStateOf<Player?>(null)
        private set

    var gameEndResult by mutableStateOf<GameEndResult?>(null)
        private set

    var turnLogs by mutableStateOf<List<GameLogEntry>>(emptyList())
        private set

    init {
        syncStatesWithEngine()
    }

    /**
     * Синхронизирует Compose State с внутренним состоянием движка GameEngine.
     */
    fun syncStatesWithEngine() {
        boardState = engine.board.toList()
        activePlayer = engine.activePlayer
        gameStatus = engine.gameStatus
        winner = engine.winner
        remainingDice = engine.remainingDice.toList()
        stakeMultiplier = engine.stakeMultiplier
        doublingOwner = engine.doublingOwner
        isDoublingOffered = engine.isDoublingOffered
        doublingProposer = engine.doublingProposer
        turnLogs = engine.logs.toList()
        gameEndResult = engine.gameEndResult ?: if (engine.winner != null) engine.calculateGameOutcome(engine.winner!!) else null
    }

    fun absoluteToRelative(player: Player, absoluteIndex: Int): Int =
        engine.absoluteToRelative(player, absoluteIndex)

    fun relativeToAbsolute(player: Player, relativeIndex: Int): Int =
        engine.relativeToAbsolute(player, relativeIndex)

    fun isMoveLegal(player: Player, fromRel: Int, dice: Int): Boolean =
        engine.isMoveLegal(player, fromRel, dice)

    fun getAllLegalMoves(player: Player, diceValues: List<Int>): List<Move> =
        engine.getAllLegalMoves(player, diceValues)

    fun calculateGameOutcome(winner: Player): GameEndResult =
        engine.calculateGameOutcome(winner)

    /**
     * Начинает новую партию с выбранным режимом игры.
     */
    fun startNewGame(mode: GameMode) {
        engine.resetGame(mode)
        syncStatesWithEngine()
    }

    /**
     * Предлагает удвоение ставки (Даве).
     */
    fun proposeDoubling(proposer: Player = activePlayer): Boolean {
        val success = engine.proposeDoubling(proposer)
        if (success) {
            syncStatesWithEngine()
        }
        return success
    }

    /**
     * Принимает удвоение ставки.
     */
    fun acceptDoubling(): Boolean {
        val success = engine.acceptDoubling()
        if (success) {
            syncStatesWithEngine()
        }
        return success
    }

    /**
     * Отклоняет удвоение ставки (капитуляция текущей партии).
     */
    fun declineDoubling(): Boolean {
        val success = engine.declineDoubling()
        if (success) {
            syncStatesWithEngine()
        }
        return success
    }
}
