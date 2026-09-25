package com.pawplay.app.games.pawtrace

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.vector.PathParser
import androidx.compose.ui.unit.Dp
import com.pawplay.app.ui.theme.FrogHead
import com.pawplay.app.ui.theme.FrogMuzzle
import com.pawplay.app.ui.theme.InkColor

/**
 * The frog marker's face, drawn here so Paw Trace depends on no other game's code. It is the same frog as
 * Paw Match and Paw Kitchen (same colours from ui/theme, same 80 x 80 grid recipe: round head with a faint
 * ink rim, muzzle, eyes, nose, and a small smile), plus the squeezed-shut-eyes open grin with blush that the
 * design asks for on the celebration. The picture is cropped tight to a 54 x 54 window (grid x 13..67,
 * y 19..73) so the head fills the square.
 */
private const val CROP_LEFT = 13f
private const val CROP_TOP = 19f
private const val CROP_SIZE = 54f

private val Rim = InkColor.copy(alpha = 0.22f)

private fun svg(d: String): Path = PathParser().parsePathString(d).toPath()

@Composable
internal fun FrogFace(size: Dp, happy: Boolean, modifier: Modifier = Modifier) {
    Canvas(modifier.size(size)) {
        scale(scale = this.size.minDimension / CROP_SIZE, pivot = Offset.Zero) {
            translate(-CROP_LEFT, -CROP_TOP) { drawFrog(happy) }
        }
    }
}

private fun DrawScope.ink(d: String, width: Float) =
    drawPath(svg(d), InkColor, style = Stroke(width = width, cap = StrokeCap.Round, join = StrokeJoin.Round))

private fun DrawScope.drawFrog(happy: Boolean) {
    drawCircle(FrogHead, 25f, Offset(40f, 46f))
    drawCircle(Rim, 25f, Offset(40f, 46f), style = Stroke(width = 1.6f, join = StrokeJoin.Round))
    drawOval(FrogMuzzle, Offset(27f, 45f), Size(26f, 18f))
    val nose = Path().apply { moveTo(40f, 50f); lineTo(36f, 54f); lineTo(44f, 54f); close() }
    if (happy) {
        drawCircle(TraceFrogBlush, 4.6f, Offset(24f, 52f), alpha = 0.7f)
        drawCircle(TraceFrogBlush, 4.6f, Offset(56f, 52f), alpha = 0.7f)
        ink("M27,44Q32,37 37,44M43,44Q48,37 53,44", 3f)
        val mouth = svg("M32,56Q40,68 48,56Z")
        drawPath(mouth, TraceFrogMouth)
        drawPath(mouth, InkColor, style = Stroke(width = 1.6f, join = StrokeJoin.Round))
        drawPath(svg("M36,60Q40,64 44,60Q40,58 36,60Z"), TraceFrogBlush)
        drawPath(nose, InkColor)
    } else {
        val r = 3.4f * 1.3f
        drawCircle(InkColor, r, Offset(32f, 42f))
        drawCircle(InkColor, r, Offset(48f, 42f))
        drawPath(nose, InkColor)
        ink("M34,58Q40,63 46,58", 2f)
    }
}
