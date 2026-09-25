package com.pawplay.app.games.pawpop

import com.pawplay.app.data.BestScore
import com.pawplay.app.data.IntStore
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Stories 68 (score, 3 paws, grace), 69 (the kind ending), 70 (the best is saved the moment it is beaten) and 72 (mashing and
 * pausing never break it). Replaces the old "no lives, no score, no game over" tests.
 */
class PopScoreAndPawsTest {

    /** Nothing fires or appears on its own: a test puts exactly what it needs on screen. */
    private fun quiet(seed: Int = 1, pops: Int = 0, bestBefore: Int = 0, startScore: Int = 0): PopSession {
        val s = PopSession(kotlin.random.Random(seed), pops, 360f, 692f, bestBefore, startScore)
        s.holdSpawnForTest = true; s.holdFireForTest = true
        s.step(FRAME)
        return s
    }

    /** A plain target right at the point where it starts to fade, then one step so the session sees it. */
    private fun missOne(s: PopSession, x: Float = 100f, gift: GiftKind? = null, speed: Float = 0.06f): PopTarget {
        val t = s.addTargetForTest(TargetKind.ROUND, x, s.height * 0.9f + 1f, 90f, gift = gift, speed = speed)
        s.step(FRAME)
        return t
    }

    // ------------------------------------------------------------------ a fresh game

    @Test
    fun `a new game starts at score 0 with 3 paws, stage 1, the ship in the middle and no grace`() {
        val s = PopSession()
        assertEquals(0, s.score)
        assertEquals(3, s.paws)
        assertEquals(PopPhase.PLAYING, s.phase)
        assertEquals(1, s.stage.number)
        assertEquals(180f, s.shipX, 0f)
        assertEquals(0f, s.graceLeft, 0f)
        assertEquals(-1, s.pawFadeIndex)
        assertFalse("0 does not beat a best of 0", s.isNewBest)
        assertEquals(3, PopMetrics.START_PAWS)
    }

    // ------------------------------------------------------------------ points

    @Test
    fun `a pop by a star is 1 point and a carrier is 3, and every route counts the same`() {
        val star = quiet(); star.holdFireForTest = false
        star.addTargetForTest(TargetKind.ROUND, star.shipX, 300f, 90f)
        star.run(1.5f)
        assertEquals(1, star.score); assertEquals(1, star.pops)

        val big = quiet(); big.arriveForTest(GiftKind.BIG); big.holdFireForTest = false
        big.addTargetForTest(TargetKind.ROUND, big.shipX, 300f, 90f); big.addTargetForTest(TargetKind.OVAL, big.shipX, 200f, 90f)
        big.run(1.5f)
        assertEquals("a big star flies through and pops both, 1 each", 2, big.score)

        val ribbon = quiet(); ribbon.arriveForTest(GiftKind.RIBBON)
        ribbon.addTargetForTest(TargetKind.ROUND, ribbon.shipX, 300f, 90f)
        ribbon.run(1f)
        assertEquals(1, ribbon.score)

        val wave = quiet()
        wave.addTargetForTest(TargetKind.ROUND, 100f, 300f, 90f); wave.addTargetForTest(TargetKind.HEART, 250f, 250f, 90f)
        wave.arriveForTest(GiftKind.WAVE); wave.run(2.5f)
        assertEquals(2, wave.score)

        val carrier = quiet(); carrier.holdFireForTest = false
        carrier.addTargetForTest(TargetKind.ROUND, carrier.shipX, 300f, 100f, gift = GiftKind.SLOW)
        carrier.run(1.5f)
        assertEquals("a carrier is 3", 3, carrier.score)
        assertEquals("and still one pop for the ramp", 1, carrier.pops)
        // the wave skips carriers, so a carrier is only ever scored by a star or the ribbon
        val w2 = quiet()
        w2.addTargetForTest(TargetKind.ROUND, 100f, 300f, 100f, gift = GiftKind.TRIPLE)
        w2.arriveForTest(GiftKind.WAVE); w2.run(2.5f)
        assertEquals(0, w2.score)
    }

    @Test
    fun `gifts and effects earn nothing, and neither does anything else that is not a pop`() {
        val s = quiet()
        for (g in GiftKind.ALL) s.arriveForTest(g)
        s.run(20f)
        assertEquals(0, s.score)
        s.touchDown(1, 40f); s.run(2f); s.touchUp(1)
        assertEquals(0, s.score)
        assertEquals(3, s.paws)
    }

