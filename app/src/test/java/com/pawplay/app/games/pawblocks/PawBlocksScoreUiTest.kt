package com.pawplay.app.games.pawblocks

import com.pawplay.app.data.BestScore
import com.pawplay.app.data.IntStore
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

/**
 * The touch layer's side of the score, paws and ending (docs/PRD.md stories 63-65, 70, 72): the score is reported after a
 * legal drop only, a tap is not a drop, the paw fade is timed against the clear-out, and once the game has ended the
 * touch layer does nothing.
 */
class PawBlocksScoreUiTest {
    private val dot = BlockShapes.dot
    private fun shape(id: String) = BlockShapes.byId.getValue(id)

    private class MemoryStore : IntStore {
        val map = HashMap<String, Int>()
        var writes = 0
        override fun read(key: String): Int? = map[key]
        override fun write(key: String, value: Int) { writes++; map[key] = value }
    }

    private fun ui(
        board: Board? = null,
        tray: List<BlockShape?> = listOf(dot, shape("bar2h"), shape("bar2v")),
        paws: Int = 3,
        bestBefore: Int = 0,
        onScore: (Int) -> Unit = {},
    ): BlocksUi = BlocksUi(BlocksSession(Random(3), startBoard = board, startTray = tray, startPaws = paws, bestBefore = bestBefore), onScore = onScore).also { it.onFrame(1000) }

    private fun slotCentre(u: BlocksUi, i: Int) = (u.layout.slotLeftOf(i) + u.layout.slotWidth / 2f) to (u.layout.slotTop + u.layout.slotHeight / 2f)

    private fun fingerFor(u: BlocksUi, shape: BlockShape, row: Int, col: Int): Pair<Float, Float> {
        val cell = u.layout.cellSize(u.session.board.size.toFloat())
        return (u.layout.gridLeft + col * cell + shape.width * cell / 2f) to (u.layout.gridTop + row * cell + shape.height * cell + Snap.RIDE_DP)
    }

    private fun stuckBoard() = Board.from(5) { r, c -> if (r == c) null else Family.BAR3 }
    private val stuckTray get() = listOf<BlockShape?>(shape("bar2h"), shape("bar2v"), shape("bar3h"))

    // ------------------------------------------------------------------ the score is reported after a legal drop

    @Test
    fun `a legal drop reports the new score once, a wrong drop, a cancel and a tap report nothing`() {
        val told = ArrayList<Int>()
        val u = ui(onScore = { told += it })
        val (sx, sy) = slotCentre(u, 1)
        val (fx, fy) = fingerFor(u, shape("bar2h"), 2, 1)

        // Cancelled over a legal spot: nothing.
        u.onPointer(1, sx, sy, true, false, false); u.onPointer(1, fx, fy, true, true, false)
        u.onPointer(1, fx, fy, false, true, true)
        assertTrue(told.isEmpty()); assertEquals(0, u.session.score)

        // A wrong drop (off the screen): nothing.
        u.onPointer(2, sx, sy, true, false, false); u.onPointer(2, sx, 900f, true, true, false)
        u.onPointer(2, sx, 900f, false, true, false)
        assertTrue(told.isEmpty()); assertEquals(0, u.session.score)

        // A legal drop: once, with the new score.
        u.onPointer(3, sx, sy, true, false, false); u.onPointer(3, fx, fy, true, true, false)
        u.onPointer(3, fx, fy, false, true, false)
        assertEquals(listOf(3), told)
        assertEquals(3, u.session.score)
    }

    @Test
    fun `a tap on a tray block does not drop it onto the board`() {
        val u = ui()
        val (sx, sy) = slotCentre(u, 1)
        // The layout puts the tray 40dp under the board, so a block riding 64dp above a resting finger sits within snap reach.
        assertTrue("sanity: the ghost would be within reach", u.layout.dropSpot(u.session.board, shape("bar2h"), u.layout.cellSize(5f), sx, sy) != null)
        u.onPointer(1, sx, sy, true, false, false)
        u.onPointer(1, sx + 3f, sy - 2f, true, true, false) // a wobble of a few dp is still a tap
        u.onPointer(1, sx + 3f, sy - 2f, false, true, false)
        assertEquals(0, u.session.board.filledCount)
        assertEquals(0, u.session.score)
        assertEquals("the block glides home", 1, u.returns.size)
    }

    @Test
    fun `a real drag, even a short one, still drops`() {
        val u = ui()
        val (sx, sy) = slotCentre(u, 1)
        u.onPointer(1, sx, sy, true, false, false)
        u.onPointer(1, sx, sy - Snap.TAP_SLOP_DP - 1f, true, true, false)
        u.onPointer(1, sx, sy - Snap.TAP_SLOP_DP - 1f, false, true, false)
        assertEquals(2, u.session.board.filledCount)
        assertEquals(3, u.session.score)
    }

