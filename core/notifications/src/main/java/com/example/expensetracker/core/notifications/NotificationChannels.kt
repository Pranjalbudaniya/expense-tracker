package com.example.expensetracker.core.notifications

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.os.Build

/**
 * Stable notification channels for financial events.
 */
object NotificationChannels {
    const val CHANNEL_RECURRING = "channel_recurring_transactions"
    const val CHANNEL_BUDGETS = "channel_budget_alerts"

    /**
     * Initializes notification channels on Android O (API 26) and above.
     * Safe to invoke multiple times as channels with existing IDs are no-ops.
     */
    fun createChannels(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager
                ?: return

            val recurringChannel = NotificationChannel(
                CHANNEL_RECURRING,
                "Recurring Transactions",
                NotificationManager.IMPORTANCE_DEFAULT
            ).apply {
                description = "Reminders and results for scheduled recurring transactions"
                enableLights(true)
                enableVibration(true)
            }

            val budgetChannel = NotificationChannel(
                CHANNEL_BUDGETS,
                "Budget Alerts",
                NotificationManager.IMPORTANCE_DEFAULT
            ).apply {
                description = "Alerts when budgets approach or exceed their spending limits"
                enableLights(true)
                enableVibration(true)
            }

            notificationManager.createNotificationChannels(listOf(recurringChannel, budgetChannel))
        }
    }
}
