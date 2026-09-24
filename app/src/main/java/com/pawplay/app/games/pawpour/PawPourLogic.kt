package com.pawplay.app.games.pawpour

import kotlin.random.Random

/**
 * Pure game-state logic for Paw Pour, kept free of Android/Compose so it's
 * covered by fast JVM unit tests (see PawPourLogicTest), the same split as
 * PawMatchLogic.kt. The Composable UI only ever calls into this — it never
 * reimplements the rules itself. The solvability search lives in
 * PawPourSolver.kt.
 *
 * Vocabulary (docs/PRD.md, Milestone 2): a *tube* holds a stack of coloured
 * *bands*, listed bottom first; a *pour* moves the top colour from one tube
 * to another.
 */

/** Ordered brightest to darkest (docs/DESIGN-SYSTEM.md), so rounds can take "the first N". */
enum class BandColor { SUNSHINE, BUBBLEGUM, SKY, CORAL, LEAF, GRAPE, MIDNIGHT }

data class Board(
    /** Each tube's bands, bottom first. */
    val tubes: List<List<BandColor>>,
    /** Bands a tube can hold; every colour has exactly this many bands. */
    val capacity: Int,
) {
    /** Won: every tube is empty or holds a single colour (docs/PRD.md story 15). */
    val isSolved: Boolean get() = tubes.all { it.isSingleColour() }
}

private fun List<BandColor>.isSingleColour(): Boolean = all { it == first() }

/** How many bands of the same colour sit together on top of tube [index]. */
fun Board.topRunLength(index: Int): Int {
    val tube = tubes[index]
    if (tube.isEmpty()) return 0
    return tube.takeLastWhile { it == tube.last() }.size
}

/** A finished tube: full, and one colour all the way up. */
fun Board.isTubeComplete(index: Int): Boolean {
    val tube = tubes[index]
    return tube.size == capacity && tube.isSingleColour()
}

/**
 * Legal pour (docs/PRD.md story 11): the target is empty or its top band is
 * the same colour, and it has room. A pour that changes nothing useful —
 * moving a tube that is already one colour into an empty tube — is not legal.
 */
fun Board.isLegalPour(from: Int, to: Int): Boolean {
    if (from == to || from !in tubes.indices || to !in tubes.indices) return false
    val source = tubes[from]
    val target = tubes[to]
    if (source.isEmpty() || target.size >= capacity) return false
    if (target.isEmpty()) return !source.isSingleColour()
    return target.last() == source.last()
}

/** Bands that a pour from [from] into [to] would move: the whole top run, limited by room. 0 if illegal. */
fun Board.pourCount(from: Int, to: Int): Int =
    if (isLegalPour(from, to)) minOf(topRunLength(from), capacity - tubes[to].size) else 0

/** The board after pouring, or this same board if the pour isn't legal. */
fun Board.poured(from: Int, to: Int): Board {
    val count = pourCount(from, to)
    if (count == 0) return this
    val moved = tubes[from].takeLast(count)
    return copy(
        tubes = tubes.mapIndexed { index, tube ->
            when (index) {
                from -> tube.dropLast(count)
                to -> tube + moved
                else -> tube
            }
        },
    )
}

fun Board.legalPours(): List<Pair<Int, Int>> =
    tubes.indices.flatMap { from -> tubes.indices.filter { to -> isLegalPour(from, to) }.map { to -> from to to } }

/** Empty and finished tubes can't be picked up; a finished tube can never pour anywhere useful. */
fun Board.canSelect(index: Int): Boolean =
    index in tubes.indices && tubes[index].isNotEmpty() && !isTubeComplete(index)

// ---- Taps -------------------------------------------------------------------------------

/** What a single tap does, given which tube (if any) is currently picked up. */
sealed interface TapOutcome {
    /** Nothing happens — e.g. tapping an empty tube first. Never punished. */
    data object Ignore : TapOutcome
    data class Select(val tube: Int) : TapOutcome
    data object Deselect : TapOutcome
    data class Pour(val from: Int, val to: Int, val count: Int) : TapOutcome

    /** Illegal pour: [tube] wobbles and the selected tube sets back down. No sound, no red, no count. */
    data class Reject(val tube: Int) : TapOutcome
}

/**
 * The tap rules (docs/PRD.md stories 10-12). Only one tube is ever selected. Any second tap
 * that isn't a legal pour is a gentle "no" (story 12) — it does not switch the selection.
 */
fun Board.tap(selected: Int?, tapped: Int): TapOutcome = when {
    tapped !in tubes.indices -> TapOutcome.Ignore
    selected == null -> if (canSelect(tapped)) TapOutcome.Select(tapped) else TapOutcome.Ignore
    selected == tapped -> TapOutcome.Deselect
    isLegalPour(selected, tapped) -> TapOutcome.Pour(selected, tapped, pourCount(selected, tapped))
    else -> TapOutcome.Reject(tapped)
}

// ---- Difficulty ramp and round generation ---------------------------------------------------

/** One row of the PRD's difficulty ramp table. */
data class RoundSpec(val tubeCount: Int, val colourCount: Int, val capacity: Int) {
    val emptyCount: Int get() = tubeCount - colourCount
}

