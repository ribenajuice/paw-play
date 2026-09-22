package com.pawplay.app.home

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.pawplay.app.games.MiniGame

/**
 * The one screen every session starts on: a picture-only shelf of game
 * tiles, one per docs/ARCHITECTURE.md's GameCatalog entry. No "coming
 * soon" tiles ever ship — see docs/PRD.md, out of scope. The grid simply
 * grows as [games] grows.
 */
@Composable
fun HomeScreen(games: List<MiniGame>, onGameSelected: (MiniGame) -> Unit) {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        if (games.size == 1) {
            GameTile(game = games[0], onClick = { onGameSelected(games[0]) })
        } else {
            LazyVerticalGrid(
                columns = GridCells.Fixed(2),
                modifier = Modifier.wrapContentSize(),
                contentPadding = PaddingValues(24.dp),
                horizontalArrangement = Arrangement.spacedBy(20.dp),
                verticalArrangement = Arrangement.spacedBy(20.dp),
            ) {
                items(games) { game ->
                    GameTile(game = game, onClick = { onGameSelected(game) })
                }
            }
        }
    }
}

@Composable
private fun GameTile(game: MiniGame, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .size(220.dp)
            .clip(RoundedCornerShape(44.dp))
            .background(MaterialTheme.colorScheme.surface)
            .clickable(onClick = onClick)
            .padding(24.dp)
            .semantics { contentDescription = "Play ${game.id}" },
        contentAlignment = Alignment.Center,
    ) {
        game.icon()
    }
}
