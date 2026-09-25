package com.pawplay.app.ui

import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.drawscope.translate
import com.pawplay.app.ui.theme.InkColor
import com.pawplay.app.ui.theme.PawCardWhite
import com.pawplay.app.ui.theme.PawSky
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

// The best-score cue: a scalloped prize rosette with two ribbon tails and a white paw in the middle (founder's
// choice over a crown, docs/DECISIONS.md). Deliberately not a coin or a star: its silhouette is a scalloped disc with
// two tails even in greyscale. Drawn on a 64-unit box exactly as in docs/DESIGN-SYSTEM.md, "Rosette".

private val RosetteGrape = Color(0xFF8E59E6)
private val RosetteRibbonDeep = Color(0xFF2FA0CC)
private val RosetteInk = InkColor.copy(alpha = 0.45f)
private val RosetteEdge = Stroke(width = 2f, join = StrokeJoin.Round)

private const val RosetteBox = 64f
private const val RosetteCentreX = 32f
private const val RosetteCentreY = 29f

private fun poly(vararg xy: Float): Path = Path().apply {
    moveTo(xy[0], xy[1])
    for (i in 2 until xy.size step 2) lineTo(xy[i], xy[i + 1])
    close()
}

private val TailLeft = poly(-9f, 10f, -19f, 32f, -10f, 27f, -4f, 33f, 1f, 12f)
private val TailRight = poly(9f, 10f, 19f, 32f, 10f, 27f, 4f, 33f, -1f, 12f)

// The paw in the middle, on Paw Match's 80-unit grid (cx, cy, rx, ry), drawn at a quarter of that size.
private val CentrePaw = arrayOf(
    floatArrayOf(40f, 52f, 20f, 16f),
    floatArrayOf(18f, 30f, 8f, 10f),
    floatArrayOf(34f, 18f, 8f, 10f),
    floatArrayOf(52f, 18f, 8f, 10f),
    floatArrayOf(64f, 32f, 8f, 10f),
)

/** The rosette, drawn to fill a square [Modifier] size (56dp on the good-game screen). */
@Composable
fun RosetteIcon(modifier: Modifier = Modifier) {
    Canvas(modifier) {
        scale(size.minDimension / RosetteBox, Offset.Zero) {
            translate(RosetteCentreX, RosetteCentreY) { drawRosette() }
        }
    }
}

private fun DrawScope.drawRosette() {
    drawPath(TailLeft, PawSky)
    drawPath(TailLeft, RosetteInk, style = RosetteEdge)
    drawPath(TailRight, RosetteRibbonDeep)
    drawPath(TailRight, RosetteInk, style = RosetteEdge)
    for (k in 0 until 10) {
        val a = k * PI / 5
        val c = Offset((cos(a) * 17.0).toFloat(), (sin(a) * 17.0).toFloat())
        drawCircle(RosetteGrape, 8f, c)
        drawCircle(RosetteInk, 8f, c, style = RosetteEdge)
    }
    drawCircle(RosetteGrape, 19.5f)
    drawCircle(PawCardWhite, 13f)
    drawCircle(RosetteInk, 13f, style = RosetteEdge)
    drawCircle(RosetteGrape, 10.5f)
    val s = 0.2f // 80 units -> 16
    for (e in CentrePaw) {
        val cx = (e[0] - 40f) * s
        val cy = (e[1] - 38f) * s
        drawOval(PawCardWhite, Offset(cx - e[2] * s, cy - e[3] * s), Size(2 * e[2] * s, 2 * e[3] * s))
    }
}
