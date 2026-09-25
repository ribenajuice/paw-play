package com.pawplay.app.games.pawblocks

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

/** The session's timeline, refill rule, ramp and growth, clear-out, and a strategy-independent fuzz. */
class PawBlocksSessionTest {
    private val dot = BlockShapes.dot
    private fun shape(id: String) = BlockShapes.byId.getValue(id)

    private fun threeDots() = listOf<BlockShape?>(dot, dot, dot)

    /** Ticks in 50ms steps from [from] to [to] and gathers every event. */
    private fun run(s: BlocksSession, from: Long, to: Long): List<SessionEvent> {
        val out = ArrayList<SessionEvent>()
        var t = from
        while (t <= to) { out += s.tick(t); t += 50 }
        return out
    }

    // ------------------------------------------------------------------ first run

    @Test
    fun `a new session is an empty 5x5 board at stage 1 with three blocks the child can place`() {
        repeat(50) { seed ->
            val s = BlocksSession(Random(seed))
            assertEquals(5, s.board.size)
            assertEquals(0, s.board.filledCount)
            assertEquals(1, s.stage)
            assertEquals(0, s.clears)
            assertEquals(3, s.tray.size)
            assertTrue(s.tray.all { it != null && it in BlocksRamp.shapesFor(1) })
            assertTrue(s.tray.filterNotNull().any { s.board.hasSpot(it) })
            assertFalse(s.hasPending)
            assertTrue(s.tick(0).isEmpty())
        }
    }

    // ------------------------------------------------------------------ placing

    @Test
    fun `placing puts the block down, empties its slot and only that slot`() {
        val s = BlocksSession(Random(1), startTray = listOf(dot, shape("bar2h"), shape("bar2v")))
        val p = s.place(1, 2, 1, 1000)!!
        assertEquals(shape("bar2h"), p.shape)
        assertFalse(p.cleared)
        assertNull(p.critter)
        assertEquals(Family.BAR2, s.board.familyAt(2, 1))
        assertEquals(Family.BAR2, s.board.familyAt(2, 2))
        assertEquals(listOf(dot, null, shape("bar2v")), s.tray)
        assertEquals(0, s.clears)
    }

    @Test
    fun `a block cannot be placed twice or where it does not fit`() {
        val s = BlocksSession(Random(1), startTray = listOf(dot, dot, shape("bar2h")))
        assertNotNull(s.place(0, 0, 0, 0))
        assertNull("the slot is empty now", s.place(0, 1, 1, 10))
        assertNull("occupied", s.place(1, 0, 0, 20))
        assertNull("off the board", s.place(2, 0, 4, 20))
        assertNull("no such slot", s.place(3, 0, 0, 20))
        assertNull("no such slot", s.place(-1, 0, 0, 20))
        assertEquals(1, s.board.filledCount)
        assertEquals(listOf(null, dot, shape("bar2h")), s.tray)
    }

    @Test
    fun `a line clear counts once however many lines, and rotates the animal`() {
        fun board() = Board.from(5) { r, c -> if ((r == 0 || c == 0) && !(r == 0 && c == 0)) Family.BAR3 else null }
        val s = BlocksSession(Random(1), startBoard = board(), startTray = threeDots())
        val p = s.place(0, 0, 0, 0)!!
        assertEquals(listOf(0), p.rows)
        assertEquals(listOf(0), p.cols)
        assertEquals(1, s.clears)
        assertEquals(0, p.critter)
        assertEquals(0, s.board.filledCount)
        // Next clear: the next animal.
        val s2 = BlocksSession(Random(1), startClears = 1, startBoard = board(), startTray = threeDots())
        assertEquals(1, s2.place(0, 0, 0, 0)!!.critter)
    }

    // ------------------------------------------------------------------ the ramp

    @Test
    fun `the ramp advances by clears and the board grows once the animation is over, keeping placed cells`() {
        // Seven clears made: stage 2 on a 5x5 board. The eighth clear makes stage 3 (6x6).
        val board = Board.from(5) { r, c -> if (r == 0 && c != 0) Family.BAR3 else if (r == 3 && c == 3) Family.SQUARE else null }
        val s = BlocksSession(Random(3), startClears = 7, startBoard = board, startTray = threeDots())
        assertEquals(2, s.stage)
        val t0 = 10_000L
        val p = s.place(0, 0, 0, t0)!!
        assertTrue(p.cleared)
        assertEquals(8, s.clears)
        assertEquals(3, s.stage)
        assertEquals(5, s.board.size) // not yet: the line-clear animation is still playing
        assertTrue(s.tick(t0 + 500).isEmpty())
        assertTrue(s.tick(t0 + BlocksTiming.DROP_MS + BlocksTiming.LINE_CLEAR_MS - 1).isEmpty())
        assertEquals(5, s.board.size)
        val events = s.tick(t0 + BlocksTiming.DROP_MS + BlocksTiming.LINE_CLEAR_MS)
        assertEquals(listOf<SessionEvent>(SessionEvent.Grew(6)), events)
        assertEquals(6, s.board.size)
        assertEquals(Family.SQUARE, s.board.familyAt(3, 3)) // still where it was
        for (i in 0 until 6) { assertTrue(s.board.isEmpty(5, i)); assertTrue(s.board.isEmpty(i, 5)) }
        assertTrue("growth happens once", run(s, t0 + 2000, t0 + 5000).none { it is SessionEvent.Grew })
        assertEquals(6, s.board.size)
    }

