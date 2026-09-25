package com.pawplay.app.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** The good-game screen's calm-motion rules (docs/DESIGN-SYSTEM.md, "Motion and timing"). */
class GoodGameMotionTest {
    private val m = GoodGameMotion

    @Test
    fun `nothing flashes or blinks faster than three times a second`() {
        val minPeriod = 1000L / 3
        assertTrue("hop period ${m.HOP_MS}", m.HOP_MS >= minPeriod)
        assertTrue("glow period ${m.GLOW_PERIOD_MS}", m.GLOW_PERIOD_MS >= minPeriod)
        assertTrue("twinkle period ${m.TWINKLE_MS}", m.TWINKLE_MS >= minPeriod)
        assertEquals(670L, m.HOP_MS) // 1.5 hops a second
    }

    @Test
    fun `the animal hops three times, 18dp high, starting when the fade is over`() {
        assertEquals(0f, m.hopHeightDp(0, true), 0f)
        assertEquals(0f, m.hopHeightDp(m.HOP_START_MS - 1, true), 0f)
        assertEquals(18f, m.hopHeightDp(m.HOP_START_MS + m.HOP_MS / 2, true), 0.05f)
        var peaks = 0
        var prev = 0f
        var rising = false
        var t = 0L
        while (t < 4000) {
            val h = m.hopHeightDp(t, true)
            assertTrue(h in 0f..18.01f)
            if (h > prev) rising = true else if (h < prev && rising) { peaks++; rising = false }
            prev = h
            t += 5
        }
        assertEquals(3, peaks)
        assertEquals("all done", 0f, m.hopHeightDp(m.HOP_START_MS + 3 * m.HOP_MS, true), 0f)
    }

    @Test
    fun `with animations off there is no hop, the glow is steady and nothing twinkles`() {
        for (t in 0L..4000L step 50) {
            assertEquals(0f, m.hopHeightDp(t, false), 0f)
            assertEquals(m.GLOW_REST, m.glowFactor(t, false), 0f)
        }
    }

    @Test
    fun `the glow breathes between 55 and 100 percent twice, then rests at 55`() {
        assertEquals(0.55f, m.glowFactor(0, true), 0f)
        assertEquals(1f, m.glowFactor(m.FADE_MS + m.GLOW_PERIOD_MS / 2, true), 0.01f)
        assertEquals(1f, m.glowFactor(m.FADE_MS + m.GLOW_PERIOD_MS + m.GLOW_PERIOD_MS / 2, true), 0.01f)
        assertEquals(0.55f, m.glowFactor(m.FADE_MS + 2 * m.GLOW_PERIOD_MS, true), 0f)
        assertEquals(0.55f, m.glowFactor(20_000, true), 0f)
        for (t in 0L..6000L step 10) assertTrue(m.glowFactor(t, true) in 0.55f..1f)
    }

    @Test
    fun `six sparkles each twinkle twice for 1_3 seconds and then are gone`() {
        assertEquals(6, m.twinkleDelaysMs.size)
        for (i in 0 until 6) {
            assertEquals(0f, m.twinkle(0, i), 0f)
            val start = m.FADE_MS + m.twinkleDelaysMs[i]
            assertEquals(1f, m.twinkle(start + m.TWINKLE_MS / 2, i), 0.01f)
            assertEquals(1f, m.twinkle(start + m.TWINKLE_MS + m.TWINKLE_MS / 2, i), 0.01f)
            assertEquals(0f, m.twinkle(start + 2 * m.TWINKLE_MS, i), 0f)
            assertEquals(0f, m.twinkle(m.newBestEndMs, i), 0f)
        }
    }

    @Test
    fun `every new-best effect is over before the clock stops, in about four seconds`() {
        assertTrue(m.newBestEndMs > m.HOP_START_MS + 3 * m.HOP_MS)
        assertTrue(m.newBestEndMs > m.FADE_MS + 2 * m.GLOW_PERIOD_MS)
        assertTrue("ends at ${m.newBestEndMs}", m.newBestEndMs in 3000..5000)
        assertEquals(500L, m.FADE_MS)
    }

    // ------------------------------------------------------------------ the clock

    @Test
    fun `the clock starts at its first frame and a new best turning up late never sends it back to zero`() {
        val c = GoodGameClock()
        val frame = 16_666_667L
        var nanos = 5_000_000_000L
        assertEquals(0L, c.sinceStartMs(nanos))
        var last = 0L
        var animate = false
        // 1.5 seconds pass with no new best known: the fade is long over and the clock is done and resting.
        while (last < 1500) {
            nanos += frame
            val t = c.sinceStartMs(nanos)
            assertTrue("time only goes forward", t >= last)
            last = t
            assertEquals(minOf(t, m.FADE_MS), c.shownMs(t, animate))
        }
        assertTrue(c.isDone(last, animate))
        assertEquals("the screen stays fully faded in", m.FADE_MS, c.shownMs(last, animate))
        // The saved best is judged late: a new best. The clock carries on from where it is: it does not read 0 again.
        animate = true
        nanos += frame
        val after = c.sinceStartMs(nanos)
        assertTrue("still ${after}ms in, not back at 0", after >= last)
        assertTrue("the fade stays complete: ${c.shownMs(after, animate)}", c.shownMs(after, animate) >= m.FADE_MS)
        assertFalse(c.isDone(after, animate))
        assertTrue(c.isDone(m.newBestEndMs, animate))
        assertEquals(m.newBestEndMs, c.shownMs(m.newBestEndMs + 10_000, animate))
    }

    @Test
    fun `without a new best the clock stops at the end of the fade, with one at the end of the celebration`() {
        val c = GoodGameClock()
        assertEquals(m.FADE_MS, c.endMs(false))
        assertEquals(m.newBestEndMs, c.endMs(true))
        assertFalse(c.isDone(m.FADE_MS - 1, false))
        assertTrue(c.isDone(m.FADE_MS, false))
    }
}
