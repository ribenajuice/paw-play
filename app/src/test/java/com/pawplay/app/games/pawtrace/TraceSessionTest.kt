package com.pawplay.app.games.pawtrace

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

class TraceSessionTest {

    private fun session(seed: Int = 1) = TraceSession(Random(seed))

    /** One finger tracing stroke [si] of the session's glyph from start to end, then lifting. */
    private fun TraceSession.trace(si: Int, id: Long = 1, reverse: Boolean = false) {
        val s = paint.strokes[si].stroke
        val order = if (reverse) (s.last downTo 0) else (0..s.last)
        var first = true
        for (i in order) {
            if (first) { pointerDown(id, s.xs[i], s.ys[i]); first = false } else pointerMove(id, s.xs[i], s.ys[i])
        }
        pointerUp(id)
    }

    private fun TraceSession.traceAll() { paint.strokes.indices.forEach { if (phase == TracePhase.TRACING) trace(it) } }

    // ---------------------------------------------------------------- first run and progress

    @Test
    fun `first run has the plain horizontal line ready with the frog on its start, and nothing else`() {
        val s = session()
        assertEquals("line-h", s.paint.glyph.id)
        assertEquals(TracePhase.TRACING, s.phase)
        assertEquals(0, s.serial)
        assertEquals(TraceMarker(0, 0), s.paint.marker())
        assertEquals(0, s.fingersDown)
    }

    @Test
    fun `tracing the glyph celebrates, and only then can play-on move to the next one`() {
        val s = session()
        assertFalse("play-on does nothing before a glyph is done", s.playOn())
        assertEquals("line-h", s.paint.glyph.id)
        s.trace(0)
        assertEquals(TracePhase.CELEBRATING, s.phase)
        assertTrue(s.playOn())
        assertEquals("line-v", s.paint.glyph.id)
        assertEquals(TracePhase.TRACING, s.phase)
        assertEquals(1, s.serial)
        assertFalse(s.paint.strokes[0].anyCovered) // the new glyph starts clean
        assertEquals(TraceMarker(0, 0), s.paint.marker())
    }

    @Test
    fun `mashing play-on advances exactly one glyph`() {
        val s = session()
        s.trace(0)
        val results = List(20) { s.playOn() }
        assertEquals(1, results.count { it })
        assertEquals("line-v", s.paint.glyph.id)
        assertEquals(1, s.serial)
        assertEquals(1, s.ramp.completedNew.size)
    }

    @Test
    fun `the celebration never advances by itself`() {
        val s = session()
        s.trace(0)
        // Fingers moving about on the finished glyph change nothing.
        s.pointerDown(9, 50.0, 50.0); s.pointerMove(9, 60.0, 50.0); s.pointerUp(9)
        assertEquals(TracePhase.CELEBRATING, s.phase)
        assertEquals("line-h", s.paint.glyph.id)
    }

    @Test
    fun `a finger still resting on the glass from the last glyph does not paint the next one until it lifts and lands again`() {
        val s = session()
        val first = s.paint.strokes[0].stroke
        s.pointerDown(1, first.xs[0], first.ys[0])
        for (i in 1..first.last) s.pointerMove(1, first.xs[i], first.ys[i]) // still down at the end
        assertEquals(TracePhase.CELEBRATING, s.phase)
        s.playOn()
        val second = s.paint.strokes[0].stroke
        s.pointerMove(1, second.xs[10], second.ys[10])
        assertFalse(s.paint.strokes[0].anyCovered)
        s.pointerUp(1)
        s.pointerDown(1, second.xs[10], second.ys[10])
        assertTrue(s.paint.strokes[0].anyCovered)
        assertEquals(1, s.fingersDown)
    }

    @Test
    fun `leaving and re-entering starts again at the first glyph, because a session is all there is`() {
        val a = session()
        a.trace(0); a.playOn(); a.trace(0); a.playOn()
        assertEquals("line-slant", a.paint.glyph.id)
        val b = session() // what re-entering the game builds
        assertEquals("line-h", b.paint.glyph.id)
    }

    @Test
    fun `a whole session of 60 glyphs plays through with strokes in any order`() {
        val s = session(5)
        repeat(60) {
            val order = s.paint.strokes.indices.shuffled(Random(it))
            for (si in order) if (s.phase == TracePhase.TRACING) s.trace(si, reverse = it % 2 == 0)
            assertEquals(TracePhase.CELEBRATING, s.phase)
            assertTrue(s.playOn())
        }
        assertEquals(60, s.serial)
    }

    // ---------------------------------------------------------------- fingers

