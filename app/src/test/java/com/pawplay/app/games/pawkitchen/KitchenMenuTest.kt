package com.pawplay.app.games.pawkitchen

import androidx.compose.ui.geometry.Offset
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The menu is content, so these check the content is complete and usable
 * (every shelf ingredient has a picture and a place on the dish) and that the
 * "tap it on the dish to take it off" hit-testing picks the right piece.
 * Only geometry is touched, never drawing, so no Android runtime is needed.
 */
class KitchenMenuTest {

    @Test
    fun `the starting menu is burger, pizza and ice cream with six ingredients each`() {
        assertEquals(listOf("burger", "pizza", "ice-cream"), KitchenMenu.dishes.map { it.id })
        KitchenMenu.dishes.forEach { assertEquals(6, it.shelf.size) }
    }

    @Test
    fun `every shelf ingredient has a picture and no picture is left over`() {
        for (spec in KitchenMenu.specs) {
            assertEquals("pictures for ${spec.dish.id}", spec.dish.shelf.toSet(), spec.icons.keys)
        }
    }

    @Test
    fun `every ingredient appears on the dish when added and nothing draws for an empty dish`() {
        for (spec in KitchenMenu.specs) {
            val empty = spec.view.pieces(emptySet())
            assertTrue("only the plate on an empty ${spec.dish.id}", empty.all { it.ingredient == null })
            for (ingredient in spec.dish.shelf) {
                val pieces = spec.view.pieces(setOf(ingredient))
                assertTrue("$ingredient shows up on ${spec.dish.id}", pieces.any { it.ingredient == ingredient })
            }
            val full = spec.view.pieces(spec.dish.shelf.toSet())
            assertEquals(spec.dish.shelf.toSet(), full.mapNotNull { it.ingredient }.toSet())
        }
    }

    @Test
    fun `every piece sits inside the dish canvas so the biggest stack still fits`() {
        for (spec in KitchenMenu.specs) {
            for (piece in spec.view.pieces(spec.dish.shelf.toSet())) {
                val b = piece.bounds
                assertTrue("${spec.dish.id}/${piece.ingredient} left", b.left >= 0f)
                assertTrue("${spec.dish.id}/${piece.ingredient} top", b.top >= 0f)
                assertTrue("${spec.dish.id}/${piece.ingredient} right", b.right <= DISH_WIDTH)
                assertTrue("${spec.dish.id}/${piece.ingredient} bottom", b.bottom <= DISH_HEIGHT)
            }
        }
    }

    @Test
    fun `tapping a piece on the dish finds that ingredient and the topmost one wins`() {
        val burger = KitchenMenu.specs.first { it.dish.id == "burger" }
        val pieces = burger.view.pieces(setOf("bun", "patty"))
        val crown = pieces.last { it.ingredient == "bun" }
        val patty = pieces.first { it.ingredient == "patty" }
        assertEquals("bun", hitTestDish(pieces, crown.bounds.center))
        assertEquals("patty", hitTestDish(pieces, patty.bounds.center))
    }

    @Test
    fun `tapping empty counter or the plate itself takes nothing off`() {
        val burger = KitchenMenu.specs.first { it.dish.id == "burger" }
        val pieces = burger.view.pieces(setOf("bun"))
        assertNull(hitTestDish(pieces, Offset(2f, 2f)))
        assertNull(hitTestDish(pieces, Offset(100f, 151f))) // the plate
    }

    @Test
    fun `pizza toppings only count inside their round shape`() {
        val pizza = KitchenMenu.specs.first { it.dish.id == "pizza" }
        val pieces = pizza.view.pieces(setOf("dough", "olive"))
        val olive = pieces.first { it.ingredient == "olive" }
        assertEquals("olive", hitTestDish(pieces, olive.bounds.center))
        // the corner of the olive's square is outside the olive, so it falls through to the dough beneath
        val corner = Offset(olive.bounds.left + 0.5f, olive.bounds.top + 0.5f)
        assertEquals("dough", hitTestDish(pieces, corner))
    }

    @Test
    fun `tray rows follow the design system layouts`() {
        assertEquals(listOf(3), trayRows(3))
        assertEquals(listOf(2, 2), trayRows(4))
        assertEquals(listOf(3, 2), trayRows(5))
        assertEquals(listOf(3, 3), trayRows(6))
    }

    // --- story 23: ingredients differ by SHAPE, not only by colour ---------------------------------

    /** Two outlines sharing more than this fraction of their combined area count as "the same silhouette". */
    private val maxOutlineOverlap = 0.80f

    /** Pairs allowed to look alike in outline because a visible mark tells them apart (design system: seed dots vs chips). */
    private val differByMarkInstead = setOf(setOf("strawberry", "chocolate"))

    @Test
    fun `every ingredient has an outline and no outline is left over`() {
        for (spec in KitchenMenu.specs) assertEquals(spec.dish.shelf.toSet(), spec.shapes.keys)
    }

    @Test
    fun `no two ingredients in a dish share an outline`() {
        val tooAlike = ArrayList<String>()
        for (spec in KitchenMenu.specs) {
            val ids = spec.dish.shelf
            for (i in ids.indices) for (j in i + 1 until ids.size) {
                if (setOf(ids[i], ids[j]) in differByMarkInstead) continue
                val overlap = silhouetteOverlap(spec.shapes.getValue(ids[i]), spec.shapes.getValue(ids[j]))
                if (overlap > maxOutlineOverlap) tooAlike += "${spec.dish.id}: ${ids[i]} vs ${ids[j]} = $overlap"
            }
        }
        assertTrue("outlines too alike: $tooAlike", tooAlike.isEmpty())
    }

    @Test
    fun `the overlap measure itself works`() {
        val disc = Silhouette(Circle(50f, 50f, 30f))
        assertEquals(1f, silhouetteOverlap(disc, disc), 0.001f)
        assertEquals(0f, silhouetteOverlap(disc, Silhouette(Circle(90f, 90f, 5f))), 0.001f)
        // a disc of half the radius covers a quarter of the area
        assertEquals(0.25f, silhouetteOverlap(disc, Silhouette(Circle(50f, 50f, 15f))), 0.02f)
        // turning a tall oval a quarter turn about the centre gives the wide oval
        val tall = Silhouette(Oval(50f, 50f, 20f, 40f))
        val wide = Silhouette(Oval(50f, 50f, 40f, 20f))
        assertEquals(1f, silhouetteOverlap(Silhouette(listOf(Oval(50f, 50f, 20f, 40f)), rotation = 90f), wide), 0.01f)
        assertTrue(silhouetteOverlap(tall, wide) < 0.5f)
    }

    @Test
    fun `path data flattens to the right outline`() {
        val square = polyFromPath("M10,10H90V90H10Z")
        assertTrue(square.contains(50f, 50f))
        assertTrue(!square.contains(95f, 50f))
        val dome = polyFromPath("M10,54C10,24 28,10 50,10C72,10 90,24 90,54Z")
        assertTrue(dome.contains(50f, 40f))
        assertTrue(!dome.contains(50f, 5f))
    }

    @Test
    fun `olive, pepperoni and sauce are clearly different outlines`() {
        val pizza = KitchenMenu.specs.first { it.dish.id == "pizza" }.shapes
        fun overlap(a: String, b: String) = silhouetteOverlap(pizza.getValue(a), pizza.getValue(b))
        assertTrue(overlap("olive", "pepperoni") < 0.65f)
        assertTrue(overlap("sauce", "pepperoni") < 0.75f)
        assertTrue(overlap("sauce", "olive") < 0.65f)
    }
}
