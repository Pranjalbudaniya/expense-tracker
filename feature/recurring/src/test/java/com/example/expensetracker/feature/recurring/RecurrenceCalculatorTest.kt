package com.example.expensetracker.feature.recurring

import com.example.expensetracker.core.model.common.EntityId
import com.example.expensetracker.core.model.money.Currency
import com.example.expensetracker.core.model.money.Money
import com.example.expensetracker.core.model.recurring.RecurrenceFrequency
import com.example.expensetracker.core.model.recurring.RecurringTransaction
import com.example.expensetracker.core.model.transaction.TransactionType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.math.BigDecimal
import java.time.LocalDate

class RecurrenceCalculatorTest {

    @Test
    fun daily_advancesByOneDay() {
        val date1 = LocalDate.of(2026, 1, 1)
        val next1 = RecurrenceCalculator.calculateNextOccurrence(date1, RecurrenceFrequency.DAILY)
        assertEquals(LocalDate.of(2026, 1, 2), next1)

        val dateMonthEnd = LocalDate.of(2026, 1, 31)
        val nextMonth = RecurrenceCalculator.calculateNextOccurrence(dateMonthEnd, RecurrenceFrequency.DAILY)
        assertEquals(LocalDate.of(2026, 2, 1), nextMonth)
    }

    @Test
    fun weekly_advancesBySevenDays() {
        val start = LocalDate.of(2026, 3, 1)
        val next = RecurrenceCalculator.calculateNextOccurrence(start, RecurrenceFrequency.WEEKLY)
        assertEquals(LocalDate.of(2026, 3, 8), next)
    }

    @Test
    fun biweekly_advancesByFourteenDays() {
        val start = LocalDate.of(2026, 3, 1)
        val next = RecurrenceCalculator.calculateNextOccurrence(start, RecurrenceFrequency.BIWEEKLY)
        assertEquals(LocalDate.of(2026, 3, 15), next)
    }

    @Test
    fun monthly_standardDateAdvancesOneMonth() {
        val start = LocalDate.of(2026, 1, 15)
        val feb = RecurrenceCalculator.calculateNextOccurrence(start, RecurrenceFrequency.MONTHLY, start)
        assertEquals(LocalDate.of(2026, 2, 15), feb)

        val mar = RecurrenceCalculator.calculateNextOccurrence(feb, RecurrenceFrequency.MONTHLY, start)
        assertEquals(LocalDate.of(2026, 3, 15), mar)
    }

    @Test
    fun monthly_january31ToFebruaryNonLeapYear_clampsTo28AndRestores31InMarch() {
        val jan31 = LocalDate.of(2026, 1, 31)

        // Jan 31 -> Feb 28 (clamps to Feb end)
        val feb = RecurrenceCalculator.calculateNextOccurrence(jan31, RecurrenceFrequency.MONTHLY, jan31)
        assertEquals(LocalDate.of(2026, 2, 28), feb)

        // Feb 28 -> Mar 31 (retains original 31st anchor!)
        val mar = RecurrenceCalculator.calculateNextOccurrence(feb, RecurrenceFrequency.MONTHLY, jan31)
        assertEquals(LocalDate.of(2026, 3, 31), mar)

        // Mar 31 -> Apr 30 (clamps to Apr end)
        val apr = RecurrenceCalculator.calculateNextOccurrence(mar, RecurrenceFrequency.MONTHLY, jan31)
        assertEquals(LocalDate.of(2026, 4, 30), apr)

        // Apr 30 -> May 31 (retains original 31st anchor!)
        val may = RecurrenceCalculator.calculateNextOccurrence(apr, RecurrenceFrequency.MONTHLY, jan31)
        assertEquals(LocalDate.of(2026, 5, 31), may)
    }

    @Test
    fun monthly_january31InLeapYear_clampsTo29() {
        val jan31Leap = LocalDate.of(2024, 1, 31)
        val febLeap = RecurrenceCalculator.calculateNextOccurrence(jan31Leap, RecurrenceFrequency.MONTHLY, jan31Leap)
        assertEquals(LocalDate.of(2024, 2, 29), febLeap)
    }

    @Test
    fun yearly_standardDateAdvancesOneYear() {
        val start = LocalDate.of(2026, 6, 15)
        val next = RecurrenceCalculator.calculateNextOccurrence(start, RecurrenceFrequency.YEARLY, start)
        assertEquals(LocalDate.of(2027, 6, 15), next)
    }

    @Test
    fun yearly_leapDayFebruary29_safelyHandlesNonLeapYearsAndPreservesLeapDay() {
        val feb29_2024 = LocalDate.of(2024, 2, 29)

        // 2024 (leap) -> 2025 (non-leap: Feb 28)
        val feb_2025 = RecurrenceCalculator.calculateNextOccurrence(feb29_2024, RecurrenceFrequency.YEARLY, feb29_2024)
        assertEquals(LocalDate.of(2025, 2, 28), feb_2025)

        // 2025 -> 2026 (non-leap: Feb 28)
        val feb_2026 = RecurrenceCalculator.calculateNextOccurrence(feb_2025, RecurrenceFrequency.YEARLY, feb29_2024)
        assertEquals(LocalDate.of(2026, 2, 28), feb_2026)

        // 2026 -> 2027 (non-leap: Feb 28)
        val feb_2027 = RecurrenceCalculator.calculateNextOccurrence(feb_2026, RecurrenceFrequency.YEARLY, feb29_2024)
        assertEquals(LocalDate.of(2027, 2, 28), feb_2027)

        // 2027 -> 2028 (leap year: Feb 29 preserved!)
        val feb_2028 = RecurrenceCalculator.calculateNextOccurrence(feb_2027, RecurrenceFrequency.YEARLY, feb29_2024)
        assertEquals(LocalDate.of(2028, 2, 29), feb_2028)
    }

