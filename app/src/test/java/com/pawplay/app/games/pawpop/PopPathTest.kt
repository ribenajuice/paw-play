package com.pawplay.app.games.pawpop

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.sqrt

/**
 * Stories 66 and 67: straight legs with sharp alternating turns, the angle and run length per stage, the speed cap and the
 * per-turn re-pick, the 8dp side rule and the 0.9s turn gap, and entering from above the entry line, where nothing can pop a
 * target until its whole picture is below it. Replaces the old sine-sway tests.
 */
class PopPathTest {

    /** Every step of a session, watching every target; one record per target instance. */
    private class Track(val t: PopTarget) {
        var lastX = t.x; var lastY = t.y
        var lastVx = Float.NaN; var lastVy = Float.NaN
        val turnTimes = ArrayList<Double>()
        val turnAngles = ArrayList<Float>()     // degrees from straight down, right positive
        val legLengths = ArrayList<Double>()    // seconds between one turn and the next (full legs only)
        val speedsAtTurn = ArrayList<Float>()   // downward speed (sh/s) of each new leg
        var enteredAt = Double.NaN
        var firstSide = 0
        var lastSeenTurns = 0
    }

    private fun watch(seed: Int, pops: Int, seconds: Float, width: Float = 360f, height: Float = 692f, slowAlways: Boolean = false, each: (PopSession, Track) -> Unit = { _, _ -> }): List<Track> {
        val s = sessionNoLoss(seed, pops, width, height)
        s.holdFireForTest = true
        val tracks = java.util.IdentityHashMap<PopTarget, Track>()
        val order = ArrayList<Track>()
        s.run(seconds) {
            if (slowAlways && it.slowLeft < 1f) it.arriveForTest(GiftKind.SLOW)
            for (i in 0 until it.targetCount) {
                val t = it.target(i)
                val tr = tracks.getOrPut(t) { Track(t).also { n -> order += n } }
                if (tr.enteredAt.isNaN() && t.entered) tr.enteredAt = it.time
                if (t.turns != tr.lastSeenTurns) {
                    tr.lastSeenTurns = t.turns
                    tr.turnTimes += it.time
                    tr.turnAngles += (atan2(t.vx, t.vy) * 180.0 / PI).toFloat()
                    tr.speedsAtTurn += t.speed
                    if (tr.turnTimes.size == 1) tr.firstSide = t.side
                }
                each(it, tr)
            }
        }
        return order
    }

    // ------------------------------------------------------------------ legs are straight

    @Test
    fun `between turns a target moves in a perfectly straight line at constant velocity, never up, never stopping`() {
        for (stage in 0 until 5) for (seed in 1..3) {
            val s = sessionNoLoss(seed, PopRamp.stages[stage].startsAtPops)
            s.holdFireForTest = true
            val seen = java.util.IdentityHashMap<PopTarget, FloatArray>() // last vx, vy, turns
            s.run(120f) {
                for (i in 0 until it.targetCount) {
                    val t = it.target(i)
                    val prev = seen[t]
                    if (prev != null && prev[1] > 0f && prev[2].toInt() == t.turns && t.x > 40f + t.size / 2f && t.x < it.width - 40f - t.size / 2f) {
                        assertEquals("stage ${stage + 1}: vx changed without a turn", prev[0], t.vx, 1e-3f)
                        assertEquals("stage ${stage + 1}: vy changed without a turn", prev[1], t.vy, 1e-3f)
                    }
                    if (prev != null) assertTrue("always heading down, never stopping: vy ${t.vy}", t.vy > 5f)
                    seen[t] = floatArrayOf(t.vx, t.vy, t.turns.toFloat())
                }
            }
            assertTrue(seen.size > 20)
        }
    }

