package com.pawplay.app.games.pawkitchen

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.DrawStyle
import androidx.compose.ui.graphics.drawscope.Fill
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.unit.Dp
import com.pawplay.app.games.pawmatch.Critter
import com.pawplay.app.games.pawmatch.PawPrintIcon
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
import com.pawplay.app.ui.theme.PawCoral

// ------------------------------------------------------------------ customers

private enum class Ears { POINTED, ROUND, FLOPPY, NONE }

private class Look(val head: Color, val muzzle: Color, val ear: Color, val ears: Ears, val eyeScale: Float = 1f)

/** The same six critters as Paw Match, same colours and ear families, so a child recognises them. */
private fun lookOf(critter: Critter): Look = when (critter) {
    Critter.FOX -> Look(FoxHead, FoxMuzzle, FoxEar, Ears.POINTED)
    Critter.BEAR -> Look(BearHead, BearMuzzle, BearEar, Ears.ROUND)
    Critter.BUNNY -> Look(BunnyHead, BunnyMuzzle, BunnyEar, Ears.FLOPPY)
    Critter.OWL -> Look(OwlHead, OwlMuzzle, OwlEar, Ears.ROUND, eyeScale = 1.6f)
    Critter.CAT -> Look(CatHead, CatMuzzle, CatEar, Ears.POINTED, eyeScale = 0.9f)
    Critter.FROG -> Look(FrogHead, FrogMuzzle, Color.Transparent, Ears.NONE, eyeScale = 1.3f)
}

/** The colour of a customer's head, which their paws on the counter match. */
fun customerPawColor(critter: Critter): Color = lookOf(critter).head

private val CritterRim = InkColor.copy(alpha = 0.22f)
private fun critterRim() = Stroke(width = 1.6f, join = StrokeJoin.Round)

/**
 * A customer's face on Paw Match's 80 x 80 grid, plus a mouth: a small smile
 * while waiting, a squeezed-shut, open-mouthed grin with blush when [happy].
 * The faint rim keeps the pale bunny and cat visible against the cream.
 */
@Composable
fun CustomerFace(critter: Critter, happy: Boolean, modifier: Modifier = Modifier) {
    val look = lookOf(critter)
    Canvas(modifier = modifier) {
        scale(scale = size.minDimension / 80f, pivot = Offset.Zero) {
            drawCustomer(look, happy)
        }
    }
}

private fun DrawScope.rimmed(path: Path, color: Color) {
    drawPath(path, color)
    drawPath(path, CritterRim, style = critterRim())
}

private fun DrawScope.drawCustomer(look: Look, happy: Boolean) {
    when (look.ears) {
        Ears.POINTED -> {
            rimmed(Path().apply { moveTo(20f, 30f); lineTo(27f, 8f); lineTo(35f, 28f); close() }, look.ear)
            rimmed(Path().apply { moveTo(60f, 30f); lineTo(53f, 8f); lineTo(45f, 28f); close() }, look.ear)
        }
        Ears.ROUND -> {
            circle(18f, 20f, 9f, look.ear)
            drawCircle(CritterRim, 9f, Offset(18f, 20f), style = critterRim())
            circle(62f, 20f, 9f, look.ear)
            drawCircle(CritterRim, 9f, Offset(62f, 20f), style = critterRim())
        }
        Ears.FLOPPY -> {
            ellipse(30f, 14f, 7f, 18f, -8f, look.ear, rimWidth = 1.6f, rim = CritterRim)
            ellipse(50f, 14f, 7f, 18f, 8f, look.ear, rimWidth = 1.6f, rim = CritterRim)
        }
        Ears.NONE -> Unit
    }
    drawCircle(look.head, 25f, Offset(40f, 46f))
    drawCircle(CritterRim, 25f, Offset(40f, 46f), style = critterRim())
    drawOval(look.muzzle, Offset(27f, 45f), Size(26f, 18f))

    val nose = Path().apply { moveTo(40f, 50f); lineTo(36f, 54f); lineTo(44f, 54f); close() }
    if (happy) {
        drawCircle(KitchenBlush, 4.6f, Offset(24f, 52f), alpha = 0.7f)
        drawCircle(KitchenBlush, 4.6f, Offset(56f, 52f), alpha = 0.7f)
        line("M27,44Q32,37 37,44M43,44Q48,37 53,44", InkColor, 3f)
        val mouth = svgPath("M32,56Q40,68 48,56Z")
        drawPath(mouth, PepperoniRed)
        drawPath(mouth, InkColor, style = critterRim())
        drawPath(svgPath("M36,60Q40,64 44,60Q40,58 36,60Z"), KitchenBlush)
        drawPath(nose, InkColor)
    } else {
        val r = 3.4f * look.eyeScale
        drawCircle(InkColor, r, Offset(32f, 42f))
        drawCircle(InkColor, r, Offset(48f, 42f))
        drawPath(nose, InkColor)
        line("M34,58Q40,63 46,58", InkColor, 2f)
    }
}