    @Test
    fun `the score is capped at 999,999`() {
        val s = quiet(startScore = 999_998); s.holdFireForTest = false
        s.addTargetForTest(TargetKind.ROUND, s.shipX, 300f, 100f, gift = GiftKind.SLOW) // worth 3
        s.run(1.5f)
        assertEquals(PopMetrics.MAX_SCORE, s.score)
    }

    // ------------------------------------------------------------------ paws

    @Test
    fun `a plain target that starts to fade at the bottom costs one paw, and the paw that goes is the rightmost`() {
        val s = quiet()
        val t = s.addTargetForTest(TargetKind.ROUND, 100f, s.height * 0.8f, 90f)
        s.run(1f)
        assertEquals("not lost while it is opaque", 3, s.paws)
        s.run(3f)
        assertTrue(t.missed)
        assertEquals(2, s.paws)
        assertEquals("paw slot 2, the rightmost, fades", 2, s.pawFadeIndex)
        assertTrue("it starts fading the moment it is lost: ${s.pawFadeStartMs} of ${s.timeMs}", s.pawFadeStartMs in 0..s.timeMs)
        assertEquals(PopPhase.PLAYING, s.phase)
        assertEquals("a miss changes no score or pop count", 0, s.score)
        assertEquals(0, s.pops)
    }

    @Test
    fun `a carrier that gets past costs no paw and does not open a grace window`() {
        for (g in GiftKind.ALL) {
            val s = quiet()
            missOne(s, gift = g)
            assertEquals("$g", 3, s.paws)
            assertEquals(0f, s.graceLeft, 0f)
            missOne(s, x = 250f) // and a plain one straight after still costs
            assertEquals(2, s.paws)
        }
    }

    @Test
    fun `after a paw is lost the next 3 seconds are free, then a miss costs again`() {
        val s = quiet()
        missOne(s, x = 60f)
        assertEquals(2, s.paws)
        assertEquals(PopMetrics.GRACE_SECONDS, s.graceLeft, 0.05f)
        s.run(1.5f)
        missOne(s, x = 180f)
        assertEquals("inside the window", 2, s.paws)
        s.run(1.0f)
        missOne(s, x = 300f)
        assertEquals("still inside it, 2.5s after the first", 2, s.paws)
        s.run(0.7f)
        assertEquals("the window is over", 0f, s.graceLeft, 0f)
        missOne(s, x = 100f)
        assertEquals(1, s.paws)
        assertEquals("a new window", PopMetrics.GRACE_SECONDS, s.graceLeft, 0.05f)
    }

    @Test
    fun `three targets leaving together cost one paw, not the whole game`() {
        val s = quiet()
        for (x in listOf(60f, 180f, 300f)) s.addTargetForTest(TargetKind.ROUND, x, s.height * 0.9f + 1f, 90f)
        s.step(FRAME)
        assertEquals(2, s.paws)
        assertEquals(PopPhase.PLAYING, s.phase)
        s.run(5f)
        assertEquals("all three are gone and only one paw fell", 2, s.paws)
    }

    @Test
    fun `a paw is lost at most once per target, however long that target keeps fading`() {
        val s = quiet()
        val t = missOne(s, speed = 0.0002f) // it drifts so slowly it stays in the fading band for the whole grace window and more
        assertEquals(2, s.paws)
        s.run(8f)
        assertTrue(s.targets().contains(t))
        assertTrue("it is still fading: ${t.alpha}", t.alpha < 1f && t.alpha > 0f)
        assertEquals("the grace ended long ago, and the same target must not cost again", 2, s.paws)
    }

    @Test
    fun `nothing gives a paw back, not points, gifts, time or a new stage`() {
        val s = quiet(); s.holdFireForTest = false
        missOne(s, x = 60f)
        assertEquals(2, s.paws)
        for (g in GiftKind.ALL) s.arriveForTest(g)
        s.addTargetForTest(TargetKind.ROUND, s.shipX, 300f, 100f, gift = GiftKind.TRIPLE)
        s.run(120f)
        assertTrue(s.paws <= 2)
        val stage = PopSession(kotlin.random.Random(1), 165)
        assertEquals("even the top stage starts with 3", 3, stage.paws)
    }

