package com.example.expensetracker

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.Composable
import com.example.expensetracker.core.preferences.ThemeMode
import com.example.expensetracker.core.preferences.UserPreferences

/**
 * App-level theme state derived from [UserPreferences] to configure [ExpenseTrackerTheme].
 *
 * @property darkTheme Whether dark theme should be applied.
 * @property useDynamicColor Whether Material You dynamic colors should be used when supported.
 * @property customAccentColor Custom accent/seed ARGB Long (or null if unset).
 * @property fontScale Typography font size scaling multiplier.
 */
data class ThemeState(
    val darkTheme: Boolean,
    val useDynamicColor: Boolean,
    val customAccentColor: Long?,
    val fontScale: Float
) {
    companion object {
        val DEFAULT = ThemeState(
            darkTheme = false,
            useDynamicColor = true,
            customAccentColor = null,
            fontScale = 1.0f
        )

        /**
         * Resolves the theme state from [UserPreferences] given the system's dark theme state.
         */
        fun fromPreferences(
            preferences: UserPreferences,
            isSystemInDark: Boolean
        ): ThemeState {
            val darkTheme = when (preferences.themeMode) {
                ThemeMode.SYSTEM -> isSystemInDark
                ThemeMode.LIGHT -> false
                ThemeMode.DARK -> true
            }
            return ThemeState(
                darkTheme = darkTheme,
                useDynamicColor = preferences.isDynamicColorEnabled,
                customAccentColor = preferences.customAccentColor,
                fontScale = preferences.fontSize.scaleFactor
            )
        }
    }
}

/**
 * Helper to remember the derived [ThemeState] based on current [UserPreferences].
 */
@Composable
fun rememberThemeState(
    preferences: UserPreferences,
    isSystemInDark: Boolean = isSystemInDarkTheme()
): ThemeState {
    return ThemeState.fromPreferences(preferences, isSystemInDark)
}
