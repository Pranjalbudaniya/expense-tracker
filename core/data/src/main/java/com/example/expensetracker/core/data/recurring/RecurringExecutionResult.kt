package com.example.expensetracker.core.data.recurring

import com.example.expensetracker.core.model.common.EntityId

/**
 * Details of a failure encountered while attempting to process a recurring transaction.
 *
 * @param recurringId The ID of the recurring transaction that encountered the failure.
 * @param reason Human-readable diagnostic description of why processing failed.
 * @param isTransient True if the error is recoverable (e.g. database lock, IO failure),
 *                    false if permanent (e.g. archived account/category reference, corrupt data).
 */
data class RecurringProcessingFailure(
    val recurringId: EntityId,
    val reason: String,
    val isTransient: Boolean = false
)

/**
 * Aggregated summary of an automatic recurring-transaction processing run.
 *
 * @param totalEvaluated Total number of recurring transaction definitions evaluated.
 * @param occurrencesCreated Total number of normal transactions successfully generated.
 * @param skippedDuplicates Number of occurrences that were skipped because they already existed.
 * @param skippedDisabledOrNotDue Number of recurring items skipped because they were paused or not due.
 * @param completedRecurrences Number of recurring items that reached their end date and were completed.
 * @param failures List of failures encountered during execution.
 */
data class RecurringExecutionResult(
    val totalEvaluated: Int,
    val occurrencesCreated: Int,
    val skippedDuplicates: Int,
    val skippedDisabledOrNotDue: Int,
    val completedRecurrences: Int,
    val failures: List<RecurringProcessingFailure> = emptyList()
) {
    /**
     * True if all evaluated items processed without any failures.
     */
    val isSuccess: Boolean get() = failures.isEmpty()

    /**
     * True if at least one recoverable transient failure occurred (e.g. IO or DB error),
     * indicating that the worker should be retried by WorkManager.
     */
    val hasTransientFailure: Boolean get() = failures.any { it.isTransient }

    /**
     * True if permanent failures occurred (e.g. archived account/category).
     */
    val hasPermanentFailure: Boolean get() = failures.any { !it.isTransient }
}
