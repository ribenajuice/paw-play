package com.pawplay.app.games.pawpop

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs
import kotlin.math.sqrt

/** Story 54 (the targets), 56 (misses), 57 (carriers) and 60 (the endless ramp), as tests. */
class PopRampTest {

    private val startsAt = listOf(0, 10, 25, 45, 70)

    private fun quiet(seed: Int = 1, pops: Int = 0, width: Float = 360f, height: Float = 692f) =
        session(seed, pops, width, height).also { it.holdFireForTest = true }

    // ------------------------------------------------------------------ the table

    @Test
    fun `the ramp is exactly the PRD table`() {
        val st = PopRamp.stages
        assertEquals(listOf(0, 10, 25, 45, 70), st.map { it.startsAtPops })
        assertEquals(listOf(3.5f, 3.0f, 2.5f, 2.0f, 1.7f), st.map { it.spawnEvery })
        assertEquals(listOf(0.07f, 0.08f, 0.09f, 0.10f, 0.11f), st.map { it.drift })
        assertEquals(listOf(4, 5, 6, 7, 8), st.map { it.maxTargets })
        assertEquals(listOf(1, 2, 3, 4, 5), st.map { it.number })
        assertEquals(listOf(104 to 112, 96 to 104, 96 to 104, 88 to 104, 88 to 104), st.map { it.minSize to it.maxSize })
        assertEquals(listOf(16f, 16f, 16f, 16f, 24f), st.map { it.sway })
        assertEquals(listOf(3, 5, 6, 6, 6), st.map { it.colours.size })
    }

    @Test
    fun `stage boundaries are counted in hidden pops`() {
        val expect = mapOf(0 to 1, 9 to 1, 10 to 2, 24 to 2, 25 to 3, 44 to 3, 45 to 4, 69 to 4, 70 to 5, 71 to 5, 100000 to 5)
        for ((pops, stage) in expect) assertEquals("pops=$pops", stage, PopRamp.stageFor(pops).number)
    }

    @Test
    fun `every pop counts, whichever way it happens, and a session starts at stage 1`() {
        assertEquals(1, session().stage.number)
        // a star
        val a = session(pops = 9); a.holdSpawnForTest = true
        a.addTargetForTest(TargetKind.ROUND, a.shipX, a.nose - 120f)
        a.run(1.5f)
        assertEquals(10, a.pops); assertEquals(2, a.stage.number)
        // the ribbon
        val b = session(pops = 9); b.holdFireForTest = true; b.holdSpawnForTest = true
        b.addTargetForTest(TargetKind.ROUND, b.shipX, b.nose - 120f)
        b.arriveForTest(GiftKind.RIBBON); b.run(1f)
        assertEquals(10, b.pops)
        // the wave
        val c = session(pops = 68); c.holdFireForTest = true; c.holdSpawnForTest = true
        repeat(3) { c.addTargetForTest(TargetKind.ROUND, 100f + 80f * it, 300f + 30f * it) }
        c.arriveForTest(GiftKind.WAVE); c.run(2f)
        assertEquals(71, c.pops); assertEquals(5, c.stage.number)
    }

    // ------------------------------------------------------------------ drift

    @Test
    fun `targets drift at the stage speed in screen heights a second, and half of that under slow drift`() {
        for (i in 0 until 5) for (h in listOf(568f, 692f, 880f)) {
            val s = quiet(pops = startsAt[i], height = h)
            val t = s.addTargetForTest(TargetKind.ROUND, 300f, 100f, 90f)
            val y0 = t.y
            s.run(1f)
            assertEquals("stage ${i + 1}, height $h", PopRamp.stages[i].drift * h, t.y - y0, 0.6f)
            s.arriveForTest(GiftKind.SLOW)
            val y1 = t.y
            s.run(1f)
            assertEquals("slow, stage ${i + 1}", PopRamp.stages[i].drift * h * 0.5f, t.y - y1, 0.6f)
        }
    }

    @Test
    fun `nothing falls faster than 90dp a second at the cap on the reference screen`() {
        assertTrue(PopRamp.stages.last().drift * 800f <= 90f)
    }

    @Test
    fun `targets sway a little from side to side, once every 3 seconds, 16dp and 24dp at the last stage`() {
        for ((pops, amp) in listOf(0 to 16f, 70 to 24f)) {
            val s = quiet(pops = pops)
            val seen = Spawns()
            var lo = Float.MAX_VALUE; var hi = -Float.MAX_VALUE
            s.run(40f) { seen.watch(it) }
            val t = seen.seen.first { it.size >= 88f }
            assertEquals(amp, t.amp, 0f)
            // sample its whole path from a fresh session
            val s2 = quiet(pops = pops, seed = 1)
            val first = Spawns()
            s2.run(0.2f) { first.watch(it) }
            val target = first.seen.first()
            s2.run(3.0f) { lo = minOf(lo, target.x); hi = maxOf(hi, target.x) }
            assertTrue("sways ${hi - lo} of ${2 * amp}", hi - lo > 1.9f * amp && hi - lo <= 2f * amp + 0.01f)
        }
    }

    // ------------------------------------------------------------------ spawning

