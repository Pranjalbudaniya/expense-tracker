package com.example.expensetracker.core.designsystem.theme

import androidx.compose.material3.ColorScheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.ui.graphics.Color

// Stitch-inspired Emerald / Forest Green curated color palette
val LightPrimary = Color(0xFF005138)
val LightOnPrimary = Color(0xFFFFFFFF)
val LightPrimaryContainer = Color(0xFF9DF4CB)
val LightOnPrimaryContainer = Color(0xFF002114)
val LightSecondary = Color(0xFF4C6358)
val LightOnSecondary = Color(0xFFFFFFFF)
val LightSecondaryContainer = Color(0xFFCCE6D8)
val LightOnSecondaryContainer = Color(0xFF091F17)
val LightTertiary = Color(0xFF254B5B)
val LightOnTertiary = Color(0xFFFFFFFF)
val LightTertiaryContainer = Color(0xFFC2E8FC)
val LightOnTertiaryContainer = Color(0xFF001F2A)
val LightError = Color(0xFFBA1A1A)
val LightOnError = Color(0xFFFFFFFF)
val LightErrorContainer = Color(0xFFFFDAD6)
val LightOnErrorContainer = Color(0xFF410002)
val LightBackground = Color(0xFFF2FCF6)
val LightOnBackground = Color(0xFF141D1A)
val LightSurface = Color(0xFFF2FCF6)
val LightOnSurface = Color(0xFF141D1A)
val LightSurfaceVariant = Color(0xFFDBE5DF)
val LightOnSurfaceVariant = Color(0xFF3F4943)
val LightOutline = Color(0xFF6F7A73)
val LightOutlineVariant = Color(0xFFBEC9C1)

val DarkPrimary = Color(0xFF81D7B0)
val DarkOnPrimary = Color(0xFF003825)
val DarkPrimaryContainer = Color(0xFF005138)
val DarkOnPrimaryContainer = Color(0xFF9DF4CB)
val DarkSecondary = Color(0xFFB3CCBF)
val DarkOnSecondary = Color(0xFF1E352C)
val DarkSecondaryContainer = Color(0xFF354B41)
val DarkOnSecondaryContainer = Color(0xFFCFE8DA)
val DarkTertiary = Color(0xFFA6CCE0)
val DarkOnTertiary = Color(0xFF0A3443)
val DarkTertiaryContainer = Color(0xFF254B5C)
val DarkOnTertiaryContainer = Color(0xFFC2E8FC)
val DarkError = Color(0xFFFFB4AB)
val DarkOnError = Color(0xFF690005)
val DarkErrorContainer = Color(0xFF93000A)
val DarkOnErrorContainer = Color(0xFFFFDAD6)
val DarkBackground = Color(0xFF0F1513)
val DarkOnBackground = Color(0xFFDEE4DF)
val DarkSurface = Color(0xFF0F1513)
val DarkOnSurface = Color(0xFFDEE4DF)
val DarkSurfaceVariant = Color(0xFF3F4943)
val DarkOnSurfaceVariant = Color(0xFFBEC9C1)
val DarkOutline = Color(0xFF89938C)
val DarkOutlineVariant = Color(0xFF3F4943)

val LightColorScheme: ColorScheme = lightColorScheme(
    primary = LightPrimary,
    onPrimary = LightOnPrimary,
    primaryContainer = LightPrimaryContainer,
    onPrimaryContainer = LightOnPrimaryContainer,
    secondary = LightSecondary,
    onSecondary = LightOnSecondary,
    secondaryContainer = LightSecondaryContainer,
    onSecondaryContainer = LightOnSecondaryContainer,
    tertiary = LightTertiary,
    onTertiary = LightOnTertiary,
    tertiaryContainer = LightTertiaryContainer,
    onTertiaryContainer = LightOnTertiaryContainer,
    error = LightError,
    onError = LightOnError,
    errorContainer = LightErrorContainer,
    onErrorContainer = LightOnErrorContainer,
    background = LightBackground,
    onBackground = LightOnBackground,
    surface = LightSurface,
    onSurface = LightOnSurface,
    surfaceVariant = LightSurfaceVariant,
    onSurfaceVariant = LightOnSurfaceVariant,
    outline = LightOutline,
    outlineVariant = LightOutlineVariant
)

val DarkColorScheme: ColorScheme = darkColorScheme(
    primary = DarkPrimary,
    onPrimary = DarkOnPrimary,
    primaryContainer = DarkPrimaryContainer,
    onPrimaryContainer = DarkOnPrimaryContainer,
    secondary = DarkSecondary,
    onSecondary = DarkOnSecondary,
    secondaryContainer = DarkSecondaryContainer,
    onSecondaryContainer = DarkOnSecondaryContainer,
    tertiary = DarkTertiary,
    onTertiary = DarkOnTertiary,
    tertiaryContainer = DarkTertiaryContainer,
    onTertiaryContainer = DarkOnTertiaryContainer,
    error = DarkError,
    onError = DarkOnError,
    errorContainer = DarkErrorContainer,
    onErrorContainer = DarkOnErrorContainer,
    background = DarkBackground,
    onBackground = DarkOnBackground,
    surface = DarkSurface,
    onSurface = DarkOnSurface,
    surfaceVariant = DarkSurfaceVariant,
    onSurfaceVariant = DarkOnSurfaceVariant,
    outline = DarkOutline,
    outlineVariant = DarkOutlineVariant
)

/**
 * Returns the curated default [ColorScheme] for the application.
 */
fun expenseTrackerColorScheme(
    darkTheme: Boolean
): ColorScheme {
    return if (darkTheme) DarkColorScheme else LightColorScheme
}

/**
 * Builds a harmonious [ColorScheme] using the user's custom accent color.
 */
fun buildAccentColorScheme(accentArgb: Long, darkTheme: Boolean): ColorScheme {
    val accent = Color(accentArgb)
    return if (darkTheme) {
        DarkColorScheme.copy(
            primary = accent,
            primaryContainer = accent.copy(alpha = 0.35f),
            onPrimaryContainer = Color.White,
            surfaceTint = accent
        )
    } else {
        LightColorScheme.copy(
            primary = accent,
            primaryContainer = accent.copy(alpha = 0.18f),
            onPrimaryContainer = accent,
            surfaceTint = accent
        )
    }
}