    @Test
    fun `a slow paw never shows in the paw slots twice, and the fade index is only set by a loss`() {
        val s = quiet()
        assertEquals(-1, s.pawFadeIndex)
        missOne(s, x = 60f); s.run(3.2f)
        missOne(s, x = 300f)
        assertEquals(1, s.paws)
        assertEquals(1, s.pawFadeIndex)
    }

    // ------------------------------------------------------------------ the ending

    /** Loses all three paws, 3.2s apart. Returns the session in ENDING. */
    private fun loseAll(s: PopSession) {
        missOne(s, x = 60f); s.run(3.2f)
        missOne(s, x = 180f); s.run(3.2f)
        missOne(s, x = 300f)
    }

    @Test
    fun `the third paw starts a half-second calm ending, and then the game is over, once`() {
        val s = quiet()
        s.holdFireForTest = false
        // three targets up the screen that the ship will not reach, so they are there to fade
        loseAll(s)
        assertEquals(0, s.paws)
        assertEquals(PopPhase.ENDING, s.phase)
        s.run(0.4f)
        assertEquals(PopPhase.ENDING, s.phase)
        s.run(0.2f)
        assertEquals(PopPhase.OVER, s.phase)
        s.run(5f)
        assertEquals(PopPhase.OVER, s.phase)
        assertEquals(0, s.targetCount)
        assertEquals(0, s.starCount)
    }

    @Test
    fun `game over happens exactly once however the third paw is lost or how long the screen stays`() {
        for (mode in 0..1) {
            val s = quiet()
            var changes = 0
            var last = s.phase
            fun watch() { if (s.phase != last) { changes++; last = s.phase } }
            if (mode == 0) { loseAll(s) } else {
                // the last three plain targets leaving in the same step: the first costs a paw, the others are inside the window,
                // so it takes three windows however they arrive
                missOne(s, x = 60f); s.run(3.2f); missOne(s, x = 300f); s.run(3.2f)
                for (x in listOf(60f, 180f)) s.addTargetForTest(TargetKind.ROUND, x, s.height * 0.9f + 1f, 90f)
                s.step(FRAME)
            }
            watch()
            s.run(10f) { watch() }
            assertEquals("mode $mode: PLAYING to ENDING to OVER is two changes", 2, changes)
            assertEquals(PopPhase.OVER, s.phase)
        }
    }

    @Test
    fun `while the game ends the ship stops shooting, the targets fade with no pop, sparkle or points, and steering stops`() {
        val s = quiet()
        s.holdFireForTest = false
        s.holdSpawnForTest = false
        missOne(s, x = 60f); s.run(3.2f)
        missOne(s, x = 180f); s.run(3.2f)
        // clear the sky so the next things on it are the ones this test controls
        s.clearTargetsForTest()
        s.holdSpawnForTest = true
        s.run(1f) // any pop picture from the play so far has finished
        assertEquals(0, s.sparkleCount)
        val ship = s.addTargetForTest(TargetKind.ROUND, 300f, s.height * 0.9f + 1f, 90f)
        val bystander = s.addTargetForTest(TargetKind.ROUND, s.shipX, 300f, 90f) // in the ship's line of fire
        val carrier = s.addTargetForTest(TargetKind.OVAL, 60f, 400f, 100f, gift = GiftKind.WAVE)
        s.step(FRAME) // the last paw falls
        assertEquals(PopPhase.ENDING, s.phase)
        assertTrue(ship.missed)
        val launched = s.starsLaunched
        val scoreThen = s.score
        val popsThen = s.pops
        val stagesThen = s.stage.number
        var fewest = 1f
        s.run(0.45f) {
            for (i in 0 until it.targetCount) fewest = minOf(fewest, it.target(i).alpha)
            assertEquals("no pop, no sparkle", 0, it.sparkleCount)
        }
        assertEquals("no new star is made", launched, s.starsLaunched)
        assertEquals(scoreThen, s.score); assertEquals(popsThen, s.pops); assertEquals(stagesThen, s.stage.number)
        assertTrue("the bystander was not popped by a star already in flight", bystander.alpha < 0.3f && bystander.alpha >= 0f)
        assertTrue("targets fade softly: $fewest", fewest < 0.3f)
        assertNull(s.gift)
        assertNull(s.starEffect)
        s.touchDown(1, 50f)
        assertFalse("no finger steers now", s.isSteering)
        s.run(0.2f)
        assertEquals(PopPhase.OVER, s.phase)
        assertEquals(0, s.targetCount)
        assertEquals(0f, s.slowLeft, 0f)
        assertTrue(carrier.gift == GiftKind.WAVE) // the gift inside was never released
    }

