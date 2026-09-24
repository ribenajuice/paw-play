package com.pawplay.app.games.pawpour

import kotlin.random.Random
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * QA-added checks that go wider than PawPourLogicTest: many more seeds, an independent brute-force
 * solver to cross-check the real one, a tap-by-tap simulation of the screen's state machine (mashing
 * included), and odd screen sizes for the layout. All pure JVM and fast (a couple of seconds).
 */
class PawPourQaTest {

    // ---- Generation: story 14, over far more seeds than the dev's 150 ----

    @Test
    fun `1000 seeds per ramp round 1 to 8 are all valid starts and never need the fallback deal`() {
        for (round in 1..8) {
            val spec = specForRound(round)
            val fallback = fallbackBoard(spec, coloursForRound(round))
            for (seed in 0 until 1000) {
                val label = "round $round seed $seed"
                val board = generateBoard(round, Random(seed))
                assertEquals(label, spec.tubeCount, board.tubes.size)
                assertEquals(label, spec.emptyCount, board.tubes.count { it.isEmpty() })
                assertEquals(label, spec.capacity, board.capacity)
                val counts = board.tubes.flatten().groupingBy { it }.eachCount()
                assertEquals(label, spec.colourCount, counts.size)
                assertTrue(label, counts.values.all { it == spec.capacity })
                assertFalse("$label starts solved", board.isSolved)
                assertTrue("$label starts with a finished tube", board.tubes.indices.none { board.isTubeComplete(it) })
                assertTrue("$label not mixed", board.isWellMixed())
                assertEquals("$label", Solvability.SOLVABLE, board.solvability())
                assertNotEquals("$label fell back to the fixed deal", fallback, board)
            }
        }
    }

    @Test
    fun `the solver's node budget is never hit by positions a child can reach, so no dead round is ever waved through`() {
        var checks = 0
        for (round in 1..8) for (seed in 0 until 150) {
            val random = Random(seed * 7 + round)
            var state = newRoundState(round, random)
            var steps = 0
            while (steps++ < 120 && !state.isWon) {
                val moves = state.board.legalPours()
                if (moves.isEmpty()) break
                val (from, to) = moves[random.nextInt(moves.size)]
                state = state.poured(from, to)
                assertNotEquals("round $round seed $seed step $steps: budget hit", Solvability.UNKNOWN, state.board.solvability())
                checks++
                state = state.checkedFinishable().let { if (it.needsRewind) it.rewound() else it }
            }
        }
        assertTrue(checks > 10_000)
    }

    // ---- Solver vs an independent brute-force search ----

    /** Own move generator (written from the PRD, not from Board), searching for "every colour gathered". */
    private fun bruteForceSolvable(start: List<List<BandColor>>, capacity: Int): Boolean {
        fun goal(t: List<List<BandColor>>) = t.all { it.isEmpty() || (it.size == capacity && it.all { c -> c == it[0] }) }
        fun canon(t: List<List<BandColor>>) = t.sortedBy { tube -> tube.joinToString(",") { it.ordinal.toString() } }
        val seen = HashSet<List<List<BandColor>>>()
        val stack = ArrayDeque<List<List<BandColor>>>()
        canon(start).also { seen += it; stack.addLast(it) }
        while (stack.isNotEmpty()) {
            val cur = stack.removeLast()
            if (goal(cur)) return true
            for (a in cur.indices) for (b in cur.indices) {
                if (a == b || cur[a].isEmpty() || cur[b].size >= capacity) continue
                val src = cur[a]
                val dst = cur[b]
                if (dst.isEmpty()) { if (src.all { it == src[0] }) continue } else if (dst.last() != src.last()) continue
                val run = src.takeLastWhile { it == src.last() }.size
                val n = minOf(run, capacity - dst.size)
                val next = cur.toMutableList().also { it[a] = src.dropLast(n); it[b] = dst + src.takeLast(n) }
                if (seen.add(canon(next))) stack.addLast(canon(next))
            }
        }
        return false
    }

