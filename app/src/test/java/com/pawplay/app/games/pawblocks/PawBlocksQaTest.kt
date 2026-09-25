package com.pawplay.app.games.pawblocks

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.pow
import kotlin.random.Random

/**
 * QA pass for Paw Blocks (docs/PRD.md Milestone 5, stories 39-50), written from the PRD rather than from the code:
 * every rule below is checked against a small model or table that does not share code with the game.
 *
 *  - rules against an independent model, the ramp against the PRD table
 *  - the fit guarantee against adversarial boards, the clear-out against a model of the PRD sentence
 *  - a long adversarial session (a player who tries to fill the board) with timing invariants on every event
 *  - snap maths against a brute-force oracle, colours re-derived, layout sweeps
 */
class PawBlocksQaTest {
    /** Paws that cannot run out, for the tests that check the rescue always leaves a move (the real 3-paw rule is in PawBlocksScoreAndPawsTest). */
    private val PLENTY_OF_PAWS = 1_000_000

    private fun shape(id: String) = BlockShapes.byId.getValue(id)
    private val dot = BlockShapes.dot

    // ================================================================== 1. rules against an independent model

    /** Plain 2-D array model of the PRD rules. Shares nothing with [Board]. */
    private class Model(val n: Int) {
        val g = Array(n) { arrayOfNulls<Family>(n) }
        fun fits(s: BlockShape, r: Int, c: Int) = s.cells.all { (r + it.row) in 0 until n && (c + it.col) in 0 until n && g[r + it.row][c + it.col] == null }
        fun copyGrid() = Array(n) { g[it].copyOf() }
    }

    @Test
    fun `placement, row and column clears match an independent model over thousands of random moves`() {
        val random = Random(2024)
        var multi = 0
        var rowClears = 0
        var colClears = 0
        var moves = 0
        for (n in 5..9) repeat(4) { round ->
            var board = Board.empty(n)
            val m = Model(n)
            val set = BlocksRamp.shapesFor(if (round % 2 == 0) 6 else 3).filter { it.width <= n && it.height <= n }
            repeat(400) {
                val s = set[random.nextInt(set.size)]
                val r = random.nextInt(-1, n + 1)
                val c = random.nextInt(-1, n + 1)
                val legal = m.fits(s, r, c)
                assertEquals("legality of ${s.id} at $r,$c on\n$board", legal, board.canPlace(s, r, c))
                if (!legal) {
                    assertNull("refused placements do nothing", runCatching { board.place(s, r, c) }.getOrNull())
                    return@repeat
                }
                moves++
                for (cell in s.cells) m.g[r + cell.row][c + cell.col] = s.family
                val rows = (0 until n).filter { rr -> (0 until n).all { m.g[rr][it] != null } }
                val cols = (0 until n).filter { cc -> (0 until n).all { m.g[it][cc] != null } }
                val before = m.copyGrid()
                val expectedRemoved = HashSet<Triple<Int, Int, Family>>()
                for (rr in 0 until n) for (cc in 0 until n) if ((rr in rows || cc in cols) && m.g[rr][cc] != null) {
                    expectedRemoved += Triple(rr, cc, m.g[rr][cc]!!); m.g[rr][cc] = null
                }
                val placement = board.placeAndClear(s, r, c)
                assertEquals(rows, placement.rows)
                assertEquals(cols, placement.cols)
                assertEquals(rows.isNotEmpty() || cols.isNotEmpty(), placement.cleared)
                assertEquals(expectedRemoved, placement.removed.map { Triple(it.row, it.col, it.family) }.toSet())
                assertEquals("no cell reported twice", expectedRemoved.size, placement.removed.size)
                for (rr in 0 until n) for (cc in 0 until n) {
                    assertEquals("cell $rr,$cc after ${s.id} at $r,$c", m.g[rr][cc], placement.board.familyAt(rr, cc))
                    // Nothing falls: a cell not on a completed line is exactly where it was.
                    if (rr !in rows && cc !in cols) assertEquals(before[rr][cc], placement.board.familyAt(rr, cc))
                }
                if (rows.isNotEmpty()) rowClears++
                if (cols.isNotEmpty()) colClears++
                if (rows.size + cols.size >= 2) multi++
                board = placement.board
                assertTrue("a resting board never holds a complete line", board.completedRows().isEmpty() && board.completedCols().isEmpty())
            }
        }
        assertTrue("model coverage: rows $rowClears cols $colClears multi $multi of $moves", rowClears > 30 && colClears > 30 && multi > 5)
    }

    @Test
    fun `one placement that finishes a row and a column together clears both crossing lines and is one clear`() {
        // Cross shape of dots: row 2 and column 2 both missing only their crossing cell.
        val b = Board.from(5) { r, c -> if ((r == 2) != (c == 2)) Family.BAR3 else null }
        val p = b.placeAndClear(dot, 2, 2)
        assertEquals(listOf(2), p.rows); assertEquals(listOf(2), p.cols)
        assertEquals("9 cells of the cross, the crossing counted once", 9, p.removed.size)
        assertEquals(0, p.board.filledCount)
        val s = BlocksSession(Random(1), startClears = 2, startBoard = b, startTray = listOf(dot, dot, dot))
        s.place(0, 2, 2, 0)
        assertEquals("a double line is ONE clear", 3, s.clears)
    }

    @Test
    fun `growing adds an empty bottom row and right column and never changes a placed cell, up to 9x9`() {
        val random = Random(5)
        var b = Board.from(5) { _, _ -> if (random.nextInt(3) > 0) Family.values()[random.nextInt(8)] else null }
        for (n in 5..8) {
            val g = b.grown()
            assertEquals(n + 1, g.size)
            for (r in 0..n) for (c in 0..n) {
                if (r == n || c == n) assertNull("new row/column is empty", g.familyAt(r, c)) else assertEquals(b.familyAt(r, c), g.familyAt(r, c))
            }
            b = g
        }
        assertEquals(9, b.size)
    }

    // ================================================================== 2. the ramp, from the PRD table

    private val prdSets = listOf(
        setOf("dot", "bar2h", "bar2v"),
        setOf("bar3h", "bar3v"),
        setOf("sq", "cTL", "cTR", "cBR", "cBL"),
        setOf("bar4h", "bar4v"),
        setOf("rectV", "rectH"),
        setOf("bar5h", "bar5v"),
    )

