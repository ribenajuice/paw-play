package com.pawplay.app.games.pawkitchen

import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.graphics.vector.PathParser
import com.pawplay.app.ui.theme.InkColor
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

/**
 * An ingredient's picture, drawn on a 100 x 100 grid (docs/DESIGN-SYSTEM.md,
 * "Ingredient drawing rules"). Like AnimalIcons.kt nothing is imported: the
 * same drawing is scaled to a 56dp bubble picture, a 96dp tray tile or a
 * piece on the dish.
 */
typealias IngredientIcon = DrawScope.() -> Unit

/** Draws [icon] into whatever box the Canvas is given, as a fraction of its size. */
@Composable
fun IngredientPicture(icon: IngredientIcon, modifier: Modifier = Modifier) {
    Canvas(modifier = modifier) {
        scale(scale = size.minDimension / 100f, pivot = Offset.Zero) { icon() }
    }
}

// --- tiny drawing kit (all coordinates are grid units; the Canvas is pre-scaled) ---

private val pathCache = HashMap<String, Path>()

/** SVG-style path data (M L H V C Q T A Z) turned into a Path once, on first draw. */
internal fun svgPath(d: String): Path =
    pathCache.getOrPut(d) { PathParser().parsePathString(d).toPath() }

internal val RimColor = InkColor.copy(alpha = 0.3f)

internal fun rimStroke(width: Float = 3f) = Stroke(width = width, join = StrokeJoin.Round)

/** A filled shape plus the faint ink rim every ingredient has, so pale things read on white and cream. */
internal fun DrawScope.shape(path: Path, fill: Color, rimWidth: Float = 3f, rim: Color = RimColor) {
    drawPath(path, fill)
    if (rimWidth > 0f) drawPath(path, rim, style = rimStroke(rimWidth))
}

internal fun DrawScope.shape(d: String, fill: Color, rimWidth: Float = 3f, rim: Color = RimColor) =
    shape(svgPath(d), fill, rimWidth, rim)

internal fun DrawScope.circle(cx: Float, cy: Float, r: Float, fill: Color, rimWidth: Float = 0f, alpha: Float = 1f) {
    drawCircle(fill, r, Offset(cx, cy), alpha = alpha)
    if (rimWidth > 0f) drawCircle(RimColor, r, Offset(cx, cy), style = rimStroke(rimWidth))
}

internal fun DrawScope.ellipse(
    cx: Float, cy: Float, rx: Float, ry: Float, rotation: Float, fill: Color,
    rimWidth: Float = 0f, alpha: Float = 1f, rim: Color = RimColor,
) {
    rotate(rotation, Offset(cx, cy)) {
        drawOval(fill, Offset(cx - rx, cy - ry), Size(rx * 2, ry * 2), alpha = alpha)
        if (rimWidth > 0f) drawOval(rim, Offset(cx - rx, cy - ry), Size(rx * 2, ry * 2), style = rimStroke(rimWidth))
    }
}

internal fun DrawScope.rrect(
    x: Float, y: Float, w: Float, h: Float, r: Float, fill: Color,
    rimWidth: Float = 0f, alpha: Float = 1f, rim: Color = RimColor,
) {
    drawRoundRect(fill, Offset(x, y), Size(w, h), CornerRadius(r), alpha = alpha)
    if (rimWidth > 0f) drawRoundRect(rim, Offset(x, y), Size(w, h), CornerRadius(r), style = rimStroke(rimWidth))
}

internal fun DrawScope.line(d: String, color: Color, width: Float, alpha: Float = 1f) =
    drawPath(svgPath(d), color, alpha = alpha, style = Stroke(width = width, cap = StrokeCap.Round, join = StrokeJoin.Round))

private fun starPath(cx: Float, cy: Float, outer: Float, inner: Float, points: Int, startAngle: Float): Path =
    Path().apply {
        for (i in 0 until points * 2) {
            val r = if (i % 2 == 1) inner else outer
            val a = startAngle + i * PI.toFloat() / points
            val x = cx + r * cos(a)
            val y = cy + r * sin(a)
            if (i == 0) moveTo(x, y) else lineTo(x, y)
        }
        close()
    }

/** A disc with wavy edges: r = radius + amplitude * sin(lobes * theta), the pizza sauce and cheese splats. */
internal fun blobPath(cx: Float, cy: Float, radius: Float, amplitude: Float, lobes: Int): Path =
    Path().apply {
        val steps = lobes * 8
        for (i in 0 until steps) {
            val a = i.toFloat() / steps * 2f * PI.toFloat()
            val rr = radius + amplitude * sin(a * lobes)
            val x = cx + rr * cos(a)
            val y = cy + rr * sin(a)
            if (i == 0) moveTo(x, y) else lineTo(x, y)
        }
        close()
    }