    @Test
    fun isDue_checksDateAndEnabledStatus() {
        val rec = createRecurring(
            nextOccurrence = LocalDate.of(2026, 5, 10),
            isEnabled = true
        )

        assertFalse("Not due before occurrence date", RecurrenceCalculator.isDue(rec, LocalDate.of(2026, 5, 9)))
        assertTrue("Due on occurrence date", RecurrenceCalculator.isDue(rec, LocalDate.of(2026, 5, 10)))
        assertTrue("Due after occurrence date", RecurrenceCalculator.isDue(rec, LocalDate.of(2026, 5, 11)))

        val disabled = rec.copy(isEnabled = false)
        assertFalse("Disabled recurrence is never due", RecurrenceCalculator.isDue(disabled, LocalDate.of(2026, 5, 15)))

        val completed = rec.copy(
            nextOccurrence = LocalDate.of(2026, 5, 10),
            endDate = LocalDate.of(2026, 5, 1)
        )
        assertFalse("Recurrence past end date is not due", RecurrenceCalculator.isDue(completed, LocalDate.of(2026, 5, 15)))
    }

    @Test
    fun calculateDueOccurrences_returnsAllSuccessiveOccurrencesUntilAsOfDate() {
        val rec = createRecurring(
            startDate = LocalDate.of(2026, 1, 1),
            nextOccurrence = LocalDate.of(2026, 1, 1),
            frequency = RecurrenceFrequency.MONTHLY
        )

        // User opens app on April 5, 2026
        val dueList = RecurrenceCalculator.calculateDueOccurrences(rec, LocalDate.of(2026, 4, 5))

        assertEquals(
            listOf(
                LocalDate.of(2026, 1, 1),
                LocalDate.of(2026, 2, 1),
                LocalDate.of(2026, 3, 1),
                LocalDate.of(2026, 4, 1)
            ),
            dueList
        )
    }

    @Test
    fun calculateDueOccurrences_respectsEndDateBoundary() {
        val rec = createRecurring(
            startDate = LocalDate.of(2026, 1, 1),
            nextOccurrence = LocalDate.of(2026, 1, 1),
            frequency = RecurrenceFrequency.MONTHLY,
            endDate = LocalDate.of(2026, 2, 15)
        )

        val dueList = RecurrenceCalculator.calculateDueOccurrences(rec, LocalDate.of(2026, 5, 1))

        assertEquals(
            listOf(
                LocalDate.of(2026, 1, 1),
                LocalDate.of(2026, 2, 1)
            ),
            dueList
        )
    }

    @Test
    fun calculateDueOccurrences_skipsAlreadyGeneratedOccurrences() {
        val rec = createRecurring(
            startDate = LocalDate.of(2026, 1, 1),
            nextOccurrence = LocalDate.of(2026, 1, 1),
            frequency = RecurrenceFrequency.MONTHLY,
            lastGenerated = LocalDate.of(2026, 2, 1) // Jan 1 & Feb 1 were generated
        )

        val dueList = RecurrenceCalculator.calculateDueOccurrences(rec, LocalDate.of(2026, 4, 1))

        assertEquals(
            listOf(
                LocalDate.of(2026, 3, 1),
                LocalDate.of(2026, 4, 1)
            ),
            dueList
        )
    }

    @Test
    fun generateOccurrenceTransactionId_isDeterministicAndUniquePerOccurrence() {
        val id = EntityId("rec-sub-1")
        val date1 = LocalDate.of(2026, 4, 1)
        val date2 = LocalDate.of(2026, 5, 1)

        val txId1 = RecurrenceCalculator.generateOccurrenceTransactionId(id, date1)
        val txId1Duplicate = RecurrenceCalculator.generateOccurrenceTransactionId(id, date1)
        val txId2 = RecurrenceCalculator.generateOccurrenceTransactionId(id, date2)

        assertEquals("Same recurring and date produce identical transaction ID", txId1, txId1Duplicate)
        assertTrue("Different dates produce different IDs", txId1 != txId2)
        assertEquals("rec_tx_rec-sub-1_2026-04-01", txId1.value)
    }

    private fun createRecurring(
        startDate: LocalDate = LocalDate.of(2026, 1, 1),
        nextOccurrence: LocalDate = startDate,
        frequency: RecurrenceFrequency = RecurrenceFrequency.MONTHLY,
        endDate: LocalDate? = null,
        isEnabled: Boolean = true,
        lastGenerated: LocalDate? = null
    ): RecurringTransaction {
        return RecurringTransaction(
            id = EntityId("rec-test-1"),
            amount = Money(BigDecimal("50.00"), Currency.USD),
            type = TransactionType.EXPENSE,
            sourceAccountId = EntityId("acc-1"),
            frequency = frequency,
            startDate = startDate,
            nextOccurrence = nextOccurrence,
            endDate = endDate,
            isEnabled = isEnabled,
            lastGeneratedOccurrence = lastGenerated
        )
    }
}
