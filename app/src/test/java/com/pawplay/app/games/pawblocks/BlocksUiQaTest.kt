package com.pawplay.app.games.pawblocks

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.hypot
import kotlin.random.Random

/**
 * QA's checks through the touch layer of Paw Blocks (`BlocksUi.onPointer`, the same call the screen makes): "a tap is
 * not a drop" at its exact edge, real drags that are short or wandering, a mashing child, and a finger that goes missing
 * while the game is waiting to end (story 72).
 */
class BlocksUiQaTest {
    private val dot = BlockShapes.dot
    private fun shape(id: String) = BlockShapes.byId.getValue(id)

    private fun ui(
        board: Board? = null,
        tray: List<BlockShape?> = listOf(dot, shape("bar2h"), shape("bar2v")),
        paws: Int = 3,
    ): BlocksUi = BlocksUi(BlocksSession(Random(3), startBoard = board, startTray = tray, startPaws = paws)).also { it.onFrame(1000) }

    private fun slotCentre(u: BlocksUi, i: Int) = (u.layout.slotLeftOf(i) + u.layout.slotWidth / 2f) to (u.layout.slotTop + u.layout.slotHeight / 2f)

    /** Finger down on slot [slot]'s centre, moved by ([dx], [dy]) in one step, lifted there. Returns how many cells the board holds afterwards. */
    private fun gesture(u: BlocksUi, slot: Int, dx: Float, dy: Float, id: Long = 1): Int {
        val (sx, sy) = slotCentre(u, slot)
        u.onPointer(id, sx, sy, true, false, false)
        if (dx != 0f || dy != 0f) u.onPointer(id, sx + dx, sy + dy, true, true, false)
        u.onPointer(id, sx + dx, sy + dy, false, true, false)
        return u.session.board.filledCount
    }

    // ------------------------------------------------------------------ "a tap is not a drop" at its edge

    @Test
    fun `travel just under 12dp is a tap and goes home, 12dp or more is a drag and lands`() {
        for (travel in listOf(0f, 0.5f, 5f, 8f, 11.9f)) {
            val u = ui()
            assertEquals("travel $travel should be a tap", 0, gesture(u, 1, 0f, -travel))
            assertEquals(0, u.session.score)
            assertEquals(1, u.returns.size)
        }
        for (travel in listOf(12f, 12.5f, 20f, 40f)) {
            val u = ui()
            assertEquals("travel $travel should be a drag", 2, gesture(u, 1, 0f, -travel))
            assertEquals(3, u.session.score)
        }
    }

    @Test
    fun `sideways and diagonal travel count the same way, and a wobble that ends where it began is still a drag`() {
        // Diagonal: 8.4 + 8.4 = 11.88 (tap), 8.6 + 8.6 = 12.16 (drag).
        assertEquals(0, gesture(ui(), 1, 8.4f, -8.4f))
        assertEquals(2, gesture(ui(), 1, 8.6f, -8.6f))
        assertTrue(hypot(8.4f, 8.4f) < Snap.TAP_SLOP_DP && hypot(8.6f, 8.6f) >= Snap.TAP_SLOP_DP)
        // Out 40dp and back to the start: it was carried, so the drop is judged where it ends (the tray is under the board).
        val u = ui()
        val (sx, sy) = slotCentre(u, 1)
        u.onPointer(1, sx, sy, true, false, false)
        u.onPointer(1, sx, sy - 40f, true, true, false)
        u.onPointer(1, sx, sy, true, true, false)
        u.onPointer(1, sx, sy, false, true, false)
        assertEquals("a carried block is not a tap", 2, u.session.board.filledCount)
    }

    @Test
    fun `a legitimate long drag to a far corner and a drop over the home button both still behave`() {
        val u = ui()
        val cell = u.layout.cellSize(5f)
        val shape = dot
        val x = u.layout.gridLeft + 4.5f * cell
        val y = u.layout.gridTop + 4.5f * cell + Snap.RIDE_DP // finger so the block is drawn over the last cell
        val (sx, sy) = slotCentre(u, 0)
        u.onPointer(1, sx, sy, true, false, false)
        u.onPointer(1, x, y, true, true, false)
        u.onPointer(1, x, y, false, true, false)
        assertEquals(1, u.session.board.filledCount)
        assertFalse(u.session.board.isEmpty(4, 4))
        // Over the home button: goes home, nothing placed (a wrong drop costs nothing).
        val u2 = ui()
        val (a, b) = slotCentre(u2, 0)
        u2.onPointer(1, a, b, true, false, false)
        u2.onPointer(1, 40f, 40f, true, true, false)
        u2.onPointer(1, 40f, 40f, false, true, false)
        assertEquals(0, u2.session.board.filledCount)
        assertEquals(0, u2.session.score)
        assertEquals(3, u2.session.paws)
        assertEquals(shape, u2.session.slot(0))
    }

