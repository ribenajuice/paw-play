package com.pawplay.app.games.pawblocks

/**
 * The score of Paw Blocks (docs/PRD.md story 63), as plain numbers so it is unit-tested with no screen.
 *
 * Every block placed is worth [PLACE_POINTS]. A placement that completes lines adds a bonus for the number of
 * lines it completed (rows and columns each count as one line; a double line is still ONE clear for the ramp, but
 * scores as two lines). The gentle clear-out, refills, growth, wrong drops and cancelled drags add and take
 * nothing. There is no combo, streak or multiplier.
 */
object PawBlocksScoring {
    const val PLACE_POINTS = 3

    /** The score never goes past six digits, so it always fits its place on screen and in the saved best. */
    const val MAX_SCORE = 999_999

    /** The bonus for [lines] completed by one placement: 0, 10, 30, 60, and 100 for four or more. */
    fun lineBonus(lines: Int): Int = when {
        lines <= 0 -> 0
        lines == 1 -> 10
        lines == 2 -> 30
        lines == 3 -> 60
        else -> 100
    }

    /** All the points one legal placement is worth: the block, plus the bonus for the lines it completed. */
    fun placementPoints(lines: Int): Int = PLACE_POINTS + lineBonus(lines)

    /** [score] plus [points], capped at [MAX_SCORE]. */
    fun add(score: Int, points: Int): Int = (score.toLong() + points).coerceIn(0L, MAX_SCORE.toLong()).toInt()
}
