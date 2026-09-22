package com.pawplay.app.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable

// Always the same bright, warm palette regardless of system dark mode —
// predictability matters more than theme-matching for a toddler's app
// (docs/DESIGN-SYSTEM.md).
private val PawPlayColorScheme = lightColorScheme(
    primary = PawCoral,
    secondary = PawSunshine,
    background = PawCream,
    surface = PawCardWhite,
    onPrimary = PawCardWhite,
    onBackground = InkColor,
    onSurface = InkColor,
)

@Composable
fun PawPlayTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = PawPlayColorScheme,
        content = content,
    )
}
