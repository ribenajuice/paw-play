package com.pawplay.app.games.pawpop

import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.tan
import kotlin.random.Random

/**
 * A bubble or balloon on its way down (stories 66, 67). Everything but the position and the path state is fixed when it
 * appears. It moves in straight legs: between turns its velocity is constant, and at the end of each leg it turns
 * sharply, alternating left and right of straight down. It is always heading down.
 */
class PopTarget(
    val kind: TargetKind,
    /** Index into [PopPalette]. */
    val colour: Int,
    /** Which animal, for a critter bubble (0 fox, 1 bear, 2 bunny, 3 frog). */
    val critter: Int,
    /** Nominal width in dp (56 to 96). */
    val size: Float,
    /** The gift inside, if this is a carrier. */
    val gift: GiftKind?,
    var x: Float,
    var y: Float,
) {
    /** 1 until the last tenth of the screen, then fading to 0 as its centre reaches the bottom (and, at the very end of a game, fading out). */
    var alpha: Float = 1f
    val isCarrier: Boolean get() = gift != null

    /** Downward speed in screen heights a second (before slow drift), picked when it appears and re-picked at turns from stage 3. */
    var speed: Float = 0f
    /** 0 on the entry leg (straight down), then -1 (left of down) or +1 (right), alternating. */
    var side: Int = 0
    /** Seconds of straight run left in this leg. It counts down only once the target has entered. */
    var legLeft: Float = 0f
    /** Seconds since the last turn (starts high: there was none). */
    var sinceTurn: Float = PopMetrics.MIN_TURN_GAP
    /** The angle of the current leg from straight down: its tangent and cosine, fixed at the turn so the leg is exactly straight. */
    var legTan: Float = 0f
    var legCos: Float = 1f
    /** Latched true once the whole picture is below the entry line. Only an entered target can be popped. */
    var entered: Boolean = false
    var sinceEntered: Float = 0f
    /** Latched true once it began to fade at the bottom: it can no longer be popped, and it costs a paw at most once. */
    var missed: Boolean = false
    /** How many turns it has made (tests read it). */
    var turns: Int = 0
    /** Velocity in dp a second as of the last step (tests and drawing read it). */
    var vx: Float = 0f
    var vy: Float = 0f
}

/**
 * The zig-zag path of a target (docs/PRD.md story 66-67, docs/DECISIONS.md "Paw Pop: zig-zag legs"). Pure Kotlin.
 *
 * A target appears fully above the entry line ([PopMetrics.SKY_TOP]) heading straight down. Once its whole picture is
 * below that line it has *entered*; from then it counts down a run of straight travel, and at the end of the run it turns
 * instantly to a fixed angle from straight down (the stage's), on a random side first and then alternating. No two turns
 * are closer than 0.9s and none comes in the first 0.5s after entry. A target that would come within 8dp of a side edge
 * turns early instead (if the 0.9s gap allows; else it slides down the wall until it does). Its path speed never passes
 * 0.2 screen heights a second, and slow drift halves all of its movement, sideways too.
 */
internal object PopPath {
    private val DEG = (PI / 180.0).toFloat()

    private fun drawRun(st: PopStage, random: Random): Float = st.runMin + random.nextFloat() * (st.runMax - st.runMin)

    /** Starts [t]'s path: straight down at a speed drawn from the stage's range, with its first run drawn. */
    fun begin(t: PopTarget, st: PopStage, random: Random) {
        t.speed = st.driftMin + random.nextFloat() * (st.driftMax - st.driftMin)
        t.side = 0
        t.legTan = 0f
        t.legCos = 1f
        t.legLeft = drawRun(st, random)
        t.sinceTurn = PopMetrics.MIN_TURN_GAP
        t.entered = false
        t.sinceEntered = 0f
        t.missed = false
        t.turns = 0
    }

    /** True when the whole picture (its top edge) is below the entry line. */
    fun isBelowLine(t: PopTarget): Boolean = t.y - t.kind.top * t.size >= PopMetrics.SKY_TOP

    /** The speed at a turn: within 25% of [old], and inside the stage's range where the two allow it. */
    fun repick(old: Float, st: PopStage, random: Random): Float {
        val lo = max(st.driftMin, old * (1f - PopMetrics.REPICK_LIMIT))
        val hi = min(st.driftMax, old * (1f + PopMetrics.REPICK_LIMIT))
        return if (lo >= hi) old else lo + random.nextFloat() * (hi - lo)
    }

    private fun turn(t: PopTarget, st: PopStage, random: Random) {
        t.side = if (t.side == 0) (if (random.nextBoolean()) 1 else -1) else -t.side
        val a = st.turnDegrees * DEG
        t.legTan = tan(a)
        t.legCos = cos(a)
        t.legLeft = drawRun(st, random)
        t.sinceTurn = 0f
        t.turns++
        if (st.repickSpeed) t.speed = repick(t.speed, st, random)
    }

    /** Fills [PopTarget.vx] and [PopTarget.vy] for the current leg: down at the speed, capped so the path speed is at most 0.2 sh/s, sideways by the leg's angle. */
    private fun setVelocity(t: PopTarget, height: Float, slow: Float) {
        val down = min(t.speed, PopMetrics.MAX_PATH_SPEED * t.legCos) * height * slow
        t.vy = down
        t.vx = t.side * down * t.legTan
    }

    /**
     * Moves [t] by [dt] seconds (already clamped by the session). [slow] is 1, or 0.5 under slow drift. [width] and [height]
     * are the play area's.
     */
    fun advance(t: PopTarget, dt: Float, st: PopStage, width: Float, height: Float, slow: Float, random: Random) {
        t.sinceTurn += dt
        if (t.entered) {
            t.sinceEntered += dt
            t.legLeft -= dt
        }
        fun mayTurn() = t.entered && t.sinceEntered >= PopMetrics.NO_TURN_AFTER_ENTRY && t.sinceTurn >= PopMetrics.MIN_TURN_GAP
        if (t.legLeft <= 0f && mayTurn()) turn(t, st, random)
        setVelocity(t, height, slow)

        // Keep the body 8dp from the side edges: turn early if the gap since the last turn allows, else stay at the wall.
        val half = t.size / 2f + PopMetrics.EDGE_CLEAR
        val lo = half
        val hi = width - half
        var nx = t.x + t.vx * dt
        if (hi >= lo && (nx < lo || nx > hi) && t.side != 0 && mayTurn()) { // asked again: a turn just made this step has reset the gap
            turn(t, st, random)
            setVelocity(t, height, slow)
            nx = t.x + t.vx * dt
        }
        nx = if (hi >= lo) nx.coerceIn(lo, hi) else width / 2f
        t.x = nx
        t.y += t.vy * dt
        if (!t.entered && isBelowLine(t)) {
            t.entered = true
            t.sinceEntered = 0f
        }
    }
}
