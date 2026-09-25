package com.pawplay.app.games.pawblocks

import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.drawscope.translate
import com.pawplay.app.ui.theme.PawCardWhite
import com.pawplay.app.ui.theme.PawSunshine
import kotlin.math.PI
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin

// Drawing of the whole play area (everything but the home button), in dp. See docs/DESIGN-SYSTEM.md,
// "Paw Blocks: board, blocks, marks and clears". Order, back to front: the animal (behind the board), the
// panel, empty cells, placed cells, cells that are leaving, the animal's paws, shimmer and sparkles, the tray,
// blocks in flight, and last the carried block with its ghost.

private const val PANEL_RADIUS = 20f
private const val PANEL_EDGE = 3f
private const val SLOT_EDGE = 3f
private const val SWEEP_LEAN = 0.18f
private const val SWEEP_WIDTH = 1.6f

internal fun DrawScope.drawScene(ui: BlocksUi, now: Long) {
    val session = ui.session
    val layout = ui.layout
    val board = session.board
    val sizeF = ui.displaySize(now)
    val cell = layout.gridSize / sizeF
    val gx = layout.gridLeft
    val gy = layout.gridTop
    val grow = ui.grow
    val growT = if (grow != null) easeInOut((now - grow.start).toFloat() / BlocksTiming.GROWTH_MS) else 1f

    fun hidden(r: Int, c: Int) = ui.drops.any { now < it.start + BlocksTiming.DROP_MS && it.covers(r, c) }
    fun plop(r: Int, c: Int): Float {
        for (d in ui.drops) {
            val k = (now - d.start - BlocksTiming.DROP_MS).toFloat() / BlocksTiming.PLOP_MS
            if (k in 0f..1f && d.covers(r, c)) return 1f + 0.08f * sin(PI.toFloat() * k)
        }
        return 1f
    }

    // The animal peeks up from behind the board's top-right corner, so the panel is drawn over it.
    val line = ui.lines.lastOrNull()
    val peek = line?.let { critterRise((now - it.start).toFloat() / BlocksTiming.CRITTER_MS) } ?: 0f
    if (line != null && peek > 0f) {
        val x = layout.panelRight - CRITTER_INSET
        val y = layout.panelTop - CRITTER_RISE + (1f - peek) * CRITTER_DROP
        translate(x, y) { drawHappyCritter(line.critter, CRITTER_SIZE) }
    }

    // Panel: white, a 3dp edge inside its bounds.
    drawRoundRect(PawCardWhite, Offset(layout.panelLeft, layout.panelTop), Size(layout.panelSize, layout.panelSize), CornerRadius(PANEL_RADIUS))
    drawRoundRect(
        BlocksPanelEdge, Offset(layout.panelLeft + PANEL_EDGE / 2, layout.panelTop + PANEL_EDGE / 2),
        Size(layout.panelSize - PANEL_EDGE, layout.panelSize - PANEL_EDGE), CornerRadius(PANEL_RADIUS - PANEL_EDGE / 2), style = Stroke(PANEL_EDGE),
    )

    // The grid, clipped to its own box so a row or column that is still growing in never spills over the panel.
    clipRect(gx, gy, gx + layout.gridSize, gy + layout.gridSize) {
        for (r in 0 until board.size) for (c in 0 until board.size) {
            val fresh = grow != null && (r == board.size - 1 || c == board.size - 1)
            drawEmptyCell(gx + c * cell, gy + r * cell, cell, alpha = if (fresh) min(1f, growT * 1.5f) else 1f)
        }
        for (r in 0 until board.size) for (c in 0 until board.size) {
            val family = board.familyAt(r, c) ?: continue
            if (hidden(r, c)) continue
            drawFilledCell(gx + c * cell, gy + r * cell, cell, family, scale = plop(r, c))
        }
        // Cells that are leaving: the line's, then the clear-out's.
        // A place that has been filled again since (a block dropped into a line that is still fading) is not covered.
        for (fx in ui.lines) for (fc in fx.cells) {
            if (!ui.fadingCellVisible(fc) || hidden(fc.row, fc.col)) continue
            val t = now - fx.start - fc.u * SWEEP_CELL_SPREAD_MS
            val (s, o, white) = lineCellLook(t) ?: continue
            drawFilledCell(gx + fc.col * cell, gy + fc.row * cell, cell, fc.family, scale = s, alpha = o, white = white)
        }
        for (fx in ui.outs) for (fc in fx.cells) {
            if (!ui.fadingCellVisible(fc)) continue
            val (s, o, white) = outCellLook((now - fx.start).toFloat()) ?: continue
            drawFilledCell(gx + fc.col * cell, gy + fc.row * cell, cell, fc.family, scale = s, alpha = o, white = white)
        }
    }

    // The animal's paws rest on the panel's edge once it is halfway up.
    if (line != null && peek >= 0.5f) {
        val paw = critterPawColor(line.critter)
        for (px in floatArrayOf(layout.panelRight - PAW_INSET_A, layout.panelRight - PAW_INSET_B)) {
            drawOval(paw, Offset(px - 10f, layout.panelTop + 2f - 7f), Size(20f, 14f))
            drawOval(Color(0x38_2B2320), Offset(px - 10f, layout.panelTop + 2f - 7f), Size(20f, 14f), style = Stroke(1.4f))
        }
    }

    // Shimmer sweeps, clear-out washes and sparkles.
    for (fx in ui.lines) {
        val p = (now - fx.start).toFloat() / BlocksTiming.SWEEP_MS
        if (p in 0f..1f) {
            val pos = -0.2f + 1.4f * p
            for (r in fx.rows) shimmerRow(layout, cell, r, pos)
            for (c in fx.cols) shimmerCol(layout, cell, c, pos)
        }
        for (s in fx.sparks) {
            val k = (now - fx.start - s.delay) / SPARKLE_MS
            if (k > 0f && k < 1f) drawTwinkle(s.x, s.y, s.r * sin(PI.toFloat() * k), PawSunshine, 0.95f)
        }
    }
    for (fx in ui.outs) {
        val tt = (now - fx.start).toFloat()
        val a = 0.5f * min(1f, tt / 200f) * (1f - max(0f, (tt - 500f) / 400f))
        if (a > 0f) for (r in fx.rows) {
            drawRoundRect(Color.White, Offset(gx, gy + r * cell), Size(layout.gridSize, cell), CornerRadius(8f), alpha = a.coerceIn(0f, 1f))
        }
        for (s in fx.sparks) {
            val k = (tt - s.delay) / SPARKLE_MS
            if (k > 0f && k < 1f) drawTwinkle(s.x, s.y, s.r * sin(PI.toFloat() * k), BlocksPaleSparkle, 0.95f)
        }
    }

    drawTray(ui, now)

    // Blocks in flight: a legal drop gliding into its cells, an illegal one gliding home.
    for (d in ui.drops) {
        val k = (now - d.start).toFloat() / BlocksTiming.DROP_MS
        if (k in 0f..1f) {
            val e = easeOut(k)
            val tx = gx + d.col * cell
            val ty = gy + d.row * cell
            drawBlock(d.shape, d.x0 + (tx - d.x0) * e, d.y0 + (ty - d.y0) * e, d.s0 + (cell - d.s0) * e)
        }
    }
    val trayCell = layout.trayCell(session.trayStage)
    for (rt in ui.returns) {
        val shape = session.slot(rt.slot) ?: continue
        val e = easeOut((now - rt.start).toFloat() / BlocksTiming.RETURN_MS)
        val s = rt.s0 + (trayCell - rt.s0) * e
        val restX = layout.slotLeftOf(rt.slot) + (layout.slotWidth - shape.width * trayCell) / 2f
        val restY = layout.slotTop + (layout.slotHeight - shape.height * trayCell) / 2f
        drawBlock(shape, rt.x0 + (restX - rt.x0) * e, rt.y0 + (restY - rt.y0) * e, s)
    }

    // The carried block: ghost where it would land, shadow, block.
    val tracker = ui.tracker
    if (tracker.active) {
        val shape = session.slot(tracker.slot)
        if (shape != null) {
            val spot = layout.dropSpot(board, shape, cell, tracker.x, tracker.y)
            if (spot != null) drawGhost(shape, gx + spot.col * cell, gy + spot.row * cell, cell, big = board.size >= BlocksRamp.NEAR_COMPLETE_MIN_BOARD)
            val pose = ui.dragPose(shape, tracker.slot, tracker.x, tracker.y, now)
            drawBlockShadow(shape, pose.x, pose.y, pose.s)
            drawBlock(shape, pose.x, pose.y, pose.s)
        }
    }
}