    @Test
    fun `stage, board size and block set follow the PRD ramp table at every clear count`() {
        fun stage(c: Int) = when { c >= 35 -> 6; c >= 24 -> 5; c >= 15 -> 4; c >= 8 -> 3; c >= 3 -> 2; else -> 1 }
        val boards = mapOf(1 to 5, 2 to 5, 3 to 6, 4 to 7, 5 to 8, 6 to 9)
        for (c in 0..120) {
            assertEquals("stage at $c clears", stage(c), BlocksRamp.stageFor(c))
            assertEquals(boards[stage(c)], BlocksRamp.boardSizeFor(stage(c)))
            val expected = prdSets.take(stage(c)).flatten().toSet()
            assertEquals("blocks at stage ${stage(c)}", expected, BlocksRamp.shapesFor(stage(c)).map { it.id }.toSet())
        }
        assertEquals(listOf(2, 2, 3, 3, 3), (5..9).map { BlocksRamp.clearOutRowCount(it) })
    }

    /** A board of the size of [startClears]'s stage with row 0 full but for (0,0) and a marker cell in the far corner. */
    private fun oneFromClearing(startClears: Int): Pair<Board, Int> {
        val n = BlocksRamp.boardSizeFor(BlocksRamp.stageFor(startClears))
        val b = Board.from(n) { r, c -> if (r == 0 && c != 0) Family.BAR3 else if (r == n - 1 && c == n - 1) Family.BAR5 else null }
        return b to n
    }

    @Test
    fun `each threshold clear advances the stage, grows the board once the animation is over, keeps placed cells, and the cap holds`() {
        val thresholds = listOf(3, 8, 15, 24, 35)
        val expectedSize = mapOf(3 to 5, 8 to 6, 15 to 7, 24 to 8, 35 to 9)
        for (k in thresholds + listOf(4, 9, 16, 25, 36, 60)) {
            val (board, n) = oneFromClearing(k - 1)
            val s = BlocksSession(Random(k), startClears = k - 1, startBoard = board, startTray = listOf(dot, dot, dot))
            val p = s.place(0, 0, 0, 0)!!
            assertTrue(p.cleared)
            assertEquals(k, s.clears)
            val newSize = BlocksRamp.boardSizeFor(BlocksRamp.stageFor(k))
            var grewAt = -1L
            for (t in 0..4000L step 10) {
                for (e in s.tick(t)) if (e is SessionEvent.Grew && grewAt < 0) grewAt = t
                if (t < 1090) assertEquals("no growth while the line-clear plays (t=$t, clears=$k)", n, s.board.size)
            }
            assertEquals("board after $k clears", newSize, s.board.size)
            if (newSize > n) {
                assertTrue("growth at $grewAt must wait for the ~1s clear animation", grewAt >= 1090)
                assertEquals("the corner cell keeps its row and column", Family.BAR5, s.board.familyAt(n - 1, n - 1))
                for (i in 0 until s.board.size) { assertNull(s.board.familyAt(s.board.size - 1, i)); assertNull(s.board.familyAt(i, s.board.size - 1)) }
            } else assertEquals("no growth expected at $k clears", -1L, grewAt)
            if (k in expectedSize) assertEquals(expectedSize[k], s.board.size)
            assertTrue(s.board.size <= 9)
        }
    }

    @Test
    fun `no growth past 9x9 however long it runs, and a 9x9 board with all its clears never grows`() {
        val (board, _) = oneFromClearing(200)
        val s = BlocksSession(Random(3), startClears = 200, startBoard = board, startTray = listOf(dot, dot, dot))
        s.place(0, 0, 0, 0)
        var t = 0L
        repeat(400) { t += 50; assertTrue(s.tick(t).none { it is SessionEvent.Grew }) }
        assertEquals(9, s.board.size)
    }

    // ================================================================== 3. adversarial boards for the dealer

    private fun cleanLines(b: Board, random: Random): Board {
        // Real boards never hold a complete line: open one random cell in every full row/column.
        var board = b
        val n = b.size
        var guard = 0
        while ((board.completedRows().isNotEmpty() || board.completedCols().isNotEmpty()) && guard++ < 50) {
            val rows = board.completedRows(); val cols = board.completedCols()
            board = Board.from(n) { r, c ->
                val open = (r in rows && c == (r * 7 + 3) % n) || (c in cols && r == (c * 5 + 1) % n)
                if (open) null else board.familyAt(r, c)
            }
        }
        return board
    }

    private fun adversarialBoards(n: Int, random: Random): List<Pair<String, Board>> {
        val out = ArrayList<Pair<String, Board>>()
        // Nearly full: exactly one empty cell in every row and column (a permutation), the fullest a real board can be.
        repeat(12) {
            val perm = (0 until n).shuffled(random)
            out += "permutation" to Board.from(n) { r, c -> if (perm[r] == c) null else Family.BAR3 }
        }
        out += "diagonal" to Board.from(n) { r, c -> if (r == c) null else Family.BAR3 }
        out += "anti-diagonal" to Board.from(n) { r, c -> if (r + c == n - 1) null else Family.BAR3 }
        // Fragmented: checkerboard both ways (only single cells are free).
        out += "checker-a" to Board.from(n) { r, c -> if ((r + c) % 2 == 0) null else Family.BAR3 }
        out += "checker-b" to Board.from(n) { r, c -> if ((r + c) % 2 == 1) null else Family.BAR3 }
        // Every other row / column left empty (lines free but no 2-wide anything).
        out += "row-stripes" to Board.from(n) { r, _ -> if (r % 2 == 0) null else Family.BAR3 }
        out += "col-stripes" to Board.from(n) { _, c -> if (c % 2 == 0) null else Family.BAR3 }
        // Random fragmentation from thin to nearly solid.
        for (p in listOf(0.3, 0.5, 0.65, 0.8, 0.9, 0.95)) repeat(8) {
            out += "random-$p" to cleanLines(Board.from(n) { _, _ -> if (random.nextDouble() < p) Family.values()[random.nextInt(8)] else null }, random)
        }
        // Never reachable in play, but the dealer must still not throw or hang on it: completely full, and completely empty.
        out += "full(unreachable)" to Board.from(n) { _, _ -> Family.BAR3 }
        out += "empty" to Board.empty(n)
        return out
    }

