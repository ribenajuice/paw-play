package com.pawplay.app.games.pawpop

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.drawscope.translate
import com.pawplay.app.ui.theme.PawSunshine
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin

// Drawing of the play area (everything but the home button), in dp. See docs/DESIGN-SYSTEM.md, "Paw Pop".
// Order, back to front: targets (so a miss fades behind everything), the wave, the ribbon, stars, glows, the ship,
// the gift in flight, then pops and the arrival's sparkle.

// ------------------------------------------------------------------ the sky (static, drawn once)

private val CloudRefs = arrayOf(
    floatArrayOf(62f, 330f, 1f), floatArrayOf(300f, 520f, 1.15f), floatArrayOf(220f, 96f, 0.8f),
    floatArrayOf(40f, 610f, 0.9f), floatArrayOf(316f, 250f, 0.75f), floatArrayOf(110f, 470f, 0.7f),
)
private val DotRefs = arrayOf(
    floatArrayOf(30f, 150f), floatArrayOf(110f, 60f), floatArrayOf(330f, 140f), floatArrayOf(250f, 380f), floatArrayOf(14f, 440f),
    floatArrayOf(340f, 400f), floatArrayOf(150f, 560f), floatArrayOf(90f, 250f), floatArrayOf(200f, 200f),
)
private val CloudWhite = Color.White.copy(alpha = 0.45f)
private val DotWhite = Color.White.copy(alpha = 0.8f)

/** Pale-blue sky top to bottom with six clouds and nine dots, placed as fractions of the play area. Nothing in it moves. */
internal fun DrawScope.drawSky(w: Float, h: Float) {
    drawRect(Brush.verticalGradient(listOf(PopSkyTop, PopSkyBottom), startY = 0f, endY = h), Offset.Zero, Size(w, h))
    for (ci in CloudRefs.indices) {
        val c = CloudRefs[ci]
        val x = c[0] / 360f * w
        val y = c[1] / 692f * h
        val s = c[2]
        cloudPart(x, y, 40f * s, 14f * s)
        cloudPart(x - 22f * s, y + 4f * s, 26f * s, 11f * s)
        cloudPart(x + 26f * s, y + 4f * s, 24f * s, 10f * s)
    }
    for (di in DotRefs.indices) drawCircle(DotWhite, 2.2f, Offset(DotRefs[di][0] / 360f * w, DotRefs[di][1] / 692f * h))
}

private fun DrawScope.cloudPart(cx: Float, cy: Float, rx: Float, ry: Float) {
    drawOval(CloudWhite, Offset(cx - rx, cy - ry), Size(2 * rx, 2 * ry))
}

// ------------------------------------------------------------------ the moving scene

private val TwoPiF = (2 * PI).toFloat()
private val RibbonPath = Path()
private val WavePath = Path()
private val RibbonStripes = arrayOf(Color(0xFFFF8A80), PawSunshine, Color(0xFF8FDDA0), Color(0xFF5CC8F0), Color(0xFFB49AF0))
private val RibbonBand = Color.White.copy(alpha = 0.7f)
private val WaveHalo = Stroke(46f, cap = StrokeCap.Round, join = StrokeJoin.Round)
private val WaveMid = Stroke(28f, cap = StrokeCap.Round, join = StrokeJoin.Round)
private val WaveCore = Stroke(10f, cap = StrokeCap.Round, join = StrokeJoin.Round)
private val WaveHaloColour = Color.White.copy(alpha = 0.16f)
private val WaveMidColour = PawSunshine.copy(alpha = 0.30f)
private val WaveCoreColour = Color.White.copy(alpha = 0.80f)
private val RingWhite = Stroke(5.5f)
private val RingColour = Stroke(2.5f)
private val ArriveWhite = Stroke(5f)
private val ArriveSun = Stroke(2.5f)
private val RibbonOffsets = FloatArray(128)

internal fun easeOut(k: Float): Float {
    val u = 1f - k.coerceIn(0f, 1f)
    return 1f - u * u * u
}

