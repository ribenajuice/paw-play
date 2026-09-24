package com.pawplay.app.games.pawpour

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.pawplay.app.ui.theme.PawCardWhite
import com.pawplay.app.ui.theme.PawLeaf
import com.pawplay.app.ui.theme.PawSky
import com.pawplay.app.ui.theme.PawSunshine
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.min
import kotlin.math.sin

/** How far a selected tube lifts (docs/DESIGN-SYSTEM.md); the layout leaves this much headroom. */
internal val SELECT_LIFT = LIFT_HEADROOM.dp

private const val WOBBLE_MS = 320
private const val LIFT_MS = 180

/**
 * The white glass tube (docs/DESIGN-SYSTEM.md, "Paw Pour: tubes, bands and marks"). All the tube
 * chrome measurements are in "design units" of [scale] dp each (1 for the play board; smaller for
 * the home tile icon), so one drawing serves both.
 *
 * The top [partialCount] bands are drawn at [partialFraction] of their height, which is what the
 * pour and un-pour animations use to drain one tube and fill another.
 */
@Composable
internal fun TubeCanvas(
    bands: List<BandColor>,
    capacity: Int,
    width: Dp,
    bandHeight: Dp,
    modifier: Modifier = Modifier,
    scale: Float = 1f,
    edge: Color = PawSunshine,
    glow: Boolean = false,
    showMarks: Boolean = true,
    partialCount: Int = 0,
    partialFraction: Float = 1f,
) {
    Canvas(modifier = modifier.size(width, tubeHeightDp(capacity, bandHeight, scale))) {
        drawTube(bands, bandHeight.toPx(), scale * density, edge, glow, showMarks, partialCount, partialFraction)
    }
}

internal fun tubeHeightDp(capacity: Int, bandHeight: Dp, scale: Float = 1f): Dp =
    bandHeight * capacity + (TUBE_CHROME_HEIGHT * scale).dp

private fun DrawScope.drawTube(
    bands: List<BandColor>,
    bandPx: Float,
    unit: Float, // px per design unit
    edge: Color,
    glow: Boolean,
    showMarks: Boolean,
    partialCount: Int,
    partialFraction: Float,
) {
    val w = size.width
    val h = size.height
    val border = 3f * unit
    val bodyLeft = 3f * unit
    val bodyTop = 4f * unit
    val bodyWidth = w - 6f * unit
    val bodyHeight = h - bodyTop - 1f * unit
    val bottomRadius = bodyWidth * 0.4f // 40% of the body width (design system)

    fun roundedBody(left: Float, top: Float, width: Float, height: Float, topR: Float, bottomR: Float) = Path().apply {
        addRoundRect(
            RoundRect(
                left, top, left + width, top + height,
                topLeftCornerRadius = CornerRadius(topR), topRightCornerRadius = CornerRadius(topR),
                bottomLeftCornerRadius = CornerRadius(bottomR), bottomRightCornerRadius = CornerRadius(bottomR),
            ),
        )
    }

    val outer = roundedBody(bodyLeft, bodyTop, bodyWidth, bodyHeight, 10f * unit, bottomRadius)
    val innerLeft = bodyLeft + border
    val innerTop = bodyTop + border
    val innerWidth = bodyWidth - 2f * border
    val innerHeight = bodyHeight - 2f * border
    val inner = roundedBody(innerLeft, innerTop, innerWidth, innerHeight, 6f * unit, bottomRadius - border)
    val innerBottom = innerTop + innerHeight

    if (glow) {
        // A soft sky glow, faked with a few widening, fading outlines (no blur APIs needed).
        for (ring in 4 downTo 1) {
            drawPath(outer, PawSky.copy(alpha = 0.13f * (5 - ring)), style = Stroke(width = ring * 5f * unit))
        }
    }
    drawPath(outer, edge)
    drawPath(inner, PawCardWhite)

    clipPath(inner) {
        var top = innerBottom
        bands.forEachIndexed { index, band ->
            val fromTop = bands.size - 1 - index
            val bandHeight = if (fromTop < partialCount) bandPx * partialFraction else bandPx
            top -= bandHeight
            val style = band.style()
            drawRect(style.color, Offset(innerLeft, top), Size(innerWidth, bandHeight))
            if (index > 0) {
                drawRect(
                    Color.White.copy(alpha = 0.55f),
                    Offset(innerLeft, top + bandHeight - 1f * unit),
                    Size(innerWidth, 2f * unit),
                )
            }
            if (showMarks && bandHeight > 6f * unit) {
                drawMark(style.mark, innerLeft + innerWidth / 2f, top + bandHeight / 2f, min(innerWidth, bandHeight) * 0.6f, style.markColor)
            }
        }
        if (bands.isNotEmpty()) {
            drawRoundRect(
                Color.White.copy(alpha = 0.38f),
                Offset(innerLeft + 5f * unit, innerTop + 10f * unit),
                Size(4f * unit, innerHeight * 0.5f),
                CornerRadius(2f * unit),
            )
        }
    }
    // The lip: slightly wider than the body, open top.
    drawRoundRect(edge, Offset.Zero, Size(w, 8f * unit), CornerRadius(4f * unit))
}

