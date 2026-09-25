package com.pawplay.app.games.pawpop

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.IdentityHashMap
import kotlin.random.Random

/**
 * QA's own adversarial checks of Paw Pop (PRD stories 54, 56, 57, 60-62, 66-69, 72), written from the PRD text: nothing
 * is popped above the entry line by any route, paws are lost only by plain targets and only once per 3 seconds, the game
 * ends once, a pause costs nothing, and a mashing child cannot break the ship or the limits.
 */
class PopAdversarialQaTest {
    private val h = 692f
    private val w = 360f

    private fun quiet(seed: Int = 1, pops: Int = 0): PopSession =
        session(seed, pops).also { it.holdSpawnForTest = true }

    /** y for a target of [kind] and [size] whose top edge is [above] dp above (negative: below) the entry line. */
    private fun yTopAt(kind: TargetKind, size: Float, aboveLine: Float): Float = PopMetrics.SKY_TOP - aboveLine + kind.top * size

    // ================================================================== entry: nothing pops above the line

    private enum class Popper { STAR, BIG, RIBBON, WAVE, TRIPLE }

    private fun prime(s: PopSession, p: Popper) {
        when (p) {
            Popper.STAR -> Unit
            Popper.BIG -> s.arriveForTest(GiftKind.BIG)
            Popper.RIBBON -> s.arriveForTest(GiftKind.RIBBON)
            Popper.TRIPLE -> s.arriveForTest(GiftKind.TRIPLE)
            Popper.WAVE -> Unit // the wave is given again during the run, see below
        }
    }

    @Test
    fun `no star, big star, ribbon, triple or wave pops a target whose picture is still above the line, for every silhouette`() {
        for (kind in TargetKind.ALL) for (popper in Popper.values()) for (above in listOf(0.5f, 1f, 8f, 30f)) {
            val s = quiet()
            val size = 100f
            val t = s.addTargetForTest(kind, s.shipX, yTopAt(kind, size, above), size, speed = 0f)
            assertFalse("$kind: sanity, it starts above the line", t.entered)
            prime(s, popper)
            var seconds = 0f
            while (seconds < 2.4f) {
                if (popper == Popper.WAVE && ((seconds * 60).toInt() % 90 == 0)) s.arriveForTest(GiftKind.WAVE)
                if (popper == Popper.RIBBON && ((seconds * 60).toInt() % 300 == 0)) s.arriveForTest(GiftKind.RIBBON)
                s.step(FRAME); seconds += FRAME
            }
            assertEquals("$kind popped by $popper with its top ${above}dp above the line", 1, s.targetCount)
            assertEquals(0, s.score)
        }
    }

    @Test
    fun `the same targets are popped once their whole picture is below the line, so the check above has teeth`() {
        for (kind in TargetKind.ALL) for (popper in Popper.values()) {
            val s = quiet()
            val size = 100f
            s.addTargetForTest(kind, s.shipX, yTopAt(kind, size, -6f), size, speed = 0f) // 6dp below the line
            prime(s, popper)
            var seconds = 0f
            while (seconds < 2.4f && s.score == 0) {
                if (popper == Popper.WAVE && ((seconds * 60).toInt() % 90 == 0)) s.arriveForTest(GiftKind.WAVE)
                s.step(FRAME); seconds += FRAME
            }
            assertEquals("$kind was not popped by $popper", 1, s.score)
        }
    }

