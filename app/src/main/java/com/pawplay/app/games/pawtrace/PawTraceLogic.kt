package com.pawplay.app.games.pawtrace

import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

/**
 * The rules of Paw Trace, in plain Kotlin with no Android types, so they are unit-tested on a JVM.
 * The screen (PawTraceGame.kt) only turns touches into calls on [TraceSession] and draws what
 * [TracePaint] says. Everything here is in glyph units (the 100 x 100 box); the few sizes that are
 * sized for a fingertip rather than for the picture are dp constants in [TraceMetrics] and are
 * converted with [TraceTuning].
 */

/** Sizes fixed in dp because they are sized for a fingertip, not for the picture (docs/DESIGN-SYSTEM.md). */
object TraceMetrics {
    const val PAINT_RADIUS_DP = 32.0   // paint counts within 32dp of a centreline: a 64dp corridor
    const val BAND_DP = 56.0           // the guide band and the paint core
    const val MARKER_AHEAD_DP = 48.0   // the frog stands this far along the stroke from the first gap
    const val HINT_WALK_DP = 134.0     // how far the idle hint walks the frog
    val CUE_DP = listOf(48.0, 83.0, 118.0) // paw prints ahead of the frog
    const val REFERENCE_BOX_DP = 320.0 // one glyph unit is 3.2dp at the reference box
}

/** Converts the dp measures above into glyph units for a box drawn at [unitDp] dp per glyph unit. */
class TraceTuning(val unitDp: Double) {
    init { require(unitDp > 0.0) { "unitDp must be positive" } }

    val paintRadius: Double = TraceMetrics.PAINT_RADIUS_DP / unitDp
    val markerAhead: Double = TraceMetrics.MARKER_AHEAD_DP / unitDp
    val hintWalk: Double = TraceMetrics.HINT_WALK_DP / unitDp
    val cueOffsets: List<Double> = TraceMetrics.CUE_DP.map { it / unitDp }

    companion object {
        val REFERENCE = TraceTuning(TraceMetrics.REFERENCE_BOX_DP / 100.0)
    }
}

/** A stroke's painted state: which samples a finger has painted, which are covered, and whether it is finished. */
class StrokeState(val stroke: SampledStroke) {
    /** Painted by a finger. Never changes when the stroke finishes and fills itself. */
    val touched = BooleanArray(stroke.count)

    /** [touched], plus everything filled in when the stroke finished. */
    val covered = BooleanArray(stroke.count)

    var done = false
        private set

    private var coveredCount = 0

    /** Share of the stroke's samples that are covered, 0..1. */
    val fraction: Double get() = coveredCount.toDouble() / stroke.count

    val anyCovered: Boolean get() = coveredCount > 0

    internal fun paint(from: Int, to: Int) {
        for (k in max(0, from)..min(stroke.last, to)) {
            touched[k] = true
            if (!covered[k]) { covered[k] = true; coveredCount++ }
        }
    }

    /** The stroke is at least [DONE_FRACTION] covered: the rest of it fills itself. */
    internal fun finish() {
        done = true
        for (k in covered.indices) if (!covered[k]) { covered[k] = true; coveredCount++ }
    }
}

/** A stroke is finished at this share of its length painted. */
const val DONE_FRACTION = 0.85

/** Each touch paints the nearest sample and this many either side (2 x 1.5 = 3 glyph units). */
const val STAMP_REACH = 2

/** Where the frog stands: sample [index] of stroke [stroke]. */
data class TraceMarker(val stroke: Int, val index: Int)

/** One of the paw prints ahead of the frog: where it sits and which way it points. */
data class TraceCue(val x: Double, val y: Double, val angleDegrees: Double)

/** The nearest point of a stroke to a spot: [index] is a fractional sample index, [distance] is in glyph units. */
data class NearestPoint(val index: Double, val distance: Double)

fun nearestOnStroke(stroke: SampledStroke, x: Double, y: Double): NearestPoint {
    var bestDist = Double.MAX_VALUE
    var bestIndex = 0.0
    for (i in 0 until stroke.last) {
        val ax = stroke.xs[i]
        val ay = stroke.ys[i]
        val dx = stroke.xs[i + 1] - ax
        val dy = stroke.ys[i + 1] - ay
        val len2 = dx * dx + dy * dy
        val t = if (len2 > 0.0) (((x - ax) * dx + (y - ay) * dy) / len2).coerceIn(0.0, 1.0) else 0.0
        val d = hypot(x - (ax + dx * t), y - (ay + dy * t))
        if (d < bestDist) { bestDist = d; bestIndex = i + t }
    }
    return NearestPoint(bestIndex, bestDist)
}

