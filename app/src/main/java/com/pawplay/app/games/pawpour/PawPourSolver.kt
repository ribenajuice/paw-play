package com.pawplay.app.games.pawpour

/**
 * Can this Paw Pour board still be finished? Used two ways: to prove every generated round is
 * winnable before the child sees it, and to notice after a pour that the round has become
 * unwinnable so the game can gently un-pour (docs/PRD.md stories 14 and 17).
 *
 * It is a depth-first search over positions, using the same pour rules as PawPourLogic.kt and
 * treating two positions as the same when they differ only by tube order. The boards are small
 * (at most 9 tubes of 4), but proving a dead position dead means exhausting its reachable
 * positions, so the search has a node budget; if it runs out the answer is [Solvability.UNKNOWN]
 * and callers choose the safe reading (see docs/DECISIONS.md, 2026-09-24, Paw Pour solver).
 */
enum class Solvability { SOLVABLE, UNSOLVABLE, UNKNOWN }

const val DEFAULT_NODE_BUDGET = 300_000
private const val MAX_DEPTH = 1_500
private const val BITS_PER_BAND = 3

fun Board.solvability(nodeBudget: Int = DEFAULT_NODE_BUDGET): Solvability {
    if (isSolved) return Solvability.SOLVABLE
    val bitsPerTube = BITS_PER_BAND * capacity
    val tubesPerWord = 64 / bitsPerTube
    if (capacity > 10 || tubes.size > 2 * tubesPerWord) {
        return Solvability.UNKNOWN // outside the packed-key range this solver is built for
    }
    val packed = IntArray(tubes.size) { index ->
        tubes[index].foldIndexed(0) { position, acc, colour -> acc or ((colour.ordinal + 1) shl (BITS_PER_BAND * position)) }
    }
    return Search(capacity, nodeBudget).run(packed)
}

/** True unless the search proved the board can no longer be finished. */
fun Board.canBeFinished(nodeBudget: Int = DEFAULT_NODE_BUDGET): Boolean =
    solvability(nodeBudget) != Solvability.UNSOLVABLE

/**
 * A tube is packed into an Int, three bits per band (colour ordinal + 1), bottom band in the
 * lowest bits; 0 is an empty tube.
 */
private class Search(private val capacity: Int, private val budget: Int) {
    private class Key(val a: Long, val b: Long) {
        override fun equals(other: Any?) = other is Key && other.a == a && other.b == b
        override fun hashCode() = (a * 31 + b).hashCode()
    }

    private val bitsPerTube = BITS_PER_BAND * capacity
    private val tubesPerWord = 64 / bitsPerTube
    private val seen = HashSet<Key>()
    private var nodes = 0
    private var gaveUp = false

    fun run(start: IntArray): Solvability = when {
        dfs(start, 0) -> Solvability.SOLVABLE
        gaveUp -> Solvability.UNKNOWN
        else -> Solvability.UNSOLVABLE
    }

    private fun length(tube: Int): Int = (32 - Integer.numberOfLeadingZeros(tube) + BITS_PER_BAND - 1) / BITS_PER_BAND
    private fun band(tube: Int, position: Int): Int = (tube shr (BITS_PER_BAND * position)) and 7

    private fun runLength(tube: Int): Int {
        val len = length(tube)
        val top = band(tube, len - 1)
        var run = 1
        while (run < len && band(tube, len - 1 - run) == top) run++
        return run
    }

    private fun isSolved(tubes: IntArray): Boolean = tubes.all { it == 0 || runLength(it) == length(it) }

    private fun keyOf(tubes: IntArray): Key {
        val sorted = tubes.copyOf().also { it.sort() }
        var a = 0L
        var b = 0L
        for (i in sorted.indices) {
            if (i < tubesPerWord) a = (a shl bitsPerTube) or sorted[i].toLong()
            else b = (b shl bitsPerTube) or sorted[i].toLong()
        }
        return Key(a, b)
    }

    private class Candidate(val from: Int, val to: Int, val count: Int, val score: Int)

    private fun dfs(tubes: IntArray, depth: Int): Boolean {
        if (isSolved(tubes)) return true
        if (depth > MAX_DEPTH) { gaveUp = true; return false }
        if (!seen.add(keyOf(tubes))) return false
        if (++nodes > budget) { gaveUp = true; return false }

        val candidates = ArrayList<Candidate>()
        var emptySeen = false
        for (from in tubes.indices) {
            val source = tubes[from]
            if (source == 0) continue
            val run = runLength(source)
            val sourceSingleColour = run == length(source)
            val topColour = band(source, length(source) - 1)
            emptySeen = false
            for (to in tubes.indices) {
                if (to == from) continue
                val target = tubes[to]
                if (target == 0) {
                    // Moving a one-colour tube into an empty tube is not a legal pour; and all
                    // empty tubes are interchangeable, so try only the first of them.
                    if (sourceSingleColour || emptySeen) continue
                    emptySeen = true
                    candidates += Candidate(from, to, run, score = run)
                } else {
                    val targetLength = length(target)
                    if (targetLength >= capacity || band(target, targetLength - 1) != topColour) continue
                    val count = minOf(run, capacity - targetLength)
                    // Merging onto a matching colour is usually the productive move; try it first.
                    candidates += Candidate(from, to, count, score = 10 + count)
                }
            }
        }
        candidates.sortByDescending { it.score }

        for (move in candidates) {
            val next = tubes.copyOf()
            val source = tubes[move.from]
            val sourceLength = length(source)
            val keep = sourceLength - move.count
            next[move.from] = source and ((1 shl (BITS_PER_BAND * keep)) - 1)
            val moved = source ushr (BITS_PER_BAND * keep)
            next[move.to] = tubes[move.to] or (moved shl (BITS_PER_BAND * length(tubes[move.to])))
            if (dfs(next, depth + 1)) return true
            if (gaveUp) return false
        }
        return false
    }
}