    @Test
    fun `the solver agrees with an independent brute-force search on positions reached by random play`() {
        var deadWithPoursLeft = 0
        var checked = 0
        for (round in 1..3) for (seed in 0 until 300) {
            val random = Random(seed * 13 + round)
            var state = newRoundState(round, random)
            var steps = 0
            while (steps++ < 30 && !state.isWon) {
                val moves = state.board.legalPours()
                if (moves.isEmpty()) break
                val (from, to) = moves[random.nextInt(moves.size)]
                state = state.poured(from, to)
                val expected = bruteForceSolvable(state.board.tubes, state.board.capacity)
                val actual = state.board.solvability()
                assertEquals("round $round seed $seed step $steps ${state.board.tubes}", expected, actual == Solvability.SOLVABLE)
                assertNotEquals(Solvability.UNKNOWN, actual)
                checked++
                // Keep playing on through dead positions (no rewind) so the solver is also checked on unwinnable boards.
                if (!expected && state.board.legalPours().isNotEmpty()) deadWithPoursLeft++
            }
        }
        assertTrue(checked > 3000)
        assertTrue("should have met dead positions that still had pours", deadWithPoursLeft > 20)
    }

    // ---- The screen's state machine, driven tap by tap (logic level; the composable only forwards these) ----

    /** Mirrors PawPourScreen: taps go through Board.tap; a pour is recorded, checked, and un-poured if dead. */
    private class Screen(var state: RoundState) {
        var selected: Int? = null
        var rewinds = 0

        fun tap(index: Int): TapOutcome {
            val outcome = state.board.tap(selected, index)
            when (outcome) {
                TapOutcome.Ignore -> Unit
                is TapOutcome.Select -> selected = outcome.tube
                TapOutcome.Deselect -> selected = null
                is TapOutcome.Reject -> selected = null
                is TapOutcome.Pour -> {
                    selected = null
                    state = state.poured(outcome.from, outcome.to)
                    if (!state.isWon) {
                        state = state.checkedFinishable()
                        if (state.needsRewind) { rewinds++; state = state.rewound() }
                    }
                }
            }
            return outcome
        }
    }

    @Test
    fun `however a child mashes the tubes, the round is always finishable or won, selection is always valid, and nothing throws`() {
        var rewinds = 0
        var pours = 0
        var rejects = 0
        for (round in listOf(1, 2, 3, 4, 5, 6, 7)) for (seed in 0 until 60) {
            val random = Random(seed * 101 + round)
            val start = newRoundState(round, random)
            val screen = Screen(start)
            val tubeCount = start.board.tubes.size
            var taps = 0
            while (taps++ < 400 && !screen.state.isWon) {
                // Half wild mashing (any tube, even out-of-range indexes), half purposeful taps.
                val index = when {
                    random.nextInt(20) == 0 -> random.nextInt(-3, tubeCount + 3)
                    random.nextBoolean() -> random.nextInt(tubeCount)
                    screen.selected == null -> screen.state.board.tubes.indices.filter { screen.state.board.canSelect(it) }.randomOrNull(random) ?: 0
                    else -> screen.state.board.tubes.indices.filter { screen.state.board.isLegalPour(screen.selected!!, it) }.randomOrNull(random) ?: random.nextInt(tubeCount)
                }
                val outcome = screen.tap(index)
                if (outcome is TapOutcome.Pour) pours++
                if (outcome is TapOutcome.Reject) rejects++
                val label = "round $round seed $seed tap $taps"
                val s = screen.state
                assertTrue("$label: dead round left on screen", s.isWon || s.board.solvability() != Solvability.UNSOLVABLE)
                assertFalse("$label: needs a rewind after settling", s.needsRewind)
                assertTrue("$label: safeDepth beyond history", s.safeDepth <= s.history.size)
                screen.selected?.let { assertTrue("$label: invalid selection", s.board.canSelect(it)) }
                assertEquals("$label: bands lost or made", tubeCount * 0 + specForRound(round).colourCount * specForRound(round).capacity, s.board.tubes.sumOf { it.size })
                assertTrue("$label: overfull tube", s.board.tubes.all { it.size <= s.board.capacity })
            }
            rewinds += screen.rewinds
        }
        assertTrue("mashing should exercise pours, wobbles and auto-undo", pours > 500 && rejects > 500 && rewinds > 20)
    }