    /** Plays natural games with a child who sweeps the ship about, and checks every target that vanished by a pop was fully below the line. */
    @Test
    fun `in whole games at every stage, every popped target had entered and was fully below the entry line`() {
        var pops = 0
        for (startPops in listOf(0, 30, 70, 120, 175)) for (seed in 1..6) {
            val s = PopSession(Random(seed * 101 + startPops), startPops, w, h)
            val r = Random(seed)
            var gifts = 0
            var steps = 0
            while (s.phase != PopPhase.OVER && steps++ < 60 * 90) {
                if (steps % 45 == 0) { if (s.isSteering) s.touchMove(1, r.nextFloat() * w) else s.touchDown(1, r.nextFloat() * w) }
                if (steps % 700 == 0) { s.arriveForTest(GiftKind.ALL[gifts++ % 5]) } // every effect, repeatedly, on top of the natural gifts
                val before = s.targets()
                s.step(FRAME)
                val now = IdentityHashMap<PopTarget, Boolean>().also { m -> s.targets().forEach { m[it] = true } }
                for (t in before) if (!now.containsKey(t) && !t.missed && s.phase == PopPhase.PLAYING) {
                    pops++
                    assertTrue("a popped ${t.kind} had not entered (stage ${s.stage.number})", t.entered)
                    assertTrue("a popped ${t.kind} was still above the line: top edge at ${t.y - t.kind.top * t.size}", PopPath.isBelowLine(t))
                }
            }
        }
        assertTrue("the run popped only $pops targets, too few to prove anything", pops > 300)
    }

    // ================================================================== paws

    /** Adds a plain target that will start to fade on the very next step. */
    private fun PopSession.dropMiss(x: Float = 100f, gift: GiftKind? = null): PopTarget =
        addTargetForTest(TargetKind.ROUND, x, 0.895f * h, 60f, gift = gift, speed = 0.1f)

    @Test
    fun `plain misses cost paws one per three seconds, carriers cost nothing and open no grace, and the third paw ends it gently, once`() {
        val s = quiet()
        // A carrier goes past first: nothing.
        s.dropMiss(gift = GiftKind.SLOW)
        s.run(0.3f)
        assertEquals(3, s.paws); assertEquals(0f, s.graceLeft, 0f)
        // Three plain targets leave in the same frame: one paw.
        s.dropMiss(60f); s.dropMiss(180f); s.dropMiss(300f)
        s.run(0.2f)
        assertEquals(2, s.paws)
        val firstLoss = s.time
        assertTrue(s.graceLeft > 2.7f)
        // 2.5 seconds later, another goes: free. 3.2 seconds later: it costs.
        s.run(2.3f)
        s.dropMiss(); s.run(0.2f)
        assertEquals("inside the 3s grace", 2, s.paws)
        while (s.time - firstLoss < 3.15f) s.step(FRAME)
        assertEquals(0f, s.graceLeft, 0f)
        s.dropMiss(); s.run(0.2f)
        assertEquals(1, s.paws)
        // Last one, after the next grace window.
        s.run(3.1f)
        assertEquals(PopPhase.PLAYING, s.phase)
        val scoreBefore = s.score
        val launched = s.starsLaunched
        s.dropMiss(); s.run(0.2f)
        assertEquals(0, s.paws)
        assertEquals(PopPhase.ENDING, s.phase)
        var overCount = 0
        var lastPhase = s.phase
        for (i in 0 until 600) {
            s.step(FRAME)
            if (s.phase != lastPhase) { if (s.phase == PopPhase.OVER) overCount++; assertTrue("phase only moves forward", s.phase.ordinal > lastPhase.ordinal); lastPhase = s.phase }
        }
        assertEquals(1, overCount)
        assertEquals(PopPhase.OVER, s.phase)
        assertEquals("no star fired after the last paw", launched, s.starsLaunched)
        assertEquals("no points during or after the ending", scoreBefore, s.score)
        assertEquals(0, s.paws)
        assertEquals(0, s.targetCount)
    }

    @Test
    fun `a target that started to fade cannot be popped, by a star, the ribbon or the wave, and does not cost a second paw`() {
        val s = quiet()
        val t = s.dropMiss(s.shipX)
        s.run(0.2f)
        assertTrue(t.missed)
        assertFalse(s.isPoppable(t))
        assertEquals(2, s.paws)
        s.arriveForTest(GiftKind.RIBBON)
        s.run(1f)
        s.arriveForTest(GiftKind.WAVE)
        s.run(2f)
        assertEquals(0, s.score)
        assertEquals(2, s.paws)
    }

