package com.example.expensetracker.worker

import javax.inject.Inject
import javax.inject.Singleton

/**
 * Application-level initializer for recurring transaction background work.
 * Ensures WorkManager is scheduled once upon application startup outside Compose.
 */
@Singleton
class RecurringWorkerInitializer @Inject constructor(
    private val scheduler: RecurringWorkScheduler
) {

    /**
     * Initializes the background periodic work schedule.
     */
    fun initialize() {
        scheduler.schedulePeriodic()
    }
}
