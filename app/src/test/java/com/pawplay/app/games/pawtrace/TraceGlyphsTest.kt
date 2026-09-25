package com.pawplay.app.games.pawtrace

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test
import java.io.File
import kotlin.math.abs
import kotlin.math.hypot

class TraceGlyphsTest {

    private val ids = TraceGlyphs.all.map { it.id }

    @Test
    fun `there are 46 glyphs with unique ids in the PRD ramp order`() {
        assertEquals(46, TraceGlyphs.all.size)
        assertEquals(46, ids.toSet().size)
        assertEquals(listOf("line-h", "line-v", "line-slant", "arch", "wave"), TraceGlyphs.stage(1).map { it.id })
        assertEquals(listOf("circle", "square", "triangle", "cross", "heart"), TraceGlyphs.stage(2).map { it.id })
        assertEquals(
            listOf("digit-1", "digit-2", "digit-3", "digit-4", "digit-5", "digit-6", "digit-7", "digit-8", "digit-9", "digit-0"),
            TraceGlyphs.stage(3).map { it.id },
        )
        val letters = TraceGlyphs.stage(4).map { it.id.removePrefix("letter-") }
        assertEquals("ILTHEF" + "AVWMNZKXY" + "OCUJDPBRGSQ", letters.joinToString(""))
        assertEquals(26, letters.size)
        // The list is in ramp order: stages never go backwards.
        assertEquals(TraceGlyphs.all.map { it.stage }.sorted(), TraceGlyphs.all.map { it.stage })
    }

    @Test
    fun `every glyph has at least one stroke, and the multi-stroke ones are exactly the designed ones`() {
        val multi = mapOf(
            "cross" to 2, "digit-4" to 2, "digit-5" to 2, "digit-9" to 2, "letter-I" to 3, "letter-T" to 2, "letter-H" to 3,
            "letter-E" to 4, "letter-F" to 3, "letter-A" to 3, "letter-M" to 3, "letter-N" to 3, "letter-K" to 2, "letter-Y" to 2,
            "letter-D" to 2, "letter-P" to 2, "letter-B" to 3, "letter-R" to 3, "letter-Q" to 2,
            "letter-X" to 2, // two crossing diagonals; the designer's count list forgot X but the data has both
        )
        for (g in TraceGlyphs.all) {
            assertTrue(g.id, g.strokes.isNotEmpty())
            assertEquals(g.id, multi[g.id] ?: 1, g.strokes.size)
        }
    }

    @Test
    fun `every glyph parses and every centreline stays inside the safe box`() {
        for (g in TraceGlyphs.all) for (s in g.strokes) {
            for (i in 0 until s.count) {
                assertTrue("${g.id} x=${s.xs[i]}", s.xs[i] in 11.99..88.01)
                assertTrue("${g.id} y=${s.ys[i]}", s.ys[i] in 10.99..89.01)
            }
            assertTrue("${g.id} has real length", s.length > 20.0)
        }
    }

    @Test
    fun `closed shapes start at the top and go counter-clockwise, ending exactly on their start`() {
        val closed = listOf("circle", "square", "triangle", "heart", "digit-8", "digit-0", "letter-O")
        for (id in closed) {
            val s = TraceGlyphs.get(id).strokes.single()
            assertEquals("$id closes", s.point(0), s.point(s.last))
            // Starts at the top: nothing is higher than the start, except the heart, which starts in its notch.
            if (id != "heart") for (i in 0 until s.count) assertTrue("$id starts at the top", s.ys[i] >= s.ys[0] - 0.01)
        }
        // Counter-clockwise on screen (y down) is a negative shoelace sum. The 8 crosses itself, so it has no single sense.
        for (id in closed - "digit-8") {
            val s = TraceGlyphs.get(id).strokes.single()
            var sum = 0.0
            for (i in 0 until s.last) sum += s.xs[i] * s.ys[i + 1] - s.xs[i + 1] * s.ys[i]
            assertTrue("$id is counter-clockwise ($sum)", sum < 0)
        }
        // Q's ring and the 9's bowl are closed too.
        for (id in listOf("letter-Q", "digit-9")) {
            val s = TraceGlyphs.get(id).strokes.first()
            assertEquals(id, s.point(0), s.point(s.last))
        }
    }

    @Test
    fun `straight strokes run left to right or top to bottom`() {
        for (g in TraceGlyphs.all) g.paths.forEachIndexed { i, path ->
            val only = path.segments.singleOrNull() as? LineTo ?: return@forEachIndexed
            val dx = only.end.x - path.start.x
            val dy = only.end.y - path.start.y
            if (abs(dx) > abs(dy)) assertTrue("${g.id} stroke ${i + 1} goes left to right", dx > 0)
            else assertTrue("${g.id} stroke ${i + 1} goes top to bottom", dy > 0)
        }
    }

