package com.pawplay.app.games.pawblocks

/** Where a block's top-left cell sits on the board. */
data class Spot(val row: Int, val col: Int)

/** A cell that was removed from the board, with the family it held (the animation needs it). */
data class ClearedCell(val row: Int, val col: Int, val family: Family)

/** What one placement did: the new board, the lines it completed (already removed), and the cells removed. */
class Placement(val board: Board, val rows: List<Int>, val cols: List<Int>, val removed: List<ClearedCell>) {
    /** A placement that completes several lines at once is still ONE clear as far as the ramp is concerned. */
    val cleared: Boolean get() = rows.isNotEmpty() || cols.isNotEmpty()
}

/**
 * The square grid of Paw Blocks: [size] x [size] cells, each empty or holding the [Family] of the block that
 * filled it. Immutable: every change returns a new board. Pure Kotlin (docs/ARCHITECTURE.md).
 *
 * Rules kept here: legality (a block sits fully on the board on empty cells), line completion (rows AND
 * columns), and that only completed lines' cells go, and nothing ever falls or shifts.
 */
class Board private constructor(val size: Int, private val cells: IntArray) {

    fun familyAt(row: Int, col: Int): Family? =
        if (row in 0 until size && col in 0 until size) cells[row * size + col].let { if (it < 0) null else FAMILIES[it] } else null

    fun isEmpty(row: Int, col: Int): Boolean = cells[row * size + col] < 0

    val filledCount: Int get() = cells.count { it >= 0 }
    val emptyCount: Int get() = size * size - filledCount

    fun rowFill(row: Int): Int = (0 until size).count { cells[row * size + it] >= 0 }
    fun colFill(col: Int): Int = (0 until size).count { cells[it * size + col] >= 0 }

    fun canPlace(shape: BlockShape, row: Int, col: Int): Boolean {
        if (row < 0 || col < 0 || row + shape.height > size || col + shape.width > size) return false
        for (c in shape.cells) if (cells[(row + c.row) * size + col + c.col] >= 0) return false
        return true
    }

    fun canPlace(shape: BlockShape, spot: Spot): Boolean = canPlace(shape, spot.row, spot.col)

    /** Puts [shape] down without clearing anything. The caller must have checked [canPlace]. */
    fun place(shape: BlockShape, row: Int, col: Int): Board {
        require(canPlace(shape, row, col)) { "${shape.id} does not fit at $row,$col" }
        val next = cells.copyOf()
        for (c in shape.cells) next[(row + c.row) * size + col + c.col] = shape.family.ordinal
        return Board(size, next)
    }

    fun completedRows(): List<Int> = (0 until size).filter { r -> (0 until size).all { cells[r * size + it] >= 0 } }
    fun completedCols(): List<Int> = (0 until size).filter { c -> (0 until size).all { cells[it * size + c] >= 0 } }

    /**
     * Places [shape] and removes every completed row and column in one go (rows and columns together, several
     * lines at once). Only those cells are removed; nothing above or beside them moves.
     */
    fun placeAndClear(shape: BlockShape, row: Int, col: Int): Placement {
        val placed = place(shape, row, col)
        val rows = placed.completedRows()
        val cols = placed.completedCols()
        if (rows.isEmpty() && cols.isEmpty()) return Placement(placed, rows, cols, emptyList())
        val next = placed.cells.copyOf()
        val removed = ArrayList<ClearedCell>()
        for (r in 0 until size) for (c in 0 until size) {
            if ((r in rows || c in cols) && next[r * size + c] >= 0) {
                removed += ClearedCell(r, c, FAMILIES[next[r * size + c]])
                next[r * size + c] = -1
            }
        }
        return Placement(Board(size, next), rows, cols, removed)
    }

    /** Every cell of [rows] removed (used by the gentle clear-out); returns the board and what went. */
    fun withoutRows(rows: Collection<Int>): Pair<Board, List<ClearedCell>> {
        val next = cells.copyOf()
        val removed = ArrayList<ClearedCell>()
        for (r in rows) for (c in 0 until size) {
            if (next[r * size + c] >= 0) {
                removed += ClearedCell(r, c, FAMILIES[next[r * size + c]])
                next[r * size + c] = -1
            }
        }
        return Board(size, next) to removed
    }

    /** One empty row at the bottom and one empty column at the right; every placed cell keeps its row and column. */
    fun grown(): Board {
        val n = size + 1
        val next = IntArray(n * n) { -1 }
        for (r in 0 until size) for (c in 0 until size) next[r * n + c] = cells[r * size + c]
        return Board(n, next)
    }

    fun hasSpot(shape: BlockShape): Boolean {
        for (r in 0..size - shape.height) for (c in 0..size - shape.width) if (canPlace(shape, r, c)) return true
        return false
    }

    fun spots(shape: BlockShape): List<Spot> {
        val out = ArrayList<Spot>()
        for (r in 0..size - shape.height) for (c in 0..size - shape.width) if (canPlace(shape, r, c)) out += Spot(r, c)
        return out
    }

    /** True when placing [shape] somewhere would complete at least one row or column. */
    fun canFinishALine(shape: BlockShape): Boolean {
        for (r in 0..size - shape.height) for (c in 0..size - shape.width) {
            if (canPlace(shape, r, c) && place(shape, r, c).let { it.completedRows().isNotEmpty() || it.completedCols().isNotEmpty() }) return true
        }
        return false
    }

    override fun equals(other: Any?) = other is Board && other.size == size && other.cells.contentEquals(cells)
    override fun hashCode() = 31 * size + cells.contentHashCode()

    /** One line per row: '.' empty, else the family's index (0-7). Handy in test failures. */
    override fun toString(): String = (0 until size).joinToString("\n") { r ->
        (0 until size).joinToString("") { c -> cells[r * size + c].let { if (it < 0) "." else it.toString() } }
    }

    companion object {
        private val FAMILIES = Family.values()

        fun empty(size: Int): Board = Board(size, IntArray(size * size) { -1 })

        /** A board whose cell (row, col) holds `fill(row, col)`; for tests and previews. */
        fun from(size: Int, fill: (row: Int, col: Int) -> Family?): Board =
            Board(size, IntArray(size * size) { fill(it / size, it % size)?.ordinal ?: -1 })

        /** Rows of text: '.' is empty, any other character a filled cell (dot family), for tests. */
        fun parse(vararg rows: String): Board {
            val n = rows.size
            require(rows.all { it.length == n }) { "rows must be square" }
            return from(n) { r, c -> if (rows[r][c] == '.') null else Family.DOT }
        }
    }
}
