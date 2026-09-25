package com.pawplay.app.games.pawblocks

import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.graphics.vector.PathParser
import com.pawplay.app.ui.theme.InkColor
import com.pawplay.app.ui.theme.PawSky
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.sin

// Everything here draws in dp-like units from the numbers in docs/DESIGN-SYSTEM.md: the caller scales the
// canvas by the screen density (or, for the home tile, by tile size / 146) so nothing is a pixel count.

/** An SVG path string as a Compose path; called once per picture (at load), never per frame. */
internal fun svg(d: String): Path = PathParser().parsePathString(d).toPath()

/** The eight marks as paths in a unit box from -0.5 to +0.5 (y down), exactly as in the design. */
private object MarkArt {
    val star: Path = Path().apply {
        for (i in 0 until 10) {
            val r = if (i % 2 == 1) 0.24f else 0.52f
            val a = -PI / 2 + i * PI / 5
            val x = (cos(a) * r).toFloat()
            val y = (sin(a) * r).toFloat() + 0.03f
            if (i == 0) moveTo(x, y) else lineTo(x, y)
        }
        close()
    }
    val heart: Path = svg("M0,0.42 C-0.58,0.02 -0.5,-0.44 -0.2,-0.44 C-0.08,-0.44 0,-0.36 0,-0.27 C0,-0.36 0.08,-0.44 0.2,-0.44 C0.5,-0.44 0.58,0.02 0,0.42Z")
    val drop: Path = svg("M0,-0.5 C0.08,-0.32 0.36,-0.12 0.36,0.14 A0.36,0.36 0 0 1 -0.36,0.14 C-0.36,-0.12 -0.08,-0.32 0,-0.5Z")
    val fish: Path = svg("M-0.46,0 C-0.34,-0.24 0.06,-0.3 0.24,-0.04 L0.48,-0.26 L0.48,0.26 L0.24,0.04 C0.06,0.3 -0.34,0.24 -0.46,0Z")
    val leaf: Path = svg("M-0.42,0.42 C-0.54,-0.08 -0.12,-0.5 0.44,-0.44 C0.5,0.12 0.08,0.52 -0.42,0.42Z")
    val moon: Path = svg("M0.25,-0.5 A0.5,0.5 0 1 0 0.25,0.5 A0.7,0.7 0 0 1 0.25,-0.5Z")
    val sparkle: Path = svg("M0,-1Q0.18,-0.18 1,0Q0.18,0.18 0,1Q-0.18,0.18 -1,0Q-0.18,-0.18 0,-1Z")
    val PAW_TOES = listOf(
        floatArrayOf(-0.36f, -0.02f, 0.11f, 0.14f), floatArrayOf(-0.13f, -0.27f, 0.12f, 0.15f),
        floatArrayOf(0.13f, -0.27f, 0.12f, 0.15f), floatArrayOf(0.36f, -0.02f, 0.11f, 0.14f),
    )
}

private const val MARK_STROKE = 0.07f
private val MarkJoin = Stroke(width = MARK_STROKE, join = StrokeJoin.Round)

private fun DrawScope.solid(path: Path, colour: Color, alpha: Float) {
    drawPath(path, colour, alpha = alpha)
    drawPath(path, colour, alpha = alpha, style = MarkJoin)
}

private fun DrawScope.solidOval(cx: Float, cy: Float, rx: Float, ry: Float, colour: Color, alpha: Float) {
    drawOval(colour, Offset(cx - rx, cy - ry), Size(2 * rx, 2 * ry), alpha = alpha)
    drawOval(colour, Offset(cx - rx, cy - ry), Size(2 * rx, 2 * ry), alpha = alpha, style = MarkJoin)
}

/**
 * One mark on a cell: [size] is the whole box (62% of the cell side), centred on ([cx], [cy]). The fish's eye and
 * the leaf's vein are cut out in the family colour. Pictures only, always upright.
 */
internal fun DrawScope.drawMark(family: Family, cx: Float, cy: Float, size: Float, alpha: Float = 1f) {
    val ink = family.markColor
    withTransform({ translate(cx, cy); scale(size, size, Offset.Zero) }) {
        when (family.mark) {
            Mark.STAR -> solid(MarkArt.star, ink, alpha)
            Mark.HEART -> solid(MarkArt.heart, ink, alpha)
            Mark.DROP -> solid(MarkArt.drop, ink, alpha)
            Mark.FISH -> {
                solid(MarkArt.fish, ink, alpha)
                drawCircle(family.color, 0.055f, Offset(-0.24f, -0.04f), alpha = alpha)
            }
            Mark.LEAF -> {
                solid(MarkArt.leaf, ink, alpha)
                drawLine(family.color, Offset(-0.34f, 0.34f), Offset(0.2f, -0.2f), strokeWidth = 0.07f, cap = StrokeCap.Round, alpha = alpha)
            }
            Mark.BONE -> rotate(-40f, Offset.Zero) {
                drawRect(ink, Offset(-0.3f, -0.1f), Size(0.6f, 0.2f), alpha = alpha)
                drawRect(ink, Offset(-0.3f, -0.1f), Size(0.6f, 0.2f), alpha = alpha, style = MarkJoin)
                for (sx in floatArrayOf(-0.32f, 0.32f)) for (sy in floatArrayOf(-0.13f, 0.13f)) {
                    drawCircle(ink, 0.13f, Offset(sx, sy), alpha = alpha)
                    drawCircle(ink, 0.13f, Offset(sx, sy), alpha = alpha, style = MarkJoin)
                }
            }
            Mark.PAW -> {
                solidOval(0f, 0.2f, 0.28f, 0.24f, ink, alpha)
                for (t in MarkArt.PAW_TOES) solidOval(t[0], t[1], t[2], t[3], ink, alpha)
            }
            Mark.MOON -> solid(MarkArt.moon, ink, alpha)
        }
    }
}

