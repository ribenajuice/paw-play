package com.pawplay.app.games.pawpop

import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Paint
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.vector.PathParser
import com.pawplay.app.ui.theme.BearEar
import com.pawplay.app.ui.theme.BearHead
import com.pawplay.app.ui.theme.BearMuzzle
import com.pawplay.app.ui.theme.FoxEar
import com.pawplay.app.ui.theme.FoxHead
import com.pawplay.app.ui.theme.FoxMuzzle
import com.pawplay.app.ui.theme.InkColor
import com.pawplay.app.ui.theme.PawCoral
import com.pawplay.app.ui.theme.PawSunshine
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

// Everything Paw Pop draws, as vectors, in local units around a centre and scaled to size (docs/DESIGN-SYSTEM.md,
// "Paw Pop"). Paths and strokes are built once here, never per frame. The mockup's stroke widths are constant in dp
// however big the picture is (`vector-effect`), so rims that must stay 3dp or 2dp come from `SizedStroke`.

internal fun svg(d: String): Path = PathParser().parsePathString(d).toPath()

/** Strokes that stay [dp] wide on screen when drawn inside a `scale(size / 100)`, made once per whole-dp size. */
internal class SizedStroke(private val dp: Float, private val cap: StrokeCap = StrokeCap.Butt, private val dash: FloatArray? = null) {
    private val made = arrayOfNulls<Stroke>(80)
    fun at(size: Float): Stroke {
        val i = (size.toInt() - 40).coerceIn(0, 79)
        return made[i] ?: Stroke(dp * 100f / (i + 40), cap = cap, join = StrokeJoin.Round, pathEffect = dash?.let { PathEffect.dashPathEffect(it) }).also { made[i] = it }
    }
}

private val Rim3 = SizedStroke(3f)
/** The rim of a target still coming in above the entry line: dashed, 9 on and 7 off in the target's own 100-unit grid, so it scales with the target. */
private val Rim3Veiled = SizedStroke(3f, dash = floatArrayOf(9f, 7f))
private val Rim2 = SizedStroke(2f)
private val Str16 = SizedStroke(1.6f, StrokeCap.Round)
private val Str18 = SizedStroke(1.8f, StrokeCap.Round)
private val Str26 = SizedStroke(2.6f, StrokeCap.Round)
private val GiftRing = SizedStroke(2f)

private val BodyRim = InkColor.copy(alpha = 0.45f)
private val StringInk = InkColor.copy(alpha = 0.5f)
private val CritterRim = InkColor.copy(alpha = 0.4f)
private val White55 = Color.White.copy(alpha = 0.55f)
private val White50 = Color.White.copy(alpha = 0.5f)
private val White60 = Color.White.copy(alpha = 0.6f)
private val Paper95 = PopPaper.copy(alpha = 0.95f)

/** One paint for every group-opacity layer (a fading target, a pop, a ghost gift). Drawing is single-threaded. */
internal val LayerPaint = Paint()

/**
 * Layer bounds for [withGroupAlpha] without making a new `Rect` every frame: the box is snapped to a grid of 8dp and
 * remembered in a small ring, so a target drifting across the screen reuses one every few frames. Always at least
 * [reach] dp each side of the point asked for.
 */
internal object LayerBounds {
    private const val SLOTS = 64
    private val keys = IntArray(SLOTS) { -1 }
    private val rects = arrayOfNulls<Rect>(SLOTS)
    private var next = 0

    fun around(cx: Float, cy: Float, reach: Float): Rect {
        val qx = ((cx + 128f) / 8f).toInt().coerceIn(0, 1023)
        val qy = ((cy + 128f) / 8f).toInt().coerceIn(0, 1023)
        val qr = kotlin.math.ceil(reach / 8f).toInt().coerceIn(0, 255)
        val key = qx or (qy shl 10) or (qr shl 20)
        for (i in 0 until SLOTS) if (keys[i] == key) return rects[i]!!
        val half = qr * 8f + 8f // the snapped centre is at most 4dp from the real one
        val x = qx * 8f - 128f + 4f
        val y = qy * 8f - 128f + 4f
        val r = Rect(x - half, y - half, x + half, y + half)
        keys[next] = key
        rects[next] = r
        next = (next + 1) % SLOTS
        return r
    }
}

