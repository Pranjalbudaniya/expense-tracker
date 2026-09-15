package com.example.expensetracker

import android.app.Application
import com.example.expensetracker.worker.RecurringWorkerInitializer
import dagger.hilt.android.HiltAndroidApp
import javax.inject.Inject

@HiltAndroidApp
class ExpenseTrackerApplication : Application() {

    @Inject
    lateinit var recurringWorkerInitializer: RecurringWorkerInitializer

    override fun onCreate() {
        super.onCreate()
        com.example.expensetracker.core.notifications.NotificationChannels.createChannels(this)
        recurringWorkerInitializer.initialize()
    }
}
