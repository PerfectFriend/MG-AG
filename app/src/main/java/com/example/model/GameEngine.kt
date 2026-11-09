package com.example.model

enum class GameMode {
    MAD,   // Бешеные нарды (каскад кушей от D до 6, передача несыгранных зариков сопернику, запрет ходов внутри дома)
    LONG,  // Классические длинные нарды (куш = 4 хода тем же числом, голова 1 за ход, без передачи ходов, свободные ходы в доме)
    SHORT  // Короткие нарды (классические правила ходов)
}

enum class Player {
    WHITE, BLACK;

    fun other(): Player = if (this == WHITE) BLACK else WHITE
    fun label(lang: String = "RU"): String {
        return if (lang == "RU") {
            if (this == WHITE) "Белые" else "Черные"
        } else {
            if (this == WHITE) "White" else "Black"
        }
    }
}

enum class GameStatus {
    LOT_STAGE1,
    LOT_STAGE2,
    BEFORE_ROLL,
    PLAYER_MOVE, // Active player's turn to move checkers
    GAME_OVER
}

data class CheckerStack(
    val count: Int,
    val player: Player?
)

data class Move(
    val from: Int, // relative index 0..23, or -1 for bar in Short backgammon
    val to: Int,   // relative index 1..24, where 24 represents exiting the board (borne off)
    val diceUsed: Int
)

data class ReachablePath(
    val finalTo: Int,
    val steps: List<Move>
)

data class GameLogEntry(
    val timestampString: String,
    val message: String
)

enum class GameOutcomeType(
    val basePoints: Int,
    val titleRu: String,
    val titleEn: String,
    val descriptionRu: String,
    val descriptionEn: String
) {
    SIMPLE(
        1,
        "Простая игра (Одинцовка)",
        "Single Game",
        "Соперник успел выбросить хотя бы одну фишку из дома",
        "Opponent borne off at least one checker"
    ),
    MARS(
        2,
        "Марс",
        "Mars",
        "Соперник вывел все фишки из начального дома, но не завёл все в финальный дом",
        "Opponent cleared initial home but did not bring all checkers to final home"
    ),
    COKE(
        3,
        "Кокс",
        "Coke",
        "У соперника осталась хотя бы одна фишка в начальном доме (или на баре)",
        "Opponent has at least one checker in initial home or on bar"
    ),
    HOME_MARS(
        4,
        "Домашний Марс",
        "Home Mars",
        "Соперник собрал все 15 фишек в финальном доме, но не успел выкинуть ни одной!",
        "Opponent gathered all 15 checkers in final home but bore none off!"
    ),
    RESIGNATION(
        1,
        "Сдача партии",
        "Resignation",
        "Соперник признал поражение или отклонил предложение об удвоении ставки",
        "Opponent surrendered or declined doubling cube"
    )
}

data class GameEndResult(
    val winner: Player,
    val loser: Player,
    val outcomeType: GameOutcomeType,
    val basePoints: Int,
    val stakeMultiplier: Int,
    val totalPoints: Int,
    val description: String
)

data class EngineSnapshot(
    val board: List<CheckerStack>,
    val remainingDice: List<Int>,
    val doubleWaterfallQueue: List<List<Int>>,
    val headTakesThisTurnMap: Map<Player, Int>,
    val isOpponentPlayingTransferred: Boolean,
    val isFreshKushRoll: Boolean,
    val borneOffCount: Map<Player, Int>,
    val bar: Map<Player, Int>,
    val activePlayer: Player,
    val roller: Player,
    val gameStatus: GameStatus,
    val winner: Player?,
    val isFirstTurnMap: Map<Player, Boolean>,
    val stakeMultiplier: Int = 1,
    val doublingOwner: Player? = null,
    val gameEndResult: GameEndResult? = null
)

class GameEngine {

    companion object {
        const val MAX_DOUBLING_CUBE = 64
    }

    var language: String = "RU"
    var gameMode: GameMode = GameMode.MAD

    // Board with 24 points (absolute indices 0..23)
    var board = MutableList(24) { CheckerStack(0, null) }
        private set

    // Bar for captured checkers in Short Backgammon
    val bar = mutableMapOf(Player.WHITE to 0, Player.BLACK to 0)

    var activePlayer: Player = Player.WHITE
        private set

    var roller: Player = Player.WHITE
        private set

    // The dice values left to be played in the current sub-phase of the turn (e.g. [3, 5] or [6, 6])
    val remainingDice = mutableListOf<Int>()

    // Remaining phases for doubles (e.g., [ [6,6,6,6] ] left in queue when 5:5 was rolled in MAD mode)
    val doubleWaterfallQueue = mutableListOf<List<Int>>()

    // How many checkers were taken from the head (relative index 0) by each player in the CURRENT turn
    val headTakesThisTurnMap = mutableMapOf(Player.WHITE to 0, Player.BLACK to 0)

    val headTakesThisTurn: Int
        get() = headTakesThisTurnMap[activePlayer] ?: 0

    // True if activePlayer is making moves that were transferred because the original roller got stuck
    var isOpponentPlayingTransferred = false
        private set

    var isFreshKushRoll = false
        private set

    var lastRolledD1 = 0
        private set
    var lastRolledD2 = 0
        private set

    var borneOffCount = mutableMapOf(Player.WHITE to 0, Player.BLACK to 0)
        private set

    var gameStatus: GameStatus = GameStatus.BEFORE_ROLL
        private set

    var winner: Player? = null
        private set

    // --- Doubling Cube & Match Outcomes (GROK-G11 & User Rules) ---
    var stakeMultiplier: Int = 1
        private set
    var doublingOwner: Player? = null
        private set
    var isDoublingOffered: Boolean = false
        private set
    var doublingProposer: Player? = null
        private set
    var gameEndResult: GameEndResult? = null
        private set

    // --- Undo Move Consensus (Disabled if at least one player disallows) ---
    var allowUndoPlayer1: Boolean = true
    var allowUndoPlayer2: Boolean = true
    val isUndoAllowed: Boolean
        get() = allowUndoPlayer1 && allowUndoPlayer2

    // Tracks when each turn actually starts (with timestamp)
    var turnStartTimeMillis: Long = 0
        private set

    val logs = mutableListOf<GameLogEntry>()

    // Check if it's the very first turn of the game for this player (important for head block exception)
    val isFirstTurnMap = mutableMapOf(Player.WHITE to true, Player.BLACK to true)

    private val diceRollHistory = mutableListOf<Pair<Int, Int>>()

    var humanPlayerColor: Player = Player.WHITE

    val botPlayerColor: Player
        get() = humanPlayerColor.other()

    var diceLot1User: Int = 0
    var diceLot1Bot: Int = 0
    var diceLot2White: Int = 0
    var diceLot2Black: Int = 0

    init {
        resetGame(GameMode.MAD)
    }

