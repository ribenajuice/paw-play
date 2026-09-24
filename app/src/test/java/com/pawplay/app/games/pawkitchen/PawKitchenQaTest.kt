package com.pawplay.app.games.pawkitchen

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import com.pawplay.app.games.pawmatch.Critter
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

/**
 * QA pass over Paw Kitchen's rules: many-seed generation, a model-checked random
 * mash of every tap the UI can send, and menu-as-content checks. Everything is
 * seeded, so a failure names a seed and reproduces exactly.
 */
class PawKitchenQaTest {

    private val burger = Dish("burger", "bun", listOf("bun", "patty", "cheese", "tomato", "lettuce", "pickle"))
    private val pizza = Dish("pizza", "dough", listOf("dough", "sauce", "cheese", "mushroom", "olive", "pepperoni"))
    private val ice = Dish("ice-cream", "cone", listOf("cone", "strawberry", "chocolate", "cherry", "sprinkles", "wafer"))
    private val realMenu = listOf(burger, pizza, ice)

    // Fixtures the real menu does not have: minimum shelf (3), and middle sizes (4, 5).
    private val toast = Dish("toast", "bread", listOf("bread", "jam", "butter"))
    private val soup = Dish("soup", "broth", listOf("broth", "carrot", "pea", "noodle"))
    private val sandwich = Dish("sandwich", "roll", listOf("roll", "ham", "egg", "leaf", "onion"))
    private val pancakes = Dish("pancakes", "stack", listOf("stack", "syrup", "berries", "butter", "banana", "cream"))

    private fun KitchenState.buildExact(): KitchenState =
        order.ingredients.fold(this) { s, i -> s.add(i) }

    /** Every rule from PRD stories 20, 22 and 26 that can be checked on one dealt customer. */
    private fun assertDealtCorrectly(state: KitchenState, label: String) {
        val order = state.order
        val dish = order.dish
        assertTrue("$label: dish is on the menu", dish in state.menu)
        assertTrue("$label: base always in the order", dish.base in order.ingredients)
        assertEquals("$label: no repeats", order.ingredients.size, order.ingredientSet.size)
        assertTrue("$label: order from own shelf", dish.shelf.containsAll(order.ingredients))
        assertEquals("$label: shelf order", dish.shelf.filter { it in order.ingredientSet }, order.ingredients)
        val expectedSize = minOf(rampFor(state.customersServed).orderSize, dish.shelf.size - EXTRAS_IN_TRAY)
        assertEquals("$label: order size", expectedSize, order.ingredients.size)
        assertEquals("$label: tray size", expectedSize + 1, state.tray.size)
        assertEquals("$label: tray no repeats", state.tray.size, state.tray.toSet().size)
        assertTrue("$label: every order ingredient is in the tray", state.tray.containsAll(order.ingredients))
        val extras = state.tray.filter { it !in order.ingredientSet }
        assertEquals("$label: exactly one extra", 1, extras.size)
        assertTrue("$label: extra from the same shelf", extras.single() in dish.shelf)
        assertTrue("$label: fresh dish", state.onDish.isEmpty())
        assertEquals("$label: building", Phase.BUILDING, state.phase)
        assertFalse("$label: an empty dish is never servable", state.canServe)
    }

    private fun playThrough(menu: List<Dish>, seeds: IntRange, customers: Int) {
        for (seed in seeds) {
            val random = Random(seed)
            var state = newGame(menu, random)
            var prevOrder: Order? = null
            var prevAnimal: Critter? = null
            for (n in 1..customers) {
                val label = "menu=${menu.map { it.id }} seed=$seed customer=$n"
                assertEquals(label, n - 1, state.customersServed)
                assertDealtCorrectly(state, label)
                assertNotEquals("$label: same order twice in a row", prevOrder, state.order)
                assertNotEquals("$label: same animal twice in a row", prevAnimal, state.customer)
                prevOrder = state.order
                prevAnimal = state.customer
                val served = state.buildExact().serve()
                assertEquals("$label: exact dish serves", Phase.SERVING, served.phase)
                state = served.nextCustomer(random)
            }
        }
    }

    // --- 1. order generation ---------------------------------------------

    @Test
    fun `real menu - 3000 seeds of 24 customers obey every generation rule`() {
        playThrough(realMenu, 1..3000, 24)
    }