    // ------------------------------------------------------------------ mashing

    @Test
    fun `a bored child tapping every tray slot as fast as he can changes nothing`() {
        val u = ui()
        val start = u.session.score
        for (round in 0 until 200) for (slot in 0..2) {
            val (sx, sy) = slotCentre(u, slot)
            val id = round * 3L + slot
            u.onPointer(id, sx, sy, true, false, false)
            u.onPointer(id, sx, sy, false, true, false)
        }
        assertEquals(0, u.session.board.filledCount)
        assertEquals(start, u.session.score)
        assertEquals(3, u.session.paws)
        assertEquals(3, u.session.tray.count { it != null })
    }

    @Test
    fun `random pointer chaos never leaves a block held, so the ending is never blocked for good`() {
        for (seed in 1..40) {
            val rnd = Random(seed)
            // Nothing in this tray fits, no paws: the game ends 1s after the last thing the child did.
            val stuck = Board.from(5) { r, c -> if ((r + c) % 2 == 0) null else Family.DOT }
            val u = ui(board = stuck, tray = listOf(shape("bar2h"), shape("bar2v"), shape("bar3h")), paws = 0)
            val down = HashSet<Long>()
            for (i in 0 until 300) {
                val id = rnd.nextInt(6).toLong()
                val x = rnd.nextFloat() * 380f - 10f
                val y = rnd.nextFloat() * 720f - 10f
                when (rnd.nextInt(5)) {
                    0, 1 -> if (id !in down) { u.onPointer(id, x, y, true, false, false); down += id }
                    2 -> if (id in down) u.onPointer(id, x, y, true, true, false)
                    3 -> if (id in down) { u.onPointer(id, x, y, false, true, false); down -= id }
                    else -> if (id in down) { u.onPointer(id, x, y, false, true, true); down -= id } // a cancel
                }
            }
            // Fingers that just vanished without a lift: the screen calls cancelDrag when nothing is pressed any more.
            u.cancelDrag()
            assertFalse(u.tracker.active)
            u.onFrame(20_000) // a long time later, nothing held
            assertEquals("seed $seed: the game did not end, something is still held", BlocksPhase.GAME_OVER, u.session.phase)
        }
    }

    // ------------------------------------------------------------------ the ending through the touch layer

    @Test
    fun `after the game ends a finger on a tray block grabs nothing and the score cannot change`() {
        val stuck = Board.from(5) { r, c -> if ((r + c) % 2 == 0) null else Family.DOT }
        val u = ui(board = stuck, tray = listOf(shape("bar2h"), shape("bar2v"), shape("bar3h")), paws = 0)
        u.onFrame(5_000)
        assertEquals(BlocksPhase.GAME_OVER, u.session.phase)
        val (sx, sy) = slotCentre(u, 0)
        assertEquals(BlocksUi.Took.NOTHING, u.onPointer(1, sx, sy, true, false, false))
        assertFalse(u.tracker.active)
        u.onPointer(1, sx, sy - 100f, true, true, false)
        u.onPointer(1, sx, sy - 100f, false, true, false)
        assertEquals(0, u.session.score)
        assertEquals(stuck.filledCount, u.session.board.filledCount)
    }

    @Test
    fun `a second finger while one is dragging cannot start another drag or end the first`() {
        val u = ui()
        val (ax, ay) = slotCentre(u, 0)
        val (bx, by) = slotCentre(u, 2)
        assertEquals(BlocksUi.Took.CHANGED, u.onPointer(1, ax, ay, true, false, false))
        assertEquals(BlocksUi.Took.NOTHING, u.onPointer(2, bx, by, true, false, false))
        assertEquals(BlocksUi.Took.NOTHING, u.onPointer(2, bx, by, false, true, false)) // the palm lifts: the drag goes on
        assertTrue(u.tracker.active)
        assertEquals(0, u.tracker.slot)
    }
}
