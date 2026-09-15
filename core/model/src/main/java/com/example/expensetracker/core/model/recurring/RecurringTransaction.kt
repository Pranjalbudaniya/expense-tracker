package com.example.expensetracker.core.model.recurring

import com.example.expensetracker.core.model.common.EntityId
import com.example.expensetracker.core.model.money.Money
import com.example.expensetracker.core.model.transaction.TransactionType
import java.time.LocalDate

enum class RecurrenceFrequency {
    DAILY,
    WEEKLY,
    BIWEEKLY,
    MONTHLY,
    YEARLY
}

data class RecurringTransaction(
    val id: EntityId,
    val amount: Money,
    val type: TransactionType,
    val sourceAccountId: EntityId,
    val destinationAccountId: EntityId? = null,
    val categoryId: EntityId? = null,
    val note: String = "",
    val frequency: RecurrenceFrequency,
    val nextOccurrence: LocalDate,
    val startDate: LocalDate = nextOccurrence,
    val endDate: LocalDate? = null,
    val isEnabled: Boolean = true,
    val lastGeneratedOccurrence: LocalDate? = null
)

/**
 * Deterministic calendar and occurrence calculation engine for recurring transactions.
 */
object RecurrenceCalculator {

    /**
     * Calculates the next occurrence date after [currentOccurrence] using [originalStartDate]
     * to preserve calendar anchor points (such as day-of-month and leap-day anchors).
     */
    fun calculateNextOccurrence(
        currentOccurrence: LocalDate,
        frequency: RecurrenceFrequency,
        originalStartDate: LocalDate = currentOccurrence
    ): LocalDate {
        return when (frequency) {
            RecurrenceFrequency.DAILY -> currentOccurrence.plusDays(1)
            RecurrenceFrequency.WEEKLY -> currentOccurrence.plusWeeks(1)
            RecurrenceFrequency.BIWEEKLY -> currentOccurrence.plusWeeks(2)
            RecurrenceFrequency.MONTHLY -> {
                val nextYearMonth = java.time.YearMonth.from(currentOccurrence).plusMonths(1)
                val targetDay = originalStartDate.dayOfMonth
                val maxDayInMonth = nextYearMonth.lengthOfMonth()
                nextYearMonth.atDay(minOf(targetDay, maxDayInMonth))
            }
            RecurrenceFrequency.YEARLY -> {
                val nextYear = currentOccurrence.year + 1
                val targetMonth = originalStartDate.month
                val targetDay = originalStartDate.dayOfMonth
                val targetYearMonth = java.time.YearMonth.of(nextYear, targetMonth)
                val maxDayInMonth = targetYearMonth.lengthOfMonth()
                targetYearMonth.atDay(minOf(targetDay, maxDayInMonth))
            }
        }
    }

    /**
     * Calculates the list of occurrences that are due as of [asOfDate] (inclusive),
     * starting from [recurring.nextOccurrence].
     */
    fun calculateDueOccurrences(
        recurring: RecurringTransaction,
        asOfDate: LocalDate,
        maxOccurrences: Int = 500
    ): List<LocalDate> {
        if (!recurring.isEnabled) return emptyList()

        val dueOccurrences = mutableListOf<LocalDate>()
        var cursor = recurring.nextOccurrence

        while (!cursor.isAfter(asOfDate) && dueOccurrences.size < maxOccurrences) {
            if (recurring.endDate != null && cursor.isAfter(recurring.endDate)) {
                break
            }

            val alreadyGenerated = recurring.lastGeneratedOccurrence?.let { last ->
                !cursor.isAfter(last)
            } ?: false

            if (!alreadyGenerated) {
                dueOccurrences.add(cursor)
            }

            cursor = calculateNextOccurrence(
                currentOccurrence = cursor,
                frequency = recurring.frequency,
                originalStartDate = recurring.startDate
            )
        }

        return dueOccurrences
    }

    /**
     * Returns true if the recurrence has at least one occurrence due on or before [asOfDate].
     */
    fun isDue(recurring: RecurringTransaction, asOfDate: LocalDate): Boolean {
        if (!recurring.isEnabled) return false
        if (recurring.nextOccurrence.isAfter(asOfDate)) return false
        if (recurring.endDate != null && recurring.nextOccurrence.isAfter(recurring.endDate)) return false
        return true
    }

    /**
     * Returns true if the recurring definition has an end date and the next occurrence has passed it.
     */
    fun isCompleted(recurring: RecurringTransaction): Boolean {
        return recurring.endDate != null && recurring.nextOccurrence.isAfter(recurring.endDate)
    }

    /**
     * Deterministic transaction ID generator for transactions generated from a recurrence.
     * Guarantees idempotency across app restarts or multi-worker executions.
     */
    fun generateOccurrenceTransactionId(recurringId: EntityId, occurrenceDate: LocalDate): EntityId {
        return EntityId("rec_tx_${recurringId.value}_${occurrenceDate}")
    }

    /**
     * Returns today's date in the given [ZoneId].
     */
    fun today(zoneId: java.time.ZoneId = java.time.ZoneId.systemDefault()): LocalDate {
        return LocalDate.now(zoneId)
    }
}


