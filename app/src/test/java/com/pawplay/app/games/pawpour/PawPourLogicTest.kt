package com.pawplay.app.games.pawpour

import com.pawplay.app.games.pawpour.BandColor.CORAL
import com.pawplay.app.games.pawpour.BandColor.SKY
import com.pawplay.app.games.pawpour.BandColor.SUNSHINE
import kotlin.random.Random
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

class PawPourLogicTest {

    private fun board(capacity: Int, vararg tubes: List<BandColor>) = Board(tubes.toList(), capacity)
    private val none = emptyList<BandColor>()

    // ---- Pours ----

    @Test
    fun `pouring onto an empty tube or the same colour with room is legal`() {
        val b = board(3, listOf(SKY, CORAL), none, listOf(CORAL), listOf(SUNSHINE, SUNSHINE))
        assertTrue(b.isLegalPour(0, 1))      // into empty
        assertTrue(b.isLegalPour(0, 2))      // same top colour, has room
        assertFalse(b.isLegalPour(0, 3))     // different colour
    }

    @Test
    fun `pouring into a full tube, out of an empty one, or onto itself is not legal`() {
        val b = board(2, listOf(SKY, CORAL), listOf(SUNSHINE, CORAL), none)
        assertFalse(b.isLegalPour(0, 1))     // same top colour but the target is full
        assertFalse(b.isLegalPour(2, 0))     // empty source
        assertFalse(b.isLegalPour(0, 0))     // itself
        assertFalse(b.isLegalPour(0, 9))     // no such tube
    }

    @Test
    fun `emptying a one-colour tube into an empty tube changes nothing useful so is not legal`() {
        val b = board(3, listOf(SKY, SKY), none, listOf(SKY, CORAL))
        assertFalse(b.isLegalPour(0, 1))
        assertTrue(b.isLegalPour(2, 1))      // a mixed tube into an empty one is fine
        assertTrue(b.isLegalPour(0, 2).not()) // top of tube 2 is coral, not sky
        assertSame(b, b.poured(0, 1))        // an illegal pour returns the board untouched
    }

    @Test
    fun `a one-colour tube can still merge onto a same-colour tube`() {
        val b = board(4, listOf(SKY), listOf(CORAL, SKY))
        assertTrue(b.isLegalPour(0, 1))
        assertEquals(listOf(listOf<BandColor>(), listOf(CORAL, SKY, SKY)), b.poured(0, 1).tubes)
    }

    @Test
    fun `a pour moves the whole matching top run`() {
        val b = board(4, listOf(CORAL, SKY, SKY, SKY), listOf(SKY))
        assertEquals(3, b.topRunLength(0))
        assertEquals(3, b.pourCount(0, 1))
        val after = b.poured(0, 1)
        assertEquals(listOf(CORAL), after.tubes[0])
        assertEquals(listOf(SKY, SKY, SKY, SKY), after.tubes[1])
    }

    @Test
    fun `a pour moves only as much of the run as the room allows`() {
        val b = board(4, listOf(CORAL, SKY, SKY, SKY), listOf(SUNSHINE, CORAL, SKY))
        assertEquals(1, b.pourCount(0, 1)) // target has room for just one
        val after = b.poured(0, 1)
        assertEquals(listOf(CORAL, SKY, SKY), after.tubes[0])
        assertEquals(listOf(SUNSHINE, CORAL, SKY, SKY), after.tubes[1])
        assertEquals(b.tubes.sumOf { it.size }, after.tubes.sumOf { it.size }) // nothing lost
    }

    @Test
    fun `pouring a mixed tube into an empty one moves only its top run`() {
        val b = board(3, listOf(SKY, CORAL, CORAL), none)
        val after = b.poured(0, 1)
        assertEquals(listOf(SKY), after.tubes[0])
        assertEquals(listOf(CORAL, CORAL), after.tubes[1])
    }

    // ---- Winning ----

    @Test
    fun `solved means every tube is empty or one colour`() {
        assertTrue(board(3, listOf(SKY, SKY, SKY), none, listOf(CORAL, CORAL, CORAL)).isSolved)
        assertTrue(board(3, listOf(SKY, SKY), listOf(SKY), none).isSolved) // one colour per tube, PRD story 15
        assertFalse(board(3, listOf(SKY, SKY, CORAL), none, listOf(CORAL, CORAL, SKY)).isSolved)
        assertFalse(board(3, listOf(SKY, CORAL), none).isSolved)
    }

    @Test
    fun `only a full one-colour tube counts as complete`() {
        val b = board(3, listOf(SKY, SKY, SKY), listOf(CORAL, CORAL), listOf(CORAL, SKY, CORAL), none)
        assertTrue(b.isTubeComplete(0))
        assertFalse(b.isTubeComplete(1))
        assertFalse(b.isTubeComplete(2))
        assertFalse(b.isTubeComplete(3))
    }

