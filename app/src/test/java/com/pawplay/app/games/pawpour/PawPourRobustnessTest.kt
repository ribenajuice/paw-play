package com.pawplay.app.games.pawpour

import kotlin.math.pow
import kotlin.random.Random
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** The fallback deal, the bounded auto-undo history, and the band colour brightness ladder. */
class PawPourRobustnessTest {

    // ---- Fallback deal (used only if random dealing somehow fails 200 times) ----

    @Test
    fun `the fallback deal is a valid, solvable start for every one of the six ramp specs`() {
        val rampSpecs = (1..6).map { specForRound(it) }
        assertEquals(6, rampSpecs.toSet().size)
        for (round in 1..6) {
            val spec = specForRound(round)
            val board = fallbackBoard(spec, coloursForRound(round))
            val label = "round $round"
            assertEquals(label, spec.tubeCount, board.tubes.size)
            assertEquals(label, spec.emptyCount, board.tubes.count { it.isEmpty() })
            assertTrue(label, board.tubes.flatten().groupingBy { it }.eachCount().values.all { it == spec.capacity })
            assertFalse("$label starts won", board.isSolved)
            assertTrue("$label has a finished tube", board.tubes.indices.none { board.isTubeComplete(it) })
            assertEquals("$label unsolvable", Solvability.SOLVABLE, board.solvability())
        }
    }

    // ---- History cap ----

    @Test
    fun `a very long round never remembers more than MAX_HISTORY pours and auto-undo still works`() {
        for (round in listOf(2, 4, 6)) {
            val random = Random(round)
            var state = newRoundState(round, random)
            var pours = 0
            var maxSeen = 0
            var rewinds = 0
            while (pours < 1500) {
                val moves = state.board.legalPours()
                if (moves.isEmpty() || state.isWon) { state = newRoundState(round, random); continue }
                val (from, to) = moves[random.nextInt(moves.size)]
                state = state.poured(from, to).checkedFinishable()
                pours++
                if (state.needsRewind) { rewinds++; state = state.rewound(); assertTrue(state.board.canBeFinished()) }
                maxSeen = maxOf(maxSeen, state.history.size)
                assertTrue(state.safeDepth <= state.history.size)
            }
            assertTrue("round $round: history grew to $maxSeen", maxSeen <= MAX_HISTORY)
        }
    }

    @Test
    fun `trimming drops the oldest safe steps but never a step auto-undo could still need`() {
        val board = Board(listOf(listOf(BandColor.SKY, BandColor.CORAL), listOf(BandColor.CORAL), emptyList()), 3)
        val filler = Step(board, Move(0, 1, 1))

        // 150 steps remembered, the latest 30 not yet known safe: the 51 oldest go, the 30 unsafe stay.
        val long = RoundState(1, board, history = List(150) { filler }, safeDepth = 120).poured(0, 1)
        assertEquals(MAX_HISTORY, long.history.size)
        assertEquals(69, long.safeDepth)
        assertEquals(31, long.history.size - long.safeDepth) // the 30 unsafe steps plus the new pour
        assertEquals(31, long.rewindSteps().size)

        // Old unsafe steps are never trimmed, even if that leaves more than MAX_HISTORY.
        val unsafe = RoundState(1, board, history = List(150) { filler }, safeDepth = 10).poured(0, 1)
        assertEquals(10, 150 + 1 - unsafe.history.size)
        assertEquals(0, unsafe.safeDepth)
        assertEquals(141, unsafe.rewindSteps().size)

        // Short histories are untouched.
        val short = RoundState(1, board).poured(0, 1)
        assertEquals(1, short.history.size)
    }

    // ---- Brightness ladder (docs/PRD.md story 13: distinguishable in greyscale) ----

    private fun channel(value: Int): Double {
        val c = value / 255.0
        return if (c <= 0.03928) c / 12.92 else ((c + 0.055) / 1.055).pow(2.4)
    }

    private fun luminance(rgb: Int): Double =
        0.2126 * channel(rgb shr 16 and 0xFF) + 0.7152 * channel(rgb shr 8 and 0xFF) + 0.0722 * channel(rgb and 0xFF)

    private fun ratio(a: Int, b: Int): Double {
        val (hi, lo) = luminance(a).let { la -> luminance(b).let { lb -> maxOf(la, lb) to minOf(la, lb) } }
        return (hi + 0.05) / (lo + 0.05)
    }

    private val ink = 0x2B2320

    @Test
    fun `the seven band colours form a brightness ladder with every step at least 1 point 3 apart`() {
        val ladder = BandColor.entries
        assertEquals(7, ladder.size)
        for (i in 0 until ladder.size - 1) {
            val lighter = luminance(ladder[i].rgb())
            val darker = luminance(ladder[i + 1].rgb())
            assertTrue("${ladder[i]} must be lighter than ${ladder[i + 1]}", lighter > darker)
            assertTrue("${ladder[i]} vs ${ladder[i + 1]}: ${ratio(ladder[i].rgb(), ladder[i + 1].rgb())}", ratio(ladder[i].rgb(), ladder[i + 1].rgb()) >= 1.3)
        }
        // Not just neighbours: any two colours are at least 1.3 apart.
        for (a in ladder) for (b in ladder) if (a != b) assertTrue("$a vs $b", ratio(a.rgb(), b.rgb()) >= 1.3)
    }

    @Test
    fun `every mark stays readable against its band`() {
        for (color in BandColor.entries) {
            val mark = if (color.markIsWhite()) 0xFFFFFF else ink
            assertTrue("$color mark contrast ${ratio(color.rgb(), mark)}", ratio(color.rgb(), mark) >= 3.0)
        }
    }
}