/** docs/PRD.md, Milestone 2 ramp. Round 6 and up is the cap. */
fun specForRound(round: Int): RoundSpec = when {
    round <= 1 -> RoundSpec(tubeCount = 6, colourCount = 3, capacity = 3)
    round == 2 -> RoundSpec(tubeCount = 6, colourCount = 4, capacity = 3)
    round == 3 -> RoundSpec(tubeCount = 7, colourCount = 5, capacity = 3)
    round == 4 -> RoundSpec(tubeCount = 8, colourCount = 6, capacity = 3)
    round == 5 -> RoundSpec(tubeCount = 8, colourCount = 6, capacity = 4)
    else -> RoundSpec(tubeCount = 9, colourCount = 7, capacity = 4)
}

/** Round 1 uses three fixed high-contrast colours; later rounds take the first N in order. */
fun coloursForRound(round: Int): List<BandColor> {
    val spec = specForRound(round)
    return if (round <= 1) {
        listOf(BandColor.SUNSHINE, BandColor.SKY, BandColor.CORAL)
    } else {
        BandColor.entries.take(spec.colourCount)
    }
}

private const val MAX_GENERATION_ATTEMPTS = 200
private const val MIN_MIX_RATIO = 0.6f

/**
 * A start position a child would call "mixed up": no tube holds just one colour (so none is
 * already complete and the board can't start solved), and neighbouring bands mostly differ.
 */
fun Board.isWellMixed(): Boolean {
    val filled = tubes.filter { it.isNotEmpty() }
    if (filled.any { it.isSingleColour() }) return false
    val neighbours = filled.sumOf { it.size - 1 }
    if (neighbours == 0) return false
    val different = filled.sumOf { tube -> tube.zipWithNext().count { (a, b) -> a != b } }
    return different.toFloat() / neighbours >= MIN_MIX_RATIO
}

/**
 * A new board for [round]: shuffled, well mixed, and proven solvable by the solver before it is
 * ever shown (docs/PRD.md story 14). Deals that fail are simply re-dealt; the deterministic
 * fallback exists only so this can never loop forever.
 */
fun generateBoard(round: Int, random: Random = Random.Default): Board {
    val spec = specForRound(round)
    val colours = coloursForRound(round)
    repeat(MAX_GENERATION_ATTEMPTS) {
        val bands = colours.flatMap { colour -> List(spec.capacity) { colour } }.shuffled(random)
        val tubes = (bands.chunked(spec.capacity) + List(spec.emptyCount) { emptyList<BandColor>() })
            .shuffled(random)
        val board = Board(tubes, spec.capacity)
        if (board.isWellMixed() && board.solvability() == Solvability.SOLVABLE) return board
    }
    return fallbackBoard(spec, colours)
}

/** Every tube gets a different rotating slice of the colours; never solved, never a complete tube. */
internal fun fallbackBoard(spec: RoundSpec, colours: List<BandColor>): Board {
    val filled = List(spec.colourCount) { tube ->
        List(spec.capacity) { band -> colours[(tube + band) % colours.size] }
    }
    return Board(filled + List(spec.emptyCount) { emptyList() }, spec.capacity)
}

// ---- Round state, with history for the gentle auto-undo ----------------------------------------

data class Move(val from: Int, val to: Int, val count: Int)

/** One pour that happened: the board [before] it, and the move itself. */
data class Step(val before: Board, val move: Move)

/**
 * A round in progress. [history] is every pour so far (oldest first), and [safeDepth] is how many
 * of them lead to the latest position known to be finishable (0 = the start, which generation
 * guarantees is). Nothing here is persisted: a round lives only as long as the screen.
 */
data class RoundState(
    val round: Int,
    val board: Board,
    val history: List<Step> = emptyList(),
    val safeDepth: Int = 0,
) {
    val isWon: Boolean get() = board.isSolved

    /**
     * True when the latest pour(s) left a position that can no longer be finished — including
     * when pours remain but the round can't be won any more (docs/PRD.md, "Stuck"). Only
     * meaningful after [checkedFinishable].
     */
    val needsRewind: Boolean get() = !isWon && safeDepth < history.size
}

fun newRoundState(round: Int, random: Random = Random.Default): RoundState =
    RoundState(round = round, board = generateBoard(round, random))

fun RoundState.nextRound(random: Random = Random.Default): RoundState = newRoundState(round + 1, random)

/** Applies a pour and remembers it. Returns this same state if the pour isn't legal. */
fun RoundState.poured(from: Int, to: Int): RoundState {
    val count = board.pourCount(from, to)
    if (count == 0) return this
    return copy(
        board = board.poured(from, to),
        history = history + Step(before = board, move = Move(from, to, count)),
    )
}

/**
 * Runs the solver on the current position. If it can still be finished (or the search gave up,
 * which counts as "don't rewind" so a slow check never costs the child progress) this position
 * becomes the new latest safe one; otherwise [needsRewind] turns true. Call it after every pour,
 * before allowing the next.
 */
fun RoundState.checkedFinishable(): RoundState =
    if (board.isSolved || board.solvability() != Solvability.UNSOLVABLE) copy(safeDepth = history.size) else this

/** Takes back the most recent pour (one step of the auto-undo animation). */
fun RoundState.undoLast(): RoundState {
    if (history.isEmpty()) return this
    val step = history.last()
    val newHistory = history.dropLast(1)
    return copy(board = step.before, history = newHistory, safeDepth = minOf(safeDepth, newHistory.size))
}

/** The pours to take back, most recent first, to land on the latest position that can still be finished. */
fun RoundState.rewindSteps(): List<Step> = history.drop(safeDepth).asReversed()

/** The state after the whole auto-undo. */
fun RoundState.rewound(): RoundState = rewindSteps().fold(this) { state, _ -> state.undoLast() }
