package com.pawplay.app.games.pawblocks

import kotlin.math.abs
import kotlin.math.floor
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min

/**
 * Where things go on the Paw Blocks screen, as plain numbers in dp (docs/DESIGN-SYSTEM.md, "Board" and
 * "Tray"). The reference play area is 360 x 692dp: home 56dp at (20, 20); board panel 328dp at left 16, top 96
 * (grid 320dp inside a 4dp padding); tray slots 104 x 112dp at x 16, 128, 240 and top 508. On other screens the
 * panel narrows to fit the width and the tray, gaps and margins give way before anything gets small; the
 * slots never drop below 72dp and everything is on screen, no scrolling.
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
     * block of the stage's set sit inside a slot with 6dp all round (46 at stage 1, 30, 23, 18 on the reference screen).
     */
    fun trayCell(stage: Int): Float {
        val set = BlocksRamp.shapesFor(stage)
        val byWidth = floor((slotWidth - 2 * SLOT_INNER) / set.maxOf { it.width })
        val byHeight = floor((slotHeight - 2 * SLOT_INNER) / set.maxOf { it.height })
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
        const val SLOT_GAP = 8f
        const val SLOT_INNER = 6f
        const val REFERENCE_PANEL = 328f
        const val SIDE_MARGIN = 16f
        const val REFERENCE_TOP = 96f
        const val REFERENCE_SLOT_HEIGHT = 112f
        const val REFERENCE_SLOT_WIDTH = 104f
        const val REFERENCE_BOTTOM_MARGIN = 72f
        const val MIN_SLOT = 72f
    }
}

/**
 * Lays the screen out for a play area [width] x [height] dp (inside any system bars). On the reference
 * 360 x 692 screen this is exactly the design: panel (16, 96, 328), slots at y 508. Wider screens keep the
 * design's sizes and centre; narrower ones (320dp) shrink the panel to width - 32 and the slots to a third of it.
 * Short screens give up bottom margin first, then slot height (down to 72), then the gap, then the top
 * margin, and only then the panel. Tall screens leave the tray near the bottom and put the extra room
 * partly above the board.
 */
fun blocksLayout(width: Float, height: Float): BlocksLayout {
    val w = max(width, 1f)
    val h = max(height, 1f)
    var panel = max(min(BlocksLayout.REFERENCE_PANEL, w - 2 * BlocksLayout.SIDE_MARGIN), 120f)
    val slotW = min(BlocksLayout.REFERENCE_SLOT_WIDTH, (panel - 2 * BlocksLayout.SLOT_GAP) / 3f)

    var top = BlocksLayout.REFERENCE_TOP
    var bottom = BlocksLayout.REFERENCE_BOTTOM_MARGIN
    var slotH = BlocksLayout.REFERENCE_SLOT_HEIGHT
    var gapMin = 24f
    var deficit = top + panel + gapMin + slotH + bottom - h

    fun give(room: Float): Float { val t = min(max(deficit, 0f), room); deficit -= t; return t }
    bottom -= give(bottom - 16f)
    slotH -= give(slotH - BlocksLayout.MIN_SLOT)
    gapMin -= give(gapMin - 12f)
    top -= give(top - 88f)
    if (deficit > 0f) panel = max(panel - deficit, 120f)

    val slotTop = h - bottom - slotH
    val spare = slotTop - (top + panel)
    val panelTop = if (spare > 84f) top + (spare - 84f) * 0.3f else top
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
