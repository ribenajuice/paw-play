package com.pawplay.app.games.pawkitchen

import com.pawplay.app.games.pawmatch.Critter
import kotlin.random.Random

/**
 * Pure game-state logic for Paw Kitchen, kept free of Android/Compose so it
 * is covered by fast JVM unit tests (see PawKitchenLogicTest), the same way
 * PawMatchLogic.kt is. The Composable UI in PawKitchenGame.kt only ever calls
 * into this — it never re-implements the rules itself.
 *
 * A dish is DATA: an id, a base ingredient and a shelf of up to 6 ingredient
 * ids. Nothing in this file knows what a burger, a pizza or an ice cream is,
 * so adding a dish later (see KitchenMenu.kt) never touches ordering, the
 * tray, serving or the ramp. Ingredient ids only need to be unique within
 * their own dish's shelf. The pictures live in KitchenMenu.kt, not here.
 */

const val MIN_SHELF_SIZE = 3
const val MAX_SHELF_SIZE = 6

/** One thing on the menu. [shelf] is bottom-to-top: it is the order pictures appear in the bubble. */
data class Dish(val id: String, val base: String, val shelf: List<String>) {
    init {
        require(shelf.size in MIN_SHELF_SIZE..MAX_SHELF_SIZE) { "shelf of $id must hold $MIN_SHELF_SIZE..$MAX_SHELF_SIZE ingredients" }
        require(shelf.toSet().size == shelf.size) { "shelf of $id has a repeated ingredient" }
        require(base in shelf) { "base of $id must be on its shelf" }
    }
}

/** What a customer asked for: [ingredients] is in shelf order (base first) and never has repeats. */
data class Order(val dish: Dish, val ingredients: List<String>) {
    val ingredientSet: Set<String> get() = ingredients.toSet()
}

/**
 * The difficulty ramp (docs/PRD.md, Milestone 3): how many ingredients the
 * order holds and how many tiles the tray shows, always one extra.
 */
data class RampStep(val orderSize: Int) {
    val trayChoices: Int get() = orderSize + EXTRAS_IN_TRAY
}

const val EXTRAS_IN_TRAY = 1
const val MAX_ORDER_SIZE = 5

/** [customersServed] is how many customers have already been served this session (0 for the first customer). */
fun rampFor(customersServed: Int): RampStep =
    RampStep(orderSize = (2 + customersServed.coerceAtLeast(0) / 2).coerceAtMost(MAX_ORDER_SIZE))

enum class Phase {
    /** The child is building; taps on ingredients and serve are live. */
    BUILDING,

    /** The serve reaction is playing; every tap is ignored until [KitchenState.nextCustomer]. */
    SERVING,
}

data class KitchenState(
    val menu: List<Dish>,
    val customersServed: Int,
    val customer: Critter,
    val order: Order,
    /** Ingredient ids on screen, in a random but fixed-for-this-customer position order. */
    val tray: List<String>,
    /** Ingredients currently on the dish. A set, so an ingredient can never be on it twice. */
    val onDish: Set<String> = emptySet(),
    val phase: Phase = Phase.BUILDING,
) {
    /** Exact match only: nothing missing and nothing extra. Order of adding never matters. */
    val isExactMatch: Boolean get() = onDish == order.ingredientSet
    val canServe: Boolean get() = phase == Phase.BUILDING && isExactMatch
}

fun newGame(menu: List<Dish>, random: Random = Random.Default): KitchenState =
    dealCustomer(menu = menu, customersServed = 0, previousOrder = null, previousCustomer = null, random = random)

/** Serve is a silent no-op unless the dish is an exact match; a second tap while serving does nothing. */
fun KitchenState.serve(): KitchenState =
    if (canServe) copy(phase = Phase.SERVING) else this

/** Only valid after [serve]; brings exactly one next customer. Anything else returns the state unchanged. */
fun KitchenState.nextCustomer(random: Random = Random.Default): KitchenState {
    if (phase != Phase.SERVING) return this
    return dealCustomer(
        menu = menu,
        customersServed = customersServed + 1,
        previousOrder = order,
        previousCustomer = customer,
        random = random,
    )
}

/** Puts a tray ingredient on the dish; ignored if it is not in the tray, already there, or a serve is playing. */
fun KitchenState.add(ingredient: String): KitchenState =
    if (phase != Phase.BUILDING || ingredient !in tray || ingredient in onDish) this
    else copy(onDish = onDish + ingredient)

fun KitchenState.remove(ingredient: String): KitchenState =
    if (phase != Phase.BUILDING || ingredient !in onDish) this
    else copy(onDish = onDish - ingredient)

/** One tap on a tray slot: on the dish means take it off, otherwise put it on. */
fun KitchenState.toggle(ingredient: String): KitchenState =
    if (ingredient in onDish) remove(ingredient) else add(ingredient)

/**
 * Every possible order for [dish] at [orderSize] ingredients: the base plus
 * any (orderSize - 1) of the other ingredients, each in shelf order.
 */
fun possibleOrders(dish: Dish, orderSize: Int): List<Order> {
    val others = dish.shelf.filter { it != dish.base }
    val toppings = (orderSize - 1).coerceIn(0, others.size)
    return combinations(others, toppings).map { picked ->
        val chosen = picked.toSet() + dish.base
        Order(dish, dish.shelf.filter { it in chosen })
    }
}

private fun <T> combinations(items: List<T>, k: Int): List<List<T>> = when {
    k == 0 -> listOf(emptyList())
    items.size < k -> emptyList()
    else -> combinations(items.drop(1), k - 1).map { listOf(items.first()) + it } + combinations(items.drop(1), k)
}

/**
 * Deals the next customer: a different animal than [previousCustomer], and a
 * random dish + ingredient set (within the ramp step for [customersServed])
 * that is never identical to [previousOrder]. Picks from the full list of
 * candidates rather than re-rolling until it differs, so it cannot loop.
 */
private fun dealCustomer(
    menu: List<Dish>,
    customersServed: Int,
    previousOrder: Order?,
    previousCustomer: Critter?,
    random: Random,
): KitchenState {
    require(menu.isNotEmpty()) { "menu is empty" }
    val ramp = rampFor(customersServed)

    // A shelf must leave room for the one extra, so an order is never longer than the shelf minus one.
    fun ordersFor(dish: Dish) = possibleOrders(dish, ramp.orderSize.coerceAtMost(dish.shelf.size - EXTRAS_IN_TRAY))

    // Dish first (every dish equally likely), then an ingredient set that is not a repeat of the last order.
    val fresh = menu.map { dish -> ordersFor(dish).filter { it != previousOrder } }.filter { it.isNotEmpty() }
    // A menu with one dish and one possible order has nothing else to offer; repeating beats crashing.
    val order = (fresh.ifEmpty { menu.map { ordersFor(it) } }).random(random).random(random)

    val extra = order.dish.shelf.filter { it !in order.ingredientSet }.random(random)
    val tray = (order.ingredients + extra).shuffled(random)

    val customer = Critter.entries.filter { it != previousCustomer }.random(random)
    return KitchenState(
        menu = menu,
        customersServed = customersServed,
        customer = customer,
        order = order,
        tray = tray,
    )
}