    fun resetGame(mode: GameMode = this.gameMode) {
        this.gameMode = mode
        board = MutableList(24) { CheckerStack(0, null) }
        bar[Player.WHITE] = 0
        bar[Player.BLACK] = 0

        if (mode == GameMode.SHORT) {
            // Traditional Short Backgammon starting layout (15 checkers each)
            // White moves counter-clockwise 23 -> 0 (Home: points 6..1, abs 5..0)
            board[23] = CheckerStack(2, Player.WHITE) // Point 24
            board[12] = CheckerStack(5, Player.WHITE) // Point 13
            board[7] = CheckerStack(3, Player.WHITE)  // Point 8
            board[5] = CheckerStack(5, Player.WHITE)  // Point 6

            // Black moves clockwise 0 -> 23 (Home: points 19..24, abs 18..23)
            board[0] = CheckerStack(2, Player.BLACK)  // Point 1
            board[11] = CheckerStack(5, Player.BLACK) // Point 12
            board[16] = CheckerStack(3, Player.BLACK) // Point 17
            board[18] = CheckerStack(5, Player.BLACK) // Point 19
        } else {
            // White starts on Head (absolute index 23)
            board[23] = CheckerStack(15, Player.WHITE)
            // Black starts on Head (absolute index 11)
            board[11] = CheckerStack(15, Player.BLACK)
        }

        activePlayer = Player.WHITE
        roller = Player.WHITE
        remainingDice.clear()
        doubleWaterfallQueue.clear()
        headTakesThisTurnMap[Player.WHITE] = 0
        headTakesThisTurnMap[Player.BLACK] = 0
        isOpponentPlayingTransferred = false
        isFreshKushRoll = false
        borneOffCount[Player.WHITE] = 0
        borneOffCount[Player.BLACK] = 0
        gameStatus = GameStatus.LOT_STAGE1
        winner = null
        stakeMultiplier = 1
        doublingOwner = null
        isDoublingOffered = false
        doublingProposer = null
        gameEndResult = null
        turnStartTimeMillis = System.currentTimeMillis()
        logs.clear()
        isFirstTurnMap[Player.WHITE] = true
        isFirstTurnMap[Player.BLACK] = true
        diceRollHistory.clear()
        lastRolledD1 = 0
        lastRolledD2 = 0

        humanPlayerColor = Player.WHITE
        diceLot1User = 0
        diceLot1Bot = 0
        diceLot2White = 0
        diceLot2Black = 0

        addLogEntry("Новый сеанс игры начат. Требуется провести жеребьёвку!")
    }

    fun transitionToStage2() {
        if (gameStatus == GameStatus.LOT_STAGE1) {
            gameStatus = GameStatus.LOT_STAGE2
        }
    }

    fun rollLotStage1Custom(userVal: Int, botVal: Int, oppName: String? = null): Boolean {
        diceLot1User = userVal
        diceLot1Bot = botVal

        val oppDisplay = oppName ?: "Бот"
        addLogEntry("🎲 Жеребьёвка первого хода: Вы выбросили $diceLot1User, $oppDisplay выбросил $diceLot1Bot")

        humanPlayerColor = Player.WHITE
        if (diceLot1User > diceLot1Bot) {
            activePlayer = Player.WHITE
            roller = Player.WHITE
            addLogEntry("⚪ Вы выиграли жеребьёвку! Вы играете Белыми и делаете первый ход.")
            return true
        } else if (diceLot1User < diceLot1Bot) {
            activePlayer = Player.BLACK
            roller = Player.BLACK
            addLogEntry("⚫ $oppDisplay выиграл жеребьёвку! $oppDisplay делает первый ход.")
            return true
        } else {
            addLogEntry("🔄 Ничья в жеребьёвке! Переброс!")
            return false
        }
    }

    fun rollLotStage2Custom(whiteVal: Int, blackVal: Int, oppName: String? = null): Boolean {
        diceLot2White = whiteVal
        diceLot2Black = blackVal

        val oppDisplay = oppName ?: "Бот"
        addLogEntry("🎲 Жеребьёвка первого хода: Вы (Белые) выбросили $diceLot2White, $oppDisplay (Черные) выбросили $diceLot2Black")

        if (diceLot2White > diceLot2Black) {
            activePlayer = Player.WHITE
            roller = Player.WHITE
            addLogEntry("⚪ Вы (Белые) выиграли жеребьёвку первого хода!")
            return true
        } else if (diceLot2White < diceLot2Black) {
            activePlayer = Player.BLACK
            roller = Player.BLACK
            addLogEntry("⚫ $oppDisplay (Черные) выиграли жеребьёвку первого хода!")
            return true
        } else {
            addLogEntry("🔄 Ничья в жеребьёвке первого хода! Переброс!")
            return false
        }
    }

    fun rollLotStage1(): Boolean {
        diceLot1User = (1..6).random()
        diceLot1Bot = (1..6).random()

        addLogEntry("🎲 Жеребьёвка первого хода: Вы выбросили $diceLot1User, Бот выбросил $diceLot1Bot")

        humanPlayerColor = Player.WHITE
        if (diceLot1User > diceLot1Bot) {
            activePlayer = Player.WHITE
            roller = Player.WHITE
            addLogEntry("⚪ Вы выиграли жеребьёвку! Вы играете Белыми и делаете первый ход.")
            return true
        } else if (diceLot1User < diceLot1Bot) {
            activePlayer = Player.BLACK
            roller = Player.BLACK
            addLogEntry("⚫ Бот выиграл жеребьёвку! Бот играет Черными и делает первый ход.")
            return true
        } else {
            addLogEntry("🔄 Ничья в жеребьёвке! Переброс!")
            return false
        }
    }

    fun rollLotStage2(): Boolean {
        diceLot2White = (1..6).random()
        diceLot2Black = (1..6).random()

        addLogEntry("🎲 Жеребьёвка первого хода: Вы (Белые) выбросили $diceLot2White, Бот (Черные) выбросил $diceLot2Black")

        if (diceLot2White > diceLot2Black) {
            activePlayer = Player.WHITE
            roller = Player.WHITE
            addLogEntry("⚪ Вы (Белые) выиграли жеребьёвку первого хода!")
            return true
        } else if (diceLot2White < diceLot2Black) {
            activePlayer = Player.BLACK
            roller = Player.BLACK
            addLogEntry("⚫ Бот (Черные) выиграл жеребьёвку первого хода!")
            return true
        } else {
            addLogEntry("🔄 Ничья в жеребьёвке первого хода! Переброс!")
            return false
        }
    }