/** A four-point sparkle in a unit box, drawn scaled. */
private val sparklePath: Path by lazy {
    Path().apply {
        moveTo(0f, -1f); quadraticBezierTo(0.18f, -0.18f, 1f, 0f); quadraticBezierTo(0.18f, 0.18f, 0f, 1f)
        quadraticBezierTo(-0.18f, 0.18f, -1f, 0f); quadraticBezierTo(-0.18f, -0.18f, 0f, -1f); close()
    }
}

/**
 * One tappable tube on the board. The whole box (lip to base) is the tap area and never moves,
 * even when the tube lifts, so a lifted tube is still easy to hit again. Border colour is the
 * state: sunshine resting, sky selected, leaf finished.
 */
@Composable
internal fun PourTube(
    bands: List<BandColor>,
    capacity: Int,
    width: Dp,
    bandHeight: Dp,
    raised: Boolean,
    finished: Boolean,
    partialCount: Int,
    partialFraction: Float,
    tiltDegrees: Float,
    wobbleNonce: Int,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val lift by animateDpAsState(if (raised) SELECT_LIFT else 0.dp, tween(LIFT_MS), label = "tube-lift")
    val wobble = remember { Animatable(1f) }
    LaunchedEffect(wobbleNonce) {
        if (wobbleNonce > 0) {
            wobble.snapTo(0f)
            wobble.animateTo(1f, tween(WOBBLE_MS, easing = LinearEasing))
        }
    }
    val sparkle = remember { Animatable(if (finished) 1f else 0f) }
    LaunchedEffect(finished) {
        if (finished) sparkle.animateTo(1f, spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessLow))
        else sparkle.snapTo(0f)
    }

    val tubeHeight = tubeHeightDp(capacity, bandHeight)
    val edge = when {
        finished -> PawLeaf
        raised -> PawSky
        else -> PawSunshine
    }
    Box(
        modifier = modifier
            .size(width, tubeHeight)
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                role = Role.Button,
                onClick = onClick,
            )
            .semantics { contentDescription = "Tube" },
    ) {
        TubeCanvas(
            bands = bands,
            capacity = capacity,
            width = width,
            bandHeight = bandHeight,
            edge = edge,
            glow = raised,
            partialCount = partialCount,
            partialFraction = partialFraction,
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer {
                    translationY = -lift.toPx()
                    // A tiny side-to-side shake that dies away; nothing else about a wrong tap.
                    translationX = sin(wobble.value * 3f * 2f * PI.toFloat()) * (1f - wobble.value) * 6.dp.toPx()
                    rotationZ = tiltDegrees
                    transformOrigin = TransformOrigin(0.5f, 0f)
                },
        )
        if (sparkle.value > 0f) {
            Canvas(modifier = Modifier.fillMaxSize()) {
                val s = sparkle.value
                // Three small sunshine sparkles around the rim and sides, popping in.
                listOf(Triple(-4f, 26f, 9f), Triple(size.width / density + 2f, 58f, 7f), Triple(size.width / density - 4f, 6f, 8f))
                    .forEach { (x, y, r) ->
                        translate(left = x * density, top = y * density) {
                            scale(scaleX = r * density * s, scaleY = r * density * s, pivot = Offset.Zero) {
                                drawPath(sparklePath, PawSunshine)
                            }
                        }
                    }
            }
        }
    }
}

/** 0 at both ends, 1 in the middle: the shape of a tilt or a stream over a pour. */
internal fun bell(progress: Float): Float = abs(sin(progress * PI.toFloat()))