    @Test
    fun `growth adds one row and column at a time, never past 9x9, and clear-outs never move the ramp`() {
        val s = BlocksSession(Random(5), startClears = 35)
        assertEquals(9, s.board.size)
        assertEquals(6, s.stage)
        assertTrue(run(s, 0, 5000).none { it is SessionEvent.Grew })
        assertEquals(9, s.board.size)
    }

    @Test
    fun `growth waits for a held block to be let go`() {
        val board = Board.from(5) { r, c -> if (r == 0 && c != 0) Family.BAR3 else null }
        val s = BlocksSession(Random(3), startClears = 7, startBoard = board, startTray = threeDots())
        s.place(0, 0, 0, 0)
        s.setHolding(true, 500)
        assertTrue(run(s, 500, 20_000).none { it is SessionEvent.Grew })
        assertEquals(5, s.board.size)
        s.setHolding(false, 20_000)
        assertEquals(listOf<SessionEvent>(SessionEvent.Grew(6)), s.tick(20_050))
    }

    // ------------------------------------------------------------------ refill

    @Test
    fun `three new blocks arrive together 500ms after the last one is placed, exactly once`() {
        val s = BlocksSession(Random(9), startTray = threeDots())
        s.place(0, 0, 0, 100)
        s.place(1, 2, 2, 400)
        assertTrue("two placed is not enough", run(s, 400, 5000).isEmpty())
        val before = s.trayDeals
        s.place(2, 4, 4, 5000)
        assertTrue(s.isTrayEmpty)
        assertTrue(s.tick(5000).isEmpty())
        assertTrue(s.tick(5499).isEmpty())
        assertTrue("never before", s.tray.all { it == null })
        assertEquals(listOf<SessionEvent>(SessionEvent.Refilled), s.tick(5500))
        assertTrue(s.tray.all { it != null })
        assertEquals(before + 1, s.trayDeals)
        // Mashing the clock does not deal again.
        assertTrue(run(s, 5500, 20_000).isEmpty())
        repeat(100) { assertTrue(s.tick(5600).isEmpty()) }
        assertEquals(before + 1, s.trayDeals)
    }

    @Test
    fun `the refill waits for a line-clear animation to finish, then 500ms more`() {
        val board = Board.from(5) { r, c -> if (r == 4 && c != 4) Family.BAR3 else null }
        val s = BlocksSession(Random(9), startBoard = board, startTray = threeDots())
        s.place(0, 0, 0, 0)
        s.place(1, 1, 1, 100)
        val p = s.place(2, 4, 4, 200)!! // completes row 4
        assertTrue(p.cleared)
        val animationEnds = 200 + BlocksTiming.DROP_MS + BlocksTiming.LINE_CLEAR_MS
        assertTrue(run(s, 200, animationEnds + BlocksTiming.REFILL_DELAY_MS - 50).isEmpty())
        assertEquals(listOf<SessionEvent>(SessionEvent.Refilled), s.tick(animationEnds + BlocksTiming.REFILL_DELAY_MS))
    }

    @Test
    fun `when the last block also grows the board, the refill waits for the growth too and deals from the new stage`() {
        val board = Board.from(5) { r, c -> if (r == 4 && c != 4) Family.BAR3 else null }
        val s = BlocksSession(Random(4), startClears = 7, startBoard = board, startTray = threeDots())
        s.place(0, 0, 0, 0)
        s.place(1, 1, 1, 100)
        s.place(2, 4, 4, 200)
        val growAt = 200 + BlocksTiming.DROP_MS + BlocksTiming.LINE_CLEAR_MS
        assertTrue(run(s, 200, growAt - 50).isEmpty())
        assertEquals(listOf<SessionEvent>(SessionEvent.Grew(6)), s.tick(growAt))
        assertTrue(run(s, growAt, growAt + BlocksTiming.GROWTH_MS + BlocksTiming.REFILL_DELAY_MS - 50).isEmpty())
        assertEquals(listOf<SessionEvent>(SessionEvent.Refilled), s.tick(growAt + BlocksTiming.GROWTH_MS + BlocksTiming.REFILL_DELAY_MS))
        assertEquals(3, s.trayStage)
        assertTrue(s.tray.all { it != null && it in BlocksRamp.shapesFor(3) })
    }

