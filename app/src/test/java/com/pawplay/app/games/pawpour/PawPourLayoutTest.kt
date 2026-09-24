package com.pawplay.app.games.pawpour

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PawPourLayoutTest {

    // A 360x740dp phone: 16dp side margins, minus the exit button row and padding above the board.
    private val availableWidth = 360f - 2 * 16f
    private val availableHeight = 580f

    @Test
    fun `round 1 matches the mockup, 3 columns of 88dp tubes with 64dp bands`() {
        val layout = layoutBoard(tubeCount = 6, capacity = 3, availableWidth, availableHeight)
        assertEquals(88f, layout.tubeWidth, 0.01f)
        assertEquals(64f, layout.bandHeight, 0.01f)
        assertEquals(212f, layout.tubeHeight, 0.01f)
        assertEquals(setOf(16f, 16f + 212f + 32f), layout.positions.map { it.y }.toSet())
    }

    @Test
    fun `the cap round lays out rows of 5 and 4`() {
        val layout = layoutBoard(tubeCount = 9, capacity = 4, availableWidth, availableHeight)
        assertEquals(2, layout.positions.map { it.y }.distinct().size)
        assertEquals(5, layout.positions.count { it.y == layout.positions.first().y })
        assertEquals(56f, layout.tubeWidth, 0.01f)
    }

    @Test
    fun `every ramp round fits on screen with big-enough, well-spaced, non-overlapping tubes`() {
        for (round in 1..8) {
            for ((width, height) in listOf(360f to 580f, 320f to 480f, 412f to 700f)) {
                val spec = specForRound(round)
                val availW = width - 32f
                val layout = layoutBoard(spec.tubeCount, spec.capacity, availW, height)
                val label = "round $round on ${width}x$height"

                assertEquals(label, spec.tubeCount, layout.positions.size)
                assertTrue(label, layout.tubeWidth >= MIN_TUBE_WIDTH)
                assertTrue(label, layout.contentHeight <= height + 0.01f)
                layout.positions.forEach {
                    assertTrue(label, it.x >= -0.01f && it.x + layout.tubeWidth <= availW + 0.01f)
                    assertTrue(label, it.y >= LIFT_HEADROOM - 0.01f && it.y + layout.tubeHeight <= layout.contentHeight + 0.01f)
                }
                for (a in layout.positions.indices) for (b in a + 1 until layout.positions.size) {
                    val p = layout.positions[a]
                    val q = layout.positions[b]
                    val sameRow = p.y == q.y
                    if (sameRow) {
                        assertTrue(label, kotlin.math.abs(p.x - q.x) >= layout.tubeWidth + MIN_GAP_X - 0.01f)
                    } else {
                        assertTrue(label, kotlin.math.abs(p.y - q.y) >= layout.tubeHeight + ROW_GAP - 0.01f)
                    }
                }
            }
        }
    }

    @Test
    fun `row sizes are even with longer rows first`() {
        assertEquals(listOf(5, 4), rowSizes(9, 2))
        assertEquals(listOf(3, 3), rowSizes(6, 2))
        assertEquals(listOf(4, 3), rowSizes(7, 2))
        assertEquals(listOf(4, 4), rowSizes(8, 2))
    }
}
