package com.pawplay.app.games.pawmatch

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PawMatchLogicTest {

    /** Deterministic stand-in for the real shuffle, so tests aren't flaky. */
    private fun orderedPairs(pairCount: Int): List<Critter> {
        val chosen = Critter.entries.take(pairCount)
        return chosen + chosen
    }

    @Test
    fun `level 1 starts with 3 pairs`() {
        val round = newRound(level = 1, shuffledCritters = ::orderedPairs)
        assertEquals(3, round.totalPairs)
        assertEquals(6, round.cards.size)
    }

    @Test
    fun `each level adds one pair up to the cap`() {
        assertEquals(3, pairsForLevel(1))
        assertEquals(4, pairsForLevel(2))
        assertEquals(5, pairsForLevel(3))
        assertEquals(6, pairsForLevel(4))
        assertEquals(6, pairsForLevel(5)) // capped, doesn't keep growing
        assertEquals(6, pairsForLevel(100))
    }

    @Test
    fun `tapping two matching cards marks them matched after resolve`() {
        var round = newRound(level = 1, shuffledCritters = ::orderedPairs)
        val firstFoxId = round.cards.first { it.critter == Critter.FOX }.id
        val secondFoxId = round.cards.first { it.critter == Critter.FOX && it.id != firstFoxId }.id

        round = round.tapCard(firstFoxId).tapCard(secondFoxId)
        assertEquals(2, round.pendingReveal.size)

        round = round.resolvePending()
        assertTrue(round.cards.first { it.id == firstFoxId }.isMatched)
        assertTrue(round.cards.first { it.id == secondFoxId }.isMatched)
        assertTrue(round.pendingReveal.isEmpty())
        assertEquals(1, round.matchedPairs)
    }

    @Test
    fun `tapping two mismatched cards flips both back down, never punished`() {
        var round = newRound(level = 1, shuffledCritters = ::orderedPairs)
        val fox = round.cards.first { it.critter == Critter.FOX }
        val bear = round.cards.first { it.critter == Critter.BEAR }

        round = round.tapCard(fox.id).tapCard(bear.id).resolvePending()

        assertFalse(round.cards.first { it.id == fox.id }.isMatched)
        assertFalse(round.cards.first { it.id == fox.id }.isRevealed)
        assertFalse(round.cards.first { it.id == bear.id }.isMatched)
        assertEquals(0, round.matchedPairs)
    }

    @Test
    fun `tapping a third card while two are pending does nothing`() {
        var round = newRound(level = 1, shuffledCritters = ::orderedPairs)
        val (a, b, c) = round.cards.take(3)
        round = round.tapCard(a.id).tapCard(b.id).tapCard(c.id)

        assertEquals(2, round.pendingReveal.size)
        assertFalse(round.cards.first { it.id == c.id }.isRevealed)
    }

    @Test
    fun `tapping an already matched card does nothing`() {
        var round = newRound(level = 1, shuffledCritters = ::orderedPairs)
        val fox = round.cards.first { it.critter == Critter.FOX }
        val otherFox = round.cards.first { it.critter == Critter.FOX && it.id != fox.id }
        round = round.tapCard(fox.id).tapCard(otherFox.id).resolvePending()

        val before = round
        round = round.tapCard(fox.id)
        assertEquals(before, round)
    }

    @Test
    fun `round is complete only once every card is matched`() {
        var round = newRound(level = 1, shuffledCritters = ::orderedPairs)
        assertFalse(round.isComplete)

        round.cards.groupBy { it.critter }.values.forEach { pair ->
            val (a, b) = pair
            round = round.tapCard(a.id).tapCard(b.id).resolvePending()
        }
        assertTrue(round.isComplete)
    }

    @Test
    fun `nextLevel starts a fresh round one level up with more pairs`() {
        val level1 = newRound(level = 1, shuffledCritters = ::orderedPairs)
        val level2 = level1.nextLevel(shuffledCritters = ::orderedPairs)

        assertEquals(2, level2.level)
        assertEquals(4, level2.totalPairs)
        assertTrue(level2.cards.none { it.isMatched })
    }
}
