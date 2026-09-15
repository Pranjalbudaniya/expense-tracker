package com.example.expensetracker.core.data.recurring

import com.example.expensetracker.core.data.repository.AccountRepository
import com.example.expensetracker.core.data.repository.CategoryRepository
import com.example.expensetracker.core.data.repository.RecurringTransactionRepository
import com.example.expensetracker.core.data.repository.TransactionRepository
import com.example.expensetracker.core.model.common.EntityId
import com.example.expensetracker.core.model.recurring.RecurrenceCalculator
import com.example.expensetracker.core.model.recurring.RecurringTransaction
import com.example.expensetracker.core.model.transaction.Transaction
import com.example.expensetracker.core.model.transaction.TransactionType
import kotlinx.coroutines.flow.first
import java.io.IOException
import java.time.LocalDate
import java.time.ZoneId
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Deterministic, idempotent processor that evaluates due recurring transactions,
 * creates the corresponding normal transactions, advances recurrence schedules,
 * and handles calendar/leap-year boundaries and archived references safely.
 */
@Singleton
class RecurringTransactionProcessor @Inject constructor(
    private val recurringRepository: RecurringTransactionRepository,
    private val transactionRepository: TransactionRepository,
    private val accountRepository: AccountRepository,
    private val categoryRepository: CategoryRepository
) {

    /**
     * Processes all enabled recurring transactions due on or before [asOfDate].
     *
     * @param asOfDate The reference date (inclusive) to evaluate due occurrences against.
     * @param zoneId The time-zone used for occurrence timestamp conversion.
     * @return [RecurringExecutionResult] detailing execution metrics and any failures.
     */
    suspend fun processDueOccurrences(
        asOfDate: LocalDate = RecurrenceCalculator.today(),
        zoneId: ZoneId = ZoneId.systemDefault()
    ): RecurringExecutionResult {
        val allRecurring = try {
            recurringRepository.getAllRecurringTransactions().first()
        } catch (e: Exception) {
            val isTransient = e is IOException
            return RecurringExecutionResult(
                totalEvaluated = 0,
                occurrencesCreated = 0,
                skippedDuplicates = 0,
                skippedDisabledOrNotDue = 0,
                completedRecurrences = 0,
                failures = listOf(
                    RecurringProcessingFailure(
                        recurringId = EntityId("global"),
                        reason = "Failed to query recurring transactions: ${e.message}",
                        isTransient = isTransient
                    )
                )
            )
        }

        var totalEvaluated = 0
        var occurrencesCreated = 0
        var skippedDuplicates = 0
        var skippedDisabledOrNotDue = 0
        var completedRecurrences = 0
        val failures = mutableListOf<RecurringProcessingFailure>()

        for (recurring in allRecurring) {
            totalEvaluated++

            if (!recurring.isEnabled) {
                skippedDisabledOrNotDue++
                continue
            }

            if (!RecurrenceCalculator.isDue(recurring, asOfDate)) {
                // If the recurrence is past its end date, mark it disabled and completed.
                if (RecurrenceCalculator.isCompleted(recurring)) {
                    recurringRepository.updateRecurringTransaction(recurring.copy(isEnabled = false))
                    completedRecurrences++
                } else {
                    skippedDisabledOrNotDue++
                }
                continue
            }

            try {
                // Validate referenced entities safely before generating transactions.
                val validationError = validateReferences(recurring)
                if (validationError != null) {
                    failures.add(
                        RecurringProcessingFailure(
                            recurringId = recurring.id,
                            reason = validationError,
                            isTransient = false
                        )
                    )
                    continue
                }

                val dueDates = RecurrenceCalculator.calculateDueOccurrences(recurring, asOfDate)
                if (dueDates.isEmpty()) {
                    if (RecurrenceCalculator.isCompleted(recurring)) {
                        recurringRepository.updateRecurringTransaction(recurring.copy(isEnabled = false))
                        completedRecurrences++
                    } else {
                        skippedDisabledOrNotDue++
                    }
                    continue
                }

                var currentRecurring = recurring
                var recurrenceCompleted = false

                for (occurrenceDate in dueDates) {
                    val txId = RecurrenceCalculator.generateOccurrenceTransactionId(
                        recurringId = recurring.id,
                        occurrenceDate = occurrenceDate
                    )

                    // Idempotency check: verify if transaction with this deterministic ID already exists.
                    val existingTx = transactionRepository.getTransactionById(txId)
                    if (existingTx != null) {
                        skippedDuplicates++
                    } else {
                        val scheduledTimestamp = occurrenceDate.atStartOfDay(zoneId).toInstant()
                        val newTransaction = Transaction(
                            id = txId,
                            amount = recurring.amount,
                            type = recurring.type,
                            sourceAccountId = recurring.sourceAccountId,
                            destinationAccountId = recurring.destinationAccountId,
                            categoryId = recurring.categoryId,
                            timestamp = scheduledTimestamp,
                            note = recurring.note,
                            recurringTransactionId = recurring.id,
                            isDeleted = false
                        )
                        transactionRepository.insertTransaction(newTransaction)
                        occurrencesCreated++
                    }

                    // Advance recurrence state deterministically
                    val next = RecurrenceCalculator.calculateNextOccurrence(
                        currentOccurrence = occurrenceDate,
                        frequency = recurring.frequency,
                        originalStartDate = recurring.startDate
                    )

                    val isPastEndDate = recurring.endDate != null && next.isAfter(recurring.endDate)
                    currentRecurring = currentRecurring.copy(
                        lastGeneratedOccurrence = occurrenceDate,
                        nextOccurrence = next,
                        isEnabled = !isPastEndDate
                    )

                    if (isPastEndDate) {
                        recurrenceCompleted = true
                    }
                }

                // Update recurring state in database
                recurringRepository.updateRecurringTransaction(currentRecurring)
                if (recurrenceCompleted) {
                    completedRecurrences++
                }

            } catch (e: Exception) {
                val isTransient = e is IOException || (e.message?.contains("database", ignoreCase = true) == true)
                failures.add(
                    RecurringProcessingFailure(
                        recurringId = recurring.id,
                        reason = e.message ?: "Unexpected processing error",
                        isTransient = isTransient
                    )
                )
            }
        }

        return RecurringExecutionResult(
            totalEvaluated = totalEvaluated,
            occurrencesCreated = occurrencesCreated,
            skippedDuplicates = skippedDuplicates,
            skippedDisabledOrNotDue = skippedDisabledOrNotDue,
            completedRecurrences = completedRecurrences,
            failures = failures
        )
    }

    /**
     * Validates that all referenced accounts and categories exist and are not archived.
     * Returns an error description string if invalid, or null if valid.
     */
    private suspend fun validateReferences(recurring: RecurringTransaction): String? {
        val sourceAccount = accountRepository.getAccountById(recurring.sourceAccountId)
            ?: return "Source account does not exist"

        if (sourceAccount.isArchived) {
            return "Source account is archived"
        }

        if (recurring.type == TransactionType.TRANSFER) {
            val destId = recurring.destinationAccountId
                ?: return "Destination account is required for transfers"

            if (destId == recurring.sourceAccountId) {
                return "Transfer source and destination accounts must be distinct"
            }

            val destAccount = accountRepository.getAccountById(destId)
                ?: return "Destination account does not exist"

            if (destAccount.isArchived) {
                return "Destination account is archived"
            }
        }

        val categoryId = recurring.categoryId
        if (categoryId != null) {
            val category = categoryRepository.getCategoryById(categoryId)
                ?: return "Category does not exist"

            if (category.isArchived) {
                return "Category is archived"
            }
        }

        return null
    }
}