/** Draws [block] as one picture at [alpha] (a plain draw when it is opaque, so the common frame costs nothing extra). */
internal inline fun DrawScope.withGroupAlpha(alpha: Float, cx: Float, cy: Float, reach: Float, block: DrawScope.() -> Unit) {
    if (alpha >= 0.995f) {
        block()
        return
    }
    drawIntoCanvas { c ->
        LayerPaint.alpha = alpha
        c.saveLayer(LayerBounds.around(cx, cy, reach), LayerPaint)
    }
    block()
    drawIntoCanvas { it.restore() }
}

// ------------------------------------------------------------------ sparkle and star

private val SparklePath = svg("M0,-1 Q0.18,-0.18 1,0 Q0.18,0.18 0,1 Q-0.18,0.18 -1,0 Q-0.18,-0.18 0,-1Z")
private val SparkleRimStroke = Stroke(0.13f, join = StrokeJoin.Round)
private val SparkleRim = PopSparkleRim.copy(alpha = 0.55f)

/** A four-point sparkle of radius [r] at ([x], [y]). Grows and shrinks smoothly, never blinks. */
internal fun DrawScope.drawSparkle(x: Float, y: Float, r: Float, fill: Color, alpha: Float = 1f) {
    if (r < 0.4f) return
    translate(x, y) {
        scale(r, r, Offset.Zero) {
            drawPath(SparklePath, fill, alpha)
            drawPath(SparklePath, SparkleRim, alpha, SparkleRimStroke)
        }
    }
}

private val StarPath: Path = Path().apply {
    for (i in 0 until 10) {
        val r = if (i % 2 == 1) 22f else 50f
        val a = -PI.toFloat() / 2f + i * PI.toFloat() / 5f
        val x = cos(a) * r
        val y = sin(a) * r
        if (i == 0) moveTo(x, y) else lineTo(x, y)
    }
    close()
}
private val StarRim14 = Stroke(2f * 50f / 14f, join = StrokeJoin.Round)   // 2dp on the 28dp star
private val StarRim28 = Stroke(2f * 50f / 28f, join = StrokeJoin.Round)   // still 2dp on the 56dp star
private val StarRimFixed = Stroke(7.14f, join = StrokeJoin.Round)          // proportional, for stars inside a gift picture
private val StarShine = White55

/** The shot: a sunshine star with a rim and a small shine, outer radius [r] (14 for 28dp across), tilted [degrees]. */
internal fun DrawScope.drawStar(x: Float, y: Float, r: Float, degrees: Float, alpha: Float = 1f, rim: Stroke = if (r > 20f) StarRim28 else StarRim14) {
    translate(x, y) {
        rotate(degrees, Offset.Zero) {
            scale(r / 50f, r / 50f, Offset.Zero) {
                drawPath(StarPath, PawSunshine, alpha)
                drawPath(StarPath, PopStarRim, alpha, rim)
                translate(-7f, -7f) { scale(0.4f, 0.4f, Offset.Zero) { drawPath(StarPath, StarShine, alpha) } }
            }
        }
    }
}

// ------------------------------------------------------------------ the five silhouettes

private val OvalKnot = svg(PopPaths.OVAL_KNOT)
private val OvalString = svg(PopPaths.OVAL_STRING)
private val HeartBody = svg(PopPaths.HEART)
private val HeartKnot = svg(PopPaths.HEART_KNOT)
private val HeartString = svg(PopPaths.HEART_STRING)
private val MoonBody = svg(PopPaths.MOON)
private val MoonKnot = svg(PopPaths.MOON_KNOT)
private val MoonString = svg(PopPaths.MOON_STRING)

