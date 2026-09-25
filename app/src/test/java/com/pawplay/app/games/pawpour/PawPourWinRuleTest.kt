package com.pawplay.app.games.pawpour

import com.pawplay.app.games.pawpour.BandColor.CORAL
import com.pawplay.app.games.pawpour.BandColor.SKY
import com.pawplay.app.games.pawpour.BandColor.SUNSHINE
import kotlin.random.Random
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Pins the founder's win rule (2026-09-25): a round is won only when EVERY COLOUR IS GATHERED INTO
 * ONE TUBE. Every colour has exactly `capacity` bands, so that is the same as "every non-empty
 * tube is full and a single colour". A colour split across two half-full single-colour tubes is
 * NOT a win. (The earlier reading, "every tube empty or one colour", let such a split count.)
 */
class PawPourWinRuleTest {

    private fun board(capacity: Int, vararg tubes: List<BandColor>) = Board(tubes.toList(), capacity)
    private val none = emptyList<BandColor>()

    @Test
    fun `a colour split across two half-full single-colour tubes is not a win`() {
        val split = board(3, listOf(SKY, SKY), listOf(SKY), listOf(CORAL, CORAL, CORAL), none)
        assertFalse(split.isSolved)
        assertFalse(RoundState(1, split).isWon)
    }

    @Test
    fun `a partly-filled single-colour tube is not a win even when it is the only one of its colour`() {
        assertFalse(board(3, listOf(SKY, SKY), listOf(CORAL, CORAL, CORAL), none).isSolved)
    }

    @Test
    fun `won means every non-empty tube is full and one colour`() {
        assertTrue(board(3, listOf(SKY, SKY, SKY), none, listOf(CORAL, CORAL, CORAL), none).isSolved)
        assertFalse(board(3, listOf(SKY, SKY, SKY), listOf(CORAL, CORAL, SKY), listOf(CORAL), none).isSolved)
    }

    @Test
    fun `the last pour wins only when it gathers the final colour into one tube`() {
        var state = RoundState(1, board(2, listOf(SKY, CORAL), listOf(CORAL, SKY), none))
        state = state.poured(0, 2) // [sky] [coral, sky] [coral]
        state = state.poured(1, 0) // [sky, sky] [coral] [coral]: every tube is one colour, but coral is split
        assertFalse("coral is still split across two tubes", state.isWon)
        assertTrue("merging the split finishes the round", state.board.isLegalPour(1, 2))
        state = state.poured(1, 2)
        assertTrue(state.isWon)
    }

    @Test
    fun `a split position is still finishable and is never treated as stuck`() {
        val split = board(3, listOf(SKY, SKY), listOf(SKY), listOf(CORAL, CORAL, CORAL), none)
        assertEquals(Solvability.SOLVABLE, split.solvability())
        assertTrue(split.legalPours().isNotEmpty())
        val state = RoundState(1, split).checkedFinishable()
        assertFalse(state.needsRewind)
        assertTrue(state.poured(1, 0).isWon) // sky gathered into the one tube
    }

    @Test
    fun `any position where every tube is one colour but the round isn't won still has a pour that finishes it`() {
        // A colour split over several one-colour tubes always has room to merge, so the new rule
        // can never create a stuck position from a "looks sorted" board.
        val split = board(4, listOf(SKY), listOf(SKY), listOf(SKY, SKY), listOf(CORAL, CORAL, CORAL, CORAL), none)
        assertFalse(split.isSolved)
        assertEquals(Solvability.SOLVABLE, split.solvability())
        assertTrue(split.legalPours().isNotEmpty())
    }

    @Test
    fun `whenever a round is won each colour sits in exactly one full tube and nothing is left to pour`() {
        var wins = 0
        for (round in 1..6) for (seed in 0 until 100) {
            val random = Random(seed * 17 + round)
            var state = newRoundState(round, random)
            var steps = 0
            while (steps++ < 400 && !state.isWon) {
                val moves = state.board.legalPours()
                if (moves.isEmpty()) break
                // Mostly merging pours, the way a child tidies up, so wins actually happen.
                val merges = moves.filter { (_, to) -> state.board.tubes[to].isNotEmpty() }
                val (from, to) = if (merges.isNotEmpty() && random.nextInt(10) < 8) merges[random.nextInt(merges.size)] else moves[random.nextInt(moves.size)]
                state = state.poured(from, to).checkedFinishable()
                if (state.needsRewind) state = state.rewound()
            }
            if (!state.isWon) continue
            wins++
            val label = "round $round seed $seed"
            val b = state.board
            val filled = b.tubes.indices.filter { b.tubes[it].isNotEmpty() }
            assertTrue("$label: every non-empty tube is full and finished (border + sparkle)", filled.all { b.isTubeComplete(it) })
            assertEquals("$label: one full tube per colour", specForRound(round).colourCount, filled.size)
            assertEquals("$label: each colour in exactly one tube", filled.size, filled.map { b.tubes[it].first() }.toSet().size)
            assertTrue("$label: nothing left to pour once won", b.legalPours().isEmpty())
        }
        assertTrue("random tidy-up play should win most rounds", wins > 300)
    }
}
