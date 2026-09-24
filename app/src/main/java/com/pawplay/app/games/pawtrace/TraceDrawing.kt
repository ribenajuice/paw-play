package com.pawplay.app.games.pawtrace

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
import com.pawplay.app.ui.theme.PawSunshine

/**
 * Drawing helpers for Paw Trace. Everything is drawn from paths, so there are no image assets.
 * Paths here are in pixels: glyph units times [unit] (pixels per glyph unit), because the band, the
 * corridor and the marker are fixed in dp and must not scale with the picture.
 */

/** The stroke's exact centreline (curves stay curves) in pixels. */
internal fun StrokePath.toPath(unit: Float): Path = Path().apply {
    moveTo((start.x * unit).toFloat(), (start.y * unit).toFloat())
    for (seg in segments) when (seg) {
        is LineTo -> lineTo((seg.end.x * unit).toFloat(), (seg.end.y * unit).toFloat())
        is CubicTo -> cubicTo(
            (seg.control1.x * unit).toFloat(), (seg.control1.y * unit).toFloat(),
            (seg.control2.x * unit).toFloat(), (seg.control2.y * unit).toFloat(),
            (seg.end.x * unit).toFloat(), (seg.end.y * unit).toFloat(),
        )
    }
}

/** Every contiguous run of flagged samples along [stroke], as a polyline in pixels added to [into]. */
internal fun addRuns(into: Path, stroke: SampledStroke, flags: BooleanArray, unit: Float) {
    var i = 0
    while (i < stroke.count) {
        if (!flags[i]) { i++; continue }
        var j = i
        while (j < stroke.last && flags[j + 1]) j++
        into.moveTo((stroke.xs[i] * unit).toFloat(), (stroke.ys[i] * unit).toFloat())
        if (i == j) {
            // A single sample: a tiny segment so the round caps draw a dot.
            into.lineTo((stroke.xs[i] * unit).toFloat() + 0.01f, (stroke.ys[i] * unit).toFloat())
        } else {
            for (k in i + 1..j) into.lineTo((stroke.xs[k] * unit).toFloat(), (stroke.ys[k] * unit).toFloat())
        }
        i = j + 1
    }
}

/** The whole stroke as a polyline of its samples, in pixels. */
internal fun SampledStroke.polyline(unit: Float, upTo: Int = last): Path = Path().apply {
    moveTo((xs[0] * unit).toFloat(), (ys[0] * unit).toFloat())
    for (k in 1..upTo) lineTo((xs[k] * unit).toFloat(), (ys[k] * unit).toFloat())
}

internal fun roundStroke(width: Float, effect: PathEffect? = null, cap: StrokeCap = StrokeCap.Round) =
    Stroke(width = width, cap = cap, join = StrokeJoin.Round, pathEffect = effect)

/** The soft wide guide: a 3dp lavender outline round a 56dp pale band, with a dotted centre. [dp] is pixels per dp. */
internal fun DrawScope.drawGuide(path: Path, dp: Float) {
    drawPath(path, TraceGuideEdge, style = roundStroke((TraceMetrics.BAND_DP.toFloat() + 6f) * dp))
    drawPath(path, TraceGuideFill, style = roundStroke(TraceMetrics.BAND_DP.toFloat() * dp))
    drawPath(path, TraceGuideDots, style = roundStroke(3f * dp, PathEffect.dashPathEffect(floatArrayOf(0.1f, 10f * dp))))
}

/** Paint on [path]: three widening glow rings (no blur), the 56dp grape core and a 16dp shine. */
internal fun DrawScope.drawPaint(path: Path, dp: Float, alpha: Float = 1f) {
    drawPath(path, TraceGlow, alpha = 0.18f * alpha, style = roundStroke(77f * dp))
    drawPath(path, TraceGlow, alpha = 0.26f * alpha, style = roundStroke(69f * dp))
    drawPath(path, TraceGlow, alpha = 0.38f * alpha, style = roundStroke(62f * dp))
    drawPath(path, TraceGrape, alpha = alpha, style = roundStroke(TraceMetrics.BAND_DP.toFloat() * dp))
    drawPath(path, TraceShine, alpha = 0.55f * alpha, style = roundStroke(16f * dp))
}

/** The thin white line down the centre of a finished stroke. */
internal fun DrawScope.drawDoneLine(path: Path, dp: Float, alpha: Float = 1f) {
    drawPath(path, Color.White, alpha = 0.5f * alpha, style = roundStroke(5f * dp))
}

/** A moving white shimmer over a finished stroke: 12% of its length, 6 units wide, looping along it. [phase01] is 0..1. */
internal fun DrawScope.drawShimmer(path: Path, lengthPx: Float, widthPx: Float, phase01: Float) {
    if (lengthPx <= 0f) return
    val effect = PathEffect.dashPathEffect(floatArrayOf(0.12f * lengthPx, 0.88f * lengthPx), (1f - phase01) * lengthPx)
    drawPath(path, Color.White, alpha = 0.75f, style = Stroke(width = widthPx, cap = StrokeCap.Butt, join = StrokeJoin.Round, pathEffect = effect))
}

private val PawOvals = listOf(
    floatArrayOf(40f, 52f, 20f, 16f), // pad
    floatArrayOf(18f, 30f, 8f, 10f),  // toes
    floatArrayOf(34f, 18f, 8f, 10f),
    floatArrayOf(52f, 18f, 8f, 10f),
    floatArrayOf(64f, 32f, 8f, 10f),
)

/** The same paw print as the play button, [sizePx] wide, centred at ([cx], [cy]) and turned so its toes point at [angleDegrees] (0 = right). */
internal fun DrawScope.drawPaw(cx: Float, cy: Float, sizePx: Float, angleDegrees: Float, color: Color, alpha: Float) {
    withTransform({
        translate(cx - sizePx / 2f, cy - sizePx / 2f)
        rotate(angleDegrees + 90f, Offset(sizePx / 2f, sizePx / 2f))
        scale(sizePx / 80f, sizePx / 80f, Offset.Zero)
    }) {
        for (o in PawOvals) drawOval(color, Offset(o[0] - o[2], o[1] - o[3]), Size(o[2] * 2f, o[3] * 2f), alpha = alpha)
    }
}

private val SparklePath: Path by lazy { PathParser().parsePathString("M0,-1Q0.18,-0.18 1,0Q0.18,0.18 0,1Q-0.18,0.18 -1,0Q-0.18,-0.18 0,-1Z").toPath() }
private val HeartPath: Path by lazy {
    PathParser().parsePathString("M0,0.42C-0.58,0.02 -0.5,-0.44 -0.2,-0.44C-0.08,-0.44 0,-0.36 0,-0.27C0,-0.36 0.08,-0.44 0.2,-0.44C0.5,-0.44 0.58,0.02 0,0.42Z").toPath()
}

/** A four-point twinkle of radius [radius] px. (Same drawing as Paw Kitchen's; see docs/DECISIONS.md on sharing it.) */
internal fun DrawScope.drawSparkle(x: Float, y: Float, radius: Float, color: Color = PawSunshine, alpha: Float = 1f) {
    withTransform({ translate(x, y); scale(radius, radius, Offset.Zero) }) { drawPath(SparklePath, color, alpha = alpha) }
}

internal fun DrawScope.drawHeart(x: Float, y: Float, size: Float, color: Color, alpha: Float = 1f) {
    withTransform({ translate(x, y); scale(size, size, Offset.Zero) }) { drawPath(HeartPath, color, alpha = alpha) }
}
