package com.pawplay.app.games.pawblocks

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Where things go, the snap and ghost maths, and which finger drags (docs/DESIGN-SYSTEM.md; PRD stories 40, 41, 42, 49). */
class PawBlocksLayoutTest {
    private fun shape(id: String) = BlockShapes.byId.getValue(id)
    private fun near(expected: Float, actual: Float, eps: Float = 0.01f) = assertTrue("expected $expected got $actual", kotlin.math.abs(expected - actual) <= eps)

    // ------------------------------------------------------------------ layout

    @Test
    fun `panel grid and tray follow the design numbers on the reference screen`() {
        val l = blocksLayout(360f, 692f)
        near(6f, l.panelLeft); near(132f, l.panelTop); near(348f, l.panelSize)
        near(10f, l.gridLeft); near(136f, l.gridTop); near(340f, l.gridSize)
        near(96f, l.slotWidth); near(112f, l.slotHeight); near(520f, l.slotTop)
        near(24f, l.slotLeftOf(0)); near(132f, l.slotLeftOf(1)); near(240f, l.slotLeftOf(2))
        for (n in 5..9) near(340f / n, l.cellSize(n.toFloat()))
    }

    @Test
    fun `tray cells are 44, 29, 29, 22, 22 and 17 across the six stages`() {
        val l = blocksLayout(360f, 692f)
        assertEquals(listOf(44f, 29f, 29f, 22f, 22f, 17f), (1..6).map { l.trayCell(it) })
    }

    @Test
    fun `on the reference screen the tray sits 40dp under the board and ends at 632`() {
        val l = blocksLayout(360f, 692f)
        assertEquals(632f, l.slotTop + l.slotHeight, 0.01f)
        assertEquals(40f, l.slotTop - l.panelBottom, 0.01f)
    }

    @Test
    fun `the top strip is above the board, in the design's order, and the animal's peek lane is clear of it`() {
        val l = blocksLayout(360f, 692f)
        // Paws 32dp, 8dp apart, from x 92, top 32: x = 92, 132, 172.
        assertEquals(listOf(92f, 132f, 172f), (0..2).map { BlocksLayout.pawLeft(it) })
        assertEquals(32f, BlocksLayout.PAW_SIZE, 0f)
        // Home, then paws, then the score: no overlap, and everything ends above the board.
        val homeRight = BlocksLayout.HOME_INSET + BlocksLayout.HOME_SIZE
        assertTrue(BlocksLayout.pawLeft(0) >= homeRight + 8f)
        val pawsRight = BlocksLayout.pawLeft(2) + BlocksLayout.PAW_SIZE
        assertEquals(204f, pawsRight, 0.01f)
        val scoreRight = 360f - BlocksLayout.SCORE_MARGIN_RIGHT
        assertEquals(340f, scoreRight, 0f)
        assertTrue("six digits fit between the paws and the right margin", scoreRight - pawsRight >= 128f - 0.01f - 8f)
        assertEquals(BlocksLayout.HOME_INSET + BlocksLayout.HOME_SIZE, BlocksLayout.STRIP_BOTTOM, 0f)
        assertTrue(l.panelTop >= BlocksLayout.STRIP_BOTTOM + 8f)
        // The fox's ears (62dp above the panel) reach y 70 on the reference screen: clear of the score digits, which end at y 62.
        assertEquals(70f, l.panelTop - 62f, 0.01f)
        // The top margin never goes up into the strip, however short the window.
        for (h in listOf(400, 480, 520, 568, 640, 692)) assertTrue("h=$h", blocksLayout(360f, h.toFloat()).panelTop >= 96f - 0.01f)
    }

    @Test
    fun `the panel follows the width to 6dp from the sides and never grows past 348`() {
        near(304f + 4f, blocksLayout(320f, 640f).panelSize)
        near(348f, blocksLayout(360f, 740f).panelSize)
        near(348f, blocksLayout(411f, 880f).panelSize)
        near(348f, blocksLayout(600f, 1000f).panelSize)
        // 9x9 cells: 37.8 at 360 wide, 33.3 at 320 wide (nine cells cannot be much bigger on 320dp), and the same on a tall phone.
        assertTrue(blocksLayout(360f, 740f).cellSize(9f) >= 37.7f)
        assertTrue(blocksLayout(320f, 640f).cellSize(9f) >= 33.2f)
        // The board is the last thing to shrink when the window is short.
        val short = blocksLayout(360f, 520f)
        assertTrue("short window cell ${short.cellSize(9f)}", short.cellSize(9f) >= 35f)
        assertEquals(72f, short.slotHeight, 0.01f)
    }