    @Test
    fun `a refill is never the same trio twice in a row`() {
        val s = BlocksSession(Random(12))
        var now = 0L
        var previous = s.tray.filterNotNull().map { it.id }.sorted()
        repeat(60) {
            // Play every block (when one has nowhere to go the game clears space by itself), then let the follow-ups run.
            var guard = 0
            while (!s.isTrayEmpty && guard++ < 50) {
                var moved = false
                for (slot in 0 until 3) s.tray[slot]?.let { sh -> s.board.spots(sh).firstOrNull()?.let { sp -> if (s.place(slot, sp.row, sp.col, now) != null) moved = true } }
                if (!moved) now = settle(s, now)
            }
            assertTrue(s.isTrayEmpty)
            now = settle(s, now)
            val trio = s.tray.filterNotNull().map { it.id }.sorted()
            assertEquals(3, trio.size)
            assertTrue("repeated $trio", trio != previous)
            previous = trio
        }
    }

    // ------------------------------------------------------------------ clear-out

    private fun stuckBoard() = Board.from(5) { r, c -> if (r == c) null else Family.BAR3 }

    @Test
    fun `when nothing in the tray fits, the fullest rows clear a second after his last move`() {
        val s = BlocksSession(Random(2), startBoard = stuckBoard(), startTray = listOf(shape("bar2h"), shape("bar2v"), shape("bar3h")))
        assertTrue(s.isStuck)
        assertTrue(s.hasPending)
        s.setHolding(true, 0)
        s.setHolding(false, 5000) // let go of a block that could not land: a full second of peace starts now
        assertTrue(run(s, 5000, 5950).isEmpty())
        assertTrue(s.tick(5999).isEmpty())
        val events = s.tick(6000)
        assertEquals(1, events.size)
        val out = events[0] as SessionEvent.ClearedOut
        assertEquals(listOf(0, 1), out.rows)
        assertEquals(8, out.removed.size)
        assertFalse(s.isStuck)
        assertEquals(3, s.tray.count { it != null }) // the tray is untouched
        assertEquals(0, s.clears) // and no progress is taken or given
        assertEquals(1, s.stage)
        assertEquals(5, s.board.size)
        assertTrue(run(s, 6000, 20_000).isEmpty())
    }

    @Test
    fun `the clear-out waits while a block is being dragged`() {
        val s = BlocksSession(Random(2), startBoard = stuckBoard(), startTray = listOf(shape("bar2h"), shape("bar2v"), shape("bar3h")))
        s.setHolding(true, 0)
        assertTrue(run(s, 0, 30_000).isEmpty())
        assertTrue(s.isStuck)
        s.setHolding(false, 30_000)
        assertTrue(s.tick(30_500).isEmpty())
        assertEquals(1, s.tick(31_000).size)
    }

    // ------------------------------------------------------------------ fuzz

    /** Runs the clock forward in 100ms steps until the session has nothing left to do (bounded), returning the new time. */
    private fun settle(s: BlocksSession, from: Long): Long {
        var t = from
        var steps = 0
        while (s.hasPending) {
            t += 100
            s.tick(t)
            assertTrue("session never settled: tray=${s.tray} stuck=${s.isStuck}\n${s.board}", steps++ < 400)
        }
        return t
    }

    private class Stats { var moves = 0; var refills = 0; var clearOuts = 0; var growths = 0; var maxStage = 1; var lines = 0 }

    private fun checkResting(s: BlocksSession, label: String) {
        assertTrue("$label: the tray is empty and nothing is coming", !s.isTrayEmpty)
        assertTrue("$label: no block fits and nothing is coming\n${s.board}\n${s.tray}", s.tray.filterNotNull().any { s.board.hasSpot(it) })
        assertTrue("$label: a complete line was left on the board", s.board.completedRows().isEmpty() && s.board.completedCols().isEmpty())
        assertEquals("$label: board size follows the stage", BlocksRamp.boardSizeFor(s.stage), s.board.size)
        assertTrue(s.board.emptyCount > 0)
    }

