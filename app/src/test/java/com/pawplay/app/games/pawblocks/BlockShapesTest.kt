package com.pawplay.app.games.pawblocks

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.pow

/** The sixteen shapes, the eight families, the ramp table and the colour ladder (docs/DESIGN-SYSTEM.md). */
class BlockShapesTest {

    private fun cells(id: String) = BlockShapes.byId.getValue(id).cells.map { it.col to it.row }.sortedWith(compareBy({ it.second }, { it.first }))
    private fun cellsOf(vararg c: Pair<Int, Int>) = c.toList().sortedWith(compareBy({ it.second }, { it.first }))

    @Test
    fun `there are sixteen shapes in eight families with unique ids`() {
        assertEquals(16, BlockShapes.all.size)
        assertEquals(16, BlockShapes.all.map { it.id }.toSet().size)
        assertEquals(8, BlockShapes.all.map { it.family }.toSet().size)
        assertEquals(Family.values().toSet(), BlockShapes.all.map { it.family }.toSet())
    }

    @Test
    fun `every shape is exactly the cells the design gives`() {
        assertEquals(cellsOf(0 to 0), cells("dot"))
        assertEquals(cellsOf(0 to 0, 1 to 0), cells("bar2h"))
        assertEquals(cellsOf(0 to 0, 0 to 1), cells("bar2v"))
        assertEquals(cellsOf(0 to 0, 1 to 0, 2 to 0), cells("bar3h"))
        assertEquals(cellsOf(0 to 0, 0 to 1, 0 to 2), cells("bar3v"))
        assertEquals(cellsOf(0 to 0, 1 to 0, 2 to 0, 3 to 0), cells("bar4h"))
        assertEquals(cellsOf(0 to 0, 0 to 1, 0 to 2, 0 to 3), cells("bar4v"))
        assertEquals(cellsOf(0 to 0, 1 to 0, 2 to 0, 3 to 0, 4 to 0), cells("bar5h"))
        assertEquals(cellsOf(0 to 0, 0 to 1, 0 to 2, 0 to 3, 0 to 4), cells("bar5v"))
        assertEquals(cellsOf(0 to 0, 1 to 0, 0 to 1, 1 to 1), cells("sq"))
        assertEquals(cellsOf(0 to 0, 1 to 0, 0 to 1), cells("cTL"))
        assertEquals(cellsOf(0 to 0, 1 to 0, 1 to 1), cells("cTR"))
        assertEquals(cellsOf(1 to 0, 0 to 1, 1 to 1), cells("cBR"))
        assertEquals(cellsOf(0 to 0, 0 to 1, 1 to 1), cells("cBL"))
        assertEquals(cellsOf(0 to 0, 1 to 0, 0 to 1, 1 to 1, 0 to 2, 1 to 2), cells("rectV"))
        assertEquals(cellsOf(0 to 0, 1 to 0, 2 to 0, 0 to 1, 1 to 1, 2 to 1), cells("rectH"))
    }

    @Test
    fun `width and height come from the cells, and the four corners are four different turns`() {
        val s = BlockShapes.byId
        assertEquals(1 to 1, s.getValue("dot").width to s.getValue("dot").height)
        assertEquals(5 to 1, s.getValue("bar5h").width to s.getValue("bar5h").height)
        assertEquals(1 to 5, s.getValue("bar5v").width to s.getValue("bar5v").height)
        assertEquals(2 to 3, s.getValue("rectV").width to s.getValue("rectV").height)
        assertEquals(3 to 2, s.getValue("rectH").width to s.getValue("rectH").height)
        val corners = BlockShapes.all.filter { it.family == Family.CORNER }
        assertEquals(4, corners.size)
        assertEquals(4, corners.map { c -> c.cells.toSet() }.toSet().size)
        assertTrue(corners.all { it.size == 3 && it.width == 2 && it.height == 2 })
        // Each shape is cut from a full grid of its own size, with no duplicate cells.
        for (shape in BlockShapes.all) assertEquals(shape.id, shape.size, shape.cells.toSet().size)
        assertEquals(BlockShapes.all.map { it.size }.sorted(), listOf(1, 2, 2, 3, 3, 3, 3, 3, 3, 4, 4, 4, 5, 5, 6, 6))
    }

    @Test
    fun `bars come both ways and corners in four turns as separate shapes, so nothing needs rotating`() {
        val sizes = BlockShapes.all.groupBy { it.family }.mapValues { it.value.size }
        assertEquals(
            mapOf(
                Family.DOT to 1, Family.BAR2 to 2, Family.BAR3 to 2, Family.SQUARE to 1,
                Family.CORNER to 4, Family.BAR4 to 2, Family.RECT to 2, Family.BAR5 to 2,
            ),
            sizes,
        )
    }