    @Test
    fun `a menu with only one dish never repeats an order or gets stuck`() {
        for (dish in listOf(burger, pizza, ice, pancakes, sandwich, soup, toast)) {
            playThrough(listOf(dish), 1..400, 24)
        }
    }

    @Test
    fun `a minimum-size shelf of 3 gives two-ingredient orders for ever and still alternates`() {
        val random = Random(99)
        var state = newGame(listOf(toast), random)
        val seen = mutableSetOf<Order>()
        repeat(60) {
            assertEquals(2, state.order.ingredients.size)
            assertEquals(3, state.tray.size)
            seen += state.order
            state = state.buildExact().serve().nextCustomer(random)
        }
        // bread + jam, bread + butter are the only two possible orders: they must strictly alternate.
        assertEquals(2, seen.size)
    }

    @Test
    fun `mixed shelf sizes on one menu keep every dish makeable through the whole ramp`() {
        playThrough(listOf(toast, soup, sandwich, burger, pizza), 1..1000, 24)
    }

    @Test
    fun `every possible order at each ramp step actually turns up, and every dish is about equally likely`() {
        val stepToCustomers = mapOf(2 to 0, 3 to 2, 4 to 4, 5 to 6)
        for ((size, served) in stepToCustomers) {
            val seen = mutableSetOf<Order>()
            val dishCount = mutableMapOf<String, Int>()
            for (seed in 1..6000) {
                val random = Random(seed)
                // Reach the step with the real code path rather than by constructing state by hand.
                var s = newGame(realMenu, random)
                repeat(served) { s = s.buildExact().serve().nextCustomer(random) }
                assertEquals(size, s.order.ingredients.size)
                seen += s.order
                dishCount.merge(s.order.dish.id, 1, Int::plus)
            }
            val possible = realMenu.sumOf { possibleOrders(it, size).size }
            assertEquals("all orders reachable at size $size", possible, seen.size)
            for ((id, n) in dishCount) assertTrue("dish $id share at size $size was $n / 6000", n in 1600..2400)
        }
    }

    @Test
    fun `a dish with a single-item shelf or a base-only shelf cannot be defined`() {
        val bad = listOf<() -> Dish>(
            { Dish("base-only", "plate", listOf("plate")) },
            { Dish("empty", "x", emptyList()) },
        )
        for (make in bad) {
            val threw = try { make(); false } catch (e: IllegalArgumentException) { true }
            assertTrue("a dish too small for base + one topping + one extra must be rejected up front", threw)
        }
    }

    // --- 2. exact match, model-checked against everything the UI can send ---

    /** The whole tap vocabulary of the screen: tray tile, dish piece, serve button, the 2s timer. */
    private sealed class Tap {
        data class Toggle(val ingredient: String) : Tap()
        data class Remove(val ingredient: String) : Tap()
        object Serve : Tap()
        object Timer : Tap()
    }

    @Test
    fun `serve is asleep for every non-exact dish and awake for every exact one across random mashing`() {
        for (seed in 1..400) {
            val random = Random(seed)
            val menu = if (seed % 3 == 0) listOf(toast, burger) else realMenu
            var s = newGame(menu, random)
            var model = emptySet<String>()
            var serving = false
            var served = 0
            repeat(400) { step ->
                val everything = s.order.dish.shelf + menu.flatMap { it.shelf } // includes ids not in the tray
                val tap: Tap = when (random.nextInt(10)) {
                    in 0..3 -> Tap.Toggle(everything.random(random))
                    in 4..5 -> Tap.Remove(everything.random(random))
                    in 6..8 -> Tap.Serve
                    else -> Tap.Timer
                }
                val label = "seed=$seed step=$step tap=$tap"
                val trayBefore = s.tray
                val orderBefore = s.order
                when (tap) {
                    is Tap.Toggle -> {
                        s = s.toggle(tap.ingredient)
                        if (!serving && tap.ingredient in trayBefore) model = if (tap.ingredient in model) model - tap.ingredient else model + tap.ingredient
                    }
                    is Tap.Remove -> {
                        s = s.remove(tap.ingredient)
                        if (!serving) model = model - tap.ingredient
                    }
                    Tap.Serve -> {
                        val shouldServe = !serving && model == orderBefore.ingredientSet
                        val after = s.serve()
                        assertEquals("$label: serve wakes only on exact match", shouldServe, !serving && after.phase == Phase.SERVING)
                        if (!shouldServe) assertSame("$label: asleep serve is a silent no-op", s, after)
                        s = after
                        if (shouldServe) serving = true
                    }
                    Tap.Timer -> {
                        val after = s.nextCustomer(random)
                        if (serving) {
                            served++
                            serving = false
                            model = emptySet()
                            assertEquals(label, served, after.customersServed)
                            assertNotEquals("$label: order repeated", orderBefore, after.order)
                            assertDealtCorrectly(after, label)
                        } else {
                            assertSame("$label: a timer with no serve pending must not skip a customer", s, after)
                        }
                        s = after
                    }
                }
                assertEquals("$label: dish contents", model, s.onDish)
                assertTrue("$label: dish only ever holds tray items", s.tray.containsAll(s.onDish))
                assertEquals("$label: phase", if (serving) Phase.SERVING else Phase.BUILDING, s.phase)
                assertEquals(
                    "$label: canServe iff exactly the order is on the dish",
                    !serving && model == s.order.ingredientSet,
                    s.canServe,
                )
                assertEquals(label, served, s.customersServed)
            }
        }
    }

