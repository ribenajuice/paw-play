package com.pawplay.app.games.pawpop

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.tan

/** Stories 53 (the shot), 55 (pops), 58 (gifts glide), 59 (the five effects and how they overlap). */
class PopEffectsTest {

    /** A session with the ship in the middle and no random targets, so only what the test adds is there. */
    private fun bare(seed: Int = 1, pops: Int = 0, height: Float = 692f, width: Float = 360f): PopSession {
        val s = session(seed, pops, width, height)
        s.holdSpawnForTest = true // only what a test puts there is there
        s.step(FRAME)
        s.clearTargetsForTest()
        return s
    }

    /** Steps until [cond], failing after [limit] seconds. Returns how long it took. */
    private fun untilTrue(s: PopSession, limit: Float, cond: (PopSession) -> Boolean): Float {
        val t0 = s.time
        var steps = 0
        while (!cond(s)) {
            s.step(FRAME)
            check(++steps < limit / FRAME) { "condition not met in $limit s" }
        }
        return (s.time - t0).toFloat()
    }

    // ------------------------------------------------------------------ the shot

    @Test
    fun `one star every 0_4 seconds, whatever the frame rate, and the first within a moment`() {
        for (dt in listOf(1f / 30f, FRAME, 1f / 120f, 0.05f, 0.013f)) {
            val s = session(); s.holdFireForTest = true
            s.run(10f, dt)
            assertTrue("dt=$dt: ${s.fireTicks} shots in 10s", s.fireTicks in 24..25)
        }
    }

    @Test
    fun `the star is 28dp across and rises 0_9 screen heights a second from the ship's nose`() {
        for (h in listOf(568f, 692f, 880f)) {
            val s = bare(height = h)
            s.run(0.3f)
            assertEquals(1, s.starCount)
            assertEquals(14f, PopMetrics.STAR_RADIUS, 0f)
            val st = s.stars[0]
            assertEquals(s.shipX, st.x, 0.01f)
            val y0 = st.y
            s.holdFireForTest = true
            s.run(0.5f)
            assertEquals("height $h", 0.9f * h * 0.5f, y0 - st.y, 0.6f)
        }
    }

    @Test
    fun `a star disappears past the top edge, having touched nothing else`() {
        val s = bare(); s.holdFireForTest = false
        s.run(0.3f)
        assertEquals(1, s.starCount)
        s.holdFireForTest = true
        s.run(1.4f)
        assertEquals(0, s.starCount)
        assertEquals(0, s.pops)
    }

    @Test
    fun `the beat is the same with every power-up running, and only what it makes changes`() {
        for (kind in GiftKind.ALL) {
            val s = bare(); s.holdFireForTest = false
            s.arriveForTest(kind)
            val ticks = s.fireTicks
            s.run(4f)
            assertTrue("$kind: ${s.fireTicks - ticks} beats in 4s", s.fireTicks - ticks in 9..11)
        }
        val s = bare()
        s.arriveForTest(GiftKind.TRIPLE)
        val before = s.starsLaunched
        val beats = s.fireTicks
        s.run(2f)
        assertEquals("three stars a beat", 3 * (s.fireTicks - beats), s.starsLaunched - before)
        val r = bare(); r.arriveForTest(GiftKind.RIBBON)
        val launched = r.starsLaunched
        r.run(3f)
        assertEquals("the ribbon replaces the stars", 0, r.starsLaunched - launched)
        assertTrue(r.fireTicks >= 7)
    }

    @Test
    fun `triple star fans out straight and 12 degrees either side`() {
        val s = bare()
        s.arriveForTest(GiftKind.TRIPLE)
        s.run(0.3f)
        assertEquals(3, s.starCount)
        val angles = (0 until 3).map { s.stars[it].degrees }.sorted()
        assertEquals(listOf(-12f, 0f, 12f), angles)
        for (i in 0 until 3) {
            val st = s.stars[i]
            val want = 0.9f * 692f * tan(12f * PI.toFloat() / 180f)
            assertEquals(abs(st.degrees) / 12f * want, abs(st.vx), 0.5f)
            assertFalse(st.big)
        }
        // stars leaving sideways are cleaned up
        s.holdFireForTest = true
        s.run(2.5f)
        assertEquals(0, s.starCount)
    }

