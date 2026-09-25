package com.pawplay.app.games.pawblocks

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

/**
 * Score, three paws and the kind ending of Paw Blocks (docs/PRD.md stories 63, 64, 65, 70 and 72), against the pure
 * session with a virtual clock.
 */
class PawBlocksScoreAndPawsTest {
    private val dot = BlockShapes.dot
    private fun shape(id: String) = BlockShapes.byId.getValue(id)

    private fun events(s: BlocksSession, from: Long, to: Long, step: Long = 50): List<SessionEvent> {
        val out = ArrayList<SessionEvent>()
        var t = from
        while (t <= to) { out += s.tick(t); t += step }
        return out
    }

    /** Nothing in the tray fits: only the diagonal is empty. */
    private fun stuckBoard() = Board.from(5) { r, c -> if (r == c) null else Family.BAR3 }
    private val stuckTray get() = listOf<BlockShape?>(shape("bar2h"), shape("bar2v"), shape("bar3h"))

    private fun stuckSession(paws: Int, bestBefore: Int = 0, startScore: Int = 0) =
        BlocksSession(Random(2), startBoard = stuckBoard(), startTray = stuckTray, startPaws = paws, bestBefore = bestBefore, startScore = startScore)

    // ================================================================== a new game

    @Test
    fun `a new game starts at score 0 with 3 paws, playing, on an empty 5x5 board at stage 1`() {
        assertEquals(3, BlocksRamp.START_PAWS)
        repeat(20) { seed ->
            val s = BlocksSession(Random(seed))
            assertEquals(0, s.score)
            assertEquals(3, s.paws)
            assertEquals(BlocksPhase.PLAYING, s.phase)
            assertEquals(5, s.board.size)
            assertEquals(0, s.board.filledCount)
            assertEquals(1, s.stage)
            assertEquals(0, s.clears)
            assertFalse("0 does not beat a best of 0", s.isNewBest)
        }
    }

    // ================================================================== scoring

    /** A board where the placement of [shape] at ([row], [col]) completes exactly the lines the picture leaves open. */
    private fun place(board: Board, shape: BlockShape, row: Int, col: Int, clears: Int = 0): Pair<BlocksSession, Placed> {
        val s = BlocksSession(Random(1), startClears = clears, startBoard = board, startTray = listOf(shape, dot, dot))
        return s to s.place(0, row, col, 1000)!!
    }

    @Test
    fun `a block that completes no line is worth 3`() {
        val (s, p) = place(Board.empty(5), dot, 2, 2)
        assertFalse(p.cleared)
        assertEquals(3, p.points)
        assertEquals(3, s.score)
    }

    @Test
    fun `one line is 3 plus 10`() {
        val (s, p) = place(Board.parse(".XXXX", ".....", ".....", ".....", "....."), dot, 0, 0)
        assertEquals(listOf(0), p.rows); assertTrue(p.cols.isEmpty())
        assertEquals(13, p.points); assertEquals(13, s.score)
    }

    @Test
    fun `a row and a column together are two lines, 3 plus 30, though the ramp counts one clear`() {
        val (s, p) = place(Board.parse("..X..", "..X..", "XX.XX", "..X..", "..X.."), dot, 2, 2)
        assertEquals(listOf(2), p.rows); assertEquals(listOf(2), p.cols)
        assertEquals(33, p.points); assertEquals(33, s.score)
        assertEquals("a double line is still one clear for the ramp", 1, s.clears)
    }

    @Test
    fun `three lines are 3 plus 60`() {
        val (s, p) = place(Board.parse(".XXXX", ".XXXX", ".XXXX", ".....", "....."), shape("bar3v"), 0, 0)
        assertEquals(3, p.rows.size + p.cols.size)
        assertEquals(63, p.points); assertEquals(63, s.score)
        assertEquals(1, s.clears)
    }

    @Test
    fun `four lines are 3 plus 100`() {
        val (s, p) = place(Board.parse("..XXX", "..XXX", "XX...", "XX...", "XX..."), shape("sq"), 0, 0)
        assertEquals(4, p.rows.size + p.cols.size)
        assertEquals(103, p.points); assertEquals(103, s.score)
    }

