package com.pawplay.app.games.pawblocks

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

/**
 * QA's own adversarial checks of Paw Blocks' score, paws and ending (PRD stories 63-65 and 72), written from the
 * PRD text rather than from the developers' tests: the one-second wait, never under a held block, exactly once, one
 * paw per clear-out, clear-outs scoring nothing and never moving the ramp, and a fuzzed player who taps, holds,
 * drops wrongly and waits for as long as he likes.
 */
class BlocksGameOverQaTest {
    private val dot = BlockShapes.dot
    private fun shape(id: String) = BlockShapes.byId.getValue(id)

    /**
     * A 5x5 checkerboard: the empty cells are those where row + column is even, so no row or column is complete, no two empty
     * cells touch, and after a dot goes into one of them no bar of 2 can fit: the tray is stuck.
     */
    private fun almostStuckBoard() = Board.from(5) { r, c -> if ((r + c) % 2 == 0) null else Family.DOT }

    private fun tray() = listOf<BlockShape?>(dot, shape("bar2h"), shape("bar2v"))

    private fun newStuckSession(paws: Int, startClears: Int = 0, bestBefore: Int = 0): Pair<BlocksSession, Placed> {
        val s = BlocksSession(Random(5), startClears = startClears, startBoard = almostStuckBoard(), startTray = tray(), startPaws = paws, bestBefore = bestBefore)
        assertFalse("sanity: not stuck before the last move", s.isStuck)
        val p = s.place(0, 0, 0, 10_000)
        assertNotNull(p)
        assertFalse("the last move made no line", p!!.cleared)
        assertTrue("sanity: now nothing in the tray fits", s.isStuck)
        return s to p
    }

    // ------------------------------------------------------------------ the one-second wait

    @Test
    fun `with no paws the game ends one second after the last move, not a millisecond before, and only once`() {
        val (s, _) = newStuckSession(paws = 0)
        for (t in 10_000L..10_999L step 37) assertTrue("nothing at $t", s.tick(t).isEmpty())
        assertTrue(s.tick(10_999).isEmpty())
        assertEquals(BlocksPhase.PLAYING, s.phase)
        val ended = s.tick(11_000)
        assertEquals(listOf<SessionEvent>(SessionEvent.GameEnded(3)), ended)
        assertEquals(BlocksPhase.GAME_OVER, s.phase)
        // A child left it sitting there for an hour: it never ends a second time.
        var more = 0
        var t = 11_000L
        while (t < 11_000L + 3_600_000L) { t += 16; more += s.tick(t).size }
        assertEquals(0, more)
    }

    @Test
    fun `a block held for a long time keeps the game from ending, and the second starts again when he lets go`() {
        val (s, _) = newStuckSession(paws = 0)
        s.setHolding(true, 10_200) // he picks a block up (nothing can land)
        var t = 10_200L
        while (t < 40_000) { t += 100; assertTrue("no event under a held block at $t", s.tick(t).isEmpty()) }
        s.setHolding(false, 40_000)
        assertTrue(s.tick(40_999).isEmpty())
        assertEquals(1, s.tick(41_000).size)
        assertEquals(BlocksPhase.GAME_OVER, s.phase)
    }

    @Test
    fun `a wrong drop restarts the second, it does not let the game end under his finger`() {
        val (s, _) = newStuckSession(paws = 0)
        s.setHolding(true, 10_900)
        s.setHolding(false, 10_950) // dropped where it cannot go
        assertTrue("the old deadline of 11000 must not end the game", s.tick(11_000).isEmpty())
        assertTrue(s.tick(11_949).isEmpty())
        assertEquals(1, s.tick(11_950).size)
    }

    @Test
    fun `with a paw left the clear-out also waits one second and never runs under a held block`() {
        val (s, _) = newStuckSession(paws = 3)
        s.setHolding(true, 10_100)
        assertTrue(s.tick(30_000).isEmpty())
        assertEquals(3, s.paws)
        s.setHolding(false, 30_000)
        assertTrue(s.tick(30_999).isEmpty())
        val out = s.tick(31_000).single() as SessionEvent.ClearedOut
        assertEquals(2, out.pawsLeft)
        assertEquals(2, s.paws)
        assertEquals(BlocksPhase.PLAYING, s.phase)
    }