    @Test
    fun `no more than 15 stars, ever, even with the fan`() {
        val s = bare()
        s.arriveForTest(GiftKind.TRIPLE)
        var most = 0
        s.run(60f) { most = maxOf(most, it.starCount); if (it.starEffect == null) it.arriveForTest(GiftKind.TRIPLE) }
        assertTrue("$most stars", most in 1..PopMetrics.MAX_STARS)
    }

    // ------------------------------------------------------------------ pops

    @Test
    fun `one star pops one target and is used up`() {
        val s = bare()
        s.holdFireForTest = false
        // two targets stacked exactly in the star's path
        s.addTargetForTest(TargetKind.ROUND, s.shipX, s.nose - 100f, 100f)
        s.addTargetForTest(TargetKind.ROUND, s.shipX, s.nose - 200f, 100f)
        s.run(0.35f)
        assertEquals(1, s.pops)
        assertEquals(1, s.targetCount)
        assertEquals("the star that popped it is gone, the next is not yet there", 0, s.starCount)
        s.run(0.8f)
        assertEquals(2, s.pops)
    }

    @Test
    fun `a star is a hit within 8dp of the drawn edge and a miss beyond it`() {
        // a round target of 100 across; its centre is 200 above the ship's line. A star that stays 8dp outside the edge hits.
        fun popped(offset: Float): Boolean {
            val s = bare()
            val t = s.addTargetForTest(TargetKind.ROUND, s.shipX + 50f + offset, s.nose - 200f, 100f)
            s.run(1.2f)
            return s.pops == 1 && s.targets().none { it === t }
        }
        assertTrue(popped(7f))
        assertFalse(popped(9f))
        assertTrue(popped(-40f))
    }

    @Test
    fun `a pop is at once, and its picture is short and calm`() {
        val s = bare()
        s.addTargetForTest(TargetKind.ROUND, s.shipX, s.nose - 100f, 100f)
        untilTrue(s, 1f) { it.pops == 1 }
        assertEquals(0, s.targetCount)
        assertEquals(1, s.fxCount)
        val f = s.fx[0]
        assertEquals(0.35f, f.duration, 0f)
        assertEquals(7, f.sparkles)
        s.holdFireForTest = true
        s.run(0.4f)
        assertEquals(0, s.fxCount)
        assertEquals(0, s.sparkleCount)
        // a critter takes 0.6 seconds
        val c = bare()
        c.addTargetForTest(TargetKind.CRITTER, c.shipX, c.nose - 100f, 100f)
        untilTrue(c, 1f) { it.pops == 1 }
        assertEquals(0.6f, c.fx[0].duration, 0f)
    }

    // ------------------------------------------------------------------ gifts

    @Test
    fun `a popped carrier releases its gift, which glides to the ship by itself in about 0_6 seconds`() {
        val s = bare(); s.holdFireForTest = true
        s.touchDown(1L, 250f); s.run(2f); s.touchUp(1L) // the finger is gone from now on
        val carrier = s.addTargetForTest(TargetKind.OVAL, 250f, 200f, 100f, gift = GiftKind.BIG)
        s.holdFireForTest = false
        val cx = carrier.x
        assertNull(s.gift)
        untilTrue(s, 2f) { it.pops == 1 }
        val g = s.gift
        assertNotNull(g)
        assertEquals(GiftKind.BIG, g!!.kind)
        assertEquals(cx, g.x0, 0.01f)
        assertEquals(carrier.y, g.y0, 0.01f)
        val took = untilTrue(s, 2f) { it.gift == null }
        assertEquals(0.6f, took, 0.05f)
        assertEquals(GiftKind.BIG, s.starEffect)
        assertEquals(8f, s.starEffectLeft, 0.1f)
        assertEquals(250f, s.shipX, 0.5f)
    }

