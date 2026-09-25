package com.pawplay.app.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** The good-game screen's touch rule (docs/PRD.md story 72): asleep for 0.6s, then acts exactly once. */
class GoodGameGateTest {

    @Test
    fun `the guard is 0_6 seconds`() {
        assertEquals(600L, GoodGameGate.GUARD_MS)
    }

    @Test
    fun `touches in the first 0_6s do nothing`() {
        val gate = GoodGameGate()
        for (t in listOf(0L, 1L, 100L, 300L, 599L)) assertFalse("at $t", gate.accept(t))
    }

    @Test
    fun `it wakes at exactly 600ms and fires exactly once`() {
        val gate = GoodGameGate()
        assertTrue(gate.accept(600))
        for (t in listOf(600L, 601L, 700L, 5_000L, 60_000L)) assertFalse("second tap at $t", gate.accept(t))
    }

    @Test
    fun `mashing early does not use up the one action`() {
        val gate = GoodGameGate()
        repeat(50) { assertFalse(gate.accept(it * 11L)) } // 0..539ms
        assertTrue("the first tap after waking still works", gate.accept(700))
        assertFalse(gate.accept(701))
    }

    @Test
    fun `a double tap on play again starts one game`() {
        val gate = GoodGameGate()
        var started = 0
        for (t in listOf(900L, 960L)) if (gate.accept(t)) started++
        assertEquals(1, started)
    }

    @Test
    fun `home and play again together do only the first, because they share one gate`() {
        val gate = GoodGameGate()
        var playAgain = 0
        var home = 0
        if (gate.accept(1000)) playAgain++
        if (gate.accept(1005)) home++
        assertEquals(1, playAgain)
        assertEquals(0, home)
    }

    @Test
    fun `every new screen has a fresh gate`() {
        val first = GoodGameGate()
        assertTrue(first.accept(700))
        val second = GoodGameGate()
        assertFalse(second.accept(100))
        assertTrue(second.accept(700))
    }

    @Test
    fun `the guard length can be set for a test`() {
        val gate = GoodGameGate(guardMs = 50)
        assertFalse(gate.accept(49))
        assertTrue(gate.accept(50))
    }
}