    /**
     * GROK-G03: Stage 2 lot winner does NOT play the lot dice.
     * Game moves to BEFORE_ROLL and the winner rolls a fresh pair of dice.
     */
    fun applyLotStage2WinnerAndStart() {
        recordTurnStarted()
        isOpponentPlayingTransferred = false
        headTakesThisTurnMap[Player.WHITE] = 0
        headTakesThisTurnMap[Player.BLACK] = 0
        isFirstTurnMap[Player.WHITE] = true
        isFirstTurnMap[Player.BLACK] = true

        doubleWaterfallQueue.clear()
        remainingDice.clear()

        val winnerLabel = if (activePlayer == humanPlayerColor) {
            "Вы (${activePlayer.label(language)})"
        } else {
            "Бот (${activePlayer.label(language)})"
        }
        addLogEntry("🎮 Жеребьёвка завершена! Право первого хода за $winnerLabel. Бросайте кости для первого хода!")

        gameStatus = GameStatus.BEFORE_ROLL
    }

    /**
     * Starts a direct P2P or preset match without requiring the local two-stage lot process.
     */
    fun startDirectMatch(localColor: Player, firstMover: Player = Player.WHITE) {
        humanPlayerColor = localColor
        activePlayer = firstMover
        roller = firstMover
        gameStatus = GameStatus.BEFORE_ROLL
        recordTurnStarted()
        isOpponentPlayingTransferred = false
        headTakesThisTurnMap[Player.WHITE] = 0
        headTakesThisTurnMap[Player.BLACK] = 0
        isFirstTurnMap[Player.WHITE] = true
        isFirstTurnMap[Player.BLACK] = true
        doubleWaterfallQueue.clear()
        remainingDice.clear()
        stakeMultiplier = 1
        doublingOwner = null
        isDoublingOffered = false
        doublingProposer = null
        gameEndResult = null
        addLogEntry("🎮 Прямой матч начат. Ваш цвет: ${localColor.label(language)}. Первый ход: ${firstMover.label(language)}.")
    }

    /**
     * Doubling Cube Logic (User Rules & Backgammon Rules).
     * Either player can propose doubling when the cube is neutral (doublingOwner == null),
     * or the player who accepted the previous double (doublingOwner == player) can redouble.
     */
    fun canProposeDoubling(player: Player): Boolean {
        if (gameStatus != GameStatus.BEFORE_ROLL && gameStatus != GameStatus.PLAYER_MOVE) return false
        if (isDoublingOffered) return false
        if (stakeMultiplier >= MAX_DOUBLING_CUBE) return false
        return doublingOwner == null || doublingOwner == player
    }

    fun proposeDoubling(proposingPlayer: Player): Boolean {
        if (!canProposeDoubling(proposingPlayer)) return false
        isDoublingOffered = true
        doublingProposer = proposingPlayer
        val nextMultiplier = stakeMultiplier * 2
        addLogEntry("🎲 Игрок ${proposingPlayer.label(language)} предлагает удвоить ставку до x$nextMultiplier!")
        return true
    }

    fun acceptDoubling(): Boolean {
        if (!isDoublingOffered || doublingProposer == null) return false
        val acceptingPlayer = doublingProposer!!.other()
        val nextStake = stakeMultiplier * 2
        if (nextStake > MAX_DOUBLING_CUBE) return false
        stakeMultiplier = nextStake
        doublingOwner = acceptingPlayer
        isDoublingOffered = false
        doublingProposer = null
        addLogEntry("🤝 Игрок ${acceptingPlayer.label(language)} принял удвоение ставки! Текущий коэффициент: x$stakeMultiplier.")
        return true
    }

    fun declineDoubling(): Boolean {
        if (!isDoublingOffered || doublingProposer == null) return false
        val decliningPlayer = doublingProposer!!.other()
        val winningPlayer = doublingProposer!!
        isDoublingOffered = false
        doublingProposer = null
        winner = winningPlayer
        gameStatus = GameStatus.GAME_OVER
        val totalPts = 1 * stakeMultiplier
        gameEndResult = GameEndResult(
            winner = winningPlayer,
            loser = decliningPlayer,
            outcomeType = GameOutcomeType.RESIGNATION,
            basePoints = 1,
            stakeMultiplier = stakeMultiplier,
            totalPoints = totalPts,
            description = if (language == "RU") {
                "Отказ от удвоения: ${decliningPlayer.label(language)} сдался. Победил ${winningPlayer.label(language)} (ставка x$stakeMultiplier = $totalPts очков)."
            } else {
                "Doubling declined: ${decliningPlayer.label(language)} surrendered. Winner: ${winningPlayer.label(language)} (stake x$stakeMultiplier = $totalPts pts)."
            }
        )
        addLogEntry("🏳️ Игрок ${decliningPlayer.label(language)} отклонил удвоение и признал поражение. Победил ${winningPlayer.label(language)} ($totalPts очков)!")
        return true
    }

    /**
     * Calculates the exact match outcome and points according to the 4-tier rule:
     * 1. Simple game (Одинцовка) = 1 pt (loser borne off >= 1 checker)
     * 2. Mars (Марс) = 2 pts (loser borne off 0, brought all out of initial home, but not all into final home)
     * 3. Coke (Кокс) = 3 pts (loser borne off 0, and has >= 1 checker still in initial home or on bar)
     * 4. Home Mars (Домашний Марс) = 4 pts (loser borne off 0, but brought all 15 checkers into final home!)
     * Final points = basePoints * stakeMultiplier
     */
    fun calculateGameOutcome(winner: Player): GameEndResult {
        val loser = winner.other()
        val loserBorneOff = borneOffCount[loser] ?: 0

        val outcomeType = if (loserBorneOff >= 1) {
            GameOutcomeType.SIMPLE
        } else {
            val loserBarCount = bar[loser] ?: 0
            var initialHomeCount = loserBarCount
            var middleCount = 0
            var finalHomeCount = 0

            for (r in 0..23) {
                val abs = relativeToAbsolute(loser, r)
                val stack = board[abs]
                if (stack.player == loser && stack.count > 0) {
                    when (r) {
                        in 0..5 -> initialHomeCount += stack.count
                        in 6..17 -> middleCount += stack.count
                        in 18..23 -> finalHomeCount += stack.count
                    }
                }
            }

            when {
                // Домашний Марс: все 15 фишек заведены в финальный дом (18..23), но ни одна не успела покинуть поле
                initialHomeCount == 0 && middleCount == 0 && finalHomeCount == 15 -> {
                    GameOutcomeType.HOME_MARS
                }
                // Кокс: хотя бы одна фишка осталась в начальном доме (0..5) или на баре
                initialHomeCount > 0 -> {
                    GameOutcomeType.COKE
                }
                // Марс: все выведены из начального дома, но не все заведены в финальный дом
                else -> {
                    GameOutcomeType.MARS
                }
            }
        }

        val basePoints = outcomeType.basePoints
        val totalPoints = basePoints * stakeMultiplier
        val outcomeName = if (language == "RU") outcomeType.titleRu else outcomeType.titleEn
        val desc = if (language == "RU") outcomeType.descriptionRu else outcomeType.descriptionEn
        val descFull = "$outcomeName ($basePoints ${if (language == "RU") "балл." else "pts"}) × $stakeMultiplier = $totalPoints ${if (language == "RU") "очков" else "pts"}. $desc"

        return GameEndResult(
            winner = winner,
            loser = loser,
            outcomeType = outcomeType,
            basePoints = basePoints,
            stakeMultiplier = stakeMultiplier,
            totalPoints = totalPoints,
            description = descFull
        )
    }

