package com.pawplay.app.games.pawpop

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

/** Code-review fixes for Paw Pop: no double turn in a step, the frame loop stops when the game is over, and tiny or shrinking play areas. */
class PopReviewFixesTest {

    // ------------------------------------------------------------------ one turn per step

    @Test
    fun `a leg-end turn and a wall turn never happen in the same step`() {
        // A 112dp-wide area and a 96dp target: the body's room is a single point (x 56), so whichever way it heads it wants the wall turn too.
        val st = PopRamp.stages[4]
        val t = PopTarget(TargetKind.ROUND, 0, 0, 96f, null, 56f, 300f)
        val random = Random(4)
        PopPath.begin(t, st, random)
        t.entered = true; t.sinceEntered = 10f; t.sinceTurn = 10f; t.side = 1; t.legLeft = 0f
        PopPath.advance(t, 1f / 60f, st, 112f, 692f, 1f, random)
        assertEquals("one turn, not two", 1, t.turns)
        assertEquals("it turned once, from right to left", -1, t.side)
    }

    @Test
    fun `however narrow the area, turns are never less than 0_9s apart and never two in a step`() {
        for (width in listOf(112f, 120f, 130f, 160f)) for (seed in 1..4) {
            val st = PopRamp.stages[4]
            val t = PopTarget(TargetKind.ROUND, 0, 0, 96f, null, width / 2f, 150f)
            val random = Random(seed)
            PopPath.begin(t, st, random)
            var time = 0.0
            var lastTurnAt = Double.NaN
            var turns = 0
            while (time < 60.0) {
                val before = t.turns
                PopPath.advance(t, 1f / 60f, st, width, 4000f, 1f, random)
                time += 1.0 / 60.0
                val made = t.turns - before
                assertTrue("width $width seed $seed: $made turns in one step at ${"%.2f".format(time)}s", made <= 1)
                if (made == 1) {
                    if (!lastTurnAt.isNaN()) assertTrue("width $width seed $seed: turns ${time - lastTurnAt}s apart", time - lastTurnAt >= PopMetrics.MIN_TURN_GAP - 0.02)
                    lastTurnAt = time
                    turns++
                }
            }
            assertTrue("width $width: it did turn now and then ($turns)", turns >= 10)
        }
    }

    // ------------------------------------------------------------------ the frame loop

    @Test
    fun `the frame loop keeps going through play and the fade-out and stops once the game is over`() {
        val s = PopSession(Random(1), startPaws = 1)
        val ui = PopUi(s)
        assertTrue(ui.active)
        var nanos = 1_000_000_000L
        var frames = 0
        var framesInEnding = 0
        while (ui.active && frames < 60 * 600) {
            nanos += 16_666_667L
            ui.onFrame(nanos)
            frames++
            if (s.phase == PopPhase.ENDING) framesInEnding++
        }
        assertEquals("the game ends by itself with nobody playing", PopPhase.OVER, s.phase)
        assertFalse("and then no more frames are wanted", ui.active)
        assertTrue("the fade-out (0.5s) was still drawn frame by frame: $framesInEnding", framesInEnding in 25..40)
        // The frame that ended it left the final picture in the frame state; a stray extra frame is harmless.
        ui.onFrame(nanos + 16_666_667L)
        assertFalse(ui.active)
    }

    @Test
    fun `a fresh session is active again, so play again restarts the loop`() {
        val over = PopSession(Random(1), startPaws = 0)
        assertFalse(PopUi(over).active)
        assertTrue(PopUi(PopSession(Random(2))).active)
    }

    // ------------------------------------------------------------------ the play area changes

    @Test
    fun `on an ordinary phone the stars end at the entry line`() {
        assertEquals(PopMetrics.SKY_TOP, session(1, 0, 360f, 692f).skyLine, 0f)
        assertEquals(PopMetrics.SKY_TOP, session(1, 0, 320f, 400f).skyLine, 0f)
    }

    @Test
    fun `a shorter area never carries a target that has entered back above the entry line`() {
        val s = sessionNoLoss(1, 0, 400f, 700f); s.holdSpawnForTest = true; s.holdFireForTest = true
        val a = s.addTargetForTest(TargetKind.ROUND, 200f, 300f, 100f)
        val b = s.addTargetForTest(TargetKind.HEART, 120f, 180f, 80f)
        assertTrue(a.entered && b.entered)
        for (height in listOf(400f, 250f, 160f, 700f, 120f)) {
            s.setArea(400f, height)
            for (t in listOf(a, b)) {
                assertTrue("height $height: ${t.kind} top edge ${t.y - t.kind.top * t.size}", PopPath.isBelowLine(t))
                assertTrue(t.entered)
            }
        }
    }

    @Test
    fun `the ribbon is never negative, on any height`() {
        for (height in listOf(100f, 150f, 200f, 212f, 260f, 692f)) {
            val s = sessionNoLoss(1, 0, 360f, height); s.holdSpawnForTest = true
            s.arriveForTest(GiftKind.RIBBON)
            s.run(2f) { assertTrue("height $height: ribbon ${it.ribbonLength}", it.ribbonLength >= 0f) }
        }
        val s = sessionNoLoss(1, 0, 360f, 692f); s.holdSpawnForTest = true
        s.arriveForTest(GiftKind.RIBBON); s.run(0.5f)
        s.setArea(360f, 150f)
        s.run(1f) { assertTrue(it.ribbonLength >= 0f) }
    }

    @Test
    fun `on a very short window the stars still fly`() {
        for (height in listOf(170f, 200f, 212f, 240f)) {
            val s = sessionNoLoss(1, 0, 360f, height); s.holdSpawnForTest = true
            var highest = Float.MAX_VALUE
            var seen = 0
            s.run(3f) { g ->
                for (i in 0 until g.starCount) { highest = minOf(highest, g.stars[i].y); seen++ }
            }
            assertTrue("height $height: stars launched ${s.starsLaunched}", s.starsLaunched >= 5)
            assertTrue("height $height: a star was seen in flight", seen > 0)
            assertTrue("height $height: the highest a star reached was $highest (nose ${s.nose})", highest < s.nose - 20f)
        }
    }

    @Test
    fun `the first target still spawns within a second, on a short window and on an ordinary one`() {
        for (height in listOf(200f, 692f)) {
            val s = sessionNoLoss(1, 0, 360f, height); s.holdFireForTest = true
            s.run(1f)
            assertTrue("height $height: spawned ${s.spawned}", s.spawned >= 1)
        }
    }
}