    /**
     * Random legal play with the clock running: [preferLines] is the chance of choosing a placement that completes a line
     * when there is one (so the ramp is actually climbed), otherwise anything legal. Never depends on a strategy for
     * there to be a way forward: after every move the game must settle into a state with a legal move.
     */
    private fun fuzz(startClears: Int, seed: Int, moves: Int, preferLines: Double, stats: Stats) {
        val random = Random(seed)
        val s = BlocksSession(Random(seed + 1), startClears)
        var now = 0L
        var lastStage = s.stage
        while (stats.moves < moves) {
            now = settle(s, now)
            checkResting(s, "seed $seed move ${stats.moves}")
            val options = (0 until 3).mapNotNull { slot -> s.tray[slot]?.let { sh -> s.board.spots(sh).map { Triple(slot, sh, it) } } }.flatten()
            assertTrue(options.isNotEmpty())
            val finishing = options.filter { (_, sh, sp) -> s.board.placeAndClear(sh, sp.row, sp.col).cleared }
            val (slot, _, spot) = if (finishing.isNotEmpty() && random.nextDouble() < preferLines) finishing[random.nextInt(finishing.size)] else options[random.nextInt(options.size)]
            val dealsBefore = s.trayDeals
            val clearsBefore = s.clears
            val p = s.place(slot, spot.row, spot.col, now)!!
            stats.moves++
            if (p.cleared) { stats.lines++; assertEquals(clearsBefore + 1, s.clears) } else assertEquals(clearsBefore, s.clears)
            assertTrue(s.stage >= lastStage)
            lastStage = s.stage
            stats.maxStage = maxOf(stats.maxStage, s.stage)
            now += random.nextInt(0, 1500)
            val events = ArrayList<SessionEvent>()
            var t = now
            // Observe the follow-ups as frames would.
            repeat(30) { events += s.tick(t); t += random.nextInt(16, 200) }
            now = t
            stats.refills += events.count { it is SessionEvent.Refilled }
            stats.clearOuts += events.count { it is SessionEvent.ClearedOut }
            stats.growths += events.count { it is SessionEvent.Grew }
            assertTrue(s.trayDeals - dealsBefore <= 1)
            assertTrue(s.board.size <= 9)
        }
        now = settle(s, now)
        checkResting(s, "seed $seed end")
    }

    @Test
    fun `random legal play never runs out of moves or throws, from every starting stage`() {
        val stats = Stats()
        for (stage in 1..6) {
            val start = listOf(0, 3, 8, 15, 24, 35)[stage - 1]
            fuzz(start, seed = 100 + stage, moves = stats.moves + 2500, preferLines = 0.0, stats = stats)
            fuzz(start, seed = 200 + stage, moves = stats.moves + 1500, preferLines = 0.6, stats = stats)
        }
        assertTrue("moves ${stats.moves}", stats.moves >= 20_000 - 1)
        assertTrue("refills ${stats.refills}", stats.refills > 1000)
        assertTrue("the game must have cleared lines it made itself", stats.lines > 500)
        println("fuzz: moves=${stats.moves} lines=${stats.lines} refills=${stats.refills} clearOuts=${stats.clearOuts} growths=${stats.growths}")
    }

    @Test
    fun `a whole session climbs the ramp from the empty start to the 9x9 cap without ever getting stuck`() {
        val stats = Stats()
        fuzz(0, seed = 7, moves = 6000, preferLines = 0.9, stats = stats)
        assertEquals(6, stats.maxStage)
        assertTrue("growths ${stats.growths}", stats.growths >= 4)
    }

    @Test
    fun `impatient play with the clock barely moving stays consistent`() {
        // Place the moment anything is legal, with the clock ticking only a little: animations pending all the time.
        val random = Random(77)
        val s = BlocksSession(Random(78), startClears = 6)
        var now = 0L
        var placed = 0
        repeat(12_000) {
            now += random.nextInt(0, 120)
            s.tick(now)
            if (random.nextInt(4) == 0) { s.setHolding(true, now); s.tick(now); s.setHolding(false, now) }
            val options = (0 until 3).mapNotNull { slot -> s.tray[slot]?.let { sh -> s.board.spots(sh).map { Triple(slot, sh, it) } } }.flatten()
            if (options.isNotEmpty() && random.nextInt(3) != 0) {
                val (slot, _, spot) = options[random.nextInt(options.size)]
                assertNotNull(s.place(slot, spot.row, spot.col, now))
                placed++
            }
            assertTrue(s.board.completedRows().isEmpty() && s.board.completedCols().isEmpty())
            assertTrue(s.board.size in 5..9)
            assertTrue(s.tray.size == 3)
        }
        now = settle(s, now)
        checkResting(s, "impatient end")
        assertTrue("placed $placed", placed > 200)
    }
}
