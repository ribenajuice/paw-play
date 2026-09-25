package com.pawplay.app.games.pawpop

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs
import kotlin.math.sqrt

/**
 * Story 54 (the targets), 56 (misses), 57 (carriers), 60 and 67 (the ramp, revised 2026-09-25: smaller, sharper, mixed
 * speeds). This replaces the old ramp-band tests, which measured a straight-drop game that no longer exists; the pace of
 * the ramp is now measured against the PRD's three targets in `PopPacingTest`.
 */
class PopRampTest {

    private val startsAt = listOf(0, 24, 60, 108, 165)

    /** No shooting and no losing, so only the spawn schedule is watched. */
    private fun quiet(seed: Int = 1, pops: Int = 0, width: Float = 360f, height: Float = 692f) =
        sessionNoLoss(seed, pops, width, height).also { it.holdFireForTest = true }

    // ------------------------------------------------------------------ the table

    @Test
    fun `the ramp is exactly the PRD table, revised 2026-09-25`() {
        val st = PopRamp.stages
        assertEquals(listOf(1, 2, 3, 4, 5), st.map { it.number })
        assertEquals(listOf(0, 24, 60, 108, 165), st.map { it.startsAtPops })
        assertEquals(listOf(3.5f, 3.0f, 2.5f, 2.0f, 1.7f), st.map { it.spawnEvery })
        assertEquals(listOf(4, 5, 6, 7, 8), st.map { it.maxTargets })
        assertEquals(listOf(88 to 96, 80 to 96, 72 to 88, 64 to 80, 56 to 72), st.map { it.minSize to it.maxSize })
        assertEquals(listOf(0.06f to 0.09f, 0.06f to 0.10f, 0.07f to 0.11f, 0.07f to 0.12f, 0.08f to 0.12f), st.map { it.driftMin to it.driftMax })
        assertEquals(listOf(20f, 30f, 40f, 45f, 50f), st.map { it.turnDegrees })
        assertEquals(listOf(2.5f to 3.5f, 2.0f to 3.0f, 1.5f to 2.5f, 1.2f to 2.0f, 0.9f to 1.6f), st.map { it.runMin to it.runMax })
        assertEquals(listOf(3, 5, 6, 6, 6), st.map { it.colours.size })
        // stage 5: about one in three of the targets is the small 56-64
        assertEquals(1f / 3f, st[4].smallChance, 0.001f)
        assertEquals(64, st[4].smallMax)
        // stage 1 is gentle: a 20 degree zig well under 30dp/s sideways at the fastest
        assertTrue(st[0].driftMax * 692f * Math.tan(Math.toRadians(20.0)) < 30.0)
    }

    @Test
    fun `stage boundaries are counted in hidden pops`() {
        val expect = mapOf(0 to 1, 23 to 1, 24 to 2, 59 to 2, 60 to 3, 107 to 3, 108 to 4, 164 to 4, 165 to 5, 166 to 5, 100000 to 5)
        for ((pops, stage) in expect) assertEquals("pops=$pops", stage, PopRamp.stageFor(pops).number)
    }

    @Test
    fun `every pop counts toward the ramp, whichever way it happens, and a session starts at stage 1`() {
        assertEquals(1, session().stage.number)
        val a = session(pops = 23); a.holdSpawnForTest = true
        a.addTargetForTest(TargetKind.ROUND, a.shipX, a.nose - 120f)
        a.run(1.5f)
        assertEquals(24, a.pops); assertEquals(2, a.stage.number)
        val b = session(pops = 23); b.holdFireForTest = true; b.holdSpawnForTest = true
        b.addTargetForTest(TargetKind.ROUND, b.shipX, b.nose - 120f)
        b.arriveForTest(GiftKind.RIBBON); b.run(1f)
        assertEquals(24, b.pops)
        val c = session(pops = 163); c.holdFireForTest = true; c.holdSpawnForTest = true
        repeat(3) { c.addTargetForTest(TargetKind.ROUND, 100f + 80f * it, 300f + 30f * it) }
        c.arriveForTest(GiftKind.WAVE); c.run(2f)
        assertEquals(166, c.pops); assertEquals(5, c.stage.number)
    }

    // ------------------------------------------------------------------ speed