    /**
     * Surrender/resign the match for the specified player.
     */
    fun resign(player: Player) {
        winner = player.other()
        gameStatus = GameStatus.GAME_OVER
        val totalPts = 1 * stakeMultiplier
        gameEndResult = GameEndResult(
            winner = winner!!,
            loser = player,
            outcomeType = GameOutcomeType.RESIGNATION,
            basePoints = 1,
            stakeMultiplier = stakeMultiplier,
            totalPoints = totalPts,
            description = if (language == "RU") {
                "Сдача партии (1 балл) × $stakeMultiplier = $totalPts очков. Игрок ${player.label(language)} признал поражение."
            } else {
                "Resignation (1 pt) × $stakeMultiplier = $totalPts pts. Player ${player.label(language)} surrendered."
            }
        )
        addLogEntry("🏳️ Игрок ${player.label(language)} признал поражение. Победил ${winner?.label(language)} ($totalPts очков)!")
    }

    // Helper: Map relative coordinates (0 to 23) to absolute board indices (0 to 23)
    fun relativeToAbsolute(player: Player, relativeIndex: Int): Int {
        return if (gameMode == GameMode.SHORT) {
            if (player == Player.WHITE) {
                (23 - relativeIndex + 24) % 24
            } else {
                (relativeIndex + 24) % 24
            }
        } else {
            if (player == Player.WHITE) {
                (23 - relativeIndex + 24) % 24
            } else {
                (11 - relativeIndex + 24) % 24
            }
        }
    }

    // Helper: Map absolute board indices to relative coordinates for a player
    fun absoluteToRelative(player: Player, absoluteIndex: Int): Int {
        return if (gameMode == GameMode.SHORT) {
            if (player == Player.WHITE) {
                (23 - absoluteIndex + 24) % 24
            } else {
                (absoluteIndex + 24) % 24
            }
        } else {
            if (player == Player.WHITE) {
                (23 - absoluteIndex + 24) % 24
            } else {
                (11 - absoluteIndex + 24) % 24
            }
        }
    }

    fun addLogEntry(message: String) {
        val sdf = java.text.SimpleDateFormat("HH:mm:ss", java.util.Locale.getDefault())
        val timeString = sdf.format(java.util.Date())
        logs.add(GameLogEntry(timeString, message))
        println("[$timeString] $message")
    }

    fun recordTurnStarted() {
        turnStartTimeMillis = System.currentTimeMillis()
        val sdf = java.text.SimpleDateFormat("HH:mm:ss", java.util.Locale.getDefault())
        val timeStr = sdf.format(java.util.Date(turnStartTimeMillis))
        addLogEntry("▶️ Начало хода игрока ${activePlayer.label(language)} зафиксировано автоматически в $timeStr.")
    }

    // Roll dice (handled by RNG) and start moving phase
    fun roll(d1: Int, d2: Int) {
        if (gameStatus != GameStatus.BEFORE_ROLL) return

        recordTurnStarted()
        lastRolledD1 = d1
        lastRolledD2 = d2
        isOpponentPlayingTransferred = false
        activePlayer = roller
        headTakesThisTurnMap[Player.WHITE] = 0
        headTakesThisTurnMap[Player.BLACK] = 0

        addLogEntry("🎲 ${roller.label(language)} бросает зарики: $d1 и $d2")

        if (d1 == d2) {
            // Double (куш)!
            if (gameMode == GameMode.MAD) {
                // Rules for Mad Backgammon (Бешеные нарды):
                // D:D -> Runs sequences from D up to 6 sequentially!
                doubleWaterfallQueue.clear()
                for (size in d1..6) {
                    doubleWaterfallQueue.add(listOf(size, size, size, size))
                }
                remainingDice.clear()
                remainingDice.addAll(doubleWaterfallQueue.removeAt(0))

                val chainStr = (d1..6).joinToString(" -> ") { "4x$it" }
                addLogEntry("🔥 КРЕЙЗИ КУШ $d1:$d1! Цепочка каскадов: $chainStr")
                isFreshKushRoll = true
            } else {
                // Classic Long or Short Backgammon: exactly 4 moves of value d1
                doubleWaterfallQueue.clear()
                remainingDice.clear()
                remainingDice.addAll(listOf(d1, d1, d1, d1))
                addLogEntry("🎲 КУШ $d1:$d1! Игроку доступно 4 хода по $d1")
                isFreshKushRoll = false
            }
        } else {
            // Standard non-double roll
            doubleWaterfallQueue.clear()
            remainingDice.clear()
            remainingDice.add(d1)
            remainingDice.add(d2)
            isFreshKushRoll = false
        }

        gameStatus = GameStatus.PLAYER_MOVE
        checkForLackOfMovesAndTransfer()
    }

    private fun countCheckersOutsideHead(player: Player): Int {
        var count = 0
        for (r in 1..23) {
            val abs = relativeToAbsolute(player, r)
            if (board[abs].player == player) {
                count += board[abs].count
            }
        }
        return count
    }

    /**
     * GROK-G02: In MAD mode, if activePlayer (roller) has no legal moves, transfer remaining dice to opponent.
     * Opponent plays whatever they can. If opponent runs out of moves, remaining dice of this cascade BURN.
     * Crucially: control does NOT ping-pong back to the roller during the same cascade!
     */
    fun checkForLackOfMovesAndTransfer() {
        if (gameStatus != GameStatus.PLAYER_MOVE) return

        val legalMoves = getAllLegalMoves(activePlayer, remainingDice)
        if (legalMoves.isNotEmpty()) {
            return
        }

        if (remainingDice.isEmpty()) {
            if (doubleWaterfallQueue.isNotEmpty()) {
                loadNextWaterfallPhase()
            } else {
                advanceTurn()
            }
            return
        }

        if (gameMode != GameMode.MAD) {
            // In classic Long / Short Backgammon, unplayable dice are simply lost!
            addLogEntry("⚠️ У игрока ${activePlayer.label(language)} нет возможных ходов. Оставшиеся ходы сгорают.")
            remainingDice.clear()
            advanceTurn()
            return
        }

        // MAD MODE (Бешеные нарды):
        val opponent = activePlayer.other()

        // Transfer occurs ONLY from roller to opponent when roller gets stuck
        if (activePlayer == roller) {
            val opponentMoves = getAllLegalMoves(opponent, remainingDice)
            if (opponentMoves.isNotEmpty()) {
                addLogEntry("🔴 У игрока ${activePlayer.label(language)} нет ходов! Остаток ходов $remainingDice передан игроку ${opponent.label(language)}.")
                activePlayer = opponent
                isOpponentPlayingTransferred = true
                addLogEntry("⚡ Игрок ${activePlayer.label(language)} вступает в игру с переданными ходами!")
                return
            }
        }

        // Neither player can move with these dice, or opponent was already playing transferred moves and got stuck
        addLogEntry("⚠️ Несыгранные зарики $remainingDice сгорают.")
        if (doubleWaterfallQueue.isNotEmpty()) {
            loadNextWaterfallPhase()
        } else {
            remainingDice.clear()
            advanceTurn()
        }
    }

