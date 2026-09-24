package com.pawplay.app.home

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.pawplay.app.games.MiniGame
import com.pawplay.app.ui.AdaptiveSquareGrid

/**
 * The one screen every session starts on: a picture-only shelf of game
 * tiles, one per docs/ARCHITECTURE.md's GameCatalog entry. No "coming
 * soon" tiles ever ship — see docs/PRD.md, out of scope. The grid simply
 * grows (and reflows into more columns) as [games] grows — see
 * docs/PRD.md's long-term plan for roughly a dozen games on this shelf.
 */
@Composable
fun HomeScreen(games: List<MiniGame>, onGameSelected: (MiniGame) -> Unit) {
    AdaptiveSquareGrid(
        itemCount = games.size,
        modifier = Modifier.fillMaxSize().padding(24.dp),
        maxItemSize = 220.dp,
        gap = 20.dp,
    ) { index, size ->
        val game = games[index]
        GameTile(game = game, size = size, onClick = { onGameSelected(game) })
    }
}

@Composable
private fun GameTile(game: MiniGame, size: Dp, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .size(size)
            .clip(RoundedCornerShape(size * 0.2f))
            .background(MaterialTheme.colorScheme.surface)
            .clickable(onClick = onClick)
            .semantics { contentDescription = "Play ${game.id}" },
        contentAlignment = Alignment.Center,
    ) {
        game.icon(size)
    }
}