    @Test
    fun `a turn is instant and sharp, to the stage's angle from straight down, and the sides alternate`() {
        for (stage in 0 until 5) {
            val want = PopRamp.stages[stage].turnDegrees
            var checked = 0
            for (tr in watch(3, PopRamp.stages[stage].startsAtPops, 200f)) {
                for ((k, a) in tr.turnAngles.withIndex()) {
                    assertEquals("stage ${stage + 1} turn $k", want, abs(a), 0.05f)
                    if (k > 0) assertNotEquals("stage ${stage + 1}: two turns to the same side in a row", Math.signum(tr.turnAngles[k - 1]), Math.signum(a))
                    checked++
                }
            }
            assertTrue("stage ${stage + 1} made only $checked turns", checked > 15)
        }
    }

    @Test
    fun `the first side is random, so some targets zig right first and some left first`() {
        val first = watch(4, 0, 600f).filter { it.turnAngles.isNotEmpty() }.map { Math.signum(it.turnAngles[0]) }
        assertTrue("only ${first.size} targets turned", first.size > 100)
        val right = first.count { it > 0 }
        assertTrue("right first $right of ${first.size}", right in (first.size * 0.3).toInt()..(first.size * 0.7).toInt())
    }

    @Test
    fun `straight runs are as long as the stage says`() {
        for (stage in 0 until 5) {
            val st = PopRamp.stages[stage]
            val runs = ArrayList<Double>()
            for (tr in watch(5, st.startsAtPops, 300f)) {
                // legs that ended by a turn well inside the sides (an early turn at a side edge is shorter by design)
                for (k in 1 until tr.turnTimes.size) {
                    val leg = tr.turnTimes[k] - tr.turnTimes[k - 1]
                    if (leg >= st.runMin - 0.03) runs += leg
                }
                if (tr.turnTimes.isNotEmpty() && !tr.enteredAt.isNaN()) {
                    val firstLeg = tr.turnTimes[0] - tr.enteredAt
                    assertTrue("stage ${stage + 1}: first leg $firstLeg after entry is under the run minimum", firstLeg >= st.runMin - 0.06)
                    assertTrue("stage ${stage + 1}: first leg $firstLeg after entry", firstLeg <= st.runMax + 0.06)
                }
            }
            assertTrue("stage ${stage + 1}: too few legs ${runs.size}", runs.size > 10)
            assertTrue("stage ${stage + 1} longest leg ${runs.max()}", runs.max() <= st.runMax + 0.06)
            assertTrue("stage ${stage + 1} shortest ${runs.min()}", runs.min() >= st.runMin - 0.06)
        }
    }

    // ------------------------------------------------------------------ the 0.9s gap, the sides, the cap

    @Test
    fun `no two turns are closer than 0_9 seconds, at every stage and many seeds, and the first turn is over half a second after entry`() {
        var closest = Double.MAX_VALUE
        var turns = 0
        for (stage in 0 until 5) for (seed in 1..8) {
            for (tr in watch(seed, PopRamp.stages[stage].startsAtPops, 150f)) {
                for (k in 1 until tr.turnTimes.size) closest = minOf(closest, tr.turnTimes[k] - tr.turnTimes[k - 1])
                if (tr.turnTimes.isNotEmpty()) assertTrue("first turn ${tr.turnTimes[0] - tr.enteredAt}s after entry", tr.turnTimes[0] - tr.enteredAt >= PopMetrics.NO_TURN_AFTER_ENTRY - 0.03)
                turns += tr.turnTimes.size
            }
        }
        assertTrue("only $turns turns seen", turns > 1000)
        assertTrue("two turns $closest s apart", closest >= PopMetrics.MIN_TURN_GAP - 0.03)
    }

    @Test
    fun `no target ever comes within 8dp of a side edge, at every stage and phone width, and an early turn happens instead`() {
        var early = 0
        for (stage in 0 until 5) for (w in listOf(320f, 360f, 411f)) for (seed in 1..4) {
            for (tr in watch(seed, PopRamp.stages[stage].startsAtPops, 150f, width = w) { s, t ->
                val g = t.t
                assertTrue("stage ${stage + 1} at ${g.x} size ${g.size} on $w", g.x - g.size / 2f >= PopMetrics.EDGE_CLEAR - 0.01f && g.x + g.size / 2f <= w - PopMetrics.EDGE_CLEAR + 0.01f)
            }) {
                // a leg that ended before its run was up turned early at a side
                early += tr.turnTimes.size
            }
        }
        assertTrue(early > 500)
    }

