package com.example.expensetracker.core.designsystem.theme

import android.app.Activity
import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.Density
import androidx.core.view.WindowCompat

@Composable
fun ExpenseTrackerTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    dynamicColor: Boolean = true,
    customAccentColor: Long? = null,
    fontScale: Float = 1.0f,
    content: @Composable () -> Unit
) {
    val colorScheme: ColorScheme = when {
        // Explicit custom accent color set by user has top priority
        customAccentColor != null -> {
            buildAccentColorScheme(accentArgb = customAccentColor, darkTheme = darkTheme)
        }
        // Material You dynamic system colors on Android 12+
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            val context = LocalContext.current
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        }
        // Curated emerald/forest theme
        else -> expenseTrackerColorScheme(darkTheme = darkTheme)
    }

    // Synchronize system bar icons with theme mode
    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as? Activity)?.window
            if (window != null) {
                val insetsController = WindowCompat.getInsetsController(window, view)
                insetsController.isAppearanceLightStatusBars = !darkTheme
                insetsController.isAppearanceLightNavigationBars = !darkTheme
            }
        }
    }

    // Dynamic typography scaling
    val typography = remember(fontScale) {
        createScaledTypography(fontScale)
    }
    val currentDensity = LocalDensity.current
    val adjustedDensity = remember(currentDensity, fontScale) {
        Density(
            density = currentDensity.density,
            fontScale = currentDensity.fontScale * fontScale
        )
    }

    CompositionLocalProvider(LocalDensity provides adjustedDensity) {
        MaterialTheme(
            colorScheme = colorScheme,
            typography = typography,
            shapes = Shapes,
            content = content
        )
    }
}
