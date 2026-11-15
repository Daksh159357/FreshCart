package com.hack.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable

private val AppColorScheme = lightColorScheme(
    primary = GreenPrimary,
    onPrimary = GreenOnPrimary,
    primaryContainer = GreenContainer,
    onPrimaryContainer = GreenOnContainer,
    secondary = AmberAccent,
    onSecondary = AmberOnAccent,
    background = ScreenBackground,
    onBackground = ScreenOnBackground,
    surface = CardSurface,
    onSurface = TextOnSurface,
    surfaceVariant = SurfaceVariantBg,
    onSurfaceVariant = TextOnSurfaceVariant,
    outline = OutlineBorder
)

@Composable
fun FreshCartTheme(
    darkTheme: Boolean = false, // Enforce light grocery theme for consistent contrast
    content: @Composable () -> Unit
) {
    MaterialTheme(
        colorScheme = AppColorScheme,
        typography = Typography,
        content = content
    )
}