private val FoxEars = PopPaths.FOX_EARS.map { pts -> Path().apply { moveTo(pts[0].first, pts[0].second); lineTo(pts[1].first, pts[1].second); lineTo(pts[2].first, pts[2].second); close() } }
private val FrogGreen = Color(0xFF7FBF6B)
private val CritterHeads = listOf(FoxHead, BearHead, Color(0xFFF1E8DD), FrogGreen)
private val CritterMuzzles = listOf(FoxMuzzle, BearMuzzle, Color.White, Color(0xFFDFF2D6))
private val BunnyEarOuter = Color(0xFFEDE3D8)
private val BunnyEarInner = Color(0xFFFFB8CC)
private val BunnyNose = PopBlush
private val NosePath = Path().apply { moveTo(0f, 10f); lineTo(-4f, 15f); lineTo(4f, 15f); close() }
private val SmilePath = svg("M-6,19 Q0,24 6,19")
private val OpenMouthPath = svg("M-7,18 Q0,28 7,18Z")
private val HappyEyesPath = svg("M-14,3 Q-9,-4 -4,3 M4,3 Q9,-4 14,3")
private val FrogHappyEyesPath = svg("M-31,-44 Q-24,-52 -17,-44 M17,-44 Q24,-52 31,-44")

/** Ears, eye bumps: the parts that poke out of the bubble, drawn first so the silhouette is not a plain circle. */
private fun DrawScope.critterEars(critter: Int, size: Float) {
    val rim = Rim2.at(size)
    when (critter) {
        0 -> for (i in FoxEars.indices) { drawPath(FoxEars[i], FoxEar); drawPath(FoxEars[i], CritterRim, style = rim) }
        1 -> for (i in PopPaths.BEAR_EARS.indices) {
            val e = PopPaths.BEAR_EARS[i]
            drawCircle(BearEar, e.third, Offset(e.first, e.second)); drawCircle(CritterRim, e.third, Offset(e.first, e.second), style = rim)
        }
        2 -> for (i in PopPaths.BUNNY_EARS.indices) PopPaths.BUNNY_EARS[i].let { e -> rotate(e[4], Offset(e[0], e[1])) {
            drawOval(BunnyEarOuter, Offset(e[0] - e[2], e[1] - e[3]), Size(2 * e[2], 2 * e[3]))
            drawOval(CritterRim, Offset(e[0] - e[2], e[1] - e[3]), Size(2 * e[2], 2 * e[3]), style = rim)
            drawOval(BunnyEarInner, Offset(e[0] - 4.5f, e[1] + 2f - 18f), Size(9f, 36f))
        } }
        else -> for (i in PopPaths.FROG_BUMPS.indices) {
            val e = PopPaths.FROG_BUMPS[i]
            drawCircle(FrogGreen, e.third, Offset(e.first, e.second)); drawCircle(CritterRim, e.third, Offset(e.first, e.second), style = rim)
        }
    }
}

/** The face: head, muzzle, eyes, nose, mouth. [happy] is the pop's squeezed-shut arches and open smile. */
private fun DrawScope.critterFace(critter: Int, size: Float, happy: Boolean) {
    val rim = Rim2.at(size)
    drawCircle(CritterHeads[critter], 26f, Offset(0f, 6f))
    drawCircle(CritterRim, 26f, Offset(0f, 6f), style = rim)
    drawOval(CritterMuzzles[critter], Offset(-13f, 7f), Size(26f, 18f))
    if (critter != 3) {
        if (happy) drawPath(HappyEyesPath, InkColor, style = Str26.at(size))
        else {
            drawCircle(InkColor, 3.4f, Offset(-9f, 2f)); drawCircle(InkColor, 3.4f, Offset(9f, 2f))
        }
        drawPath(NosePath, if (critter == 2) BunnyNose else InkColor)
    } else {
        drawCircle(InkColor, 1.6f, Offset(-5f, 9f)); drawCircle(InkColor, 1.6f, Offset(5f, 9f))
    }
    if (happy) {
        drawPath(OpenMouthPath, PopMouth)
        drawCircle(PopBlush.copy(alpha = 0.7f), 4f, Offset(-17f, 14f)); drawCircle(PopBlush.copy(alpha = 0.7f), 4f, Offset(17f, 14f))
    } else {
        drawPath(SmilePath, InkColor, style = Str18.at(size))
    }
}