    @Test
    fun `the glide curve starts at the carrier, ends at the ship's nose, eases in and out`() {
        assertEquals(0f, PopGlide.ease(0f), 0f)
        assertEquals(0.5f, PopGlide.ease(0.5f), 1e-6f)
        assertEquals(1f, PopGlide.ease(1f), 0f)
        assertEquals(0.125f, PopGlide.ease(0.25f), 1e-6f)
        assertEquals(0.875f, PopGlide.ease(0.75f), 1e-6f)
        val x0 = 100f; val x1 = 250f; val y0 = 200f; val y1 = 560f
        assertEquals(x0, PopGlide.x(x0, x1, 0f), 1e-4f); assertEquals(y0, PopGlide.y(y0, y1, 0f), 1e-4f)
        assertEquals(x1, PopGlide.x(x0, x1, 1f), 1e-4f); assertEquals(y1, PopGlide.y(y0, y1, 1f), 1e-4f)
        // half way: e = 0.5, control point (x0 + 0.2 dx, y0 - 26)
        assertEquals(x0 + 0.35f * (x1 - x0), PopGlide.x(x0, x1, 0.5f), 1e-3f)
        assertEquals(0.25f * y0 + 0.5f * (y0 - 26f) + 0.25f * y1, PopGlide.y(y0, y1, 0.5f), 1e-3f)
        // it floats up a little first
        assertTrue(PopGlide.y(y0, y1, 0.2f) < y0 + 0.5f * (y1 - y0))
        var lastY = -1f
        for (i in 0..20) { val y = PopGlide.y(y0, y1, i / 20f); assertTrue(y >= lastY - 40f); lastY = y }
        assertTrue((0..20).all { PopGlide.y(y0, y1, it / 20f) < y1 + 0.5f })
    }

    @Test
    fun `the gift finds the ship wherever the ship has gone`() {
        val s = bare(); s.holdFireForTest = true
        s.touchDown(1L, 100f); s.run(2f)
        val carrier = s.addTargetForTest(TargetKind.ROUND, 100f, 200f, 100f, gift = GiftKind.SLOW)
        s.holdFireForTest = false
        untilTrue(s, 2f) { it.pops == 1 }
        s.touchDown(2L, 300f) // the child slides away while the gift glides
        untilTrue(s, 2f) { it.gift == null }
        s.run(2f)
        assertTrue("slow drift is running: ${s.slowLeft}", s.slowLeft > 5f && s.slowLeft <= 8f)
        assertEquals(GiftKind.SLOW, carrier.gift)
        assertEquals(300f, s.shipX, 0.5f)
    }

    @Test
    fun `only one gift can be in flight, and a second carrier popped meanwhile just pops`() {
        val s = bare(); s.holdFireForTest = false
        s.addTargetForTest(TargetKind.ROUND, s.shipX, s.nose - 100f, 100f, gift = GiftKind.TRIPLE)
        s.addTargetForTest(TargetKind.ROUND, s.shipX, s.nose - 260f, 100f, gift = GiftKind.SLOW)
        var most = 0
        s.run(1.5f) { most = maxOf(most, if (it.gift != null) 1 else 0) }
        assertEquals(2, s.pops)
        assertEquals(1, most)
        assertEquals(GiftKind.TRIPLE, s.starEffect)
        assertEquals("the second gift was not made", 0f, s.slowLeft, 0f)
    }

    @Test
    fun `arrival gives the ship a ring and six sparkles`() {
        val s = bare(); s.holdFireForTest = true
        s.arriveForTest(GiftKind.TRIPLE)
        assertEquals(1, s.fxCount)
        assertTrue(s.fx[0].arrival)
        assertEquals(6, s.fx[0].sparkles)
        assertEquals(6, s.sparkleCount)
    }

    // ------------------------------------------------------------------ the effects

    @Test
    fun `each effect lasts its own time, triple 8 s, big 8 s, ribbon 6 s, slow 8 s`() {
        for ((kind, seconds) in listOf(GiftKind.TRIPLE to 8f, GiftKind.BIG to 8f, GiftKind.RIBBON to 6f, GiftKind.SLOW to 8f)) {
            val s = bare(); s.holdFireForTest = true
            s.arriveForTest(kind)
            val on: (PopSession) -> Boolean = { if (kind == GiftKind.SLOW) it.slowLeft > 0f else it.starEffect == kind }
            s.run(seconds - 0.1f)
            assertTrue("$kind still on at ${seconds - 0.1f}", on(s))
            s.run(0.2f)
            assertFalse("$kind over after $seconds", on(s))
        }
    }