internal fun DrawScope.capsule(x: Float, y: Float, rotation: Float, color: Color, length: Float, width: Float) {
    withTransform({ translate(x, y); rotate(rotation, Offset.Zero) }) {
        val topLeft = Offset(-length / 2f, -width / 2f)
        drawRoundRect(color, topLeft, Size(length, width), CornerRadius(width / 2f))
        drawRoundRect(
            InkColor.copy(alpha = 0.25f), topLeft, Size(length, width), CornerRadius(width / 2f),
            style = Stroke(width = 1.6f, join = StrokeJoin.Round),
        )
    }
}

/** Draws [icon] (a 100-grid drawing) into the box at x,y sized w by h — used for pieces on a dish. */
internal fun DrawScope.placeIcon(icon: IngredientIcon, x: Float, y: Float, w: Float, h: Float = w) {
    withTransform({ translate(x, y); scale(w / 100f, h / 100f, Offset.Zero) }) { icon() }
}

// --- palette shared by an ingredient's tray icon and its piece on the dish ---
internal val CheeseBurger = Color(0xFFFFC83A)
internal val CheeseHole = Color(0xFFE8A400)
internal val BunTop = Color(0xFFF0A94A)
internal val BunHeel = Color(0xFFE39A3B)
internal val SesameSeed = Color(0xFFFFF3D6)
internal val PattyBrown = Color(0xFF7B4A2B)
internal val PattyShine = Color(0xFFA56B42)
internal val TomatoRed = Color(0xFFF2543A)
internal val LettuceGreen = Color(0xFF6FCB5A)
internal val PickleGreen = Color(0xFF4E9A3A)
internal val DoughEdge = Color(0xFFE5B676)
internal val DoughMiddle = Color(0xFFF6DDB0)
internal val SauceRed = Color(0xFFE63C2C)
internal val MushroomStem = Color(0xFFF3E3CC)
internal val OliveGreen = Color(0xFF4B5A2E)
internal val PepperoniRed = Color(0xFFB8301F)
internal val ConeBody = Color(0xFFE6A85C)
internal val ConeWaffle = Color(0xFFB8762F)
internal val ConeRim = Color(0xFFF0C07C)
internal val StrawberryPink = Color(0xFFFF9EC4)
internal val ChocolateBrown = Color(0xFF7A4B2B)
internal val WaferTan = Color(0xFFF4D8A0)

// ---------------------------------------------------------------- BURGER (side view)

internal val BunIcon: IngredientIcon = {
    shape("M10,54C10,24 28,10 50,10C72,10 90,24 90,54Z", BunTop)
    rrect(12f, 60f, 76f, 24f, 11f, BunHeel, rimWidth = 3f)
    sesame(BunSesameTray)
}
private val BunSesameTray = listOf(
    Triple(34f, 32f, -25f), Triple(52f, 25f, 10f), Triple(68f, 34f, 30f),
    Triple(44f, 45f, -10f), Triple(62f, 47f, 20f), Triple(27f, 47f, -30f),
)

internal fun DrawScope.sesame(seeds: List<Triple<Float, Float, Float>>, dx: Float = 0f, dy: Float = 0f) {
    seeds.forEach { (x, y, r) -> ellipse(x + dx, y + dy, 5f, 2.8f, r, SesameSeed) }
}

internal val PattyIcon: IngredientIcon = {
    rrect(8f, 26f, 84f, 48f, 24f, PattyBrown, rimWidth = 3f)
    rrect(24f, 34f, 34f, 7f, 3.5f, PattyShine)
    line("M34,64L44,46M50,66L60,46M66,64L76,48", Color(0xFF4A2914), 5f)
}

internal val BurgerCheeseIcon: IngredientIcon = {
    rotate(-8f, Offset(50f, 50f)) {
        shape("M10,20H90V56H76V76Q76,84 68,84Q60,84 60,76V56H10Z", CheeseBurger)
        circle(26f, 36f, 5f, CheeseHole)
        circle(50f, 42f, 4f, CheeseHole)
        circle(72f, 32f, 4.5f, CheeseHole)
    }
}

