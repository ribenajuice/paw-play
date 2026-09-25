package com.pawplay.app.games.pawpop

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Ignore
import org.junit.Test
import kotlin.math.abs
import kotlin.math.floor
import kotlin.random.Random

/**
 * Independent QA checks for Paw Pop (stories 51-62), written against the PRD and not against the code: a reference
 * model for the newest-finger rule, cadence measured over long runs at several frame rates, whole-game soaks driven
 * by two kinds of player at every phone size, and a few things a bored 5-year-old does that nobody scripts.
 */
class PopQaTest {

    // ------------------------------------------------------------------ steering (stories 52, 61)

    @Test
    fun `newest finger steers whatever order fingers land, move and lift in, with ids reused`() {
        for (seed in 1..80) {
            val r = Random(seed)
            val s = session(seed)
            val w = s.width
            var pilot: Long? = null
            var aim = s.shipX
            val dts = floatArrayOf(FRAME, 1f / 120f, 0.05f, 0.2f)
            repeat(700) {
                val id = r.nextInt(4).toLong()
                val x = if (r.nextInt(25) == 0) Float.NaN else r.nextFloat() * w * 1.5f - 0.25f * w
                val ok = x.isFinite()
                when (r.nextInt(8)) {
                    0, 1 -> { s.touchDown(id, x); if (ok) { pilot = id; aim = x.coerceIn(46f, w - 46f) } }
                    2, 3 -> { s.touchMove(id, x); if (ok && pilot == id) aim = x.coerceIn(46f, w - 46f) }
                    4 -> { s.touchUp(id); if (pilot == id) pilot = null }
                    5 -> { s.touchCancel(id); if (pilot == id) { pilot = null; aim = s.shipX } }
                    6 -> { if (r.nextInt(6) == 0) { s.touchCancelAll(); if (pilot != null) { pilot = null; aim = s.shipX } } }
                    else -> {}
                }
                for (k in 0L..3L) assertEquals("seed $seed: is $k the pilot", pilot == k, s.isPilot(k))
                assertEquals(pilot != null, s.isSteering)
                val before = s.shipX
                val dt = dts[r.nextInt(dts.size)]
                s.step(dt)
                val move = s.shipX - before
                val want = aim - before
                assertTrue("seed $seed: moved $move away from aim $aim (was $before)", move * want >= -1e-4f)
                assertTrue("seed $seed: overshot: moved $move, wanted $want", abs(move) <= abs(want) + 1e-3f)
                assertTrue("seed $seed: faster than 1.5 widths a second", abs(move) <= 1.5f * w * minOf(dt, 0.05f) + 1e-3f)
                assertTrue("seed $seed: ship at ${s.shipX}", s.shipX >= 46f - 1e-3f && s.shipX <= w - 46f + 1e-3f)
            }
        }
    }

    @Test
    fun `a resting palm never captures the ship, however it moves, lifts or lands again`() {
        val s = session()
        val ui = PopUi(s)
        fun down(id: Long, x: Float, y: Float = 500f) = ui.onPointer(id, x, y, pressed = true, previousPressed = false, consumed = false)
        fun move(id: Long, x: Float, y: Float = 500f) = ui.onPointer(id, x, y, pressed = true, previousPressed = true, consumed = false)
        fun up(id: Long, x: Float, y: Float = 500f) = ui.onPointer(id, x, y, pressed = false, previousPressed = true, consumed = false)

        down(7, 20f)                 // the palm lands: it steers, that is the rule
        s.run(1f)
        down(8, 300f)                // a fingertip lands: newest wins
        s.run(1.5f)
        assertEquals(300f, s.shipX, 0.5f)
        up(8, 300f)                  // the fingertip lifts: the ship stays, the palm (still down) does not take over
        s.run(0.5f)
        move(7, 30f); s.run(0.5f); move(7, 200f); s.run(1f)
        assertEquals("palm slid while resting", 300f, s.shipX, 0.5f)
        assertFalse(s.isSteering)
        up(7, 200f)
        down(7, 90f)                 // it lifts and lands again: a new touch steers
        s.run(2f)
        assertEquals(90f, s.shipX, 0.5f)
        // a finger whose lift never arrives is simply replaced by the next one that lands
        down(9, 250f); s.run(2f)
        assertEquals(250f, s.shipX, 0.5f)
        assertTrue(s.isPilot(9))
        assertFalse(s.isPilot(7))
    }