    @Test
    fun `big star is 56dp, flies through everything in its path and is not used up`() {
        val s = bare(); s.arriveForTest(GiftKind.BIG)
        s.run(0.3f)
        val st = s.stars[0]
        assertTrue(st.big)
        assertEquals(28f, PopMetrics.BIG_STAR_RADIUS, 0f)
        s.holdFireForTest = true
        s.addTargetForTest(TargetKind.ROUND, s.shipX, s.nose - 200f, 100f)
        s.addTargetForTest(TargetKind.OVAL, s.shipX, s.nose - 300f, 100f)
        s.addTargetForTest(TargetKind.HEART, s.shipX + 60f, s.nose - 380f, 100f) // beside the path: only the wide reach gets it
        s.run(1.2f)
        assertEquals(3, s.pops)
        assertEquals(0, s.targetCount)
        // an ordinary star would have stopped at the first
        val n = bare(); n.holdFireForTest = false
        n.addTargetForTest(TargetKind.ROUND, n.shipX, n.nose - 200f, 100f)
        n.addTargetForTest(TargetKind.OVAL, n.shipX, n.nose - 300f, 100f)
        n.run(0.9f)
        assertEquals(1, n.pops)
    }

    @Test
    fun `the ribbon replaces the stars, grows from the nose and pops what it touches as the ship slides`() {
        val s = bare(); s.holdFireForTest = true
        s.arriveForTest(GiftKind.RIBBON)
        assertEquals(0f, s.ribbonLength, 0f)
        s.step(0.05f)
        assertEquals(60f, s.ribbonLength, 0.5f) // 1200 dp a second
        s.run(1f)
        assertEquals(s.nose + 10f, s.ribbonLength, 0.5f)
        s.clearTargetsForTest()
        s.addTargetForTest(TargetKind.ROUND, s.shipX + 20f, 250f, 100f)   // in the 56dp column
        s.addTargetForTest(TargetKind.ROUND, s.shipX + 140f, 250f, 100f)  // well outside it
        s.step(FRAME)
        assertEquals(1, s.pops)
        assertEquals(1, s.targetCount)
        // slide the ship under the second: the ribbon follows and pops it
        s.touchDown(1L, s.target(0).x)
        s.run(2f)
        assertEquals(2, s.pops)
        // a target below the ship's nose is behind it and is not popped
        s.clearTargetsForTest()
        s.addTargetForTest(TargetKind.ROUND, s.shipX, s.nose + 30f, 100f)
        s.step(FRAME)
        assertEquals(2, s.pops)
    }

    @Test
    fun `the ribbon waits for the beat to come back after it ends`() {
        val s = bare(); s.holdFireForTest = false
        s.arriveForTest(GiftKind.RIBBON)
        s.run(6.5f)
        assertNull(s.starEffect)
        val launched = s.starsLaunched
        s.run(1f)
        assertTrue(s.starsLaunched - launched >= 2)
    }

    @Test
    fun `slow drift halves every target's speed and runs alongside another effect`() {
        val s = bare(); s.holdFireForTest = true
        val t = s.addTargetForTest(TargetKind.ROUND, 300f, 100f, 90f)
        s.arriveForTest(GiftKind.TRIPLE)
        s.arriveForTest(GiftKind.SLOW)
        assertEquals(GiftKind.TRIPLE, s.starEffect)
        assertTrue(s.slowLeft > 7.9f)
        val y = t.y
        s.run(1f)
        assertEquals(0.07f * 692f * 0.5f, t.y - y, 0.6f)
        s.run(8f)
        assertEquals(0f, s.slowLeft, 0f)
        val y2 = t.y
        s.run(0.5f)
        assertEquals("back to full speed: half a second covers half a second's drift", 0.07f * 692f * 0.5f, t.y - y2, 0.6f)
    }

    @Test
    fun `sparkle wave pops the ordinary targets one after another as it rises, skipping carriers`() {
        val s = bare(); s.holdFireForTest = true
        s.addTargetForTest(TargetKind.ROUND, 100f, 500f, 90f)
        s.addTargetForTest(TargetKind.OVAL, 260f, 400f, 90f)
        s.addTargetForTest(TargetKind.HEART, 120f, 300f, 90f)
        s.addTargetForTest(TargetKind.ROUND, 240f, 150f, 90f)
        val carrier = s.addTargetForTest(TargetKind.ROUND, 180f, 250f, 100f, gift = GiftKind.TRIPLE)
        s.arriveForTest(GiftKind.WAVE)
        assertTrue(s.waveActive)
        val popTimes = ArrayList<Float>()
        var seen = 0
        val t0 = s.time
        s.run(1.6f) { ss -> if (ss.pops > seen) { repeat(ss.pops - seen) { popTimes += (ss.time - t0).toFloat() }; seen = ss.pops } }
        assertEquals(4, s.pops)
        assertTrue("the carrier waited for a star or the ribbon", s.targets().any { it === carrier })
        assertNull(s.gift)
        assertTrue("staggered over the wave, not all at once: $popTimes", popTimes.max() - popTimes.min() > 0.5f)
        assertEquals(popTimes.sorted(), popTimes)
        assertFalse("the wave is done in about 1.5 s", s.waveActive)
    }