    @Test
    fun `a target heading for a side edge turns away at it, not through it, and never slides off`() {
        val s = sessionNoLoss(1); s.holdSpawnForTest = true; s.holdFireForTest = true
        val t = s.addTargetForTest(TargetKind.ROUND, 100f, 300f, 80f, straight = false)
        t.side = -1; t.legTan = 1f; t.legCos = 0.7071f; t.legLeft = 100f; t.sinceTurn = 5f; t.sinceEntered = 5f; t.speed = 0.1f
        var turnedAt = Float.NaN
        s.run(3f) { if (t.side == 1 && turnedAt.isNaN()) turnedAt = t.x }
        assertFalse("it turned", turnedAt.isNaN())
        assertTrue("turned at x=$turnedAt, body edge ${turnedAt - 40f}", turnedAt - 40f >= 8f - 0.01f && turnedAt - 40f < 20f)
        assertTrue(t.x - 40f >= 8f - 0.01f)
    }

    @Test
    fun `if a turn was too recent, a target at a wall waits there until the gap has passed and then turns away`() {
        val s = sessionNoLoss(1); s.holdSpawnForTest = true; s.holdFireForTest = true
        val t = s.addTargetForTest(TargetKind.ROUND, 60f, 300f, 80f, straight = false)
        t.side = -1; t.legTan = 1f; t.legCos = 0.7071f; t.legLeft = 100f; t.sinceEntered = 5f; t.speed = 0.1f
        t.sinceTurn = 0f // it has just turned, and the wall is right there
        s.run(0.5f) { assertTrue("at the wall, never past it: ${t.x}", t.x - 40f >= 8f - 0.01f) }
        assertEquals(48f, t.x, 0.01f)
        assertTrue("still heading down along the wall", t.vy > 0f)
        assertEquals("no turn yet", -1, t.side)
        s.run(0.6f)
        assertEquals("turned away from the wall once 0.9s had passed", 1, t.side)
        assertTrue(t.x > 48f)
    }

    @Test
    fun `along its path a target never moves faster than 0_2 screen heights a second, at any stage, speed or slow drift`() {
        for (h in listOf(568f, 692f, 880f)) for (stage in 0 until 5) for (slow in listOf(false, true)) {
            var fastest = 0f
            watch(2, PopRamp.stages[stage].startsAtPops, 100f, height = h, slowAlways = slow) { _, tr ->
                val g = tr.t
                val v = sqrt(g.vx * g.vx + g.vy * g.vy)
                fastest = maxOf(fastest, v)
            }
            assertTrue("stage ${stage + 1} at height $h slow=$slow: $fastest dp/s", fastest <= PopMetrics.MAX_PATH_SPEED * h + 0.01f)
        }
        // the cap is real: even the stage-5 fastest, steepest leg is under it before the cap ever has to bite
        val st = PopRamp.stages.last()
        assertTrue(st.driftMax / Math.cos(st.turnDegrees * PI / 180.0) <= PopMetrics.MAX_PATH_SPEED)
        // and it bites when it must: a target given a silly speed is held to the cap
        val s = session(1); s.holdSpawnForTest = true; s.holdFireForTest = true
        val t = s.addTargetForTest(TargetKind.ROUND, 180f, 300f, 60f, straight = false)
        t.speed = 0.5f; t.side = 1; t.legTan = 1f; t.legCos = 0.7071f; t.legLeft = 100f; t.sinceEntered = 5f
        s.step(FRAME)
        assertEquals(PopMetrics.MAX_PATH_SPEED * 692f, sqrt(t.vx * t.vx + t.vy * t.vy), 0.01f)
    }