/**
 * The painting of one glyph. A touch paints only where it is within the paint corridor of a
 * stroke's centreline; anywhere else nothing at all happens (no penalty, no state change). Strokes
 * may be painted in any order and either direction. A stroke finishes at 85% covered and its
 * remaining gaps fill themselves; the glyph is complete when every stroke is.
 */
class TracePaint(val glyph: Glyph, var tuning: TraceTuning = TraceTuning.REFERENCE) {
    val strokes: List<StrokeState> = glyph.strokes.map { StrokeState(it) }

    /** The stroke the child is currently painting, while it is unfinished. */
    var current: Int? = null
        private set

    /** Bumped on every change to the painting, so a screen knows when to redraw. */
    var version = 0
        private set

    val isComplete: Boolean get() = strokes.all { it.done }

    /** Is this spot within the paint corridor of any stroke (finished or not)? */
    fun isOnPath(x: Double, y: Double): Boolean =
        strokes.any { nearestOnStroke(it.stroke, x, y).distance <= tuning.paintRadius }

    /**
     * A finger at ([x], [y]): paints the point of every unfinished stroke nearest to it, for each
     * stroke within the corridor. Returns the strokes that finished because of it. Off the path this
     * changes nothing.
     */
    fun stamp(x: Double, y: Double): List<Int> {
        var hitCurrent = false
        var nearestHit = -1
        var nearestHitDistance = Double.MAX_VALUE
        var changed = false
        strokes.forEachIndexed { si, s ->
            if (s.done) return@forEachIndexed
            val near = nearestOnStroke(s.stroke, x, y)
            if (near.distance <= tuning.paintRadius) {
                val k = near.index.roundToInt()
                s.paint(k - STAMP_REACH, k + STAMP_REACH)
                changed = true
                if (si == current) hitCurrent = true
                if (near.distance < nearestHitDistance) { nearestHitDistance = near.distance; nearestHit = si }
            }
        }
        if (nearestHit >= 0 && !hitCurrent) current = nearestHit

        val finished = ArrayList<Int>()
        strokes.forEachIndexed { si, s ->
            if (!s.done && s.fraction >= DONE_FRACTION) {
                s.finish()
                finished += si
                changed = true
            }
        }
        if (changed) version++
        return finished
    }

    /**
     * A finger sliding from ([x0], [y0]) to ([x1], [y1]): stamps along the way every 2 units, so a fast
     * swipe leaves no holes. Returns the strokes that finished.
     */
    fun stampSegment(x0: Double, y0: Double, x1: Double, y1: Double): List<Int> {
        val distance = hypot(x1 - x0, y1 - y0)
        val steps = max(1, Math.ceil(distance / 2.0).toInt())
        val finished = ArrayList<Int>()
        for (k in 1..steps) finished += stamp(x0 + (x1 - x0) * k / steps, y0 + (y1 - y0) * k / steps)
        return finished
    }

    /**
     * Where the frog stands: on the stroke being painted (else the first unfinished one in list order),
     * standing [TraceTuning.markerAhead] past the first unpainted spot, or exactly on the start point if
     * the start is unpainted. Null once the glyph is complete.
     */
    fun marker(): TraceMarker? {
        val si = current?.takeIf { !strokes[it].done } ?: strokes.indexOfFirst { !it.done }
        if (si < 0) return null
        val s = strokes[si]
        val firstGap = s.covered.indexOfFirst { !it }
        val index = if (!s.anyCovered || firstGap <= 0) {
            0
        } else {
            min(s.stroke.last, firstGap + (tuning.markerAhead / SAMPLE_STEP).roundToInt())
        }
        return TraceMarker(si, index)
    }

    /** The paw prints ahead of the frog (the direction cue). Ones that would fall past the stroke's end are left out. */
    fun cues(marker: TraceMarker): List<TraceCue> {
        val stroke = strokes[marker.stroke].stroke
        return tuning.cueOffsets.mapNotNull { offset ->
            val j = marker.index + (offset / SAMPLE_STEP).roundToInt()
            if (j > stroke.last) null else stroke.point(j).let { TraceCue(it.x, it.y, stroke.angleDegrees(j)) }
        }
    }
}