    @Test
    fun `the wave takes about 1_5 seconds to cross and only pops when 3 or more targets are on screen`() {
        val s = bare(); s.holdFireForTest = true
        s.addTargetForTest(TargetKind.ROUND, 100f, 300f, 90f)
        s.addTargetForTest(TargetKind.ROUND, 250f, 200f, 90f)
        s.arriveForTest(GiftKind.WAVE)
        val took = untilTrue(s, 3f) { !it.waveActive }
        assertEquals(1.5f, took, 0.05f)
        assertEquals("two targets: it glitters and pops nothing", 0, s.pops)
        assertEquals(2, s.targetCount)
        // exactly three is enough
        val t = bare(); t.holdFireForTest = true
        repeat(3) { t.addTargetForTest(TargetKind.ROUND, 80f + 100f * it, 200f + 60f * it, 90f) }
        t.arriveForTest(GiftKind.WAVE); t.run(1.8f)
        assertEquals(3, t.pops)
        // the count is taken when the gift arrives, not later
        val u = bare(); u.holdFireForTest = true
        u.addTargetForTest(TargetKind.ROUND, 80f, 200f, 90f); u.addTargetForTest(TargetKind.ROUND, 180f, 260f, 90f)
        u.arriveForTest(GiftKind.WAVE)
        u.addTargetForTest(TargetKind.ROUND, 280f, 300f, 90f)
        u.run(1.8f)
        assertEquals(0, u.pops)
    }

    @Test
    fun `the wave is instant and it changes no effect clock`() {
        val s = bare(); s.holdFireForTest = true
        s.arriveForTest(GiftKind.TRIPLE); s.arriveForTest(GiftKind.SLOW)
        s.run(3f)
        val a = s.starEffectLeft; val b = s.slowLeft
        s.arriveForTest(GiftKind.WAVE)
        assertEquals(a, s.starEffectLeft, 0f); assertEquals(b, s.slowLeft, 0f)
        assertEquals(GiftKind.TRIPLE, s.starEffect)
    }

    // ------------------------------------------------------------------ overlap

    @Test
    fun `triple, big and ribbon never stack, a new one replaces the current one and starts its own full time`() {
        val s = bare(); s.holdFireForTest = true
        s.arriveForTest(GiftKind.TRIPLE); s.run(5f)
        s.arriveForTest(GiftKind.BIG)
        assertEquals(GiftKind.BIG, s.starEffect)
        assertEquals(8f, s.starEffectLeft, 0.001f)
        s.run(2f)
        s.arriveForTest(GiftKind.RIBBON)
        assertEquals(GiftKind.RIBBON, s.starEffect)
        assertEquals(6f, s.starEffectLeft, 0.001f)
        assertEquals(0f, s.ribbonLength, 0f)
        s.run(1f)
        s.arriveForTest(GiftKind.TRIPLE)
        assertEquals(8f, s.starEffectLeft, 0.001f)
        // nothing but the newest is running: after 8 s all is over
        s.run(8.1f)
        assertNull(s.starEffect)
    }

    @Test
    fun `the same gift again restarts its time, with no level 2`() {
        for (kind in listOf(GiftKind.TRIPLE, GiftKind.BIG, GiftKind.RIBBON, GiftKind.SLOW)) {
            val s = bare(); s.holdFireForTest = true
            s.arriveForTest(kind); s.run(4f)
            s.arriveForTest(kind)
            val left = if (kind == GiftKind.SLOW) s.slowLeft else s.starEffectLeft
            assertEquals(kind.seconds, left, 0.001f)
            s.run(kind.seconds - 0.2f)
            val still = if (kind == GiftKind.SLOW) s.slowLeft > 0f else s.starEffect == kind
            assertTrue("$kind", still)
        }
        val s = bare(); s.holdFireForTest = false
        s.arriveForTest(GiftKind.TRIPLE); s.arriveForTest(GiftKind.TRIPLE)
        val ticks = s.fireTicks; val launched = s.starsLaunched
        s.run(2f)
        assertEquals("still three, never more", 3 * (s.fireTicks - ticks), s.starsLaunched - launched)
    }

