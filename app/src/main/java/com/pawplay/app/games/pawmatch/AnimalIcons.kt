package com.pawplay.app.games.pawmatch

import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.rotate
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
import com.pawplay.app.ui.theme.PawSunshine

/**
 * Every icon here is drawn, not imported — no image assets, no icon-font
 * dependency (docs/ARCHITECTURE.md's zero-extra-dependency default). All
 * coordinates are authored against an 80x80 (or 24x24 for badges) design
 * grid and scaled to whatever size the Canvas is given, so the same
 * drawing code works for a small card and a large home tile.
 */

private enum class EarShape { POINTED, ROUND, FLOPPY, NONE }

@Composable
private fun CritterFace(
    modifier: Modifier,
    headColor: Color,
    muzzleColor: Color,
    earColor: Color,
    earShape: EarShape,
    eyeScale: Float = 1f,
) {
    Canvas(modifier = modifier) {
        val w = size.width
        val h = size.height
        fun px(x: Float) = x / 80f * w
        fun py(y: Float) = y / 80f * h

        when (earShape) {
            EarShape.POINTED -> {
                drawPath(
                    Path().apply {
                        moveTo(px(20f), py(30f)); lineTo(px(27f), py(8f)); lineTo(px(35f), py(28f)); close()
                    },
                    color = earColor,
                )
                drawPath(
                    Path().apply {
                        moveTo(px(60f), py(30f)); lineTo(px(53f), py(8f)); lineTo(px(45f), py(28f)); close()
                    },
                    color = earColor,
                )
            }
            EarShape.ROUND -> {
                drawCircle(color = earColor, radius = px(9f), center = Offset(px(18f), py(20f)))
                drawCircle(color = earColor, radius = px(9f), center = Offset(px(62f), py(20f)))
            }
            EarShape.FLOPPY -> {
                rotate(degrees = -8f, pivot = Offset(px(30f), py(14f))) {
                    drawOval(
                        color = earColor,
                        topLeft = Offset(px(30f) - px(7f), py(14f) - py(18f)),
                        size = Size(px(14f), py(36f)),
                    )
                }
                rotate(degrees = 8f, pivot = Offset(px(50f), py(14f))) {
                    drawOval(
                        color = earColor,
                        topLeft = Offset(px(50f) - px(7f), py(14f) - py(18f)),
                        size = Size(px(14f), py(36f)),
                    )
                }
            }
            EarShape.NONE -> Unit
        }

        drawCircle(color = headColor, radius = px(25f), center = Offset(px(40f), py(46f)))
        drawOval(
            color = muzzleColor,
            topLeft = Offset(px(40f) - px(13f), py(54f) - py(9f)),
            size = Size(px(26f), py(18f)),
        )
        drawCircle(color = InkColor, radius = px(3.4f) * eyeScale, center = Offset(px(32f), py(42f)))
        drawCircle(color = InkColor, radius = px(3.4f) * eyeScale, center = Offset(px(48f), py(42f)))
        drawPath(
            Path().apply {
                moveTo(px(40f), py(50f)); lineTo(px(36f), py(54f)); lineTo(px(44f), py(54f)); close()
            },
            color = InkColor,
        )
    }
}

@Composable
fun FoxIcon(modifier: Modifier = Modifier) =
    CritterFace(modifier, headColor = FoxHead, muzzleColor = FoxMuzzle, earColor = FoxEar, earShape = EarShape.POINTED)

@Composable
fun BearIcon(modifier: Modifier = Modifier) =
    CritterFace(modifier, headColor = BearHead, muzzleColor = BearMuzzle, earColor = BearEar, earShape = EarShape.ROUND)

@Composable
fun BunnyIcon(modifier: Modifier = Modifier) =
    CritterFace(modifier, headColor = BunnyHead, muzzleColor = BunnyMuzzle, earColor = BunnyEar, earShape = EarShape.FLOPPY)

@Composable
fun OwlIcon(modifier: Modifier = Modifier) =
    CritterFace(
        modifier,
        headColor = OwlHead,
        muzzleColor = OwlMuzzle,
        earColor = OwlEar,
        earShape = EarShape.ROUND,
        eyeScale = 1.6f,
    )

