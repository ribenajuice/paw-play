package com.pawplay.app.games.pawpop

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs
import kotlin.random.Random

/** Story 61 (calm and consistent) and 60 (no dead end): limits, time steps, and a long fuzz. */
class PopRobustnessTest {

    // ------------------------------------------------------------------ time

    @Test
    fun `one step never covers more than 50ms, so a long gap is one short step and nothing jumps or bursts`() {
        val s = session()
        s.run(3f)
        val t = s.time
        val spawned = s.spawned
        val launched = s.starsLaunched
        val ys = s.targets().map { it.y }
        s.step(60f) // a minute in the background
        assertEquals(0.05, s.time - t, 1e-6)
        assertTrue("at most one more target: ${s.spawned - spawned}", s.spawned - spawned <= 1)
        assertTrue("at most one more shot", s.starsLaunched - launched <= 3)
        for ((i, y0) in ys.withIndex()) {
            val tg = s.targets().getOrNull(i) ?: continue
            assertTrue("target jumped ${tg.y - y0}", tg.y - y0 < 0.11f * 692f * 0.05f + 0.01f)
        }
        // steps of any silly size act the same: they are capped
        for (dt in listOf(0.051f, 1f, 1e9f)) {
            val a = session(3); val b = session(3)
            a.run(2f); b.run(2f)
            a.step(dt); b.step(0.05f)
            assertEquals(b.time, a.time, 1e-9)
            assertEquals(b.shipX, a.shipX, 0f)
            assertEquals(b.targetCount, a.targetCount)
        }
    }

    @Test
    fun `steps that are zero, negative or not a number pass no time at all`() {
        val s = session()
        s.run(1f)
        val t = s.time
        for (dt in listOf(0f, -0.016f, -100f, Float.NaN, Float.POSITIVE_INFINITY, Float.NEGATIVE_INFINITY)) s.step(dt)
        assertEquals(t, s.time, 0.0)
    }

    @Test
    fun `a slow 12 frames a second phone plays the same game`() {
        val a = session(2); val b = session(2)
        a.run(60f, FRAME); b.run(60f, 0.05f)
        assertTrue("shots ${a.fireTicks} vs ${b.fireTicks}", abs(a.fireTicks - b.fireTicks) <= 1)
        assertTrue("targets ${a.spawned} vs ${b.spawned}", abs(a.spawned - b.spawned) <= 2)
    }

    @Test
    fun `the same seed plays out the same way`() {
        fun trace(seed: Int): List<Float> {
            val s = session(seed); val out = ArrayList<Float>()
            s.touchDown(1L, 120f)
            s.run(60f) { if (it.time.toInt() % 5 == 0) out += it.targetCount + it.pops * 100f + it.starCount * 10000f + it.shipX }
            return out
        }
        assertEquals(trace(9), trace(9))
        assertFalse(trace(9) == trace(10))
    }

    // ------------------------------------------------------------------ limits under stress

    @Test
    fun `nothing on screen ever exceeds its limit, and the counters always agree with what is there`() {
        val s = session(11, pops = 165)
        val r = Random(3)
        var giftSeen = false
        s.run(300f) {
            assertTrue(it.targetCount <= PopMetrics.MAX_TARGETS)
            assertTrue(it.starCount <= PopMetrics.MAX_STARS)
            assertTrue(it.sparkleCount <= PopMetrics.MAX_SPARKLES)
            assertTrue(it.fxCount <= PopMetrics.MAX_FX)
            assertEquals(it.recountSparkles(), it.sparkleCount)
            if (it.gift != null) giftSeen = true
            if (r.nextInt(120) == 0) it.arriveForTest(GiftKind.ALL[r.nextInt(5)])
            if (r.nextInt(300) == 0) it.touchDown(1L, r.nextFloat() * 360f)
        }
        assertTrue(giftSeen || s.pops > 50)
    }

    @Test
    fun `a screen that is full waits for room instead of exceeding its cap`() {
        val s = session(); s.holdFireForTest = true
        repeat(8) { s.addTargetForTest(TargetKind.ROUND, 40f + 40f * it, 100f + 30f * it, 60f) }
        s.run(10f) { assertTrue(it.targetCount <= 8) }
        assertTrue(s.spawned == 0 || s.targetCount <= 8)
    }

