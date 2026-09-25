package com.pawplay.app.games.pawpop

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs

/** Story 52 (the ship goes where the finger is) and story 61 (newest finger, cancel, lift). */
class PopSteeringTest {

    @Test
    fun `the ship starts in the middle and is at least 72dp wide`() {
        val s = session()
        assertEquals(180f, s.shipX, 0.001f)
        assertTrue(2 * PopMetrics.SHIP_REACH >= 72f)
    }

    @Test
    fun `a touch anywhere makes the ship glide to that horizontal position, and only the horizontal position`() {
        for (x in listOf(60f, 100f, 250f, 300f)) {
            val s = session()
            s.touchDown(1L, x)
            s.run(2.5f)
            assertEquals("ship for finger at $x", x, s.shipX, 0.5f)
        }
        // there is nowhere to give a height: the API takes x only, and the ship's own line never changes
        val s = session()
        val y0 = s.shipCentreY
        s.touchDown(1L, 300f); s.run(1f)
        assertEquals(y0, s.shipCentreY, 0.0f)
    }

    @Test
    fun `the ship never moves faster than 1_5 screen widths a second and slows softly as it arrives`() {
        for (dt in listOf(FRAME, 1f / 120f, 0.05f)) {
            val s = session()
            s.touchDown(1L, 500f) // clamps to the right edge
            var last = s.shipX
            var fastest = 0f
            var firstMove = 0f
            var lastMove = 0f
            s.run(3f, dt) {
                val move = abs(it.shipX - last)
                if (firstMove == 0f) firstMove = move
                if (move > 0f) lastMove = move
                fastest = maxOf(fastest, move / dt)
                last = it.shipX
            }
            assertTrue("dt=$dt fastest=$fastest", fastest <= 1.5f * 360f + 0.5f)
            assertTrue("dt=$dt reaches top speed: $fastest", fastest > 1.4f * 360f)
            assertTrue("dt=$dt eases: first step $firstMove, last step $lastMove", lastMove < firstMove / 10f)
            assertEquals(314f, s.shipX, 0.3f)
        }
    }

    @Test
    fun `top speed follows the width of the screen`() {
        val s = session(width = 411f, height = 880f)
        s.touchDown(1L, 0f)
        var last = s.shipX
        var fastest = 0f
        s.run(2f) { fastest = maxOf(fastest, abs(it.shipX - last) / FRAME); last = it.shipX }
        assertTrue(fastest <= 1.5f * 411f + 0.5f)
        assertTrue(fastest > 1.4f * 411f)
    }

    @Test
    fun `a tap moves the ship there even though the finger has already lifted`() {
        val s = session()
        s.touchDown(1L, 90f)
        s.step(FRAME)
        s.touchUp(1L)
        assertFalse(s.isSteering)
        s.run(2.5f)
        assertEquals(90f, s.shipX, 0.5f)
    }

    @Test
    fun `with no finger down the ship stays exactly where it is and keeps firing`() {
        val s = session()
        s.touchDown(1L, 100f); s.run(2.5f); s.touchUp(1L)
        val at = s.shipX
        val ticks = s.fireTicks
        s.run(6f)
        assertEquals(at, s.shipX, 0f)
        assertTrue("fired ${s.fireTicks - ticks} times in 6s", s.fireTicks - ticks in 14..16)
    }

    @Test
    fun `the whole ship stays at least 8dp inside both sides, whatever the finger does`() {
        for (w in listOf(320f, 360f, 411f)) {
            val s = session(width = w)
            for (x in listOf(-500f, -1f, 0f, 5f, w - 5f, w, w + 1f, 100000f)) {
                s.touchDown(1L, x); s.run(2.5f) { check(it, w) }
                s.touchUp(1L)
            }
        }
    }

    private fun check(s: PopSession, w: Float) {
        assertTrue("left fin at ${s.shipX - 37f}", s.shipX - 37f >= 8f - 0.01f)
        assertTrue("right fin at ${s.shipX + 37f}", s.shipX + 37f <= w - 8f + 0.01f)
    }

