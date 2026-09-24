package com.pawplay.app.games.pawkitchen

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath

/**
 * The menu: every dish Paw Kitchen can order, as CONTENT. A [DishSpec] pairs a
 * [Dish] (the pure-Kotlin id / base / shelf that ordering, the tray, serving
 * and the ramp work from, see PawKitchenLogic.kt) with what it looks like: one
 * tray/bubble picture per shelf ingredient and a [DishView] that draws the
 * plate as ingredients are added (docs/DESIGN-SYSTEM.md, "Dish pieces").
 *
 * Adding pancakes, soup or cupcakes later means adding one more DishSpec to
 * [KitchenMenu.specs] below. No ordering, tray, serve, ramp or screen code
 * changes, and no other game is touched.
 */
class DishSpec(
    val dish: Dish,
    /** Tray icon = bubble picture, for every ingredient on the shelf. */
    val icons: Map<String, IngredientIcon>,
    val view: DishView,
)

/** What is drawn on the 200 x 160 dish canvas for a given set of ingredients on the plate. */
fun interface DishView {
    /** In drawing order (back to front). Plate and stand pieces have a null [DishPiece.ingredient]. */
    fun pieces(onDish: Set<String>): List<DishPiece>
}

/**
 * One drawn thing on the dish. Its [bounds] (200 x 160 grid) are also its tap
 * area, so the tap "remove it from the dish" needs no separate hit logic.
 * [oval] pieces (pizza toppings) only count taps inside the ellipse.
 */
class DishPiece(
    val ingredient: String?,
    val bounds: Rect,
    val oval: Boolean = false,
    val draw: DrawScope.() -> Unit,
)

const val DISH_WIDTH = 200f
const val DISH_HEIGHT = 160f

/** Which ingredient was tapped on the dish: the topmost piece wins, plates and stands never count. */
fun hitTestDish(pieces: List<DishPiece>, point: Offset): String? =
    pieces.asReversed().firstOrNull { it.ingredient != null && it.contains(point) }?.ingredient

private fun DishPiece.contains(p: Offset): Boolean {
    if (!bounds.contains(p)) return false
    if (!oval) return true
    val rx = bounds.width / 2f
    val ry = bounds.height / 2f
    val dx = (p.x - bounds.center.x) / rx
    val dy = (p.y - bounds.center.y) / ry
    return dx * dx + dy * dy <= 1f
}

object KitchenMenu {
    val specs: List<DishSpec> = listOf(burger(), pizza(), iceCream())

    val dishes: List<Dish> get() = specs.map { it.dish }

    fun specFor(dish: Dish): DishSpec = specs.first { it.dish.id == dish.id }
}

// --------------------------------------------------------------------- shared

private val PlateWhite = Color.White

private fun plate(cx: Float, cy: Float, rx: Float, ry: Float, wellRx: Float, wellRy: Float, wellDy: Float) = DishPiece(
    ingredient = null,
    bounds = Rect(cx - rx, cy - ry, cx + rx, cy + ry),
) {
    drawOval(PlateWhite, Offset(cx - rx, cy - ry), Size(rx * 2, ry * 2))
    drawOval(
        KitchenPlateRim, Offset(cx - rx, cy - ry), Size(rx * 2, ry * 2),
        style = rimStrokeOf(3f),
    )
    drawOval(
        KitchenPlateWell, Offset(cx - wellRx, cy + wellDy - wellRy),
        Size(wellRx * 2, wellRy * 2),
    )
}

private fun rimStrokeOf(width: Float) = Stroke(width = width, join = StrokeJoin.Round)

private fun DrawScope.plateRim(path: Path) {
    drawPath(path, PlateWhite)
    drawPath(path, KitchenPlateRim, style = rimStrokeOf(3f))
}

// --------------------------------------------------------------------- burger

private const val BURGER_BUN = "bun"
private const val BURGER_PATTY = "patty"
private const val BURGER_CHEESE = "cheese"
private const val BURGER_TOMATO = "tomato"
private const val BURGER_LETTUCE = "lettuce"
private const val BURGER_PICKLE = "pickle"

