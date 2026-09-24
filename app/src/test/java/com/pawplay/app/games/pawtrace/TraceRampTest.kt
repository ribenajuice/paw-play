package com.pawplay.app.games.pawtrace

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

class TraceRampTest {

    /** Plays [count] glyphs: returns what was shown, in order, with the state each was shown in. */
    private fun play(seed: Int, count: Int): List<RampState> {
        val random = Random(seed)
        var state = startRamp()
        val shown = ArrayList<RampState>()
        repeat(count) {
            shown += state
            state = state.completed(random)
        }
        return shown
    }

    private fun ids(states: List<RampState>) = states.map { it.current.id }

    @Test
    fun `the first glyph is the plain horizontal line, and stage 1 plays in order with no mixing`() {
        val shown = ids(play(1, 5))
        assertEquals(listOf("line-h", "line-v", "line-slant", "arch", "wave"), shown)
    }

    @Test
    fun `stage 2 opens with a new shape and then plays new, new, earlier, new, new, earlier, new`() {
        for (seed in 1..200) {
            val shown = play(seed, 5 + 7)
            val stage2 = shown.drop(5)
            val kinds = stage2.map { if (it.currentIsNew) "new" else "old" }
            assertEquals("seed $seed", listOf("new", "new", "old", "new", "new", "old", "new"), kinds)
            assertEquals(listOf("circle", "square", "triangle", "cross", "heart"), stage2.filter { it.currentIsNew }.map { it.current.id })
            // The mixed-in ones are lines and curves, never the one just played.
            stage2.forEachIndexed { i, st ->
                if (!st.currentIsNew) {
                    assertEquals(1, st.current.stage)
                    assertNotEquals(stage2[i - 1].current.id, st.current.id)
                }
            }
        }
    }

    @Test
    fun `numbers follow shapes in counting order with zero last, mixing from earlier stages only`() {
        for (seed in 1..100) {
            val shown = play(seed, 300)
            val digits = shown.filter { it.currentIsNew && it.current.stage == 3 }.map { it.current.id }
            assertEquals(
                listOf("digit-1", "digit-2", "digit-3", "digit-4", "digit-5", "digit-6", "digit-7", "digit-8", "digit-9", "digit-0"),
                digits,
            )
            // Nothing from a later stage ever shows up early.
            var top = 1
            for (st in shown) {
                if (st.currentIsNew) assertTrue("seed $seed: ${st.current.id} is new but stage ${st.current.stage} > $top+1", st.current.stage <= top + 1)
                if (st.currentIsNew) top = maxOf(top, st.current.stage)
                if (!st.currentIsNew && st.stage <= TraceGlyphs.STAGE_COUNT) assertTrue("mixed-in glyph is from an earlier stage", st.current.stage < st.stage)
            }
        }
    }

    @Test
    fun `the 26 letters come in the PRD order`() {
        for (seed in 1..100) {
            val shown = play(seed, 400)
            val letters = shown.filter { it.currentIsNew && it.current.stage == 4 }.map { it.current.id.removePrefix("letter-") }
            assertEquals("ILTHEFAVWMNZKXYOCUJDPBRGSQ", letters.joinToString(""))
        }
    }

    @Test
    fun `from stage 2 every third glyph is an earlier-stage one, until the endless mix`() {
        for (seed in 1..100) {
            val shown = play(seed, 300)
            val firstEndless = shown.indexOfFirst { it.stage == ENDLESS_STAGE }
            assertTrue("seed $seed reaches the endless mix", firstEndless > 0)
            val ramp = shown.take(firstEndless).drop(5) // from the first shape on
            ramp.forEachIndexed { i, st ->
                assertEquals("seed $seed position $i", (i + 1) % 3 == 0, !st.currentIsNew)
            }
        }
    }

    @Test
    fun `only new glyphs count towards advancing, so the ramp takes exactly 46 new glyphs plus the mixes`() {
        val shown = play(7, 400)
        val firstEndless = shown.indexOfFirst { it.stage == ENDLESS_STAGE }
        val before = shown.take(firstEndless)
        assertEquals(46 - 5 + 5, before.count { it.currentIsNew }) // 46 new glyphs: 5 + 5 + 10 + 26
        assertEquals(46, before.count { it.currentIsNew })
        // 5 lines, then 41 new glyphs with a mixed one after every second: 20 mixes (floor(41 / 2)) in between.
        assertEquals(5 + 41 + 20, before.size)
    }

    @Test
    fun `a stage advances only when every glyph in it has been completed once`() {
        var state = startRamp()
        val random = Random(3)
        assertEquals(1, state.stage)
        repeat(4) { state = state.completed(random); assertEquals(1, state.stage) }
        state = state.completed(random) // the fifth line/curve
        assertEquals(2, state.stage)
        assertEquals("circle", state.current.id)
    }

    @Test
    fun `after all 46 the mix is endless, random, over all 46, and never the same glyph twice`() {
        for (seed in 1..60) {
            val shown = play(seed, 46 + 20 + 1500)
            val endless = shown.filter { it.stage == ENDLESS_STAGE }
            assertTrue(endless.size >= 1400)
            endless.forEach { assertEquals(false, it.currentIsNew) }
            val all = shown.map { it.current.id }
            for (i in 1 until all.size) assertNotEquals("seed $seed position $i repeats", all[i - 1], all[i])
        }
    }

    @Test
    fun `the endless mix reaches every glyph, including the earliest`() {
        val seen = HashSet<String>()
        for (seed in 1..20) play(seed, 46 + 20 + 400).filter { it.stage == ENDLESS_STAGE }.forEach { seen += it.current.id }
        assertEquals(TraceGlyphs.all.map { it.id }.toSet(), seen)
    }

    @Test
    fun `the mix in stage 4 reaches every earlier glyph over many sessions`() {
        val seen = HashSet<String>()
        for (seed in 1..400) play(seed, 5 + 7 + 15 + 3 + 40).filter { !it.currentIsNew && it.stage == 4 }.forEach { seen += it.current.id }
        assertEquals(TraceGlyphs.all.filter { it.stage < 4 }.map { it.id }.toSet(), seen)
    }

    @Test
    fun `the same seed always gives the same session, and different seeds differ in their mixed-in glyphs`() {
        assertEquals(ids(play(11, 120)), ids(play(11, 120)))
        assertNotEquals(ids(play(11, 120)), ids(play(12, 120)))
    }

    @Test
    fun `the ramp state is a plain value, completing does not change the state it came from`() {
        val start = startRamp()
        val next = start.completed(Random(1))
        assertEquals("line-h", start.current.id)
        assertEquals(emptySet<String>(), start.completedNew)
        assertEquals(setOf("line-h"), next.completedNew)
    }
}