    @Test
    fun `blocks on offer per stage match the ramp`() {
        fun ids(stage: Int) = BlocksRamp.shapesFor(stage).map { it.id }.toSet()
        assertEquals(setOf("dot", "bar2h", "bar2v"), ids(1))
        assertEquals(ids(1) + setOf("bar3h", "bar3v"), ids(2))
        assertEquals(ids(2) + setOf("sq", "cTL", "cTR", "cBR", "cBL"), ids(3))
        assertEquals(ids(3) + setOf("bar4h", "bar4v"), ids(4))
        assertEquals(ids(4) + setOf("rectV", "rectH"), ids(5))
        assertEquals(ids(5) + setOf("bar5h", "bar5v"), ids(6))
        assertEquals(16, ids(6).size)
        for (stage in 1..6) assertTrue("dots stay in every stage", BlockShapes.dot in BlocksRamp.shapesFor(stage))
    }

    @Test
    fun `the ramp advances after 3, 8, 15, 24 and 35 clears and the board grows 5 5 6 7 8 9`() {
        val expected = mapOf(0 to 1, 1 to 1, 2 to 1, 3 to 2, 7 to 2, 8 to 3, 14 to 3, 15 to 4, 23 to 4, 24 to 5, 34 to 5, 35 to 6, 36 to 6, 1000 to 6)
        for ((clears, stage) in expected) assertEquals("clears $clears", stage, BlocksRamp.stageFor(clears))
        assertEquals(listOf(5, 5, 6, 7, 8, 9), (1..6).map { BlocksRamp.boardSizeFor(it) })
        assertEquals(6, BlocksRamp.MAX_STAGE)
        // Never beyond the cap, whatever is asked.
        assertEquals(9, BlocksRamp.boardSizeFor(99))
        assertEquals(5, BlocksRamp.boardSizeFor(0))
    }

    @Test
    fun `the clear-out takes a third of the height rounded up`() {
        assertEquals(listOf(2, 2, 3, 3, 3), (5..9).map { BlocksRamp.clearOutRowCount(it) })
    }

    @Test
    fun `critters rotate through the six in a fixed order`() {
        assertEquals(listOf(0, 1, 2, 3, 4, 5, 0, 1), (1..8).map { BlocksRamp.critterIndex(it) })
    }

    // ------------------------------------------------------------------ colour ladder and marks

    private fun linear(v: Int): Double = (v / 255.0).let { if (it <= 0.04045) it / 12.92 else ((it + 0.055) / 1.055).pow(2.4) }
    private fun luminance(argb: Long): Double =
        0.2126 * linear(((argb shr 16) and 255).toInt()) + 0.7152 * linear(((argb shr 8) and 255).toInt()) + 0.0722 * linear((argb and 255).toInt())
    private fun ratio(a: Long, b: Long): Double = luminance(a).let { la -> luminance(b).let { lb -> (maxOf(la, lb) + 0.05) / (minOf(la, lb) + 0.05) } }

    @Test
    fun `every pair of the eight family colours differs by at least 1_3 in luminance ratio`() {
        val families = Family.values()
        for (i in families.indices) for (j in i + 1 until families.size) {
            val r = ratio(families[i].colour, families[j].colour)
            assertTrue("${families[i]} vs ${families[j]} only $r", r >= 1.3)
        }
    }

    @Test
    fun `the ladder runs light to dark in the order families arrive, each step at least 1_3`() {
        val families = Family.values()
        for (i in 0 until families.size - 1) {
            assertTrue("${families[i]} must be lighter than ${families[i + 1]}", luminance(families[i].colour) > luminance(families[i + 1].colour))
            assertTrue("step ${families[i]} to ${families[i + 1]}", ratio(families[i].colour, families[i + 1].colour) >= 1.3)
            assertTrue("families arrive in stage order", families[i].stage <= families[i + 1].stage)
        }
    }

    @Test
    fun `every mark is at least 4 to 1 against its own block and no two families share a mark`() {
        for (f in Family.values()) assertTrue("${f.name} mark ${ratio(f.colour, f.markColour)}", ratio(f.colour, f.markColour) >= 4.0)
        assertEquals(8, Family.values().map { it.mark }.toSet().size)
        assertEquals(8, Family.values().map { it.colour }.toSet().size)
    }

    @Test
    fun `family colours are the design hexes`() {
        val hex = Family.values().associate { it.name to "#%06X".format(it.colour and 0xFFFFFF) }
        assertEquals(
            mapOf(
                "DOT" to "#FFD23F", "BAR2" to "#FF9ACB", "BAR3" to "#28A9E3", "SQUARE" to "#E85A10",
                "CORNER" to "#1A8344", "BAR4" to "#83532B", "RECT" to "#5A31A6", "BAR5" to "#223475",
            ),
            hex,
        )
        assertTrue(Family.values().all { (it.colour ushr 24) == 0xFFL }) // fully opaque
        assertFalse(Family.values().any { it.colour == it.markColour })
    }
}