    private fun loadNextWaterfallPhase() {
        if (doubleWaterfallQueue.isNotEmpty()) {
            remainingDice.clear()
            remainingDice.addAll(doubleWaterfallQueue.removeAt(0))
            addLogEntry("⚡ Переход к следующему каскаду куша: $remainingDice")
            // A new cascade must start with the original roller!
            activePlayer = roller
            isOpponentPlayingTransferred = false
            checkForLackOfMovesAndTransfer()
        } else {
            advanceTurn()
        }
    }

    // Execute checker movement
    fun makeMove(move: Move, validateLegal: Boolean = true): Boolean {
        if (gameStatus != GameStatus.PLAYER_MOVE) return false

        val expectedTo = if (move.from == -1) move.diceUsed - 1 else move.from + move.diceUsed
        if (move.to != expectedTo) {
            System.err.println("Illegal move attempt: move.to (${move.to}) != expected ($expectedTo)")
            return false
        }

        // Double check eligibility
        if (validateLegal && !isMoveLegal(activePlayer, move)) {
            System.err.println("Illegal move attempt of size ${move.diceUsed} from ${move.from} to ${move.to}")
            return false
        }

        isFreshKushRoll = false

        // Consume dice
        remainingDice.remove(move.diceUsed)

        if (move.from == -1) {
            // Entry from bar
            bar[activePlayer] = (bar[activePlayer] ?: 1) - 1
            val absTo = relativeToAbsolute(activePlayer, move.to)
            val destStack = board[absTo]
            if (destStack.player != null && destStack.player != activePlayer) {
                // Hit opponent blot!
                val opp = destStack.player!!
                bar[opp] = (bar[opp] ?: 0) + 1
                board[absTo] = CheckerStack(1, activePlayer)
                addLogEntry("💥 ${activePlayer.label(language)} вошел с бара на позицию ${move.to + 1} и выбил фишку ${opp.label(language)} на бар!")
            } else {
                board[absTo] = CheckerStack(
                    count = destStack.count + 1,
                    player = activePlayer
                )
                addLogEntry("👉 ${activePlayer.label(language)} вошел с бара на позицию ${move.to + 1} (кубик: ${move.diceUsed})")
            }
        } else {
            val absFrom = relativeToAbsolute(activePlayer, move.from)

            // Update source stack
            val sourceStack = board[absFrom]
            board[absFrom] = CheckerStack(
                count = sourceStack.count - 1,
                player = if (sourceStack.count - 1 == 0) null else activePlayer
            )

            // If from is head
            if (move.from == 0) {
                headTakesThisTurnMap[activePlayer] = (headTakesThisTurnMap[activePlayer] ?: 0) + 1
            }

            if (move.to >= 24) {
                // Borne off!
                borneOffCount[activePlayer] = (borneOffCount[activePlayer] ?: 0) + 1
                addLogEntry("📤 ${activePlayer.label(language)} выбросил фишку с позиции ${move.from + 1}")

                // Check win condition
                if (borneOffCount[activePlayer] == 15) {
                    winner = activePlayer
                    gameStatus = GameStatus.GAME_OVER
                    gameEndResult = calculateGameOutcome(activePlayer)
                    addLogEntry("🏆 ИГРА ЗАВЕРШЕНА! Игрок ${activePlayer.label(language)} победил! Исход: ${gameEndResult?.description}")
                    return true
                }
            } else {
                // Normal board move
                val absTo = relativeToAbsolute(activePlayer, move.to)
                val destStack = board[absTo]
                if (gameMode == GameMode.SHORT && destStack.player != null && destStack.player != activePlayer) {
                    // Hit opponent blot!
                    val opp = destStack.player!!
                    bar[opp] = (bar[opp] ?: 0) + 1
                    board[absTo] = CheckerStack(1, activePlayer)
                    addLogEntry("💥 ${activePlayer.label(language)} походил ${move.from + 1} -> ${move.to + 1} и выбил фишку ${opp.label(language)} на бар!")
                } else {
                    board[absTo] = CheckerStack(
                        count = (destStack.count) + 1,
                        player = activePlayer
                    )
                    addLogEntry("👉 ${activePlayer.label(language)} походил: ${move.from + 1} -> ${move.to + 1} (кубик: ${move.diceUsed})")
                }
            }
        }

        // Check if current phase of dice is fully completed
        if (remainingDice.isEmpty()) {
            if (doubleWaterfallQueue.isNotEmpty()) {
                loadNextWaterfallPhase()
            } else {
                advanceTurn()
            }
        } else {
            // Keep going with current player, check if stuck
            checkForLackOfMovesAndTransfer()
        }

        return true
    }

    private fun advanceTurn() {
        isFirstTurnMap[roller] = false
        roller = roller.other()
        activePlayer = roller
        headTakesThisTurnMap[Player.WHITE] = 0
        headTakesThisTurnMap[Player.BLACK] = 0
        isOpponentPlayingTransferred = false
        remainingDice.clear()
        doubleWaterfallQueue.clear()
        gameStatus = GameStatus.BEFORE_ROLL
        turnStartTimeMillis = System.currentTimeMillis()
        addLogEntry("🔄 Переход хода к ${roller.label(language)}.")
    }

    // Are all checkers in the home (points 19-24, relative 18..23)?
    fun areAllCheckersInHome(player: Player, boardState: List<CheckerStack> = this.board, barState: Map<Player, Int> = this.bar): Boolean {
        if (gameMode == GameMode.SHORT && (barState[player] ?: 0) > 0) return false
        var outsideCount = 0
        for (r in 0..17) {
            val abs = relativeToAbsolute(player, r)
            if (boardState[abs].player == player) {
                outsideCount += boardState[abs].count
            }
        }
        return outsideCount == 0
    }