internal fun DrawScope.drawScene(ui: PopUi) {
    val s = ui.session
    val t = s.time.toFloat()
    val slow = s.slowLeft > 0f

    // Targets, oldest first: two swaying targets that overlap simply pass over each other. Misses fade here, behind the ship.
    for (i in 0 until s.targetCount) {
        val tg = s.target(i)
        drawTarget(tg.kind, tg.colour, tg.critter, tg.size, tg.x, tg.y, alpha = tg.alpha, halo = slow, gift = tg.gift, giftT = t + tg.phase)
    }

    if (s.waveActive) drawWave(s, t)
    if (s.starEffect == GiftKind.RIBBON) drawRibbon(s, t)

    for (i in 0 until s.starCount) {
        val st = s.stars[i]
        if (st.big) bigStarTrail(st)
        drawStar(st.x, st.y, if (st.big) PopMetrics.BIG_STAR_RADIUS else PopMetrics.STAR_RADIUS, st.degrees)
    }

    val cy = s.shipCentreY
    if (slow) drawGlow(s.shipX, cy, PopLilac, 76f, glowBreath(s.slowLeft))
    if (s.starEffect != null) drawGlow(s.shipX, cy, PawSunshine, 62f, glowBreath(s.starEffectLeft))
    drawShip(s.shipX, cy, 1f + 0.08f * sin(TwoPiF * t))

    val g = s.gift
    if (g != null) {
        val k = g.progress
        val x1 = s.shipX
        val y1 = s.nose - 4f
        for (j in 3 downTo 1) {
            val kk = k - 0.07f * j
            if (kk < 0f) continue
            drawFlyingGift(g.kind, PopGlide.x(g.x0, x1, kk), PopGlide.y(g.y0, y1, kk), 0.6f, 0.14f + 0.1f * (3 - j))
        }
        drawFlyingGift(g.kind, PopGlide.x(g.x0, x1, k), PopGlide.y(g.y0, y1, k), 1f, 1f - 0.4f * (if (k > 0.85f) (k - 0.85f) / 0.15f else 0f))
    }

    for (i in 0 until s.fxCount) {
        val f = s.fx[i]
        if (f.arrival) drawArrival(s, f) else drawPop(f)
    }
}

/** The big star's three ghosts behind it (0.5, 0.4, 0.32 of its size at 30%, 18% and 9%, about 34dp apart). */
private fun DrawScope.bigStarTrail(st: PopStar) {
    val dx = -st.vx / st.vy * 34f // the ghosts sit back along the star's own path
    drawStar(st.x + dx, st.y + 34f, PopMetrics.BIG_STAR_RADIUS * 0.5f, st.degrees, 0.30f)
    drawStar(st.x + 2 * dx, st.y + 68f, PopMetrics.BIG_STAR_RADIUS * 0.4f, st.degrees, 0.18f)
    drawStar(st.x + 3 * dx, st.y + 102f, PopMetrics.BIG_STAR_RADIUS * 0.32f, st.degrees, 0.09f)
}

private fun ribbonOff(y: Float, t: Float): Float = 5f * sin((y / 140f + 0.35f * t) * TwoPiF)

private fun DrawScope.ribbonBand(shipX: Float, n: Int, t: Float, a: Float, b: Float, colour: Color, alpha: Float) {
    RibbonPath.rewind()
    for (i in 0 until n) {
        val y = RibbonOffsets[i]
        val x = shipX + a + ribbonOff(y, t)
        if (i == 0) RibbonPath.moveTo(x, y) else RibbonPath.lineTo(x, y)
    }
    for (i in n - 1 downTo 0) {
        val y = RibbonOffsets[i]
        RibbonPath.lineTo(shipX + b + ribbonOff(y, t), y)
    }
    RibbonPath.close()
    drawPath(RibbonPath, colour, alpha)
}

/** A column 56dp wide of five stripes on a white band, swaying a few dp, from the nose to its grown height. */
private fun DrawScope.drawRibbon(s: PopSession, t: Float) {
    val top = s.nose - s.ribbonLength
    var n = 0
    var y = s.nose + 10f
    while (y > top && n < RibbonOffsets.size - 1) {
        RibbonOffsets[n++] = y
        y -= 10f
    }
    RibbonOffsets[n++] = top
    ribbonBand(s.shipX, n, t, -31f, 31f, RibbonBand, 1f)
    val w = 56f / 5f
    for (i in 0 until 5) ribbonBand(s.shipX, n, t, -28f + i * w, -28f + (i + 1) * w, RibbonStripes[i], 0.92f)
}

private fun DrawScope.waveLine(width: Float, y0: Float, ph: Float, stroke: Stroke, colour: Color) {
    WavePath.rewind()
    var x = -6f
    var first = true
    while (x <= width + 6f) {
        val yy = y0 + 11f * sin(TwoPiF * x / 96f + ph)
        if (first) { WavePath.moveTo(x, yy); first = false } else WavePath.lineTo(x, yy)
        x += 6f
    }
    drawPath(WavePath, colour, style = stroke)
}