    @Test
    fun `slow drift halves all of a target's movement, sideways too, and does not change its run timer`() {
        val a = sessionNoLoss(1); a.holdSpawnForTest = true; a.holdFireForTest = true
        val b = sessionNoLoss(1); b.holdSpawnForTest = true; b.holdFireForTest = true
        val ta = a.addTargetForTest(TargetKind.ROUND, 180f, 300f, 60f, straight = false)
        val tb = b.addTargetForTest(TargetKind.ROUND, 180f, 300f, 60f, straight = false)
        for (t in listOf(ta, tb)) { t.speed = 0.08f; t.side = 1; t.legTan = 0.5f; t.legCos = 0.89f; t.legLeft = 50f; t.sinceEntered = 5f }
        b.arriveForTest(GiftKind.SLOW)
        a.step(FRAME); b.step(FRAME)
        assertEquals(ta.vx * 0.5f, tb.vx, 1e-3f)
        assertEquals(ta.vy * 0.5f, tb.vy, 1e-3f)
        assertEquals("the run timer ticks at the same pace", ta.legLeft, tb.legLeft, 1e-6f)
    }

    // ------------------------------------------------------------------ speed variety

    @Test
    fun `each target's downward speed is drawn from the stage's range, so slow and fast targets share the sky`() {
        for (stage in 0 until 5) {
            val st = PopRamp.stages[stage]
            val speeds = ArrayList<Float>()
            val s = sessionNoLoss(6, st.startsAtPops); s.holdFireForTest = true
            val seen = java.util.IdentityHashMap<PopTarget, Float>()
            s.run(400f) { for (i in 0 until it.targetCount) { val t = it.target(i); if (seen.put(t, t.speed) == null) speeds += t.speed } }
            assertTrue("stage ${stage + 1}: ${speeds.size} targets", speeds.size > 60)
            assertTrue("range ${speeds.min()}..${speeds.max()}", speeds.min() >= st.driftMin - 1e-6f && speeds.max() <= st.driftMax + 1e-6f)
            assertTrue("stage ${stage + 1}: speeds ${speeds.min()}..${speeds.max()} barely vary", speeds.max() - speeds.min() > (st.driftMax - st.driftMin) * 0.8f)
        }
    }

    @Test
    fun `from stage 3 the speed is re-picked at every turn by at most 25%, and before that it is not`() {
        for (stage in 0 until 5) {
            val st = PopRamp.stages[stage]
            var changes = 0
            var turns = 0
            val s = sessionNoLoss(7, st.startsAtPops); s.holdFireForTest = true
            val last = java.util.IdentityHashMap<PopTarget, FloatArray>() // speed, turns
            s.run(300f) {
                for (i in 0 until it.targetCount) {
                    val t = it.target(i)
                    val p = last[t]
                    if (p != null && t.turns != p[1].toInt()) {
                        turns++
                        if (t.speed != p[0]) changes++
                        assertTrue("stage ${stage + 1}: speed ${p[0]} to ${t.speed} is over 25%", abs(t.speed - p[0]) <= 0.25f * p[0] + 1e-5f)
                        assertTrue("outside the stage's range", t.speed >= st.driftMin - 1e-5f && t.speed <= st.driftMax + 1e-5f)
                    }
                    last[t] = floatArrayOf(t.speed, t.turns.toFloat())
                }
            }
            assertTrue(turns > 20)
            if (st.repickSpeed) assertTrue("stage ${stage + 1}: only $changes of $turns turns re-picked", changes > turns * 0.9)
            else assertEquals("stage ${stage + 1} must not re-pick", 0, changes)
        }
        assertEquals(listOf(false, false, true, true, true), PopRamp.stages.map { it.repickSpeed })
    }

    // ------------------------------------------------------------------ entry