    /**
     * Checks if a single step is valid on a given board simulation state.
     */
    fun isSingleStepLegal(
        player: Player,
        fromRel: Int,
        dice: Int,
        boardState: List<CheckerStack>,
        barState: Map<Player, Int>,
        headTakes: Int,
        isFirstTurn: Boolean
    ): Boolean {
        if (gameMode == GameMode.SHORT) {
            val checkersOnBar = barState[player] ?: 0
            if (checkersOnBar > 0) {
                if (fromRel != -1) return false
                val toRel = dice - 1
                val absTo = relativeToAbsolute(player, toRel)
                val destStack = boardState[absTo]
                if (destStack.player != null && destStack.player != player && destStack.count >= 2) {
                    return false
                }
                return true
            } else {
                if (fromRel == -1) return false
            }
        } else {
            if (fromRel == -1) return false
        }

        val toRel = fromRel + dice
        val absFrom = relativeToAbsolute(player, fromRel)
        val srcStack = boardState[absFrom]
        if (srcStack.player != player || srcStack.count == 0) return false

        // Case 1: Bear off
        if (toRel >= 24) {
            if (!areAllCheckersInHome(player, boardState, barState)) return false
            if (toRel == 24) return true

            if (gameMode != GameMode.MAD) {
                for (r in 18 until fromRel) {
                    val abs = relativeToAbsolute(player, r)
                    if (boardState[abs].player == player && boardState[abs].count > 0) {
                        return false
                    }
                }
                return true
            }
            return false // In MAD, only exact match exit
        }

        // Case 2: Move on board
        if (gameMode == GameMode.MAD && fromRel >= 18 && toRel < 24) {
            return false // No moves within home in MAD
        }

        val absTo = relativeToAbsolute(player, toRel)
        val destStack = boardState[absTo]
        if (destStack.player != null && destStack.player != player) {
            if (gameMode == GameMode.SHORT) {
                if (destStack.count >= 2) return false
            } else {
                return false // In Long/MAD >= 1 opponent checker blocks
            }
        }

        // GROK-G01: Head takes rule
        if (gameMode != GameMode.SHORT && fromRel == 0) {
            val isFirstTurnKush346 = (gameMode == GameMode.LONG &&
                    isFirstTurn &&
                    lastRolledD1 == lastRolledD2 &&
                    (lastRolledD1 == 3 || lastRolledD1 == 4 || lastRolledD1 == 6))
            val maxHeadTakes = if (isFirstTurnKush346) 2 else 1
            if (headTakes >= maxHeadTakes) {
                return false
            }
        }

        // GROK-G07: Linear 6-block check
        if (gameMode != GameMode.SHORT && willBuildIllegalSixBlockSim(player, fromRel, toRel, boardState)) {
            return false
        }

        return true
    }

    /**
     * GROK-G05: Generates all legal maximal playouts and returns allowed first moves.
     */
    fun getAllLegalMoves(player: Player, diceValues: List<Int>): List<Move> {
        if (diceValues.isEmpty()) return emptyList()

        val isFirst = isFirstTurnMap[player] ?: false
        val currentHeadTakes = headTakesThisTurnMap[player] ?: 0

        // Find all maximal playouts
        val playouts = mutableListOf<List<Move>>()
        val currentPath = mutableListOf<Move>()
        var maxLenFound = 0
        val targetMaxLen = diceValues.size

        fun dfs(
            curBoard: MutableList<CheckerStack>,
            curBar: MutableMap<Player, Int>,
            curDice: MutableList<Int>,
            curHeadTakes: Int
        ) {
            // Branch-and-bound: if currentPath + remaining dice cannot reach or exceed maxLenFound, prune branch!
            if (currentPath.size + curDice.size < maxLenFound) return

            val distinctDice = curDice.distinct()
            var movedAny = false

            val candidateFroms = if (gameMode == GameMode.SHORT && (curBar[player] ?: 0) > 0) {
                listOf(-1)
            } else {
                (0..23).filter { r ->
                    val abs = relativeToAbsolute(player, r)
                    curBoard[abs].player == player && curBoard[abs].count > 0
                }
            }

            for (d in distinctDice) {
                for (from in candidateFroms) {
                    if (isSingleStepLegal(player, from, d, curBoard, curBar, curHeadTakes, isFirst)) {
                        movedAny = true
                        val toRel = if (from == -1) d - 1 else from + d
                        val move = Move(from, toRel, d)
                        currentPath.add(move)

                        // Apply step to local state
                        val absFrom = if (from == -1) -1 else relativeToAbsolute(player, from)
                        val absTo = if (toRel < 24) relativeToAbsolute(player, toRel) else -1

                        val oldSrc = if (absFrom != -1) curBoard[absFrom] else null
                        val oldDest = if (absTo != -1) curBoard[absTo] else null
                        val oldBarPlayer = curBar[player] ?: 0
                        val oldBarOpp = curBar[player.other()] ?: 0

                        if (from == -1) {
                            curBar[player] = oldBarPlayer - 1
                        } else {
                            curBoard[absFrom] = CheckerStack(oldSrc!!.count - 1, if (oldSrc.count - 1 == 0) null else player)
                        }

                        if (toRel < 24) {
                            if (gameMode == GameMode.SHORT && oldDest!!.player != null && oldDest.player != player) {
                                curBar[player.other()] = oldBarOpp + 1
                                curBoard[absTo] = CheckerStack(1, player)
                            } else {
                                curBoard[absTo] = CheckerStack(oldDest!!.count + 1, player)
                            }
                        }

                        curDice.remove(d)
                        val newHeadTakes = if (from == 0) curHeadTakes + 1 else curHeadTakes

                        dfs(curBoard, curBar, curDice, newHeadTakes)

                        // Backtrack
                        curDice.add(d)
                        if (from == -1) {
                            curBar[player] = oldBarPlayer
                        } else {
                            curBoard[absFrom] = oldSrc!!
                        }
                        if (toRel < 24) {
                            curBoard[absTo] = oldDest!!
                            curBar[player.other()] = oldBarOpp
                        }
                        currentPath.removeAt(currentPath.size - 1)
                    }
                }
            }

            if (!movedAny && currentPath.isNotEmpty()) {
                if (currentPath.size > maxLenFound) {
                    maxLenFound = currentPath.size
                    playouts.clear() // Discard strictly shorter paths
                    playouts.add(currentPath.toList())
                } else if (currentPath.size == maxLenFound && playouts.size < 500) {
                    playouts.add(currentPath.toList())
                }
            }
        }

        val boardCopy = board.map { it.copy() }.toMutableList()
        val barCopy = bar.toMutableMap()
        val diceCopy = diceValues.toMutableList()

        dfs(boardCopy, barCopy, diceCopy, currentHeadTakes)

        if (playouts.isEmpty()) return emptyList()

        // Filter playouts by maximal length
        val maxLen = playouts.maxOf { it.size }
        var maximalPlayouts = playouts.filter { it.size == maxLen }

        // GROK-G05: If length is 1 and 2 different dice were available, must play larger die if possible
        if (maxLen == 1 && diceValues.size >= 2 && diceValues[0] != diceValues[1]) {
            val maxDie = maxOf(diceValues[0], diceValues[1])
            val hasMaxDieMove = maximalPlayouts.any { it.first().diceUsed == maxDie }
            if (hasMaxDieMove) {
                maximalPlayouts = maximalPlayouts.filter { it.first().diceUsed == maxDie }
            }
        }

        // Return distinct first moves of allowed maximal playouts
        val validFirstMoves = maximalPlayouts.map { it.first() }.distinct()
        return validFirstMoves
    }