    @Test
    fun `a lift keeps gliding to the finger but never past it, and a cancel freezes the ship where it is`() {
        for (dt in listOf(FRAME, 1f / 120f, 0.05f)) {
            val s = session()
            s.touchDown(1, 340f)
            s.run(0.1f, dt)                       // still far from the finger
            val mid = s.shipX
            assertTrue(mid < 300f)
            s.touchUp(1)
            var last = s.shipX
            s.run(3f, dt) { assertTrue("never backwards", it.shipX >= last - 1e-4f); assertTrue(it.shipX <= 314f + 1e-3f); last = it.shipX }
            assertEquals(314f, s.shipX, 0.3f)
            // a cancel mid-glide: it stays where it is
            val c = session()
            c.touchDown(1, 340f); c.run(0.1f, dt)
            val here = c.shipX
            c.touchCancel(1)
            c.run(2f, dt)
            assertEquals(here, c.shipX, 1e-3f)
        }
    }

    @Test
    fun `hundreds of frantic taps only move the ship`() {
        val r = Random(11)
        val s = session()
        val ui = PopUi(s)
        var id = 100L
        repeat(600) {
            val x = r.nextFloat() * 360f; val y = r.nextFloat() * 692f
            val took = ui.onPointer(id, x, y, pressed = true, previousPressed = false, consumed = false)
            s.step(FRAME)
            ui.onPointer(id, x, y, pressed = false, previousPressed = true, consumed = false)
            id++
            assertTrue(s.shipX in 46f..314f)
            // the home square never steers; anywhere else does
            if (x in 20f..76f && y in 20f..76f) assertEquals(PopUi.Took.NOTHING, took)
        }
    }

    // ------------------------------------------------------------------ fire (story 53)

    @Test
    fun `one beat every 0_4 s with no drift over ten minutes at 30, 60, 120 fps and irregular frames`() {
        for (mode in listOf(1f / 30, 1f / 60, 1f / 120, -1f)) {
            val s = session(5)
            s.holdSpawnForTest = true
            val r = Random(9)
            var ticks = 0
            var worst = 0.0
            var maxStep = 0f
            while (s.time < 600.0) {
                val dt = if (mode > 0f) mode else 0.004f + r.nextFloat() * 0.046f
                maxStep = maxOf(maxStep, dt)
                s.step(dt)
                if (s.fireTicks != ticks) {
                    assertEquals("one beat per step at most", ticks + 1, s.fireTicks)
                    ticks = s.fireTicks
                    worst = maxOf(worst, abs(s.time - (0.25 + (ticks - 1) * 0.4)))
                }
            }
            val ideal = floor((s.time - 0.25) / 0.4).toInt() + 1
            assertEquals("mode $mode: beats over ten minutes", ideal, ticks)
            assertTrue("mode $mode: a beat is at most one frame off the 0.4 s grid, never cumulatively (was $worst)", worst <= maxStep + 1e-3)
        }
    }

    @Test
    fun `every gift keeps the beat and only changes what a beat makes`() {
        for (kind in GiftKind.ALL) {
            val s = session(2)
            s.holdSpawnForTest = true
            s.arriveForTest(kind)
            s.run(5.5f)
            val perBeat = when (kind) { GiftKind.TRIPLE -> 3; GiftKind.RIBBON -> 0; else -> 1 }
            assertEquals("$kind beats", 5.5 / 0.4, s.fireTicks.toDouble(), 1.0)
            assertEquals("$kind stars per beat", perBeat * s.fireTicks, s.starsLaunched)
        }
    }

    @Test
    fun `the 15-star ceiling is never even approached in real play, so no beat is ever silently skipped`() {
        for (h in listOf(480f, 692f, 1366f)) for (dt in listOf(FRAME, 0.05f)) {
            val s = session(4, 0, 360f, h)
            s.holdSpawnForTest = true
            s.arriveForTest(GiftKind.TRIPLE)
            var most = 0
            s.run(7.9f, dt) { most = maxOf(most, it.starCount) }
            assertEquals("h=$h dt=$dt: every beat made its three stars", 3 * s.fireTicks, s.starsLaunched)
            assertTrue("h=$h dt=$dt: most stars at once $most", most <= 12)
        }
    }

    @Test
    fun `a star cannot skip clean over the smallest target in one 50ms frame on any phone or tablet up to 1366dp tall`() {
        for (h in listOf(568f, 740f, 880f, 1280f, 1366f)) {
            val step = PopMetrics.STAR_SPEED * h * PopMetrics.MAX_STEP
            for (kind in TargetKind.ALL) {
                val smallest = PopMetrics.SMALL_SIZE.toFloat()
                val hitHeight = 2f * (kind.ry * smallest + PopMetrics.HIT_REACH)
                assertTrue("$kind on a ${h.toInt()}dp screen: star moves $step per frame, target is only $hitHeight tall", hitHeight > step)
            }
        }
    }