    // ------------------------------------------------------------------ what a clear-out does not do

    @Test
    fun `a clear-out scores nothing, takes nothing and does not move the ramp, even one clear short of the next stage`() {
        val (s, _) = newStuckSession(paws = 3, startClears = 2)
        assertEquals(1, s.stage)
        val before = s.score
        val events = (11_000L..14_000L step 50).flatMap { s.tick(it) }
        assertEquals(1, events.filterIsInstance<SessionEvent.ClearedOut>().size)
        assertEquals(2, s.clears)
        assertEquals(1, s.stage)
        assertEquals(before, s.score)
        assertEquals(5, s.board.size)
    }

    @Test
    fun `no paws left and not stuck does nothing at all (a game with room never ends)`() {
        val s = BlocksSession(Random(9), startPaws = 0)
        var t = 0L
        var n = 0
        while (t < 600_000) { t += 250; n += s.tick(t).size }
        assertEquals(0, n)
        assertEquals(BlocksPhase.PLAYING, s.phase)
    }

    @Test
    fun `once the game has ended nothing can be placed, held, or ticked into life`() {
        val (s, _) = newStuckSession(paws = 0)
        s.tick(11_000)
        val score = s.score
        val filled = s.board.filledCount
        assertNull(s.place(1, 3, 3, 12_000))
        assertNull(s.place(0, 1, 1, 12_000))
        assertFalse(s.canPlace(1, 1, 1))
        s.setHolding(true, 12_000)
        assertTrue(s.tick(99_999).isEmpty())
        assertFalse(s.hasPending)
        assertEquals(score, s.score)
        assertEquals(filled, s.board.filledCount)
    }

    // ------------------------------------------------------------------ isNewBest

    @Test
    fun `new best means strictly more than the best from before, and a first score above zero counts`() {
        val equal = BlocksSession(Random(1), bestBefore = 13, startScore = 13)
        assertFalse(equal.isNewBest)
        assertTrue(BlocksSession(Random(1), bestBefore = 13, startScore = 14).isNewBest)
        assertFalse("0 against 0 is not a new best", BlocksSession(Random(1), bestBefore = 0, startScore = 0).isNewBest)
        assertTrue("first ever game, first block", BlocksSession(Random(1), bestBefore = 0, startScore = 3).isNewBest)
    }

    @Test
    fun `the score stops at 999999 whatever is placed`() {
        val s = BlocksSession(Random(1), startBoard = Board.empty(5), startTray = tray(), startScore = 999_998)
        s.place(0, 0, 0, 1_000)
        assertEquals(999_999, s.score)
        s.place(1, 3, 0, 1_100)
        assertEquals(999_999, s.score)
        assertEquals(999_999, BlocksSession(Random(1), startScore = 5_000_000).score)
        assertEquals(0, BlocksSession(Random(1), startScore = -50).score)
    }

    // ------------------------------------------------------------------ the fuzzed child

    /**
     * A child who places blocks anywhere legal, holds blocks for a while, drops some wrongly, and sometimes puts the phone
     * down. Every rule of stories 63-65 and 72 is checked at every step.
     */
    private class Outcome(val ended: Boolean, val clearOuts: Int, val moves: Int)