    @Test
    fun `every screen from 320 x 480 to 480 x 1000 fits everything with room to grab`() {
        for (w in listOf(320, 340, 360, 384, 411, 430, 480)) for (h in listOf(480, 520, 568, 640, 692, 740, 800, 880, 1000)) {
            val l = blocksLayout(w.toFloat(), h.toFloat())
            val tag = "${w}x$h"
            // Tray slots: the whole slot is the grab area, at least 72dp, 8dp apart.
            assertTrue("$tag slot width ${l.slotWidth}", l.slotWidth >= 72f)
            assertTrue("$tag slot height ${l.slotHeight}", l.slotHeight >= 72f)
            near(12f, l.slotLeftOf(1) - (l.slotLeftOf(0) + l.slotWidth))
            // Everything is inside the play area.
            assertTrue(tag, l.panelLeft >= 0f && l.panelRight <= w)
            assertTrue(tag, l.slotLeftOf(0) >= 0f && l.slotLeftOf(2) + l.slotWidth <= w + 0.01f)
            assertTrue("$tag bottom", l.slotTop + l.slotHeight <= h - 11.99f)
            // Home, board and tray never overlap.
            val homeBottom = BlocksLayout.HOME_INSET + BlocksLayout.HOME_SIZE
            assertTrue("$tag home vs panel", l.panelTop >= homeBottom + 8f || l.panelLeft >= BlocksLayout.HOME_INSET + BlocksLayout.HOME_SIZE)
            assertTrue("$tag board vs tray", l.slotTop >= l.panelBottom + 11.99f)
            // On a tall enough phone a 9x9 cell is about 36dp or more at 360 wide, and about 33dp at 320 wide.
            if (w >= 360 && h >= 568) assertTrue("$tag cell ${l.cellSize(9f)}", l.cellSize(9f) >= 36f)
            if (w >= 320 && h >= 568) assertTrue("$tag cell ${l.cellSize(9f)}", l.cellSize(9f) >= 33f)
            // The outer tray slots stay off the back-gesture strip at the screen sides on phones 360 wide or more.
            if (w >= 360) assertTrue("$tag tray inset ${l.slotLeftOf(0)}", l.slotLeftOf(0) >= 24f - 0.01f && w - (l.slotLeftOf(2) + l.slotWidth) >= 24f - 0.01f)
            if (w >= 320) assertTrue("$tag tray inset ${l.slotLeftOf(0)}", l.slotLeftOf(0) >= 24f - 0.01f)
            // Tray blocks are still a picture: 12dp or more on every real phone (was 14 before the strip pushed the board down;
            // stage 6 on a 320 x 568 phone is 13dp, and the colour and silhouette carry it).
            if (h >= 568) for (stage in 1..6) assertTrue("$tag tray cell stage $stage = ${l.trayCell(stage)}", l.trayCell(stage) >= 12f)
            // Every tray block of every stage fits in its slot.
            for (stage in 1..6) for (s in BlocksRamp.shapesFor(stage)) {
                assertTrue("$tag ${s.id}", s.width * l.trayCell(stage) <= l.slotWidth && s.height * l.trayCell(stage) <= l.slotHeight)
            }
        }
    }

    @Test
    fun `absurd or zero windows still give finite numbers`() {
        for ((w, h) in listOf(0 to 0, 1 to 1, 2 to 800, 400 to 1, 5000 to 5000)) {
            val l = blocksLayout(w.toFloat(), h.toFloat())
            for (v in listOf(l.panelLeft, l.panelTop, l.panelSize, l.slotTop, l.slotWidth, l.slotHeight, l.gridSize, l.trayCell(6))) assertTrue("${w}x$h $v", v.isFinite())
            assertTrue(l.panelSize > 0f)
        }
    }

    @Test
    fun `slot hits use the whole slot, so a dot is as easy to grab as a bar`() {
        val l = blocksLayout(360f, 692f)
        for (i in 0 until 3) {
            assertEquals(i, l.slotAt(l.slotLeftOf(i) + 1f, l.slotTop + 1f))
            assertEquals(i, l.slotAt(l.slotLeftOf(i) + l.slotWidth - 1f, l.slotTop + l.slotHeight - 1f))
            assertEquals(i, l.slotAt(l.slotLeftOf(i) + l.slotWidth / 2, l.slotTop + l.slotHeight / 2))
        }
        assertEquals(-1, l.slotAt(l.slotLeftOf(0) + l.slotWidth + 4f, l.slotTop + 10f)) // in the 8dp gap
        assertEquals(-1, l.slotAt(100f, l.slotTop - 2f))
        assertEquals(-1, l.slotAt(100f, 300f)) // on the board
        assertTrue(l.overHome(48f, 48f))
        assertFalse(l.overHome(100f, 48f))
    }

