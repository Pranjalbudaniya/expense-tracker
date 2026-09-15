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
 * Builds a complete harmonious [ColorScheme] using the user's custom accent color.
 */
fun buildAccentColorScheme(accentArgb: Long, darkTheme: Boolean): ColorScheme {
    val accent = Color(accentArgb)
    val hsl = rgbToHsl(accent.red, accent.green, accent.blue)
    val h = hsl[0]
    val s = hsl[1]

    return if (darkTheme) {
        darkColorScheme(
            primary = hslToColor(h, (s * 0.85f).coerceIn(0.55f, 0.90f), 0.72f),
            onPrimary = hslToColor(h, 0.40f, 0.12f),
            primaryContainer = hslToColor(h, 0.60f, 0.28f),
            onPrimaryContainer = hslToColor(h, 0.25f, 0.92f),
            secondary = hslToColor(h, (s * 0.45f).coerceIn(0.25f, 0.55f), 0.68f),
            onSecondary = hslToColor(h, 0.35f, 0.15f),
            secondaryContainer = hslToColor(h, 0.35f, 0.26f),
            onSecondaryContainer = hslToColor(h, 0.20f, 0.90f),
            tertiary = hslToColor((h + 35f) % 360f, 0.50f, 0.72f),
            onTertiary = hslToColor((h + 35f) % 360f, 0.40f, 0.15f),
            tertiaryContainer = hslToColor((h + 35f) % 360f, 0.45f, 0.28f),
            onTertiaryContainer = hslToColor((h + 35f) % 360f, 0.20f, 0.92f),
            background = hslToColor(h, 0.10f, 0.08f),
            onBackground = Color(0xFFDEE4DF),
            surface = hslToColor(h, 0.10f, 0.09f),
            onSurface = Color(0xFFDEE4DF),
            surfaceVariant = hslToColor(h, 0.12f, 0.22f),
            onSurfaceVariant = hslToColor(h, 0.10f, 0.75f),
            surfaceContainer = hslToColor(h, 0.12f, 0.15f),
            surfaceContainerHigh = hslToColor(h, 0.12f, 0.19f),
            surfaceContainerLow = hslToColor(h, 0.10f, 0.12f),
            outline = hslToColor(h, 0.15f, 0.50f),
            outlineVariant = hslToColor(h, 0.12f, 0.28f)
        )
    } else {
        lightColorScheme(
            primary = hslToColor(h, (s * 0.90f).coerceIn(0.65f, 0.95f), 0.36f),
            onPrimary = Color.White,
            primaryContainer = hslToColor(h, 0.50f, 0.88f),
            onPrimaryContainer = hslToColor(h, 0.80f, 0.12f),
            secondary = hslToColor(h, 0.35f, 0.45f),
            onSecondary = Color.White,
            secondaryContainer = hslToColor(h, 0.30f, 0.90f),
            onSecondaryContainer = hslToColor(h, 0.60f, 0.15f),
            tertiary = hslToColor((h + 35f) % 360f, 0.45f, 0.40f),
            onTertiary = Color.White,
            tertiaryContainer = hslToColor((h + 35f) % 360f, 0.40f, 0.88f),
            onTertiaryContainer = hslToColor((h + 35f) % 360f, 0.70f, 0.12f),
            background = hslToColor(h, 0.12f, 0.98f),
            onBackground = Color(0xFF141D1A),
            surface = hslToColor(h, 0.12f, 0.98f),
            onSurface = Color(0xFF141D1A),
            surfaceVariant = hslToColor(h, 0.15f, 0.90f),
            onSurfaceVariant = hslToColor(h, 0.20f, 0.32f),
            surfaceContainer = hslToColor(h, 0.14f, 0.93f),
            surfaceContainerHigh = hslToColor(h, 0.16f, 0.89f),
            surfaceContainerLow = hslToColor(h, 0.10f, 0.96f),
            outline = hslToColor(h, 0.18f, 0.55f),
            outlineVariant = hslToColor(h, 0.15f, 0.80f)
        )
    }
}

private fun rgbToHsl(r: Float, g: Float, b: Float): FloatArray {
    val max = maxOf(r, g, b)
    val min = minOf(r, g, b)
    val delta = max - min
    var h = 0f
    var s = 0f
    val l = (max + min) / 2f

    if (delta != 0f) {
        s = if (l > 0.5f) delta / (2f - max - min) else delta / (max + min)
        h = when (max) {
            r -> ((g - b) / delta) + (if (g < b) 6f else 0f)
            g -> ((b - r) / delta) + 2f
            else -> ((r - g) / delta) + 4f
        } * 60f
    }
    return floatArrayOf(h, s, l)
}

private fun hslToColor(h: Float, s: Float, l: Float, alpha: Float = 1f): Color {
    val c = (1f - kotlin.math.abs(2f * l - 1f)) * s
    val x = c * (1f - kotlin.math.abs(((h / 60f) % 2f) - 1f))
    val m = l - c / 2f

    val (r1, g1, b1) = when (((h / 60f).toInt()) % 6) {
        0 -> Triple(c, x, 0f)
        1 -> Triple(x, c, 0f)
        2 -> Triple(0f, c, x)
        3 -> Triple(0f, x, c)
        4 -> Triple(x, 0f, c)
        else -> Triple(c, 0f, x)
    }
    return Color(
        red = (r1 + m).coerceIn(0f, 1f),
        green = (g1 + m).coerceIn(0f, 1f),
        blue = (b1 + m).coerceIn(0f, 1f),
        alpha = alpha
    )
}