private fun burger(): DishSpec {
    val dish = Dish(
        id = "burger",
        base = BURGER_BUN,
        shelf = listOf(BURGER_BUN, BURGER_PATTY, BURGER_CHEESE, BURGER_TOMATO, BURGER_LETTUCE, BURGER_PICKLE),
    )
    val icons = mapOf(
        BURGER_BUN to BunIcon, BURGER_PATTY to PattyIcon, BURGER_CHEESE to BurgerCheeseIcon,
        BURGER_TOMATO to TomatoIcon, BURGER_LETTUCE to LettuceIcon, BURGER_PICKLE to PickleIcon,
    )
    val view = DishView { on ->
        val out = ArrayList<DishPiece>()
        out += plate(cx = 100f, cy = 151f, rx = 88f, ry = 9f, wellRx = 62f, wellRy = 4.5f, wellDy = -1f)
        // Slabs stack upward from `top`, each nesting slightly into the one below. Order = shelf order.
        var top = 142f
        if (BURGER_BUN in on) { // the heel; the crown is added last
            val y = top - 18f
            out += DishPiece(BURGER_BUN, Rect(40f, y, 160f, top)) { rrect(40f, y, 120f, 18f, 9f, BunHeel, rimWidth = 3f) }
            top -= 16f
        }
        if (BURGER_PATTY in on) {
            val y = top - 20f
            out += DishPiece(BURGER_PATTY, Rect(34f, y, 166f, top)) {
                rrect(34f, y, 132f, 20f, 10f, PattyBrown, rimWidth = 3f)
                rrect(52f, y + 5f, 30f, 4f, 2f, PattyShine)
            }
            top -= 18f
        }
        if (BURGER_CHEESE in on) {
            val t = top
            out += DishPiece(BURGER_CHEESE, Rect(34f, t - 7f, 166f, t + 9f)) {
                shape(
                    "M34,${t - 7}H166V${t - 1}L148,${t - 1}L142,${t + 9}L136,${t - 1}L66,${t - 1}L60,${t + 9}L54,${t - 1}L34,${t - 1}Z",
                    CheeseBurger,
                )
            }
            top -= 6f
        }
        if (BURGER_TOMATO in on) {
            val t = top
            out += DishPiece(BURGER_TOMATO, Rect(42f, t - 11f, 158f, t)) {
                rrect(42f, t - 11f, 116f, 11f, 5.5f, TomatoRed, rimWidth = 3f)
                listOf(58f, 80f, 102f, 124f, 142f).forEach { x -> circle(x, t - 5.5f, 1.8f, Color(0xFFFFD9A8)) }
            }
            top -= 9f
        }
        if (BURGER_LETTUCE in on) {
            val t = top
            val y = t - 6f
            out += DishPiece(BURGER_LETTUCE, Rect(30f, y - 10f, 170f, y + 9f)) {
                shape(
                    "M30,$y Q40,${y - 10} 50,$y T70,$y T90,$y T110,$y T130,$y T150,$y T170,$y V${y + 9} H30 Z",
                    LettuceGreen,
                )
            }
            top -= 10f
        }
        if (BURGER_PICKLE in on) {
            val t = top
            out += DishPiece(BURGER_PICKLE, Rect(44f, t - 10f, 156f, t + 2f)) {
                listOf(62f, 100f, 138f).forEach { x ->
                    ellipse(x, t - 4f, 18f, 5.5f, 0f, PickleGreen, rimWidth = 3f)
                    ellipse(x, t - 4f, 11f, 2.4f, 0f, Color(0xFF93CC72))
                }
            }
            top -= 7f
        }
        if (BURGER_BUN in on) { // crown, always on top: a bun with toppings is a closed burger
            val t = top
            out += DishPiece(BURGER_BUN, Rect(40f, t - 40f, 160f, t + 2f)) {
                shape("M40,${t + 2}C40,${t - 36} 66,${t - 40} 100,${t - 40}C134,${t - 40} 160,${t - 36} 160,${t + 2}Z", BunTop)
                sesame(
                    listOf(
                        Triple(78f, -24f, -20f), Triple(100f, -30f, 5f), Triple(122f, -24f, 20f), Triple(90f, -14f, -5f),
                        Triple(112f, -14f, 10f), Triple(66f, -12f, -25f), Triple(134f, -12f, 25f),
                    ).map { (x, y, r) -> Triple(x, t + y, r) },
                )
            }
        }
        out
    }
    return DishSpec(dish, icons, view)
}

// --------------------------------------------------------------------- pizza (from above)