    @Test
    fun `slow drift sits alongside each star effect and neither disturbs the other`() {
        for (kind in listOf(GiftKind.TRIPLE, GiftKind.BIG, GiftKind.RIBBON)) {
            val s = bare(); s.holdFireForTest = true
            s.arriveForTest(kind); s.arriveForTest(GiftKind.SLOW)
            s.run(2f)
            assertEquals(kind, s.starEffect)
            assertEquals(6f + if (kind == GiftKind.RIBBON) 0f else 2f - 0f, s.starEffectLeft + 0f, 2.1f)
            assertEquals(6f, s.slowLeft, 0.05f)
        }
    }

    // ------------------------------------------------------------------ the glow

    @Test
    fun `the glow is steady, then breathes once a second between 100 and 35 percent for the last 2 seconds, then fades`() {
        assertEquals(1f, glowBreath(8f), 0f)
        assertEquals(1f, glowBreath(2.01f), 0f)
        assertEquals(1f, glowBreath(2f), 1e-5f)
        assertEquals(0.35f, glowBreath(1.5f), 1e-5f)
        assertEquals(1f, glowBreath(1f), 1e-5f)
        assertEquals(0.35f, glowBreath(0.5f), 1e-5f)
        assertEquals(0.675f, glowBreath(0.25f), 1e-5f)
        assertEquals(0f, glowBreath(0f), 1e-6f)
        assertEquals(0f, glowBreath(-1f), 1e-6f)
        var lowest = 1f
        var r = 2f
        while (r > 0.25f) { lowest = minOf(lowest, glowBreath(r)); r -= 0.005f }
        assertEquals(0.35f, lowest, 0.001f)
        // the fade is smooth: no jump larger than a few percent between frames anywhere
        var last = glowBreath(3f)
        r = 3f
        while (r > 0f) { r -= 0.001f; val b = glowBreath(r); assertTrue("jump at $r", abs(b - last) < 0.04f); last = b }
        // it never exceeds 1 or goes below 0
        r = 4f
        while (r > -0.5f) { val b = glowBreath(r); assertTrue(b in 0f..1f); r -= 0.01f }
    }

    @Test
    fun `no effect makes anything flash faster than 3 times a second`() {
        // the fastest thing in the code is the breath, one full cycle a second; a cycle is a rise and a fall
        var crossings = 0
        var above = glowBreath(2f) > 0.675f
        var r = 2f
        while (r > 0.3f) { r -= 0.001f; val a = glowBreath(r) > 0.675f; if (a != above) { crossings++; above = a } }
        assertTrue("$crossings crossings of the midline in 1.7 s", crossings <= 4)
    }

    // ------------------------------------------------------------------ limits

    @Test
    fun `sparkles never go over 60, a pop over the limit just has none, and the total is always right`() {
        val s = bare(); s.holdFireForTest = true
        s.arriveForTest(GiftKind.BIG); s.holdFireForTest = false
        // 8 targets in the path, then 8 more a moment later
        var most = 0
        repeat(8) { s.addTargetForTest(TargetKind.ROUND, s.shipX, s.nose - 100f - 60f * it, 90f) }
        s.run(0.1f)
        repeat(8) { s.addTargetForTest(TargetKind.ROUND, s.shipX, s.nose - 100f - 60f * it, 90f) }
        s.run(1.0f) { most = maxOf(most, it.sparkleCount); assertEquals(it.recountSparkles(), it.sparkleCount); assertTrue(it.sparkleCount <= 60) }
        assertTrue("most $most", most > 40)
        assertTrue(s.pops >= 12)
        assertEquals(0, s.recountSparkles() - s.sparkleCount)
    }

    @Test
    fun `the wave's own sparkles count against the limit too`() {
        val s = bare(); s.holdFireForTest = true
        repeat(3) { s.addTargetForTest(TargetKind.ROUND, 60f + 100f * it, 100f + 50f * it, 90f) }
        s.arriveForTest(GiftKind.WAVE)
        assertTrue(s.waveSparkly)
        assertEquals(6 + 10, s.sparkleCount)
        s.run(2f)
        assertEquals(0, s.sparkleCount)
        assertFalse(s.waveSparkly)
    }
}
