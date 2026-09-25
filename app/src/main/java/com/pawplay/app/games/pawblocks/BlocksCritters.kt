package com.pawplay.app.games.pawblocks

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.scale
import com.pawplay.app.ui.theme.BearEar
import com.pawplay.app.ui.theme.BearHead
import com.pawplay.app.ui.theme.BearMuzzle
import com.pawplay.app.ui.theme.BunnyEar
import com.pawplay.app.ui.theme.BunnyHead
import com.pawplay.app.ui.theme.BunnyMuzzle
import com.pawplay.app.ui.theme.CatEar
import com.pawplay.app.ui.theme.CatHead
import com.pawplay.app.ui.theme.CatMuzzle
import com.pawplay.app.ui.theme.FoxEar
import com.pawplay.app.ui.theme.FoxHead
import com.pawplay.app.ui.theme.FoxMuzzle
import com.pawplay.app.ui.theme.FrogHead
import com.pawplay.app.ui.theme.FrogMuzzle
import com.pawplay.app.ui.theme.InkColor
import com.pawplay.app.ui.theme.OwlEar
import com.pawplay.app.ui.theme.OwlHead
import com.pawplay.app.ui.theme.OwlMuzzle

/**
 * The little animal that peeks up over the board's corner when a line clears, drawn here so Paw Blocks
 * depends on no other game's code. The same six critters as Paw Match, in the same colours (from ui/theme)
 * and on the same 80 x 80 grid, with the happy face Paw Kitchen's customers use: squeezed-shut arched eyes,
 * an open smile and pink cheeks. One animal per clear, in a fixed rotation (`BlocksRamp.critterIndex`).
 */
private enum class Ears { POINTED, ROUND, FLOPPY, NONE }

private class Look(val head: Color, val muzzle: Color, val ear: Color, val ears: Ears)

private val looks = listOf(
    Look(FoxHead, FoxMuzzle, FoxEar, Ears.POINTED),
    Look(BearHead, BearMuzzle, BearEar, Ears.ROUND),
    Look(BunnyHead, BunnyMuzzle, BunnyEar, Ears.FLOPPY),
    Look(OwlHead, OwlMuzzle, OwlEar, Ears.ROUND),
    Look(CatHead, CatMuzzle, CatEar, Ears.POINTED),
    Look(FrogHead, FrogMuzzle, Color.Transparent, Ears.NONE),
)

private val Rim = InkColor.copy(alpha = 0.22f)
private val RimStroke = Stroke(width = 1.6f, join = StrokeJoin.Round)

// Parsed once, not on every frame while the animal is showing.
private val EyesPath = svg("M27,44Q32,37 37,44M43,44Q48,37 53,44")
private val MouthPath = svg("M32,56Q40,68 48,56Z")
private val TonguePath = svg("M36,60Q40,64 44,60Q40,58 36,60Z")
private val NosePath = Path().apply { moveTo(40f, 50f); lineTo(36f, 54f); lineTo(44f, 54f); close() }
private val EarPaths = listOf(
    Path().apply { moveTo(20f, 30f); lineTo(27f, 8f); lineTo(35f, 28f); close() },
    Path().apply { moveTo(60f, 30f); lineTo(53f, 8f); lineTo(45f, 28f); close() },
)
private val EyeStroke = Stroke(3f, cap = StrokeCap.Round, join = StrokeJoin.Round)

/** The colour of this animal's paws on the board's edge: its head colour. */
internal fun critterPawColor(index: Int): Color = looks[index.mod(looks.size)].head

/** Draws critter [index] (0-5) with the happy face on an 80 x 80 grid whose top-left is the current origin, scaled to [size] units. */
internal fun DrawScope.drawHappyCritter(index: Int, size: Float) = drawCritter(index, size, happy = true)

/**
 * The calm face for the good-game screen: dot eyes, a small smile, faint cheeks. Never sad, worried or crying
 * (docs/DESIGN-SYSTEM.md, "Good-game screen"). A new best gets [drawHappyCritter] instead.
 */
internal fun DrawScope.drawCalmCritter(index: Int, size: Float) = drawCritter(index, size, happy = false)

private val SmilePath = svg("M33,56Q40,62 47,56")
private val SmileStroke = Stroke(2.4f, cap = StrokeCap.Round, join = StrokeJoin.Round)

private fun DrawScope.drawCritter(index: Int, size: Float, happy: Boolean) {
    val look = looks[index.mod(looks.size)]
    scale(scale = size / 80f, pivot = Offset.Zero) {
        when (look.ears) {
            Ears.POINTED -> {
                for (p in EarPaths) { drawPath(p, look.ear); drawPath(p, Rim, style = RimStroke) }
            }
            Ears.ROUND -> for (x in listOf(18f, 62f)) {
                drawCircle(look.ear, 9f, Offset(x, 20f))
                drawCircle(Rim, 9f, Offset(x, 20f), style = RimStroke)
            }
            Ears.FLOPPY -> for ((x, deg) in listOf(30f to -8f, 50f to 8f)) rotate(deg, Offset(x, 14f)) {
                drawOval(look.ear, Offset(x - 7f, -4f), Size(14f, 36f))
                drawOval(Rim, Offset(x - 7f, -4f), Size(14f, 36f), style = RimStroke)
            }
            Ears.NONE -> Unit
        }
        drawCircle(look.head, 25f, Offset(40f, 46f))
        drawCircle(Rim, 25f, Offset(40f, 46f), style = RimStroke)
        drawOval(look.muzzle, Offset(27f, 45f), Size(26f, 18f))
        val cheek = if (happy) 0.7f else 0.4f
        drawCircle(BlocksBlush, 4.6f, Offset(24f, 52f), alpha = cheek)
        drawCircle(BlocksBlush, 4.6f, Offset(56f, 52f), alpha = cheek)
        if (happy) {
            drawPath(EyesPath, InkColor, style = EyeStroke)
            drawPath(MouthPath, BlocksMouth)
            drawPath(MouthPath, InkColor, style = RimStroke)
            drawPath(TonguePath, BlocksBlush)
        } else {
            drawCircle(InkColor, 2.8f, Offset(31f, 42f))
            drawCircle(InkColor, 2.8f, Offset(49f, 42f))
            drawPath(SmilePath, InkColor, style = SmileStroke)
        }
        drawPath(NosePath, InkColor)
    }
}