    @Test
    fun `at every refill at least one block has a legal spot, on adversarial boards at every size and stage`() {
        val random = Random(99)
        var deals = 0
        val started = System.nanoTime()
        for (n in 5..9) for ((name, board) in adversarialBoards(n, random)) for (stage in 1..6) {
            val reachable = board.emptyCount > 0
            val set = BlocksRamp.shapesFor(stage)
            repeat(2) { rep ->
                val previous = if (rep == 0) null else List(3) { set[random.nextInt(set.size)].id }
                val trio = TrayDealer.deal(board, stage, previous, Random(random.nextInt()))
                deals++
                assertEquals(3, trio.size)
                assertTrue("only this stage's blocks", trio.all { it in set })
                if (reachable) assertTrue("no block fits: $name ${n}x$n stage $stage trio $trio\n$board", trio.any { board.hasSpot(it) })
            }
        }
        val ms = (System.nanoTime() - started) / 1_000_000
        assertTrue("dealt $deals in $ms ms: the dealer must be cheap enough to run on a tap", ms < 60_000)
    }

    @Test
    fun `dealing never repeats the previous trio in any order, on boards with room`() {
        for (stage in 1..6) for (n in listOf(5, 6, 7, 8, 9)) {
            val board = Board.empty(n)
            val set = BlocksRamp.shapesFor(stage)
            val random = Random(stage * 100 + n)
            var previous = List(3) { set[random.nextInt(set.size)] }
            repeat(300) {
                val trio = TrayDealer.deal(board, stage, previous.map { it.id }, random)
                assertTrue("same trio twice: $previous then $trio", trio.map { it.id }.sorted() != previous.map { it.id }.sorted())
                previous = trio
            }
        }
    }

    @Test
    fun `best effort - three blocks placeable one after another - holds on boards with room at every size`() {
        var total = 0
        var good = 0
        for (n in 5..9) for (stage in 1..6) {
            val random = Random(n * 10 + stage)
            repeat(15) {
                // half-full board reached by random legal play
                var b = Board.empty(n)
                val set = BlocksRamp.shapesFor(stage)
                repeat(n) { val s = set[random.nextInt(set.size)]; val sp = b.spots(s); if (sp.isNotEmpty()) sp[random.nextInt(sp.size)].let { p -> b = b.placeAndClear(s, p.row, p.col).board } }
                if (b.emptyCount < n * n / 2) return@repeat
                val trio = TrayDealer.deal(b, stage, null, random)
                total++
                if (TrayDealer.canPlaceAll(b, trio)) good++
            }
        }
        assertTrue("$good of $total trios placeable in some order", total > 100 && good == total)
    }

    @Test
    fun `on 7x7 and up a block that finishes a near-complete line is on offer, for rows and columns and every gap length`() {
        var offers = 0
        var withFinisher = 0
        for (n in 7..9) for (gap in 1..5) for (vertical in listOf(false, true)) for (stage in 4..6) {
            if (gap >= 4 && stage < 4 || gap == 5 && stage < 6) continue
            // Line 3 is full but for `gap` contiguous cells; the rest of the board is empty.
            val board = Board.from(n) { r, c ->
                val (line, pos) = if (vertical) c to r else r to c
                if (line == 3 && pos !in 2 until 2 + gap) Family.BAR3 else null
            }
            // Independent oracle: which stage blocks can finish some line here?
            val finishers = BlocksRamp.shapesFor(stage).filter { s ->
                board.spots(s).any { sp ->
                    val after = HashSet<Pair<Int, Int>>()
                    for (r in 0 until n) for (c in 0 until n) if (board.familyAt(r, c) != null) after += r to c
                    for (cell in s.cells) after += (sp.row + cell.row) to (sp.col + cell.col)
                    (0 until n).any { rr -> (0 until n).all { (rr to it) in after } } || (0 until n).any { cc -> (0 until n).all { (it to cc) in after } }
                }
            }
            if (finishers.isEmpty()) continue
            repeat(60) { seed ->
                val trio = TrayDealer.deal(board, stage, null, Random(seed))
                offers++
                if (trio.any { it in finishers }) withFinisher++
                assertTrue("still at least one placeable", trio.any { board.hasSpot(it) })
            }
        }
        assertTrue("$withFinisher of $offers deals offered a finisher", offers > 500 && withFinisher == offers)
    }

    // ================================================================== 4. clear-out against a model of the PRD sentence

    private fun rowFill(b: Board, r: Int) = (0 until b.size).count { b.familyAt(r, it) != null }

    /** The PRD sentence, written out: fullest rows first (top first on ties), ceil(n/3) at a time, only until a block fits. */
    private fun modelClearOut(board: Board, tray: List<BlockShape>): Set<Int> {
        val order = (0 until board.size).sortedWith(compareByDescending<Int> { rowFill(board, it) }.thenBy { it })
        val per = (board.size + 2) / 3
        var current = board
        val gone = LinkedHashSet<Int>()
        var i = 0
        while (tray.none { current.hasSpot(it) } && i < order.size) {
            val chunk = order.drop(i).take(per); i += per
            for (r in chunk) { gone += r; current = Board.from(current.size) { rr, cc -> if (rr == r) null else current.familyAt(rr, cc) } }
        }
        return gone.filter { r -> rowFill(board, r) > 0 }.toSet()
    }

    @Test
    fun `clear-out removes exactly the model's rows, ends with room, and touches nothing else, on hundreds of stuck boards`() {
        val random = Random(4242)
        var stuckBoards = 0
        val perSize = HashMap<Int, Int>()
        var attempts = 0
        while (stuckBoards < 600 && attempts++ < 40_000) {
            val n = random.nextInt(5, 10)
            val p = 0.55 + random.nextDouble() * 0.4
            val board = cleanLines(Board.from(n) { _, _ -> if (random.nextDouble() < p) Family.values()[random.nextInt(8)] else null }, random)
            val set = BlocksRamp.shapesFor(random.nextInt(1, 7)).filter { it.width <= n && it.height <= n }
            val tray = List(random.nextInt(1, 4)) { set[random.nextInt(set.size)] }
            if (!GentleClearOut.isStuck(board, tray)) {
                assertNull("nothing fits-check: no clear-out when something fits", GentleClearOut.clear(board, tray))
                continue
            }
            stuckBoards++
            perSize.merge(n, 1, Int::plus)
            val out = GentleClearOut.clear(board, tray)!!
            val expectedRows = modelClearOut(board, tray)
            assertEquals("rows for ${n}x$n tray $tray\n$board", expectedRows, out.rows.toSet())
            assertEquals("no row listed twice", out.rows.size, out.rows.toSet().size)
            assertTrue("after the clear-out a block fits", tray.any { out.board.hasSpot(it) })
            assertFalse(GentleClearOut.isStuck(out.board, tray))
            for (r in 0 until n) for (c in 0 until n) {
                if (r in out.rows) assertNull(out.board.familyAt(r, c)) else assertEquals("only whole rows go, nothing moves", board.familyAt(r, c), out.board.familyAt(r, c))
            }
            assertEquals("removed = every filled cell of those rows", out.rows.sumOf { rowFill(board, it) }, out.removed.size)
            assertEquals(board.filledCount - out.removed.size, out.board.filledCount)
            // Fewest rounds: one round fewer would still leave it stuck (repeats only if still needed).
            val per = BlocksRamp.clearOutRowCount(n)
            if (out.rows.size > per) {
                val fewer = Board.from(n) { r, c -> if (r in out.rows.take(out.rows.size - (out.rows.size % per).let { if (it == 0) per else it })) null else board.familyAt(r, c) }
                assertTrue("a shorter clear-out would have been enough", tray.none { fewer.hasSpot(it) })
            }
            // No loop: with the same tray it is not stuck, so a second clear-out is not due.
            assertNull(GentleClearOut.clear(out.board, tray))
        }
        assertTrue("only $stuckBoards stuck boards generated", stuckBoards >= 600)
        for (n in 5..9) assertTrue("board size $n covered ($perSize)", (perSize[n] ?: 0) > 20)
    }

