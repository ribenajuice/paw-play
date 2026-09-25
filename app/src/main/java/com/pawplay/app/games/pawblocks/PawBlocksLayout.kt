package com.pawplay.app.games.pawblocks

import kotlin.math.abs
import kotlin.math.floor
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min

/**
 * Where things go on the Paw Blocks screen, as plain numbers in dp (docs/DESIGN-SYSTEM.md, "Board" and
 * "Tray"). The reference play area is 360 x 692dp: home 56dp at (20, 20); board panel 348dp at left 6, top 96
 * (grid 340dp inside a 4dp padding, so a 9x9 cell is 37.8dp); tray slots 96 x 112dp with 12dp gaps, which
 * puts their outer edges 24dp in from the screen sides (clear of the back-gesture strip), top at 508. On other
 * screens the panel follows the width (never wider than 348, always 6dp in from the sides); on a 320dp phone a
 * 9x9 cell is 33.3dp, which is about the most 320dp can give nine cells. The tray, gaps and margins give way
 * before the board does, the slots never drop below 72dp, and everything is on screen, no scrolling.
 */
class BlocksLayout(
    val width: Float,
    val height: Float,
    val panelLeft: Float,
    val panelTop: Float,
    val panelSize: Float,
    val slotLeft: Float,
    val slotTop: Float,
    val slotWidth: Float,
    val slotHeight: Float,
) {
    val gridLeft: Float get() = panelLeft + PANEL_PADDING
    val gridTop: Float get() = panelTop + PANEL_PADDING
    val gridSize: Float get() = panelSize - 2 * PANEL_PADDING
    val panelRight: Float get() = panelLeft + panelSize
    val panelBottom: Float get() = panelTop + panelSize

    fun slotLeftOf(i: Int): Float = slotLeft + i * (slotWidth + SLOT_GAP)

    /** Which tray slot the point is in (the whole slot is the grab area), or -1. */
    fun slotAt(x: Float, y: Float): Int {
        if (y < slotTop || y > slotTop + slotHeight) return -1
        for (i in 0 until BlocksRamp.TRAY_SIZE) {
            val left = slotLeftOf(i)
            if (x >= left && x <= left + slotWidth) return i
        }
        return -1
    }

    fun overHome(x: Float, y: Float): Boolean =
        x >= HOME_INSET && x <= HOME_INSET + HOME_SIZE && y >= HOME_INSET && y <= HOME_INSET + HOME_SIZE

    /** Cell size of the board when it is [boardSize] cells across (320 / N on the reference screen). */
    fun cellSize(boardSize: Float): Float = gridSize / boardSize

    /**
     * The tray cell size for blocks dealt at [stage]: the board cell, or whatever lets the widest and tallest
     * block of the stage's set sit inside a slot with 4dp all round (44 at stage 1, then 29, 29, 22, 22, 17 on the reference screen).
     */
    fun trayCell(stage: Int): Float {
        val byWidth = floor((slotWidth - 2 * SLOT_INNER) / BlocksRamp.widestFor(stage))
        val byHeight = floor((slotHeight - 2 * SLOT_INNER) / BlocksRamp.tallestFor(stage))
        return min(cellSize(BlocksRamp.boardSizeFor(stage).toFloat()), min(byWidth, byHeight))
    }

    /**
     * Where a dropped block goes: the legal spot within reach of where the block is drawn, or null (an illegal
     * drop) if the finger is off the screen, over the home button, or no legal spot is close enough.
     * The ghost uses the same call, so what it shows is what a drop does.
     */
    fun dropSpot(board: Board, shape: BlockShape, cell: Float, fingerX: Float, fingerY: Float): Spot? {
        if (!fingerX.isFinite() || !fingerY.isFinite()) return null
        if (fingerX < 0f || fingerY < 0f || fingerX > width || fingerY > height) return null
        if (overHome(fingerX, fingerY)) return null
        return Snap.spot(board, shape, cell, gridLeft, gridTop, fingerX, fingerY)
    }

    companion object {
        const val HOME_SIZE = 56f
        const val HOME_INSET = 20f
        const val PANEL_PADDING = 4f
        const val SLOT_GAP = 12f
        const val SLOT_INNER = 4f
        const val REFERENCE_PANEL = 348f      // the widest the board gets, however wide the phone
        const val SIDE_MARGIN = 6f            // panel edge to screen edge: the board is never touched, so it may go near the edge
        const val TRAY_SIDE_MARGIN = 24f      // tray edge to screen edge: keeps the outer slots off the back-gesture strip
        const val REFERENCE_TOP = 96f
        const val MIN_TOP = 84f
        const val REFERENCE_SLOT_HEIGHT = 112f
        const val REFERENCE_SLOT_WIDTH = 96f
        const val REFERENCE_BOTTOM_MARGIN = 72f
        const val MIN_BOTTOM_MARGIN = 12f
        const val MIN_GAP = 12f               // board to tray
        const val REFERENCE_GAP = 24f
        const val MIN_SLOT = 72f
    }
}

/**
 * Lays the screen out for a play area [width] x [height] dp (inside any system bars). On the reference
 * 360 x 692 screen: panel (6, 96, 348), slots 96 x 112 at y 508. Wider screens keep those sizes and centre; narrower
 * ones shrink the panel to width - 12 and the slots to fit 24dp in from each side (never under 72dp).
 * Short screens give up, in this order, the tray's bottom margin (72 to 12), slot height (112 to 72), the gap
 * above the tray (24 to 12) and the top margin (96 to 84), and only then shrink the panel. Tall screens leave the
 * tray near the bottom and put a little of the extra room above the board.
 */
