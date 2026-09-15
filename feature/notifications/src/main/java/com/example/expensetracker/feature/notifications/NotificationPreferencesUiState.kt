package com.example.expensetracker.feature.notifications

/**
 * Immutable UI state for the notification preferences screen.
 *
 * @property isLoading Whether preferences are currently being loaded from storage.
 * @property notificationsEnabled Master toggle state for all notifications.
 * @property recurringNotificationsEnabled Toggle state for recurring transaction notifications.
 * @property budgetAlertsEnabled Toggle state for budget threshold and limit alerts.
 * @property budgetThresholdPercent Selected percentage threshold for budget alerts (e.g. 50, 75, 80, 90, 100).
 * @property isPermissionGranted Whether the system POST_NOTIFICATIONS permission is currently granted.
 * @property userMessage Transient informative feedback for the user.
 * @property errorMessage Transient error feedback for the user.
 */
data class NotificationPreferencesUiState(
    val isLoading: Boolean = false,
    val notificationsEnabled: Boolean = false,
    val recurringNotificationsEnabled: Boolean = false,
    val budgetAlertsEnabled: Boolean = false,
    val budgetThresholdPercent: Int = 80,
    val isPermissionGranted: Boolean = true,
    val userMessage: String? = null,
    val errorMessage: String? = null
)
