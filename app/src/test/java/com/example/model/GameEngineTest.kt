package com.example.model

import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

/**
 * Pure JVM Unit Tests for GameEngine (Auditor Acceptance Suite GROK-G01 ... GROK-G14).
 * Runs completely independent of Android runtime and Robolectric.
 */
class GameEngineTest {

    private lateinit var engine: GameEngine

    @Before
    fun setUp() {
        engine = GameEngine()
    }

    @Test
    fun testLongInitialBoardSetup() {
        engine.resetGame(GameMode.LONG)
        assertEquals(GameMode.LONG, engine.gameMode)

        // White head at absolute 23, Black head at absolute 11
        assertEquals(15, engine.board[23].count)
        assertEquals(Player.WHITE, engine.board[23].player)

        assertEquals(15, engine.board[11].count)
        assertEquals(Player.BLACK, engine.board[11].player)

        assertEquals(0, engine.bar[Player.WHITE])
        assertEquals(0, engine.bar[Player.BLACK])
    }

    @Test
    fun testShortInitialBoardSetup() {
        engine.resetGame(GameMode.SHORT)
        assertEquals(GameMode.SHORT, engine.gameMode)

        // White starting points (abs 23: 2, abs 12: 5, abs 7: 3, abs 5: 5)
        assertEquals(2, engine.board[23].count)
        assertEquals(Player.WHITE, engine.board[23].player)
        assertEquals(5, engine.board[12].count)
        assertEquals(Player.WHITE, engine.board[12].player)
        assertEquals(3, engine.board[7].count)
        assertEquals(Player.WHITE, engine.board[7].player)
        assertEquals(5, engine.board[5].count)
        assertEquals(Player.WHITE, engine.board[5].player)

        // Total white checkers = 15
        val whiteTotal = engine.board.filter { it.player == Player.WHITE }.sumOf { it.count }
        assertEquals(15, whiteTotal)

        // Total black checkers = 15
        val blackTotal = engine.board.filter { it.player == Player.BLACK }.sumOf { it.count }
        assertEquals(15, blackTotal)
    }

    @Test
    fun testGrokG01_LongFirstTurnDouble6AllowsTwoFromHead() {
        engine.resetGame(GameMode.LONG)
        engine.startDirectMatch(Player.WHITE, firstMover = Player.WHITE)

        // First turn of the game in LONG mode
        engine.roll(6, 6)
        assertEquals(4, engine.remainingDice.size)

        // First move from head (from = 0) with die 6
        assertTrue("First checker from head must be legal", engine.isMoveLegal(Player.WHITE, 0, 6))
        val firstMoveOk = engine.makeMove(Move(from = 0, to = 6, diceUsed = 6))
        assertTrue(firstMoveOk)
        assertEquals(1, engine.board[engine.relativeToAbsolute(Player.WHITE, 6)].count)

        // GROK-G01: Second checker from head on 6:6 on first turn MUST BE LEGAL!
        assertTrue("Second checker from head on 6:6 first turn must be legal", engine.isMoveLegal(Player.WHITE, 0, 6))
        val secondMoveOk = engine.makeMove(Move(from = 0, to = 6, diceUsed = 6))
        assertTrue(secondMoveOk)

        // 2 checkers have successfully been moved to point 6 from head!
        assertEquals(2, engine.board[engine.relativeToAbsolute(Player.WHITE, 6)].count)
        assertEquals(13, engine.board[engine.relativeToAbsolute(Player.WHITE, 0)].count)
        // Since point 12 is Black's head (occupied by 15 checkers), the remaining 2 sixes cannot be played,
        // so turn correctly and legally advances to Black!
        assertEquals(Player.BLACK, engine.activePlayer)
    }

    @Test
    fun testGrokG01_LongFirstTurnDouble2AllowsOnlyOneFromHead() {
        engine.resetGame(GameMode.LONG)
        engine.startDirectMatch(Player.WHITE, firstMover = Player.WHITE)

        // First turn of the game in LONG mode with 2:2
        engine.roll(2, 2)

        // First move from head
        assertTrue("First checker from head must be legal", engine.isMoveLegal(Player.WHITE, 0, 2))
        engine.makeMove(Move(from = 0, to = 2, diceUsed = 2))
        assertEquals(1, engine.headTakesThisTurnMap[Player.WHITE])

        // Second checker from head on 2:2 MUST BE ILLEGAL (only 3:3, 4:4, 6:6 allow 2)
        assertFalse("Second checker from head on 2:2 must be illegal", engine.isMoveLegal(Player.WHITE, 0, 2))
    }

