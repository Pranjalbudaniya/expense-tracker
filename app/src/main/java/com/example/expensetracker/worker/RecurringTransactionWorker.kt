package com.example.expensetracker.worker

import android.content.Context
import android.util.Log
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.example.expensetracker.core.data.recurring.RecurringTransactionProcessor
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Periodic WorkManager worker that executes due recurring transactions in the background.
 */
class RecurringTransactionWorker(
    appContext: Context,
    workerParams: WorkerParameters,
    private val processorOverride: RecurringTransactionProcessor? = null
) : CoroutineWorker(appContext, workerParams) {

    @EntryPoint
    @InstallIn(SingletonComponent::class)
    interface RecurringWorkerEntryPoint {
        fun recurringTransactionProcessor(): RecurringTransactionProcessor
    }

    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        val processor = processorOverride ?: try {
            EntryPointAccessors.fromApplication(
                applicationContext,
                RecurringWorkerEntryPoint::class.java
            ).recurringTransactionProcessor()
        } catch (e: Exception) {
            Log.e(TAG, "Failed to resolve RecurringTransactionProcessor from application EntryPoint", e)
            return@withContext Result.failure()
        }

        executeRecurringWork(processor)
    }

    companion object {
        const val TAG = "RecurringTxWorker"
        const val WORK_NAME = "RecurringTransactionWork"

        suspend fun executeRecurringWork(
            processor: RecurringTransactionProcessor,
            asOfDate: java.time.LocalDate = com.example.expensetracker.core.model.recurring.RecurrenceCalculator.today(),
            zoneId: java.time.ZoneId = java.time.ZoneId.systemDefault()
        ): Result {
            return try {
                Log.d(TAG, "Starting periodic recurring transaction processing")
                val result = processor.processDueOccurrences(asOfDate = asOfDate, zoneId = zoneId)

                Log.i(
                    TAG,
                    "Recurring run finished: evaluated=${result.totalEvaluated}, created=${result.occurrencesCreated}, skippedDuplicates=${result.skippedDuplicates}, skippedNotDue=${result.skippedDisabledOrNotDue}, completed=${result.completedRecurrences}, failures=${result.failures.size}"
                )

                if (result.hasTransientFailure) {
                    Log.w(TAG, "Recoverable transient failure occurred. Scheduling retry.")
                    Result.retry()
                } else {
                    Result.success()
                }
            } catch (e: Exception) {
                Log.e(TAG, "Execution failed with unexpected exception", e)
                Result.retry()
            }
        }
    }
}
