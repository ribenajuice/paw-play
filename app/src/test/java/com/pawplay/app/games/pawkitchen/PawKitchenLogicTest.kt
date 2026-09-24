package com.pawplay.app.games.pawkitchen

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

class PawKitchenLogicTest {

    private val burger = Dish("burger", "bun", listOf("bun", "patty", "cheese", "tomato", "lettuce", "pickle"))
    private val pizza = Dish("pizza", "dough", listOf("dough", "sauce", "cheese", "mushroom", "olive", "pepperoni"))
    private val iceCream = Dish("ice", "cone", listOf("cone", "strawberry", "chocolate", "cherry", "sprinkles", "wafer"))
    private val menu = listOf(burger, pizza, iceCream)

    /** A dish the game has never heard of: defined here, in a test, with no change to any game code. */
    private val pancakes = Dish("pancakes", "stack", listOf("stack", "syrup", "berries", "butter", "banana", "cream"))

    private fun stateFor(dish: Dish, order: List<String>, tray: List<String>) =
        newGame(listOf(dish), Random(1)).copy(order = Order(dish, order), tray = tray, onDish = emptySet())

    /** Builds the exact dish for the current order and serves it. */
    private fun KitchenState.buildAndServe(): KitchenState =
        order.ingredients.fold(this) { s, i -> s.add(i) }.serve()

    // --- ramp table -------------------------------------------------------

    @Test
    fun `ramp follows the PRD table and caps at 5 in the order and 6 in the tray`() {
        val expected = mapOf(
            0 to (2 to 3), 1 to (2 to 3),
            2 to (3 to 4), 3 to (3 to 4),
            4 to (4 to 5), 5 to (4 to 5),
            6 to (5 to 6), 7 to (5 to 6), 50 to (5 to 6), 10_000 to (5 to 6),
        )
        for ((served, sizes) in expected) {
            val step = rampFor(served)
            assertEquals("order size after $served served", sizes.first, step.orderSize)
            assertEquals("tray size after $served served", sizes.second, step.trayChoices)
        }
    }

    // --- order generation invariants -------------------------------------

    @Test
    fun `every customer over many seeds obeys the invariants`() {
        for (seed in 1..300) {
            val random = Random(seed)
            var state = newGame(menu, random)
            var previousOrder: Order? = null
            var previousCustomer = null as Any?
            for (customerNumber in 1..14) {
                val step = rampFor(state.customersServed)
                val order = state.order
                assertEquals("customer $customerNumber seed $seed", customerNumber - 1, state.customersServed)

                assertTrue("base is always in the order", order.dish.base in order.ingredients)
                assertEquals("no repeated ingredient", order.ingredients.size, order.ingredientSet.size)
                assertEquals("order size", step.orderSize, order.ingredients.size)
                assertTrue("order comes from its own shelf", order.dish.shelf.containsAll(order.ingredients))
                assertEquals(
                    "bubble is in shelf (bottom-to-top) order",
                    order.dish.shelf.filter { it in order.ingredientSet },
                    order.ingredients,
                )

                assertEquals("tray size", step.trayChoices, state.tray.size)
                assertEquals("tray has no repeats", state.tray.size, state.tray.toSet().size)
                assertTrue("tray holds everything needed", state.tray.containsAll(order.ingredients))
                val extras = state.tray.filter { it !in order.ingredientSet }
                assertEquals("exactly one extra", 1, extras.size)
                assertTrue("extra is from the same dish", extras.single() in order.dish.shelf)

                assertNotEquals("never the same order twice in a row", previousOrder, order)
                assertNotEquals("never the same animal twice in a row", previousCustomer, state.customer)
                previousOrder = order
                previousCustomer = state.customer

                state = state.buildAndServe().nextCustomer(random)
            }
        }
    }

    @Test
    fun `all three dishes and all six animals turn up`() {
        val random = Random(7)
        var state = newGame(menu, random)
        val dishes = mutableSetOf<String>()
        val animals = mutableSetOf<Any>()
        repeat(200) {
            dishes += state.order.dish.id
            animals += state.customer
            state = state.buildAndServe().nextCustomer(random)
        }
        assertEquals(setOf("burger", "pizza", "ice"), dishes)
        assertEquals(6, animals.size)
    }