    // ------------------------------------------------------------------ snap and ghost

    private val l9 = blocksLayout(360f, 692f)
    private fun fingerFor(shape: BlockShape, cell: Float, topLeftX: Float, topLeftY: Float) =
        (topLeftX + shape.width * cell / 2f) to (topLeftY + Snap.RIDE_DP + shape.height * cell)

    @Test
    fun `the block rides 64dp above the fingertip measured from its bottom edge`() {
        for (id in listOf("dot", "bar5v", "rectH", "sq")) {
            val s = shape(id)
            val cell = 36f
            val top = Snap.blockTop(s, cell, 500f)
            near(500f - 64f, top + s.height * cell)
            near(300f - s.width * cell / 2f, Snap.blockLeft(s, cell, 300f))
        }
    }

    @Test
    fun `snap reach is one cell and never under 40dp`() {
        near(64f, Snap.reach(64f))
        near(53.33f, Snap.reach(320f / 6), 0.01f)
        near(40f, Snap.reach(35.56f))
        near(40f, Snap.reach(40f))
        near(45.71f, Snap.reach(320f / 7), 0.01f)
        for (n in 5..9) assertTrue(Snap.reach(320f / n) >= 40f)
    }

    /** A board where only (row, col) is empty, so a snap either finds that spot or nothing. */
    private fun oneHole(n: Int, row: Int, col: Int) = Board.from(n) { r, c -> if (r == row && c == col) null else Family.BAR3 }

    @Test
    fun `it snaps to a legal spot within one cell and not beyond, on every board size`() {
        for (n in 5..9) {
            val cell = 320f / n
            val board = oneHole(n, n - 2, 1)
            val reach = maxOf(cell, 40f)
            val spotX = l9.gridLeft + 1 * cell
            val spotY = l9.gridTop + (n - 2) * cell
            for (dx in listOf(-1f, 1f)) for (frac in listOf(0f, 0.5f, 0.95f)) {
                val (fx, fy) = fingerFor(BlockShapes.dot, cell, spotX + dx * reach * frac, spotY)
                assertEquals("n=$n frac=$frac", Spot(n - 2, 1), Snap.spot(board, BlockShapes.dot, cell, l9.gridLeft, l9.gridTop, fx, fy))
            }
            val (fx, fy) = fingerFor(BlockShapes.dot, cell, spotX + reach + 1f, spotY)
            assertNull("n=$n beyond reach", Snap.spot(board, BlockShapes.dot, cell, l9.gridLeft, l9.gridTop, fx, fy))
            val (gx, gy) = fingerFor(BlockShapes.dot, cell, spotX, spotY - reach - 1f)
            assertNull("n=$n above reach", Snap.spot(board, BlockShapes.dot, cell, l9.gridLeft, l9.gridTop, gx, gy))
        }
    }

    @Test
    fun `on the 9x9 board forgiveness is 40dp, more than one 35_6dp cell`() {
        val cell = 320f / 9
        val board = oneHole(9, 4, 4)
        val spotX = l9.gridLeft + 4 * cell
        val spotY = l9.gridTop + 4 * cell
        val (nx, ny) = fingerFor(BlockShapes.dot, cell, spotX + 39.5f, spotY)
        assertNotNull(Snap.spot(board, BlockShapes.dot, cell, l9.gridLeft, l9.gridTop, nx, ny))
        val (fx, fy) = fingerFor(BlockShapes.dot, cell, spotX + 40.5f, spotY)
        assertNull(Snap.spot(board, BlockShapes.dot, cell, l9.gridLeft, l9.gridTop, fx, fy))
    }

    @Test
    fun `the nearest legal spot wins`() {
        val board = Board.from(5) { r, c -> if (r == 1 && c in 0..1) null else if (r == 1 && c == 3) null else if (r == 1) Family.BAR3 else null }
        val cell = 64f
        // Holes at (1,0), (1,1) and (1,3) plus everything else empty: drawn exactly over (2,3) the nearest is (2,3).
        val (fx, fy) = fingerFor(BlockShapes.dot, cell, l9.gridLeft + 3 * cell, l9.gridTop + 2 * cell)
        assertEquals(Spot(2, 3), Snap.spot(board, BlockShapes.dot, cell, l9.gridLeft, l9.gridTop, fx, fy))
    }

    @Test
    fun `two equally near spots go to the one nearer the fingertip`() {
        // Only (1,2) and (3,2) are empty; the block is drawn exactly between them.
        val board = Board.from(5) { r, c -> if (c == 2 && (r == 1 || r == 3)) null else Family.BAR3 }
        val cell = 64f
        val (fx, fy) = fingerFor(BlockShapes.dot, cell, l9.gridLeft + 2 * cell, l9.gridTop + 2 * cell)
        assertEquals(Spot(3, 2), Snap.spot(board, BlockShapes.dot, cell, l9.gridLeft, l9.gridTop, fx, fy))
    }