    @Test
    fun `clear-out on a board that is nothing but the worst case still ends and always leaves room`() {
        for (n in 5..9) {
            // Every row nearly full (one gap, staggered so no line completes) and a tray that needs real room.
            val board = Board.from(n) { r, c -> if (c == (r * 2 + 1) % n && (r * 2 + 1) % n != c - 1) null else Family.BAR3 }.let { cleanLines(it, Random(1)) }
            for (tray in listOf(listOf(shape("rectH")), listOf(shape("bar5h"), shape("bar5v"), shape("rectV")), listOf(shape("sq")))) {
                if (tray.any { it.width > n || it.height > n }) continue
                val out = GentleClearOut.clear(board, tray) ?: continue
                assertTrue(tray.any { out.board.hasSpot(it) })
                assertTrue(out.rows.size <= n)
            }
        }
    }

    // ================================================================== 5. adversarial session with timing invariants

    private class Stats { var moves = 0; var clearOuts = 0; var refills = 0; var growths = 0; var lines = 0; var maxStage = 1; var maxWait = 0L; var holds = 0 }

    private fun adversarial(startClears: Int, seed: Int, strategy: Int, moves: Int, stats: Stats) {
        val rnd = Random(seed)
        val s = BlocksSession(Random(seed * 31 + 7), startClears = startClears, startPaws = PLENTY_OF_PAWS)
        var now = 0L
        var lastMove = -100_000L
        var busyEnd = -100_000L
        var holding = false
        var outSinceMove = false
        var lastTrio: List<String> = s.tray.filterNotNull().map { it.id }.sorted()
        fun ctx() = "start=$startClears seed=$seed strategy=$strategy t=$now clears=${s.clears} size=${s.board.size}"

        fun step(dt: Long): List<SessionEvent> {
            now += dt
            val boardBefore = s.board; val trayBefore = s.tray; val clearsBefore = s.clears; val dealsBefore = s.trayDeals
            val pawsBefore = s.paws; val scoreBefore = s.score
            val events = s.tick(now)
            assertEquals("tick never changes the ramp count: ${ctx()}", clearsBefore, s.clears)
            for (e in events) when (e) {
                is SessionEvent.Grew -> {
                    stats.growths++
                    assertFalse("growth while a block is held: ${ctx()}", holding)
                    assertTrue("growth before the line-clear animation ended: ${ctx()}", now >= busyEnd)
                    assertEquals(boardBefore.grown(), s.board)
                    assertTrue(s.board.size <= 9 && s.board.size <= BlocksRamp.boardSizeFor(s.stage))
                }
                SessionEvent.Refilled -> {
                    stats.refills++
                    assertTrue("refill with blocks still in the tray: ${ctx()}", trayBefore.all { it == null })
                    assertTrue(s.tray.all { it != null })
                    assertEquals("exactly one refill", dealsBefore + 1, s.trayDeals)
                    assertTrue("refill must offer a block that fits: ${ctx()}\n${s.board}", s.tray.filterNotNull().any { s.board.hasSpot(it) })
                    assertTrue("refill earlier than 500ms after the last block/animation: ${ctx()}", now >= max(lastMove, busyEnd) + 500)
                    val ids = s.tray.filterNotNull().map { it.id }.sorted()
                    assertTrue("same trio twice in a row: ${ctx()}", ids != lastTrio)
                    lastTrio = ids
                    assertEquals(s.stage, s.trayStage)
                    assertTrue(s.tray.filterNotNull().all { it in BlocksRamp.shapesFor(s.stage) })
                }
                is SessionEvent.ClearedOut -> {
                    stats.clearOuts++
                    assertFalse("clear-out under a held block: ${ctx()}", holding)
                    assertTrue("clear-out sooner than 1s after the last move and its animation: ${ctx()}", now >= max(lastMove, busyEnd) + 1000)
                    assertFalse("a second clear-out without a move in between (loop): ${ctx()}", outSinceMove)
                    outSinceMove = true
                    assertTrue("it only fires when nothing in the tray fit", trayBefore.filterNotNull().none { boardBefore.hasSpot(it) } && trayBefore.any { it != null })
                    assertEquals("the tray is untouched", trayBefore, s.tray)
                    val order = (0 until boardBefore.size).sortedWith(compareByDescending<Int> { rowFill(boardBefore, it) }.thenBy { it })
                    assertEquals("fullest rows first, top on ties: ${ctx()}", order.take(e.rows.size), e.rows)
                    val per = BlocksRamp.clearOutRowCount(boardBefore.size)
                    assertTrue("whole rounds of $per rows unless the board ran out", e.rows.size % per == 0 || e.rows.size == boardBefore.size - order.count { rowFill(boardBefore, it) == 0 })
                    for (r in 0 until boardBefore.size) for (c in 0 until boardBefore.size)
                        assertEquals(if (r in e.rows) null else boardBefore.familyAt(r, c), s.board.familyAt(r, c))
                    assertTrue("something fits after a clear-out: ${ctx()}", s.tray.filterNotNull().any { s.board.hasSpot(it) })
                    assertEquals(e.removed.size, boardBefore.filledCount - s.board.filledCount)
                    assertEquals("one paw per clear-out: ${ctx()}", pawsBefore - 1, s.paws)
                    assertEquals(s.paws, e.pawsLeft)
                    assertEquals("a clear-out scores nothing: ${ctx()}", scoreBefore, s.score)
                }
                is SessionEvent.GameEnded -> fail("the game ended with paws that cannot run out: ${ctx()}")
            }
            assertTrue("a resting board never holds a complete line", s.board.completedRows().isEmpty() && s.board.completedCols().isEmpty())
            assertTrue(s.board.size in 5..9)
            assertEquals(3, s.tray.size)
            stats.maxStage = max(stats.maxStage, s.stage)
            return events
        }

        fun candidateScore(slot: Int, sp: Spot): Long {
            val shape = s.tray[slot]!!
            val res = s.board.placeAndClear(shape, sp.row, sp.col)
            return when (strategy) {
                0 -> { // fragmenter: never finish a line if avoidable; leave as little room as possible
                    val set = BlocksRamp.shapesFor(s.stage)
                    (if (res.cleared) 1_000_000L else 0L) + set.sumOf { res.board.spots(it).size }.toLong()
                }
                1 -> if (res.cleared) -1_000L else -res.board.filledCount.toLong() // line-lover: clear when possible, else pack
                else -> rnd.nextInt(1000).toLong()
            }
        }

        repeat(moves) { moveNo ->
            // Wait until something can be placed (the game's own follow-ups: growth, refill, clear-out).
            var waited = 0L
            while (s.tray.filterNotNull().none { s.board.hasSpot(it) }) {
                step(50); waited += 50
                // The child picks up a block he cannot place while the game is about to make room: nothing may happen under his finger.
                if (waited == 100L && s.tray.any { it != null } && rnd.nextInt(3) == 0) {
                    stats.holds++
                    s.setHolding(true, now); holding = true
                    repeat(40) { step(100) }
                    s.setHolding(false, now); holding = false
                    lastMove = now
                }
                if (waited > 6000) fail("child left stuck for ${waited}ms with tray ${s.tray.map { it?.id }} ${ctx()}\n${s.board}")
            }
            stats.maxWait = max(stats.maxWait, waited)
            // A held block freezes growth and clear-out (never refill: nothing to hold when the tray is empty).
            if (rnd.nextInt(8) == 0) {
                stats.holds++
                s.setHolding(true, now); holding = true
                repeat(30) { step(100) }
                s.setHolding(false, now); holding = false
                lastMove = now
            }
            if (rnd.nextInt(3) == 0) step(rnd.nextLong(0, 300))
            val options = (0 until 3).filter { s.tray[it] != null }.flatMap { slot -> s.board.spots(s.tray[slot]!!).map { slot to it } }
            if (options.isEmpty()) return@repeat
            val sample = if (options.size > 14) List(14) { options[rnd.nextInt(options.size)] } else options
            val (slot, spot) = sample.minByOrNull { (sl, sp) -> candidateScore(sl, sp) }!!
            val clearsBefore = s.clears
            val trayBefore = s.tray
            val placed = s.place(slot, spot.row, spot.col, now)
            assertNotNull(placed)
            assertEquals(clearsBefore + if (placed!!.cleared) 1 else 0, s.clears)
            assertNull("the slot empties on placement", s.tray[slot])
            for (i in 0 until 3) if (i != slot) assertEquals("no other slot changes", trayBefore[i], s.tray[i])
            assertNull("no double placement", s.place(slot, spot.row, spot.col, now))
            assertEquals(clearsBefore + if (placed.cleared) 1 else 0, s.clears)
            stats.moves++
            if (placed.cleared) { stats.lines++; busyEnd = max(busyEnd, now + 90 + 1000) }
            lastMove = now
            outSinceMove = false
            step(rnd.nextLong(10, 80))
        }
    }