    @Test
    fun `a slow drift that halves the speed still costs a paw for a plain miss and none for a carrier`() {
        val s = quiet()
        s.arriveForTest(GiftKind.SLOW)
        s.dropMiss(gift = GiftKind.TRIPLE)
        s.run(1f)
        assertEquals(3, s.paws)
        s.dropMiss(); s.run(1f)
        assertEquals(2, s.paws)
    }

    @Test
    fun `no gift, pop count or stage ever gives a paw back, and the paws never go over three`() {
        val s = quiet(pops = 170)
        s.dropMiss(); s.run(0.3f)
        assertEquals(2, s.paws)
        for (g in GiftKind.ALL) { s.arriveForTest(g); s.run(2f) }
        assertEquals(2, s.paws)
        assertEquals(3, PopSession(Random(1), startPaws = 99).paws)
    }

    // ================================================================== scoring

    @Test
    fun `a big star through three targets in a line is three points, a carrier is three by itself, and the score stops at 999999`() {
        val s = quiet()
        s.arriveForTest(GiftKind.BIG)
        for (y in listOf(250f, 350f, 450f)) s.addTargetForTest(TargetKind.ROUND, s.shipX, y, 60f, speed = 0f)
        s.run(2.5f)
        assertEquals(3, s.score)
        val c = quiet()
        c.addTargetForTest(TargetKind.OVAL, c.shipX, 300f, 72f, gift = GiftKind.SLOW, speed = 0f)
        c.run(1f)
        assertEquals(3, c.score)
        c.run(2f) // the gift arrives and the slow drift starts: no points for it
        assertEquals(3, c.score)
        val cap = PopSession(Random(1), 0, w, h, startScore = 999_998).also { it.holdSpawnForTest = true }
        cap.addTargetForTest(TargetKind.OVAL, cap.shipX, 300f, 72f, gift = GiftKind.SLOW, speed = 0f)
        cap.run(1f)
        assertEquals(999_999, cap.score)
    }

    @Test
    fun `the wave pops plain targets and skips carriers, and with only a carrier on screen it pops nothing`() {
        val s = quiet()
        s.addTargetForTest(TargetKind.ROUND, 100f, 300f, 60f, speed = 0f)
        s.addTargetForTest(TargetKind.OVAL, 260f, 300f, 72f, gift = GiftKind.TRIPLE, speed = 0f)
        s.arriveForTest(GiftKind.WAVE)
        s.run(2f)
        assertEquals(1, s.score)
        assertEquals("the carrier waits", 1, s.targetCount)
        val only = quiet()
        only.addTargetForTest(TargetKind.OVAL, 260f, 300f, 72f, gift = GiftKind.TRIPLE, speed = 0f)
        only.arriveForTest(GiftKind.WAVE)
        only.run(2f)
        assertEquals(0, only.score)
        assertEquals(1, only.targetCount)
    }

    // ================================================================== pause and resume

    @Test
    fun `a ten minute pause is one short step with no paw lost, no burst of targets or stars, and nothing jumping`() {
        val s = PopSession(Random(4), 0, w, h)
        val ui = PopUi(s)
        var nanos = 1_000_000_000L
        repeat(120) { nanos += 16_666_667L; ui.onFrame(nanos) }
        val t0 = s.time
        val targets = s.targetCount
        val launched = s.starsLaunched
        val spawned = s.spawned
        val ys = s.targets().map { it.y }
        nanos += 600_000_000_000L // ten minutes in the background
        ui.onFrame(nanos)
        assertTrue("time moved ${s.time - t0}s", s.time - t0 <= 0.0501)
        assertTrue(s.spawned - spawned <= 1)
        assertTrue(s.starsLaunched - launched <= 1)
        assertTrue(s.targetCount - targets <= 1)
        assertEquals(3, s.paws)
        s.targets().zip(ys).forEach { (t, y) -> assertTrue("a target jumped ${t.y - y}dp", t.y - y <= 0.13f * h * 0.05f + 0.5f) }
    }