    @Test
    fun `more than four lines are still 3 plus 100`() {
        // 6x6: a 2 x 3 block completes three rows and two columns at once.
        val board = Board.parse("..XXXX", "..XXXX", "..XXXX", "XX....", "XX....", "XX....")
        val (s, p) = place(board, shape("rectV"), 0, 0)
        assertEquals(3, p.rows.size); assertEquals(2, p.cols.size)
        assertEquals(103, p.points); assertEquals(103, s.score)
    }

    @Test
    fun `the score adds up over placements and never goes down`() {
        val s = BlocksSession(Random(1), startBoard = Board.parse(".XXXX", ".....", ".....", ".....", "....."), startTray = listOf(dot, dot, shape("bar2h")))
        var last = 0
        assertEquals(13, s.place(0, 0, 0, 100)!!.points); assertEquals(13, s.score); last = s.score
        assertEquals(3, s.place(1, 3, 3, 200)!!.points); assertEquals(16, s.score); assertTrue(s.score >= last)
        assertEquals(3, s.place(2, 4, 0, 300)!!.points); assertEquals(19, s.score)
    }

    @Test
    fun `a wrong drop, a second placement of the same block and a placement off the board add and take nothing`() {
        val s = BlocksSession(Random(1), startBoard = Board.parse("X....", ".....", ".....", ".....", "....."), startTray = listOf(dot, shape("bar2h"), shape("bar2v")))
        assertNull("on a filled cell", s.place(0, 0, 0, 100))
        assertNull("off the board", s.place(1, 0, 4, 100))
        assertNull("no such slot", s.place(7, 0, 1, 100))
        assertEquals(0, s.score)
        assertNotNull(s.place(0, 1, 1, 200))
        assertEquals(3, s.score)
        assertNull("the same block twice", s.place(0, 2, 2, 300))
        assertEquals(3, s.score)
    }

    @Test
    fun `refills and growth add nothing to the score, and neither does the ramp moving on`() {
        val s = BlocksSession(Random(4), startClears = 2, startBoard = Board.parse(".XXXX", ".....", ".....", ".....", "....."), startTray = listOf(dot, dot, dot))
        s.place(0, 0, 0, 0) // one line: clears becomes 3, stage 2, no growth (5x5 again), points 13
        assertEquals(13, s.score)
        s.place(1, 2, 2, 10); s.place(2, 3, 3, 20)
        assertEquals(19, s.score)
        val ev = events(s, 20, 5000)
        assertTrue("the refill happened", ev.any { it is SessionEvent.Refilled })
        assertEquals("nothing but placements scores", 19, s.score)

        // Growth: the ramp moved on to a 6x6 board.
        val g = BlocksSession(Random(4), startClears = 7, startBoard = Board.parse(".XXXX", ".....", ".....", ".....", "....."), startTray = listOf(dot, dot, dot))
        g.place(0, 0, 0, 0)
        assertEquals(13, g.score)
        assertTrue(events(g, 0, 3000).any { it is SessionEvent.Grew })
        assertEquals(13, g.score)
    }

    @Test
    fun `a clear-out scores nothing, counts nothing for the ramp and takes nothing away`() {
        val s = stuckSession(3, startScore = 40)
        val ev = events(s, 0, 4000)
        assertEquals(1, ev.count { it is SessionEvent.ClearedOut })
        assertEquals(40, s.score)
        assertEquals(0, s.clears)
        assertEquals(1, s.stage)
    }

    @Test
    fun `the score stops at 999999`() {
        val s = BlocksSession(Random(1), startScore = 999_998, startTray = listOf(dot, dot, dot))
        s.place(0, 0, 0, 0)
        assertEquals(999_999, s.score)
        s.place(1, 1, 1, 10)
        assertEquals(999_999, s.score)
        assertEquals(999_999, BlocksSession(Random(1), startScore = 5_000_000).score)
    }

    // ================================================================== new best