private fun DrawScope.frogEyes(size: Float, happy: Boolean) {
    if (happy) {
        drawPath(FrogHappyEyesPath, InkColor, style = Str26.at(size))
    } else {
        drawCircle(Color.White, 8f, Offset(-24f, -44f)); drawCircle(Color.White, 8f, Offset(24f, -44f))
        drawCircle(InkColor, 4f, Offset(-24f, -43f)); drawCircle(InkColor, 4f, Offset(24f, -43f))
    }
}

/**
 * One target's body in local units (call inside `translate(cx, cy) { scale(size / 100) { ... } }`), filled with [fill],
 * with the 3dp rim that carries the lightest colours against the sky. Order back to front: string, knot, body, shine.
 */
internal fun DrawScope.targetBody(kind: TargetKind, fill: Color, size: Float, critter: Int, happy: Boolean = false, veiled: Boolean = false) {
    val rim = if (veiled) Rim3Veiled.at(size) else Rim3.at(size)
    when (kind) {
        TargetKind.ROUND -> {
            drawCircle(fill, 50f, Offset.Zero)
            drawCircle(BodyRim, 50f, Offset.Zero, style = rim)
            rotate(-38f, Offset(-20f, -25f)) { drawOval(White55, Offset(-35f, -33f), Size(30f, 16f)) }
            drawCircle(White50, 3.6f, Offset(-34f, -4f))
        }
        TargetKind.OVAL -> {
            drawPath(OvalString, StringInk, style = Str16.at(size))
            drawPath(OvalKnot, fill); drawPath(OvalKnot, BodyRim, style = rim)
            drawOval(fill, Offset(-50f, -62f), Size(100f, 124f))
            drawOval(BodyRim, Offset(-50f, -62f), Size(100f, 124f), style = rim)
            rotate(20f, Offset(-22f, -30f)) { drawOval(White50, Offset(-32f, -52f), Size(20f, 44f)) }
        }
        TargetKind.HEART -> {
            drawPath(HeartString, StringInk, style = Str16.at(size))
            drawPath(HeartKnot, fill); drawPath(HeartKnot, BodyRim, style = rim)
            drawPath(HeartBody, fill); drawPath(HeartBody, BodyRim, style = rim)
            rotate(-35f, Offset(-27f, -30f)) { drawOval(White55, Offset(-37f, -35f), Size(20f, 10f)) }
        }
        TargetKind.MOON -> {
            drawPath(MoonString, StringInk, style = Str16.at(size))
            drawPath(MoonKnot, fill); drawPath(MoonKnot, BodyRim, style = rim)
            drawPath(MoonBody, fill); drawPath(MoonBody, BodyRim, style = rim)
            drawOval(White50, Offset(-43.5f, -29f), Size(11f, 34f))
        }
        TargetKind.CRITTER -> {
            critterEars(critter, size)
            drawCircle(fill, 50f, Offset.Zero)
            drawCircle(BodyRim, 50f, Offset.Zero, style = rim)
            drawCircle(Paper95, 40f, Offset.Zero)
            critterFace(critter, size, happy)
            if (critter == 3) frogEyes(size, happy)
            rotate(-35f, Offset(-31f, -33f)) { drawOval(White60, Offset(-40f, -37.5f), Size(18f, 9f)) }
        }
    }
}

/** The free animal a critter pop leaves: ears at 0.8, face at 1.4 shifted up 6, happy (or [happy] false: the calm smile), no bubble. Local units. */
internal fun DrawScope.freeCritter(critter: Int, size: Float, happy: Boolean = true) {
    scale(0.8f, 0.8f, Offset.Zero) { critterEars(critter, size) }
    scale(1.4f, 1.4f, Offset.Zero) {
        translate(0f, -6f) {
            critterFace(critter, size, happy)
            if (critter == 3) frogEyes(size, happy)
        }
    }
}

/** The good-game screen's animal: one of Pop's four critters filling the canvas, with a calm smile or (a new best) the happy face. */
internal fun DrawScope.goodGameCritter(critter: Int, happy: Boolean) {
    val unit = size.minDimension / 115f
    translate(size.width / 2f, size.height * 0.62f) { scale(unit, unit, Offset.Zero) { freeCritter(critter, 110f, happy) } }
}