// ------------------------------------------------------------------ tray

private fun DrawScope.drawTray(ui: BlocksUi, now: Long) {
    val layout = ui.layout
    val session = ui.session
    val trayCell = layout.trayCell(session.trayStage)
    val slide = if (ui.trayInStart == Long.MIN_VALUE) 1f else easeOut((now - ui.trayInStart).toFloat() / BlocksTiming.REFILL_SLIDE_MS)
    for (i in 0 until BlocksRamp.TRAY_SIZE) {
        val shape = session.slot(i)
        val lifted = shape != null && ((ui.tracker.active && ui.tracker.slot == i) || ui.returns.any { it.slot == i })
        val resting = shape != null && !lifted
        val left = layout.slotLeftOf(i)
        val top = layout.slotTop
        val dy = if (resting) (1f - slide) * 28f else 0f
        val alpha = if (resting) slide.coerceIn(0f, 1f) else 1f
        val topLeft = Offset(left + SLOT_EDGE / 2, top + SLOT_EDGE / 2 + dy)
        val size = Size(layout.slotWidth - SLOT_EDGE, layout.slotHeight - SLOT_EDGE)
        val corner = CornerRadius(PANEL_RADIUS)
        if (resting) {
            drawRoundRect(PawCardWhite, topLeft, size, corner, alpha = alpha)
            drawRoundRect(PawSunshine, topLeft, size, corner, style = Stroke(SLOT_EDGE), alpha = alpha)
        } else {
            drawRoundRect(BlocksSlotDash, topLeft, size, corner, style = Stroke(SLOT_EDGE, pathEffect = SlotDash))
        }
        if (shape != null) {
            val bx = left + (layout.slotWidth - shape.width * trayCell) / 2f
            val by = top + (layout.slotHeight - shape.height * trayCell) / 2f + dy
            drawBlock(shape, bx, by, trayCell, alpha = if (resting) alpha else 0.18f)
        }
    }
}