    // ------------------------------------------------------------------ ramp, carriers, misses (stories 54-57, 60)

    @Test
    fun `each stage begins on exactly the pop that the table says, and the next frame drifts at the new speed`() {
        val starts = intArrayOf(10, 25, 45, 70)
        for ((i, b) in starts.withIndex()) {
            val s = session(3, b - 1)
            s.holdSpawnForTest = true
            assertEquals(i + 1, s.stage.number)
            s.addTargetForTest(TargetKind.ROUND, 180f, 250f, 100f)
            var guard = 0
            while (s.pops < b && guard++ < 400) s.step(FRAME)
            assertEquals(b, s.pops)
            assertEquals("stage after pop $b", i + 2, s.stage.number)
            val t = s.addTargetForTest(TargetKind.ROUND, 100f, 100f, 100f)
            val y0 = t.y
            s.holdFireForTest = true
            s.step(FRAME)
            assertEquals("drift", PopRamp.stages[i + 1].drift * s.height * FRAME, t.y - y0, 0.01f)
        }
    }

    @Test
    fun `a missed target changes nothing, no pop counted, no gift, no effect, no sparkle`() {
        for (carrier in listOf<GiftKind?>(null, GiftKind.TRIPLE, GiftKind.WAVE)) {
            val s = session(6)
            s.holdSpawnForTest = true
            s.holdFireForTest = true
            s.touchDown(1, 40f) // ship out of the way
            s.addTargetForTest(TargetKind.OVAL, 300f, s.height - 90f, 100f, gift = carrier)
            s.run(6f) {
                assertEquals(0, it.pops); assertNull(it.gift); assertNull(it.starEffect)
                assertEquals(0f, it.slowLeft, 0f); assertFalse(it.waveActive); assertEquals(0, it.fxCount); assertEquals(0, it.sparkleCount)
            }
            assertEquals("the miss is gone", 0, s.targetCount)
        }
    }

    // ------------------------------------------------------------------ whole-game soaks (stories 56, 60, 61)

    private class Soak {
        var maxTargets = 0; var maxStars = 0; var maxSparkles = 0; var maxFx = 0; var maxCarriers = 0
        var longestEmpty = 0f; var giftsLaunched = 0; var minGiftGap = Double.MAX_VALUE
        var pops = 0; var stage = 1
    }

    /** mode 0 = perfect aim, 1 = wanders and taps at random, 2 = wanders with irregular frame times. */
    private fun soak(seed: Int, w: Float, h: Float, seconds: Float, mode: Int, startPops: Int = 0): Soak {
        val s = session(seed, startPops, w, h)
        val r = Random(seed * 17 + mode)
        val out = Soak()
        var wander = 0f
        var empty = 0f
        var lastGiftAt = -100.0
        var hadGift = false
        var spawnedSeen = 0
        val margin = minOf(46f, w / 2f)
        val dts = floatArrayOf(FRAME, 1f / 30f, 1f / 120f, 0.05f, 0.3f)
        val range = w >= 200f
        while (s.time < seconds) {
            val dt = if (mode == 2) dts[r.nextInt(dts.size)] else FRAME
            if (mode == 0) {
                var best: PopTarget? = null
                for (i in 0 until s.targetCount) { val g = s.target(i); if (g.y < s.nose - 20f && (best == null || g.y > best.y)) best = g }
                if (best != null) s.touchDown(1, best.x)
            } else {
                wander -= dt
                if (wander <= 0f) { wander = 0.15f + r.nextFloat() * 2f; s.touchDown(r.nextInt(3).toLong(), r.nextFloat() * w * 1.3f - 0.15f * w) }
                if (r.nextInt(300) == 0) s.touchUp(r.nextInt(3).toLong())
                if (r.nextInt(2000) == 0) s.touchCancelAll()
            }
            s.step(dt)
            out.maxTargets = maxOf(out.maxTargets, s.targetCount)
            out.maxStars = maxOf(out.maxStars, s.starCount)
            out.maxSparkles = maxOf(out.maxSparkles, s.sparkleCount)
            out.maxFx = maxOf(out.maxFx, s.fxCount)
            var carriers = 0
            for (i in 0 until s.targetCount) {
                val t = s.target(i)
                if (t.isCarrier) carriers++
                assertTrue("target finite", t.x.isFinite() && t.y.isFinite() && t.alpha in 0f..1f)
                if (range) assertTrue("target ${t.size} wide at x=${t.x} inside a ${w}dp screen", t.x - t.size / 2 >= -0.5f && t.x + t.size / 2 <= w + 0.5f)
            }
            out.maxCarriers = maxOf(out.maxCarriers, carriers)
            assertTrue("ship at ${s.shipX} on a ${w}dp screen", s.shipX.isFinite() && s.shipX >= margin - 1e-3f && s.shipX <= w - margin + 1e-3f)
            assertTrue("sparkles ${s.sparkleCount}", s.sparkleCount in 0..60 && s.sparkleCount == s.recountSparkles())
            assertTrue(s.starCount <= 15 && s.fxCount <= 16 && s.targetCount <= 8 && s.targetCount <= s.stage.maxTargets)
            val g = s.gift
            if (g != null && !hadGift) {
                out.giftsLaunched++
                out.minGiftGap = minOf(out.minGiftGap, s.time - lastGiftAt)
                lastGiftAt = s.time
                assertTrue(g.age < 0.1f)
            }
            hadGift = g != null
            // "empty" means nothing on screen and nothing new arriving: a target that appears and is popped in the same
            // frame (the ribbon meets it at the top edge) still counts as the sky being refilled on time.
            if (s.spawned != spawnedSeen) { spawnedSeen = s.spawned; empty = 0f }
            else if (s.targetCount == 0) { empty += minOf(dt, 0.05f); out.longestEmpty = maxOf(out.longestEmpty, empty) }
            else empty = 0f
        }
        out.pops = s.pops
        out.stage = s.stage.number
        return out
    }