/** The two soft lilac ellipses behind a target while slow drift runs: its hit ellipse grown by 0.18 S and 0.09 S. */
internal fun DrawScope.slowHalo(kind: TargetKind) {
    val cx = kind.dx * 100f
    val cy = kind.dy * 100f
    var rx = kind.rx * 100f + 18f
    var ry = kind.ry * 100f + 18f
    drawOval(PopLilac.copy(alpha = 0.16f), Offset(cx - rx, cy - ry), Size(2 * rx, 2 * ry))
    rx -= 9f; ry -= 9f
    drawOval(PopLilac.copy(alpha = 0.26f), Offset(cx - rx, cy - ry), Size(2 * rx, 2 * ry))
}

/**
 * A whole target at ([cx], [cy]): [size] dp across, [alpha] as one group, [grow] a scale on top (the pop's swell),
 * with the lilac halo when [halo], and the gift inside when it is a carrier ([giftT] is its own clock for the bob and swell).
 */
internal fun DrawScope.drawTarget(
    kind: TargetKind, colour: Int, critter: Int, size: Float, cx: Float, cy: Float,
    alpha: Float = 1f, grow: Float = 1f, halo: Boolean = false, gift: GiftKind? = null, giftT: Float = 0f, happy: Boolean = false,
    veiled: Boolean = false,
) {
    withGroupAlpha(alpha, cx, cy, size * 1.4f) {
        translate(cx, cy) {
            scale(size / 100f * grow, size / 100f * grow, Offset.Zero) {
                if (halo) slowHalo(kind)
                targetBody(kind, TargetColors[colour], size, critter, happy, veiled)
                if (gift != null) carrierGift(gift, size, giftT)
            }
        }
    }
}

private val TwoPi = (2 * PI).toFloat()

/** The gift shown inside a carrier: a sunshine halo swelling once every 1.6 s, a white disc, and the picture bobbing. */
private fun DrawScope.carrierGift(gift: GiftKind, size: Float, t: Float) {
    val phase = TwoPi * t / 1.6f
    val bob = 2.5f * sin(phase)
    val swell = 0.5f + 0.5f * sin(phase + 1f)
    drawCircle(PawSunshine.copy(alpha = 0.10f + 0.14f * swell), 44f, Offset.Zero)
    drawCircle(PawSunshine.copy(alpha = 0.18f + 0.20f * swell), 39f, Offset.Zero)
    drawCircle(Color.White, 34f, Offset.Zero)
    drawCircle(PawSunshine, 34f, Offset.Zero, style = GiftRing.at(size))
    translate(-25f, -25f + bob) { scale(0.5f, 0.5f, Offset.Zero) { giftPicture(gift) } }
}

// ------------------------------------------------------------------ the five gift pictures (100 grid)

private val GiftRim = Stroke(4f, join = StrokeJoin.Round)          // 2dp at the 0.5 S scale of a carrier
private val GiftRimInk = InkColor.copy(alpha = 0.4f)
private val RibbonArcs = listOf(36f, 28f, 20f, 12f).map { r -> svg("M${50 - r},74 A$r,$r 0 0 1 ${50 + r},74") }
private val RibbonColours = listOf(Color(0xFFFF7A70), PawSunshine, Color(0xFF6FD08C), Color(0xFF4FC1E9))
private val RibbonUnder = Stroke(10.5f, cap = StrokeCap.Round)
private val RibbonStripe = Stroke(8f, cap = StrokeCap.Round)
private val Ink30 = InkColor.copy(alpha = 0.3f)
private val SnailBody = Color(0xFFF6B57A)
private val SnailShell = Color(0xFF9B6BE0)
private val SnailStalks = svg("M16,47 L13,33 M26,46 L29,32")
private val SnailStalk = Stroke(4f, cap = StrokeCap.Round)
private val SnailSpiral = svg("M58,52 c0,-4 6,-4 6,0 c0,8 -12,8 -12,0 c0,-12 18,-12 18,0 c0,15 -24,15 -24,0")
private val SnailSpiralStroke = Stroke(3f, cap = StrokeCap.Round)
private val SnailSmile = svg("M15,60 Q20,64 25,60")
private val SnailSmileStroke = Stroke(1.8f, cap = StrokeCap.Round)
private val WaveIcon = svg("M8,64 C18,44 30,44 40,64 C50,84 62,84 72,64 C77,54 84,50 92,52")
private val WaveIconUnder = Stroke(13f, cap = StrokeCap.Round)
private val WaveIconStroke = Stroke(9f, cap = StrokeCap.Round)
private val WaveBlue = Color(0xFF28A9E3)

