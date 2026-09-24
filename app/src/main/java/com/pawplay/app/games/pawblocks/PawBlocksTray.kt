package com.pawplay.app.games.pawblocks

import kotlin.random.Random

/**
 * Choosing the three blocks on offer (docs/PRD.md story 44) and the gentle clear-out (story 45).
 * Pure Kotlin. See docs/DECISIONS.md, 2026-09-25, for why the dealer works by ranking random trios.
 */
object TrayDealer {
    /** How many random trios are looked at before settling for the best one seen. */
    const val ATTEMPTS = 48

    /** Placements the "all three, one after another" search may try before it stops and says "probably". */
    const val SEARCH_BUDGET = 20_000

    /**
     * Picks three blocks for [board] at [stage]. Always random within the stage's set.
     *
     * Guarantees, in this order of importance:
     *  1. **At least one of the three has a legal spot** (hard; whenever the board has any empty cell, the dot fits).
     *  2. The trio is not the same as [previous] (same blocks in any order), whenever another trio works.
     *  3. Best effort: all three can be placed one after another in some order, lines cleared on the way.
     *  4. Best effort, boards 7x7 and up only: if some block could finish a row or column, one is on offer.
     */
    fun deal(board: Board, stage: Int, previous: List<String>?, random: Random): List<BlockShape> {
        val set = BlocksRamp.shapesFor(stage)
        val placeable = set.filter { board.hasSpot(it) }
        val finishers = if (board.size >= BlocksRamp.NEAR_COMPLETE_MIN_BOARD) placeable.filter { board.canFinishALine(it) } else emptyList()
        val previousKey = previous?.sorted()

        var best: List<BlockShape>? = null
        var bestRank = -1
        for (attempt in 0 until ATTEMPTS) {
            val trio = MutableList(BlocksRamp.TRAY_SIZE) { set[random.nextInt(set.size)] }
            // Every other attempt puts a line-finishing block in, so both kinds of trio get looked at.
            if (finishers.isNotEmpty() && attempt % 2 == 0) trio[0] = finishers[random.nextInt(finishers.size)]
            if (placeable.isNotEmpty() && trio.none { it in placeable }) continue // rule 1 is never traded away
            val distinct = previousKey == null || trio.map { it.id }.sorted() != previousKey
            val allFit = canPlaceAll(board, trio)
            val hasFinisher = finishers.isEmpty() || trio.any { it in finishers }
            val rank = (if (distinct) 4 else 0) + (if (allFit) 2 else 0) + (if (hasFinisher) 1 else 0)
            if (rank > bestRank) { best = trio; bestRank = rank }
            if (rank == 7) break
        }
        // A tight board where no random trio had a placeable block: put one placeable shape in on purpose.
        val chosen = best ?: MutableList(BlocksRamp.TRAY_SIZE) { set[random.nextInt(set.size)] }.also { trio ->
            if (placeable.isNotEmpty()) trio[random.nextInt(trio.size)] = placeable[random.nextInt(placeable.size)]
        }
        return chosen.shuffled(random)
    }

    /**
     * Can every block in [trio] be placed one after another, in some order, on [board]? Lines that a placement
     * completes are cleared before the next block, as they are in the game. Stops looking after
     * [SEARCH_BUDGET] placements and answers yes: a board with that many ways to place things has room.
     */
    fun canPlaceAll(board: Board, trio: List<BlockShape>): Boolean = search(board, trio, intArrayOf(SEARCH_BUDGET))

    private fun search(board: Board, remaining: List<BlockShape>, budget: IntArray): Boolean {
        if (remaining.isEmpty()) return true
        if (remaining.size == 1) return board.hasSpot(remaining[0])
        for (i in remaining.indices) {
            val shape = remaining[i]
            if (remaining.subList(0, i).any { it.id == shape.id }) continue // same block twice: one order is enough
            val rest = remaining.filterIndexed { j, _ -> j != i }
            for (spot in board.spots(shape)) {
                if (budget[0]-- <= 0) return true
                if (search(board.placeAndClear(shape, spot.row, spot.col).board, rest, budget)) return true
            }
        }
        return false
    }
}

/** The result of a gentle clear-out: the board afterwards, the rows that were emptied, and every cell that went. */
class ClearOut(val board: Board, val rows: List<Int>, val removed: List<ClearedCell>)

object GentleClearOut {
    /** True when the tray still holds blocks and none of them has a legal spot. */
    fun isStuck(board: Board, remaining: List<BlockShape>): Boolean =
        remaining.isNotEmpty() && remaining.none { board.hasSpot(it) }

    /**
     * Clears the fullest rows (ties: the top one first), a third of the board's height at a time (2 on 5x5 and
     * 6x6, 3 on 7x7 to 9x9), and repeats only until one of [remaining] fits. Rows are removed whole; nothing
     * else moves. Ends at the latest when every row is gone, and an empty board fits every block.
     * Returns null when nothing needs clearing.
     */
    fun clear(board: Board, remaining: List<BlockShape>): ClearOut? {
        if (!isStuck(board, remaining)) return null
        val order = (0 until board.size).sortedWith(compareByDescending<Int> { board.rowFill(it) }.thenBy { it })
        val perRound = BlocksRamp.clearOutRowCount(board.size)
        var current = board
        val rows = ArrayList<Int>()
        val removed = ArrayList<ClearedCell>()
        var taken = 0
        while (taken < order.size && remaining.none { current.hasSpot(it) }) {
            val chunk = order.subList(taken, minOf(order.size, taken + perRound)).filter { current.rowFill(it) > 0 }
            taken += perRound
            if (chunk.isEmpty()) continue
            val (next, gone) = current.withoutRows(chunk)
            current = next
            rows += chunk
            removed += gone
        }
        return ClearOut(current, rows, removed)
    }
}
