package com.pawplay.app.games.pawblocks

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

/** The fit guarantee and the gentle clear-out (docs/PRD.md stories 44 and 45). */
class PawBlocksTrayTest {
    private fun shape(id: String) = BlockShapes.byId.getValue(id)

    /** A board reached by random legal play at [stage] (lines clear as they do in the game), [moves] blocks placed. */
    private fun randomBoard(size: Int, stage: Int, moves: Int, random: Random): Board {
        var board = Board.empty(size)
        val set = BlocksRamp.shapesFor(stage)
        repeat(moves) {
            val s = set.shuffled(random).firstOrNull { board.hasSpot(it) } ?: return board
            val spots = board.spots(s)
            val spot = spots[random.nextInt(spots.size)]
            board = board.placeAndClear(s, spot.row, spot.col).board
        }
        return board
    }

    /** A board packed until nothing but dots fits, i.e. as full as random play gets: the hard case for the dealer. */
    private fun packedBoard(size: Int, stage: Int, random: Random): Board {
        var board = Board.empty(size)
        val set = BlocksRamp.shapesFor(stage).filter { it.size > 1 }
        var guard = 0
        while (guard++ < 400) {
            val fits = set.filter { board.hasSpot(it) }
            if (fits.isEmpty()) break
            val s = fits[random.nextInt(fits.size)]
            val spots = board.spots(s)
            val spot = spots[random.nextInt(spots.size)]
            val next = board.placeAndClear(s, spot.row, spot.col)
            board = next.board
        }
        return board
    }

    /** Oracle written separately from the dealer: can [trio] all be placed one after another (lines cleared between)? No budget. */
    private fun oraclePlaceAll(board: Board, trio: List<BlockShape>): Boolean {
        if (trio.isEmpty()) return true
        for (i in trio.indices) {
            val rest = trio.filterIndexed { j, _ -> j != i }
            for (spot in board.spots(trio[i])) if (oraclePlaceAll(board.placeAndClear(trio[i], spot.row, spot.col).board, rest)) return true
        }
        return false
    }

    // ------------------------------------------------------------------ the fit guarantee

    @Test
    fun `every deal has at least one block with a legal spot, on every stage and many boards`() {
        var deals = 0
        for (stage in 1..6) {
            val size = BlocksRamp.boardSizeFor(stage)
            for (seed in 0 until 60) {
                val random = Random(seed * 31 + stage)
                val boards = listOf(
                    Board.empty(size),
                    randomBoard(size, stage, 3 + seed % 30, random),
                    packedBoard(size, stage, random),
                )
                for (board in boards) {
                    val trio = TrayDealer.deal(board, stage, null, random)
                    deals++
                    assertEquals(3, trio.size)
                    assertTrue("stage $stage seed $seed: nothing fits\n$board\n$trio", trio.any { board.hasSpot(it) })
                    assertTrue("only the stage's blocks", trio.all { it in BlocksRamp.shapesFor(stage) })
                }
            }
        }
        assertTrue(deals >= 1000)
    }

    @Test
    fun `a nearly full board still gets a block that fits, the dot at worst`() {
        // One hole per row and no two in a line: only a dot fits anywhere.
        val board = Board.from(5) { r, c -> if (r == c) null else Family.BAR3 }
        assertEquals(0, board.completedRows().size + board.completedCols().size)
        for (seed in 0 until 100) {
            val trio = TrayDealer.deal(board, 3, null, Random(seed))
            assertTrue("seed $seed: $trio", trio.any { it.id == "dot" })
        }
    }

    @Test
    fun `the trio is never the same as the previous one, in any order`() {
        for (stage in listOf(1, 2, 3, 6)) {
            val board = Board.empty(BlocksRamp.boardSizeFor(stage))
            val random = Random(stage)
            var previous: List<String>? = null
            repeat(300) {
                val trio = TrayDealer.deal(board, stage, previous, random).map { it.id }
                if (previous != null) assertTrue("stage $stage repeated $trio", trio.sorted() != previous!!.sorted())
                previous = trio
            }
        }
    }

    @Test
    fun `deals are random within the set and use every block on offer`() {
        for (stage in 1..6) {
            val board = Board.empty(BlocksRamp.boardSizeFor(stage))
            val random = Random(99)
            val seen = HashSet<String>()
            repeat(400) { TrayDealer.deal(board, stage, null, random).forEach { seen += it.id } }
            assertEquals(BlocksRamp.shapesFor(stage).map { it.id }.toSet(), seen)
        }
    }