    @Test
    fun `an adversarial player who tries to fill the board is never left stuck, and every follow-up obeys its timing`() {
        val stats = Stats()
        val starts = listOf(0, 3, 8, 15, 24, 35)
        for ((i, start) in starts.withIndex()) for (strategy in 0..2) for (seed in 0..1)
            adversarial(start, 1000 + i * 100 + strategy * 10 + seed, strategy, 260, stats)
        println("QA fuzz: moves=${stats.moves} lines=${stats.lines} refills=${stats.refills} clearOuts=${stats.clearOuts} growths=${stats.growths} holds=${stats.holds} maxWaitMs=${stats.maxWait} maxStage=${stats.maxStage}")
        assertTrue("moves ${stats.moves}", stats.moves > 8000)
        assertTrue("the adversary must have forced clear-outs (${stats.clearOuts})", stats.clearOuts > 30)
        assertTrue("and growth (${stats.growths})", stats.growths >= 3)
        assertTrue("the longest wait a child ever sat with no legal move: ${stats.maxWait}ms", stats.maxWait <= 4000)
    }

    @Test
    fun `a stuck tray is never cleared out under a held block, only a second after it is let go`() {
        // 5x5, only the diagonal is empty: a bar of 2 fits nowhere, so the tray is stuck from the start.
        val board = Board.from(5) { r, c -> if (r == c) null else Family.BAR3 }
        val s = BlocksSession(Random(1), startBoard = board, startTray = listOf(shape("bar2h"), shape("bar2v"), shape("sq")))
        assertTrue(s.isStuck)
        s.setHolding(true, 0)
        var t = 0L
        repeat(200) { t += 50; assertTrue("nothing while held (t=$t)", s.tick(t).isEmpty()) }
        s.setHolding(false, t)
        val letGo = t
        var firedAt = -1L
        repeat(80) { t += 50; if (s.tick(t).any { it is SessionEvent.ClearedOut } && firedAt < 0) firedAt = t }
        assertTrue("fired at $firedAt, let go at $letGo", firedAt >= letGo + 1000 && firedAt <= letGo + 1100)
        assertFalse(s.isStuck)
        assertEquals("the tray is untouched", listOf(shape("bar2h"), shape("bar2v"), shape("sq")), s.tray)
        assertEquals(0, s.clears)
    }

    @Test
    fun `a whole game from the empty start, played to fill the board, reaches 9x9 without a dead end`() {
        val stats = Stats()
        adversarial(0, 31337, 1, 3500, stats)
        assertEquals(6, stats.maxStage)
    }

    // ================================================================== 6. snap: brute-force oracle

