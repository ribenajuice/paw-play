package com.pawplay.app.games.pawblocks

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

/**
 * Follow-ups from QA and review: a system cancel sends the block home, the frame loop wakes only on down and up,
 * fading cells never cover a newly placed block, and one deal spends a bounded search allowance.
 */
class PawBlocksFollowUpTest {
    private fun shape(id: String) = BlockShapes.byId.getValue(id)

    private fun ui(board: Board? = null, tray: List<BlockShape?> = listOf(BlockShapes.dot, shape("bar2h"), shape("bar2v"))): BlocksUi =
        BlocksUi(BlocksSession(Random(3), startBoard = board, startTray = tray)).also { it.onFrame(1000) }

    private fun slotCentre(u: BlocksUi, i: Int) = (u.layout.slotLeftOf(i) + u.layout.slotWidth / 2f) to (u.layout.slotTop + u.layout.slotHeight / 2f)

    private fun fingerFor(u: BlocksUi, shape: BlockShape, row: Int, col: Int): Pair<Float, Float> {
        val cell = u.layout.cellSize(u.session.board.size.toFloat())
        return (u.layout.gridLeft + col * cell + shape.width * cell / 2f) to (u.layout.gridTop + row * cell + shape.height * cell + Snap.RIDE_DP)
    }

    // ------------------------------------------------------------------ cancel is not a lift

    @Test
    fun `a lift over a legal spot places the block, a cancel over the same spot sends it home`() {
        for (cancel in listOf(false, true)) {
            val u = ui()
            val (sx, sy) = slotCentre(u, 1)
            val (fx, fy) = fingerFor(u, shape("bar2h"), 2, 1)
            assertEquals(BlocksUi.Took.CHANGED, u.onPointer(1, sx, sy, pressed = true, previousPressed = false, consumed = false))
            assertEquals(BlocksUi.Took.FOLLOWED, u.onPointer(1, fx, fy, pressed = true, previousPressed = true, consumed = false))
            // Compose delivers a cancelled touch as an "up" that is already consumed.
            assertEquals(BlocksUi.Took.CHANGED, u.onPointer(1, fx, fy, pressed = false, previousPressed = true, consumed = cancel))
            if (cancel) {
                assertEquals("cancel: nothing placed", 0, u.session.board.filledCount)
                assertNotNull("cancel: the block is still in its slot", u.session.slot(1))
                assertEquals(1, u.returns.size)
            } else {
                assertEquals("lift: placed", 2, u.session.board.filledCount)
                assertEquals(null, u.session.slot(1))
                assertTrue(u.returns.isEmpty())
            }
            assertFalse(u.tracker.active)
        }
    }

    @Test
    fun `the cancel of a finger that is not dragging changes nothing`() {
        val u = ui()
        val (sx, sy) = slotCentre(u, 0)
        u.onPointer(1, sx, sy, pressed = true, previousPressed = false, consumed = false)
        assertEquals(BlocksUi.Took.NOTHING, u.onPointer(2, 100f, 300f, pressed = false, previousPressed = true, consumed = true))
        assertTrue("the first finger still drags", u.tracker.active)
    }

    // ------------------------------------------------------------------ the frame loop wakes only when a drag starts or ends

    @Test
    fun `moves are followed but do not ask for a wake-up, only down and up do`() {
        val u = ui()
        val (sx, sy) = slotCentre(u, 0)
        assertEquals(BlocksUi.Took.CHANGED, u.onPointer(1, sx, sy, true, false, false))
        repeat(20) { assertEquals(BlocksUi.Took.FOLLOWED, u.onPointer(1, sx + it, sy - it, true, true, false)) }
        assertEquals(BlocksUi.Took.NOTHING, u.onPointer(2, 10f, 10f, true, false, false)) // a second finger: ignored
        assertEquals(BlocksUi.Took.NOTHING, u.onPointer(2, 20f, 20f, true, true, false))
        assertEquals(BlocksUi.Took.CHANGED, u.onPointer(1, sx, sy, false, true, false))
    }

    // ------------------------------------------------------------------ fading cells