    @Test
    fun `stories 10 and 12 - with a tube picked up, a tap on another liquid tube never switches the selection`() {
        // Implemented behaviour: a second tap is Deselect, Pour or Reject, never Select. So the
        // outcome is deterministic and the child can never get stuck holding a tube.
        for (round in 1..6) for (seed in 0 until 50) {
            val board = generateBoard(round, Random(seed))
            for (selected in board.tubes.indices.filter { board.canSelect(it) }) for (tapped in board.tubes.indices) {
                val outcome = board.tap(selected, tapped)
                assertEquals(outcome, board.tap(selected, tapped)) // deterministic
                assertFalse("round $round: selection switched", outcome is TapOutcome.Select)
                if (tapped == selected) assertEquals(TapOutcome.Deselect, outcome)
                else if (board.isLegalPour(selected, tapped)) assertTrue(outcome is TapOutcome.Pour)
                else assertEquals(TapOutcome.Reject(tapped), outcome)
            }
        }
    }

    @Test
    fun `tapping an empty tube first does nothing, and a finished tube can't be picked up`() {
        val b = Board(listOf(listOf(BandColor.SKY, BandColor.SKY, BandColor.SKY), emptyList(), listOf(BandColor.CORAL, BandColor.SKY)), 3)
        assertEquals(TapOutcome.Ignore, b.tap(null, 1))
        assertEquals(TapOutcome.Ignore, b.tap(null, 0))
        assertEquals(TapOutcome.Ignore, b.tap(null, -1))
        assertEquals(TapOutcome.Ignore, b.tap(null, 3))
    }

    // ---- Auto-undo edges ----

    @Test
    fun `rewinding is idempotent, never goes before the start and does nothing when nothing needs it`() {
        val fresh = newRoundState(3, Random(9))
        assertSame(fresh, fresh.undoLast())          // nothing to undo at the very start
        assertSame(fresh, fresh.rewound())           // nothing needs rewinding
        assertTrue(fresh.rewindSteps().isEmpty())

        for (seed in 0 until 200) {
            var state = newRoundState(4, Random(seed))
            val startBoard = state.board
            val random = Random(seed)
            var steps = 0
            while (steps++ < 60 && !state.isWon) {
                val moves = state.board.legalPours()
                if (moves.isEmpty()) break
                val (from, to) = moves[random.nextInt(moves.size)]
                val checked = state.poured(from, to).checkedFinishable()
                if (checked.needsRewind) {
                    val back = checked.rewound()
                    assertTrue(back.history.size >= 0 && back.history.size < checked.history.size)
                    assertEquals(back.safeDepth, back.history.size)
                    assertFalse(back.needsRewind)
                    assertSame("a second check must not rewind again", back, back.rewound())
                    assertEquals(back, back.checkedFinishable())
                    // Replaying the surviving history from the round start reproduces the board.
                    var replay = startBoard
                    back.history.forEach { replay = replay.poured(it.move.from, it.move.to) }
                    assertEquals(back.board, replay)
                    state = back
                } else state = checked
            }
        }
    }

    // ---- Layout on odd screens (story 18 / PRD sizing: >=48dp, no scroll) ----

    @Test
    fun `every ramp round fits without scrolling and keeps 48dp tubes across a wide range of screen sizes`() {
        for (screenWidth in listOf(280f, 320f, 360f, 392f, 411f, 480f, 600f, 800f)) {
            for (boardHeight in listOf(360f, 400f, 480f, 520f, 580f, 640f, 700f, 900f)) {
                for (round in 1..8) {
                    val spec = specForRound(round)
                    val availableWidth = screenWidth - 32f
                    val layout = layoutBoard(spec.tubeCount, spec.capacity, availableWidth, boardHeight)
                    val label = "round $round in ${availableWidth}x$boardHeight"
                    assertTrue("$label: tube ${layout.tubeWidth}dp wide", layout.tubeWidth >= MIN_TUBE_WIDTH - 0.01f)
                    assertTrue("$label: content ${layout.contentHeight}dp tall", layout.contentHeight <= boardHeight + 0.01f)
                    assertTrue(label, layout.positions.all { it.x >= -0.01f && it.x + layout.tubeWidth <= availableWidth + 0.01f })
                    assertNotNull(layout)
                }
            }
        }
    }
}