    private fun oracle(board: Board, shape: BlockShape, cell: Float, gl: Float, gt: Float, fx: Float, fy: Float): Spot? {
        val px = fx - shape.width * cell / 2f
        val py = fy - 64f - shape.height * cell
        val reach = max(cell, 40f)
        val cands = ArrayList<Triple<Spot, Double, Double>>()
        for (r in 0 until board.size) for (c in 0 until board.size) {
            if (r + shape.height > board.size || c + shape.width > board.size) continue
            if (shape.cells.any { !board.isEmpty(r + it.row, c + it.col) }) continue
            val d = hypot((gl + c * cell - px).toDouble(), (gt + r * cell - py).toDouble())
            if (d > reach) continue
            val df = hypot((gl + c * cell + shape.width * cell / 2 - fx).toDouble(), (gt + r * cell + shape.height * cell - fy).toDouble())
            cands += Triple(Spot(r, c), d, df)
        }
        if (cands.isEmpty()) return null
        val minD = cands.minOf { it.second }
        return cands.filter { it.second <= minD + 0.5 }.minByOrNull { it.third }!!.first
    }

    @Test
    fun `snap picks the nearest legal spot within max(one cell, 40dp), ties to the finger, matching a brute-force oracle`() {
        val random = Random(8080)
        var snapped = 0
        var none = 0
        repeat(6000) {
            val n = random.nextInt(5, 10)
            val layout = blocksLayout(360f, 740f)
            val cell = layout.cellSize(n.toFloat())
            val board = Board.from(n) { _, _ -> if (random.nextDouble() < 0.4) Family.BAR3 else null }
            val shape = BlockShapes.all[random.nextInt(BlockShapes.all.size)]
            val fx = random.nextFloat() * 360f
            val fy = 60f + random.nextFloat() * 500f
            val got = Snap.spot(board, shape, cell, layout.gridLeft, layout.gridTop, fx, fy)
            val want = oracle(board, shape, cell, layout.gridLeft, layout.gridTop, fx, fy)
            assertEquals("${shape.id} on ${n}x$n finger $fx,$fy\n$board", want, got)
            if (got != null) { snapped++; assertTrue(board.canPlace(shape, got)) } else none++
            // ghost == drop: the layout's drop function is what both call, and it only ever answers legal spots
            layout.dropSpot(board, shape, cell, fx, fy)?.let { assertTrue(board.canPlace(shape, it)); assertEquals(got, it) }
        }
        assertTrue("coverage $snapped / $none", snapped > 500 && none > 500)
    }

    @Test
    fun `off-screen, home-corner and non-finite fingers are never a drop`() {
        val l = blocksLayout(360f, 740f)
        val b = Board.empty(5)
        val cell = l.cellSize(5f)
        val good = l.dropSpot(b, dot, cell, 180f, l.gridTop + 100f + 64f)
        assertNotNull("sanity: a finger over the empty board drops", good)
        for ((x, y) in listOf(-1f to 300f, 300f to -1f, 361f to 300f, 300f to 741f, Float.NaN to 300f, 300f to Float.POSITIVE_INFINITY, 48f to 48f, 20f to 20f, 76f to 76f))
            assertNull("($x,$y)", l.dropSpot(b, dot, cell, x, y))
    }

    // ================================================================== 7. colours, marks

    private fun lin(v: Int): Double = (v / 255.0).let { if (it <= 0.04045) it / 12.92 else ((it + 0.055) / 1.055).pow(2.4) }
    private fun lum(c: Long) = 0.2126 * lin(((c shr 16) and 255).toInt()) + 0.7152 * lin(((c shr 8) and 255).toInt()) + 0.0722 * lin((c and 255).toInt())
    private fun ratio(a: Long, b: Long): Double { val x = lum(a); val y = lum(b); return (max(x, y) + 0.05) / (minOf(x, y) + 0.05) }

    @Test
    fun `eight families each with its own colour and mark, every pair 1_3 apart in luminance, marks 4 to 1`() {
        val f = Family.values()
        assertEquals(8, f.size)
        assertEquals(8, f.map { it.colour }.toSet().size)
        assertEquals(8, f.map { it.mark }.toSet().size)
        var minPair = 99.0
        for (i in f.indices) for (j in i + 1 until f.size) minPair = minOf(minPair, ratio(f[i].colour, f[j].colour))
        val minMark = f.minOf { ratio(it.colour, it.markColour) }
        println("QA colours: min pair luminance ratio %.3f, min mark contrast %.2f".format(minPair, minMark))
        assertTrue("min pair $minPair", minPair >= 1.3)
        assertTrue("min mark $minMark", minMark >= 4.0)
        // Greyscale is only reported: in gamma-space Rec.601 luma two families (Leaf, Cocoa) land on the same grey; the
        // silhouette and the mark still tell them apart, and the PRD's brightness rule is the WCAG ratio checked above.
        fun grey(c: Long) = 0.299 * ((c shr 16) and 255) + 0.587 * ((c shr 8) and 255) + 0.114 * (c and 255)
        val greys = f.map { it.name to grey(it.colour) }.sortedBy { it.second }
        println("QA greyscale (Rec.601 of sRGB bytes): " + greys.joinToString { "%s=%.1f".format(it.first, it.second) })
    }

    @Test
    fun `sixteen shapes, no rotation needed, and no shape bigger than 6 cells`() {
        assertEquals(16, BlockShapes.all.size)
        assertTrue(BlockShapes.all.all { it.size in 1..6 })
        // Every bar and corner exists in every orientation as its own block.
        for (len in 2..5) assertTrue(BlockShapes.all.any { it.id == "bar${len}v" } && BlockShapes.all.any { it.id == "bar${len}h" })
        assertEquals(4, BlockShapes.all.count { it.family == Family.CORNER })
        val normalised = BlockShapes.all.map { s -> s.cells.map { it.col to it.row }.toSet() }
        assertEquals("no two shapes are the same picture", normalised.size, normalised.toSet().size)
    }

    // ================================================================== 8. layout sweeps