// ------------------------------------------------------------------ effects

private const val SWEEP_CELL_SPREAD_MS = 450f
private const val SPARKLE_MS = 520f
private const val CRITTER_SIZE = 72f
private const val CRITTER_INSET = 78f  // the head's left edge, back from the panel's right edge
private const val CRITTER_RISE = 68f   // the head's top when fully up, above the panel top
private const val CRITTER_DROP = 58f
private const val PAW_INSET_A = 60f
private const val PAW_INSET_B = 24f

/** How far up the animal is (0 hidden, 1 up) [k] of the way through its second: rises 250ms, holds 500ms, sinks 250ms. */
internal fun critterRise(k: Float): Float = when {
    k <= 0f || k >= 1f -> 0f
    k < 0.25f -> easeOut(k / 0.25f)
    k < 0.75f -> 1f
    else -> 1f - easeOut((k - 0.75f) / 0.25f)
}

private data class CellLook(val scale: Float, val alpha: Float, val white: Float)

/** A cell of a completed line, [t] ms after the sweep reaches it: brightens and swells in 150ms, then shrinks away over 250ms. */
private fun lineCellLook(t: Float): CellLook? = when {
    t < 0f -> CellLook(1f, 1f, 0f)
    t < 150f -> (t / 150f).let { CellLook(1f + 0.06f * it, 1f, 0.5f * it) }
    t < 400f -> ((t - 150f) / 250f).let { it * it }.let { CellLook(1.06f * (1f - it), 1f - it, 0.5f) }
    else -> null
}

/** A cell of a clear-out: over 700ms it fades and shrinks to 0.72 under a light wash. */
private fun outCellLook(t: Float): CellLook? = when {
    t < 0f -> CellLook(1f, 1f, 0f)
    t < 700f -> easeOut(t / 700f).let { CellLook(1f - 0.28f * it, 1f - it, 0.35f * min(1f, t / 200f)) }
    else -> null
}

/** One path reused by every shimmer band in a frame (drawing is single-threaded), so a clear allocates no path per frame. */
private val ShimmerPath = Path()

private val ShimmerColors = listOf(Color.White.copy(alpha = 0f), Color.White.copy(alpha = 0.85f), Color.White.copy(alpha = 0f))

/** The white band sweeping along a row: 1.6 cells wide, leaning 0.18 of a cell, clipped to the row. */
private fun DrawScope.shimmerRow(layout: BlocksLayout, cell: Float, row: Int, pos: Float) {
    val y0 = layout.gridTop + row * cell
    val x = layout.gridLeft + pos * layout.gridSize
    val w = SWEEP_WIDTH * cell
    val lean = SWEEP_LEAN * cell
    val path = ShimmerPath.apply {
        rewind()
        moveTo(x - w / 2 + lean, y0); lineTo(x + w / 2 + lean, y0); lineTo(x + w / 2 - lean, y0 + cell); lineTo(x - w / 2 - lean, y0 + cell); close()
    }
    clipRect(layout.gridLeft, y0, layout.gridLeft + layout.gridSize, y0 + cell) {
        drawPath(path, Brush.horizontalGradient(ShimmerColors, startX = x - w / 2 - lean, endX = x + w / 2 + lean))
    }
}

private fun DrawScope.shimmerCol(layout: BlocksLayout, cell: Float, col: Int, pos: Float) {
    val x0 = layout.gridLeft + col * cell
    val y = layout.gridTop + pos * layout.gridSize
    val w = SWEEP_WIDTH * cell
    val lean = SWEEP_LEAN * cell
    val path = ShimmerPath.apply {
        rewind()
        moveTo(x0, y - w / 2 + lean); lineTo(x0 + cell, y - w / 2 - lean); lineTo(x0 + cell, y + w / 2 - lean); lineTo(x0, y + w / 2 + lean); close()
    }
    clipRect(x0, layout.gridTop, x0 + cell, layout.gridTop + layout.gridSize) {
        drawPath(path, Brush.verticalGradient(ShimmerColors, startY = y - w / 2 - lean, endY = y + w / 2 + lean))
    }
}