    @Test
    fun `remove and re-add sequences never leave serve in the wrong state`() {
        val base = newGame(listOf(burger), Random(5)).let { it.copy(order = Order(burger, listOf("bun", "patty", "cheese")), tray = listOf("cheese", "bun", "tomato", "patty")) }
        var s = base.add("bun").add("patty").add("cheese")
        assertTrue(s.canServe)
        s = s.remove("patty"); assertFalse(s.canServe)
        s = s.add("tomato"); assertFalse(s.canServe)          // one missing AND one extra: same count as the order
        s = s.add("patty"); assertFalse(s.canServe)           // right count + 1, extra on
        s = s.remove("tomato"); assertTrue(s.canServe)        // back to exact
        s = s.toggle("cheese").toggle("cheese"); assertTrue(s.canServe)
        s = s.toggle("bun").toggle("tomato"); assertFalse(s.canServe) // swap of one for an extra, same size
        s = s.toggle("tomato").toggle("bun"); assertTrue(s.canServe)
    }

    @Test
    fun `whatever the child has piled on, taking things off and putting the order on always reaches a servable dish`() {
        for (seed in 1..300) {
            val random = Random(seed)
            var s = newGame(realMenu, random)
            repeat(15) {
                // pile on a random mess
                s.tray.shuffled(random).take(random.nextInt(0, s.tray.size + 1)).forEach { s = s.toggle(it) }
                // now fix it the way a child would: take off what does not belong, put on what is missing
                s.onDish.filter { it !in s.order.ingredientSet }.forEach { s = s.toggle(it) }
                s.order.ingredients.filter { it !in s.onDish }.forEach { s = s.toggle(it) }
                assertTrue("seed=$seed", s.canServe)
                s = s.serve().nextCustomer(random)
            }
        }
    }

    // --- 3. state machine, as the screen drives it -------------------------

    @Test
    fun `mashing serve and tiles through the reaction serves once, brings exactly one customer, and starts clean`() {
        val random = Random(21)
        var s = newGame(realMenu, random).let { it.buildExact() }
        val before = s.customersServed
        s = s.serve()
        val frozenDish = s.onDish
        repeat(50) {
            s = s.serve()
            s.tray.forEach { t -> s = s.toggle(t) }
            frozenDish.forEach { d -> s = s.remove(d) }
        }
        assertEquals(Phase.SERVING, s.phase)
        assertEquals(before, s.customersServed)
        assertEquals(frozenDish, s.onDish)
        s = s.nextCustomer(random)
        s = s.nextCustomer(random).nextCustomer(random) // extra timers
        assertEquals(before + 1, s.customersServed)
        assertTrue(s.onDish.isEmpty())
        assertEquals(Phase.BUILDING, s.phase)
    }