    fun isMoveLegal(player: Player, move: Move): Boolean {
        val expectedTo = if (move.from == -1) move.diceUsed - 1 else move.from + move.diceUsed
        if (move.to != expectedTo) return false
        if (!remainingDice.contains(move.diceUsed)) return false

        val legals = getAllLegalMoves(player, remainingDice)
        if (legals.any { it.from == move.from && it.to == move.to && it.diceUsed == move.diceUsed }) {
            return true
        }

        val curTakes = headTakesThisTurnMap[player] ?: 0
        val isFirst = isFirstTurnMap[player] ?: false
        return isSingleStepLegal(player, move.from, move.diceUsed, board, bar, curTakes, isFirst)
    }

    fun isMoveLegal(player: Player, fromRel: Int, dice: Int): Boolean {
        if (!remainingDice.contains(dice)) return false
        val legals = getAllLegalMoves(player, remainingDice)
        if (legals.any { it.from == fromRel && it.diceUsed == dice }) {
            return true
        }

        val curTakes = headTakesThisTurnMap[player] ?: 0
        val isFirst = isFirstTurnMap[player] ?: false
        return isSingleStepLegal(player, fromRel, dice, board, bar, curTakes, isFirst)
    }

    /**
     * GROK-G06: findReachablePathsFrom operates purely on a local clone.
     * The live engine board is NEVER mutated.
     */
    fun findReachablePathsFrom(player: Player, fromRel: Int): List<ReachablePath> {
        val results = mutableListOf<ReachablePath>()
        val currentSteps = mutableListOf<Move>()

        val simBoard = board.map { it.copy() }.toMutableList()
        val simRemainingDice = remainingDice.toMutableList()
        val simHeadTakes = headTakesThisTurnMap.toMutableMap()
        val simBar = bar.toMutableMap()
        val isFirst = isFirstTurnMap[player] ?: false

        fun search(currFrom: Int) {
            if (currentSteps.isNotEmpty()) {
                val finalTo = currentSteps.last().to
                val existing = results.find { it.finalTo == finalTo }
                if (existing == null) {
                    results.add(ReachablePath(finalTo, currentSteps.toList()))
                } else if (currentSteps.size < existing.steps.size) {
                    results.remove(existing)
                    results.add(ReachablePath(finalTo, currentSteps.toList()))
                }
            }

            if (simRemainingDice.isEmpty() || currFrom >= 24) return

            val distinctDice = simRemainingDice.distinct()
            for (dice in distinctDice) {
                val curTakes = simHeadTakes[player] ?: 0
                if (isSingleStepLegal(player, currFrom, dice, simBoard, simBar, curTakes, isFirst)) {
                    val toRel = if (currFrom == -1) dice - 1 else currFrom + dice
                    val absFrom = if (currFrom == -1) -1 else relativeToAbsolute(player, currFrom)
                    val absTo = if (toRel < 24) relativeToAbsolute(player, toRel) else -1

                    val prevBoardFrom = if (absFrom != -1) simBoard[absFrom] else null
                    val prevBoardTo = if (absTo != -1) simBoard[absTo] else null
                    val prevBarPlayer = simBar[player] ?: 0
                    val prevBarOpp = simBar[player.other()] ?: 0

                    if (currFrom == -1) {
                        simBar[player] = prevBarPlayer - 1
                    } else {
                        simBoard[absFrom] = CheckerStack(prevBoardFrom!!.count - 1, if (prevBoardFrom.count - 1 == 0) null else player)
                    }

                    if (toRel < 24) {
                        if (gameMode == GameMode.SHORT && prevBoardTo!!.player != null && prevBoardTo.player != player) {
                            simBar[player.other()] = prevBarOpp + 1
                            simBoard[absTo] = CheckerStack(1, player)
                        } else {
                            simBoard[absTo] = CheckerStack(prevBoardTo!!.count + 1, player)
                        }
                    }

                    simRemainingDice.remove(dice)
                    if (currFrom == 0) {
                        simHeadTakes[player] = curTakes + 1
                    }
                    currentSteps.add(Move(currFrom, toRel, dice))

                    search(toRel)

                    // Backtrack
                    currentSteps.removeAt(currentSteps.size - 1)
                    if (currFrom == -1) {
                        simBar[player] = prevBarPlayer
                    } else {
                        simBoard[absFrom] = prevBoardFrom!!
                    }
                    if (toRel < 24) {
                        simBoard[absTo] = prevBoardTo!!
                        simBar[player.other()] = prevBarOpp
                    }
                    simRemainingDice.add(dice)
                    simHeadTakes[player] = curTakes
                }
            }
        }

        search(fromRel)
        return results
    }

    /**
     * GROK-G07: Linear 6-block check on simulated board.
     * Verifies consecutive 6 points along player's path and ensures opponent has passed the fence.
     */
    fun willBuildIllegalSixBlockSim(player: Player, fromRel: Int, toRel: Int, currentBoard: List<CheckerStack>): Boolean {
        if (toRel >= 24) return false
        if (gameMode == GameMode.SHORT) return false

        // Fast-path: A 6-block is impossible if player has fewer than 6 checkers in play outside borne-off
        var totalPlayerPieces = 0
        for (i in 0 until 24) {
            val s = currentBoard[i]
            if (s.player == player && s.count > 0) {
                totalPlayerPieces += s.count
            }
        }
        if (totalPlayerPieces < 6) return false

        val absFrom = if (fromRel != -1) relativeToAbsolute(player, fromRel) else -1
        val minStart = maxOf(0, toRel - 5)
        val maxStart = minOf(18, toRel)

        var hasFenceOfSix = false
        var fenceStartRel = -1

        for (startRel in minStart..maxStart) {
            var allOccupied = true
            for (offset in 0 until 6) {
                val r = startRel + offset
                val isOccupied = if (r == toRel) {
                    true
                } else if (r == fromRel) {
                    val count = currentBoard[absFrom].count - 1
                    count > 0 && currentBoard[absFrom].player == player
                } else {
                    val absIdx = relativeToAbsolute(player, r)
                    val stack = currentBoard[absIdx]
                    stack.player == player && stack.count > 0
                }
                if (!isOccupied) {
                    allOccupied = false
                    break
                }
            }
            if (allOccupied) {
                hasFenceOfSix = true
                fenceStartRel = startRel
                break
            }
        }

        if (!hasFenceOfSix) return false

        val opponent = player.other()
        if ((borneOffCount[opponent] ?: 0) > 0) return false

        // A 6-block is legal if at least one opponent checker is already past the fence (ahead toward opponent's finish)
        val fenceAbsPoints = (0 until 6).map { offset -> relativeToAbsolute(player, fenceStartRel + offset) }.toSet()

        var hasOpponentPassedFence = false
        for (i in 0 until 24) {
            val count = if (i == absFrom && currentBoard[i].player == opponent) {
                currentBoard[i].count - 1
            } else {
                currentBoard[i].count
            }
            if (currentBoard[i].player == opponent && count > 0) {
                val oppRel = absoluteToRelative(opponent, i)
                var pathIntersectsFence = false
                for (step in oppRel..23) {
                    val absStep = relativeToAbsolute(opponent, step)
                    if (fenceAbsPoints.contains(absStep)) {
                        pathIntersectsFence = true
                        break
                    }
                }
                if (!pathIntersectsFence) {
                    hasOpponentPassedFence = true
                    break
                }
            }
        }

        if (hasOpponentPassedFence) {
            return false
        }

        return true
    }

