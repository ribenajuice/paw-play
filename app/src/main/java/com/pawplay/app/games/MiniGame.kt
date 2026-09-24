package com.pawplay.app.games

import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.Dp

/**
 * The contract every mini-game implements to appear on the Paw Play home
 * screen. See docs/ARCHITECTURE.md: adding a game should mean writing one
 * of these plus one line in [GameCatalog] — nothing else changes.
 */
interface MiniGame {
    /** Stable and never reused once shipped — nothing currently depends on
     *  it surviving a rename, but future save data might. */
    val id: String

    /**
     * The home-screen tile's icon, drawn at [size] — the tile grid can be
     * anywhere from one tile to a dozen (docs/PRD.md), so the icon must
     * scale with it rather than assume a fixed size. No text — the player
     * picks by picture.
     */
    val icon: @Composable (size: Dp) -> Unit

    /** The game itself, full screen. Call [onExit] to return home. */
    val content: @Composable (onExit: () -> Unit) -> Unit
}