    @Test
    fun `new best means the score beats the best from before this game, equal does not`() {
        assertFalse(BlocksSession(Random(1), bestBefore = 10, startScore = 9).isNewBest)
        assertFalse(BlocksSession(Random(1), bestBefore = 10, startScore = 10).isNewBest)
        assertTrue(BlocksSession(Random(1), bestBefore = 10, startScore = 11).isNewBest)
        assertTrue("first ever game: best is 0, so any score above 0 is new", BlocksSession(Random(1), bestBefore = 0, startScore = 3).isNewBest)
        assertFalse("but 0 is not", BlocksSession(Random(1), bestBefore = 0, startScore = 0).isNewBest)
    }

    @Test
    fun `new best follows the score as the game is played`() {
        val s = BlocksSession(Random(1), bestBefore = 5, startTray = listOf(dot, dot, dot))
        assertFalse(s.isNewBest)
        s.place(0, 0, 0, 0)
        assertFalse("3 is under 5", s.isNewBest)
        s.place(1, 1, 1, 10)
        assertTrue("6 beats 5", s.isNewBest)
    }

    // ================================================================== paws

    @Test
    fun `stuck with paws left clears the fullest rows a second after his last move and spends exactly one paw`() {
        val s = stuckSession(3)
        s.setHolding(true, 0)
        s.setHolding(false, 5000)
        assertTrue(events(s, 5000, 5999, 1).isEmpty())
        val ev = s.tick(6000)
        assertEquals(1, ev.size)
        val out = ev[0] as SessionEvent.ClearedOut
        assertEquals(2, out.pawsLeft)
        assertEquals(2, s.paws)
        assertEquals(BlocksPhase.PLAYING, s.phase)
        assertFalse(s.isStuck)
        // Every frame after that: no second paw for the same clear-out, however long it takes.
        assertTrue(events(s, 6000, 30_000, 16).isEmpty())
        assertEquals(2, s.paws)
    }

    @Test
    fun `one paw goes per clear-out even when it takes several rows`() {
        // A 9x9 board with only the top and bottom rows lacking a hole: the clear-out takes 3 rows per round, maybe more rounds.
        val board = Board.from(9) { r, c -> if (r == c) null else Family.BAR4 }
        val s = BlocksSession(Random(3), startClears = 35, startBoard = board, startTray = listOf(shape("bar5h"), shape("bar5v"), shape("bar4h")))
        assertTrue(s.isStuck)
        val ev = events(s, 0, 4000)
        val out = ev.filterIsInstance<SessionEvent.ClearedOut>()
        assertEquals(1, out.size)
        assertTrue("several rows went", out[0].rows.size >= 3)
        assertEquals(2, s.paws)
    }

    @Test
    fun `the paws go 2, 1, 0 for three clear-outs and the game ends on the next stuck moment`() {
        val paws = ArrayList<Int>()
        var s = stuckSession(3)
        // Three separate stuck situations in a row, each starting from the previous session's paws.
        var left = 3
        repeat(3) {
            s = stuckSession(left)
            val ev = events(s, 0, 4000).filterIsInstance<SessionEvent.ClearedOut>()
            assertEquals(1, ev.size)
            paws += ev[0].pawsLeft
            left = ev[0].pawsLeft
        }
        assertEquals(listOf(2, 1, 0), paws)
        // With none left, the same stuck situation ends the game.
        val end = stuckSession(0)
        val ev = events(end, 0, 4000)
        assertEquals(1, ev.filterIsInstance<SessionEvent.GameEnded>().size)
        assertTrue(ev.none { it is SessionEvent.ClearedOut })
        assertEquals(BlocksPhase.GAME_OVER, end.phase)
    }

    @Test
    fun `a paw only ever goes down`() {
        val s = stuckSession(3)
        var last = s.paws
        var t = 0L
        repeat(400) { t += 50; s.tick(t); assertTrue(s.paws <= last); last = s.paws }
        assertEquals(2, last)
    }

    // ================================================================== the ending