    fun willBuildIllegalSixBlock(player: Player, fromRel: Int, toRel: Int): Boolean {
        return willBuildIllegalSixBlockSim(player, fromRel, toRel, this.board)
    }

    // Bot Move selection (Heuristics)
    fun selectBestBotMove(): Move? {
        var legals = getAllLegalMoves(activePlayer, remainingDice)
        if (legals.isEmpty() && remainingDice.isNotEmpty()) {
            val fallbackMoves = mutableListOf<Move>()
            val candidateFroms = if (gameMode == GameMode.SHORT && (bar[activePlayer] ?: 0) > 0) {
                listOf(-1)
            } else {
                (0..23).filter { r ->
                    val abs = relativeToAbsolute(activePlayer, r)
                    board[abs].player == activePlayer && board[abs].count > 0
                }
            }
            val curTakes = headTakesThisTurnMap[activePlayer] ?: 0
            val isFirst = isFirstTurnMap[activePlayer] ?: false
            for (d in remainingDice.distinct()) {
                for (from in candidateFroms) {
                    if (isSingleStepLegal(activePlayer, from, d, board, bar, curTakes, isFirst)) {
                        val toRel = if (from == -1) d - 1 else from + d
                        fallbackMoves.add(Move(from, toRel, d))
                    }
                }
            }
            legals = fallbackMoves
        }
        if (legals.isEmpty()) return null

        if (gameMode == GameMode.SHORT) {
            val fromBar = legals.filter { it.from == -1 }
            if (fromBar.isNotEmpty()) {
                val hitMove = fromBar.find {
                    val abs = relativeToAbsolute(activePlayer, it.to)
                    board[abs].player == activePlayer.other() && board[abs].count == 1
                }
                return hitMove ?: fromBar.first()
            }

            val bearOffMoves = legals.filter { it.to >= 24 }
            if (bearOffMoves.isNotEmpty()) {
                return bearOffMoves.maxByOrNull { it.from }
            }

            val hitMoves = legals.filter {
                if (it.to < 24) {
                    val abs = relativeToAbsolute(activePlayer, it.to)
                    board[abs].player == activePlayer.other() && board[abs].count == 1
                } else false
            }
            if (hitMoves.isNotEmpty()) {
                return hitMoves.first()
            }

            val anchorMoves = legals.filter {
                if (it.to < 24) {
                    val abs = relativeToAbsolute(activePlayer, it.to)
                    board[abs].player == activePlayer && board[abs].count == 1
                } else false
            }
            if (anchorMoves.isNotEmpty()) {
                return anchorMoves.first()
            }

            val furthestCheckers = legals.filter { it.from < 12 }
            if (furthestCheckers.isNotEmpty()) {
                return furthestCheckers.minByOrNull { it.from } ?: furthestCheckers.first()
            }

            return legals.maxByOrNull { it.to } ?: legals.first()
        }

        // Long / Mad mode heuristics:
        val bearOffMoves = legals.filter { it.to >= 24 }
        if (bearOffMoves.isNotEmpty()) {
            return bearOffMoves.maxByOrNull { it.from }
        }

        val headMoves = legals.filter { it.from == 0 }
        if (headMoves.isNotEmpty() && board[relativeToAbsolute(activePlayer, 0)].count > 5) {
            return headMoves.first()
        }

        val enterHomeMoves = legals.filter { it.from < 18 && it.to >= 18 }
        if (enterHomeMoves.isNotEmpty()) {
            return enterHomeMoves.maxByOrNull { it.to }
        }

        val fromClogged = legals.sortedByDescending { board[relativeToAbsolute(activePlayer, it.from)].count }
        if (fromClogged.isNotEmpty()) {
            val furthestCheckers = legals.filter { it.from < 12 }
            if (furthestCheckers.isNotEmpty()) {
                return furthestCheckers.minByOrNull { it.from } ?: furthestCheckers.first()
            }
            return fromClogged.first()
        }

        return legals.maxByOrNull { it.to } ?: legals.first()
    }

    fun createSnapshot(): EngineSnapshot {
        return EngineSnapshot(
            board = board.map { it.copy() },
            remainingDice = remainingDice.toList(),
            doubleWaterfallQueue = doubleWaterfallQueue.map { it.toList() },
            headTakesThisTurnMap = headTakesThisTurnMap.toMap(),
            isOpponentPlayingTransferred = isOpponentPlayingTransferred,
            isFreshKushRoll = isFreshKushRoll,
            borneOffCount = borneOffCount.toMap(),
            bar = bar.toMap(),
            activePlayer = activePlayer,
            roller = roller,
            gameStatus = gameStatus,
            winner = winner,
            isFirstTurnMap = isFirstTurnMap.toMap(),
            stakeMultiplier = stakeMultiplier,
            doublingOwner = doublingOwner,
            gameEndResult = gameEndResult
        )
    }

    fun restoreSnapshot(snapshot: EngineSnapshot) {
        board = snapshot.board.map { it.copy() }.toMutableList()
        remainingDice.clear()
        remainingDice.addAll(snapshot.remainingDice)
        doubleWaterfallQueue.clear()
        doubleWaterfallQueue.addAll(snapshot.doubleWaterfallQueue.map { it.toList() })
        headTakesThisTurnMap.clear()
        headTakesThisTurnMap.putAll(snapshot.headTakesThisTurnMap)
        isOpponentPlayingTransferred = snapshot.isOpponentPlayingTransferred
        isFreshKushRoll = snapshot.isFreshKushRoll
        borneOffCount.clear()
        borneOffCount.putAll(snapshot.borneOffCount)
        bar.clear()
        bar.putAll(snapshot.bar)
        activePlayer = snapshot.activePlayer
        roller = snapshot.roller
        gameStatus = snapshot.gameStatus
        winner = snapshot.winner
        isFirstTurnMap.clear()
        isFirstTurnMap.putAll(snapshot.isFirstTurnMap)
        stakeMultiplier = snapshot.stakeMultiplier
        doublingOwner = snapshot.doublingOwner
        gameEndResult = snapshot.gameEndResult
        addLogEntry("↩️ Отмена хода: состояние позиции возвращено.")
    }
}
