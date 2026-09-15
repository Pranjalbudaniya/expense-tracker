package com.example.expensetracker.core.preferences

import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey

/**
 * DataStore preference keys for user settings.
 */
object PreferencesKeys {
    val THEME_MODE = stringPreferencesKey("theme_mode")
    val DYNAMIC_COLOR_ENABLED = booleanPreferencesKey("dynamic_color_enabled")
    val CUSTOM_ACCENT_COLOR = longPreferencesKey("custom_accent_color")
    val CURRENCY_CODE = stringPreferencesKey("currency_code")
    val NOTIFICATIONS_ENABLED = booleanPreferencesKey("notifications_enabled")
    val RECURRING_NOTIFICATIONS_ENABLED = booleanPreferencesKey("recurring_notifications_enabled")
    val BUDGET_ALERTS_ENABLED = booleanPreferencesKey("budget_alerts_enabled")
    val BUDGET_THRESHOLD_PERCENT = intPreferencesKey("budget_threshold_percent")
    val NOTIFIED_EVENT_KEYS = stringSetPreferencesKey("notified_event_keys")
    val FONT_SIZE = stringPreferencesKey("font_size")
}