    @Test
    fun `a block dropped into a line that is still fading is not covered by the old cells`() {
        val board = Board.from(5) { r, c -> if (r == 0 && c != 0) Family.BAR3 else null }
        val u = ui(board, listOf(BlockShapes.dot, BlockShapes.dot, BlockShapes.dot))
        val (sx, sy) = slotCentre(u, 0)
        val (fx, fy) = fingerFor(u, BlockShapes.dot, 0, 0)
        u.onPointer(1, sx, sy, true, false, false)
        u.onPointer(1, fx, fy, true, true, false)
        u.onPointer(1, fx, fy, false, true, false)
        assertEquals("the row cleared", 1, u.session.clears)
        assertEquals(1, u.lines.size)
        val fading = u.lines[0].cells
        assertEquals(5, fading.size)
        assertTrue("every fading cell shows while its place is empty", fading.all { u.fadingCellVisible(it) })
        // A new dot lands in the middle of the fading row, straight away.
        assertNotNull(u.session.place(1, 0, 2, 1100))
        for (c in fading) assertEquals("cell ${c.col}", c.col != 2, u.fadingCellVisible(c))
        // Once the line's cells are gone from the list, nothing is left to draw over it.
        u.onFrame(1000 + 1500)
        assertTrue(u.lines.isEmpty())
    }

    // ------------------------------------------------------------------ the dealer's allowance

    private fun packedBoard(size: Int, stage: Int, random: Random): Board {
        var board = Board.empty(size)
        val set = BlocksRamp.shapesFor(stage).filter { it.size > 1 }
        repeat(400) {
            val fits = set.filter { board.hasSpot(it) }
            if (fits.isEmpty()) return board
            val s = fits[random.nextInt(fits.size)]
            val spots = board.spots(s)
            val spot = spots[random.nextInt(spots.size)]
            board = board.placeAndClear(s, spot.row, spot.col).board
        }
        return board
    }

    @Test
    fun `one deal never spends more than its whole allowance, on any stage`() {
        var worst = 0
        for (stage in 1..6) {
            val size = BlocksRamp.boardSizeFor(stage)
            for (seed in 0 until 150) {
                val random = Random(seed * 13 + stage)
                var board = Board.empty(size)
                repeat(seed % 40) {
                    val set = BlocksRamp.shapesFor(stage)
                    val s = set.shuffled(random).firstOrNull { board.hasSpot(it) } ?: return@repeat
                    val spots = board.spots(s)
                    val spot = spots[random.nextInt(spots.size)]
                    board = board.placeAndClear(s, spot.row, spot.col).board
                }
                for (b in listOf(board, packedBoard(size, stage, random))) {
                    val budget = TrayDealer.Budget()
                    val trio = TrayDealer.deal(b, stage, null, random, budget)
                    assertTrue(budget.used <= TrayDealer.TOTAL_BUDGET)
                    assertTrue(trio.any { b.hasSpot(it) })
                    worst = maxOf(worst, budget.used)
                }
            }
        }
        println("dealer: most placements one deal searched = $worst of ${TrayDealer.TOTAL_BUDGET}")
    }

    @Test
    fun `the hard guarantee and distinctness hold however little search is allowed`() {
        for (allowance in listOf(0, 1, 5, 50, 500)) {
            for (stage in listOf(1, 3, 4, 6)) {
                val size = BlocksRamp.boardSizeFor(stage)
                for (seed in 0 until 40) {
                    val random = Random(seed + allowance)
                    val board = packedBoard(size, stage, random)
                    val previous = BlocksRamp.shapesFor(stage).shuffled(random).take(3).map { it.id }
                    val trio = TrayDealer.deal(board, stage, previous, random, TrayDealer.Budget(allowance))
                    assertEquals(3, trio.size)
                    assertTrue("allowance $allowance stage $stage seed $seed: nothing fits", trio.any { board.hasSpot(it) })
                    assertTrue(trio.all { it in BlocksRamp.shapesFor(stage) })
                }
            }
        }
    }

    @Test
    fun `slot access and the per-stage sets agree with the copies they replace`() {
        val s = BlocksSession(Random(1), startTray = listOf(BlockShapes.dot, null, shape("bar3h")))
        for (i in 0 until 3) assertEquals(s.tray[i], s.slot(i))
        assertEquals(null, s.slot(5))
        for (stage in 1..6) {
            assertEquals(BlockShapes.forStage(stage), BlocksRamp.shapesFor(stage))
            assertEquals(BlockShapes.forStage(stage).maxOf { it.width }, BlocksRamp.widestFor(stage))
            assertEquals(BlockShapes.forStage(stage).maxOf { it.height }, BlocksRamp.tallestFor(stage))
        }
    }
}