    @Test
    fun `it never snaps onto filled cells or off the board`() {
        val board = Board.empty(5).place(shape("bar3h"), 0, 0)
        val cell = 64f
        for (fingerX in 0..360 step 12) for (fingerY in 0..692 step 12) {
            val spot = Snap.spot(board, shape("bar3h"), cell, l9.gridLeft, l9.gridTop, fingerX.toFloat(), fingerY.toFloat()) ?: continue
            assertTrue(board.canPlace(shape("bar3h"), spot))
        }
    }

    @Test
    fun `the ghost and the drop agree, and an off-screen finger is never a drop`() {
        val board = Board.empty(5)
        val cell = l9.cellSize(5f)
        val s = shape("bar2h")
        val (fx, fy) = fingerFor(s, cell, l9.gridLeft + cell, l9.gridTop + cell)
        assertEquals(Spot(1, 1), l9.dropSpot(board, s, cell, fx, fy))
        assertNull(l9.dropSpot(board, s, cell, -1f, fy))
        assertNull(l9.dropSpot(board, s, cell, fx, -1f))
        assertNull(l9.dropSpot(board, s, cell, 361f, fy))
        assertNull(l9.dropSpot(board, s, cell, fx, 693f))
        assertNull(l9.dropSpot(board, s, cell, Float.NaN, fy))
        assertNull(l9.dropSpot(board, s, cell, fx, Float.POSITIVE_INFINITY))
        // Over the home button: illegal, even though the board is empty.
        assertNull(l9.dropSpot(board, s, cell, 48f, 48f))
    }

    @Test
    fun `a drop over the tray or far from any legal spot is illegal`() {
        val board = Board.empty(5)
        val cell = l9.cellSize(5f)
        assertNull(l9.dropSpot(board, shape("bar2h"), cell, 180f, 600f))
    }

    // ------------------------------------------------------------------ first finger only

    @Test
    fun `only the first finger that grabs a block is followed`() {
        val d = DragTracker()
        assertTrue(d.down(1, 50f, 550f, slotHit = 0, hasBlock = true))
        assertFalse("a second finger on another block", d.down(2, 180f, 550f, slotHit = 1, hasBlock = true))
        assertFalse("a second finger anywhere", d.down(3, 10f, 10f, slotHit = -1, hasBlock = false))
        assertFalse(d.move(2, 200f, 300f))
        assertTrue(d.move(1, 60f, 500f))
        assertEquals(60f, d.x); assertEquals(500f, d.y); assertEquals(0, d.slot)
        assertNull("another finger lifting does nothing", d.up(2, 0f, 0f))
        assertTrue(d.active)
        val ended = d.up(1, 70f, 400f)!!
        assertEquals(0, ended.slot); assertEquals(70f, ended.x); assertEquals(400f, ended.y)
        assertFalse(d.active)
    }

    @Test
    fun `a finger that is already down does not start a drag when the first lets go`() {
        val d = DragTracker()
        assertTrue(d.down(1, 50f, 550f, 0, true))
        assertFalse(d.down(2, 180f, 550f, 1, true))
        d.up(1, 50f, 400f)
        // Finger 2 is still down, but its down event has passed: moves and lifts of it are nothing.
        assertFalse(d.move(2, 180f, 500f))
        assertNull(d.up(2, 180f, 500f))
        assertFalse(d.active)
        // A fresh touch on a block drags again.
        assertTrue(d.down(4, 180f, 550f, 1, true))
    }

    @Test
    fun `a finger on empty space or an empty slot never holds the job, so a resting palm cannot block play`() {
        val d = DragTracker()
        assertFalse(d.down(1, 180f, 200f, slotHit = -1, hasBlock = false))
        assertFalse(d.down(2, 180f, 550f, slotHit = 1, hasBlock = false))
        assertFalse(d.active)
        assertTrue(d.down(3, 60f, 550f, slotHit = 0, hasBlock = true))
        assertEquals(0, d.slot)
    }

    @Test
    fun `a cancelled or vanished drag ends once and sends the block home`() {
        val d = DragTracker()
        assertNull(d.cancel())
        d.down(1, 60f, 550f, 2, true)
        d.move(1, 90f, 300f)
        val ended = d.cancel()!!
        assertEquals(2, ended.slot); assertEquals(90f, ended.x); assertEquals(300f, ended.y)
        assertNull(d.cancel())
        assertNull(d.up(1, 90f, 300f))
    }
}