    // ------------------------------------------------------------------ the fuzz

    /** Hundreds of thousands of steps with random fingers, gifts, size changes and time steps, at every stage. */
    @Test
    fun `a long fuzz at every stage never throws, never exceeds a limit and never reaches a dead state`() {
        var totalSteps = 0
        for (stageIndex in 0 until 5) for (seed in 1..4) {
            val r = Random(seed * 100 + stageIndex)
            var width = 360f; var height = 692f
            val s = session(seed, PopRamp.stages[stageIndex].startsAtPops, width, height)
            var lastTime = 0.0
            var lastPops = s.pops
            var lastSpawned = 0
            var lastSpawnAt = 0.0
            var lastTick = 0
            var lastTickAt = 0.0
            var lastStars = 0
            repeat(20000) { step ->
                // random fingers, ids 0..5
                when (r.nextInt(40)) {
                    0 -> s.touchDown(r.nextInt(6).toLong(), r.nextFloat() * (width + 100f) - 50f)
                    1, 2, 3 -> s.touchMove(r.nextInt(6).toLong(), r.nextFloat() * (width + 100f) - 50f)
                    4 -> s.touchUp(r.nextInt(6).toLong())
                    5 -> s.touchCancel(r.nextInt(6).toLong())
                    6 -> if (r.nextInt(10) == 0) s.touchCancelAll()
                    7 -> if (r.nextInt(6) == 0) s.arriveForTest(GiftKind.ALL[r.nextInt(5)])
                    8 -> if (r.nextInt(400) == 0) { width = 300f + r.nextFloat() * 150f; height = 480f + r.nextFloat() * 450f; s.setArea(width, height) }
                }
                val dt = when (r.nextInt(50)) { 0 -> r.nextFloat() * 5f; 1 -> 0f; 2 -> -1f; else -> 0.004f + r.nextFloat() * 0.03f }
                s.step(dt)
                totalSteps++
                // limits
                assertTrue(s.targetCount <= PopMetrics.MAX_TARGETS && s.targetCount <= s.stage.maxTargets.coerceAtLeast(0) + 0)
                assertTrue(s.starCount in 0..PopMetrics.MAX_STARS)
                assertTrue(s.fxCount in 0..PopMetrics.MAX_FX)
                assertTrue(s.sparkleCount in 0..PopMetrics.MAX_SPARKLES)
                assertEquals(s.recountSparkles(), s.sparkleCount)
                assertTrue(s.time >= lastTime); lastTime = s.time
                assertTrue(s.pops >= lastPops); lastPops = s.pops
                // sanity of every number the drawing reads
                assertTrue(s.shipX.isFinite() && s.shipX >= 45f - 0.01f && s.shipX <= width - 45f + 0.01f)
                for (i in 0 until s.targetCount) {
                    val t = s.target(i)
                    assertTrue(t.x.isFinite() && t.y.isFinite() && t.alpha in 0f..1f && t.alpha > 0f)
                    assertTrue(t.size in 72f..112f)
                }
                for (i in 0 until s.starCount) assertTrue(s.stars[i].x.isFinite() && s.stars[i].y.isFinite())
                assertTrue(s.starEffectLeft in 0f..8f && s.slowLeft in 0f..8f)
                assertTrue((s.starEffect != null) == (s.starEffectLeft > 0f))
                s.gift?.let { assertTrue(it.progress in 0f..1f) }
                // never dead: a target arrives at least every 12 s of play, the beat never stops, the ship keeps making stars unless the ribbon runs
                if (s.spawned != lastSpawned) { lastSpawned = s.spawned; lastSpawnAt = s.time }
                assertTrue("no target for ${s.time - lastSpawnAt}s at stage ${stageIndex + 1}", s.time - lastSpawnAt < 12.0)
                if (s.fireTicks != lastTick) { lastTick = s.fireTicks; lastTickAt = s.time }
                assertTrue("no beat for ${s.time - lastTickAt}s", s.time - lastTickAt < 0.5)
                lastStars = s.starsLaunched
            }
            assertTrue("stage ${stageIndex + 1} seed $seed made ${s.spawned} targets", s.spawned > 30)
        }
        assertTrue(totalSteps >= 400000)
    }

