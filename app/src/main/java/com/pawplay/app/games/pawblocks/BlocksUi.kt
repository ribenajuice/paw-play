package com.pawplay.app.games.pawblocks

import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import kotlin.math.max
import kotlin.math.min
import kotlin.random.Random

// ------------------------------------------------------------------ the moving pictures
// Every effect is plain data with a start time on the screen's frame clock; the drawing works out where it is
// from (now - start). None of it changes the game: the session already holds the truth when an effect starts.

/** A block gliding into its cells (90ms), whose cells then swell once (the "plop", 160ms). */
internal class DropFx(val start: Long, val shape: BlockShape, val row: Int, val col: Int, val x0: Float, val y0: Float, val s0: Float) {
    val end get() = start + BlocksTiming.DROP_MS + BlocksTiming.PLOP_MS
    fun covers(r: Int, c: Int) = shape.cells.any { row + it.row == r && col + it.col == c }
}

/** A block that was let go where it cannot land, gliding back to its slot (250ms). */
internal class ReturnFx(val slot: Int, val start: Long, val x0: Float, val y0: Float, val s0: Float) {
    val end get() = start + BlocksTiming.RETURN_MS
}

/** A cell that is leaving the board, and how far along the line it sits (0 to 1) for the sweep's timing. */
internal class FxCell(val row: Int, val col: Int, val family: Family, val u: Float)

internal class Spark(val x: Float, val y: Float, val r: Float, val delay: Float)

/** A completed line's shimmer, shrinking cells and sparkles, and the animal peeking up. */
internal class LineFx(val start: Long, val cells: List<FxCell>, val rows: List<Int>, val cols: List<Int>, val sparks: List<Spark>, val critter: Int) {
    val end get() = start + BlocksTiming.LINE_CLEAR_MS
}

/** The quieter clear-out: a white wash on the rows, cells fading, a few pale sparkles. No animal. */
internal class OutFx(val start: Long, val cells: List<FxCell>, val rows: List<Int>, val sparks: List<Spark>) {
    val end get() = start + BlocksTiming.CLEAR_OUT_MS
}

internal class GrowFx(val start: Long, val from: Int, val to: Int) {
    val end get() = start + BlocksTiming.GROWTH_MS
}

/** The paw starts to fade 200ms into the clear-out's 900ms. */
internal const val PAW_FADE_DELAY_MS = 200L

internal fun easeOut(k: Float): Float = 1f - (1f - k.coerceIn(0f, 1f)).let { it * it * it }
internal fun easeInOut(k: Float): Float {
    val t = k.coerceIn(0f, 1f)
    return if (t < 0.5f) 2f * t * t else 1f - (-2f * t + 2f) * (-2f * t + 2f) / 2f
}

/**
 * Everything the screen holds besides the [BlocksSession]: which finger drags which slot, and the effects that
 * are playing. Plain Kotlin apart from two Compose state holders that tell the drawing to run again.
 * The pointer functions take positions in dp relative to the play area.
 */