    @Test
    fun `there is no lose or dead-end state - customersServed only ever goes up by one per serve, from any build state`() {
        // A customer can always be served: from ANY reachable build state one tap sequence reaches exact match.
        for (seed in 1..500) {
            val random = Random(seed)
            var s = newGame(realMenu, random)
            repeat(30) { n ->
                assertEquals(n, s.customersServed)
                // arbitrary noise first
                repeat(random.nextInt(0, 12)) { s = s.toggle(s.tray.random(random)) }
                // reset the dish like a child tapping every piece off
                s.onDish.toList().forEach { s = s.remove(it) }
                assertTrue(s.onDish.isEmpty())
                s = s.buildExact()
                assertTrue(s.canServe)
                s = s.serve().nextCustomer(random)
            }
        }
    }

    // --- 4. menu is content ------------------------------------------------

    private fun fakeSpec(dish: Dish): DishSpec {
        val icons = dish.shelf.associateWith<String, IngredientIcon> { { } }
        val view = DishView { on ->
            dish.shelf.filter { it in on }.mapIndexed { i, id ->
                DishPiece(id, Rect(10f, 10f + i * 20f, 190f, 28f + i * 20f)) { }
            }
        }
        val shapes = dish.shelf.associateWith { Silhouette(Circle(50f, 50f, 30f)) }
        return DishSpec(dish, icons, shapes, view)
    }

    private fun assertSpecComplete(spec: DishSpec) {
        val id = spec.dish.id
        assertEquals("$id: a picture for every shelf item and none spare", spec.dish.shelf.toSet(), spec.icons.keys)
        assertTrue("$id: empty dish draws no ingredient", spec.view.pieces(emptySet()).all { it.ingredient == null })
        assertEquals("$id: full dish draws every ingredient", spec.dish.shelf.toSet(), spec.view.pieces(spec.dish.shelf.toSet()).mapNotNull { it.ingredient }.toSet())
        for (piece in spec.view.pieces(spec.dish.shelf.toSet())) {
            val b = piece.bounds
            assertTrue("$id/${piece.ingredient}: inside the dish canvas", b.left >= 0f && b.top >= 0f && b.right <= DISH_WIDTH && b.bottom <= DISH_HEIGHT)
        }
    }

    @Test
    fun `menu ids are unique and every dish has a spec found by id`() {
        val ids = KitchenMenu.specs.map { it.dish.id }
        assertEquals("a duplicate dish id would make specFor draw the wrong dish", ids.toSet().size, ids.size)
        KitchenMenu.dishes.forEach { assertSame(it, KitchenMenu.specFor(it).dish) }
        KitchenMenu.specs.forEach { assertSpecComplete(it) }
    }

    @Test
    fun `a new dish spec of any legal shelf size drives the same generation and serve path as the built-in ones`() {
        val specs = KitchenMenu.specs + listOf(pancakes, soup, toast).map { fakeSpec(it) }
        specs.forEach { assertSpecComplete(it) }
        val menu = specs.map { it.dish }
        val byId = specs.associateBy { it.dish.id }
        for (seed in 1..300) {
            val random = Random(seed)
            var s = newGame(menu, random)
            repeat(20) {
                val spec = byId.getValue(s.order.dish.id) // what KitchenScene does via specFor
                // every tray tile and every bubble picture has a drawing (KitchenScene calls getValue on both)
                s.tray.forEach { assertTrue(it in spec.icons) }
                s.order.ingredients.forEach { assertTrue(it in spec.icons) }
                s = s.buildExact().also { assertTrue(it.canServe) }.serve().nextCustomer(random)
            }
        }
    }

    @Test
    fun `every ingredient stays tappable on the dish for every subset, so a child can always tap it off`() {
        for (spec in KitchenMenu.specs) {
            val shelf = spec.dish.shelf
            for (mask in 1 until (1 shl shelf.size)) {
                val on = shelf.filterIndexed { i, _ -> mask and (1 shl i) != 0 }.toSet()
                val pieces = spec.view.pieces(on)
                val reachable = mutableSetOf<String>()
                var y = 0f
                while (y <= DISH_HEIGHT) {
                    var x = 0f
                    while (x <= DISH_WIDTH) {
                        hitTestDish(pieces, Offset(x, y))?.let { reachable += it }
                        x += 2f
                    }
                    y += 2f
                }
                assertEquals("${spec.dish.id} with $on: every piece on the plate must be tappable", on, reachable)
            }
        }
    }
}