internal val TomatoIcon: IngredientIcon = {
    circle(50f, 57f, 35f, TomatoRed, rimWidth = 3f)
    ellipse(36f, 48f, 6f, 11f, 25f, Color.White, alpha = 0.45f)
    val star = starPath(50f, 25f, 15f, 5.5f, 5, (-PI / 2).toFloat())
    drawPath(star, Color(0xFF2E9E4E))
    drawPath(star, RimColor, style = rimStroke(2f))
}

internal val LettuceIcon: IngredientIcon = {
    shape(
        "M8,50C6,26 28,16 40,24C46,14 60,14 66,24C80,16 94,30 92,50C94,62 88,72 78,72C72,82 60,78 54,74" +
            "C46,82 34,80 30,72C18,74 6,64 8,50Z",
        LettuceGreen,
    )
    line("M50,26C48,44 50,58 50,70M50,46L34,38M50,46L66,38M50,58L36,54M50,58L64,54", Color(0xFF4FA83E), 4f)
}

internal val PickleIcon: IngredientIcon = {
    rotate(-38f, Offset(50f, 50f)) {
        rrect(6f, 32f, 88f, 36f, 18f, PickleGreen, rimWidth = 3f)
        rrect(18f, 37f, 40f, 5f, 2.5f, Color.White, alpha = 0.3f)
        listOf(26f to 52f, 40f to 60f, 54f to 45f, 68f to 59f, 80f to 48f, 33f to 46f)
            .forEach { (x, y) -> circle(x, y, 3.2f, Color(0xFFA6D986)) }
    }
}

// ---------------------------------------------------------------- PIZZA (top view)

internal val DoughIcon: IngredientIcon = {
    circle(50f, 50f, 43f, DoughEdge, rimWidth = 3f)
    circle(50f, 50f, 32f, DoughMiddle)
    listOf(Triple(40f, 42f, -20f), Triple(60f, 38f, 30f), Triple(62f, 60f, -10f), Triple(40f, 62f, 25f), Triple(50f, 50f, 0f))
        .forEach { (x, y, r) -> ellipse(x, y, 4.5f, 2.6f, r, Color(0xFFE7C287)) }
}

internal val SauceIcon: IngredientIcon = {
    shape(blobPath(50f, 50f, 36f, 4.5f, 7), SauceRed)
    line("M32,56C30,38 60,32 64,50C66,64 46,70 40,60C36,52 48,46 54,52", Color(0xFFFF9078), 5f)
}

internal val PizzaCheeseIcon: IngredientIcon = {
    shape("M6,80L6,58L94,26L94,80Z", Color(0xFFFFD23F))
    drawPath(svgPath("M10,60L90,31L90,38L10,68Z"), Color(0xFFFFE98A))
    circle(26f, 70f, 6.5f, CheeseHole)
    circle(52f, 60f, 7f, CheeseHole)
    circle(76f, 66f, 5.5f, CheeseHole)
    circle(62f, 75f, 3.5f, CheeseHole)
}

internal val MushroomIcon: IngredientIcon = {
    shape(
        "M10,52C10,26 30,12 50,12C70,12 90,26 90,52C90,60 84,62 76,62L62,62L62,82Q62,90 54,90L46,90" +
            "Q38,90 38,82L38,62L24,62C16,62 10,60 10,52Z",
        MushroomStem,
    )
    drawPath(svgPath("M13,48C13,28 30,15 50,15C70,15 87,28 87,48C70,42 30,42 13,48Z"), Color(0xFFC9976A))
    circle(34f, 29f, 3.2f, MushroomStem)
    circle(52f, 24f, 3.2f, MushroomStem)
    circle(68f, 31f, 3.2f, MushroomStem)
}

internal val OliveIcon: IngredientIcon = {
    circle(50f, 50f, 38f, OliveGreen, rimWidth = 3f)
    drawCircle(Color.White, 14f, Offset(50f, 50f))
    drawCircle(InkColor.copy(alpha = 0.25f), 14f, Offset(50f, 50f), style = rimStroke(2f))
    line("M22,40A30,30 0 0 1 40,21", Color.White, 5f, alpha = 0.45f)
}

internal val PepperoniIcon: IngredientIcon = {
    circle(50f, 50f, 38f, PepperoniRed, rimWidth = 3f)
    listOf(
        Triple(36f, 36f, -20f), Triple(60f, 32f, 30f), Triple(66f, 56f, 0f),
        Triple(44f, 64f, 40f), Triple(50f, 48f, 0f), Triple(30f, 56f, -30f),
    ).forEach { (x, y, r) -> ellipse(x, y, 5.5f, 3.4f, r, Color(0xFFF4C3A0)) }
}