    @Test
    fun `a target appears with its whole picture above the entry line and heads straight down`() {
        for (stage in 0 until 5) for (seed in 1..3) {
            val s = sessionNoLoss(seed, PopRamp.stages[stage].startsAtPops)
            s.holdFireForTest = true
            var n = 0
            s.run(200f) {
                if (it.spawned != n) {
                    n = it.spawned
                    val t = it.target(it.targetCount - 1)
                    assertTrue("stage ${stage + 1}: ${t.kind} of ${t.size} at y=${t.y}: bottom edge ${t.y + t.kind.bottom * t.size}", t.y + t.kind.bottom * t.size <= PopMetrics.SKY_TOP)
                    assertFalse(t.entered)
                    assertEquals(0, t.side)
                    assertEquals("straight down on the entry leg", 0f, t.vx, 0f)
                }
            }
            assertTrue(n > 20)
        }
    }

    @Test
    fun `a target enters exactly when its whole picture is below the line, and makes no turn in its first half second there`() {
        val s = sessionNoLoss(2); s.holdFireForTest = true
        var checked = 0
        s.run(150f) {
            for (i in 0 until it.targetCount) {
                val t = it.target(i)
                val below = t.y - t.kind.top * t.size >= PopMetrics.SKY_TOP
                if (below) assertTrue("a target whose whole picture is below the line has entered", t.entered)
                if (t.entered) assertTrue("entered but its picture is still over the line: y=${t.y}", below)
                if (t.entered && t.sinceEntered < PopMetrics.NO_TURN_AFTER_ENTRY) { assertEquals(0, t.turns); checked++ }
            }
        }
        assertTrue(checked > 100)
    }

    @Test
    fun `the first target is visible within a second and in the first seconds is straight above the line`() {
        for (seed in 1..20) {
            val s = session(seed); s.holdFireForTest = true
            var at = -1f
            s.run(1f) { if (at < 0f && it.targetCount > 0) at = it.time.toFloat() }
            assertTrue("seed $seed: first target at $at", at in 0f..1f)
            val t = s.target(0)
            assertTrue("part of the picture is already below the line: bottom ${t.y + t.kind.bottom * t.size}", t.y + t.kind.bottom * t.size > PopMetrics.SKY_TOP)
            assertFalse("but it has not entered yet", t.entered)
        }
    }

    // ------------------------------------------------------------------ one isPoppable for every way of popping

    private fun quiet(pops: Int = 0): PopSession {
        val s = sessionNoLoss(1, pops); s.holdSpawnForTest = true; s.holdFireForTest = true
        s.step(FRAME); s.clearTargetsForTest()
        return s
    }

    /** A target whose picture is half over the line: not entered. */
    private fun coming(s: PopSession, x: Float = s.shipX, gift: GiftKind? = null): PopTarget {
        val t = s.addTargetForTest(TargetKind.ROUND, x, PopMetrics.SKY_TOP - 10f, 90f, gift = gift)
        assertFalse(t.entered)
        assertFalse(s.isPoppable(t))
        return t
    }

    @Test
    fun `a star cannot pop a target that is still coming in`() {
        val s = quiet()
        s.holdFireForTest = false
        val t = coming(s)
        t.speed = 0.0001f // hardly moving: it stays above the line for the star's whole flight
        s.run(1.5f)
        assertEquals(0, s.pops)
        assertTrue(s.targets().contains(t))
        assertTrue("stars twinkled out at the line", s.starCount < 4)
    }

    @Test
    fun `a big star cannot pop a target that is still coming in either`() {
        val s = quiet()
        s.arriveForTest(GiftKind.BIG)
        s.holdFireForTest = false
        val t = coming(s)
        t.speed = 0.0001f
        s.run(1.5f)
        assertEquals(0, s.pops)
        assertTrue(s.targets().contains(t))
    }

    @Test
    fun `the ribbon cannot pop a target that is still coming in`() {
        val s = quiet()
        s.arriveForTest(GiftKind.RIBBON)
        val t = coming(s)
        t.speed = 0.0001f
        s.run(1.5f)
        assertEquals(0, s.pops)
        assertTrue(s.targets().contains(t))
    }

