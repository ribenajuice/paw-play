package com.pawplay.app.games.pawblocks

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Placement legality, line completion, "nothing falls", and growth (docs/PRD.md stories 41, 43, 46). */
class PawBlocksBoardTest {
    private val dot = BlockShapes.dot
    private fun shape(id: String) = BlockShapes.byId.getValue(id)

    @Test
    fun `an empty board fits a block anywhere it is fully inside`() {
        val b = Board.empty(5)
        assertTrue(b.canPlace(dot, 0, 0))
        assertTrue(b.canPlace(dot, 4, 4))
        assertTrue(b.canPlace(shape("bar3h"), 0, 2))
        assertFalse("hangs off the right", b.canPlace(shape("bar3h"), 0, 3))
        assertFalse("hangs off the bottom", b.canPlace(shape("bar3v"), 3, 0))
        assertFalse(b.canPlace(dot, -1, 0))
        assertFalse(b.canPlace(dot, 0, -1))
        assertFalse(b.canPlace(dot, 5, 0))
        assertFalse(b.canPlace(dot, 0, 5))
        assertEquals(25, b.spots(dot).size)
        assertEquals(4 * 5, b.spots(shape("bar2h")).size)
        assertEquals(15, b.spots(shape("bar3h")).size)
        assertEquals(10, b.spots(shape("bar4h")).size)
        assertEquals(5, b.spots(shape("bar5h")).size)
    }

    @Test
    fun `a block cannot overlap a filled cell but may touch one`() {
        val b = Board.empty(5).place(shape("bar2h"), 2, 1)
        assertFalse(b.canPlace(dot, 2, 1))
        assertFalse(b.canPlace(dot, 2, 2))
        assertFalse(b.canPlace(shape("bar2v"), 1, 2))
        assertTrue(b.canPlace(dot, 2, 0))
        assertTrue(b.canPlace(dot, 2, 3))
        assertTrue(b.canPlace(shape("bar2h"), 3, 1))
        assertEquals(Family.BAR2, b.familyAt(2, 1))
        assertEquals(Family.BAR2, b.familyAt(2, 2))
        assertNull(b.familyAt(2, 3))
        assertNull("off the board is nothing", b.familyAt(9, 9))
    }

    @Test
    fun `boards are immutable, placing returns a new board`() {
        val a = Board.empty(5)
        val b = a.place(dot, 0, 0)
        assertEquals(0, a.filledCount)
        assertEquals(1, b.filledCount)
        assertTrue(a != b)
        assertEquals(b, Board.empty(5).place(dot, 0, 0))
    }

    @Test(expected = IllegalArgumentException::class)
    fun `placing where it does not fit is refused`() {
        Board.empty(5).place(shape("bar3h"), 0, 4)
    }

    @Test
    fun `a full row completes and goes, and only that row`() {
        val b = Board.from(5) { r, c -> if (r == 2 && c != 4) Family.BAR3 else if (r == 1 && c == 1) Family.SQUARE else null }
        val p = b.placeAndClear(dot, 2, 4)
        assertTrue(p.cleared)
        assertEquals(listOf(2), p.rows)
        assertEquals(emptyList<Int>(), p.cols)
        assertEquals(5, p.removed.size)
        assertEquals((0 until 5).map { 2 to it }, p.removed.map { it.row to it.col }.sortedBy { it.second })
        assertEquals(1, p.board.filledCount)
        assertEquals(Family.SQUARE, p.board.familyAt(1, 1))
    }

    @Test
    fun `a full column completes too`() {
        val b = Board.from(6) { r, c -> if (c == 3 && r != 5) Family.BAR2 else null }
        val p = b.placeAndClear(dot, 5, 3)
        assertEquals(listOf(3), p.cols)
        assertEquals(emptyList<Int>(), p.rows)
        assertEquals(0, p.board.filledCount)
    }