@Composable
fun CatIcon(modifier: Modifier = Modifier) =
    CritterFace(
        modifier,
        headColor = CatHead,
        muzzleColor = CatMuzzle,
        earColor = CatEar,
        earShape = EarShape.POINTED,
        eyeScale = 0.9f,
    )

@Composable
fun FrogIcon(modifier: Modifier = Modifier) =
    CritterFace(
        modifier,
        headColor = FrogHead,
        muzzleColor = FrogMuzzle,
        earColor = Color.Transparent,
        earShape = EarShape.NONE,
        eyeScale = 1.3f,
    )

@Composable
fun CritterIcon(critter: Critter, modifier: Modifier = Modifier) {
    when (critter) {
        Critter.FOX -> FoxIcon(modifier)
        Critter.BEAR -> BearIcon(modifier)
        Critter.BUNNY -> BunnyIcon(modifier)
        Critter.OWL -> OwlIcon(modifier)
        Critter.CAT -> CatIcon(modifier)
        Critter.FROG -> FrogIcon(modifier)
    }
}

/** The face-down card back, and the "play again" button — same mark, both spots. */
@Composable
fun PawPrintIcon(modifier: Modifier = Modifier, tint: Color) {
    Canvas(modifier = modifier) {
        val w = size.width
        val h = size.height
        fun px(x: Float) = x / 80f * w
        fun py(y: Float) = y / 80f * h

        drawOval(color = tint, topLeft = Offset(px(20f), py(36f)), size = Size(px(40f), py(32f)))
        drawOval(color = tint, topLeft = Offset(px(10f), py(20f)), size = Size(px(16f), py(20f)))
        drawOval(color = tint, topLeft = Offset(px(26f), py(8f)), size = Size(px(16f), py(20f)))
        drawOval(color = tint, topLeft = Offset(px(44f), py(8f)), size = Size(px(16f), py(20f)))
        drawOval(color = tint, topLeft = Offset(px(56f), py(22f)), size = Size(px(16f), py(20f)))
    }
}

/** The win-screen celebration mark. */
@Composable
fun StarBadgeIcon(modifier: Modifier = Modifier, tint: Color = PawSunshine) {
    Canvas(modifier = modifier) {
        val w = size.width
        val h = size.height
        fun px(x: Float) = x / 24f * w
        fun py(y: Float) = y / 24f * h

        val path = Path().apply {
            moveTo(px(12f), py(2f))
            lineTo(px(14.5f), py(9f))
            lineTo(px(22f), py(9.5f))
            lineTo(px(16f), py(14.5f))
            lineTo(px(18f), py(22f))
            lineTo(px(12f), py(17.5f))
            lineTo(px(6f), py(22f))
            lineTo(px(8f), py(14.5f))
            lineTo(px(2f), py(9.5f))
            lineTo(px(9.5f), py(9f))
            close()
        }
        drawPath(path, color = tint)
    }
}

/**
 * The exit-to-home glyph, used inside every game (docs/DESIGN-SYSTEM.md). The door is a notch cut
 * out of the house, so whatever button colour is behind the icon shows through it — on the white
 * exit button and on the coloured win-screen button alike.
 */
@Composable
fun HomeGlyphIcon(modifier: Modifier = Modifier, tint: Color = InkColor) {
    Canvas(modifier = modifier) {
        val w = size.width
        val h = size.height
        fun px(x: Float) = x / 24f * w
        fun py(y: Float) = y / 24f * h

        val house = Path().apply {
            moveTo(px(12f), py(3f))
            lineTo(px(21f), py(11f))
            lineTo(px(18f), py(11f))
            lineTo(px(18f), py(20f))
            lineTo(px(13.5f), py(20f))
            lineTo(px(13.5f), py(13f))
            lineTo(px(10.5f), py(13f))
            lineTo(px(10.5f), py(20f))
            lineTo(px(6f), py(20f))
            lineTo(px(6f), py(11f))
            lineTo(px(3f), py(11f))
            close()
        }
        drawPath(house, color = tint)
    }
}