    @Test
    fun `targets drop at their own speed in screen heights a second, and half of that under slow drift`() {
        for (i in 0 until 5) for (h in listOf(568f, 692f, 880f)) {
            val s = quiet(pops = startsAt[i], height = h)
            val t = s.addTargetForTest(TargetKind.ROUND, 300f, 100f, 90f, speed = PopRamp.stages[i].driftMax)
            val y0 = t.y
            s.run(1f)
            assertEquals("stage ${i + 1}, height $h", PopRamp.stages[i].driftMax * h, t.y - y0, 0.6f)
            s.arriveForTest(GiftKind.SLOW)
            val y1 = t.y
            s.run(1f)
            assertEquals("slow, stage ${i + 1}", PopRamp.stages[i].driftMax * h * 0.5f, t.y - y1, 0.6f)
        }
    }

    @Test
    fun `the fastest thing on the sky at the cap is far slower than the child's own star`() {
        val fastest = PopRamp.stages.last().driftMax / Math.cos(Math.toRadians(PopRamp.stages.last().turnDegrees.toDouble())) * 800.0
        assertTrue("$fastest dp/s along the path", fastest < 0.2 * 800.0 + 1e-6)
        assertTrue(fastest < PopMetrics.STAR_SPEED * 800.0)
    }

    // ------------------------------------------------------------------ spawning

    @Test
    fun `a new target comes every so many seconds at each stage`() {
        for (i in 0 until 5) {
            val s = quiet(pops = startsAt[i])
            val max = PopRamp.stages[i].maxTargets
            var last = 0; var lastAt = 0f
            var waitedForRoom = false
            val gaps = ArrayList<Float>()
            s.run(200f) {
                if (it.targetCount >= max) waitedForRoom = true
                if (it.spawned != last) {
                    if (last > 0 && !waitedForRoom) gaps += it.time.toFloat() - lastAt // gaps that were not held up by a full screen
                    last = it.spawned; lastAt = it.time.toFloat(); waitedForRoom = false
                }
            }
            val want = PopRamp.stages[i].spawnEvery
            assertTrue("stage ${i + 1} made only ${gaps.size} free gaps", gaps.size >= 2)
            assertTrue("stage ${i + 1} shortest gap ${gaps.min()}", gaps.min() >= want - 0.03f)
            assertEquals("stage ${i + 1} median", want, gaps.sorted()[gaps.size / 2], 0.03f)
            assertTrue("stage ${i + 1} longest gap ${gaps.max()}", gaps.max() <= want + 1.0f)
        }
    }

    @Test
    fun `never more targets on screen than the stage allows, and the cap is reached when they are slow enough`() {
        for (i in 0 until 5) {
            val s = quiet(pops = startsAt[i])
            var most = 0
            s.run(300f) {
                if (it.slowLeft < 1f) it.arriveForTest(GiftKind.SLOW)
                most = maxOf(most, it.targetCount)
                assertTrue(it.targetCount <= PopRamp.stages[i].maxTargets)
            }
            assertEquals("stage ${i + 1}", PopRamp.stages[i].maxTargets, most)
        }
        assertEquals(8, PopRamp.stages.maxOf { it.maxTargets })
    }

    @Test
    fun `a new target is at least 12dp clear of every other when it appears`() {
        for (i in 0 until 5) for (seed in 1..3) {
            val s = quiet(seed, pops = startsAt[i])
            var worst = Float.MAX_VALUE
            var n = 0
            s.run(300f) {
                if (it.spawned != n) {
                    n = it.spawned
                    val t = it.target(it.targetCount - 1)
                    for (j in 0 until it.targetCount - 1) {
                        val o = it.target(j)
                        val gap = sqrt((o.x - t.x) * (o.x - t.x) + (o.y - t.y) * (o.y - t.y)) - o.kind.reach * o.size - t.kind.reach * t.size
                        worst = minOf(worst, gap)
                    }
                }
            }
            assertTrue("stage ${i + 1} seed $seed: smallest gap $worst", worst >= 12f - 0.001f)
        }
    }

    @Test
    fun `variety unlocks exactly as the table says`() {
        val wantKinds = listOf(
            setOf(TargetKind.ROUND),
            setOf(TargetKind.ROUND, TargetKind.OVAL),
            setOf(TargetKind.ROUND, TargetKind.OVAL, TargetKind.HEART, TargetKind.MOON),
            TargetKind.ALL.toSet(), TargetKind.ALL.toSet(),
        )
        val wantColours = listOf(setOf(0, 2, 4), setOf(0, 1, 2, 4, 5), (0..5).toSet(), (0..5).toSet(), (0..5).toSet())
        for (i in 0 until 5) {
            val s = quiet(2, pops = startsAt[i])
            val seen = Spawns()
            s.run(3000f, 0.05f) { seen.watch(it) }
            val ordinary = seen.seen.filter { !it.isCarrier }
            val kinds = ordinary.map { it.kind }.toSet()
            val colours = seen.seen.map { it.colour }.toSet()
            assertEquals("stage ${i + 1} kinds", wantKinds[i], kinds)
            assertEquals("stage ${i + 1} colours", wantColours[i], colours)
        }
    }

