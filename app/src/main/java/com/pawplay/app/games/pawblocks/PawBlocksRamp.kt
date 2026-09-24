package com.pawplay.app.games.pawblocks

/**
 * The ramp, exactly per docs/PRD.md Milestone 5. A "clear" is one placement that completes at least one line
 * (a double line counts once); the gentle clear-out never counts. Stages are invisible to the child: no number,
 * banner or lock.
 *
 * | Stage | Starts after | Board | Blocks on offer                        |
 * | 1     | 0            | 5x5   | dot, bar of 2                          |
 * | 2     | 3 clears     | 5x5   | + bar of 3                             |
 * | 3     | 8 clears     | 6x6   | + 2x2 square, corner of 3 (4 turns)    |
 * | 4     | 15 clears    | 7x7   | + bar of 4                             |
 * | 5     | 24 clears    | 8x8   | + 2x3 rectangle (both ways)            |
 * | 6     | 35 clears    | 9x9   | + bar of 5 (the cap)                   |
 */
object BlocksRamp {
    const val MAX_STAGE = 6
    const val TRAY_SIZE = 3

    /** Cumulative clears at which stages 2..6 begin. */
    val stageStartsAfter: List<Int> = listOf(3, 8, 15, 24, 35)

    private val boardSizes = listOf(5, 5, 6, 7, 8, 9)

    fun stageFor(clears: Int): Int = 1 + stageStartsAfter.count { clears >= it }

    fun boardSizeFor(stage: Int): Int = boardSizes[stage.coerceIn(1, MAX_STAGE) - 1]

    fun shapesFor(stage: Int): List<BlockShape> = BlockShapes.forStage(stage.coerceIn(1, MAX_STAGE))

    /** The gentle clear-out takes a third of the board's height, rounded up: 2 rows on 5x5 and 6x6, 3 on 7x7 to 9x9. */
    fun clearOutRowCount(boardSize: Int): Int = (boardSize + 2) / 3

    /** From this size up a line is long, so a block that nearly finishes one is offered (story 44). */
    const val NEAR_COMPLETE_MIN_BOARD = 7

    /** Which of the six critters peeks up for the [clears]th self-made clear: a fixed rotation. */
    fun critterIndex(clears: Int): Int = ((clears - 1) % CRITTER_COUNT + CRITTER_COUNT) % CRITTER_COUNT

    const val CRITTER_COUNT = 6
}

/** Every duration in one place, in milliseconds (docs/DESIGN-SYSTEM.md, "Drag, ghost and snap" and "Clear, clear-out and growth motion"). */
object BlocksTiming {
    const val LIFT_MS = 120L          // a block grows from tray size to board cell size
    const val DROP_MS = 90L           // a legal drop glides into its cells
    const val PLOP_MS = 160L          // then the cells swell once
    const val RETURN_MS = 250L        // an illegal drop glides home
    const val SWEEP_MS = 600L         // the shimmer along a cleared line
    const val LINE_CLEAR_MS = 1000L   // the whole line-clear moment, animal included
    const val GROWTH_MS = 520L
    const val CLEAR_OUT_MS = 900L
    const val REFILL_DELAY_MS = 500L  // after the last block is placed and every animation has ended
    const val REFILL_SLIDE_MS = 320L
    const val CLEAR_OUT_DELAY_MS = 1000L // after his last move
    const val CRITTER_MS = 1000L
}