    @Test
    fun testGrokG03_LotWinnerStartsBeforeRoll() {
        engine.resetGame(GameMode.MAD)
        assertEquals(GameStatus.LOT_STAGE1, engine.gameStatus)

        // Win stage 1 as White
        engine.rollLotStage1Custom(userVal = 5, botVal = 2)
        assertEquals(Player.WHITE, engine.humanPlayerColor)

        engine.transitionToStage2()
        assertEquals(GameStatus.LOT_STAGE2, engine.gameStatus)

        // White wins stage 2 (5 vs 3)
        engine.rollLotStage2Custom(whiteVal = 5, blackVal = 3)
        assertEquals(Player.WHITE, engine.roller)

        // GROK-G03: Applying winner must transition to BEFORE_ROLL, NOT PLAYER_MOVE with lot dice!
        engine.applyLotStage2WinnerAndStart()
        assertEquals(GameStatus.BEFORE_ROLL, engine.gameStatus)
        assertTrue("Remaining dice must be empty before roll", engine.remainingDice.isEmpty())
        assertEquals(Player.WHITE, engine.roller)
    }

    @Test
    fun testGrokG04_LotLabelsAreAccurateForBlack() {
        engine.resetGame(GameMode.MAD)

        // Bot wins stage 1 -> Bot rolls first
        engine.rollLotStage1Custom(userVal = 1, botVal = 6)
        assertEquals(Player.BLACK, engine.roller)

        engine.transitionToStage2()
        engine.rollLotStage2Custom(whiteVal = 2, blackVal = 4)

        // Black wins lot 2 -> Active player should be Black
        assertEquals(Player.BLACK, engine.roller)
    }

    @Test
    fun testGrokG06_FindReachablePathsFromDoesNotMutateBoard() {
        engine.resetGame(GameMode.SHORT)
        engine.startDirectMatch(Player.WHITE, firstMover = Player.WHITE)
        engine.roll(3, 5)

        val boardSnapshotBefore = engine.board.map { it.copy() }
        val remainingDiceBefore = engine.remainingDice.toList()
        val barBefore = engine.bar.toMap()

        // Call findReachablePathsFrom
        val paths = engine.findReachablePathsFrom(Player.WHITE, 12)
        assertNotNull(paths)

        // GROK-G06: Live state must be 100% identical!
        assertEquals(boardSnapshotBefore, engine.board)
        assertEquals(remainingDiceBefore, engine.remainingDice)
        assertEquals(barBefore, engine.bar)
    }

    @Test
    fun testGrokG11_BorneOff15ResultsInGameOver() {
        engine.resetGame(GameMode.MAD)
        engine.startDirectMatch(Player.WHITE, firstMover = Player.WHITE)

        // Place 1 checker at relative 23 (point 24)
        for (i in 0 until 24) {
            engine.board[i] = CheckerStack(0, null)
        }
        val abs23 = engine.relativeToAbsolute(Player.WHITE, 23)
        engine.board[abs23] = CheckerStack(1, Player.WHITE)
        engine.borneOffCount[Player.WHITE] = 14

        // Roll 1:2
        engine.roll(1, 2)
        val move = Move(from = 23, to = 24, diceUsed = 1)
        assertTrue(engine.isMoveLegal(Player.WHITE, 23, 1))

        val moved = engine.makeMove(move)
        assertTrue(moved)
        assertEquals(15, engine.borneOffCount[Player.WHITE])
        assertEquals(GameStatus.GAME_OVER, engine.gameStatus)
        assertEquals(Player.WHITE, engine.winner)
    }