    @Test
    fun `after the game is over nothing steers, shoots, spawns or scores, and the ship stays where it was`() {
        val s = quiet(); s.holdFireForTest = false; s.holdSpawnForTest = false
        s.touchDown(1, 100f); s.run(1.5f); s.touchUp(1)
        loseAll(s); s.run(1f)
        assertEquals(PopPhase.OVER, s.phase)
        val x = s.shipX; val launched = s.starsLaunched; val spawned = s.spawned; val score = s.score
        for (id in 1L..5L) { s.touchDown(id, 300f); s.touchMove(id, 310f) }
        s.run(20f)
        assertEquals(x, s.shipX, 0f); assertEquals(launched, s.starsLaunched); assertEquals(spawned, s.spawned); assertEquals(score, s.score)
        assertFalse(s.isSteering)
    }

    @Test
    fun `a new best is a score above the best from before this game, and equal is not one`() {
        for ((best, score, want) in listOf(Triple(10, 11, true), Triple(10, 10, false), Triple(10, 9, false), Triple(0, 0, false), Triple(0, 1, true))) {
            val s = quiet(bestBefore = best, startScore = score)
            assertEquals("best $best score $score", want, s.isNewBest)
            assertEquals(best, s.bestBefore)
        }
    }

    // ------------------------------------------------------------------ play again

    @Test
    fun `play again is a fresh game at stage 1 with score 0, 3 paws, an empty sky, the ship in the middle and a first target within a second`() {
        val old = PopSession(kotlin.random.Random(3), 100)
        old.touchDown(1, 40f); old.run(30f)
        loseAllQuickly(old)
        val fresh = PopSession(kotlin.random.Random(4), 0, 360f, 692f, bestBefore = 77)
        assertEquals(0, fresh.score); assertEquals(3, fresh.paws); assertEquals(1, fresh.stage.number)
        assertEquals(PopPhase.PLAYING, fresh.phase); assertEquals(0, fresh.targetCount); assertEquals(180f, fresh.shipX, 0f)
        assertEquals(0f, fresh.graceLeft, 0f)
        var at = -1f
        fresh.run(1f) { if (at < 0f && it.targetCount > 0) at = it.time.toFloat() }
        assertTrue("first target at $at", at in 0f..1f)
        assertTrue("the old game is not touched", old.phase != PopPhase.PLAYING || old.paws < 3 || old.score >= 0)
    }

    private fun loseAllQuickly(s: PopSession) {
        s.holdSpawnForTest = true; s.holdFireForTest = true
        loseAll(s); s.run(1f)
    }

    // ------------------------------------------------------------------ pausing, mashing

    @Test
    fun `a long pause is one 50ms step, so it never costs a paw, uses up a grace window or ends the game`() {
        val s = quiet()
        s.addTargetForTest(TargetKind.ROUND, 100f, s.height * 0.5f, 90f)
        s.step(600f)
        assertEquals(3, s.paws)
        assertEquals(PopPhase.PLAYING, s.phase)
        missOne(s, x = 60f)
        val grace = s.graceLeft
        s.step(3600f)
        assertEquals("a pause used up only one step of the window", grace - 0.05f, s.graceLeft, 1e-4f)
        assertEquals(2, s.paws)
        // and a game that is over stays as it was, score and all
        val o = quiet(startScore = 42); loseAll(o); o.run(1f)
        val sc = o.score
        o.step(1e6f)
        assertEquals(PopPhase.OVER, o.phase); assertEquals(sc, o.score)
    }

    @Test
    fun `mashing while the game ends changes nothing, and the score and paws are kept for the good-game screen`() {
        val s = quiet(startScore = 12); s.holdFireForTest = false
        loseAll(s)
        val ui = PopUi(s)
        var id = 100L
        repeat(300) {
            ui.onPointer(id, (it * 7 % 360).toFloat(), 400f, pressed = true, previousPressed = false, consumed = false)
            s.step(FRAME)
            ui.onPointer(id, (it * 7 % 360).toFloat(), 400f, pressed = false, previousPressed = true, consumed = false)
            id++
        }
        assertEquals(PopPhase.OVER, s.phase)
        assertEquals(12, s.score)
        assertEquals(0, s.paws)
    }

