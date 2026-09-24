package com.pawplay.app.games.pawpour

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Fill
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.drawscope.translate
import com.pawplay.app.ui.theme.InkColor
import kotlin.math.PI
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * How each [BandColor] looks: a colour plus its own mark, so no two colours share a mark and
 * none needs hue to be told apart (docs/PRD.md story 13). Band colours are content colours, like
 * the critter palette in Color.kt — not theme tokens — and live here so adding this game touches
 * nothing outside its own package. Values are in docs/DESIGN-SYSTEM.md, "Paw Pour".
 */
internal enum class Mark { STAR, HEART, RING, TRIANGLE, DIAMOND, PLUS, MOON }

internal data class BandStyle(val color: Color, val mark: Mark, val markColor: Color)

internal fun BandColor.style(): BandStyle {
    val mark = when (this) {
        BandColor.SUNSHINE -> Mark.STAR
        BandColor.BUBBLEGUM -> Mark.HEART
        BandColor.SKY -> Mark.RING
        BandColor.CORAL -> Mark.TRIANGLE
        BandColor.LEAF -> Mark.DIAMOND
        BandColor.GRAPE -> Mark.PLUS
        BandColor.MIDNIGHT -> Mark.MOON
    }
    // Colours come from BandColor.rgb() (PawPourLogic.kt) so their brightness ladder is unit-tested.
    return BandStyle(Color(0xFF000000L or rgb().toLong()), mark, if (markIsWhite()) Color.White else InkColor)
}

// Every mark is authored in a unit box (roughly -0.5..0.5) and scaled when drawn, so one drawing
// serves a 20dp band on a small tube and a 40dp band on a big one.
private val markPaths: Map<Mark, Path> by lazy {
    mapOf(
        Mark.STAR to Path().apply {
            for (i in 0 until 10) {
                val radius = if (i % 2 == 1) 0.24f else 0.52f
                val angle = -PI.toFloat() / 2f + i * PI.toFloat() / 5f
                val x = cos(angle) * radius
                val y = sin(angle) * radius + 0.03f
                if (i == 0) moveTo(x, y) else lineTo(x, y)
            }
            close()
        },
        Mark.HEART to Path().apply {
            moveTo(0f, 0.42f)
            cubicTo(-0.58f, 0.02f, -0.5f, -0.44f, -0.2f, -0.44f)
            cubicTo(-0.08f, -0.44f, 0f, -0.36f, 0f, -0.27f)
            cubicTo(0f, -0.36f, 0.08f, -0.44f, 0.2f, -0.44f)
            cubicTo(0.5f, -0.44f, 0.58f, 0.02f, 0f, 0.42f)
            close()
        },
        Mark.RING to Path().apply { addOval(Rect(-0.33f, -0.33f, 0.33f, 0.33f)) },
        Mark.TRIANGLE to Path().apply {
            moveTo(0f, -0.42f); lineTo(0.46f, 0.36f); lineTo(-0.46f, 0.36f); close()
        },
        Mark.DIAMOND to Path().apply {
            moveTo(0f, -0.5f); lineTo(0.4f, 0f); lineTo(0f, 0.5f); lineTo(-0.4f, 0f); close()
        },
        Mark.PLUS to Path().apply {
            moveTo(-0.16f, -0.5f); lineTo(0.16f, -0.5f); lineTo(0.16f, -0.16f); lineTo(0.5f, -0.16f)
            lineTo(0.5f, 0.16f); lineTo(0.16f, 0.16f); lineTo(0.16f, 0.5f); lineTo(-0.16f, 0.5f)
            lineTo(-0.16f, 0.16f); lineTo(-0.5f, 0.16f); lineTo(-0.5f, -0.16f); lineTo(-0.16f, -0.16f)
            close()
        },
        Mark.MOON to Path().apply {
            // Outer edge: left half of a circle of radius 0.5; inner edge: a shallower radius-0.7 arc back.
            val innerCentreX = 0.25f + sqrt(0.7f * 0.7f - 0.5f * 0.5f)
            val startDegrees = Math.toDegrees(atan2(0.5f, 0.25f - innerCentreX).toDouble()).toFloat()
            moveTo(0.25f, -0.5f)
            arcTo(Rect(-0.25f, -0.5f, 0.75f, 0.5f), -90f, -180f, false)
            arcTo(
                Rect(innerCentreX - 0.7f, -0.7f, innerCentreX + 0.7f, 0.7f),
                startDegrees,
                360f - 2f * startDegrees,
                false,
            )
            close()
        },
    )
}

/** Draws [mark] centred at ([centreX], [centreY]) with a unit box of [size] px. Softly rounded corners. */
internal fun DrawScope.drawMark(mark: Mark, centreX: Float, centreY: Float, size: Float, color: Color) {
    val path = markPaths.getValue(mark)
    translate(left = centreX, top = centreY) {
        scale(scaleX = size, scaleY = size, pivot = Offset.Zero) {
            if (mark == Mark.RING) {
                drawPath(path, color, style = Stroke(width = 0.2f))
            } else {
                drawPath(path, color, style = Fill)
                drawPath(path, color, style = Stroke(width = 0.07f, join = StrokeJoin.Round))
            }
        }
    }
}