// ------------------------------------------------------------------ effects

/** 4-point twinkle, [size] is its radius in whatever units the scope is drawing in. */
internal fun DrawScope.sparkle(x: Float, y: Float, size: Float, color: Color, alpha: Float = 1f) {
    withTransform({ translate(x, y); scale(size, size, Offset.Zero) }) {
        drawPath(svgPath("M0,-1Q0.18,-0.18 1,0Q0.18,0.18 0,1Q-0.18,0.18 -1,0Q-0.18,-0.18 0,-1Z"), color, alpha = alpha)
    }
}

internal fun DrawScope.heart(x: Float, y: Float, size: Float, color: Color, alpha: Float = 1f) {
    withTransform({ translate(x, y); scale(size, size, Offset.Zero) }) {
        drawPath(
            svgPath("M0,0.42C-0.58,0.02 -0.5,-0.44 -0.2,-0.44C-0.08,-0.44 0,-0.36 0,-0.27C0,-0.36 0.08,-0.44 0.2,-0.44C0.5,-0.44 0.58,0.02 0,0.42Z"),
            color, alpha = alpha,
        )
    }
}

// ------------------------------------------------------------------ serve button glyph

/**
 * The serve button's picture: a dish cloche (the round metal lid). Asleep it
 * has closed-eye arcs; awake it has open eyes and a smile. No text.
 */
@Composable
fun ServeGlyph(awake: Boolean, modifier: Modifier = Modifier) {
    Canvas(modifier = modifier) {
        scale(scale = size.minDimension / 48f, pivot = Offset.Zero) {
            drawCircle(Color.White, 3.4f, Offset(24f, 9.5f))
            drawPath(svgPath("M6,33C6,18 14,12.5 24,12.5C34,12.5 42,18 42,33Z"), Color.White)
            rrect(2f, 35f, 44f, 5.5f, 2.75f, Color.White)
            if (awake) {
                drawCircle(InkColor, 2.3f, Offset(18f, 26f))
                drawCircle(InkColor, 2.3f, Offset(30f, 26f))
                line("M19.5,30Q24,34.5 28.5,30", InkColor, 2f)
            } else {
                line("M15.5,26.5Q18,29 20.5,26.5M27.5,26.5Q30,29 32.5,26.5", InkColor, 2f, alpha = 0.55f)
            }
        }
    }
}

// ------------------------------------------------------------------ home tile

/**
 * The Paw Kitchen home tile: Paw Match's bear face in a tall white chef hat,
 * with a coral paw badge in the same bottom-end spot as the other games'.
 * Everything is a fraction of [size], like the other tile icons.
 */
@Composable
fun PawKitchenTileIcon(size: Dp) {
    Box(contentAlignment = Alignment.Center, modifier = Modifier.size(size)) {
        Canvas(modifier = Modifier.size(size * 0.59f)) {
            scale(scale = this.size.minDimension / 80f, pivot = Offset.Zero) { drawChefBear() }
        }
        Box(
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .size(size * 0.155f)
                .clip(RoundedCornerShape(size * 0.055f))
                .background(PawCoral),
            contentAlignment = Alignment.Center,
        ) {
            PawPrintIcon(modifier = Modifier.size(size * 0.09f), tint = Color.White)
        }
    }
}

private fun DrawScope.drawChefBear() {
    translate(0f, 5f) {
        val look = lookOf(Critter.BEAR)
        circle(18f, 24f, 9f, look.ear)
        circle(62f, 24f, 9f, look.ear)
        circle(40f, 48f, 25f, look.head)
        drawOval(look.muzzle, Offset(27f, 47f), Size(26f, 18f))
        circle(32f, 45f, 3.4f, InkColor)
        circle(48f, 45f, 3.4f, InkColor)
        drawPath(Path().apply { moveTo(40f, 52f); lineTo(36f, 56f); lineTo(44f, 56f); close() }, InkColor)

        // Hat: a warm-grey outline first so the white hat shows on the white tile, then the white fill.
        fun hat(color: Color, style: DrawStyle) {
            drawCircle(color, 9f, Offset(29f, 14f), style = style)
            drawCircle(color, 10f, Offset(40f, 9f), style = style)
            drawCircle(color, 9f, Offset(51f, 14f), style = style)
            drawRoundRect(color, Offset(27f, 15f), Size(26f, 16f), CornerRadius(4f), style = style)
        }
        hat(KitchenPlateRim, Fill)
        hat(KitchenPlateRim, Stroke(width = 5f, join = StrokeJoin.Round))
        hat(Color.White, Fill)
        rrect(27f, 26f, 26f, 5f, 2f, PawCoral)
    }
}