    @Test
    fun `all three can be placed one after another whenever the board has plenty of room`() {
        // Whenever a quarter or more of all trios could be placed one after another, the dealer must find such a trio.
        var checked = 0
        var perfect = 0
        for (stage in 1..3) {
            val size = BlocksRamp.boardSizeFor(stage)
            val set = BlocksRamp.shapesFor(stage)
            for (seed in 0 until 25) {
                val random = Random(seed * 7 + stage)
                val board = randomBoard(size, stage, 4 + seed % 14, random)
                val multisets = ArrayList<List<BlockShape>>()
                for (a in set.indices) for (b in a until set.size) for (c in b until set.size) multisets += listOf(set[a], set[b], set[c])
                val good = multisets.count { oraclePlaceAll(board, it) }
                if (good * 4 >= multisets.size) {
                    checked++
                    val trio = TrayDealer.deal(board, stage, null, random)
                    assertTrue("stage $stage seed $seed: a placeable trio exists ($good of ${multisets.size}) but got $trio\n$board", oraclePlaceAll(board, trio))
                    perfect++
                }
            }
        }
        assertTrue("the check must actually run ($checked)", checked >= 30)
        assertEquals(checked, perfect)
    }

    @Test
    fun `on a tight board the dealer prefers a trio that can all be placed over one that cannot`() {
        // Where any all-placeable trio exists at all, the dealer finds one nearly always; count the misses.
        var exist = 0
        var found = 0
        for (seed in 0 until 60) {
            val random = Random(seed)
            val stage = 1 + seed % 3
            val board = packedBoard(BlocksRamp.boardSizeFor(stage), stage, random)
            val set = BlocksRamp.shapesFor(stage)
            val any = set.any { a -> set.any { b -> set.any { c -> oraclePlaceAll(board, listOf(a, b, c)) } } }
            if (!any) continue
            exist++
            if (oraclePlaceAll(board, TrayDealer.deal(board, stage, null, random))) found++
        }
        assertTrue("exist=$exist", exist > 20)
        assertTrue("found $found of $exist", found >= exist * 0.9)
    }

    @Test
    fun `on 7x7 and up a block that would finish a line is on offer`() {
        // Row 3 is missing only three in a row; only bar3h finishes a line.
        val board = Board.from(7) { r, c -> if (r == 3 && c !in 2..4) Family.DOT else null }
        for (stage in 4..6) {
            val b = if (stage == 4) board else Board.from(BlocksRamp.boardSizeFor(stage)) { r, c -> if (r == 3 && c !in 2..4) Family.DOT else null }
            val finishers = BlocksRamp.shapesFor(stage).filter { b.canFinishALine(it) }
            assertTrue(finishers.isNotEmpty())
            for (seed in 0 until 150) {
                val trio = TrayDealer.deal(b, stage, null, Random(seed))
                assertTrue("stage $stage seed $seed: $trio has no line finisher", trio.any { it in finishers })
            }
        }
    }

    @Test
    fun `the near-complete rule does not apply below 7x7`() {
        // On 6x6 the same setup only sometimes offers the finisher: the rule is not forced there.
        val board = Board.from(6) { r, c -> if (r == 3 && c !in 2..4) Family.DOT else null }
        val finishers = BlocksRamp.shapesFor(3).filter { board.canFinishALine(it) }
        assertTrue(finishers.isNotEmpty())
        val without = (0 until 300).count { seed -> TrayDealer.deal(board, 3, null, Random(seed)).none { it in finishers } }
        assertTrue("some deals must be free of the finisher, got $without", without > 0)
    }

    @Test
    fun `a line finisher is never offered at the price of the hard guarantee`() {
        for (seed in 0 until 100) {
            val random = Random(seed)
            val stage = 4 + seed % 3
            val size = BlocksRamp.boardSizeFor(stage)
            val board = randomBoard(size, stage, 10 + seed, random)
            val trio = TrayDealer.deal(board, stage, null, random)
            assertTrue(trio.any { board.hasSpot(it) })
        }
    }

    // ------------------------------------------------------------------ the gentle clear-out

    /** Every row full except one hole on the diagonal: no line is complete and only dots fit. */
    private fun diagonalHoles(n: Int) = Board.from(n) { r, c -> if (r == c) null else Family.BAR3 }

    @Test
    fun `stuck means the tray has blocks and none of them fits`() {
        val b = diagonalHoles(5)
        assertTrue(GentleClearOut.isStuck(b, listOf(shape("bar2h"), shape("bar3v"))))
        assertFalse(GentleClearOut.isStuck(b, listOf(shape("bar2h"), BlockShapes.dot)))
        assertFalse("an empty tray is not stuck", GentleClearOut.isStuck(b, emptyList()))
        assertFalse(GentleClearOut.isStuck(Board.empty(5), listOf(shape("bar2h"))))
    }

