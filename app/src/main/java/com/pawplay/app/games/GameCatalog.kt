package com.pawplay.app.games

import com.pawplay.app.games.pawblocks.PawBlocksGame
import com.pawplay.app.games.pawkitchen.PawKitchenGame
import com.pawplay.app.games.pawmatch.PawMatchGame
import com.pawplay.app.games.pawpop.PawPopGame
import com.pawplay.app.games.pawpour.PawPourGame
import com.pawplay.app.games.pawtrace.PawTraceGame

/**
 * Every game on the Paw Play shelf, in the order tiles appear on the home
 * screen. See docs/ARCHITECTURE.md — adding a game is adding one line here.
 */
object GameCatalog {
    val games: List<MiniGame> = listOf(
        PawMatchGame,
        PawPourGame,
        PawKitchenGame,
        PawTraceGame,
        PawBlocksGame,
        PawPopGame,
    )
}