    // ------------------------------------------------------------------ sizes (story 67)

    @Test
    fun `sizes are the stage's own, never below 56dp, critters never below 64dp, carriers never below 72dp`() {
        for (i in 0 until 5) {
            val st = PopRamp.stages[i]
            val s = quiet(3, pops = startsAt[i])
            val seen = Spawns()
            s.run(2500f, 0.05f) { seen.watch(it) }
            assertTrue(seen.seen.size > 500)
            for (t in seen.seen) {
                assertTrue("stage ${i + 1}: ${t.kind} of ${t.size} is under 56dp", t.size >= PopMetrics.MIN_TARGET_SIZE)
                if (t.kind == TargetKind.CRITTER) assertTrue("critter of ${t.size}", t.size >= PopMetrics.MIN_CRITTER_SIZE)
                if (t.isCarrier) assertTrue("carrier of ${t.size}", t.size >= PopMetrics.CARRIER_MIN_SIZE)
                else if (t.kind != TargetKind.CRITTER) assertTrue("stage ${i + 1} size ${t.size}", t.size.toInt() in st.minSize..st.maxSize)
                else assertTrue("stage ${i + 1} critter ${t.size}", t.size.toInt() in maxOf(st.minSize, PopMetrics.MIN_CRITTER_SIZE)..st.maxSize)
            }
        }
        assertEquals(56, PopMetrics.MIN_TARGET_SIZE); assertEquals(64, PopMetrics.MIN_CRITTER_SIZE); assertEquals(72, PopMetrics.CARRIER_MIN_SIZE)
    }

    @Test
    fun `at the cap about one in three ordinary targets is the small 56-64 and the rest 65-72`() {
        val s = quiet(4, pops = 165)
        val seen = Spawns()
        s.run(4000f, 0.05f) { seen.watch(it) }
        val ordinary = seen.seen.filter { !it.isCarrier && it.kind != TargetKind.CRITTER }
        val small = ordinary.count { it.size <= 64f }
        assertTrue("$small of ${ordinary.size} are small", small.toFloat() / ordinary.size in 0.26f..0.41f)
        assertTrue(ordinary.all { it.size in 56f..72f })
        assertTrue("the smallest size is really used", ordinary.any { it.size == 56f })
    }

    @Test
    fun `no star can skip clean over the smallest target in one 50ms frame on any phone or tablet up to 1366dp tall`() {
        for (h in listOf(568f, 740f, 880f, 1280f, 1366f)) {
            val step = PopMetrics.STAR_SPEED * h * PopMetrics.MAX_STEP
            for (kind in TargetKind.ALL) {
                val smallest = (if (kind == TargetKind.CRITTER) PopMetrics.MIN_CRITTER_SIZE else PopMetrics.MIN_TARGET_SIZE).toFloat()
                val hitHeight = 2f * (kind.ry * smallest + PopMetrics.HIT_REACH)
                assertTrue("$kind on a ${h.toInt()}dp screen: star moves $step per frame, target is only $hitHeight tall", hitHeight > step)
            }
        }
    }

    @Test
    fun `when the screen empties the next target arrives within a second`() {
        for (i in 0 until 5) {
            val s = quiet(pops = startsAt[i])
            s.run(30f)
            repeat(5) {
                s.clearTargetsForTest()
                var at = -1f
                val t0 = s.time
                s.run(1.2f) { if (at < 0f && it.targetCount > 0) at = (it.time - t0).toFloat() }
                assertTrue("stage ${i + 1}: next target after $at s", at in 0f..1.0f)
                s.run(4f)
            }
        }
    }

    // ------------------------------------------------------------------ misses

    @Test
    fun `a missed target fades out behind the ship over the last tenth of the height and is simply gone`() {
        val h = 692f
        assertEquals(1f, missFade(h * 0.9f, h), 0.001f)
        assertEquals(1f, missFade(300f, h), 0f)
        assertEquals(0.5f, missFade(h * 0.95f, h), 0.001f)
        assertEquals(0f, missFade(h, h), 0f)
        assertEquals(0f, missFade(h + 50f, h), 0f)

        val s = quiet()
        s.touchDown(1L, 46f); s.run(1f) // the ship is off to one side, out of the way
        s.clearTargetsForTest()
        val t = s.addTargetForTest(TargetKind.OVAL, 300f, h - 130f, 100f, gift = GiftKind.TRIPLE)
        var lowestAlpha = 1f
        s.run(6f) { if (it.targetCount > 0 && it.target(0) === t) lowestAlpha = minOf(lowestAlpha, t.alpha) }
        assertTrue("faded to $lowestAlpha", lowestAlpha < 0.05f)
        assertTrue(s.targets().none { it === t })
        // and nothing else noticed: no pop, no gift, no effect, no sparkle, no paw (it is a carrier)
        assertEquals(0, s.pops)
        assertEquals(null, s.gift)
        assertEquals(null, s.starEffect)
        assertEquals(0f, s.slowLeft, 0f)
        assertEquals(0, s.sparkleCount)
        assertEquals(0, s.fxCount)
        assertEquals(3, s.paws)
        assertEquals(46f, s.shipX, 0.5f) // the ship did not react
    }