    @Test
    fun testGrokG12_ShortBarMustEnterFirst() {
        engine.resetGame(GameMode.SHORT)
        engine.startDirectMatch(Player.WHITE, firstMover = Player.WHITE)

        // Put 1 white checker on the bar
        engine.bar[Player.WHITE] = 1
        engine.roll(2, 4)

        // Normal moves on board must be ILLEGAL when bar > 0
        assertFalse(engine.isMoveLegal(Player.WHITE, 23, 2))
        assertFalse(engine.isMoveLegal(Player.WHITE, 12, 4))

        // Move from bar (from = -1) must be legal
        val legalMoves = engine.getAllLegalMoves(Player.WHITE, engine.remainingDice)
        assertTrue(legalMoves.all { it.from == -1 })
    }

    @Test
    fun testEngineSnapshotAndRestore() {
        engine.resetGame(GameMode.MAD)
        engine.startDirectMatch(Player.WHITE, firstMover = Player.WHITE)
        engine.roll(3, 4)

        val snapshot = engine.createSnapshot()

        // Make a move
        val legal = engine.getAllLegalMoves(Player.WHITE, engine.remainingDice).first()
        engine.makeMove(legal)

        // Restore
        engine.restoreSnapshot(snapshot)

        assertEquals(snapshot.board, engine.board)
        assertEquals(snapshot.remainingDice, engine.remainingDice)
        assertEquals(snapshot.activePlayer, engine.activePlayer)
        assertEquals(snapshot.gameStatus, engine.gameStatus)
    }

    @Test
    fun testUndoConsensus_BothMustAgree() {
        // Condition: If at least one player disallows undo, undo is disabled!
        engine.allowUndoPlayer1 = true
        engine.allowUndoPlayer2 = true
        assertTrue("Both players agree -> undo allowed", engine.isUndoAllowed)

        engine.allowUndoPlayer1 = false
        engine.allowUndoPlayer2 = true
        assertFalse("Player 1 disallows -> undo disabled", engine.isUndoAllowed)

        engine.allowUndoPlayer1 = true
        engine.allowUndoPlayer2 = false
        assertFalse("Player 2 disallows -> undo disabled", engine.isUndoAllowed)

        engine.allowUndoPlayer1 = false
        engine.allowUndoPlayer2 = false
        assertFalse("Both disallow -> undo disabled", engine.isUndoAllowed)
    }

    @Test
    fun testDoublingCube_AcceptAndRedouble() {
        engine.resetGame(GameMode.LONG)
        engine.startDirectMatch(Player.WHITE, firstMover = Player.WHITE)
        assertEquals(1, engine.stakeMultiplier)
        assertNull(engine.doublingOwner)

        // White proposes doubling to x2
        assertTrue(engine.canProposeDoubling(Player.WHITE))
        assertTrue(engine.proposeDoubling(Player.WHITE))
        assertTrue(engine.isDoublingOffered)
        assertEquals(Player.WHITE, engine.doublingProposer)

        // Black accepts doubling
        assertTrue(engine.acceptDoubling())
        assertEquals(2, engine.stakeMultiplier)
        assertEquals(Player.BLACK, engine.doublingOwner)
        assertFalse(engine.isDoublingOffered)

        // White cannot redouble because Black owns the cube!
        assertFalse("White cannot redouble because Black owns cube", engine.canProposeDoubling(Player.WHITE))

        // Black CAN redouble to x4!
        assertTrue(engine.canProposeDoubling(Player.BLACK))
        assertTrue(engine.proposeDoubling(Player.BLACK))
        assertTrue(engine.acceptDoubling())
        assertEquals(4, engine.stakeMultiplier)
        assertEquals(Player.WHITE, engine.doublingOwner)
    }

    @Test
    fun testDoublingCube_DeclineResultsInSurrender() {
        engine.resetGame(GameMode.LONG)
        engine.startDirectMatch(Player.WHITE, firstMover = Player.WHITE)
        engine.proposeDoubling(Player.WHITE)

        // Black declines doubling -> White wins with 1 * stakeMultiplier (1 pt)
        assertTrue(engine.declineDoubling())
        assertEquals(GameStatus.GAME_OVER, engine.gameStatus)
        assertEquals(Player.WHITE, engine.winner)
        val result = engine.gameEndResult
        assertNotNull(result)
        assertEquals(Player.WHITE, result!!.winner)
        assertEquals(Player.BLACK, result.loser)
        assertEquals(GameOutcomeType.RESIGNATION, result.outcomeType)
        assertEquals(1, result.totalPoints)
    }

