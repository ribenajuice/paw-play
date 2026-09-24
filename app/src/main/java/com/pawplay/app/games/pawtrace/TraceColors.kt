package com.pawplay.app.games.pawtrace

import androidx.compose.ui.graphics.Color

// Paw Trace content colours: not theme tokens (docs/DESIGN-SYSTEM.md, "Paw Trace: guide, paint and glow").
// Chrome colours (cream, white, ink, leaf, sunshine, coral) come from ui/theme/Color.kt as usual.
val TraceGuideFill = Color(0xFFE6DBF9)   // the 56dp band
val TraceGuideEdge = Color(0xFFA98BE6)   // 3dp outline round the band; also the direction-cue paws
val TraceGuideDots = Color(0xFFB9A2EC)   // dotted centre line
val TraceGlow = Color(0xFFB18CFA)        // three widening rings under the paint
val TraceGrape = Color(0xFF8E59E6)       // the paint, and the tile badge
val TraceShine = Color(0xFFB995FA)       // centre highlight down painted runs
val TraceFrogBlush = Color(0xFFFF8FA8)   // the happy frog's cheeks and tongue
val TraceFrogMouth = Color(0xFFB8301F)   // the happy frog's open mouth
