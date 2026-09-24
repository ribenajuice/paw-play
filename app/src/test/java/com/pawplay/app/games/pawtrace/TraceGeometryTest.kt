package com.pawplay.app.games.pawtrace

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import kotlin.math.PI
import kotlin.math.hypot
import kotlin.math.sqrt

class TraceGeometryTest {

    private fun assertRejected(d: String) {
        try {
            parseStrokePath(d)
            fail("expected '$d' to be rejected")
        } catch (expected: IllegalArgumentException) {
            assertNotNull(expected.message)
        }
    }

    @Test
    fun `parses a straight line`() {
        val path = parseStrokePath("M14,50 L86,50")
        assertEquals(Pt(14.0, 50.0), path.start)
        assertEquals(listOf<PathSegment>(LineTo(Pt(86.0, 50.0))), path.segments)
    }

    @Test
    fun `parses cubic curves, decimals and mixed segments`() {
        val path = parseStrokePath("M50,14 C30.12,14 14,30.12 14,50 L20.5,60.25")
        assertEquals(Pt(50.0, 14.0), path.start)
        assertEquals(CubicTo(Pt(30.12, 14.0), Pt(14.0, 30.12), Pt(14.0, 50.0)), path.segments[0])
        assertEquals(LineTo(Pt(20.5, 60.25)), path.segments[1])
    }

    @Test
    fun `accepts spaces instead of commas and repeated coordinate groups`() {
        val path = parseStrokePath("M 0 0 L 10 0 20 10")
        assertEquals(listOf<PathSegment>(LineTo(Pt(10.0, 0.0)), LineTo(Pt(20.0, 10.0))), path.segments)
    }

    @Test
    fun `parses negative numbers`() {
        val path = parseStrokePath("M-2,-4 L3,-0.5")
        assertEquals(Pt(-2.0, -4.0), path.start)
        assertEquals(LineTo(Pt(3.0, -0.5)), path.segments.single())
    }

    @Test
    fun `rejects anything outside the absolute M, L, C subset`() {
        assertRejected("")
        assertRejected("L10,10")               // must start with M
        assertRejected("M10,10")               // needs a segment
        assertRejected("M10,10 Z")             // no close
        assertRejected("M10,10 l5,5")          // relative
        assertRejected("M10,10 H50")           // no shorthand
        assertRejected("M10,10 Q5,5 20,20")    // no quadratics
        assertRejected("M10,10 L20,20 M30,30 L40,40") // one M per stroke
        assertRejected("M10 L20,20")           // short M
        assertRejected("M10,10 L20")           // half a point
        assertRejected("M10,10 C1,2 3,4 5,6 7") // half a cubic group
        assertRejected("M10,10 L 20,20 x")     // stray text
        assertRejected("M10,10 L20,2..0")      // bad number
    }

    @Test
    fun `a horizontal line 72 units long samples every 1_5 units exactly`() {
        val s = sampleStroke(parseStrokePath("M14,50 L86,50"))
        assertEquals(72.0, s.length, 1e-9)
        assertEquals(49, s.count)          // 72 / 1.5 = 48 gaps
        for (i in 0 until s.count) {
            assertEquals(14.0 + 1.5 * i, s.xs[i], 1e-9)
            assertEquals(50.0, s.ys[i], 1e-9)
        }
    }

    @Test
    fun `a slanted line has the hand-computed length and count`() {
        // (24,14) -> (76,86): dx 52, dy 72, length sqrt(52^2 + 72^2) = sqrt(7888)
        val s = sampleStroke(parseStrokePath("M24,14 L76,86"))
        assertEquals(sqrt(7888.0), s.length, 1e-9)
        assertEquals(59 + 1, s.count)      // round(88.816 / 1.5) = 59 gaps
        val gap = hypot(s.xs[1] - s.xs[0], s.ys[1] - s.ys[0])
        assertEquals(s.length / 59, gap, 1e-9)
        assertEquals(Pt(24.0, 14.0), s.point(0))
        assertEquals(Pt(76.0, 86.0), s.point(s.last))
    }

    @Test
    fun `a polyline is sampled around its corner by arc length`() {
        // Two legs of 30: 60 long, 40 gaps of exactly 1.5. The corner (30,0) is sample 20.
        val s = sampleStroke(parseStrokePath("M0,0 L30,0 L30,30"))
        assertEquals(60.0, s.length, 1e-9)
        assertEquals(41, s.count)
        assertEquals(30.0, s.xs[20], 1e-9)
        assertEquals(0.0, s.ys[20], 1e-9)
        assertEquals(30.0, s.xs[21], 1e-9)
        assertEquals(1.5, s.ys[21], 1e-9)
    }

    @Test
    fun `the circle glyph is 2 pi r long with a matching start and end`() {
        val s = TraceGlyphs.get("circle").strokes.single()
        assertEquals(2 * PI * 36.0, s.length, 0.2) // four cubics approximate a circle to about 0.03%
        assertEquals(152, s.count)                 // round(226.19 / 1.5) = 151 gaps, so 152 points
    }

    @Test
    fun `spacing is even and about 1_5 for every stroke of every glyph`() {
        for (glyph in TraceGlyphs.all) for (s in glyph.strokes) {
            val expectedGaps = s.last
            val gap = s.length / expectedGaps
            assertTrue("${glyph.id}: gap $gap", gap in 1.0..2.25)
            for (i in 1 until s.count) {
                val d = hypot(s.xs[i] - s.xs[i - 1], s.ys[i] - s.ys[i - 1])
                assertTrue("${glyph.id}: sample $i is $d from the last", d <= gap + 1e-6)
            }
        }
    }

    @Test
    fun `the ends are pinned exactly on the path's start and end`() {
        for (glyph in TraceGlyphs.all) glyph.paths.forEachIndexed { i, path ->
            val s = glyph.strokes[i]
            assertEquals(path.start, s.point(0))
            assertEquals(path.segments.last().end, s.point(s.last))
        }
    }

    @Test
    fun `pointAt interpolates between samples and clamps`() {
        val s = sampleStroke(parseStrokePath("M14,50 L86,50"))
        assertEquals(Pt(14.75, 50.0), s.pointAt(0.5))
        assertEquals(Pt(14.0, 50.0), s.pointAt(-3.0))
        assertEquals(Pt(86.0, 50.0), s.pointAt(1000.0))
        assertEquals(Pt(86.0, 50.0), s.pointAt(s.last.toDouble()))
    }

    @Test
    fun `angle follows the direction of travel`() {
        val right = sampleStroke(parseStrokePath("M0,0 L30,0"))
        val down = sampleStroke(parseStrokePath("M0,0 L0,30"))
        assertEquals(0.0, right.angleDegrees(5), 1e-9)
        assertEquals(90.0, down.angleDegrees(5), 1e-9)
    }
}