    @Test
    fun `nothing overflows and slots stay at 72dp or more on every phone-sized window`() {
        var worstSlot = 999f
        for (w in 320..900 step 20) for (h in 480..1500 step 20) {
            val l = blocksLayout(w.toFloat(), h.toFloat())
            val ctx = "${w}x$h"
            assertTrue("$ctx slot width ${l.slotWidth}", l.slotWidth >= 72f)
            assertTrue("$ctx slot height ${l.slotHeight}", l.slotHeight >= 72f)
            assertTrue("$ctx panel right", l.panelLeft >= 0f && l.panelRight <= w)
            assertTrue("$ctx tray right", l.slotLeftOf(0) >= 0f && l.slotLeftOf(2) + l.slotWidth <= w + 0.01f)
            assertTrue("$ctx tray bottom ${l.slotTop + l.slotHeight}", l.slotTop + l.slotHeight <= h + 0.01f)
            assertTrue("$ctx tray below board", l.slotTop >= l.panelBottom + 11.99f)
            assertTrue("$ctx board below the home button", l.panelTop >= BlocksLayout.HOME_INSET + BlocksLayout.HOME_SIZE)
            assertTrue("home is at least 48dp", BlocksLayout.HOME_SIZE >= 48f)
            assertTrue("at least 8dp between slots", BlocksLayout.SLOT_GAP >= 8f)
            worstSlot = minOf(worstSlot, minOf(l.slotWidth, l.slotHeight))
        }
        assertTrue(worstSlot >= 72f)
    }

    @Test
    fun `every touch in the tray slots and no touch on the board or home grabs a block`() {
        val ui = BlocksUi(BlocksSession(Random(1), startTray = listOf(dot, shape("bar2h"), shape("bar2v"))))
        val l = ui.layout
        for (x in 0..360 step 6) for (y in 0..692 step 6) {
            val hit = l.slotAt(x.toFloat(), y.toFloat())
            val insideASlot = (0..2).any { i -> x >= l.slotLeftOf(i) && x <= l.slotLeftOf(i) + l.slotWidth && y >= l.slotTop && y <= l.slotTop + l.slotHeight }
            assertEquals("$x,$y", insideASlot, hit >= 0)
            if (l.overHome(x.toFloat(), y.toFloat()) || (x >= l.panelLeft && x <= l.panelRight && y >= l.panelTop && y <= l.panelBottom)) assertEquals(-1, hit)
        }
    }

    /** PRD story 40: 9x9 cells are about 36dp. The board follows the width (6dp margins); 320dp cannot give nine cells more than ~33.3dp. */
    @Test
    fun `a 9x9 board keeps cells of about 36dp at 360 wide and as close as 320 wide allows`() {
        val wide = blocksLayout(360f, 740f)
        assertTrue("cell ${wide.cellSize(9f)}", wide.cellSize(9f) >= 36f)
        val narrow = blocksLayout(320f, 568f)
        assertTrue("cell ${narrow.cellSize(9f)}", narrow.cellSize(9f) >= 33f)
    }

    // ================================================================== 9. the touch layer: BlocksUi

    private fun freshUi(tray: List<BlockShape?> = listOf(dot, shape("bar2h"), shape("bar2v")), board: Board? = null, seed: Int = 3, paws: Int = BlocksRamp.START_PAWS): BlocksUi {
        val s = BlocksSession(Random(seed), startBoard = board, startTray = tray, startPaws = paws)
        return BlocksUi(s).also { it.onFrame(1000) }
    }

    private fun slotCentre(ui: BlocksUi, i: Int) = (ui.layout.slotLeftOf(i) + ui.layout.slotWidth / 2f) to (ui.layout.slotTop + ui.layout.slotHeight / 2f)

    /** Where the finger must be so the block is drawn with its top-left over (row, col). */
    private fun fingerFor(ui: BlocksUi, shape: BlockShape, row: Int, col: Int): Pair<Float, Float> {
        val cell = ui.layout.cellSize(ui.session.board.size.toFloat())
        return (ui.layout.gridLeft + col * cell + shape.width * cell / 2f) to (ui.layout.gridTop + row * cell + shape.height * cell + Snap.RIDE_DP)
    }

    @Test
    fun `a finger on the board, the home corner, the gap between slots or an empty slot never starts a drag`() {
        val ui = freshUi()
        val l = ui.layout
        assertFalse(ui.onDown(1, l.gridLeft + 50f, l.gridTop + 50f))
        assertFalse(ui.onDown(2, 48f, 48f))
        assertFalse(ui.onDown(3, l.slotLeftOf(0) + l.slotWidth + 4f, l.slotTop + 20f)) // in the 8dp gap
        assertFalse(ui.tracker.active)
        val (x, y) = slotCentre(ui, 1)
        assertTrue(ui.onDown(4, x, y))
        assertTrue(ui.onUp(4, x, y)) // a tap without dragging: a drop over the tray, so an illegal drop
        assertEquals("a tap places nothing", 0, ui.session.board.filledCount)
        assertNotNull(ui.session.tray[1])
    }

    @Test
    fun `dropping on the ghost places once and only once, and the slot stays empty`() {
        val ui = freshUi()
        val (sx, sy) = slotCentre(ui, 1)
        assertTrue(ui.onDown(1, sx, sy))
        val (fx, fy) = fingerFor(ui, shape("bar2h"), 2, 1)
        assertTrue(ui.onMove(1, fx, fy))
        assertTrue(ui.onUp(1, fx, fy))
        assertEquals(2, ui.session.board.filledCount)
        assertNull(ui.session.tray[1])
        assertFalse("a second lift of the same finger is ignored", ui.onUp(1, fx, fy))
        assertFalse("nothing to grab in an emptied slot", ui.onDown(2, sx, sy))
        assertFalse(ui.cancelDrag())
        assertEquals(2, ui.session.board.filledCount)
        assertNotNull(ui.session.tray[0]); assertNotNull(ui.session.tray[2])
    }

    @Test
    fun `a wrong drop glides home with nothing lost, and the block can be regrabbed at once, mid-glide`() {
        val ui = freshUi()
        val (sx, sy) = slotCentre(ui, 0)
        assertTrue(ui.onDown(1, sx, sy))
        assertTrue(ui.onUp(1, sx + 5f, sy)) // dropped over the tray
        assertEquals(0, ui.session.board.filledCount)
        assertEquals(dot, ui.session.tray[0])
        assertEquals("one glide home is playing", 1, ui.returns.size)
        assertTrue("regrab while it is gliding", ui.onDown(9, sx, sy))
        assertTrue("the glide is dropped, the block is in the hand", ui.returns.isEmpty() && ui.tracker.slot == 0)
        assertEquals(dot, ui.session.tray[0])
    }