/** The sparkle wave: a wavy band the width of the screen rising past the top, three round polylines, ten sparkles riding it. */
private fun DrawScope.drawWave(s: PopSession, t: Float) {
    val y0 = s.waveY
    val ph = t * 3f
    waveLine(s.width, y0, ph, WaveHalo, WaveHaloColour)
    waveLine(s.width, y0, ph, WaveMid, WaveMidColour)
    waveLine(s.width, y0, ph, WaveCore, WaveCoreColour)
    if (s.waveSparkly) {
        for (i in 0 until PopMetrics.WAVE_SPARKLES) {
            val x = s.width * (i + 0.5f) / PopMetrics.WAVE_SPARKLES + (i % 2) * 8f
            drawSparkle(x, y0 + 11f * sin(TwoPiF * x / 96f + ph) + (i % 3 - 1) * 10f, 5f + (i % 3) * 2.2f, if (i % 2 == 1) Color.White else PawSunshine, 0.95f)
        }
    }
}

/** A ring and six sparkles around the ship's nose as a gift arrives. */
private fun DrawScope.drawArrival(s: PopSession, f: PopFx) {
    val k = (f.age / f.duration).coerceIn(0f, 1f)
    val e = easeOut(k)
    val op = 0.8f * (1f - k)
    val cx = s.shipX
    val cy = s.nose + 8f
    drawCircle(Color.White, 30f + 30f * e, Offset(cx, cy), alpha = op, style = ArriveWhite)
    drawCircle(PawSunshine, 30f + 30f * e, Offset(cx, cy), alpha = op, style = ArriveSun)
    if (f.sparkles > 0) for (i in 0 until PopMetrics.ARRIVE_SPARKLES) {
        val a = (i * 60f + 20f) * PI.toFloat() / 180f
        val d = 34f + 26f * e
        drawSparkle(cx + cos(a) * d, s.nose + 16f + sin(a) * d * 0.95f, (if (i % 2 == 1) 6f else 8f) * sin(PI.toFloat() * k), if (i % 2 == 1) Color.White else PawSunshine)
    }
}

/**
 * A pop, [PopFx.age] seconds in: the body swells to 1.06 by 60ms and shrinks to 0.94 by 170ms while fading out; a ring
 * (white under, the target's own colour on top) swells from 20ms to 350ms; seven sparkles drift outward and fade smoothly;
 * a critter's animal hops and floats up smiling until 600ms.
 */
private fun DrawScope.drawPop(f: PopFx) {
    val ms = f.age * 1000f
    val size = f.size
    if (ms < 170f) {
        val kk = ms / 170f
        val grow = 1f + 0.06f * min(1f, ms / 60f) - 0.12f * maxOf(0f, (ms - 60f) / 110f)
        drawTarget(f.kind, f.colour, f.critter, size, f.x, f.y, alpha = 1f - kk * kk, grow = grow)
    }
    if (ms > 20f && ms <= 350f) {
        val k = ((ms - 20f) / 330f).coerceIn(0f, 1f)
        val e = easeOut(k)
        val r = size * (0.5f + 0.38f * e)
        val op = 0.85f * (1f - k)
        drawCircle(Color.White, r, Offset(f.x, f.y), alpha = op, style = RingWhite)
        drawCircle(TargetColors[f.colour], r, Offset(f.x, f.y), alpha = op, style = RingColour)
        if (f.sparkles > 0) for (i in 0 until PopMetrics.POP_SPARKLES) {
            val a = (i * 360f / PopMetrics.POP_SPARKLES + 12f) * PI.toFloat() / 180f
            val d = size * (0.34f + 0.42f * e)
            val sz = (5f + 4.5f * (i % 2)) * sin(PI.toFloat() * k)
            drawSparkle(f.x + cos(a) * d, f.y + sin(a) * d, sz, if (i % 2 == 1) Color.White else PawSunshine)
        }
    }
    if (f.kind == TargetKind.CRITTER && ms >= 60f && ms <= 600f) {
        val hop = 16f * sin(PI.toFloat() * min(1f, ms / 220f))
        val rise = 50f * easeOut(min(1f, ms / 600f))
        val al = ((ms - 60f) / 100f).coerceIn(0f, 1f) * (1f - ((ms - 360f) / 240f).coerceIn(0f, 1f))
        val cy = f.y - hop - rise
        withGroupAlpha(al, f.x, cy, size * 1.4f) {
            translate(f.x, cy) { scale(size / 100f, size / 100f, Offset.Zero) { freeCritter(f.critter, size) } }
        }
    }
}