/** One gift's picture on a 100 x 100 grid, centred on (50, 50). Each is a different shape in greyscale. */
internal fun DrawScope.giftPicture(kind: GiftKind) {
    when (kind) {
        GiftKind.TRIPLE -> {
            drawStar(50f, 30f, 25f, 0f, rim = StarRimFixed)
            drawStar(22f, 66f, 19f, -14f, rim = StarRimFixed)
            drawStar(78f, 66f, 19f, 14f, rim = StarRimFixed)
        }
        GiftKind.BIG -> {
            drawStar(50f, 52f, 44f, 0f, rim = StarRimFixed)
            drawSparkle(86f, 16f, 9f, PawSunshine)
            drawSparkle(14f, 86f, 6f, PawSunshine)
        }
        GiftKind.RIBBON -> {
            for (i in RibbonArcs.indices) drawPath(RibbonArcs[i], Ink30, style = RibbonUnder)
            for (i in RibbonArcs.indices) drawPath(RibbonArcs[i], RibbonColours[i], style = RibbonStripe)
        }
        GiftKind.SLOW -> {
            drawRoundRect(SnailBody, Offset(12f, 62f), Size(78f, 15f), CornerRadius(7.5f))
            drawRoundRect(GiftRimInk, Offset(12f, 62f), Size(78f, 15f), CornerRadius(7.5f), style = GiftRim)
            drawCircle(SnailBody, 11f, Offset(20f, 56f)); drawCircle(GiftRimInk, 11f, Offset(20f, 56f), style = GiftRim)
            drawPath(SnailStalks, SnailBody, style = SnailStalk)
            drawCircle(Color.White, 4.4f, Offset(13f, 31f)); drawCircle(GiftRimInk, 4.4f, Offset(13f, 31f), style = GiftRim)
            drawCircle(Color.White, 4.4f, Offset(29f, 30f)); drawCircle(GiftRimInk, 4.4f, Offset(29f, 30f), style = GiftRim)
            drawCircle(SnailShell, 24f, Offset(58f, 52f)); drawCircle(GiftRimInk, 24f, Offset(58f, 52f), style = GiftRim)
            drawPath(SnailSpiral, Color.White, style = SnailSpiralStroke)
            drawPath(SnailSmile, InkColor, style = SnailSmileStroke)
        }
        GiftKind.WAVE -> {
            drawPath(WaveIcon, Ink30, style = WaveIconUnder)
            drawPath(WaveIcon, WaveBlue, style = WaveIconStroke)
            drawSparkle(26f, 26f, 10f, PawSunshine); drawSparkle(62f, 22f, 8f, PawSunshine); drawSparkle(86f, 34f, 6f, PawSunshine)
        }
    }
}

private val FlyRingInk = InkColor.copy(alpha = 0.3f)
private val FlyRing = Stroke(2.5f)
private val FlyDisc = Stroke(1.5f)

/** A gift in flight: 48dp of shiny disc with the picture in it, [sc] times full size, at ([x], [y]). */
internal fun DrawScope.drawFlyingGift(kind: GiftKind, x: Float, y: Float, sc: Float, alpha: Float) {
    withGroupAlpha(alpha, x, y, 48f * sc) {
        drawCircle(PawSunshine.copy(alpha = 0.14f), 40f * sc, Offset(x, y))
        drawCircle(PawSunshine.copy(alpha = 0.26f), 34f * sc, Offset(x, y))
        drawCircle(Color.White, 28f * sc, Offset(x, y))
        drawCircle(FlyRingInk, 28f * sc, Offset(x, y), style = FlyDisc)
        drawCircle(PawSunshine, 25f * sc, Offset(x, y), style = FlyRing)
        translate(x - 22f * sc, y - 22f * sc) { scale(0.44f * sc, 0.44f * sc, Offset.Zero) { giftPicture(kind) } }
    }
}

