package com.example.expensetracker.core.preferences

/**
 * Supported theme modes.
 */
enum class ThemeMode {
    SYSTEM,
    LIGHT,
    DARK;

    companion object {
        val DEFAULT = SYSTEM

        fun fromString(value: String?): ThemeMode {
            return entries.firstOrNull { it.name.equals(value, ignoreCase = true) } ?: DEFAULT
        }
    }
}

/**
 * Immutable user preferences representation.
 *
 * @property themeMode The active theme mode (SYSTEM, LIGHT, or DARK).
 * @property isDynamicColorEnabled Whether dynamic color (Material You) is enabled.
 * @property customAccentColor Custom accent/seed color represented as a 32-bit ARGB Long (or null if unset).
 * @property currencyCode Selected ISO currency code.
 */
data class UserPreferences(
    val themeMode: ThemeMode = DEFAULT_THEME_MODE,
    val isDynamicColorEnabled: Boolean = DEFAULT_DYNAMIC_COLOR,
    val customAccentColor: Long? = DEFAULT_CUSTOM_ACCENT_COLOR,
    val currencyCode: String = DEFAULT_CURRENCY_CODE,
    val notificationsEnabled: Boolean = DEFAULT_NOTIFICATIONS_ENABLED,
    val recurringNotificationsEnabled: Boolean = DEFAULT_RECURRING_NOTIFICATIONS_ENABLED,
    val budgetAlertsEnabled: Boolean = DEFAULT_BUDGET_ALERTS_ENABLED,
    val budgetThresholdPercent: Int = DEFAULT_BUDGET_THRESHOLD_PERCENT,
    val fontSize: FontSizePreference = DEFAULT_FONT_SIZE
) {
    companion object {
        val DEFAULT_THEME_MODE: ThemeMode = ThemeMode.SYSTEM
        const val DEFAULT_DYNAMIC_COLOR: Boolean = true
        val DEFAULT_CUSTOM_ACCENT_COLOR: Long? = null
        val DEFAULT_FONT_SIZE: FontSizePreference = FontSizePreference.DEFAULT_SIZE
        const val DEFAULT_CURRENCY_CODE: String = "INR"
        const val DEFAULT_NOTIFICATIONS_ENABLED: Boolean = false
        const val DEFAULT_RECURRING_NOTIFICATIONS_ENABLED: Boolean = false
        const val DEFAULT_BUDGET_ALERTS_ENABLED: Boolean = false
        const val DEFAULT_BUDGET_THRESHOLD_PERCENT: Int = 80

        val DEFAULT = UserPreferences()
    }
}
