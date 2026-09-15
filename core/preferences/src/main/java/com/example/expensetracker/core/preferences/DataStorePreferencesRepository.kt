package com.example.expensetracker.core.preferences

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import java.io.IOException

private val Context.userPreferencesDataStore: DataStore<Preferences> by preferencesDataStore(
    name = "user_preferences"
)

/**
 * [PreferencesRepository] implementation backed by Android DataStore Preferences.
 */
class DataStorePreferencesRepository(
    private val dataStore: DataStore<Preferences>
) : PreferencesRepository {

    constructor(context: Context) : this(
        context.applicationContext.userPreferencesDataStore
    )

    override val userPreferences: Flow<UserPreferences> = dataStore.data
        .catch { exception ->
            if (exception is IOException) {
                emit(emptyPreferences())
            } else {
                throw exception
            }
        }
        .map { preferences ->
            mapUserPreferences(preferences)
        }

    override suspend fun setThemeMode(themeMode: ThemeMode) {
        dataStore.edit { preferences ->
            preferences[PreferencesKeys.THEME_MODE] = themeMode.name
        }
    }

    override suspend fun setDynamicColorEnabled(enabled: Boolean) {
        dataStore.edit { preferences ->
            preferences[PreferencesKeys.DYNAMIC_COLOR_ENABLED] = enabled
        }
    }

    override suspend fun setCustomAccentColor(color: Long?) {
        if (color == null) {
            clearCustomAccentColor()
            return
        }
        require(color in VALID_COLOR_RANGE) {
            "Custom accent color must be a valid 32-bit ARGB value (0x00000000..0xFFFFFFFF), got: $color"
        }
        dataStore.edit { preferences ->
            preferences[PreferencesKeys.CUSTOM_ACCENT_COLOR] = color
        }
    }

    override suspend fun clearCustomAccentColor() {
        dataStore.edit { preferences ->
            preferences.remove(PreferencesKeys.CUSTOM_ACCENT_COLOR)
        }
    }

    override suspend fun setCurrencyCode(currencyCode: String) {
        val trimmed = currencyCode.trim()
        require(trimmed.isNotBlank()) { "Currency code cannot be blank" }
        require(isValidCurrencyCode(trimmed)) { "Invalid currency code: $currencyCode" }
        dataStore.edit { preferences ->
            preferences[PreferencesKeys.CURRENCY_CODE] = trimmed.uppercase()
        }
    }

    private fun mapUserPreferences(preferences: Preferences): UserPreferences {
        val rawTheme = preferences[PreferencesKeys.THEME_MODE]
        val themeMode = ThemeMode.fromString(rawTheme)

        val isDynamicColor = preferences[PreferencesKeys.DYNAMIC_COLOR_ENABLED]
            ?: UserPreferences.DEFAULT_DYNAMIC_COLOR

        val rawAccent = preferences[PreferencesKeys.CUSTOM_ACCENT_COLOR]
        val customAccent = if (rawAccent != null && rawAccent in VALID_COLOR_RANGE) {
            rawAccent
        } else {
            null
        }

        val rawCurrency = preferences[PreferencesKeys.CURRENCY_CODE]?.trim()
        val currencyCode = if (!rawCurrency.isNullOrBlank() && isValidCurrencyCode(rawCurrency)) {
            rawCurrency.uppercase()
        } else {
            UserPreferences.DEFAULT_CURRENCY_CODE
        }

        val notificationsEnabled = preferences[PreferencesKeys.NOTIFICATIONS_ENABLED]
            ?: UserPreferences.DEFAULT_NOTIFICATIONS_ENABLED

        val recurringNotificationsEnabled = preferences[PreferencesKeys.RECURRING_NOTIFICATIONS_ENABLED]
            ?: UserPreferences.DEFAULT_RECURRING_NOTIFICATIONS_ENABLED

        val budgetAlertsEnabled = preferences[PreferencesKeys.BUDGET_ALERTS_ENABLED]
            ?: UserPreferences.DEFAULT_BUDGET_ALERTS_ENABLED

        val budgetThresholdPercent = preferences[PreferencesKeys.BUDGET_THRESHOLD_PERCENT]
            ?: UserPreferences.DEFAULT_BUDGET_THRESHOLD_PERCENT

        val rawFontSize = preferences[PreferencesKeys.FONT_SIZE]
        val fontSize = FontSizePreference.fromString(rawFontSize)

        return UserPreferences(
            themeMode = themeMode,
            isDynamicColorEnabled = isDynamicColor,
            customAccentColor = customAccent,
            currencyCode = currencyCode,
            notificationsEnabled = notificationsEnabled,
            recurringNotificationsEnabled = recurringNotificationsEnabled,
            budgetAlertsEnabled = budgetAlertsEnabled,
            budgetThresholdPercent = budgetThresholdPercent,
            fontSize = fontSize
        )
    }

    override suspend fun setFontSize(fontSize: FontSizePreference) {
        dataStore.edit { preferences ->
            preferences[PreferencesKeys.FONT_SIZE] = fontSize.name
        }
    }

    override suspend fun setNotificationsEnabled(enabled: Boolean) {
        dataStore.edit { preferences ->
            preferences[PreferencesKeys.NOTIFICATIONS_ENABLED] = enabled
        }
    }

    override suspend fun setRecurringNotificationsEnabled(enabled: Boolean) {
        dataStore.edit { preferences ->
            preferences[PreferencesKeys.RECURRING_NOTIFICATIONS_ENABLED] = enabled
        }
    }

    override suspend fun setBudgetAlertsEnabled(enabled: Boolean) {
        dataStore.edit { preferences ->
            preferences[PreferencesKeys.BUDGET_ALERTS_ENABLED] = enabled
        }
    }

    override suspend fun setBudgetThresholdPercent(percent: Int) {
        require(percent in 1..100) { "Budget threshold percent must be between 1 and 100, got: $percent" }
        dataStore.edit { preferences ->
            preferences[PreferencesKeys.BUDGET_THRESHOLD_PERCENT] = percent
        }
    }

    override suspend fun hasNotificationBeenSent(eventKey: String): Boolean {
        val prefs = dataStore.data.first()
        return prefs[PreferencesKeys.NOTIFIED_EVENT_KEYS]?.contains(eventKey) == true
    }

    override suspend fun markNotificationSent(eventKey: String) {
        dataStore.edit { preferences ->
            val current = preferences[PreferencesKeys.NOTIFIED_EVENT_KEYS] ?: emptySet()
            val updated = (current + eventKey).toList().takeLast(MAX_SAVED_EVENT_KEYS).toSet()
            preferences[PreferencesKeys.NOTIFIED_EVENT_KEYS] = updated
        }
    }

    private fun isValidCurrencyCode(code: String): Boolean {
        if (code.isBlank() || code.length != 3 || !code.all { it.isLetter() }) {
            return false
        }
        return runCatching {
            java.util.Currency.getInstance(code.uppercase())
        }.isSuccess
    }

    companion object {
        private val VALID_COLOR_RANGE = 0x00000000L..0xFFFFFFFFL
        private const val MAX_SAVED_EVENT_KEYS = 500
    }
}
