package com.pawplay.app.games.pawtrace

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class TracePaintTest {

    private fun paint(id: String, tuning: TraceTuning = TraceTuning.REFERENCE) = TracePaint(TraceGlyphs.get(id), tuning)

    /** Drags a finger along stroke [si] from sample [from] to sample [to] (either direction), like a real swipe. */
    private fun TracePaint.drag(si: Int, from: Int, to: Int): List<Int> {
        val s = strokes[si].stroke
        val finished = ArrayList<Int>()
        val step = if (to >= from) 1 else -1
        var i = from
        finished += stamp(s.xs[i], s.ys[i])
        while (i != to) {
            val next = i + step
            finished += stampSegment(s.xs[i], s.ys[i], s.xs[next], s.ys[next])
            i = next
        }
        return finished
    }

    private fun TracePaint.dragWhole(si: Int, reverse: Boolean = false) {
        val last = strokes[si].stroke.last
        if (reverse) drag(si, last, 0) else drag(si, 0, last)
    }

    private fun TracePaint.touchedIndices(si: Int) = strokes[si].touched.indices.filter { strokes[si].touched[it] }

    // ---------------------------------------------------------------- on and off the path

    @Test
    fun `nothing is painted before a touch, and the first marker is on the first stroke's start`() {
        val p = paint("line-h")
        assertEquals(0, p.version)
        assertFalse(p.strokes[0].anyCovered)
        assertEquals(TraceMarker(0, 0), p.marker())
        assertNull(p.current)
    }

    @Test
    fun `a touch on the path paints the nearest sample and two either side`() {
        // line-h samples sit at x = 14 + 1.5 i, so x = 50 is exactly sample 24.
        val p = paint("line-h")
        assertTrue(p.isOnPath(50.0, 50.0))
        p.stamp(50.0, 50.0)
        assertEquals(listOf(22, 23, 24, 25, 26), p.touchedIndices(0))
        assertEquals(5.0 / 49.0, p.strokes[0].fraction, 1e-12)
        assertEquals(0, p.current)
        assertEquals(1, p.version)
    }

    @Test
    fun `a touch off the path does nothing at all`() {
        val p = paint("line-h")
        // Corridor at the reference box is 32dp = 10 units. 10.5 units away is off the path.
        assertFalse(p.isOnPath(50.0, 60.5))
        val finished = p.stamp(50.0, 60.5)
        assertTrue(finished.isEmpty())
        assertFalse(p.strokes[0].anyCovered)
        assertEquals(0, p.version)
        assertNull(p.current)
        // Far away, and nowhere near any stroke of a busy glyph.
        val e = paint("letter-E")
        e.stamp(98.0, 98.0)
        assertEquals(0, e.version)
        assertTrue(e.strokes.none { it.anyCovered })
    }

    @Test
    fun `the corridor edge is 10 units at the reference box, inclusive`() {
        val p = paint("line-h")
        assertTrue(p.isOnPath(50.0, 60.0))      // exactly 10.0 away
        assertFalse(p.isOnPath(50.0, 60.001))
        assertTrue(p.isOnPath(50.0, 40.0))      // either side
        assertFalse(p.isOnPath(50.0, 39.999))
        // Beyond the ends the corridor is a rounded cap, not a box: 10 from the end point.
        assertTrue(p.isOnPath(4.0, 50.0))
        assertFalse(p.isOnPath(3.9, 50.0))
        assertFalse(p.isOnPath(7.0, 58.0))      // the corner of a square end cap would say yes; the rounded cap says 10.6 units
    }

    @Test
    fun `the corridor is 32dp whatever the box size`() {
        // A 288dp box is 2.88dp per unit, so 32dp is 11.11 units; a 320dp box is 10.
        val small = paint("line-h", TraceTuning(2.88))
        assertEquals(32.0 / 2.88, small.tuning.paintRadius, 1e-9)
        assertTrue(small.isOnPath(50.0, 61.1))
        assertFalse(small.isOnPath(50.0, 61.2))
        val ref = paint("line-h")
        assertEquals(10.0, ref.tuning.paintRadius, 1e-9)
        assertFalse(ref.isOnPath(50.0, 61.1))
    }

    @Test
    fun `off the path between two on-path points paints only what the finger really crossed`() {
        // Arch: a finger cutting straight across its open bottom crosses no centreline in the middle.
        val p = paint("arch")
        p.stampSegment(14.0, 78.0, 86.0, 78.0)
        val painted = p.touchedIndices(0)
        assertTrue(painted.isNotEmpty())
        val s = p.strokes[0].stroke
        // Everything painted is within the corridor of the straight line the finger travelled.
        for (i in painted) assertTrue("sample $i at (${s.xs[i]}, ${s.ys[i]})", kotlin.math.abs(s.ys[i] - 78.0) <= 10.0 + 3.0 * 1.5 + 1e-6)
        // The top of the arch, 70+ units above that line, is untouched.
        assertFalse(p.strokes[0].touched[s.last / 2])
    }

    @Test
    fun `a fast swipe leaves no holes`() {
        val p = paint("line-h")
        p.stampSegment(14.0, 50.0, 60.0, 50.0) // one big jump between two events
        val painted = p.touchedIndices(0)
        assertEquals((0..33).toList(), painted) // x=60 is sample 30.67, so the last stamp is on 31, plus 2 of reach
    }

    // ---------------------------------------------------------------- completion

    @Test
    fun `a stroke finishes at 85 percent and the gap fills itself`() {
        // line-h has 49 samples; 85% is 41.65, so 42 covered samples finishes it. Dragging from the start
        // covers samples 0..k+2 when the finger is at sample k.
        val p = paint("line-h")
        assertTrue(p.drag(0, 0, 37).isEmpty())          // covered 0..39: 40 samples = 81.6%
        assertFalse(p.strokes[0].done)
        assertEquals(40.0 / 49.0, p.strokes[0].fraction, 1e-12)
        assertTrue(p.drag(0, 37, 38).isEmpty())         // 0..40: 41 samples = 83.7%
        assertFalse(p.strokes[0].done)
        val finished = p.drag(0, 38, 39)                // 0..41: 42 samples = 85.7%
        assertEquals(listOf(0), finished)
        assertTrue(p.strokes[0].done)
        // The gap filled itself: every sample covered, but only what a finger really painted is `touched`.
        assertTrue(p.strokes[0].covered.all { it })
        assertEquals(1.0, p.strokes[0].fraction, 1e-12)
        assertEquals((0..41).toList(), p.touchedIndices(0))
        assertTrue(p.isComplete)
        assertNull(p.marker())
    }

    @Test
    fun `exactly 85 percent counts and just under does not`() {
        // The threshold is inclusive: build a state with exactly ceil(0.85 * n) covered.
        val p = paint("line-v")
        val s = p.strokes[0]
        val need = Math.ceil(DONE_FRACTION * s.stroke.count).toInt()
        // Paint the first (need - 1) samples one stamp-window at a time from the end of the window.
        p.drag(0, 0, need - 1 - STAMP_REACH - 1) // covers up to need-2 (index), i.e. need-1 samples
        assertFalse(s.done)
        assertEquals(need - 1, (0 until s.stroke.count).count { s.covered[it] })
        p.drag(0, need - 1 - STAMP_REACH - 1, need - 1 - STAMP_REACH)
        assertTrue(s.done)
    }

    @Test
    fun `finishing is reported once`() {
        val p = paint("line-h")
        val first = p.drag(0, 0, 45)
        assertEquals(listOf(0), first)
        // Painting on a finished stroke does nothing further.
        val v = p.version
        assertTrue(p.stamp(50.0, 50.0).isEmpty())
        assertEquals(v, p.version)
    }

    @Test
    fun `a whole stroke painted end to end finishes, forwards or backwards`() {
        for (reverse in listOf(false, true)) for (id in listOf("line-h", "line-v", "line-slant", "arch", "wave", "circle", "letter-S", "letter-J")) {
            val p = paint(id)
            p.dragWhole(0, reverse)
            assertTrue("$id reverse=$reverse", p.isComplete)
        }
    }

    @Test
    fun `painting from the middle in both directions finishes too`() {
        val p = paint("wave")
        val last = p.strokes[0].stroke.last
        p.drag(0, last / 2, 0)
        assertFalse(p.isComplete)
        p.drag(0, last / 2, last)
        assertTrue(p.isComplete)
    }

    @Test
    fun `lifting and continuing elsewhere keeps earlier paint`() {
        val p = paint("line-h")
        p.drag(0, 0, 10)
        val before = p.touchedIndices(0)
        p.drag(0, 30, 40)                                 // a new touch further along (lift, land, drag)
        assertTrue(p.touchedIndices(0).containsAll(before))
        assertFalse(p.strokes[0].done)
        p.drag(0, 10, 30)                                 // and another that joins them up
        assertTrue(p.touchedIndices(0).containsAll(before))
        assertTrue(p.strokes[0].done)
    }

    // ---------------------------------------------------------------- several strokes

    @Test
    fun `strokes can be painted in any order and the glyph completes only when all are done`() {
        val e = paint("letter-E")
        assertEquals(4, e.strokes.size)
        e.dragWhole(3)                                    // the bottom bar first
        assertTrue(e.strokes[3].done)
        assertFalse(e.isComplete)
        e.dragWhole(1, reverse = true)                    // the top bar, right to left
        e.dragWhole(2)
        assertFalse(e.isComplete)
        assertTrue(e.strokes[1].done && e.strokes[2].done)
        e.dragWhole(0)
        assertTrue(e.isComplete)
    }

    @Test
    fun `the four is closed, two strokes that share the top point`() {
        val four = paint("digit-4")
        four.dragWhole(1)                                 // the vertical first
        assertTrue(four.strokes[1].done)
        assertFalse(four.isComplete)
        four.dragWhole(0, reverse = true)                 // the diagonal and bar, backwards
        assertTrue(four.isComplete)
    }

    @Test
    fun `A takes three strokes and finishing the crossbar alone is not enough`() {
        val a = paint("letter-A")
        a.dragWhole(2)
        assertFalse(a.isComplete)
        a.dragWhole(0)
        assertFalse(a.isComplete)
        a.dragWhole(1)
        assertTrue(a.isComplete)
    }

    @Test
    fun `a finger at a join paints both strokes`() {
        // Digit 4: both strokes start at (62,12).
        val p = paint("digit-4")
        p.stamp(62.0, 12.0)
        assertEquals(listOf(0, 1, 2), p.touchedIndices(0))
        assertEquals(listOf(0, 1, 2), p.touchedIndices(1))
    }

    @Test
    fun `strokes that do not join never paint each other`() {
        // E: the spine (x=28) and the middle arm (y=50) join, but the top arm and the bottom arm are 76 apart.
        val e = paint("letter-E")
        e.stamp(60.0, 12.0)                               // out on the top arm
        assertTrue(e.strokes[1].anyCovered)
        assertFalse(e.strokes[0].anyCovered)
        assertFalse(e.strokes[2].anyCovered)
        assertFalse(e.strokes[3].anyCovered)
    }

    // ---------------------------------------------------------------- the marker and the cue

    @Test
    fun `the marker stands ahead of the paint, never on the fingertip`() {
        val p = paint("line-h")
        p.drag(0, 0, 10)                                  // covered 0..12
        val m = p.marker()!!
        assertEquals(0, m.stroke)
        // First gap is 13; the frog stands 48dp = 15 units = 10 samples further on.
        assertEquals(23, m.index)
        val frog = p.strokes[0].stroke.point(m.index)
        val finger = p.strokes[0].stroke.point(10)
        assertTrue(frog.x - finger.x >= 48.0 / 3.2 - 1.5 * 3) // clear of a 40dp fingertip's edge
    }

    @Test
    fun `the marker is exactly on the start while the start is unpainted, even if the middle is painted`() {
        val p = paint("line-h")
        p.drag(0, 20, 30)
        assertEquals(TraceMarker(0, 0), p.marker())
    }

    @Test
    fun `the marker stops at the end of a stroke instead of running past it`() {
        val p = paint("line-h")
        p.drag(0, 0, 36)                                  // 0..38 covered (not yet done), first gap 39
        assertEquals(p.strokes[0].stroke.last, p.marker()!!.index)
    }

    @Test
    fun `the marker follows the stroke being painted and hops to the next unfinished one`() {
        val e = paint("letter-E")
        assertEquals(TraceMarker(0, 0), e.marker())        // list order when nothing is painted
        e.drag(2, 0, 8)                                    // start painting the middle arm
        assertEquals(2, e.current)
        assertEquals(2, e.marker()!!.stroke)
        e.dragWhole(2)                                     // finish it
        assertTrue(e.strokes[2].done)
        assertEquals(TraceMarker(0, 0), e.marker())        // back to the first unfinished stroke's start
        e.drag(0, 0, 42)                                   // most of the spine; it finishes, and the top arm's start is painted where they join
        assertTrue(e.strokes[0].done)
        assertEquals(1, e.marker()!!.stroke)
        e.dragWhole(1)
        assertEquals(3, e.marker()!!.stroke)
        e.dragWhole(3)
        assertNull(e.marker())
    }

    @Test
    fun `the marker never points at a finished stroke`() {
        val cross = paint("cross")
        cross.dragWhole(1)
        assertEquals(0, cross.marker()!!.stroke)
        cross.stamp(50.0, 50.0)                            // touches the finished stroke's crossing; nothing moves
        assertEquals(0, cross.marker()!!.stroke)
    }

    @Test
    fun `the direction cue is three paw prints ahead of the frog and stops at the stroke's end`() {
        val p = paint("line-h")
        val m = p.marker()!!
        val cues = p.cues(m)
        assertEquals(3, cues.size)
        // 48, 83, 118dp on: 15, 25.9, 36.9 units along, rounded to whole samples (10, 17, 25).
        assertEquals(14.0 + 1.5 * 10, cues[0].x, 1e-9)
        assertEquals(14.0 + 1.5 * 17, cues[1].x, 1e-9)
        assertEquals(14.0 + 1.5 * 25, cues[2].x, 1e-9)
        assertEquals(0.0, cues[0].angleDegrees, 1e-9)       // pointing right, the direction of travel
        // Near the end only the ones that fit on the stroke are drawn (the last sample is 48; cues are 10, 17, 25 samples on).
        assertEquals(2, p.cues(TraceMarker(0, 30)).size)   // 40, 47 fit; 55 does not
        assertEquals(1, p.cues(TraceMarker(0, 38)).size)   // 48 fits
        assertEquals(0, p.cues(TraceMarker(0, 39)).size)
    }

    @Test
    fun `cues point down a vertical stroke`() {
        val p = paint("line-v")
        val cue = p.cues(p.marker()!!).first()
        assertEquals(90.0, cue.angleDegrees, 1e-9)
    }

    // ---------------------------------------------------------------- shapes with quirks

    @Test
    fun `a closed shape is one stroke and finishes without touching its end`() {
        val p = paint("circle")
        val s = p.strokes[0].stroke
        val needed = (0.85 * s.count).toInt() + 2
        p.drag(0, 0, needed - 3)
        assertTrue(p.isComplete)
    }

    @Test
    fun `digit 8 passes its centre twice and still finishes by being traced`() {
        val p = paint("digit-8")
        p.dragWhole(0)
        assertTrue(p.isComplete)
    }

    @Test
    fun `digit 3 doubles back at the cusp and finishes by being traced`() {
        val p = paint("digit-3")
        p.dragWhole(0)
        assertTrue(p.isComplete)
    }

    @Test
    fun `every glyph can be finished by tracing every stroke from its start`() {
        for (g in TraceGlyphs.all) {
            val p = TracePaint(g)
            g.strokes.indices.forEach { p.dragWhole(it) }
            assertTrue(g.id, p.isComplete)
            assertNull(g.id, p.marker())
        }
    }

    @Test
    fun `every glyph can be finished by tracing every stroke backwards, last stroke first`() {
        for (g in TraceGlyphs.all) {
            val p = TracePaint(g)
            g.strokes.indices.reversed().forEach { p.dragWhole(it, reverse = true) }
            assertTrue(g.id, p.isComplete)
        }
    }

    @Test
    fun `a sloppy finger 8 units off the centreline still paints and finishes the whole glyph`() {
        // Half a fingertip of wobble: offset every touch 8 units (26dp) sideways; the corridor is 10.
        for (g in TraceGlyphs.all) {
            val p = TracePaint(g)
            for (si in g.strokes.indices) {
                val s = p.strokes[si].stroke
                for (i in 0 until s.count) {
                    val a = s.angleDegrees(i)
                    val nx = -kotlin.math.sin(Math.toRadians(a)) * 8.0
                    val ny = kotlin.math.cos(Math.toRadians(a)) * 8.0
                    p.stamp(s.xs[i] + nx, s.ys[i] + ny)
                }
            }
            assertTrue("${g.id} finishes with a wobbly finger", p.isComplete)
        }
    }

    @Test
    fun `tuning can change mid-glyph without losing paint`() {
        val p = paint("line-h")
        p.drag(0, 0, 10)
        val before = p.touchedIndices(0)
        p.tuning = TraceTuning(2.88)
        assertEquals(before, p.touchedIndices(0))
        assertNotNull(p.marker())
    }
}