// ------------------------------------------------------------------ the ship (100 grid, drawn at 88dp)

private val ShipBody = svg("M50,4 C72,16 80,44 76,78 L24,78 C20,44 28,16 50,4Z")
private val ShipFins = listOf(svg("M28,52 C14,56 8,66 8,82 C20,82 28,78 33,70Z"), svg("M72,52 C86,56 92,66 92,82 C80,82 72,78 67,70Z"))
private val FlamePath = svg("M50,78 C41,86 44,94 50,100 C56,94 59,86 50,78Z")
private val FlameCore = svg("M50,81 C46,86 47,91 50,95 C53,91 54,86 50,81Z")
private val ShipRim = Stroke(1.6f / 0.88f, join = StrokeJoin.Round)
private val ShipRimInk = InkColor.copy(alpha = 0.4f)
private val WindowRing = Stroke(3f)
private val WindowClip = Path().apply { addOval(Rect(Offset(50f, 48f) - Offset(14f, 14f), Size(28f, 28f))) }
private val FoxEarsShip = listOf(
    Path().apply { moveTo(20f, 30f); lineTo(27f, 8f); lineTo(35f, 28f); close() },
    Path().apply { moveTo(60f, 30f); lineTo(53f, 8f); lineTo(45f, 28f); close() },
)
private val FoxNoseShip = Path().apply { moveTo(40f, 50f); lineTo(36f, 54f); lineTo(44f, 54f); close() }

/** The friendly rocket: white with coral nose, band and fins, a fox in the window, and a flame that breathes once a second. */
internal fun DrawScope.drawShip(cx: Float, cy: Float, flame: Float) {
    translate(cx - PopMetrics.SHIP_SIZE / 2f, cy - PopMetrics.SHIP_SIZE / 2f) {
        scale(0.88f, 0.88f, Offset.Zero) {
            scale(1f, flame, Offset(50f, 78f)) {
                drawPath(FlamePath, PawSunshine); drawPath(FlamePath, ShipRimInk, style = ShipRim)
                drawPath(FlameCore, PopFlameCore)
            }
            for (i in ShipFins.indices) { drawPath(ShipFins[i], PawCoral); drawPath(ShipFins[i], ShipRimInk, style = ShipRim) }
            drawPath(ShipBody, Color.White)
            clipPath(ShipBody) {
                drawRect(PawCoral, Offset.Zero, Size(100f, 28f))
                drawRect(PawCoral, Offset(0f, 68f), Size(100f, 10f))
            }
            drawPath(ShipBody, ShipRimInk, style = ShipRim)
            drawCircle(PopWindow, 16f, Offset(50f, 48f))
            drawCircle(Color.White, 16f, Offset(50f, 48f), style = WindowRing)
            drawCircle(ShipRimInk, 16f, Offset(50f, 48f), style = ShipRim)
            clipPath(WindowClip) {
                translate(35f, 34f) {
                    scale(30f / 80f, 30f / 80f, Offset.Zero) {
                        for (i in FoxEarsShip.indices) drawPath(FoxEarsShip[i], FoxEar)
                        drawCircle(FoxHead, 25f, Offset(40f, 46f))
                        drawOval(FoxMuzzle, Offset(27f, 45f), Size(26f, 18f))
                        drawCircle(InkColor, 3.4f, Offset(32f, 42f)); drawCircle(InkColor, 3.4f, Offset(48f, 42f))
                        drawPath(FoxNoseShip, InkColor)
                    }
                }
            }
        }
    }
}

/** The ship's soft glow: three flat circles, no blur. [alpha] carries the breathing. */
internal fun DrawScope.drawGlow(cx: Float, cy: Float, color: Color, r0: Float, alpha: Float) {
    if (alpha <= 0f) return
    drawCircle(color.copy(alpha = 0.16f * alpha), r0, Offset(cx, cy))
    drawCircle(color.copy(alpha = 0.24f * alpha), r0 - 10f, Offset(cx, cy))
    drawCircle(color.copy(alpha = 0.34f * alpha), r0 - 18f, Offset(cx, cy))
}