// ---------------------------------------------------------------- ICE CREAM (side view)

private const val CONE_PATH = "M20,22L80,22L55,90Q50,98 45,90Z"
private const val SCOOP_TOP = "M10,54C10,26 28,10 50,10C72,10 90,26 90,54"

internal val ConeIcon: IngredientIcon = {
    drawPath(svgPath(CONE_PATH), ConeBody)
    clipPath(svgPath(CONE_PATH)) {
        listOf(-10f, 10f, 30f, 50f, 70f, 90f).forEach { x ->
            line("M$x,18L${x + 56},100M${x + 56},18L$x,100", ConeWaffle, 3f, alpha = 0.65f)
        }
    }
    drawPath(svgPath(CONE_PATH), RimColor, style = rimStroke())
    rrect(16f, 14f, 68f, 14f, 7f, ConeRim, rimWidth = 3f)
}

internal val StrawberryIcon: IngredientIcon = {
    shape(
        SCOOP_TOP + "L90,60C90,72 80,76 72,68C68,80 56,82 50,74C44,82 32,80 28,68C20,76 10,72 10,60Z",
        StrawberryPink,
    )
    listOf(
        Triple(34f, 30f, -15f), Triple(54f, 24f, 20f), Triple(70f, 38f, -25f), Triple(44f, 44f, 10f),
        Triple(26f, 50f, 30f), Triple(60f, 56f, -10f), Triple(76f, 54f, 20f),
    ).forEach { (x, y, r) -> ellipse(x, y, 2.4f, 3.8f, r, Color(0xFFD63A78)) }
}

internal val ChocolateIcon: IngredientIcon = {
    shape(SCOOP_TOP + "L90,62L80,74L70,62L60,76L50,62L40,76L30,62L20,74L10,62Z", ChocolateBrown)
    val chip = Path().apply { moveTo(-5f, 4f); lineTo(5f, 4f); lineTo(0f, -5f); close() }
    listOf(
        Triple(34f, 30f, 15f), Triple(56f, 24f, -20f), Triple(70f, 38f, 35f),
        Triple(44f, 44f, -10f), Triple(26f, 50f, 50f), Triple(62f, 56f, 20f),
    ).forEach { (x, y, r) ->
        withTransform({ translate(x, y); rotate(r, Offset.Zero) }) { drawPath(chip, Color(0xFFEBC790)) }
    }
}

internal val CherryIcon: IngredientIcon = {
    line("M50,16Q40,32 28,56M50,16Q60,30 68,54", Color(0xFF4E7A2B), 4.5f)
    ellipse(64f, 14f, 13f, 5.5f, -20f, Color(0xFF35A34A), rimWidth = 2f, rim = InkColor.copy(alpha = 0.25f))
    circle(28f, 70f, 20f, Color(0xFFD62839), rimWidth = 3f)
    circle(68f, 68f, 20f, Color(0xFFD62839), rimWidth = 3f)
    ellipse(21f, 62f, 3.4f, 6f, 25f, Color.White, alpha = 0.5f)
    ellipse(61f, 60f, 3.4f, 6f, 25f, Color.White, alpha = 0.5f)
}

private val SprinkleColors = listOf(
    Color(0xFFFF6B4A), Color(0xFF4FC1E9), Color(0xFFFFD23F), Color(0xFF35C46B), Color(0xFFFF9EC4),
)

internal val SprinklesIcon: IngredientIcon = {
    listOf(
        listOf(28f, 26f, -30f, 0f), listOf(58f, 20f, 20f, 1f), listOf(78f, 38f, -60f, 2f), listOf(42f, 46f, 50f, 3f),
        listOf(66f, 58f, 10f, 4f), listOf(22f, 64f, -10f, 1f), listOf(48f, 76f, -40f, 0f), listOf(76f, 80f, 35f, 2f),
        listOf(30f, 86f, 60f, 3f), listOf(16f, 40f, 70f, 4f),
    ).forEach { (x, y, r, c) -> capsule(x, y, r, SprinkleColors[c.toInt()], 18f, 7f) }
}

internal val WaferIcon: IngredientIcon = {
    rotate(28f, Offset(50f, 50f)) {
        rrect(30f, 4f, 40f, 92f, 6f, WaferTan, rimWidth = 3f)
        line("M43,8V92M57,8V92M32,26H68M32,44H68M32,62H68M32,80H68", Color(0xFFD9A85E), 2.5f)
    }
}