    @Test
    fun `the best is saved the moment a placement beats it, so leaving early never loses it`() {
        val store = MemoryStore().also { it.map["best.paw-blocks"] = 4 }
        val best = BestScore("best.paw-blocks", store)
        best.load()
        val u = ui(bestBefore = best.best, onScore = { best.submit(it) })
        val (sx, sy) = slotCentre(u, 0)
        val (fx, fy) = fingerFor(u, dot, 1, 1)
        u.onPointer(1, sx, sy, true, false, false); u.onPointer(1, fx, fy, true, true, false); u.onPointer(1, fx, fy, false, true, false)
        assertEquals("3 does not beat 4", 4, best.best)
        assertEquals(0, store.writes)
        assertFalse(u.session.isNewBest)
        val (sx2, sy2) = slotCentre(u, 2)
        val (gx, gy) = fingerFor(u, shape("bar2v"), 2, 3)
        u.onPointer(2, sx2, sy2, true, false, false); u.onPointer(2, gx, gy, true, true, false); u.onPointer(2, gx, gy, false, true, false)
        // The game is left right here (home, back, closing the app): the score is already saved.
        assertEquals(6, u.session.score)
        assertEquals(6, best.best)
        assertEquals(6, store.map["best.paw-blocks"])
        assertTrue(u.session.isNewBest)
    }

    // ------------------------------------------------------------------ the paw fade

    @Test
    fun `a clear-out fades the paw that was spent, from 200ms into the clear-out`() {
        // Stuck from the start, so the first frame does the clear-out.
        val u = ui(board = stuckBoard(), tray = stuckTray)
        assertEquals(2, u.session.paws)
        assertEquals("the rightmost paw fades first", 2, u.pawFadeIndex)
        assertEquals(PAW_FADE_DELAY_MS, u.pawFadeStart) // the game's clock reads 0 at the first frame
        assertEquals(1, u.outs.size)
        assertTrue("the frame loop keeps running for the whole clear-out, so the fade is seen to the end", u.active)
        assertTrue("the paw's 0.6s fade is over before the clear-out effect is", u.pawFadeStart + com.pawplay.app.ui.PAW_FADE_MS <= u.outs[0].end)
        u.onFrame(1000 + BlocksTiming.CLEAR_OUT_MS)
        assertFalse(u.active)
        assertEquals("the used paw stays used", 2, u.pawFadeIndex)
    }

    @Test
    fun `each clear-out fades the next paw to the left`() {
        val seen = ArrayList<Int>()
        for (paws in listOf(3, 2, 1)) {
            val u = ui(board = stuckBoard(), tray = stuckTray, paws = paws)
            seen += u.pawFadeIndex
        }
        assertEquals(listOf(2, 1, 0), seen)
    }

    // ------------------------------------------------------------------ after the ending

    @Test
    fun `once the game has ended the touch layer grabs nothing, places nothing and the frame loop rests`() {
        val u = ui(board = stuckBoard(), tray = stuckTray, paws = 0)
        u.onFrame(2000)
        assertEquals(BlocksPhase.GAME_OVER, u.session.phase)
        assertFalse(u.active)
        for (i in 0..2) {
            val (x, y) = slotCentre(u, i)
            assertFalse(u.onDown(10L + i, x, y))
        }
        assertFalse(u.tracker.active)
        assertEquals(BlocksUi.Took.NOTHING, u.onPointer(20, 100f, 500f, true, false, false))
        u.onFrame(60_000)
        assertEquals(BlocksPhase.GAME_OVER, u.session.phase)
        assertEquals(0, u.session.score)
    }


    @Test
    fun `mashing the screen after the ending changes nothing and never throws`() {
        val u = ui(board = stuckBoard(), tray = stuckTray, paws = 0)
        u.onFrame(2000)
        val board = u.session.board
        val random = Random(9)
        var frame = 2000L
        repeat(3000) {
            val x = random.nextFloat() * 400f - 20f
            val y = random.nextFloat() * 760f - 30f
            val id = random.nextLong(1, 5)
            when (random.nextInt(5)) {
                0 -> u.onPointer(id, x, y, true, false, false)
                1 -> u.onPointer(id, x, y, true, true, false)
                2 -> u.onPointer(id, x, y, false, true, random.nextBoolean())
                3 -> u.cancelDrag()
                else -> { frame += random.nextLong(1, 400); u.onFrame(frame) }
            }
        }
        assertEquals(BlocksPhase.GAME_OVER, u.session.phase)
        assertEquals(board, u.session.board)
        assertEquals(0, u.session.score)
        assertEquals(0, u.session.paws)
        assertNull(u.session.place(0, 0, 0, frame))
    }
}