    @Test
    fun `the ship sits 24dp above the bottom and stays on its own line`() {
        for (h in listOf(520f, 692f, 830f)) {
            val s = session(height = h)
            assertEquals(h - 24f, s.shipCentreY + PopMetrics.SHIP_SIZE / 2f, 0.001f)
            assertEquals(h - 24f - 88f, s.nose, 0.001f)
        }
    }

    @Test
    fun `the newest finger steers and older fingers are ignored until they lift and land again`() {
        val s = session()
        s.touchDown(1L, 100f)
        s.touchDown(2L, 300f)            // a newer finger takes over at once
        s.touchMove(1L, 50f)             // the older finger wiggles: nothing
        s.run(2.5f)
        assertEquals(300f, s.shipX, 0.5f)
        s.touchMove(2L, 200f); s.run(2.5f)
        assertEquals(200f, s.shipX, 0.5f)
        s.touchUp(2L)                    // the newest lifts: the ship stays put ...
        s.touchMove(1L, 54f)             // ... and the older finger, still down and only resting, does not capture it
        s.run(2f)
        assertEquals(200f, s.shipX, 0.5f)
        assertFalse(s.isSteering)
        s.touchDown(1L, 60f)             // only a new touch steers again
        s.run(2.5f)
        assertEquals(60f, s.shipX, 0.5f)
    }

    @Test
    fun `when the steering finger lifts, an older finger that keeps dragging takes over once it has moved a little`() {
        val s = session()
        s.touchDown(1L, 100f)            // the older finger
        s.touchDown(2L, 300f)            // the newest steers
        s.run(2.5f)
        assertEquals(300f, s.shipX, 0.5f)
        s.touchUp(2L)                    // the newest lifts; the ship finishes at 300 and nothing steers
        assertFalse(s.isSteering)
        s.touchMove(1L, 100f + PopMetrics.ADOPT_SLOP - 1f)  // a tremor is not enough
        assertFalse(s.isSteering)
        s.touchMove(1L, 100f + PopMetrics.ADOPT_SLOP)       // a real drag is
        assertTrue(s.isPilot(1L))
        s.run(2.5f)
        assertEquals("follows the finger that is still dragging", 108f, s.shipX, 0.5f)
        s.touchMove(1L, 150f); s.run(2f)
        assertEquals(150f, s.shipX, 0.5f)
        // and when that one lifts too, the ship stays
        s.touchUp(1L); s.run(1f)
        assertEquals(150f, s.shipX, 0.5f)
        assertFalse(s.isSteering)
    }

    @Test
    fun `a resting hand is never adopted, however long it stays and however far the ship is from it`() {
        val s = session()
        s.touchDown(1L, 30f)             // a palm lands first and rests
        s.touchDown(2L, 320f)
        s.run(2.5f); s.touchUp(2L)
        repeat(20) { s.touchMove(1L, 30f + (it % 3) * 2f); s.run(0.25f) } // stays put, jitters by a few dp
        assertFalse(s.isSteering)
        assertEquals(314f, s.shipX, 0.5f)
        // creeping slowly is measured from the hand-over, so it never adds up to a capture while it stays within the slop
        s.touchMove(1L, 30f + PopMetrics.ADOPT_SLOP - 0.5f)
        assertFalse(s.isSteering)
    }

    @Test
    fun `the slop is measured from where the older finger was when the steering finger lifted`() {
        val s = session()
        s.touchDown(1L, 100f)
        s.touchMove(1L, 100f)
        s.touchDown(2L, 200f)
        s.touchMove(1L, 150f)            // the older finger wandered while it was not steering
        s.touchUp(2L)                    // hand-over point for finger 1 is 150
        s.touchMove(1L, 152f)
        assertFalse(s.isSteering)
        s.touchMove(1L, 160f)
        assertTrue(s.isPilot(1L))
        s.run(2f)
        assertEquals(160f, s.shipX, 0.5f)
    }

    @Test
    fun `three fingers hand over in turn, newest first`() {
        val s = session()
        s.touchDown(1L, 60f); s.touchDown(2L, 120f); s.touchDown(3L, 240f)
        assertTrue(s.isPilot(3L))
        s.touchUp(3L)
        s.touchMove(2L, 140f); s.touchMove(1L, 80f)     // both older fingers move: the first to move far enough takes over
        assertTrue(s.isPilot(2L))
        s.touchUp(2L)
        s.touchMove(1L, 80f + 10f)
        assertTrue(s.isPilot(1L))
        s.run(2f)
        assertEquals(90f, s.shipX, 0.5f)
    }