    @Test
    fun `a target that reaches the ship's line passes behind it without touching anything`() {
        val s = quiet()
        val ship = s.shipX
        s.clearTargetsForTest()
        val t = s.addTargetForTest(TargetKind.ROUND, ship, s.nose - 60f)
        s.run(20f)
        assertEquals(0, s.pops)
        assertTrue(s.targets().none { it === t })
        assertEquals(ship, s.shipX, 0f)
    }

    // ------------------------------------------------------------------ carriers

    @Test
    fun `the first carrier is the fifth target and there are 6 to 9 ordinary ones between carriers`() {
        for (i in 0 until 5) for (seed in 1..3) {
            val s = quiet(seed, pops = startsAt[i])
            val seen = Spawns()
            s.run(2500f, 0.05f) { seen.watch(it) }
            val order = seen.seen
            val carrierIdx = order.indices.filter { order[it].isCarrier }
            assertTrue("stage ${i + 1}: only ${carrierIdx.size} carriers", carrierIdx.size >= 20)
            assertEquals("stage ${i + 1} seed $seed: the first carrier is the 5th target", 4, carrierIdx.first())
            for (k in 1 until carrierIdx.size) {
                val between = carrierIdx[k] - carrierIdx[k - 1] - 1
                assertTrue("stage ${i + 1} seed $seed: $between ordinary targets between carriers", between in 6..9)
            }
            val share = carrierIdx.size.toFloat() / order.size
            assertTrue("about 1 in 8: $share", share in 1f / 10.5f..1f / 6.5f)
        }
    }

    @Test
    fun `never more than one carrier on screen, even when targets are slow`() {
        for (i in 0 until 5) {
            val s = quiet(4, pops = startsAt[i])
            var most = 0
            s.run(1500f, 0.05f) {
                if (it.slowLeft < 1f) it.arriveForTest(GiftKind.SLOW)
                most = maxOf(most, it.targets().count { t -> t.isCarrier })
            }
            assertTrue("stage ${i + 1}: $most carriers at once", most <= 1)
            assertEquals("a carrier does appear", 1, most)
        }
    }

    @Test
    fun `only round bubbles and oval balloons carry gifts, big enough to show them`() {
        for (i in 0 until 5) {
            val s = quiet(5, pops = startsAt[i])
            val seen = Spawns()
            s.run(1500f, 0.05f) { seen.watch(it) }
            val carriers = seen.seen.filter { it.isCarrier }
            assertTrue(carriers.size > 10)
            assertTrue(carriers.all { it.kind == TargetKind.ROUND || it.kind == TargetKind.OVAL })
            assertTrue("gift at least 48dp inside at least a 72dp shell: ${carriers.minOf { it.size }}", carriers.all { it.size >= 72f })
        }
    }

    @Test
    fun `gifts are chosen at random and never the same twice in a row`() {
        val s = quiet(6)
        val seen = Spawns()
        s.run(6000f, 0.05f) { seen.watch(it) }
        val gifts = seen.seen.mapNotNull { it.gift }
        assertTrue(gifts.size > 100)
        for (k in 1 until gifts.size) assertTrue("twice in a row at $k: ${gifts[k]}", gifts[k] != gifts[k - 1])
        assertEquals(GiftKind.ALL.toSet(), gifts.toSet())
        val counts = GiftKind.ALL.map { g -> gifts.count { it == g } }
        assertTrue("fairly even: $counts", counts.max() < counts.min() * 2)
    }

    @Test
    fun `carriers are about one target in eight`() {
        for (pops in listOf(0, 165)) {
            val s = quiet(7, pops = pops)
            s.run(3000f, 0.05f)
            val share = s.carriersMade.toFloat() / s.spawned
            assertTrue("stage at $pops pops: carrier share $share", abs(share - 1f / 8f) < 0.03f)
        }
    }
}
