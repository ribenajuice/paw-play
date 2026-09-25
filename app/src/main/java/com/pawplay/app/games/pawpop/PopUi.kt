package com.pawplay.app.games.pawpop

import androidx.compose.runtime.mutableLongStateOf

/**
 * The screen's side of Paw Pop: the frame clock in, the touches in, and one state holder that tells the drawing to
 * run again. Every rule lives in [PopSession]; this only turns frames and pointer changes into calls on it.
 * Pointer positions are dp relative to the play area.
 */
internal class PopUi(val session: PopSession) {
    /** The frame time, bumped every frame: the drawing reads it, so it redraws every frame (the game never rests). */
    val frame = mutableLongStateOf(0L)

    private var lastNanos = 0L

    /** One frame at [nanos] (the screen's frame clock). The first frame after a start, or after a long gap, is one short step. */
    fun onFrame(nanos: Long) {
        val dt = if (lastNanos == 0L || nanos <= lastNanos) 0f else (nanos - lastNanos) / 1_000_000_000f
        lastNanos = nanos
        session.step(dt) // the session caps a step at 50ms, so backgrounding or a slow frame never makes anything jump
        frame.longValue = nanos
    }

    /** What [onPointer] did with a pointer change. */
    enum class Took { NOTHING, FOLLOWED, CHANGED }

    /** The home button's square, where a finger never steers. */
    private fun overHome(x: Float, y: Float): Boolean =
        x >= PopMetrics.HOME_INSET && x <= PopMetrics.HOME_INSET + PopMetrics.HOME_SIZE &&
            y >= PopMetrics.HOME_INSET && y <= PopMetrics.HOME_INSET + PopMetrics.HOME_SIZE

    /**
     * One pointer change from the touch layer. A finger that lands anywhere else steers at once (only its horizontal
     * position counts) and becomes the newest finger. A change that is already [consumed] when it arrives as an "up"
     * is how Compose reports a cancelled touch (a system gesture, a notification pull-down, an app switch): it is not
     * a lift, so the ship simply stays where it is instead of finishing a glide to a stale position.
     */
    fun onPointer(id: Long, x: Float, y: Float, pressed: Boolean, previousPressed: Boolean, consumed: Boolean): Took = when {
        pressed && !previousPressed ->
            if (overHome(x, y)) Took.NOTHING else { session.touchDown(id, x); Took.CHANGED }
        pressed ->
            if (session.isPilot(id)) { session.touchMove(id, x); Took.FOLLOWED } else Took.NOTHING
        previousPressed -> {
            val mine = session.isPilot(id)
            if (consumed) session.touchCancel(id) else session.touchUp(id)
            if (mine) Took.CHANGED else Took.NOTHING
        }
        else -> Took.NOTHING
    }

    /** No finger is down any more, or the touch layer is going away: nothing steers, and the ship stays put. */
    fun cancelAll() = session.touchCancelAll()
}