/** 4-point twinkle of radius [r] centred on ([x], [y]). */
internal fun DrawScope.drawTwinkle(x: Float, y: Float, r: Float, colour: Color, alpha: Float = 1f) {
    if (r <= 0f) return
    withTransform({ translate(x, y); scale(r, r, Offset.Zero) }) { drawPath(MarkArt.sparkle, colour, alpha = alpha) }
}

// ------------------------------------------------------------------ cells and blocks

private const val CELL_INSET = 0.03f
private const val CELL_RADIUS = 0.22f

/** A pale, outlined, unmarked cell: how an empty spot on the board looks. */
internal fun DrawScope.drawEmptyCell(x: Float, y: Float, s: Float, alpha: Float = 1f) {
    val g = s * CELL_INSET
    val w = s - 2 * g
    drawRoundRect(BlocksEmptyFill, Offset(x + g, y + g), Size(w, w), CornerRadius(s * CELL_RADIUS), alpha = alpha)
    drawRoundRect(BlocksEmptyEdge, Offset(x + g, y + g), Size(w, w), CornerRadius(s * CELL_RADIUS), style = Stroke(max(1.25f, s * 0.025f)), alpha = alpha)
}

/**
 * A filled cell: family colour, a faint ink rim (which is what keeps the palest colour visible against an empty
 * cell) and the family's mark. [scale] grows or shrinks it about its centre; [white] lays white over it.
 */
internal fun DrawScope.drawFilledCell(x: Float, y: Float, s: Float, family: Family, scale: Float = 1f, alpha: Float = 1f, white: Float = 0f) {
    if (alpha <= 0f || scale <= 0f) return
    val cx = x + s / 2f
    val cy = y + s / 2f
    withTransform({ if (scale != 1f) scale(scale, scale, Offset(cx, cy)) }) {
        val g = s * CELL_INSET
        val w = s - 2 * g
        val topLeft = Offset(x + g, y + g)
        val corner = CornerRadius(s * CELL_RADIUS)
        drawRoundRect(family.color, topLeft, Size(w, w), corner, alpha = alpha)
        drawRoundRect(InkColor, topLeft, Size(w, w), corner, style = Stroke(max(1f, s * 0.025f)), alpha = 0.28f * alpha)
        drawMark(family, cx, cy, s * 0.62f, alpha)
        if (white > 0f) drawRoundRect(Color.White, topLeft, Size(w, w), corner, alpha = white * alpha)
    }
}

internal fun DrawScope.drawBlock(shape: BlockShape, x: Float, y: Float, s: Float, alpha: Float = 1f) {
    for (c in shape.cells) drawFilledCell(x + c.col * s, y + c.row * s, s, shape.family, alpha = alpha)
}

/** The soft shadow under a lifted block: ink at 18%, 8dp down. */
internal fun DrawScope.drawBlockShadow(shape: BlockShape, x: Float, y: Float, s: Float) {
    val g = s * CELL_INSET
    for (c in shape.cells) {
        drawRoundRect(
            InkColor, Offset(x + c.col * s + g, y + c.row * s + g + 8f), Size(s - 2 * g, s - 2 * g),
            CornerRadius(s * CELL_RADIUS), alpha = 0.18f,
        )
    }
}

/**
 * The ghost: where a drop would land. Sky at 30% with a 3dp deep-blue outline; on boards 7x7 and up, 42%, a 4.5dp
 * outline and a 10dp sky halo at 50% under it, so it stays easy to see on 36dp cells. It carries no mark.
 */
internal fun DrawScope.drawGhost(shape: BlockShape, x: Float, y: Float, s: Float, big: Boolean) {
    val g = s * CELL_INSET
    val corner = CornerRadius(s * CELL_RADIUS)
    for (c in shape.cells) {
        val topLeft = Offset(x + c.col * s + g, y + c.row * s + g)
        val size = Size(s - 2 * g, s - 2 * g)
        if (big) drawRoundRect(PawSky, topLeft, size, corner, style = Stroke(10f), alpha = 0.5f)
        drawRoundRect(PawSky, topLeft, size, corner, alpha = if (big) 0.42f else 0.30f)
        drawRoundRect(BlocksGhostEdge, topLeft, size, corner, style = Stroke(if (big) 4.5f else 3f))
    }
}

internal val SlotDash: PathEffect = PathEffect.dashPathEffect(floatArrayOf(8f, 7f))