    @Test
    fun `stuck with no paws ends the game a second after his last move, exactly once, with the score`() {
        val s = stuckSession(0, startScore = 77, bestBefore = 50)
        s.setHolding(true, 0)
        s.setHolding(false, 5000)
        assertTrue(events(s, 5000, 5999, 1).isEmpty())
        assertEquals(BlocksPhase.PLAYING, s.phase)
        val ev = s.tick(6000)
        assertEquals(listOf(SessionEvent.GameEnded(77)), ev)
        assertEquals(BlocksPhase.GAME_OVER, s.phase)
        assertEquals(6000L, s.gameOverAt)
        assertTrue(s.isNewBest)
        // Never again, however long we keep ticking.
        assertTrue(events(s, 6000, 600_000, 250).isEmpty())
        assertEquals(6000L, s.gameOverAt)
        assertFalse(s.hasPending)
    }

    @Test
    fun `after the game has ended nothing can be placed, held or ticked`() {
        val s = stuckSession(0)
        events(s, 0, 4000)
        assertEquals(BlocksPhase.GAME_OVER, s.phase)
        val score = s.score
        val board = s.board
        for (slot in 0..2) assertNull(s.place(slot, 0, 0, 5000))
        assertFalse(s.canPlace(0, 0, 0))
        s.setHolding(true, 5000)
        s.setHolding(false, 5001)
        assertTrue(s.tick(9000).isEmpty())
        assertEquals(score, s.score)
        assertEquals(board, s.board)
        assertEquals(0, s.paws)
    }

    @Test
    fun `the game never ends while a block is held, however long`() {
        val s = stuckSession(0)
        s.setHolding(true, 0)
        assertTrue(events(s, 0, 120_000, 100).isEmpty())
        assertEquals(BlocksPhase.PLAYING, s.phase)
        s.setHolding(false, 120_000)
        assertTrue("a full second of peace after the let-go", events(s, 120_000, 120_999, 1).isEmpty())
        assertEquals(1, s.tick(121_000).size)
        assertEquals(BlocksPhase.GAME_OVER, s.phase)
    }

    /** After this move the row clears and the two squares left in the tray fit nowhere (checkerboard holes only). */
    private fun clearThenStuck(paws: Int): BlocksSession = BlocksSession(
        Random(1),
        startBoard = Board.parse(".XXXX", "X.X.X", ".X.X.", "X.X.X", ".X.X."),
        startTray = listOf(dot, shape("sq"), shape("sq")),
        startPaws = paws,
    )

    @Test
    fun `the game never ends during a line-clear animation, only a second after it`() {
        val s = clearThenStuck(0)
        assertFalse(s.isStuck)
        val p = s.place(0, 0, 0, 1000)!!
        assertTrue(p.cleared)
        assertTrue(s.isStuck)
        // The animation keeps the board busy until 1000 + 90 + 1000; the ending waits a second after that.
        assertTrue(events(s, 1000, 3089, 1).isEmpty())
        assertEquals(BlocksPhase.PLAYING, s.phase)
        assertEquals(listOf(SessionEvent.GameEnded(13)), s.tick(3090))
    }

    @Test
    fun `with a paw left the same moment is a clear-out instead, and the paw goes`() {
        val s = clearThenStuck(1)
        s.place(0, 0, 0, 1000)
        assertTrue(events(s, 1000, 3089, 1).isEmpty())
        val ev = s.tick(3090)
        assertEquals(1, ev.size)
        assertEquals(0, (ev[0] as SessionEvent.ClearedOut).pawsLeft)
        assertEquals(0, s.paws)
        assertEquals(BlocksPhase.PLAYING, s.phase)
        assertEquals("the clear-out scored nothing", 13, s.score)
    }

    @Test
    fun `growth comes first, so a stuck board that is about to grow does not end the game`() {
        // Stage 3 says 6x6 but the board is still 5x5; the new row and column give the blocks room.
        val s = BlocksSession(Random(2), startClears = 8, startBoard = stuckBoard(), startTray = stuckTray, startPaws = 0)
        assertTrue(s.isStuck)
        val ev = events(s, 0, 10_000)
        assertTrue(ev.any { it is SessionEvent.Grew })
        assertTrue(ev.none { it is SessionEvent.GameEnded })
        assertEquals(BlocksPhase.PLAYING, s.phase)
        assertEquals(6, s.board.size)
        assertFalse(s.isStuck)
    }