    @Test
    fun testGameOutcome_SimpleGame_OnePoint() {
        engine.resetGame(GameMode.LONG)
        engine.startDirectMatch(Player.WHITE, firstMover = Player.WHITE)

        // White borne off 15, Black has borne off 2 checkers
        engine.borneOffCount[Player.WHITE] = 15
        engine.borneOffCount[Player.BLACK] = 2

        val result = engine.calculateGameOutcome(Player.WHITE)
        assertEquals(GameOutcomeType.SIMPLE, result.outcomeType)
        assertEquals(1, result.basePoints)
        assertEquals(1, result.totalPoints)
    }

    @Test
    fun testGameOutcome_HomeMars_FourPoints() {
        engine.resetGame(GameMode.LONG)
        engine.startDirectMatch(Player.WHITE, firstMover = Player.WHITE)

        // White wins. Loser (Black) has borne off 0 checkers,
        // and has brought ALL 15 checkers into the final home (rel 18..23), but none borne off yet!
        engine.borneOffCount[Player.WHITE] = 15
        engine.borneOffCount[Player.BLACK] = 0

        // Clear board
        for (i in 0 until 24) {
            engine.board[i] = CheckerStack(0, null)
        }
        // Place all 15 black checkers inside final home (r in 18..23)
        // For example: 3 checkers each on points 18, 19, 20, 21, 22
        for (r in 18..22) {
            val abs = engine.relativeToAbsolute(Player.BLACK, r)
            engine.board[abs] = CheckerStack(3, Player.BLACK)
        }

        val result = engine.calculateGameOutcome(Player.WHITE)
        assertEquals("Home Mars: All 15 checkers in final home but none borne off", GameOutcomeType.HOME_MARS, result.outcomeType)
        assertEquals(4, result.basePoints)
        assertEquals(4, result.totalPoints)
    }

    @Test
    fun testGameOutcome_Coke_ThreePoints() {
        engine.resetGame(GameMode.LONG)
        engine.startDirectMatch(Player.WHITE, firstMover = Player.WHITE)

        // White wins. Loser (Black) has borne off 0 checkers,
        // and has at least one checker still in the initial home (rel 0..5)!
        engine.borneOffCount[Player.WHITE] = 15
        engine.borneOffCount[Player.BLACK] = 0

        // Clear board
        for (i in 0 until 24) {
            engine.board[i] = CheckerStack(0, null)
        }
        // 1 checker in initial home (e.g. rel 0 / head), 14 checkers in final home
        val absHead = engine.relativeToAbsolute(Player.BLACK, 0)
        engine.board[absHead] = CheckerStack(1, Player.BLACK)
        val absHome = engine.relativeToAbsolute(Player.BLACK, 20)
        engine.board[absHome] = CheckerStack(14, Player.BLACK)

        val result = engine.calculateGameOutcome(Player.WHITE)
        assertEquals("Coke: Checker remaining in initial home", GameOutcomeType.COKE, result.outcomeType)
        assertEquals(3, result.basePoints)
        assertEquals(3, result.totalPoints)
    }

    @Test
    fun testGameOutcome_Mars_TwoPoints() {
        engine.resetGame(GameMode.LONG)
        engine.startDirectMatch(Player.WHITE, firstMover = Player.WHITE)

        // White wins. Loser (Black) has borne off 0 checkers,
        // all checkers left initial home (rel 0..5 count == 0),
        // but not all entered final home (some in middle rel 6..17)!
        engine.borneOffCount[Player.WHITE] = 15
        engine.borneOffCount[Player.BLACK] = 0

        // Clear board
        for (i in 0 until 24) {
            engine.board[i] = CheckerStack(0, null)
        }
        // 0 in initial home (0..5), 5 checkers on point 10 (middle), 10 in final home (20)
        val absMid = engine.relativeToAbsolute(Player.BLACK, 10)
        engine.board[absMid] = CheckerStack(5, Player.BLACK)
        val absHome = engine.relativeToAbsolute(Player.BLACK, 20)
        engine.board[absHome] = CheckerStack(10, Player.BLACK)

        val result = engine.calculateGameOutcome(Player.WHITE)
        assertEquals("Mars: Exited initial home but not all in final home", GameOutcomeType.MARS, result.outcomeType)
        assertEquals(2, result.basePoints)
        assertEquals(2, result.totalPoints)
    }

