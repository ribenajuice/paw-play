package com.pawplay.app.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlin.math.ceil

/**
 * Lays out [itemCount] square items in whichever column count (within
 * [minColumns]..[maxColumns]) yields the largest item that still fits
 * everything on screen with no scrolling — never below [minItemSize].
 *
 * Shared by the home screen's game shelf (up to ~a dozen tiles, per
 * docs/PRD.md's long-term plan) and Paw Match's card grid, so a screen
 * that has to fit a growing number of same-size things always behaves
 * the same way. See docs/DECISIONS.md, 2026-09-23 — this is the reuse
 * that ADR called for.
 */
@Composable
fun AdaptiveSquareGrid(
    itemCount: Int,
    modifier: Modifier = Modifier,
    minColumns: Int = 2,
    maxColumns: Int = 4,
    gap: Dp = 16.dp,
    maxItemSize: Dp = 170.dp,
    minItemSize: Dp = 48.dp,
    itemContent: @Composable (index: Int, size: Dp) -> Unit,
) {
    if (itemCount == 0) return

    BoxWithConstraints(modifier = modifier, contentAlignment = Alignment.Center) {
        val columns = bestColumnCount(itemCount, maxWidth, maxHeight, minColumns, maxColumns, gap)
        val rows = ceil(itemCount / columns.toFloat()).toInt()
        val cellWidth = (maxWidth - gap * (columns - 1)) / columns
        val cellHeight = (maxHeight - gap * (rows - 1)) / rows
        val itemSize = minOf(cellWidth, cellHeight, maxItemSize).coerceAtLeast(minItemSize)

        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(gap),
        ) {
            (0 until itemCount).chunked(columns).forEach { rowIndices ->
                Row(horizontalArrangement = Arrangement.spacedBy(gap)) {
                    rowIndices.forEach { index -> itemContent(index, itemSize) }
                }
            }
        }
    }
}

private fun bestColumnCount(
    itemCount: Int,
    maxWidth: Dp,
    maxHeight: Dp,
    minColumns: Int,
    maxColumns: Int,
    gap: Dp,
): Int =
    (minColumns..maxColumns).maxByOrNull { columns ->
        val rows = ceil(itemCount / columns.toFloat()).toInt()
        val cellWidth = (maxWidth - gap * (columns - 1)) / columns
        val cellHeight = (maxHeight - gap * (rows - 1)) / rows
        minOf(cellWidth, cellHeight)
    } ?: minColumns