private const val PIZZA_DOUGH = "dough"
private const val PIZZA_SAUCE = "sauce"
private const val PIZZA_CHEESE = "cheese"
private const val PIZZA_MUSHROOM = "mushroom"
private const val PIZZA_OLIVE = "olive"
private const val PIZZA_PEPPERONI = "pepperoni"

private fun pizza(): DishSpec {
    val dish = Dish(
        id = "pizza",
        base = PIZZA_DOUGH,
        shelf = listOf(PIZZA_DOUGH, PIZZA_SAUCE, PIZZA_CHEESE, PIZZA_MUSHROOM, PIZZA_OLIVE, PIZZA_PEPPERONI),
    )
    val icons = mapOf(
        PIZZA_DOUGH to DoughIcon, PIZZA_SAUCE to SauceIcon, PIZZA_CHEESE to PizzaCheeseIcon,
        PIZZA_MUSHROOM to MushroomIcon, PIZZA_OLIVE to OliveIcon, PIZZA_PEPPERONI to PepperoniIcon,
    )
    // Toppings sit in fixed slots so a missing one never leaves a broken-looking gap.
    val slots = mapOf(
        PIZZA_PEPPERONI to (28f to listOf(76f to 66f, 124f to 66f, 100f to 108f)),
        PIZZA_MUSHROOM to (28f to listOf(100f to 52f, 72f to 96f, 128f to 96f)),
        PIZZA_OLIVE to (20f to listOf(100f to 80f, 64f to 80f, 136f to 80f)),
    )
    val view = DishView { on ->
        val out = ArrayList<DishPiece>()
        out += DishPiece(null, Rect(26f, 6f, 174f, 154f), oval = true) {
            drawCircle(PlateWhite, 74f, Offset(100f, 80f))
            drawCircle(KitchenPlateRim, 74f, Offset(100f, 80f), style = rimStrokeOf(3f))
            drawCircle(KitchenPlateWell, 66f, Offset(100f, 80f))
        }
        if (PIZZA_DOUGH in on) out += DishPiece(PIZZA_DOUGH, Rect(38f, 18f, 162f, 142f), oval = true) {
            circle(100f, 80f, 62f, DoughEdge, rimWidth = 3f)
            circle(100f, 80f, 52f, DoughMiddle)
        }
        if (PIZZA_SAUCE in on) out += DishPiece(PIZZA_SAUCE, Rect(53f, 33f, 147f, 127f), oval = true) {
            drawPath(blobPath(100f, 80f, 47f, 3f, 7), SauceRed)
            line("M70,90C66,62 122,54 130,86", Color(0xFFFF9078), 4f, alpha = 0.7f)
        }
        if (PIZZA_CHEESE in on) out += DishPiece(PIZZA_CHEESE, Rect(59f, 39f, 141f, 121f), oval = true) {
            drawPath(blobPath(100f, 80f, 41f, 4f, 9), Color(0xFFFFD66B))
            listOf(
                listOf(84f, 70f, 4f, 2.6f), listOf(116f, 92f, 4.5f, 2.8f),
                listOf(112f, 64f, 3.5f, 2.4f), listOf(86f, 96f, 3.5f, 2.4f),
            ).forEach { (x, y, rx, ry) -> ellipse(x, y, rx, ry, 20f, Color(0xFFF2B734)) }
        }
        for (id in listOf(PIZZA_MUSHROOM, PIZZA_OLIVE, PIZZA_PEPPERONI)) {
            if (id !in on) continue
            val (size, points) = slots.getValue(id)
            val icon = icons.getValue(id)
            points.forEach { (x, y) ->
                out += DishPiece(id, Rect(x - size / 2, y - size / 2, x + size / 2, y + size / 2), oval = true) {
                    placeIcon(icon, x - size / 2, y - size / 2, size)
                }
            }
        }
        out
    }
    return DishSpec(dish, icons, view)
}

// --------------------------------------------------------------------- ice cream

private const val ICE_CONE = "cone"
private const val ICE_STRAWBERRY = "strawberry"
private const val ICE_CHOCOLATE = "chocolate"
private const val ICE_CHERRY = "cherry"
private const val ICE_SPRINKLES = "sprinkles"
private const val ICE_WAFER = "wafer"