internal class BlocksUi(
    val session: BlocksSession,
    seed: Int = 11,
    /** Told the new score after every legal placement; the screen saves the best from it the moment it is beaten (story 70). */
    private val onScore: (Int) -> Unit = {},
) {
    val tracker = DragTracker()
    var layout: BlocksLayout = blocksLayout(360f, 692f)

    val drops = ArrayList<DropFx>()
    val returns = ArrayList<ReturnFx>()
    val lines = ArrayList<LineFx>()
    val outs = ArrayList<OutFx>()
    var grow: GrowFx? = null
    var trayInStart: Long = Long.MIN_VALUE
    var liftStart: Long = 0L

    /** The paw that was spent by the latest clear-out (its index, 0 to 2) and when its 0.6s fade begins; -1 until the first clear-out. */
    var pawFadeIndex: Int = -1
        private set
    var pawFadeStart: Long = 0L
        private set

    /** The screen's frame time; the drawing reads this so it redraws every frame while something moves. */
    val frame = mutableLongStateOf(0L)
    /** Bumped when a touch changed something between frames. */
    val revision = mutableIntStateOf(0)

    private val random = Random(seed)
    private var lastFrameMs = 0L
    private var lastFrameWall = 0L

    /** "Now" for a touch: the last frame's time plus the real time since, so a touch after a quiet spell is not stale. */
    fun nowMs(): Long = if (lastFrameWall == 0L) lastFrameMs else lastFrameMs + (System.nanoTime() - lastFrameWall) / 1_000_000L

    /** True while anything needs another frame: a finger is down, an effect is playing, or the game has a follow-up due. */
    val active: Boolean
        get() = tracker.active || drops.isNotEmpty() || returns.isNotEmpty() || lines.isNotEmpty() || outs.isNotEmpty() ||
            grow != null || trayInStart != Long.MIN_VALUE || session.hasPending

    /** The board's size as drawn: a fraction while it eases from N to N+1 (cells share the room, nothing jumps). */
    fun displaySize(now: Long): Float {
        val g = grow ?: return session.board.size.toFloat()
        val e = easeInOut((now - g.start).toFloat() / BlocksTiming.GROWTH_MS)
        return g.to - (1f - e)
    }

    fun displayCell(now: Long): Float = layout.cellSize(displaySize(now))

    // ------------------------------------------------------------ frames

    fun onFrame(now: Long) {
        lastFrameMs = now
        lastFrameWall = System.nanoTime()
        for (event in session.tick(now)) {
            when (event) {
                is SessionEvent.Grew -> grow = GrowFx(now, event.size - 1, event.size)
                SessionEvent.Refilled -> trayInStart = now
                is SessionEvent.ClearedOut -> {
                    outs += clearOutFx(now, event)
                    // The paw fades from the same instant as the wash (t = 200ms of the 900ms), so the frames that
                    // the clear-out effect keeps running cover the whole 0.6s.
                    pawFadeIndex = event.pawsLeft
                    pawFadeStart = now + PAW_FADE_DELAY_MS
                }
                is SessionEvent.GameEnded -> Unit // the screen reads session.phase and fades in the good-game screen
            }
        }
        drops.removeAll { now >= it.end }
        returns.removeAll { now >= it.end }
        lines.removeAll { now >= it.end }
        outs.removeAll { now >= it.end }
        if (grow?.let { now >= it.end } == true) grow = null
        if (trayInStart != Long.MIN_VALUE && now >= trayInStart + BlocksTiming.REFILL_SLIDE_MS) trayInStart = Long.MIN_VALUE
        frame.longValue = now
    }

    // ------------------------------------------------------------ touches (dp, relative to the play area)

    /** A finger went down. True if it grabbed a tray block (only then does the game follow it). */
    fun onDown(id: Long, x: Float, y: Float): Boolean {
        if (session.phase != BlocksPhase.PLAYING) return false // the game has ended kindly: nothing more to grab
        val slot = layout.slotAt(x, y)
        val hasBlock = slot >= 0 && session.slot(slot) != null
        if (!tracker.down(id, x, y, slot, hasBlock)) return false
        val now = nowMs()
        returns.removeAll { it.slot == slot } // grabbed again mid-glide: it is in the hand at once
        liftStart = now
        session.setHolding(true, now)
        revision.intValue++
        return true
    }

    fun onMove(id: Long, x: Float, y: Float): Boolean = tracker.move(id, x, y)

    fun onUp(id: Long, x: Float, y: Float): Boolean {
        val ended = tracker.up(id, x, y) ?: return false
        finish(ended, drop = true)
        return true
    }

    /** What [onPointer] did with a pointer change. */
    enum class Took { NOTHING, FOLLOWED, CHANGED }

    /**
     * One pointer change from the touch layer. [pressed] and [previousPressed] say whether it went down, moved or
     * came up. A change that is already [consumed] when it arrives as an "up" is how Compose reports a cancel (a system
     * gesture, a notification pull-down, an app switch): it is NOT a lift, so the block goes home instead of landing.
     * [Took.CHANGED] means a drag started or ended (worth waking the frame loop); a plain move is only [Took.FOLLOWED].
     */
    fun onPointer(id: Long, x: Float, y: Float, pressed: Boolean, previousPressed: Boolean, consumed: Boolean): Took = when {
        pressed && !previousPressed -> if (onDown(id, x, y)) Took.CHANGED else Took.NOTHING
        pressed -> if (onMove(id, x, y)) Took.FOLLOWED else Took.NOTHING
        previousPressed -> {
            if (consumed) { if (cancelPointer(id)) Took.CHANGED else Took.NOTHING }
            else if (onUp(id, x, y)) Took.CHANGED else Took.NOTHING
        }
        else -> Took.NOTHING
    }

    /** A cancel for pointer [id]: only the dragging finger's cancel matters. */
    private fun cancelPointer(id: Long): Boolean = if (tracker.pointerId == id) cancelDrag() else false

    /** The drag is interrupted (cancelled touch, screen left, a lift went missing): the block goes home. */
    fun cancelDrag(): Boolean {
        val ended = tracker.cancel() ?: return false
        finish(ended, drop = false)
        return true
    }

    private fun finish(ended: DragTracker.Ended, drop: Boolean) {
        val now = nowMs()
        val shape = session.slot(ended.slot)
        session.setHolding(false, now)
        revision.intValue++
        if (shape == null) return
        val cell = displayCell(now)
        val pose = dragPose(shape, ended.slot, ended.x, ended.y, now)
        // A tap (the finger never really moved) is not a drop: the block goes home.
        val spot = if (drop && !ended.wasTap) layout.dropSpot(session.board, shape, cell, ended.x, ended.y) else null
        val placed = spot?.let { session.place(ended.slot, it.row, it.col, now) }
        if (placed == null) {
            returns += ReturnFx(ended.slot, now, pose.x, pose.y, pose.s)
            return
        }
        onScore(session.score)
        drops += DropFx(now, shape, placed.row, placed.col, pose.x, pose.y, pose.s)
        if (placed.cleared) lines += lineFx(now + BlocksTiming.DROP_MS, placed, session.board.size, cell)
    }

    /** A fading cell of a cleared line or clear-out is drawn only while its place is still empty: a block dropped there since is not covered. */
    fun fadingCellVisible(cell: FxCell): Boolean = session.board.isEmptyAt(cell.row, cell.col)

    class Pose(val x: Float, val y: Float, val s: Float)

    /** Where a carried block is drawn: [Snap.RIDE_DP] above the fingertip from its bottom edge, growing to board size in 120ms. */
    fun dragPose(shape: BlockShape, slot: Int, fingerX: Float, fingerY: Float, now: Long): Pose {
        val tray = layout.trayCell(session.trayStage)
        val s = tray + (displayCell(now) - tray) * easeOut((now - liftStart).toFloat() / BlocksTiming.LIFT_MS)
        return Pose(fingerX - shape.width * s / 2f, fingerY - Snap.RIDE_DP - shape.height * s, s)
    }

    // ------------------------------------------------------------ effects

    private fun lineFx(start: Long, placed: Placed, boardSize: Int, cell: Float): LineFx {
        val denom = max(1, boardSize - 1).toFloat()
        val cells = placed.removed.map { c ->
            val u = if (c.row in placed.rows) c.col / denom else c.row / denom
            FxCell(c.row, c.col, c.family, u)
        }
        val n = min(12, 4 + cells.size)
        val sparks = List(n) {
            val c = cells[random.nextInt(cells.size)]
            Spark(
                x = layout.gridLeft + (c.col + 0.5f + (random.nextFloat() - 0.5f) * 0.9f) * cell,
                y = layout.gridTop + (c.row + 0.5f + (random.nextFloat() - 0.5f) * 0.9f) * cell,
                r = 8f + random.nextFloat() * 7f,
                delay = c.u * 450f + random.nextFloat() * 80f,
            )
        }
        return LineFx(start, cells, placed.rows, placed.cols, sparks, placed.critter ?: 0)
    }

    private fun clearOutFx(now: Long, e: SessionEvent.ClearedOut): OutFx {
        val cell = layout.cellSize(session.board.size.toFloat())
        val cells = e.removed.map { FxCell(it.row, it.col, it.family, 0f) }
        val n = if (e.rows.isEmpty()) 0 else min(8, 4 + cells.size)
        val sparks = List(n) {
            val row = e.rows[random.nextInt(e.rows.size)]
            Spark(
                x = layout.gridLeft + random.nextFloat() * layout.gridSize,
                y = layout.gridTop + (row + 0.5f + (random.nextFloat() - 0.5f)) * cell,
                r = 5f + random.nextFloat() * 3f,
                delay = random.nextFloat() * 250f,
            )
        }
        return OutFx(now, cells, e.rows, sparks)
    }
}
