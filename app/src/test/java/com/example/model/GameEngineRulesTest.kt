package com.example.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class GameEngineRulesTest {

    private lateinit var engine: GameEngine

    @Before
    fun setUp() {
        engine = GameEngine()
    }

    @Test
    fun testLongBackgammonFirstTurnKushTakesTwoFromHead() {
        engine.gameMode = GameMode.LONG
        engine.resetGame()
        // Skip lottery directly into BEFORE_ROLL for WHITE
        engine.startDirectMatch(localColor = Player.WHITE, firstMover = Player.WHITE)
        assertEquals(GameStatus.BEFORE_ROLL, engine.gameStatus)
        assertEquals(Player.WHITE, engine.roller)

        // Roll kush 3:3 on first turn
        engine.roll(3, 3)
        assertEquals(GameStatus.PLAYER_MOVE, engine.gameStatus)
        assertEquals(listOf(3, 3, 3, 3), engine.remainingDice)

        // First move from head (0 -> 3)
        val legalMoves1 = engine.getAllLegalMoves(Player.WHITE, engine.remainingDice)
        val headMove1 = legalMoves1.firstOrNull { it.from == 0 && it.diceUsed == 3 }
        assertTrue("First checker from head must be legal on first turn with 3:3", headMove1 != null)

        val success1 = engine.makeMove(headMove1!!)
        assertTrue("Move 1 succeeded", success1)
        assertEquals(1, engine.headTakesThisTurnMap[Player.WHITE])

        // Second move from head should ALSO be legal because it's first turn with kush 3:3!
        val legalMoves2 = engine.getAllLegalMoves(Player.WHITE, engine.remainingDice)
        val headMove2 = legalMoves2.firstOrNull { it.from == 0 && it.diceUsed == 3 }
        assertTrue("Second checker from head must be legal on first turn with 3:3 in Long Backgammon", headMove2 != null)

        val success2 = engine.makeMove(headMove2!!)
        assertTrue("Move 2 succeeded", success2)
        assertEquals(2, engine.headTakesThisTurnMap[Player.WHITE])

        // Third move from head should be FORBIDDEN (max 2 checkers)
        val legalMoves3 = engine.getAllLegalMoves(Player.WHITE, engine.remainingDice)
        val headMove3 = legalMoves3.firstOrNull { it.from == 0 }
        assertTrue("Third checker from head must be blocked", headMove3 == null)
    }

    @Test
    fun testLongBackgammonNonKushTakesOnlyOneFromHead() {
        engine.gameMode = GameMode.LONG
        engine.resetGame()
        engine.startDirectMatch(localColor = Player.WHITE, firstMover = Player.WHITE)

        // Roll non-kush (e.g. 5:2) on first turn
        engine.roll(5, 2)
        assertEquals(listOf(5, 2), engine.remainingDice)

        // First move from head
        val legalMoves1 = engine.getAllLegalMoves(Player.WHITE, engine.remainingDice)
        val headMove1 = legalMoves1.first { it.from == 0 && it.diceUsed == 5 }
        engine.makeMove(headMove1)
        assertEquals(1, engine.headTakesThisTurnMap[Player.WHITE])

        // Second move from head should NOT be allowed on non-kush!
        val legalMoves2 = engine.getAllLegalMoves(Player.WHITE, engine.remainingDice)
        val headMove2 = legalMoves2.firstOrNull { it.from == 0 }
        assertTrue("Second checker from head must be blocked on non-kush", headMove2 == null)
    }

    @Test
    fun testMadBackgammonOnlyOneCheckerFromHeadEvenOnKush() {
        engine.gameMode = GameMode.MAD
        engine.resetGame()
        engine.startDirectMatch(localColor = Player.WHITE, firstMover = Player.WHITE)

        // Roll kush 4:4 in Mad mode
        engine.roll(4, 4)
        assertEquals(Player.WHITE, engine.activePlayer)

        // Take 1 from head
        val legals1 = engine.getAllLegalMoves(Player.WHITE, engine.remainingDice)
        val headMove1 = legals1.first { it.from == 0 && it.diceUsed == 4 }
        engine.makeMove(headMove1)

        // Second checker from head must be blocked in Mad mode
        val legals2 = engine.getAllLegalMoves(Player.WHITE, engine.remainingDice)
        val headMove2 = legals2.firstOrNull { it.from == 0 }
        assertTrue("Mad mode strictly allows only 1 checker from head per turn", headMove2 == null)
    }

    @Test
    fun testShortBackgammonMovementTowardsHome() {
        engine.gameMode = GameMode.SHORT
        // White starts at rel 0 (point 24) and moves towards rel 23 (point 1)
        assertEquals(23, engine.relativeToAbsolute(Player.WHITE, 0))
        assertEquals(0, engine.relativeToAbsolute(Player.WHITE, 23))

        // Black starts at rel 0 (point 1) and moves towards rel 23 (point 24)
        assertEquals(0, engine.relativeToAbsolute(Player.BLACK, 0))
        assertEquals(23, engine.relativeToAbsolute(Player.BLACK, 23))

        // They move towards each other!
    }
}