    @Test
    fun `the sparkle wave cannot pop a target that is still coming in, and does not count it as one to pop`() {
        val s = quiet()
        val t = coming(s)
        t.speed = 0.0001f
        s.arriveForTest(GiftKind.WAVE) // nothing poppable on screen: it only glitters
        s.run(2.5f)
        assertEquals(0, s.pops)
        assertTrue(s.targets().contains(t))
        // with a poppable one as well, the wave pops that and still leaves the coming one alone
        val u = quiet()
        val c = coming(u); c.speed = 0.0001f
        val p = u.addTargetForTest(TargetKind.ROUND, 100f, 300f, 90f)
        u.arriveForTest(GiftKind.WAVE)
        u.run(2.5f)
        assertEquals(1, u.pops)
        assertTrue(u.targets().contains(c))
        assertFalse(u.targets().contains(p))
    }

    @Test
    fun `once entered, every way of popping works, and a target that has begun to fade cannot be popped by any of them`() {
        for (way in listOf("star", "big", "ribbon", "wave")) {
            val s = quiet()
            when (way) {
                "big" -> s.arriveForTest(GiftKind.BIG)
                "ribbon" -> s.arriveForTest(GiftKind.RIBBON)
            }
            if (way == "star" || way == "big") s.holdFireForTest = false
            val t = s.addTargetForTest(TargetKind.ROUND, s.shipX, 300f, 90f)
            assertTrue(way, s.isPoppable(t))
            if (way == "wave") s.arriveForTest(GiftKind.WAVE)
            s.run(2.5f)
            assertEquals("$way pops an entered target", 1, s.pops)
        }
    }

    @Test
    fun `a target that has begun to fade at the bottom is no longer poppable`() {
        val u = quiet()
        val f = u.addTargetForTest(TargetKind.ROUND, 100f, u.height * 0.93f, 90f)
        assertTrue(u.isPoppable(f)) // it has not started to fade until a step sees it
        u.step(FRAME)
        assertTrue(f.missed)
        assertFalse(u.isPoppable(f))
    }

    @Test
    fun `stars twinkle out at the entry line, never into the strip above it`() {
        val s = quiet()
        s.holdFireForTest = false
        var highest = Float.MAX_VALUE
        s.run(3f) { for (i in 0 until it.starCount) highest = minOf(highest, it.stars[i].y - PopMetrics.STAR_RADIUS) }
        assertTrue("a star's top edge reached $highest, above the line ${PopMetrics.SKY_TOP}", highest >= PopMetrics.SKY_TOP - 0.001f - 0.9f * 692f * FRAME)
        assertEquals(0, s.pops)
        // a big star ends at the same line by its own top edge
        val b = quiet(); b.arriveForTest(GiftKind.BIG); b.holdFireForTest = false
        var hb = Float.MAX_VALUE
        b.run(3f) { for (i in 0 until it.starCount) hb = minOf(hb, it.stars[i].y - PopMetrics.BIG_STAR_RADIUS) }
        assertTrue(hb >= PopMetrics.SKY_TOP - 0.9f * 692f * FRAME - 0.001f)
    }

    @Test
    fun `the ribbon and the wave also end at the entry line`() {
        val s = quiet(); s.arriveForTest(GiftKind.RIBBON); s.run(1f)
        assertEquals(s.nose - PopMetrics.SKY_TOP, s.ribbonLength, 0.5f)
        val w = quiet(); w.arriveForTest(GiftKind.WAVE)
        var ended = -1f
        w.run(2f) { if (ended < 0f && !it.waveActive) ended = it.time.toFloat() }
        assertTrue("the wave ended by itself after $ended s", ended in 1.3f..1.7f)
    }

    @Test
    fun `a carrier that is still coming in cannot be popped and its gift is not released`() {
        val s = quiet()
        s.holdFireForTest = false
        val c = coming(s, gift = GiftKind.TRIPLE)
        c.speed = 0.0001f
        s.run(1.5f)
        assertEquals(0, s.pops)
        assertEquals(null, s.gift)
        assertEquals(null, s.starEffect)
    }
}
