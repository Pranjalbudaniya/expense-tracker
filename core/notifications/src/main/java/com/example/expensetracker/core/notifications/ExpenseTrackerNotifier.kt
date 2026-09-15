package com.example.expensetracker.core.notifications

import android.app.NotificationManager
import android.content.Context
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.example.expensetracker.core.preferences.PreferencesRepository
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.flow.first
import java.time.LocalDate
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Entry-point contract for dispatching user-facing financial notifications.
 */
interface ExpenseTrackerNotifier {

    /**
     * Dispatches a notification confirming a recurring transaction occurrence was processed.
     */
    suspend fun notifyRecurringProcessed(
        recurringId: String,
        occurrenceDate: LocalDate,
        title: String,
        amountText: String
    )

    /**
     * Dispatches an advance reminder for an upcoming recurring transaction.
     */
    suspend fun notifyRecurringUpcoming(
        recurringId: String,
        dueDate: LocalDate,
        title: String,
        amountText: String
    )

    /**
     * Dispatches an alert when a budget reaches or crosses a specific threshold (e.g. 80%, 90%).
     */
    suspend fun notifyBudgetThresholdReached(
        budgetId: String,
        budgetName: String,
        thresholdPercent: Int,
        periodKey: String
    )

    /**
     * Dispatches an alert when a budget limit is reached or exceeded (100%+).
     */
    suspend fun notifyBudgetExceeded(
        budgetId: String,
        budgetName: String,
        periodKey: String
    )
}

@Singleton
class ExpenseTrackerNotifierImpl @Inject constructor(
    @ApplicationContext private val context: Context,
    private val preferencesRepository: PreferencesRepository
) : ExpenseTrackerNotifier {

    init {
        NotificationChannels.createChannels(context)
    }

    override suspend fun notifyRecurringProcessed(
        recurringId: String,
        occurrenceDate: LocalDate,
        title: String,
        amountText: String
    ) {
        val prefs = preferencesRepository.userPreferences.first()
        if (!prefs.notificationsEnabled || !prefs.recurringNotificationsEnabled) return

        val eventKey = "recurring_processed_${recurringId}_${occurrenceDate}"
        if (preferencesRepository.hasNotificationBeenSent(eventKey)) return

        val displayTitle = "Recurring Payment Processed"
        val message = "Scheduled payment for '$title' ($amountText) was recorded"

        postNotification(
            channelId = NotificationChannels.CHANNEL_RECURRING,
            eventKey = eventKey,
            title = displayTitle,
            message = message,
            category = NotificationCompat.CATEGORY_EVENT
        )
    }

    override suspend fun notifyRecurringUpcoming(
        recurringId: String,
        dueDate: LocalDate,
        title: String,
        amountText: String
    ) {
        val prefs = preferencesRepository.userPreferences.first()
        if (!prefs.notificationsEnabled || !prefs.recurringNotificationsEnabled) return

        val eventKey = "recurring_upcoming_${recurringId}_${dueDate}"
        if (preferencesRepository.hasNotificationBeenSent(eventKey)) return

        val displayTitle = "Upcoming Recurring Payment"
        val message = "Payment for '$title' ($amountText) is due on $dueDate"

        postNotification(
            channelId = NotificationChannels.CHANNEL_RECURRING,
            eventKey = eventKey,
            title = displayTitle,
            message = message,
            category = NotificationCompat.CATEGORY_REMINDER
        )
    }

    override suspend fun notifyBudgetThresholdReached(
        budgetId: String,
        budgetName: String,
        thresholdPercent: Int,
        periodKey: String
    ) {
        val prefs = preferencesRepository.userPreferences.first()
        if (!prefs.notificationsEnabled || !prefs.budgetAlertsEnabled) return
        if (thresholdPercent < prefs.budgetThresholdPercent) return

        val eventKey = "budget_threshold_${budgetId}_${periodKey}_${thresholdPercent}"
        if (preferencesRepository.hasNotificationBeenSent(eventKey)) return

        val displayTitle = "Budget Alert"
        val message = "Your '$budgetName' budget has reached $thresholdPercent% of its limit"

        postNotification(
            channelId = NotificationChannels.CHANNEL_BUDGETS,
            eventKey = eventKey,
            title = displayTitle,
            message = message,
            category = NotificationCompat.CATEGORY_STATUS
        )
    }

    override suspend fun notifyBudgetExceeded(
        budgetId: String,
        budgetName: String,
        periodKey: String
    ) {
        val prefs = preferencesRepository.userPreferences.first()
        if (!prefs.notificationsEnabled || !prefs.budgetAlertsEnabled) return

        val eventKey = "budget_exceeded_${budgetId}_${periodKey}"
        if (preferencesRepository.hasNotificationBeenSent(eventKey)) return

        val displayTitle = "Budget Limit Exceeded"
        val message = "Your '$budgetName' budget has reached its limit"

        postNotification(
            channelId = NotificationChannels.CHANNEL_BUDGETS,
            eventKey = eventKey,
            title = displayTitle,
            message = message,
            category = NotificationCompat.CATEGORY_ALARM
        )
    }

    private suspend fun postNotification(
        channelId: String,
        eventKey: String,
        title: String,
        message: String,
        category: String
    ) {
        val notificationManagerCompat = NotificationManagerCompat.from(context)
        if (!notificationManagerCompat.areNotificationsEnabled()) return

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val systemManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager
            val channel = systemManager?.getNotificationChannel(channelId)
            if (channel != null && channel.importance == NotificationManager.IMPORTANCE_NONE) {
                return
            }
        }

        val notificationId = eventKey.hashCode()

        val notification = NotificationCompat.Builder(context, channelId)
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentTitle(title)
            .setContentText(message)
            .setStyle(NotificationCompat.BigTextStyle().bigText(message))
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setCategory(category)
            .setVisibility(NotificationCompat.VISIBILITY_PRIVATE)
            .setAutoCancel(true)
            .build()

        try {
            notificationManagerCompat.notify(notificationId, notification)
            preferencesRepository.markNotificationSent(eventKey)
        } catch (e: SecurityException) {
            // Permission revoked or not granted at runtime
        } catch (e: Exception) {
            // Fail safely without crashing
        }
    }
}

@Module
@InstallIn(SingletonComponent::class)
abstract class NotificationsModule {
    @Binds
    abstract fun bindExpenseTrackerNotifier(
        impl: ExpenseTrackerNotifierImpl
    ): ExpenseTrackerNotifier
}