    @Test
    fun `stems come before bars and arms in every list`() {
        // The first stroke of these is the vertical stem (or the diagonal), starting at the top.
        for (id in listOf("letter-I", "letter-H", "letter-E", "letter-F", "letter-D", "letter-P", "letter-B", "letter-R", "letter-K", "letter-N", "letter-M")) {
            val first = TraceGlyphs.get(id).strokes.first()
            assertEquals("$id stem is vertical", first.xs[0], first.xs[first.last], 1e-9)
            assertTrue("$id stem goes down", first.ys[first.last] > first.ys[0])
        }
    }

    @Test
    fun `digit 4 is closed, digit 1 is one stroke, and the shapes with special starts are as designed`() {
        val four = TraceGlyphs.get("digit-4").strokes
        assertEquals(four[0].point(0), four[1].point(0)) // both strokes start at the top point (62,12)
        assertEquals(Pt(62.0, 12.0), four[0].point(0))
        assertEquals(1, TraceGlyphs.get("digit-1").strokes.size)
        assertEquals(Pt(32.0, 32.0), TraceGlyphs.get("digit-1").strokes[0].point(0)) // flag first, then the stem
        assertEquals(Pt(50.0, 12.0), TraceGlyphs.get("digit-8").strokes[0].point(0))
        assertEquals(1, TraceGlyphs.get("digit-8").strokes.size)
    }

    /** Smallest distance between two strokes' centrelines, by samples plus segments. */
    private fun gap(a: SampledStroke, b: SampledStroke): Double {
        var best = Double.MAX_VALUE
        for (i in 0 until a.count) best = minOf(best, nearestOnStroke(b, a.xs[i], a.ys[i]).distance)
        for (i in 0 until b.count) best = minOf(best, nearestOnStroke(a, b.xs[i], b.ys[i]).distance)
        return best
    }

    @Test
    fun `strokes that are not joined keep their distance, so a band never touches an unrelated stroke`() {
        // The design rule is 20 units between unrelated centrelines (the band is 17.5 wide, so 2.5 remain). Strokes
        // that meet touch (gap under 1). Sampling error is under a unit, so the test allows 19. The one exception
        // the data has is R's leg, which starts on the bowl and so is 16 from the stem while joined to it through the bowl.
        for (g in TraceGlyphs.all) {
            val strokes = g.strokes
            val touching = { i: Int, j: Int -> gap(strokes[i], strokes[j]) <= 1.0 }
            for (i in strokes.indices) for (j in i + 1 until strokes.size) {
                if (touching(i, j)) continue
                val viaAnother = strokes.indices.any { k -> k != i && k != j && touching(i, k) && touching(k, j) }
                val d = gap(strokes[i], strokes[j])
                if (viaAnother) assertTrue("${g.id}: strokes ${i + 1} and ${j + 1} are $d apart", d >= 15.0)
                else assertTrue("${g.id}: strokes ${i + 1} and ${j + 1} are $d apart and unrelated", d >= 19.0)
            }
        }
    }

    @Test
    fun `the Kotlin glyph data matches the designer's glyph file`() {
        // The unit-test working directory is the app module in Gradle. Skipped if the docs are not reachable.
        val file = listOf("../docs/glyphs/paw-trace-glyphs.md", "docs/glyphs/paw-trace-glyphs.md").map { File(it) }.firstOrNull { it.exists() }
        assumeTrue("glyph docs not found from ${File(".").absolutePath}", file != null)
        val lines = file!!.readLines().mapNotNull { Regex("""^(\S+)\s+\|\s+(M.*)$""").matchEntire(it) }
        assertEquals(46, lines.size)
        for (m in lines) {
            val glyph = TraceGlyphs.get(m.groupValues[1])
            assertEquals(m.groupValues[1], m.groupValues[2].split(";").map { it.trim() }, glyph.pathData)
        }
        assertEquals(lines.map { it.groupValues[1] }, ids) // same order too
    }

    @Test
    fun `tightest spots from the design notes hold`() {
        // Q's tail starts inside the ring, about 21 units from it; digit 3's tip is about 33 from its middle cusp.
        val q = TraceGlyphs.get("letter-Q").strokes
        val tailStart = q[1].point(0)
        assertEquals(21.0, nearestOnStroke(q[0], tailStart.x, tailStart.y).distance, 0.5)
        val three = TraceGlyphs.get("digit-3").strokes.single()
        val tip = three.point(three.last)
        assertEquals(33.0, hypot(tip.x - 42.0, tip.y - 47.0), 0.5)
    }
}