    @Test
    fun `the first target arrives within a second, already partly on screen`() {
        for (seed in 1..20) {
            val s = quiet(seed)
            var at = -1f
            s.run(1f) { if (at < 0f && it.targetCount > 0) at = it.time.toFloat() }
            assertTrue("seed $seed: first target at $at", at in 0f..1f)
            val t = s.target(0)
            assertTrue("first target is in view: bottom edge at ${t.y + t.kind.halfHeight * t.size}", t.y + t.kind.halfHeight * t.size > 20f)
            assertTrue("and not fully: top edge at ${t.y - t.kind.halfHeight * t.size}", t.y - t.kind.halfHeight * t.size < 0f)
        }
    }

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
            // a retry after a crowded spot is 0.3s later, so the usual gap is exactly the table's and none is far off
            assertEquals("stage ${i + 1} median", want, gaps.sorted()[gaps.size / 2], 0.03f)
            assertTrue("stage ${i + 1} longest gap ${gaps.max()}", gaps.max() <= want + 1.0f)
        }
    }

    @Test
    fun `never more targets on screen than the stage allows, and the cap is reached when they are slow enough`() {
        for (i in 0 until 5) {
            val s = quiet(pops = startsAt[i])
            var most = 0
            s.run(200f) {
                if (it.time.toInt() % 2 == 0 && it.slowLeft < 1f) it.arriveForTest(GiftKind.SLOW)
                most = maxOf(most, it.targetCount)
                assertTrue(it.targetCount <= PopRamp.stages[i].maxTargets)
            }
            assertEquals("stage ${i + 1}", PopRamp.stages[i].maxTargets, most)
        }
    }

    @Test
    fun `a new target is at least 12dp clear of every other when it appears`() {
        for (i in 0 until 5) for (seed in 1..3) {
            val s = quiet(seed, pops = startsAt[i])
            val seen = Spawns()
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
                    seen.watch(it)
                }
            }
            assertTrue("stage ${i + 1} seed $seed: smallest gap $worst", worst >= 12f - 0.001f)
        }
    }

    @Test
    fun `targets stay on screen, sway included, and start just above the top edge`() {
        for (i in 0 until 5) for (w in listOf(320f, 360f, 411f)) {
            val s = quiet(pops = startsAt[i], width = w)
            val seen = Spawns()
            s.run(200f) { seen.watch(it) }
            assertTrue(seen.seen.size > 20)
            for (t in seen.seen) {
                assertTrue("x0 ${t.x0} size ${t.size} amp ${t.amp}", t.x0 - t.amp - t.size / 2f >= 8f - 0.01f && t.x0 + t.amp + t.size / 2f <= w - 8f + 0.01f)
            }
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
            // carriers are always round or oval, so they only ever add to the stage's own kinds; ordinary ones are the stage's kinds exactly
            assertEquals("stage ${i + 1} kinds", wantKinds[i], kinds)
            assertEquals("stage ${i + 1} colours", wantColours[i], colours)
            val st = PopRamp.stages[i]
            for (t in ordinary) {
                val ok = (t.size.toInt() in st.minSize..st.maxSize) || (i == 4 && t.size == 72f)
                assertTrue("stage ${i + 1} size ${t.size}", ok)
            }
            val small = ordinary.count { it.size == 72f }
            if (i == 4) assertTrue("about one in five is small: $small of ${ordinary.size}", small.toFloat() / ordinary.size in 0.15f..0.25f)
            else assertEquals(0, small)
            assertTrue(ordinary.all { it.amp == st.sway })
        }
    }

    @Test
    fun `nothing is ever smaller than 72dp or bigger than 112dp`() {
        val s = quiet(3, pops = 70)
        val seen = Spawns()
        s.run(2000f, 0.05f) { seen.watch(it) }
        assertTrue(seen.seen.all { it.size in 72f..112f })
        assertTrue(seen.seen.filter { it.size == 72f }.all { !it.isCarrier })
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
        var steps = 0
        s.run(6f) { if (it.targetCount > 0 && it.target(0) === t) { lowestAlpha = minOf(lowestAlpha, t.alpha); steps++ } }
        assertTrue("faded to $lowestAlpha", lowestAlpha < 0.05f)
        assertTrue(s.targets().none { it === t })
        // and nothing else noticed: no pop, no gift, no effect, no sparkle
        assertEquals(0, s.pops)
        assertEquals(null, s.gift)
        assertEquals(null, s.starEffect)
        assertEquals(0f, s.slowLeft, 0f)
        assertEquals(0, s.sparkleCount)
        assertEquals(0, s.fxCount)
        assertEquals(46f, s.shipX, 0.5f) // the ship did not react
    }

    @Test
    fun `a target that reaches the ship's line passes behind it without touching anything`() {
        val s = quiet()
        val ship = s.shipX
        s.clearTargetsForTest()
        val t = s.addTargetForTest(TargetKind.ROUND, ship, s.nose - 60f)
        var pops = 0
        s.run(8f) { pops = it.pops }
        assertEquals(0, pops)
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
            assertTrue("gift at least 48dp: ${carriers.minOf { it.size }}", carriers.all { it.size >= 96f })
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
    fun `about one carrier every 25 seconds at the start and every 13 at the cap`() {
        for ((pops, seconds) in listOf(0 to 25f, 70 to 13f)) {
            val s = quiet(7, pops = pops)
            s.run(1500f, 0.05f)
            val every = 1500f / s.carriersMade
            assertTrue("stage at $pops pops: a carrier every $every s", abs(every - seconds) < seconds * 0.35f)
        }
    }
}
