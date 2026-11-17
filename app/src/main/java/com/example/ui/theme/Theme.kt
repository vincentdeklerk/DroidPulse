package com.example.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val DarkColorScheme = darkColorScheme(
    primary = Mint400,
    onPrimary = Slate950,
    primaryContainer = Mint900,
    onPrimaryContainer = Mint400,
    secondary = Sky400,
    onSecondary = Slate950,
    secondaryContainer = Sky900,
    onSecondaryContainer = Sky400,
    tertiary = Amber400,
    onTertiary = Slate950,
    tertiaryContainer = Amber900,
    onTertiaryContainer = Amber400,
    error = Coral400,
    onError = Slate950,
    errorContainer = Coral900,
    onErrorContainer = Coral400,
    background = Slate950,
    onBackground = Slate200,
    surface = Slate900,
    onSurface = Slate200,
    surfaceVariant = Slate800,
    onSurfaceVariant = Slate400,
    surfaceContainerLow = Slate900,
    surfaceContainer = Slate850,
    surfaceContainerHigh = Slate800,
    outline = Slate700,
    outlineVariant = Color(0xFF1F2732)
)

private val LightColorScheme = lightColorScheme(
    primary = Mint700,
    onPrimary = Color.White,
    primaryContainer = Color(0xFFD1FAE5),
    onPrimaryContainer = Color(0xFF064E3B),
    secondary = Sky700,
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFE0F2FE),
    onSecondaryContainer = Color(0xFF0C4A6E),
    tertiary = Amber700,
    onTertiary = Color.White,
    tertiaryContainer = Color(0xFFFEF3C7),
    onTertiaryContainer = Color(0xFF78350F),
    error = Coral700,
    onError = Color.White,
    errorContainer = Color(0xFFFFE4E6),
    onErrorContainer = Color(0xFF881337),
    background = LightBg,
    onBackground = LightText,
    surface = LightSurface,
    onSurface = LightText,
    surfaceVariant = LightSurfaceVariant,
    onSurfaceVariant = LightTextSecondary,
    surfaceContainerLow = LightSurface,
    surfaceContainer = Color(0xFFF1F5F9),
    surfaceContainerHigh = LightSurfaceVariant,
    outline = LightOutline,
    outlineVariant = Color(0xFFE2E8F0)
)

@Composable
fun DroidPulseTheme(
    darkTheme: Boolean = true,
    useSystemTheme: Boolean = false,
    content: @Composable () -> Unit
) {
    val resolvedDark = if (useSystemTheme) isSystemInDarkTheme() else darkTheme
    val colorScheme = if (resolvedDark) DarkColorScheme else LightColorScheme

    MaterialTheme(
        colorScheme = colorScheme,
        typography = AppTypography,
        content = content
    )
}