    @Test
    fun `the last pour of a round wins it`() {
        var state = RoundState(1, board(2, listOf(SKY, CORAL), listOf(CORAL, SKY), none))
        state = state.poured(0, 2) // coral to the empty tube: [sky] [coral, sky] [coral]
        assertFalse(state.isWon)
        state = state.poured(1, 0)  // sky onto sky: [sky, sky] [coral] [coral]
        assertTrue(state.isWon)
    }

    // ---- Taps ----

    @Test
    fun `tap rules select, deselect, pour, reject and ignore`() {
        val b = board(3, listOf(SKY, CORAL), listOf(CORAL), none, listOf(SKY, SKY, SKY), listOf(SUNSHINE, SKY))
        assertEquals(TapOutcome.Ignore, b.tap(null, 2))            // empty tube first: nothing
        assertEquals(TapOutcome.Ignore, b.tap(null, 3))            // finished tube can't be picked up
        assertEquals(TapOutcome.Select(0), b.tap(null, 0))
        assertEquals(TapOutcome.Deselect, b.tap(0, 0))
        assertEquals(TapOutcome.Pour(0, 1, 1), b.tap(0, 1))
        assertEquals(TapOutcome.Pour(0, 2, 1), b.tap(0, 2))
        assertEquals(TapOutcome.Reject(4), b.tap(0, 4))            // different colour: gentle no
        assertEquals(TapOutcome.Reject(3), b.tap(0, 3))            // full tube: gentle no
        assertEquals(TapOutcome.Ignore, b.tap(null, 42))
    }

    // ---- Ramp and generation ----

    @Test
    fun `the ramp follows the PRD table and caps at round 6`() {
        val expected = listOf(
            RoundSpec(6, 3, 3), RoundSpec(6, 4, 3), RoundSpec(7, 5, 3),
            RoundSpec(8, 6, 3), RoundSpec(8, 6, 4), RoundSpec(9, 7, 4),
        )
        expected.forEachIndexed { index, spec -> assertEquals("round ${index + 1}", spec, specForRound(index + 1)) }
        assertEquals(3, specForRound(1).emptyCount)
        (2..6).forEach { assertEquals(2, specForRound(it).emptyCount) }
        assertEquals(specForRound(6), specForRound(7))
        assertEquals(specForRound(6), specForRound(500))
        assertEquals(specForRound(1), specForRound(0))
    }

    @Test
    fun `round 1 uses sunshine, sky and coral`() {
        assertEquals(setOf(SUNSHINE, SKY, CORAL), coloursForRound(1).toSet())
        assertEquals(BandColor.entries.take(7), coloursForRound(6))
    }

    @Test
    fun `every ramp round generates a solvable, unsolved, well-mixed board with no finished tube`() {
        for (round in 1..8) {
            val spec = specForRound(round)
            for (seed in 0 until 150) {
                val label = "round $round seed $seed"
                val board = generateBoard(round, Random(seed))

                assertEquals(label, spec.tubeCount, board.tubes.size)
                assertEquals(label, spec.capacity, board.capacity)
                assertEquals(label, spec.emptyCount, board.tubes.count { it.isEmpty() })
                val counts = board.tubes.flatten().groupingBy { it }.eachCount()
                assertEquals(label, coloursForRound(round).toSet(), counts.keys)
                assertTrue(label, counts.values.all { it == spec.capacity }) // exactly capacity bands per colour
                assertTrue(label, board.tubes.all { it.size <= spec.capacity })

                assertFalse("$label starts solved", board.isSolved)
                assertTrue("$label has a finished tube", board.tubes.indices.none { board.isTubeComplete(it) })
                assertTrue("$label not mixed", board.isWellMixed())
                assertEquals("$label unsolvable", Solvability.SOLVABLE, board.solvability())
            }
        }
    }

    @Test
    fun `boards differ from seed to seed`() {
        val boards = (0 until 20).map { generateBoard(3, Random(it)) }.toSet()
        assertTrue(boards.size > 15)
    }

    @Test
    fun `the deterministic fallback is itself a valid start for every round`() {
        for (round in 1..8) {
            val board = fallbackBoard(specForRound(round), coloursForRound(round))
            assertFalse(board.isSolved)
            assertTrue(board.tubes.indices.none { board.isTubeComplete(it) })
            assertTrue(board.isWellMixed())
            assertEquals("round $round", Solvability.SOLVABLE, board.solvability())
        }
    }

    // ---- Solver, stuck detection ----

    @Test
    fun `a board with no legal pour left and not solved is stuck`() {
        val b = board(2, listOf(SKY, CORAL), listOf(CORAL, SKY))
        assertTrue(b.legalPours().isEmpty())
        assertEquals(Solvability.UNSOLVABLE, b.solvability())
        assertFalse(b.canBeFinished())
    }

    @Test
    fun `a board that still has pours but can no longer be won counts as stuck`() {
        val dead = board(
            3,
            listOf(SUNSHINE, SKY), listOf(CORAL, SKY, SKY), listOf(SUNSHINE), listOf(CORAL, SUNSHINE, CORAL),
        )
        assertTrue(dead.legalPours().isNotEmpty())
        assertEquals(Solvability.UNSOLVABLE, dead.solvability())
        assertFalse(dead.canBeFinished())
    }