    @Test
    fun `a board that is not stuck never ends, and a fresh tray is never stuck`() {
        val s = BlocksSession(Random(9), startPaws = 0)
        assertFalse(s.isStuck)
        assertTrue(events(s, 0, 60_000, 250).isEmpty())
        assertEquals(BlocksPhase.PLAYING, s.phase)
        // A tray only counts as stuck when blocks are still in it: an empty tray is waiting for its refill.
        val t = BlocksSession(Random(9), startBoard = stuckBoard(), startTray = listOf(null, null, null), startPaws = 0)
        assertFalse(t.isStuck)
        assertTrue(events(t, 0, 400).any { it is SessionEvent.Refilled })
    }

    @Test
    fun `coming back to the app after a long time ends the game once on the first frame`() {
        val s = stuckSession(0)
        s.setHolding(true, 0)
        s.setHolding(false, 1000)
        assertEquals(1, s.tick(3_600_000).size) // an hour later, the first frame back
        assertTrue(s.tick(3_600_016).isEmpty())
    }

    // ================================================================== a whole game, ended by paws running out

    private fun options(s: BlocksSession) =
        (0 until 3).filter { s.slot(it) != null }.flatMap { slot -> s.board.spots(s.slot(slot)!!).map { slot to it } }

    /** A player who avoids lines and leaves as little room as he can: the game must end, with three paws spent and one ending. */
    private fun playToTheEnd(seed: Int): List<SessionEvent> {
        val s = BlocksSession(Random(seed))
        val all = ArrayList<SessionEvent>()
        var now = 0L
        var moves = 0
        while (s.phase == BlocksPhase.PLAYING && moves < 20_000) {
            var waited = 0
            while (s.phase == BlocksPhase.PLAYING && options(s).isEmpty()) {
                now += 100
                all += s.tick(now)
                assertTrue("seed $seed: waited too long", waited++ < 500)
            }
            if (s.phase != BlocksPhase.PLAYING) break
            val (slot, spot) = options(s).minByOrNull { (sl, sp) ->
                val r = s.board.placeAndClear(s.slot(sl)!!, sp.row, sp.col)
                (if (r.cleared) 1_000_000 else 0) + BlocksRamp.shapesFor(s.stage).sumOf { r.board.spots(it).size }
            }!!
            val scoreBefore = s.score
            val p = s.place(slot, spot.row, spot.col, now)!!
            assertEquals(scoreBefore + p.points, s.score)
            moves++
            now += 200
            all += s.tick(now)
        }
        assertEquals("seed $seed: the game ended", BlocksPhase.GAME_OVER, s.phase)
        assertEquals(0, s.paws)
        return all
    }

    @Test
    fun `a player who fills the board uses three paws, one after another, and then the game ends once`() {
        for (seed in 1..40) {
            val ev = playToTheEnd(seed)
            val outs = ev.filterIsInstance<SessionEvent.ClearedOut>()
            assertEquals("seed $seed clear-outs", listOf(2, 1, 0), outs.map { it.pawsLeft })
            assertEquals("seed $seed endings", 1, ev.count { it is SessionEvent.GameEnded })
            assertTrue("the ending is the last thing that happened", ev.last() is SessionEvent.GameEnded)
        }
    }

    // ================================================================== play again

    @Test
    fun `play again is a fresh session with an empty 5x5, stage 1, score 0, 3 paws, playing, and the old one stays over`() {
        val old = stuckSession(0, startScore = 90)
        events(old, 0, 4000)
        assertEquals(BlocksPhase.GAME_OVER, old.phase)
        val next = BlocksSession(Random(5), bestBefore = maxOf(0, old.score))
        assertEquals(5, next.board.size); assertEquals(0, next.board.filledCount)
        assertEquals(1, next.stage); assertEquals(0, next.clears)
        assertEquals(0, next.score); assertEquals(3, next.paws)
        assertEquals(BlocksPhase.PLAYING, next.phase)
        assertEquals(3, next.tray.count { it != null })
        assertFalse(next.isNewBest)
        assertEquals(BlocksPhase.GAME_OVER, old.phase)
    }
}