    private fun fuzz(seed: Int): Outcome {
        val rnd = Random(seed)
        val s = BlocksSession(Random(seed * 13 + 1))
        var now = 0L
        var lastMove = Long.MIN_VALUE
        var holding = false
        var clearOuts = 0
        var endedCount = 0
        var moves = 0
        var lastPaws = s.paws
        var lastScore = s.score
        var lastClears = s.clears
        var iterations = 0

        fun check(events: List<SessionEvent>) {
            for (e in events) {
                when (e) {
                    is SessionEvent.ClearedOut -> {
                        clearOuts++
                        assertFalse("clear-out under a held block (seed $seed)", holding)
                        assertTrue("clear-out came ${now - lastMove}ms after the last move (seed $seed)", now - lastMove >= 1000)
                        assertEquals("one paw per clear-out (seed $seed)", lastPaws - 1, s.paws)
                        assertEquals(s.paws, e.pawsLeft)
                        lastPaws = s.paws
                        assertEquals("a clear-out never scores (seed $seed)", lastScore, s.score)
                        assertEquals("a clear-out never advances the ramp (seed $seed)", lastClears, s.clears)
                    }
                    is SessionEvent.GameEnded -> {
                        endedCount++
                        assertFalse("ended under a held block (seed $seed)", holding)
                        assertTrue("ended ${now - lastMove}ms after the last move (seed $seed)", now - lastMove >= 1000)
                        assertEquals("ended with paws left (seed $seed)", 0, s.paws)
                        assertTrue("ended though the tray was not stuck (seed $seed)", s.isStuck)
                        assertEquals(s.score, e.score)
                    }
                    is SessionEvent.Grew -> assertFalse("the board grew under a held block (seed $seed)", holding)
                    SessionEvent.Refilled -> Unit
                }
            }
            assertTrue("paws never come back (seed $seed)", s.paws <= lastPaws)
            assertTrue("the score never goes down (seed $seed)", s.score >= lastScore)
            assertTrue("clears only rise (seed $seed)", s.clears >= lastClears)
            assertTrue(s.score in 0..999_999)
            assertTrue(s.paws in 0..3)
        }

        while (s.phase == BlocksPhase.PLAYING && iterations++ < 6000) {
            now += when (rnd.nextInt(6)) { 0 -> 1_500L; 1 -> 1_000L; else -> 16L + rnd.nextInt(300) }
            val filledSlots = (0..2).filter { s.slot(it) != null }
            when (rnd.nextInt(5)) {
                0 -> check(s.tick(now).also { }) // idle: a frame goes by
                1 -> if (filledSlots.isNotEmpty()) { // he picks one up, holds it, lets go over nothing
                    s.setHolding(true, now); holding = true
                    repeat(1 + rnd.nextInt(4)) { now += 400L + rnd.nextInt(2_000); check(s.tick(now)) }
                    now += 10; s.setHolding(false, now); holding = false; lastMove = maxOf(lastMove, now)
                }
                else -> if (filledSlots.isNotEmpty()) { // he places a block anywhere it goes
                    val slot = filledSlots[rnd.nextInt(filledSlots.size)]
                    val spots = s.board.spots(s.slot(slot)!!)
                    s.setHolding(true, now); holding = true
                    now += 20 + rnd.nextInt(500); check(s.tick(now))
                    s.setHolding(false, now); holding = false; lastMove = maxOf(lastMove, now)
                    if (spots.isNotEmpty()) {
                        val spot = spots[rnd.nextInt(spots.size)]
                        val before = s.score
                        val placed = s.place(slot, spot.row, spot.col, now)
                        assertNotNull(placed)
                        moves++
                        lastMove = now
                        val lines = placed!!.rows.size + placed.cols.size
                        assertEquals("score step (seed $seed)", before + 3 + when { lines == 0 -> 0; lines == 1 -> 10; lines == 2 -> 30; lines == 3 -> 60; else -> 100 }, s.score)
                        lastScore = s.score
                        lastClears = s.clears
                    }
                }
            }
            check(s.tick(now))
        }
        assertEquals("a game ends exactly once or not at all (seed $seed)", if (s.phase == BlocksPhase.GAME_OVER) 1 else 0, endedCount)
        if (s.phase == BlocksPhase.GAME_OVER) {
            assertEquals("paws are spent before the end (seed $seed)", 3, clearOuts)
            // Long after: still exactly one ending, nothing else.
            for (k in 1..50) assertTrue(s.tick(now + k * 5_000L).isEmpty())
        }
        assertTrue("never more than 3 clear-outs (seed $seed)", clearOuts <= 3)
        return Outcome(s.phase == BlocksPhase.GAME_OVER, clearOuts, moves)
    }

    @Test
    fun `fuzzed children obey every rule of the score, the paws and the ending`() {
        val outcomes = (1..60).map { fuzz(it) }
        val ended = outcomes.count { it.ended }
        // A child who places blocks at random on a small board runs out of room; if nothing ever ended the fuzz proves little.
        assertTrue("only $ended of 60 random games ended, the fuzz is not reaching the ending", ended >= 10)
        assertTrue("some games used clear-outs", outcomes.any { it.clearOuts > 0 })
    }
}