    @Test
    fun `the first customer is always the simple two-ingredient order`() {
        for (seed in 1..100) {
            val state = newGame(menu, Random(seed))
            assertEquals(0, state.customersServed)
            assertEquals(2, state.order.ingredients.size)
            assertEquals(3, state.tray.size)
            assertTrue(state.onDish.isEmpty())
            assertEquals(Phase.BUILDING, state.phase)
        }
    }

    @Test
    fun `possibleOrders always includes the base and has the right count`() {
        // base + choose(5, k-1)
        assertEquals(5, possibleOrders(burger, 2).size)
        assertEquals(10, possibleOrders(burger, 3).size)
        assertEquals(10, possibleOrders(burger, 4).size)
        assertEquals(5, possibleOrders(burger, 5).size)
        assertTrue(possibleOrders(pizza, 4).all { "dough" in it.ingredients })
    }

    // --- exact match ------------------------------------------------------

    @Test
    fun `missing ingredient is not a match and serve does nothing`() {
        var s = stateFor(burger, listOf("bun", "patty"), listOf("tomato", "bun", "patty"))
        s = s.add("bun")
        assertFalse(s.isExactMatch)
        assertFalse(s.canServe)
        assertSame(s, s.serve())
    }

    @Test
    fun `an extra ingredient is not a match`() {
        var s = stateFor(burger, listOf("bun", "patty"), listOf("tomato", "bun", "patty"))
        s = s.add("bun").add("patty").add("tomato")
        assertFalse(s.isExactMatch)
        assertSame(s, s.serve())
        // taking the extra off wakes it again
        assertTrue(s.remove("tomato").canServe)
    }

    @Test
    fun `exactly the ordered ingredients is a match whatever order they were added in`() {
        val forwards = stateFor(burger, listOf("bun", "patty", "cheese"), listOf("cheese", "bun", "tomato", "patty"))
            .add("bun").add("patty").add("cheese")
        val backwards = stateFor(burger, listOf("bun", "patty", "cheese"), listOf("cheese", "bun", "tomato", "patty"))
            .add("cheese").add("patty").add("bun")
        assertTrue(forwards.isExactMatch)
        assertTrue(backwards.isExactMatch)
        assertEquals(Phase.SERVING, forwards.serve().phase)
    }

    @Test
    fun `an empty dish never matches`() {
        assertFalse(newGame(menu, Random(3)).isExactMatch)
    }

    @Test
    fun `serving twice serves once and brings exactly one next customer`() {
        val random = Random(9)
        val ready = newGame(menu, random).let { s -> s.order.ingredients.fold(s) { acc, i -> acc.add(i) } }
        val serving = ready.serve().serve().serve()
        assertEquals(Phase.SERVING, serving.phase)
        assertEquals(0, serving.customersServed)

        val next = serving.nextCustomer(random)
        assertEquals(1, next.customersServed)
        assertEquals(Phase.BUILDING, next.phase)
        assertTrue(next.onDish.isEmpty())

        // a stray second "next" from a double timer does not skip a customer
        assertSame(next, next.nextCustomer(random))
    }

    @Test
    fun `nextCustomer does nothing before a serve`() {
        val s = newGame(menu, Random(4))
        assertSame(s, s.nextCustomer(Random(4)))
    }

    // --- add / remove ------------------------------------------------------

    @Test
    fun `add puts an ingredient on the dish once and remove takes it off`() {
        var s = stateFor(burger, listOf("bun", "patty"), listOf("tomato", "bun", "patty"))
        s = s.add("tomato")
        assertEquals(setOf("tomato"), s.onDish)
        assertSame(s, s.add("tomato")) // mashing does not duplicate it
        s = s.remove("tomato")
        assertTrue(s.onDish.isEmpty())
        assertSame(s, s.remove("tomato"))
    }

    @Test
    fun `extras can be added and removed freely with no penalty`() {
        var s = stateFor(burger, listOf("bun", "patty"), listOf("tomato", "bun", "patty"))
        s = s.add("tomato").add("bun").remove("tomato").add("tomato").add("patty").remove("bun").add("bun")
        assertEquals(setOf("tomato", "bun", "patty"), s.onDish)
        assertEquals(Phase.BUILDING, s.phase)
    }