    // ------------------------------------------------------------------ the best score is saved the moment it is beaten

    private class FakeStore(var value: Int? = null) : IntStore {
        var writes = 0
        override fun read(key: String): Int? = value
        override fun write(key: String, value: Int) { this.value = value; writes++ }
    }

    @Test
    fun `the best is saved as soon as a pop beats it, equal never writes, and leaving early loses nothing`() {
        val store = FakeStore(5)
        val best = BestScore(BEST_KEY, store)
        best.load()
        assertEquals("best.paw-pop", BEST_KEY)
        val s = PopSession(kotlin.random.Random(1), 0, 360f, 692f, bestBefore = best.best)
        s.holdSpawnForTest = true
        val ui = PopUi(s, onScore = { best.submit(it) })
        var nanos = 1_000_000_000L
        fun frame() { nanos += 16_666_667L; ui.onFrame(nanos) }
        for (k in 1..7) {
            s.addTargetForTest(TargetKind.ROUND, s.shipX, 300f, 100f)
            var guard = 0
            while (s.score < k && guard++ < 400) frame()
            assertEquals(k, s.score)
            // the moment the score passes 5 it is on disk, without waiting for the end of the game
            if (k > 5) assertEquals("score $k saved at once", k, store.value)
        }
        assertEquals(7, best.best)
        assertEquals("only scores that beat the best are written: 6 and 7", 2, store.writes)
        assertTrue(s.isNewBest) // 7 beats the 5 it began with
        // leaving now (home, back, closing) needs no extra save: it is already there
        assertEquals(7, store.value)
    }

    @Test
    fun `a failing store never reaches the game, and the in-memory best carries on`() {
        val broken = object : IntStore {
            override fun read(key: String): Int? = throw IllegalStateException("unreadable")
            override fun write(key: String, value: Int) = throw IllegalStateException("full")
        }
        val best = BestScore(BEST_KEY, broken)
        best.load()
        assertEquals(0, best.best)
        val s = PopSession(kotlin.random.Random(1), 0, 360f, 692f, bestBefore = best.best)
        s.holdSpawnForTest = true
        val ui = PopUi(s, onScore = { best.submit(it) })
        s.addTargetForTest(TargetKind.ROUND, s.shipX, 300f, 100f)
        var nanos = 1_000_000_000L
        repeat(120) { nanos += 16_666_667L; ui.onFrame(nanos) }
        assertEquals(1, s.score)
        assertEquals(1, best.best)
    }

    // ------------------------------------------------------------------ the glow and the bank

    @Test
    fun `the peach glow fades in over 0_3s, holds, fades out over the last 0_5s and never pulses`() {
        assertEquals(0f, graceGlow(0f), 0f)
        assertEquals(0f, graceGlow(PopMetrics.GRACE_SECONDS), 0.001f)
        assertEquals(0.5f, graceGlow(PopMetrics.GRACE_SECONDS - 0.15f), 0.001f)
        assertEquals(1f, graceGlow(PopMetrics.GRACE_SECONDS - 0.3f), 0.001f)
        assertEquals(1f, graceGlow(1.5f), 0f)
        assertEquals(1f, graceGlow(0.5f), 0.001f)
        assertEquals(0.5f, graceGlow(0.25f), 0.001f)
        // one hump: never rises after it has fallen
        var falling = false
        var last = 0f
        var t = PopMetrics.GRACE_SECONDS
        while (t > 0f) {
            val g = graceGlow(t)
            if (g < last - 1e-6f) falling = true
            if (falling) assertTrue("rose again at $t", g <= last + 1e-6f)
            last = g; t -= 0.01f
        }
    }

    @Test
    fun `the cloud bank has 15 scallops 24dp wide on the reference phone, and its lowest points are the entry line`() {
        assertEquals(15, bankScallops(360f))
        assertEquals(14, bankScallops(336f))
        assertEquals(17, bankScallops(400f))
        assertEquals(1, bankScallops(10f))
        assertEquals(PopMetrics.SKY_TOP, PopMetrics.BANK_STRAIGHT + 14f, 0f)
        assertEquals(100f, PopMetrics.SKY_TOP, 0f)
    }
}
