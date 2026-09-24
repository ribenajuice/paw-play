package com.pawplay.app.games.pawpour

import kotlin.math.ceil

/**
 * Where every tube goes, so the whole board fits on screen with no scrolling and every tube is
 * a comfortable tap target (docs/DESIGN-SYSTEM.md, "Paw Pour: tubes, bands and marks" -> Sizing).
 * Pure numbers in dp, no Compose, so it is unit-tested (PawPourLayoutTest). It does for tubes what
 * AdaptiveSquareGrid does for squares; tubes aren't square and rows are centred when the last is
 * short (rows of 5 and 4), which is why it isn't reused directly.
 */

const val MIN_TUBE_WIDTH = 48f   // touch-target floor
const val MAX_TUBE_WIDTH = 88f
const val MAX_BAND_HEIGHT = 64f
const val MIN_GAP_X = 12f
const val MAX_GAP_X = 24f
const val ROW_GAP = 32f
const val LIFT_HEADROOM = 16f    // room above the top row for a selected tube's lift
const val TUBE_CHROME_HEIGHT = 20f // lip (8) + padding above the top band and below the bottom band (12)
private const val MAX_BAND_TO_WIDTH = 0.85f

data class TubePosition(val x: Float, val y: Float)

data class BoardLayout(
    val tubeWidth: Float,
    val bandHeight: Float,
    val tubeHeight: Float,
    val gapX: Float,
    val positions: List<TubePosition>,
    val contentHeight: Float,
)

fun tubeHeight(capacity: Int, bandHeight: Float): Float = capacity * bandHeight + TUBE_CHROME_HEIGHT

/** Tubes in each row: as even as possible, longer rows first (9 tubes on 2 rows -> 5 and 4). */
fun rowSizes(tubeCount: Int, rows: Int): List<Int> =
    List(rows) { row -> tubeCount / rows + if (row < tubeCount % rows) 1 else 0 }

/**
 * Picks the row count giving the biggest bands (which is what makes marks readable), then the
 * widest tubes. [availableWidth] should already exclude the 16dp side margins.
 */
fun layoutBoard(tubeCount: Int, capacity: Int, availableWidth: Float, availableHeight: Float): BoardLayout {
    data class Candidate(val rows: Int, val width: Float, val band: Float, val feasible: Boolean)

    val candidates = (1..tubeCount).map { rows ->
        val columns = ceil(tubeCount / rows.toFloat()).toInt()
        val width = minOf(MAX_TUBE_WIDTH, (availableWidth - MIN_GAP_X * (columns - 1)) / columns)
        val rowHeight = (availableHeight - LIFT_HEADROOM - ROW_GAP * (rows - 1)) / rows
        val band = minOf(MAX_BAND_HEIGHT, width * MAX_BAND_TO_WIDTH, (rowHeight - TUBE_CHROME_HEIGHT) / capacity)
        Candidate(rows, width, band, feasible = width >= MIN_TUBE_WIDTH && band > 0f)
    }
    val best = candidates.filter { it.feasible }.maxWithOrNull(compareBy({ it.band }, { it.width }))
        ?: candidates.maxByOrNull { it.width }!! // nothing fits: keep the touch-target floor, accept overflow

    val width = best.width.coerceAtLeast(MIN_TUBE_WIDTH)
    val band = best.band.coerceAtLeast(1f)
    val height = tubeHeight(capacity, band)
    val sizes = rowSizes(tubeCount, best.rows)
    val widest = sizes.max()
    val gapX = if (widest > 1) minOf(MAX_GAP_X, (availableWidth - widest * width) / (widest - 1)).coerceAtLeast(MIN_GAP_X) else 0f

    val positions = sizes.flatMapIndexed { row, count ->
        val rowWidth = count * width + (count - 1) * gapX
        val left = (availableWidth - rowWidth) / 2f
        List(count) { column -> TubePosition(x = left + column * (width + gapX), y = LIFT_HEADROOM + row * (height + ROW_GAP)) }
    }
    return BoardLayout(
        tubeWidth = width,
        bandHeight = band,
        tubeHeight = height,
        gapX = gapX,
        positions = positions,
        contentHeight = LIFT_HEADROOM + best.rows * height + (best.rows - 1) * ROW_GAP,
    )
}