    @Test
    fun `toggle adds when off the dish and removes when on it`() {
        var s = stateFor(burger, listOf("bun", "patty"), listOf("tomato", "bun", "patty"))
        s = s.toggle("bun")
        assertEquals(setOf("bun"), s.onDish)
        s = s.toggle("bun")
        assertTrue(s.onDish.isEmpty())
    }

    @Test
    fun `an ingredient that is not in the tray cannot be added`() {
        val s = stateFor(burger, listOf("bun", "patty"), listOf("tomato", "bun", "patty"))
        assertSame(s, s.add("lettuce"))
        assertSame(s, s.add("not-a-thing"))
    }

    @Test
    fun `taps during the serve reaction are ignored`() {
        val random = Random(2)
        var s = newGame(menu, random)
        s = s.order.ingredients.fold(s) { acc, i -> acc.add(i) }.serve()
        val onDish = s.onDish
        val extra = s.tray.first { it !in onDish }
        assertSame(s, s.add(extra))
        assertSame(s, s.remove(s.onDish.first()))
        assertSame(s, s.toggle(extra))
        assertEquals(onDish, s.onDish)
    }

    @Test
    fun `a next customer starts with a clean dish`() {
        val random = Random(5)
        var s = newGame(menu, random)
        s = s.buildAndServe().nextCustomer(random)
        assertTrue(s.onDish.isEmpty())
        assertFalse(s.canServe)
    }

    // --- dishes are data --------------------------------------------------

    @Test
    fun `a dish defined only in test data works through the same generation and serve code`() {
        for (seed in 1..200) {
            val random = Random(seed)
            var state = newGame(menu + pancakes, random)
            var sawPancakes = false
            var previous: Order? = null
            repeat(30) {
                val order = state.order
                sawPancakes = sawPancakes || order.dish == pancakes
                assertTrue(order.dish.base in order.ingredients)
                assertEquals(rampFor(state.customersServed).orderSize, order.ingredients.size)
                assertEquals(order.ingredients.size + 1, state.tray.size)
                assertTrue(state.tray.containsAll(order.ingredients))
                assertNotEquals(previous, order)
                previous = order
                state = state.buildAndServe().also { assertEquals(Phase.SERVING, it.phase) }.nextCustomer(random)
            }
            assertTrue("pancakes should turn up within 30 customers (seed $seed)", sawPancakes)
        }
    }

    @Test
    fun `a menu of only the new dish plays end to end`() {
        val random = Random(11)
        var state = newGame(listOf(pancakes), random)
        repeat(20) {
            assertEquals("pancakes", state.order.dish.id)
            assertTrue(state.order.dish.base in state.order.ingredients)
            state = state.buildAndServe().nextCustomer(random)
        }
    }

    @Test
    fun `a small shelf still leaves room for the one extra`() {
        val toast = Dish("toast", "bread", listOf("bread", "jam", "butter"))
        val random = Random(8)
        var state = newGame(listOf(toast), random)
        repeat(20) {
            // ramp asks for 5 but a 3-item shelf can give at most 2 plus one extra
            assertTrue(state.order.ingredients.size <= 2)
            assertEquals(state.order.ingredients.size + 1, state.tray.size)
            assertTrue(state.tray.containsAll(state.order.ingredients))
            val previous = state.order
            state = state.buildAndServe().nextCustomer(random)
            assertNotEquals(previous, state.order)
        }
    }

    @Test
    fun `dish definitions reject shapes the tray cannot show`() {
        val bad = listOf(
            { Dish("a", "x", listOf("x", "y")) },                          // too small
            { Dish("b", "x", listOf("x", "y", "z", "1", "2", "3", "4")) }, // too big
            { Dish("c", "nope", listOf("x", "y", "z")) },                  // base not on shelf
            { Dish("d", "x", listOf("x", "x", "y")) },                     // repeat
        )
        for (make in bad) {
            val threw = try { make(); false } catch (e: IllegalArgumentException) { true }
            assertTrue(threw)
        }
    }
}
