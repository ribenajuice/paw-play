package com.pawplay.app.games.pawblocks

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

/** Code-review fixes for Paw Blocks: backgrounding does not count as time, the game's clock, and the peeking animal on short phones. */
class BlocksReviewFixesTest {
    private val dot = BlockShapes.dot
    private fun shape(id: String) = BlockShapes.byId.getValue(id)

    /** A 5x5 checkerboard with a dot about to go in: after it, nothing in the tray fits (the same set-up as the game-over tests). */
    private fun stuckAfterOneMove(paws: Int): BlocksUi {
        val board = Board.from(5) { r, c -> if ((r + c) % 2 == 0) null else Family.DOT }
        val session = BlocksSession(Random(5), startBoard = board, startTray = listOf(dot, shape("bar2h"), shape("bar2v")), startPaws = paws)
        val ui = BlocksUi(session)
        ui.onFrame(50_000) // the first frame: game time 0
        assertNotNull(session.place(0, 0, 0, 0))
        assertTrue(session.isStuck)
        return ui
    }

    @Test
    fun `time spent in the background does not fire the clear-out or the ending on the first frame back`() {
        for (paws in listOf(3, 0)) {
            val ui = stuckAfterOneMove(paws)
            ui.onFrame(50_500) // half a second of play
            assertEquals(paws, ui.session.paws)
            ui.resumed() // the app went to the background for ten minutes and came back
            ui.onFrame(50_500 + 600_000)
            assertEquals("paws $paws: no clear-out on the first frame back", paws, ui.session.paws)
            assertEquals(BlocksPhase.PLAYING, ui.session.phase)
            ui.onFrame(50_500 + 600_000 + 400)
            assertEquals("still inside the one quiet second: 0.9s in", paws, ui.session.paws)
            assertEquals(BlocksPhase.PLAYING, ui.session.phase)
            ui.onFrame(50_500 + 600_000 + 600) // 1.1s of real play in all
            if (paws > 0) assertEquals("the clear-out comes at its own time", paws - 1, ui.session.paws)
            else assertEquals("and so does the ending", BlocksPhase.GAME_OVER, ui.session.phase)
        }
    }

    @Test
    fun `the game's clock does not jump on resume, so effects and fades do not skip either`() {
        val ui = stuckAfterOneMove(3)
        ui.onFrame(50_300)
        val before = ui.frame.longValue
        ui.resumed()
        ui.onFrame(50_300 + 3_600_000)
        assertEquals(before, ui.frame.longValue)
        ui.onFrame(50_300 + 3_600_000 + 16)
        assertEquals(before + 16, ui.frame.longValue)
    }

    @Test
    fun `frames that follow each other normally are unaffected by resume`() {
        val ui = stuckAfterOneMove(3)
        ui.resumed() // e.g. the initial ON_RESUME, or a quick pull-down of the notification shade
        ui.onFrame(50_016)
        ui.onFrame(50_032)
        assertEquals("16ms + 16ms of real play after the pull-down, with the resume gap (16ms) left out", 16L, ui.frame.longValue)
        ui.onFrame(50_048)
        assertEquals(32L, ui.frame.longValue)
    }

    @Test
    fun `a gap with no resume is still real time, so an idle board that wakes on a touch is judged on real time`() {
        val ui = stuckAfterOneMove(3)
        ui.onFrame(50_000 + 5_000)
        assertEquals("nobody backgrounded the app: a second of quiet passed and the clear-out ran", 2, ui.session.paws)
    }

    @Test
    fun `the clock reads from zero before the first frame and stays on the same footing after it`() {
        val ui = BlocksUi(BlocksSession(Random(1)))
        assertTrue("a touch before the first frame is at about 0: ${ui.nowMs()}", ui.nowMs() in 0L..1_000L)
        ui.onFrame(9_000_000)
        assertEquals(0L, ui.frame.longValue)
        assertTrue("and about 0 just after it too: ${ui.nowMs()}", ui.nowMs() in 0L..1_000L)
        ui.onFrame(9_000_100)
        assertEquals(100L, ui.frame.longValue)
    }

    @Test
    fun `the peeking animal never rises above the strip on any phone`() {
        var checked = 0
        for (w in listOf(320f, 360f, 411f, 480f)) for (h in 400..1000 step 10) {
            val l = blocksLayout(w, h.toFloat())
            assertTrue("$w x $h: lift ${l.critterLift}", l.critterLift > 0f)
            assertTrue("$w x $h: animal drawn from y ${l.panelTop - l.critterLift}", l.panelTop - l.critterLift >= BlocksLayout.CRITTER_TOP_MIN - 0.001f)
            assertTrue("$w x $h: the animal's ears (about 6dp lower) stay clear of the score digits' bottom at 62", l.panelTop - l.critterLift + 6f >= 62f)
            checked++
        }
        assertTrue(checked > 100)
    }

    @Test
    fun `on the reference phone the animal rises exactly as designed, and a short phone gives it less`() {
        val ref = blocksLayout(360f, 692f)
        assertEquals(132f, ref.panelTop, 0f)
        assertEquals(68f, ref.critterLift, 0f)
        val short = blocksLayout(360f, 560f)
        assertTrue("the short phone's panel top is ${short.panelTop}", short.panelTop < ref.panelTop)
        assertTrue("its animal rises less: ${short.critterLift}", short.critterLift < 68f)
        assertFalse(short.panelTop - short.critterLift < BlocksLayout.CRITTER_TOP_MIN)
    }
}