    @Test
    fun `a target about to fade is not lost by a long pause, and the grace window is not eaten by one`() {
        val s = quiet()
        s.dropMiss(); s.run(0.3f)
        assertEquals(2, s.paws)
        val grace = s.graceLeft
        s.step(600f)
        assertTrue("grace dropped from $grace to ${s.graceLeft}", grace - s.graceLeft <= 0.0501f)
        val near = s.addTargetForTest(TargetKind.ROUND, 100f, 0.8f * h, 60f, speed = 0.1f)
        s.step(600f)
        assertFalse(near.missed)
    }

    // ================================================================== mashing

    @Test
    fun `garbage input and a hundred fingers never break the ship, the limits, or the paws and score rules`() {
        for (seed in 1..25) {
            val r = Random(seed)
            val s = PopSession(Random(seed), r.nextInt(0, 200), w, h)
            var paws = s.paws
            var score = s.score
            var phase = s.phase
            repeat(4000) { i ->
                val id = r.nextInt(-5, 130).toLong()
                val x = when (r.nextInt(12)) { 0 -> Float.NaN; 1 -> Float.POSITIVE_INFINITY; 2 -> -1e9f; 3 -> 1e9f; else -> r.nextFloat() * 420f - 30f }
                when (r.nextInt(9)) {
                    0, 1 -> s.touchDown(id, x)
                    2, 3 -> s.touchMove(id, x)
                    4 -> s.touchUp(id)
                    5 -> if (r.nextInt(20) == 0) s.touchCancel(id)
                    6 -> if (r.nextInt(50) == 0) s.touchCancelAll()
                    7 -> if (r.nextInt(300) == 0) s.arriveForTest(GiftKind.ALL[r.nextInt(5)])
                    else -> if (r.nextInt(400) == 0) s.setArea(300f + r.nextFloat() * 200f, 500f + r.nextFloat() * 300f)
                }
                s.step(when (r.nextInt(10)) { 0 -> 0.2f; 1 -> Float.NaN; 2 -> -1f; else -> FRAME })
                assertTrue("ship off screen: ${s.shipX}", s.shipX.isFinite() && s.shipX >= 8f + 38f - 1f && s.shipX <= s.width - 8f - 38f + 1f)
                assertTrue(s.targetCount <= PopMetrics.MAX_TARGETS)
                assertTrue(s.starCount <= PopMetrics.MAX_STARS)
                assertTrue(s.fxCount <= PopMetrics.MAX_FX)
                assertTrue(s.sparkleCount in 0..PopMetrics.MAX_SPARKLES)
                assertTrue("paws only go down", s.paws in 0..paws)
                assertTrue("score only goes up", s.score >= score && s.score <= 999_999)
                assertTrue("phase only moves forward", s.phase.ordinal >= phase.ordinal)
                if (s.paws == 0) assertTrue("paws gone but still playing at step $i", s.phase != PopPhase.PLAYING)
                paws = s.paws; score = s.score; phase = s.phase
            }
        }
    }

    @Test
    fun `an ended game ignores every touch and keeps the score and paws for the good-game screen`() {
        val s = quiet()
        var tries = 0
        while (s.phase == PopPhase.PLAYING && tries++ < 40) { s.dropMiss(); s.run(3.2f) }
        s.run(1f)
        assertEquals(PopPhase.OVER, s.phase)
        val x = s.shipX
        for (id in 1L..20L) { s.touchDown(id, 20f); s.touchMove(id, 300f) }
        s.run(1f)
        assertEquals(x, s.shipX, 0f)
        assertFalse(s.isSteering)
        assertEquals(0, s.paws)
        assertEquals(0, s.starCount)
    }
}