/**
 * Which finger paints. Only the first finger down paints; extra fingers and palms do nothing.
 * One kindness: if the first finger has never yet been on a path (a palm resting on the glass, say)
 * and a later finger lands on the path, the later finger takes over, so a resting palm can never make
 * the game feel broken. Once the painting finger has been on the path it keeps the job until it lifts.
 * When it lifts, the next finger to land (with nobody else painting) is the painter.
 */
class PrimaryPointer {
    var id: Long? = null
        private set
    private var seenOnPath = false

    val active: Boolean get() = id != null

    /** A finger landed. Returns true if it is now the painting finger. */
    fun down(pointer: Long, onPath: Boolean): Boolean {
        val current = id
        return when {
            current == null -> { id = pointer; seenOnPath = onPath; true }
            current == pointer -> true
            !seenOnPath && onPath -> { id = pointer; seenOnPath = true; true }
            else -> false
        }
    }

    /** A finger moved. Returns true if it is the painting finger. */
    fun move(pointer: Long, onPath: Boolean): Boolean {
        if (id != pointer) return false
        if (onPath) seenOnPath = true
        return true
    }

    /** A finger lifted or the touch was cancelled. Returns true if it was the painting finger. */
    fun up(pointer: Long): Boolean {
        if (id != pointer) return false
        id = null
        seenOnPath = false
        return true
    }

    fun reset() { id = null; seenOnPath = false }
}

enum class TracePhase { TRACING, CELEBRATING }

/**
 * One visit to Paw Trace: the ramp position, the glyph being painted, the phase, and which finger paints.
 * Session-only, nothing persisted; leaving the game drops it and re-entering starts again at the first glyph.
 */
class TraceSession(private val random: kotlin.random.Random = kotlin.random.Random.Default, tuning: TraceTuning = TraceTuning.REFERENCE) {
    var ramp: RampState = startRamp()
        private set
    var paint: TracePaint = TracePaint(ramp.current, tuning)
        private set
    var phase: TracePhase = TracePhase.TRACING
        private set

    /** Counts glyphs shown, so a screen can tell "the next glyph" from "the same glyph again". */
    var serial = 0
        private set

    private val primary = PrimaryPointer()
    private var lastX = 0.0
    private var lastY = 0.0

    val tuning: TraceTuning get() = paint.tuning

    /** How many fingers are down (for the idle-hint timer). Counts every finger, painting or not. */
    var fingersDown = 0
        private set

    fun updateTuning(newTuning: TraceTuning) { paint.tuning = newTuning }

    /** A finger landed at ([x], [y]) in glyph units. Returns the strokes it finished. */
    fun pointerDown(id: Long, x: Double, y: Double): List<Int> {
        fingersDown++
        if (phase != TracePhase.TRACING) return emptyList()
        val onPath = paint.isOnPath(x, y)
        if (!primary.down(id, onPath)) return emptyList()
        lastX = x; lastY = y
        return if (onPath) settle(paint.stamp(x, y)) else emptyList()
    }

    fun pointerMove(id: Long, x: Double, y: Double): List<Int> {
        if (phase != TracePhase.TRACING) return emptyList()
        if (!primary.move(id, paint.isOnPath(x, y))) return emptyList()
        val finished = paint.stampSegment(lastX, lastY, x, y)
        lastX = x; lastY = y
        return settle(finished)
    }

    fun pointerUp(id: Long) {
        fingersDown = max(0, fingersDown - 1)
        primary.up(id)
    }

    /** Clears every finger (the screen lost focus or was disposed mid-touch). */
    fun releaseAll() { fingersDown = 0; primary.reset() }

    private fun settle(finished: List<Int>): List<Int> {
        if (phase == TracePhase.TRACING && paint.isComplete) {
            phase = TracePhase.CELEBRATING
            primary.reset()
        }
        return finished
    }

    /**
     * The play-on button. Only does anything while celebrating, and only once: the first tap moves to the
     * next glyph and leaves the celebrating phase, so a mashed button advances exactly one glyph.
     * Returns true if it advanced.
     */
    fun playOn(): Boolean {
        if (phase != TracePhase.CELEBRATING) return false
        val tuning = paint.tuning
        ramp = ramp.completed(random)
        paint = TracePaint(ramp.current, tuning)
        phase = TracePhase.TRACING
        serial++
        primary.reset()
        // A finger that is still resting on the glass from the finished glyph does not paint the new one
        // until it lifts and lands again.
        return true
    }
}
