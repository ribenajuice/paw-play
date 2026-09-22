package com.pawplay.app.games

import androidx.compose.runtime.Composable

/**
 * The contract every mini-game implements to appear on the Paw Play home
 * screen. See docs/ARCHITECTURE.md: adding a game should mean writing one
 * of these plus one line in [GameCatalog] — nothing else changes.
 */
interface MiniGame {
    /** Stable and never reused once shipped — nothing currently depends on
     *  it surviving a rename, but future save data might. */
    val id: String

    /** The home-screen tile's icon. No text — the player picks by picture. */
    val icon: @Composable () -> Unit

    /** The game itself, full screen. Call [onExit] to return home. */
    val content: @Composable (onExit: () -> Unit) -> Unit
}