    @Test
    fun `a touch that begins off the path does nothing until the finger reaches the path`() {
        val s = session()
        val line = s.paint.strokes[0].stroke
        s.pointerDown(1, 50.0, 90.0)                       // well below the line
        assertFalse(s.paint.strokes[0].anyCovered)
        s.pointerMove(1, 60.0, 80.0)
        assertFalse(s.paint.strokes[0].anyCovered)
        s.pointerMove(1, 60.0, 50.0)                       // now on it
        assertTrue(s.paint.strokes[0].anyCovered)
        assertTrue(s.paint.strokes[0].touched[((60.0 - line.xs[0]) / 1.5).toInt()])
    }

    @Test
    fun `lifting and touching down again anywhere on the path resumes painting`() {
        val s = session()
        val line = s.paint.strokes[0].stroke
        s.pointerDown(1, line.xs[0], line.ys[0])
        for (i in 1..10) s.pointerMove(1, line.xs[i], line.ys[i])
        s.pointerUp(1)
        val painted = s.paint.strokes[0].touched.count { it }
        s.pointerDown(2, line.xs[30], line.ys[30])         // a new finger id, far along
        assertTrue(s.paint.strokes[0].touched.count { it } > painted)
        assertTrue(s.paint.strokes[0].touched[30])
        assertTrue(s.paint.strokes[0].touched[10])          // earlier paint stays
    }

    @Test
    fun `only the first finger paints`() {
        val s = session()
        val line = s.paint.strokes[0].stroke
        s.pointerDown(1, line.xs[0], line.ys[0])            // first finger, on the path
        s.pointerDown(2, line.xs[30], line.ys[30])          // a second finger, also on the path
        assertFalse("a second finger paints nothing", s.paint.strokes[0].touched[30])
        s.pointerMove(2, line.xs[35], line.ys[35])
        assertFalse(s.paint.strokes[0].touched[35])
        s.pointerMove(1, line.xs[5], line.ys[5])
        assertTrue(s.paint.strokes[0].touched[5])
        assertEquals(2, s.fingersDown)
    }

    @Test
    fun `when the first finger lifts, the second does not take over but the next new touch does`() {
        val s = session()
        val line = s.paint.strokes[0].stroke
        s.pointerDown(1, line.xs[0], line.ys[0])
        s.pointerDown(2, line.xs[30], line.ys[30])
        s.pointerUp(1)
        s.pointerMove(2, line.xs[36], line.ys[36])
        assertFalse(s.paint.strokes[0].touched[36])          // finger 2 was never the painter
        s.pointerDown(3, line.xs[40], line.ys[40])            // but a fresh touch is, with nobody painting now
        assertTrue(s.paint.strokes[0].touched[40])
    }

    @Test
    fun `a palm resting off the path cannot stop a finger landing on the path`() {
        val s = session()
        val line = s.paint.strokes[0].stroke
        s.pointerDown(1, 50.0, 95.0)                          // palm, first down, nowhere near the path
        s.pointerDown(2, line.xs[10], line.ys[10])            // real finger on the path
        assertTrue("the finger paints", s.paint.strokes[0].touched[10])
        s.pointerMove(1, line.xs[40], line.ys[40])            // and the palm sliding onto the path later paints nothing
        assertFalse(s.paint.strokes[0].touched[40])
    }

    @Test
    fun `a first finger that has been on the path keeps the job against a later finger`() {
        val p = PrimaryPointer()
        assertTrue(p.down(1, onPath = true))
        assertFalse(p.down(2, onPath = true))
        assertTrue(p.move(1, onPath = false))
        assertFalse(p.move(2, onPath = true))
        assertTrue(p.up(1))
        assertFalse(p.up(2))
        assertNull(p.id)
    }

    @Test
    fun `the primary pointer rules on their own`() {
        val p = PrimaryPointer()
        // The first finger holds the job even while off the path.
        assertTrue(p.down(1, onPath = false))
        assertFalse("second finger off the path: ignored", p.down(2, onPath = false))
        // A later finger that lands on the path takes over from a first finger that never touched a path.
        assertTrue(p.down(3, onPath = true))
        assertFalse(p.move(1, onPath = true))
        assertTrue(p.move(3, onPath = true))
        // Unknown fingers never paint.
        assertFalse(p.move(99, onPath = true))
        assertFalse(p.up(99))
        p.reset()
        assertFalse(p.active)
    }

