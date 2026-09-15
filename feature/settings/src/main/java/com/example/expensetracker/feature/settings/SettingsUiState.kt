package com.example.expensetracker.feature.settings

import android.os.Build
import com.example.expensetracker.core.model.money.Currency
import com.example.expensetracker.core.preferences.FontSizePreference
import com.example.expensetracker.core.preferences.ThemeMode

/**
 * Sensible Material-style seed color option for custom accent theming.
 *
 * @property name Human-readable color name.
 * @property argb 32-bit ARGB representation of the seed color.
 */
data class AccentColorOption(
    val name: String,
    val argb: Long
)

/**
 * Predefined set of Material-inspired accent/seed colors.
 */
val PRESET_ACCENT_COLORS: List<AccentColorOption> = listOf(
    AccentColorOption(name = "Ocean Blue", argb = 0xFF1976D2L),
    AccentColorOption(name = "Indigo", argb = 0xFF3F51B5L),
    AccentColorOption(name = "Deep Purple", argb = 0xFF7B1FA2L),
    AccentColorOption(name = "Teal", argb = 0xFF00796BL),
    AccentColorOption(name = "Forest Green", argb = 0xFF388E3CL),
    AccentColorOption(name = "Amber Orange", argb = 0xFFF57C00L),
    AccentColorOption(name = "Crimson Red", argb = 0xFFD32F2FL),
    AccentColorOption(name = "Rose Pink", argb = 0xFFC2185BL)
)

/**
 * Curated list of common ISO currencies supported in the application.
 */
val SUPPORTED_CURRENCIES: List<Currency> = listOf(
    Currency(code = "USD", symbol = "$", displayName = "US Dollar"),
    Currency(code = "EUR", symbol = "€", displayName = "Euro"),
    Currency(code = "GBP", symbol = "£", displayName = "British Pound"),
    Currency(code = "INR", symbol = "₹", displayName = "Indian Rupee"),
    Currency(code = "JPY", symbol = "¥", displayName = "Japanese Yen"),
    Currency(code = "CAD", symbol = "CA$", displayName = "Canadian Dollar"),
    Currency(code = "AUD", symbol = "A$", displayName = "Australian Dollar"),
    Currency(code = "CHF", symbol = "CHF", displayName = "Swiss Franc"),
    Currency(code = "CNY", symbol = "CN¥", displayName = "Chinese Yuan"),
    Currency(code = "SGD", symbol = "S$", displayName = "Singapore Dollar")
)

/**
 * Immutable UI state for the Settings screen.
 *
 * @property themeMode Active theme mode (SYSTEM, LIGHT, or DARK).
 * @property isDynamicColorEnabled Whether dynamic color (Material You) is enabled.
 * @property isDynamicColorSupported Whether dynamic colors are supported on this Android OS version.
 * @property customAccentColor Custom accent/seed color ARGB Long (or null if unset).
 * @property availableAccentColors Finite set of preset accent colors.
 * @property selectedCurrency The current default currency.
 * @property availableCurrencies List of supported ISO currencies available for selection.
 * @property isLoading Whether initial preferences are still loading.
 */
data class SettingsUiState(
    val themeMode: ThemeMode = ThemeMode.SYSTEM,
    val isDynamicColorEnabled: Boolean = true,
    val isDynamicColorSupported: Boolean = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S,
    val customAccentColor: Long? = null,
    val availableAccentColors: List<AccentColorOption> = PRESET_ACCENT_COLORS,
    val selectedCurrency: Currency = Currency.INR,
    val availableCurrencies: List<Currency> = SUPPORTED_CURRENCIES,
    val fontSize: FontSizePreference = FontSizePreference.DEFAULT,
    val isLoading: Boolean = false
)