fun blocksLayout(width: Float, height: Float): BlocksLayout {
    val w = max(width, 1f)
    val h = max(height, 1f)
    var panel = max(min(BlocksLayout.REFERENCE_PANEL, w - 2 * BlocksLayout.SIDE_MARGIN), 120f)
    val slotW = max(
        min(BlocksLayout.REFERENCE_SLOT_WIDTH, (w - 2 * BlocksLayout.TRAY_SIDE_MARGIN - 2 * BlocksLayout.SLOT_GAP) / 3f),
        BlocksLayout.MIN_SLOT,
    )

    var top = BlocksLayout.REFERENCE_TOP
    var bottom = BlocksLayout.REFERENCE_BOTTOM_MARGIN
    var slotH = BlocksLayout.REFERENCE_SLOT_HEIGHT
    var gapMin = BlocksLayout.REFERENCE_GAP
    var deficit = top + panel + gapMin + slotH + bottom - h

    fun give(room: Float): Float { val t = min(max(deficit, 0f), room); deficit -= t; return t }
    bottom -= give(bottom - BlocksLayout.MIN_BOTTOM_MARGIN)
    slotH -= give(slotH - BlocksLayout.MIN_SLOT)
    gapMin -= give(gapMin - BlocksLayout.MIN_GAP)
    top -= give(top - BlocksLayout.MIN_TOP)
    if (deficit > 0f) panel = max(panel - deficit, 120f)

    val slotTop = h - bottom - slotH
    val spare = slotTop - (top + panel)
    val panelTop = if (spare > 64f) top + (spare - 64f) * 0.3f else top
    return BlocksLayout(
        width = w, height = h,
        panelLeft = (w - panel) / 2f, panelTop = panelTop, panelSize = panel,
        slotLeft = (w - (3 * slotW + 2 * BlocksLayout.SLOT_GAP)) / 2f, slotTop = slotTop,
        slotWidth = slotW, slotHeight = slotH,
    )
}

/**
 * Snap and ghost maths (docs/PRD.md story 41). All in dp, the grid's top-left at [gridLeft], [gridTop].
 *
 * The block rides 64dp above the fingertip, measured from its bottom edge, centred on the finger. It snaps to
 * the legal spot nearest to where it is drawn (top-left to top-left) if that is within max(one board cell, 40dp);
 * the 40dp floor keeps forgiveness a fingertip's size when cells shrink on big boards. Two spots equally close
 * (within half a dp) go to the one nearer the fingertip.
 */
object Snap {
    const val RIDE_DP = 64f
    const val MIN_REACH_DP = 40f
    private const val TIE_DP = 0.5f

    fun reach(cell: Float): Float = max(cell, MIN_REACH_DP)

    /** Top-left of the block's bounding box when drawn at [cell] size and carried by a finger at [fingerX], [fingerY]. */
    fun blockLeft(shape: BlockShape, cell: Float, fingerX: Float): Float = fingerX - shape.width * cell / 2f
    fun blockTop(shape: BlockShape, cell: Float, fingerY: Float): Float = fingerY - RIDE_DP - shape.height * cell

    fun spot(board: Board, shape: BlockShape, cell: Float, gridLeft: Float, gridTop: Float, fingerX: Float, fingerY: Float): Spot? {
        val px = blockLeft(shape, cell, fingerX)
        val py = blockTop(shape, cell, fingerY)
        val reach = reach(cell)
        var best: Spot? = null
        var bestD = 0f
        var bestFinger = 0f
        for (r in 0..board.size - shape.height) for (c in 0..board.size - shape.width) {
            if (!board.canPlace(shape, r, c)) continue
            val x = gridLeft + c * cell
            val y = gridTop + r * cell
            val d = hypot(x - px, y - py)
            if (d > reach) continue
            val df = hypot(x + shape.width * cell / 2f - fingerX, y + shape.height * cell - fingerY)
            if (best == null || d < bestD - TIE_DP || (abs(d - bestD) <= TIE_DP && df < bestFinger)) {
                best = Spot(r, c); bestD = d; bestFinger = df
            }
        }
        return best
    }
}

/**
 * Which finger is dragging which tray slot. Only the first finger that grabs a block is followed; every other
 * finger does nothing until it lifts (docs/PRD.md story 49). A finger that lands on empty space, or on a slot
 * with nothing to lift, never holds the job, so a resting palm cannot block play. Pure and unit-tested; the
 * screen feeds it pointer events.
 */
class DragTracker {
    var pointerId: Long? = null
        private set
    var slot: Int = -1
        private set
    var x: Float = 0f
        private set
    var y: Float = 0f
        private set

    val active: Boolean get() = pointerId != null

    /** A finger went down at [px], [py] on [slotHit] (or -1), which [hasBlock] says can be lifted. True if this finger now drags. */
    fun down(id: Long, px: Float, py: Float, slotHit: Int, hasBlock: Boolean): Boolean {
        if (active || slotHit < 0 || !hasBlock) return false
        pointerId = id; slot = slotHit; x = px; y = py
        return true
    }

    /** True if [id] is the dragging finger (and it has been followed to the new position). */
    fun move(id: Long, px: Float, py: Float): Boolean {
        if (id != pointerId) return false
        x = px; y = py
        return true
    }

    /** The dragging finger lifted at [px], [py]: returns the drag that ended (slot and last position), else null. */
    fun up(id: Long, px: Float, py: Float): Ended? {
        if (id != pointerId) return null
        x = px; y = py
        return end()
    }

    /** The drag is over for any other reason (cancelled, screen left, a lift was missed). */
    fun cancel(): Ended? = if (active) end() else null

    private fun end(): Ended {
        val ended = Ended(slot, x, y)
        pointerId = null; slot = -1
        return ended
    }

    class Ended(val slot: Int, val x: Float, val y: Float)
}
