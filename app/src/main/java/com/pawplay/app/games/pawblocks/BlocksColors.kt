package com.pawplay.app.games.pawblocks

import androidx.compose.ui.graphics.Color

// Paw Blocks content colours: not theme tokens (docs/DESIGN-SYSTEM.md, "Paw Blocks: board, blocks, marks and clears").
// Chrome colours (cream, white, ink, sunshine, sky) come from ui/theme/Color.kt as usual. The eight block colours
// live in `Family` (pure Kotlin, so the contrast tests can read them); `Family.color` turns them into a `Color`.
val BlocksEmptyFill = Color(0xFFF4ECDF)   // an empty cell
val BlocksEmptyEdge = Color(0xFFD9CCB8)   // its outline (empty cells carry an outline and no mark)
val BlocksPanelEdge = Color(0xFFEADFCF)   // the 3dp edge of the board panel
val BlocksSlotDash = Color(0xFFDCCFC0)    // dashed outline of an empty or lifted tray slot
val BlocksGhostEdge = Color(0xFF1A8FCB)   // the ghost's outline (3.1 against an empty cell); fill is sky
val BlocksPaleSparkle = Color(0xFFFFF6C7) // the clear-out's small sparkles
val BlocksBadgePink = Color(0xFFF2599B)   // the home tile badge
val BlocksBlush = Color(0xFFFF8FA8)       // the animal's cheeks and tongue
val BlocksMouth = Color(0xFFB8301F)       // the animal's open mouth

val Family.color: Color get() = Color(colour)
val Family.markColor: Color get() = Color(markColour)
