package com.example.expensetracker.core.preferences

import kotlinx.coroutines.flow.Flow

/**
 * Repository interface for observing and modifying user preferences.
 */
interface PreferencesRepository {

    /**
     * Observable stream of current [UserPreferences].
     */
    val userPreferences: Flow<UserPreferences>

    /**
     * Updates the application theme mode.
     */
    suspend fun setThemeMode(themeMode: ThemeMode)

    /**
     * Enables or disables dynamic color (Material You).
     */
    suspend fun setDynamicColorEnabled(enabled: Boolean)

    /**
     * Sets or clears the custom accent/seed color.
     * Passing `null` clears the custom accent color.
     */
    suspend fun setCustomAccentColor(color: Long?)

    /**
     * Clears any active custom accent/seed color.
     */
    suspend fun clearCustomAccentColor()

    /**
     * Changes the selected currency code.
     */
    suspend fun setCurrencyCode(currencyCode: String)

    /**
     * Updates the application text/font size preference.
     */
    suspend fun setFontSize(fontSize: FontSizePreference) {}

    /**
     * Enables or disables notifications globally.
     */
    suspend fun setNotificationsEnabled(enabled: Boolean) {}

    /**
     * Enables or disables recurring transaction notifications.
     */
    suspend fun setRecurringNotificationsEnabled(enabled: Boolean) {}

    /**
     * Enables or disables budget alerts.
     */
    suspend fun setBudgetAlertsEnabled(enabled: Boolean) {}

    /**
     * Sets the budget percentage threshold (e.g. 50, 75, 80, 90, 100).
     */
    suspend fun setBudgetThresholdPercent(percent: Int) {}

    /**
     * Checks whether a notification event with the specified deterministic key has already been sent.
     */
    suspend fun hasNotificationBeenSent(eventKey: String): Boolean = false

    /**
     * Marks a notification event key as sent.
     */
    suspend fun markNotificationSent(eventKey: String) {}
}
