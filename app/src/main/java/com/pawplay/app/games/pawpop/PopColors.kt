package com.pawplay.app.games.pawpop

import androidx.compose.ui.graphics.Color

// Paw Pop content colours: not theme tokens (docs/DESIGN-SYSTEM.md, "Paw Pop"). Chrome colours (cream, white, ink,
// sunshine, coral) come from ui/theme/Color.kt as usual. The six target colours live in `PopPalette` (pure Kotlin, so
// the contrast test can read them); `TargetColors` turns them into `Color`s once.
internal val PopSkyTop = Color(0xFFB7D6F4)
internal val PopSkyBottom = Color(0xFFDCEBF8)
internal val PopBadgeTeal = Color(0xFF0F9D94)   // the home tile badge
internal val PopStarRim = Color(0xFFD99A00)
internal val PopSparkleRim = Color(0xFFE8A400)
internal val PopLilac = Color(0xFFB79BF0)       // the slow-drift halo and glow
internal val PopPaper = Color(0xFFFFF8EC)       // the critter bubble's inner disc
internal val PopWindow = Color(0xFFDFF3FB)      // the ship's window
internal val PopFlameCore = Color(0xFFFFF6C7)
internal val PopMouth = Color(0xFFB8301F)
internal val PopBlush = Color(0xFFFF8FA8)

internal val TargetColors: Array<Color> = Array(PopPalette.count) { Color(PopPalette.argb[it]) }