private fun iceCream(): DishSpec {
    val dish = Dish(
        id = "ice-cream",
        base = ICE_CONE,
        shelf = listOf(ICE_CONE, ICE_STRAWBERRY, ICE_CHOCOLATE, ICE_CHERRY, ICE_SPRINKLES, ICE_WAFER),
    )
    val icons = mapOf(
        ICE_CONE to ConeIcon, ICE_STRAWBERRY to StrawberryIcon, ICE_CHOCOLATE to ChocolateIcon,
        ICE_CHERRY to CherryIcon, ICE_SPRINKLES to SprinklesIcon, ICE_WAFER to WaferIcon,
    )
    val sprinkleSpots = listOf(
        Triple(-17f, 13f, -30f) to Color(0xFFFF6B4A), Triple(-4f, 6f, 20f) to Color(0xFF4FC1E9),
        Triple(12f, 10f, -50f) to Color(0xFFFFD23F), Triple(-9f, 20f, 40f) to Color(0xFF35C46B),
        Triple(8f, 21f, 10f) to Color(0xFFFF9EC4), Triple(21f, 17f, -20f) to Color(0xFF4FC1E9),
    )
    val view = DishView { on ->
        val out = ArrayList<DishPiece>()
        val base = 138f
        // Small white stand so the cone tip has somewhere to rest.
        out += DishPiece(null, Rect(30f, 144f, 170f, 160f), oval = true) {
            drawOval(PlateWhite, Offset(30f, 144f), Size(140f, 16f))
            drawOval(KitchenPlateRim, Offset(30f, 144f), Size(140f, 16f), style = rimStrokeOf(3f))
            drawOval(KitchenPlateWell, Offset(52f, 147.5f), Size(96f, 7f))
        }
        var top = if (ICE_CONE in on) base - 40f else base - 2f
        var crest: Float? = null

        // Scoops are worked out first because the cherry, sprinkles and wafer sit on the top one.
        val scoops = ArrayList<DishPiece>()
        for (id in listOf(ICE_STRAWBERRY, ICE_CHOCOLATE)) {
            if (id !in on) continue
            val y = top - 48f
            crest = y + 6f
            val icon = icons.getValue(id)
            scoops += DishPiece(id, Rect(62f, y, 138f, y + 60f)) { placeIcon(icon, 62f, y, 76f, 60f) }
            top = top - 42f + 12f // scoops nest 12 into each other
        }
        if (ICE_WAFER in on) { // leans out behind the scoops
            val ty = crest ?: (top - 20f)
            out += DishPiece(ICE_WAFER, Rect(108f, ty - 26f, 170f, ty + 36f)) { placeIcon(WaferIcon, 108f, ty - 26f, 62f) }
        }
        if (ICE_CONE in on) {
            val y0 = base - 54f
            val conePath = "M72,${y0}L128,${y0}L104,${base - 4}Q100,${base + 2} 96,${base - 4}Z"
            out += DishPiece(ICE_CONE, Rect(68f, y0 - 4f, 132f, base + 2f)) {
                drawPath(svgPath(conePath), ConeBody)
                clipPath(svgPath(conePath)) {
                    listOf(52f, 66f, 80f, 94f, 108f, 122f).forEach { x ->
                        line("M$x,${y0 - 4}L${x + 34},${base + 4}M${x + 34},${y0 - 4}L$x,${base + 4}", ConeWaffle, 2.6f, alpha = 0.65f)
                    }
                }
                drawPath(svgPath(conePath), RimColor, style = rimStrokeOf(3f))
                rrect(68f, y0 - 4f, 64f, 11f, 5.5f, ConeRim, rimWidth = 3f)
            }
        }
        out += scoops
        if (ICE_CHERRY in on) {
            val ty = crest ?: top
            out += DishPiece(ICE_CHERRY, Rect(83f, (ty - 27f).coerceAtLeast(0f), 117f, ty + 7f)) { placeIcon(CherryIcon, 83f, ty - 27f, 34f) }
        }
        if (ICE_SPRINKLES in on) {
            val ty = crest ?: (top - 4f)
            out += DishPiece(ICE_SPRINKLES, Rect(78f, ty + 2f, 126f, ty + 28f)) {
                sprinkleSpots.forEach { (spot, color) -> capsule(100f + spot.first, ty + spot.second, spot.third, color, 10f, 4f) }
            }
        }
        // The stand's cup is drawn last so the cone tip sits inside it.
        out += DishPiece(null, Rect(80f, base - 8f, 120f, base + 10f)) {
            plateRim(svgPath("M80,${base - 8}H120L114,${base + 10}H86Z"))
        }
        out
    }
    return DishSpec(dish, icons, view)
}