    @Test
    fun `several fingers, palm touches and cancelled touches cannot crash or stick the game`() {
        val s = session(3)
        val rnd = Random(42)
        repeat(3000) {
            val id = rnd.nextLong(1, 6)
            val x = rnd.nextDouble(-20.0, 120.0)
            val y = rnd.nextDouble(-20.0, 120.0)
            when (rnd.nextInt(4)) {
                0 -> s.pointerDown(id, x, y)
                1, 2 -> s.pointerMove(id, x, y)
                else -> s.pointerUp(id)
            }
            if (s.phase == TracePhase.CELEBRATING) s.playOn()
        }
        s.releaseAll()
        assertEquals(0, s.fingersDown)
        // Still playable afterwards.
        s.traceAll()
        assertEquals(TracePhase.CELEBRATING, s.phase)
    }

    @Test
    fun `a finger that goes down after the glyph is done is counted but paints nothing`() {
        val s = session()
        s.trace(0)
        s.pointerDown(4, 50.0, 50.0)
        assertEquals(1, s.fingersDown)
        s.pointerUp(4)
        assertEquals(0, s.fingersDown)
    }

    @Test
    fun `finger counts never go negative on stray lifts`() {
        val s = session()
        s.pointerUp(1); s.pointerUp(2)
        assertEquals(0, s.fingersDown)
    }

    @Test
    fun `finishing the last stroke reports the stroke that finished, once`() {
        val s = session()
        val line = s.paint.strokes[0].stroke
        var finished = s.pointerDown(1, line.xs[0], line.ys[0])
        var reported = 0
        for (i in 1..line.last) finished = s.pointerMove(1, line.xs[i], line.ys[i]).also { reported += it.size }
        assertEquals(1, reported)
        assertTrue(finished.isEmpty())
    }

    @Test
    fun `resizing mid-glyph keeps the paint and changes the corridor in glyph units`() {
        val s = session()
        val line = s.paint.strokes[0].stroke
        s.pointerDown(1, line.xs[0], line.ys[0])
        s.pointerMove(1, line.xs[10], line.ys[10])
        s.updateTuning(TraceTuning(2.88))
        assertTrue(s.paint.strokes[0].touched[8])
        assertEquals(32.0 / 2.88, s.tuning.paintRadius, 1e-9)
    }

    @Test
    fun `a missed lift cannot leave the game waiting for a finger that is gone`() {
        val s = session()
        val line = s.paint.strokes[0].stroke
        s.pointerDown(1, line.xs[0], line.ys[0])
        s.pointerMove(1, line.xs[5], line.ys[5])           // finger 1 is now "painting", and its lift never arrives
        s.pointerDown(2, line.xs[30], line.ys[30])
        assertFalse(s.paint.strokes[0].touched[30])        // ignored while finger 1 is thought to be down
        s.releaseAll()                                     // the screen sees that no finger is down at all
        assertEquals(0, s.fingersDown)
        s.pointerDown(3, line.xs[30], line.ys[30])
        assertTrue(s.paint.strokes[0].touched[30])
    }

    @Test
    fun `a finger that lands well outside the glyph box and slides onto the path starts painting when it reaches it`() {
        val s = session()
        val line = s.paint.strokes[0].stroke
        s.pointerDown(1, 50.0, -60.0)                       // in the blank space above the box
        assertFalse(s.paint.strokes[0].anyCovered)
        s.pointerMove(1, 50.0, -20.0)
        assertFalse(s.paint.strokes[0].anyCovered)
        s.pointerMove(1, 50.0, 30.0)                        // 20 units above the line: still off it
        assertFalse(s.paint.strokes[0].anyCovered)
        s.pointerMove(1, 50.0, 45.0)                        // now within the corridor
        assertTrue(s.paint.strokes[0].touched[24])
        assertTrue(line.xs[24] == 50.0)
    }

    @Test
    fun `a finger that lands outside the box to the side and slides in along the line paints it`() {
        val s = session()
        s.pointerDown(1, -40.0, 50.0)
        for (x in -40..90 step 5) s.pointerMove(1, x.toDouble(), 50.0)
        assertTrue(s.phase == TracePhase.CELEBRATING)
    }

    @Test
    fun `a huge or non-finite movement cannot freeze the game or stamp endlessly`() {
        val s = session()
        val line = s.paint.strokes[0].stroke
        s.pointerDown(1, line.xs[0], line.ys[0])
        val t0 = System.nanoTime()
        s.pointerMove(1, 1e15, 50.0)
        s.pointerMove(1, Double.NaN, 50.0)
        s.pointerMove(1, Double.POSITIVE_INFINITY, Double.NEGATIVE_INFINITY)
        assertTrue("took ${(System.nanoTime() - t0) / 1e6}ms", System.nanoTime() - t0 < 500_000_000L)
        // Still working afterwards.
        s.pointerMove(1, line.xs[10], line.ys[10])
        assertTrue(s.paint.strokes[0].touched[10])
    }
}