    @Test
    fun `an interrupted drag (cancel, vanished finger, screen closing) sends the block home even over a legal spot`() {
        val ui = freshUi()
        val (sx, sy) = slotCentre(ui, 1)
        assertTrue(ui.onDown(1, sx, sy))
        val (fx, fy) = fingerFor(ui, shape("bar2h"), 2, 1)
        ui.onMove(1, fx, fy)
        assertTrue(ui.cancelDrag())
        assertEquals("nothing is placed by an interruption", 0, ui.session.board.filledCount)
        assertNotNull(ui.session.tray[1])
        assertEquals(1, ui.returns.size)
        assertFalse("a lift that arrives late is nothing", ui.onUp(1, fx, fy))
        assertFalse(ui.cancelDrag())
        // and the game is not stuck holding: a clear-out / growth is not blocked any more
        assertFalse(ui.tracker.active)
    }

    @Test
    fun `second finger is ignored while the first drags, on blocks and on the board, and cannot end or steal the drag`() {
        val ui = freshUi()
        val (ax, ay) = slotCentre(ui, 0)
        val (bx, by) = slotCentre(ui, 2)
        assertTrue(ui.onDown(1, ax, ay))
        assertFalse(ui.onDown(2, bx, by))
        assertFalse(ui.onMove(2, bx, by - 300f))
        assertFalse("second finger lifting does nothing", ui.onUp(2, bx, by - 300f))
        assertTrue(ui.tracker.active && ui.tracker.pointerId == 1L && ui.tracker.slot == 0)
        val (fx, fy) = fingerFor(ui, dot, 1, 3)
        ui.onMove(1, fx, fy)
        assertTrue(ui.onUp(1, fx, fy))
        assertEquals(Family.DOT, ui.session.board.familyAt(1, 3))
        assertNotNull("finger two's block was never touched", ui.session.tray[2])
        assertEquals(1, ui.session.board.filledCount)
    }

    @Test
    fun `lifting over the home corner, off the screen or at nonsense coordinates is an illegal drop`() {
        for ((x, y) in listOf(48f to 48f, -20f to 300f, 400f to 300f, 180f to -5f, 180f to 900f, Float.NaN to Float.NaN)) {
            val ui = freshUi()
            val (sx, sy) = slotCentre(ui, 0)
            assertTrue(ui.onDown(1, sx, sy))
            assertTrue(ui.onUp(1, x, y))
            assertEquals("($x,$y)", 0, ui.session.board.filledCount)
            assertEquals(dot, ui.session.tray[0])
        }
    }

    @Test
    fun `mashing - thousands of random touches, cancels and frames never lose, duplicate or double-place a block`() {
        val random = Random(555)
        val ui = freshUi(tray = listOf(dot, shape("bar2h"), shape("bar2v")), seed = 5, paws = PLENTY_OF_PAWS)
        val s = ui.session
        var frameMs = 1000L
        var lastTray = s.tray
        var lastDeals = s.trayDeals
        var placements = 0
        var step = 0
        // Audited after EVERY call into the touch layer, so a placement and a refill can never hide behind each other.
        fun audit() {
            val tray = s.tray
            if (s.trayDeals != lastDeals) {
                assertTrue("a new tray only ever arrives when the old one was empty (call $step)", lastTray.all { it == null })
                assertTrue("and arrives full", tray.all { it != null })
                assertEquals("exactly one deal at a time", lastDeals + 1, s.trayDeals)
            } else for (k in 0 until 3) {
                if (lastTray[k] != null && tray[k] == null) placements++
                assertFalse("a slot never gets a block without a refill (call $step, slot $k)", lastTray[k] == null && tray[k] != null)
                if (lastTray[k] != null && tray[k] != null) assertEquals("a block never changes in its slot", lastTray[k], tray[k])
            }
            assertTrue(s.board.size in 5..9)
            assertTrue(s.board.completedRows().isEmpty() && s.board.completedCols().isEmpty())
            lastTray = tray; lastDeals = s.trayDeals; step++
        }
        fun frame(ms: Long) { frameMs += ms; ui.onFrame(frameMs); audit() }
        val down = HashSet<Long>()
        repeat(6_000) {
            when (random.nextInt(4)) {
                0 -> { // a purposeful gesture: grab, carry to a legal spot (or not), maybe a second finger, then lift or cancel
                    ui.cancelDrag(); down.clear(); audit()
                    val slot = (0 until 3).filter { s.tray[it] != null }.let { if (it.isEmpty()) null else it[random.nextInt(it.size)] }
                    if (slot != null) {
                        val shape = s.tray[slot]!!
                        val (sx, sy) = slotCentre(ui, slot)
                        ui.onDown(7, sx, sy); audit()
                        val spots = s.board.spots(shape)
                        val (tx, ty) = if (spots.isNotEmpty() && random.nextInt(4) > 0) spots[random.nextInt(spots.size)].let { fingerFor(ui, shape, it.row, it.col) }
                        else (random.nextFloat() * 360f) to (random.nextFloat() * 692f)
                        ui.onMove(7, tx, ty); audit()
                        if (random.nextBoolean()) { val (ox, oy) = slotCentre(ui, random.nextInt(3)); ui.onDown(8, ox, oy); audit(); ui.onMove(8, tx, ty); ui.onUp(8, tx, ty); audit() }
                        if (random.nextInt(4) == 0) { ui.cancelDrag(); audit() } else { ui.onUp(7, tx, ty); audit(); ui.onUp(7, tx, ty); audit() }
                    }
                }
                1 -> { // noise: random fingers, half on slots, half anywhere on or off the screen
                    val id = random.nextLong(1, 4)
                    val (x, y) = if (random.nextBoolean()) slotCentre(ui, random.nextInt(3)).let { (a, b) -> a + random.nextFloat() * 30 - 15 to b + random.nextFloat() * 30 - 15 }
                    else random.nextFloat() * 420f - 30f to random.nextFloat() * 760f - 30f
                    when (random.nextInt(4)) {
                        0 -> if (down.add(id)) ui.onDown(id, x, y)
                        1 -> if (id in down) ui.onMove(id, x, y)
                        2 -> if (down.remove(id)) ui.onUp(id, x, y)
                        else -> if (random.nextInt(3) == 0) { ui.cancelDrag(); down.clear() }
                    }
                    audit()
                }
                2 -> frame(random.nextLong(1, 400))
                else -> if (!ui.tracker.active) frame(1500)
            }
        }
        assertTrue("the mash actually placed blocks: $placements", placements > 200)
        assertTrue("and went through refills: ${s.trayDeals}", s.trayDeals > 20)
        println("QA mash: placements=$placements deals=${s.trayDeals} clears=${s.clears} size=${s.board.size} stage=${s.stage}")
    }
}