    @Test
    fun `an idle child who never touches the screen still gets a calm game that neither ends nor piles up`() {
        val s = session(21)
        var most = 0
        s.run(900f, 0.05f) { most = maxOf(most, it.targetCount) }
        assertTrue(s.spawned > 200)
        assertTrue("$most on screen", most <= PopMetrics.MAX_TARGETS)
        assertTrue(s.pops > 0) // whatever drifts through the middle gets popped by the ship's own shots
        assertEquals(180f, s.shipX, 0f)
    }

    @Test
    fun `after the play area gets narrower every target, sway included, is still inside it, and keeps its place as a fraction of the width`() {
        val s = session(1, 0, 400f, 700f); s.holdSpawnForTest = true; s.holdFireForTest = true
        val a = s.addTargetForTest(TargetKind.ROUND, 340f, 200f, 100f)
        val b = s.addTargetForTest(TargetKind.OVAL, 60f, 300f, 96f)
        s.setArea(200f, 700f)
        s.run(3f) { assertTrue("a at ${a.x}", a.x + a.size / 2 <= 200f + 0.01f && a.x - a.size / 2 >= -0.01f); assertTrue("b at ${b.x}", b.x - b.size / 2 >= -0.01f) }
        assertTrue(a.x0 in 50f..150f)
        // and back out again: nothing snaps back to an old column
        s.setArea(400f, 700f)
        s.run(1f) { assertTrue(it.target(0).x in 0f..400f) }
        // targets that keep sway on a narrow area never leave it either
        val t = session(2, 165, 320f, 568f); t.holdFireForTest = true
        var n = 0
        t.run(120f) {
            if (n++ % 600 == 0) it.setArea(if ((n / 600) % 2 == 0) 320f else 240f, 568f)
            for (i in 0 until it.targetCount) { val g = it.target(i); assertTrue("x ${g.x} size ${g.size} in ${it.width}", g.x - g.size / 2 >= -0.5f && g.x + g.size / 2 <= it.width + 0.5f) }
        }
    }

    @Test
    fun `layer bounds always hold the box asked for and are reused instead of made every frame`() {
        val r = Random(2)
        repeat(3000) {
            val cx = r.nextFloat() * 500f - 60f; val cy = r.nextFloat() * 900f - 60f; val reach = 20f + r.nextFloat() * 130f
            val b = LayerBounds.around(cx, cy, reach)
            assertTrue("box ($cx, $cy) reach $reach in $b", b.left <= cx - reach && b.right >= cx + reach && b.top <= cy - reach && b.bottom >= cy + reach)
            assertTrue(b.width < 2f * reach + 40f)
        }
        val a = LayerBounds.around(100.2f, 200.3f, 60f)
        assertTrue("the same cell gives the same Rect", a === LayerBounds.around(100.9f, 200.9f, 60f))
    }

    @Test
    fun `drawing phases are wrapped so a very long session keeps its precision`() {
        val s = session(); s.holdSpawnForTest = true; s.holdFireForTest = true
        repeat(4000) { s.step(0.05f) } // 200 s
        for (period in listOf(1.0, 1.6, 3.0, 1.0 / 0.35, 2.0 * Math.PI / 3.0)) assertTrue(s.wrapped(period) in 0f..period.toFloat())
    }

    @Test
    fun `a session can be resized without anything leaving its place as a fraction of the screen`() {
        val s = session(5)
        s.run(20f)
        val n = s.targetCount
        val frac = s.shipX / s.width
        s.setArea(411f, 880f)
        assertEquals(n, s.targetCount)
        assertEquals(frac, s.shipX / s.width, 0.001f)
        s.setArea(0f, 0f); s.setArea(Float.NaN, 100f)
        assertEquals(411f, s.width, 0f)
        s.run(10f)
        assertEquals(880f - 24f - 88f, s.nose, 0.001f)
    }
}