    @Test
    fun `nothing is cleared when something fits`() {
        assertNull(GentleClearOut.clear(Board.empty(5), listOf(shape("bar3h"))))
        assertNull(GentleClearOut.clear(diagonalHoles(5), listOf(BlockShapes.dot)))
        assertNull(GentleClearOut.clear(diagonalHoles(5), emptyList()))
    }

    @Test
    fun `clear-out takes two rows on 5x5 and 6x6 and three on 7x7 to 9x9, the fullest first and the top on ties`() {
        for (n in 5..9) {
            val out = GentleClearOut.clear(diagonalHoles(n), listOf(shape("bar2h")))
            assertNotNull(out)
            // All rows are equally full, so the top ones go.
            assertEquals("size $n", (0 until BlocksRamp.clearOutRowCount(n)).toList(), out!!.rows)
            assertEquals(BlocksRamp.clearOutRowCount(n), out.rows.size)
            assertEquals(out.rows.size * (n - 1), out.removed.size)
        }
    }

    @Test
    fun `the fullest rows go first, and the top one wins a tie`() {
        // Row fill: 1, 2, 4, 3, 4. Rows 2 and 4 tie on four cells, so the order is 2, 4, 3, 1, 0.
        val b = Board.parse(
            "#....",
            "##...",
            "####.",
            "###..",
            ".####",
        )
        val out = GentleClearOut.clear(b, listOf(shape("bar5h")))!!
        assertEquals(listOf(2, 4), out.rows)
        assertEquals(8, out.removed.size)
        assertEquals(1, out.board.rowFill(0))
        assertEquals(2, out.board.rowFill(1))
        assertEquals(3, out.board.rowFill(3))
    }

    @Test
    fun `it repeats only until a tray block fits, and no further`() {
        // A bar of 5 standing needs a whole free column: on diagonal holes that takes clearing rows 0-3 (two rounds of two).
        val b = diagonalHoles(5)
        val out = GentleClearOut.clear(b, listOf(shape("bar5v")))!!
        assertEquals(listOf(0, 1, 2, 3), out.rows)
        assertTrue(out.board.hasSpot(shape("bar5v")))
        // One round is enough for a bar of 2: rows 0 and 1 only, and row 2 onwards is untouched.
        val small = GentleClearOut.clear(b, listOf(shape("bar2h")))!!
        assertEquals(listOf(0, 1), small.rows)
        for (r in 2 until 5) for (c in 0 until 5) assertEquals(b.familyAt(r, c), small.board.familyAt(r, c))
    }

    @Test
    fun `clear-out leaves every other cell where it was`() {
        val b = diagonalHoles(7)
        val out = GentleClearOut.clear(b, listOf(shape("bar2h")))!!
        for (r in 0 until 7) for (c in 0 until 7) {
            if (r in out.rows) assertNull(out.board.familyAt(r, c)) else assertEquals(b.familyAt(r, c), out.board.familyAt(r, c))
        }
    }

    @Test
    fun `after a clear-out something always fits and it always ends, over thousands of random boards and trays`() {
        val random = Random(2026)
        val everything = BlockShapes.all
        var cleared = 0
        repeat(4000) {
            val size = 5 + random.nextInt(5)
            val stage = (1..6).filter { BlocksRamp.boardSizeFor(it) == size }.random(random)
            val board = packedBoard(size, stage, random)
            val remaining = List(1 + random.nextInt(3)) { everything.filter { s -> s.width <= size && s.height <= size }.random(random) }
            val out = GentleClearOut.clear(board, remaining)
            if (GentleClearOut.isStuck(board, remaining)) {
                assertNotNull(out)
                cleared++
                assertTrue("still stuck after clear-out\n${out!!.board}\n$remaining", remaining.any { out.board.hasSpot(it) })
                assertFalse(GentleClearOut.isStuck(out.board, remaining))
                assertTrue(out.board.filledCount <= board.filledCount)
                // Only whole rows went: the untouched rows are the same, cleared rows are empty.
                for (r in 0 until size) for (c in 0 until size) {
                    if (r in out.rows) assertNull(out.board.familyAt(r, c)) else assertEquals(board.familyAt(r, c), out.board.familyAt(r, c))
                }
            } else {
                assertNull(out)
            }
        }
        assertTrue("the test must hit real clear-outs ($cleared)", cleared > 200)
    }

    @Test
    fun `even a nearly full board with awkward blocks ends with room`() {
        // Worst case: every row holds cells, and the tray blocks are the biggest of the set.
        for (n in 5..9) {
            val b = Board.from(n) { r, c -> if (r == c) null else Family.BAR5 }
            val biggest = BlockShapes.all.filter { it.width <= n && it.height <= n }.sortedByDescending { it.size }.take(3)
            val out = GentleClearOut.clear(b, biggest)!!
            assertTrue(biggest.any { out.board.hasSpot(it) })
        }
    }
}