    @Test
    fun `a cancelled steering finger stops the ship, an older finger still down can take over only by moving, and cancel-all forgets everyone`() {
        val s = session()
        s.touchDown(1L, 60f); s.touchDown(2L, 320f)
        s.run(0.1f)
        val at = s.shipX
        s.touchCancel(2L)
        s.run(1f)
        assertEquals(at, s.shipX, 0f)
        assertFalse(s.isSteering)
        s.touchMove(1L, 61f); assertFalse(s.isSteering)
        s.touchMove(1L, 80f); assertTrue(s.isPilot(1L))
        s.touchCancelAll()
        s.touchMove(1L, 200f)            // forgotten: a finger that is not known to be down cannot steer
        assertFalse(s.isSteering)
        // a finger that was never seen landing (it landed on home) cannot steer by moving
        val t = session()
        t.touchMove(5L, 300f)
        assertFalse(t.isSteering)
    }

    @Test
    fun `more fingers than a hand never break the rule`() {
        val s = session()
        for (id in 0L until 25L) s.touchDown(id, 20f + id * 10f)
        assertTrue(s.isPilot(24L))
        for (id in 24L downTo 0L) { s.touchUp(id); s.touchMove(id - 1, 100f + id) }
        s.run(2f)
        assertTrue(s.shipX in 46f..314f)
    }

    @Test
    fun `the pointer layer hands over the same way`() {
        val ui = PopUi(session())
        val s = ui.session
        fun down(id: Long, x: Float) = ui.onPointer(id, x, 400f, pressed = true, previousPressed = false, consumed = false)
        fun move(id: Long, x: Float) = ui.onPointer(id, x, 400f, pressed = true, previousPressed = true, consumed = false)
        fun up(id: Long, x: Float) = ui.onPointer(id, x, 400f, pressed = false, previousPressed = true, consumed = false)
        down(1L, 100f); down(2L, 300f)
        assertEquals(PopUi.Took.NOTHING, move(1L, 105f))       // the older finger is not steering
        assertEquals(PopUi.Took.CHANGED, up(2L, 300f))
        assertEquals(PopUi.Took.NOTHING, move(1L, 106f))       // resting: still not
        assertEquals(PopUi.Took.FOLLOWED, move(1L, 130f))      // dragging: it steers now
        s.run(2.5f)
        assertEquals(130f, s.shipX, 0.5f)
        assertEquals(PopUi.Took.NOTHING, up(9L, 10f))           // a finger that never steered lifting changes nothing
        // a finger that landed on home never enters the picture
        assertEquals(PopUi.Took.NOTHING, ui.onPointer(5L, 40f, 40f, pressed = true, previousPressed = false, consumed = false))
        assertEquals(PopUi.Took.NOTHING, move(5L, 200f))
    }

    @Test
    fun `an older finger lifting does not disturb the newest`() {
        val s = session()
        s.touchDown(1L, 100f); s.touchDown(2L, 250f)
        s.touchUp(1L)
        assertTrue(s.isSteering)
        s.touchMove(2L, 280f); s.run(2.5f)
        assertEquals(280f, s.shipX, 0.5f)
    }

    @Test
    fun `a cancelled touch leaves the ship where it is instead of finishing the glide`() {
        val s = session()
        s.touchDown(1L, 320f)
        s.run(0.15f)
        val at = s.shipX
        assertTrue(at > 180f && at < 300f)
        s.touchCancel(1L)
        s.run(2f)
        assertEquals(at, s.shipX, 0f)
        assertFalse(s.isSteering)
        // the cancel of a finger that was not steering changes nothing
        s.touchDown(2L, 100f)
        s.touchCancel(9L)
        assertTrue(s.isSteering)
    }