    @Test
    fun testGameOutcome_WithStakeMultiplier() {
        engine.resetGame(GameMode.LONG)
        engine.startDirectMatch(Player.WHITE, firstMover = Player.WHITE)

        // Propose and accept doubling to x2, then redouble to x4
        engine.proposeDoubling(Player.WHITE)
        engine.acceptDoubling() // stake = 2
        engine.proposeDoubling(Player.BLACK)
        engine.acceptDoubling() // stake = 4
        assertEquals(4, engine.stakeMultiplier)

        // Home Mars situation with stake x4
        engine.borneOffCount[Player.WHITE] = 15
        engine.borneOffCount[Player.BLACK] = 0
        for (i in 0 until 24) {
            engine.board[i] = CheckerStack(0, null)
        }
        val absHome = engine.relativeToAbsolute(Player.BLACK, 20)
        engine.board[absHome] = CheckerStack(15, Player.BLACK)

        val result = engine.calculateGameOutcome(Player.WHITE)
        assertEquals(GameOutcomeType.HOME_MARS, result.outcomeType)
        assertEquals(4, result.basePoints)
        assertEquals(4, result.stakeMultiplier)
        assertEquals(16, result.totalPoints) // 4 base * 4 stake = 16 points!
    }

    @Test
    fun testP1_05_MoveToMustMatchFromPlusDice() {
        engine.resetGame(GameMode.LONG)
        engine.startDirectMatch(Player.WHITE, firstMover = Player.WHITE)
        engine.roll(3, 5)

        // Legal move from head (0) with die 3 should land on 3
        val malformedMove = Move(from = 0, to = 7, diceUsed = 3) // Malicious / invalid to!
        assertFalse("Move with mismatched to must be rejected by isMoveLegal", engine.isMoveLegal(Player.WHITE, malformedMove))
        assertFalse("Move with mismatched to must be rejected by makeMove", engine.makeMove(malformedMove))

        // Correct move
        val validMove = Move(from = 0, to = 3, diceUsed = 3)
        assertTrue("Move with matching to must be accepted by isMoveLegal", engine.isMoveLegal(Player.WHITE, validMove))
        assertTrue("Move with matching to must succeed in makeMove", engine.makeMove(validMove))
    }

    @Test
    fun testP1_13_DoublingCubeCappedAt64() {
        engine.resetGame(GameMode.LONG)
        engine.startDirectMatch(Player.WHITE, firstMover = Player.WHITE)

        // Double sequentially: 2, 4, 8, 16, 32, 64
        var proposer = Player.WHITE
        while (engine.stakeMultiplier < 64) {
            assertTrue("Should allow doubling before 64", engine.canProposeDoubling(proposer))
            engine.proposeDoubling(proposer)
            engine.acceptDoubling()
            proposer = proposer.other()
        }

        assertEquals(64, engine.stakeMultiplier)
        assertFalse("Should forbid doubling beyond 64", engine.canProposeDoubling(proposer))
        assertFalse("Proposing doubling when stake is 64 must fail", engine.proposeDoubling(proposer))
    }

    @Test
    fun testLanguageParamIndependence() {
        assertEquals("Белые", Player.WHITE.label("RU"))
        assertEquals("Черные", Player.BLACK.label("RU"))
        assertEquals("White", Player.WHITE.label("EN"))
        assertEquals("Black", Player.BLACK.label("EN"))
    }

    @Test
    fun testSelectBestBotMove_IsDeterministic() {
        engine.resetGame(GameMode.LONG)
        engine.startDirectMatch(Player.BLACK, firstMover = Player.BLACK)
        engine.roll(4, 2)

        val move1 = engine.selectBestBotMove()
        assertNotNull(move1)

        // Reset and repeat with same dice
        engine.resetGame(GameMode.LONG)
        engine.startDirectMatch(Player.BLACK, firstMover = Player.BLACK)
        engine.roll(4, 2)

        val move2 = engine.selectBestBotMove()
        assertEquals(move1, move2)
    }
}