    @Test
    fun `a solved board and a winnable board are not stuck`() {
        assertEquals(Solvability.SOLVABLE, board(2, listOf(SKY, SKY), none).solvability())
        val winnable = board(3, listOf(SUNSHINE, SKY, SUNSHINE), listOf(CORAL, SKY, SKY), none, listOf(CORAL, SUNSHINE, CORAL))
        assertEquals(Solvability.SOLVABLE, winnable.solvability())
    }

    @Test
    fun `a search that runs out of budget says unknown and does not count as stuck`() {
        val b = generateBoard(6, Random(1))
        assertEquals(Solvability.UNKNOWN, b.solvability(nodeBudget = 1))
        assertTrue(b.canBeFinished(nodeBudget = 1))
    }

    // ---- Auto-undo ----

    private val winnableStart = board(
        3,
        listOf(SUNSHINE, SKY, SUNSHINE), listOf(CORAL, SKY, SKY), none, listOf(CORAL, SUNSHINE, CORAL),
    )

    @Test
    fun `a pour that leaves the round unwinnable rewinds to the position before it`() {
        val start = RoundState(1, winnableStart)
        val afterDeadPour = start.poured(0, 2).checkedFinishable()
        assertTrue(afterDeadPour.needsRewind)
        assertEquals(1, afterDeadPour.rewindSteps().size)

        val rewound = afterDeadPour.rewound()
        assertEquals(winnableStart, rewound.board)
        assertTrue(rewound.history.isEmpty())
        assertFalse(rewound.needsRewind)
    }

    @Test
    fun `a good pour is remembered as safe and is not rewound`() {
        val start = RoundState(1, winnableStart)
        val safe = start.board.legalPours().map { (f, t) -> start.poured(f, t).checkedFinishable() }.filterNot { it.needsRewind }
        assertTrue(safe.isNotEmpty())
        safe.forEach { assertEquals(1, it.safeDepth) }
    }

    /** A round-3 position after a safe pour, and the state after a second pour that can't be finished. */
    private fun safeThenFatal(): Pair<RoundState, RoundState> {
        for (seed in 0 until 200) {
            val start = newRoundState(3, Random(seed))
            for ((f1, t1) in start.board.legalPours()) {
                val first = start.poured(f1, t1).checkedFinishable()
                if (first.needsRewind) continue
                for ((f2, t2) in first.board.legalPours()) {
                    val second = first.poured(f2, t2).checkedFinishable()
                    if (second.needsRewind) return first to second
                }
            }
        }
        error("no safe-then-fatal sequence found in 200 generated boards")
    }

    @Test
    fun `undo lands on the latest finishable position, not all the way back to the start`() {
        val (first, second) = safeThenFatal()
        assertEquals(1, second.safeDepth)
        assertEquals(1, second.rewindSteps().size)
        assertEquals(first.board, second.rewound().board)
        assertEquals(1, second.rewound().history.size)
        assertTrue(second.rewound().board.canBeFinished())
    }

    @Test
    fun `whenever random play gets stuck the rewind lands on a finishable position that is the latest one`() {
        var rewinds = 0
        for (round in 1..6) for (seed in 0 until 60) {
            val random = Random(seed * 31 + round)
            var state = newRoundState(round, random)
            var steps = 0
            while (steps++ < 150 && !state.isWon) {
                val moves = state.board.legalPours()
                if (moves.isEmpty()) break
                val (from, to) = moves[random.nextInt(moves.size)]
                state = state.poured(from, to).checkedFinishable()
                if (state.needsRewind) {
                    rewinds++
                    val target = state.rewound()
                    assertTrue(target.board.canBeFinished())
                    assertFalse(target.board.isSolved)
                    // Every position after the landing spot, up to the stuck one, really was a dead end.
                    state.history.drop(target.history.size + 1).forEach { assertFalse(it.before.canBeFinished()) }
                    assertFalse(state.board.canBeFinished())
                    state = target
                }
            }
        }
        assertTrue("random play should have hit at least a few dead ends", rewinds > 5)
    }

    @Test
    fun `undoing step by step ends in the same state as rewinding at once`() {
        val (_, second) = safeThenFatal()
        val stepwise = second.rewindSteps().fold(second) { state, _ -> state.undoLast() }
        assertEquals(second.rewound(), stepwise)
    }

    @Test
    fun `a pour that isn't legal changes nothing and adds no history`() {
        val start = RoundState(1, winnableStart)
        assertSame(start, start.poured(0, 1)) // sunshine onto coral
        assertSame(start, start.poured(2, 0)) // from empty
    }

    @Test
    fun `next round follows the ramp and starts fresh`() {
        val next = newRoundState(1, Random(3)).nextRound(Random(4))
        assertEquals(2, next.round)
        assertEquals(4, next.board.tubes.flatten().distinct().size)
        assertTrue(next.history.isEmpty())
        assertFalse(next.isWon)
    }
}