    @Test
    fun `no finger down any more does not stop a ship still gliding to where the last finger lifted`() {
        val s = session()
        s.touchDown(1L, 320f); s.run(0.1f); s.touchUp(1L)
        s.touchCancelAll() // the touch layer says "nothing is down" after every last lift
        s.run(2.5f)
        assertEquals(314f, s.shipX, 0.5f)
        // but a steering finger that simply vanished (no lift arrived) leaves the ship where it is
        val t = session()
        t.touchDown(1L, 320f); t.run(0.1f)
        val at = t.shipX
        t.touchCancelAll(); t.run(2f)
        assertEquals(at, t.shipX, 0f)
    }

    @Test
    fun `touches with a position that is not a number are ignored`() {
        val s = session()
        s.touchDown(1L, Float.NaN)
        assertFalse(s.isSteering)
        s.touchDown(1L, 100f)
        s.touchMove(1L, Float.NaN); s.touchMove(1L, Float.POSITIVE_INFINITY)
        s.run(2.5f)
        assertEquals(100f, s.shipX, 0.5f)
    }

    @Test
    fun `mashing many fingers never leaves the ship outside its range or the session in a odd state`() {
        val s = session()
        val r = java.util.Random(5)
        var id = 0L
        repeat(20000) {
            when (r.nextInt(6)) {
                0 -> s.touchDown(id++ % 5, r.nextFloat() * 400f - 20f)
                1 -> s.touchMove(r.nextInt(5).toLong(), r.nextFloat() * 400f - 20f)
                2 -> s.touchUp(r.nextInt(5).toLong())
                3 -> s.touchCancel(r.nextInt(5).toLong())
                4 -> if (r.nextInt(20) == 0) s.touchCancelAll()
                else -> Unit
            }
            s.step(FRAME)
            assertTrue(s.shipX in 45f..315f)
        }
    }

    @Test
    fun `the pointer layer maps touches onto the session and never steers from the home button`() {
        val ui = PopUi(session())
        val s = ui.session
        // a finger landing on home (its 56dp square at 20,20) never steers, even if it slides out
        assertEquals(PopUi.Took.NOTHING, ui.onPointer(1L, 40f, 40f, true, false, false))
        assertEquals(PopUi.Took.NOTHING, ui.onPointer(1L, 300f, 400f, true, true, false))
        assertFalse(s.isSteering)
        // anywhere else steers at once, and the height changes nothing
        assertEquals(PopUi.Took.CHANGED, ui.onPointer(2L, 300f, 10f, true, false, false))
        assertEquals(PopUi.Took.FOLLOWED, ui.onPointer(2L, 100f, 600f, true, true, false))
        assertTrue(s.isSteering)
        s.run(2.5f); assertEquals(100f, s.shipX, 0.5f)
        // a real lift: the ship keeps going to where the finger was
        ui.onPointer(2L, 250f, 300f, true, true, false)
        assertEquals(PopUi.Took.CHANGED, ui.onPointer(2L, 250f, 300f, false, true, false))
        s.run(2.5f); assertEquals(250f, s.shipX, 0.5f)
        // a consumed up is a cancel: the ship just stays
        ui.onPointer(3L, 60f, 300f, true, false, false)
        s.run(0.1f)
        val at = s.shipX
        assertEquals(PopUi.Took.CHANGED, ui.onPointer(3L, 60f, 300f, false, true, true))
        s.run(2f); assertEquals(at, s.shipX, 0f)
        // a pointer that was never steering is not "taken"
        assertEquals(PopUi.Took.NOTHING, ui.onPointer(7L, 10f, 10f, false, true, false))
        ui.onPointer(4L, 200f, 300f, true, false, false)
        ui.cancelAll(); assertFalse(s.isSteering)
    }

    @Test
    fun `the frame clock feeds the session and the first frame or a backwards clock moves nothing`() {
        val ui = PopUi(session())
        ui.onFrame(5_000_000_000L)
        assertEquals(0.0, ui.session.time, 0.0)
        ui.onFrame(5_016_000_000L)
        assertEquals(0.016, ui.session.time, 1e-6)
        ui.onFrame(4_000_000_000L) // clock went backwards
        assertEquals(0.016, ui.session.time, 1e-6)
        ui.onFrame(64_000_000_000L) // a minute in the background: one 50ms step
        assertEquals(0.066, ui.session.time, 1e-6)
    }
}