    @Test
    fun `several lines from one placement are removed together and count as one clear`() {
        // Row 0 and column 0 are both missing only the corner; a dot in the corner completes both.
        val b = Board.from(5) { r, c -> if ((r == 0 || c == 0) && !(r == 0 && c == 0)) Family.BAR3 else if (r == 3 && c == 3) Family.SQUARE else null }
        val p = b.placeAndClear(dot, 0, 0)
        assertEquals(listOf(0), p.rows)
        assertEquals(listOf(0), p.cols)
        assertEquals(9, p.removed.size) // the corner cell is counted once
        assertEquals(9, p.removed.map { it.row to it.col }.toSet().size)
        assertTrue(p.cleared)
        assertEquals(Family.SQUARE, p.board.familyAt(3, 3))
        assertEquals(1, p.board.filledCount)
        // Two rows at once with one 2x? block.
        val two = Board.from(5) { r, c -> if ((r == 0 || r == 1) && c >= 2) Family.BAR2 else null }
        val q = two.placeAndClear(shape("sq"), 0, 0)
        assertEquals(listOf(0, 1), q.rows)
        assertEquals(0, q.board.filledCount)
    }

    @Test
    fun `nothing falls or shifts when a line goes`() {
        // Cells above and below the cleared row 2 keep their exact rows and columns.
        val b = Board.from(5) { r, c ->
            when {
                r == 2 && c != 0 -> Family.BAR3
                r == 0 && c == 1 -> Family.DOT
                r == 1 && c == 4 -> Family.SQUARE
                r == 3 && c == 2 -> Family.CORNER
                r == 4 && c == 0 -> Family.BAR4
                else -> null
            }
        }
        val after = b.placeAndClear(dot, 2, 0).board
        assertEquals(Family.DOT, after.familyAt(0, 1))
        assertEquals(Family.SQUARE, after.familyAt(1, 4))
        assertEquals(Family.CORNER, after.familyAt(3, 2))
        assertEquals(Family.BAR4, after.familyAt(4, 0))
        assertEquals(4, after.filledCount)
        for (c in 0 until 5) assertNull(after.familyAt(2, c))
    }

    @Test
    fun `a board with no complete line clears nothing`() {
        val p = Board.empty(5).placeAndClear(shape("bar4h"), 0, 0)
        assertFalse(p.cleared)
        assertEquals(4, p.board.filledCount)
        assertTrue(p.removed.isEmpty())
    }

    @Test
    fun `the whole board is never left full, a placement that fills it clears every line`() {
        val b = Board.from(5) { r, c -> if (r == 4 && c == 4) null else Family.DOT }
        val p = b.placeAndClear(dot, 4, 4)
        assertEquals(5, p.rows.size)
        assertEquals(5, p.cols.size)
        assertEquals(0, p.board.filledCount)
        assertEquals(25, p.removed.size)
    }

    @Test
    fun `growing adds an empty row at the bottom and column at the right and keeps every placed cell`() {
        val b = Board.empty(5).place(shape("sq"), 0, 0).place(shape("bar3v"), 1, 4).place(dot, 4, 4)
        val g = b.grown()
        assertEquals(6, g.size)
        for (r in 0 until 5) for (c in 0 until 5) assertEquals("$r,$c", b.familyAt(r, c), g.familyAt(r, c))
        for (i in 0 until 6) { assertNull(g.familyAt(5, i)); assertNull(g.familyAt(i, 5)) }
        assertEquals(b.filledCount, g.filledCount)
        assertEquals(0, g.completedRows().size + g.completedCols().size)
    }

    @Test
    fun `growing a nearly full line does not complete it`() {
        val b = Board.from(5) { r, _ -> if (r == 0) Family.DOT else null }
        assertEquals(listOf(0), b.completedRows())
        assertEquals(emptyList<Int>(), b.grown().completedRows())
    }

    @Test
    fun `rows can be removed whole and only their cells go`() {
        val b = Board.from(5) { r, c -> if ((r + c) % 2 == 0) Family.BAR2 else null }
        val (after, gone) = b.withoutRows(listOf(0, 3))
        assertEquals(b.rowFill(0) + b.rowFill(3), gone.size)
        for (c in 0 until 5) { assertNull(after.familyAt(0, c)); assertNull(after.familyAt(3, c)) }
        assertEquals(b.familyAt(1, 1), after.familyAt(1, 1))
        assertEquals(b.familyAt(4, 4), after.familyAt(4, 4))
    }

    @Test
    fun `the near-complete finder sees which blocks would finish a line`() {
        val b = Board.from(7) { r, c -> if (r == 3 && c !in 2..4) Family.DOT else null }
        assertTrue(b.canFinishALine(shape("bar3h")))
        assertFalse(b.canFinishALine(shape("bar2h")))
        assertFalse(b.canFinishALine(dot))
        assertFalse(b.canFinishALine(shape("bar3v")))
    }
}