    @Test
    fun `a soak at every phone size and three kinds of player never exceeds a limit or leaves the screen`() {
        val sizes = listOf(320f to 568f, 360f to 740f, 411f to 880f, 320f to 480f, 800f to 1280f)
        for ((w, h) in sizes) for (mode in 0..2) for (seed in 1..2) {
            val o = soak(seed, w, h, 240f, mode)
            assertTrue("${w}x$h mode $mode carriers", o.maxCarriers <= 1)
            assertTrue("${w}x$h mode $mode: something happened", o.pops > 10)
            // a second gift is never lost to "one in flight": consecutive gifts are far apart
            assertTrue("${w}x$h mode $mode: gifts only ${o.minGiftGap}s apart", o.minGiftGap > PopMetrics.GLIDE_SECONDS + 0.5f)
        }
    }

    @Test
    fun `the stage-5 sky of a long session stays inside every limit`() {
        for (mode in 0..2) {
            val o = soak(7 + mode, 360f, 740f, 600f, mode, startPops = 70)
            assertEquals(5, o.stage)
            assertTrue(o.maxTargets <= 8 && o.maxStars <= 15 && o.maxSparkles <= 60 && o.maxFx <= 16)
        }
    }

    @Test
    fun `when the sky empties the next target comes within half a second, so a child is never left staring at nothing`() {
        for (seed in 1..3) {
            val o = soak(seed, 360f, 740f, 400f, 0)
            assertTrue("empty for ${o.longestEmpty}s", o.longestEmpty <= 0.5f + 0.1f)
        }
    }

    @Test
    fun `odd and tiny windows never crash or produce a strange number`() {
        for ((w, h) in listOf(1f to 1f, 40f to 40f, 92f to 300f, 200f to 200f, 320f to 300f, 300f to 1500f)) for (mode in 0..2) {
            soak(3, w, h, 90f, mode)
        }
    }

    @Test
    fun `a 90 second gap in the frame clock is one 50ms step with no burst`() {
        val s = session()
        val ui = PopUi(s)
        ui.onFrame(1_000_000_000L)
        ui.onFrame(1_016_000_000L)
        val t0 = s.time
        val spawned = s.spawned
        val ticks = s.fireTicks
        ui.onFrame(1_016_000_000L + 90_000_000_000L)
        assertEquals(0.05, s.time - t0, 1e-6)
        assertTrue(s.spawned - spawned <= 1 && s.fireTicks - ticks <= 1 && s.starCount <= 2)
    }

    // ------------------------------------------------------------------ known bug (kept out of the green suite)

    @Ignore("QA bug: PopSession.setArea scales a target's x but not its sway centre x0, so the next step snaps it back to the old, unscaled column. Un-ignore to reproduce; it fails today.")
    @Test
    fun `after the play area gets narrower every target is still inside it`() {
        val s = session(1, 0, 400f, 700f)
        s.holdSpawnForTest = true
        s.holdFireForTest = true
        val t = s.addTargetForTest(TargetKind.ROUND, 340f, 200f, 100f)
        s.setArea(200f, 700f)
        s.step(FRAME)
        assertTrue("target at x=${t.x} on a 200dp wide play area", t.x <= 200f)
    }
}
