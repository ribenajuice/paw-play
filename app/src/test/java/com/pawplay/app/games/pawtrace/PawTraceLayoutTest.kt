package com.pawplay.app.games.pawtrace

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PawTraceLayoutTest {

    @Test
    fun `the reference 360 x 692 play area matches the mockup`() {
        val l = traceLayout(360f, 692f)
        assertEquals(320f, l.boxSize, 0.01f)
        assertEquals(20f, l.boxLeft, 0.01f)
        assertEquals(186f, l.boxTop, 0.01f)
        assertEquals(3.2f, l.unitDp, 1e-4f)
        assertEquals(136f, l.playOnLeft, 0.01f)
        assertEquals(560f, l.playOnTop, 0.01f)
        assertEquals(648f, l.playOnBottom, 0.01f)
    }

    @Test
    fun `on phones with room the glyph is centred and clear of home and play-on by at least 16dp`() {
        for ((w, h) in listOf(360f to 692f, 411f to 800f, 393f to 760f, 412f to 915f, 360f to 640f, 320f to 600f)) {
            val l = traceLayout(w, h)
            val label = "${w}x$h"
            assertEquals(label, (w - l.boxSize) / 2f, l.boxLeft, 0.01f)
            assertTrue("$label box top ${l.boxTop} clear of home", l.boxTop >= TraceLayout.HOME_INSET + TraceLayout.HOME_SIZE + 16f - 0.01f)
            assertTrue("$label play-on ${l.playOnTop} clear of the glyph ${l.boxBottom}", l.playOnTop >= l.boxBottom + 16f - 0.01f)
            assertTrue("$label play-on on screen", l.playOnBottom <= h - TraceLayout.MIN_PLAY_ON_BOTTOM_MARGIN + 0.01f)
            assertTrue("$label box on screen", l.boxLeft >= 16f - 0.01f && l.boxLeft + l.boxSize <= w - 16f + 0.01f)
        }
    }

    @Test
    fun `the box is never below 288dp and never above 320dp when the screen allows it`() {
        for (w in listOf(320f, 340f, 360f, 393f, 411f, 600f)) for (h in listOf(520f, 560f, 640f, 692f, 800f, 915f)) {
            val l = traceLayout(w, h)
            assertTrue("${w}x$h: ${l.boxSize}", l.boxSize <= 320f + 0.01f)
            assertTrue("${w}x$h: ${l.boxSize}", l.boxSize >= 288f - 0.01f)
        }
    }

    @Test
    fun `a 320dp wide phone gets its side margins squeezed to 16dp before the box drops below 288dp`() {
        val l = traceLayout(320f, 640f)
        assertEquals(288f, l.boxSize, 0.01f)
        assertEquals(16f, l.boxLeft, 0.01f)
    }

    @Test
    fun `short screens move the glyph rather than shrink it, until they run out of room`() {
        // 320 x 568 phone with 24dp of status bar and 48dp of nav bar: about 496 tall.
        val l = traceLayout(360f, 496f)
        assertEquals(288f, l.boxSize, 0.01f)
        assertEquals(92f, l.boxTop, 0.01f)                 // as high as it may go: 16 under the home button
        assertTrue(l.playOnTop >= l.boxBottom + 16f - 0.01f)
    }

    @Test
    fun `screens too short for any clearance still lay out sanely`() {
        val l = traceLayout(360f, 400f)                     // the play-on button may overlap the glyph's foot, only during the celebration
        assertTrue(l.boxSize >= TraceLayout.FLOOR_BOX)
        assertTrue(l.boxTop >= 92f)
        assertTrue(l.playOnTop >= 0f && l.playOnBottom <= 400f)
    }

    @Test
    fun `a zero or tiny window cannot produce a zero, negative or NaN layout`() {
        for ((w, h) in listOf(0f to 0f, 2f to 2f, 1f to 700f, 360f to 1f, -5f to -5f)) {
            val l = traceLayout(w, h)
            assertTrue(l.boxSize >= TraceLayout.FLOOR_BOX)
            assertTrue(!l.unitDp.isNaN() && l.unitDp > 0f)
            assertTrue(!l.boxTop.isNaN() && !l.playOnTop.isNaN() && !l.boxLeft.isNaN())
        }
    }

    @Test
    fun `the fingertip sizes are fixed in dp and stay above the 48dp floor`() {
        assertTrue(TraceLayout.HOME_SIZE >= 48f)
        assertEquals(88f, TraceLayout.PLAY_ON_SIZE, 0f)
        assertEquals(56.0, TraceMetrics.BAND_DP, 0.0)
        assertTrue("the guide band is at least 56dp", TraceMetrics.BAND_DP >= 56.0)
        assertEquals(64.0, 2 * TraceMetrics.PAINT_RADIUS_DP, 0.0)
        // On every box size the frog stands at least 48dp away from the first unpainted point.
        for (box in listOf(288.0, 320.0, 360.0)) {
            val t = TraceTuning(box / 100.0)
            assertEquals(48.0, t.markerAhead * (box / 100.0), 1e-9)
            assertEquals(134.0, t.hintWalk * (box / 100.0), 1e-9)
        }
    }
}
