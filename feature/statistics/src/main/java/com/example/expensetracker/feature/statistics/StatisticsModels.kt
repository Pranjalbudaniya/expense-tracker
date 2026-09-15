package com.example.expensetracker.feature.statistics

import com.example.expensetracker.core.model.common.EntityId
import com.example.expensetracker.core.model.money.Currency
import com.example.expensetracker.core.model.money.Money
import com.example.expensetracker.core.model.transaction.TransactionType
import java.math.BigDecimal
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId
import java.time.temporal.TemporalAdjusters
import java.time.format.DateTimeFormatter

/**
 * Preset date ranges supported by the analytics engine.
 */
enum class DateRangePreset(val label: String) {
    THIS_WEEK("This Week"),
    THIS_MONTH("This Month"),
    LAST_MONTH("Last Month"),
    LAST_3_MONTHS("Last 3 Months"),
    THIS_YEAR("This Year"),
    CUSTOM("Custom")
}

/**
 * Encapsulates a resolved date range with inclusive boundaries.
 */
data class DateRangeFilter(
    val preset: DateRangePreset,
    val startDate: LocalDate,
    val endDate: LocalDate
) {
    /**
     * Checks whether the given timestamp falls within [startDate] and [endDate] inclusively in [zoneId].
     */
    fun contains(instant: Instant, zoneId: ZoneId): Boolean {
        val date = instant.atZone(zoneId).toLocalDate()
        return !date.isBefore(startDate) && !date.isAfter(endDate)
    }

    companion object {
        fun fromPreset(preset: DateRangePreset, today: LocalDate = LocalDate.now()): DateRangeFilter {
            return when (preset) {
                DateRangePreset.THIS_WEEK -> {
                    val start = today.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
                    val end = today.with(TemporalAdjusters.nextOrSame(DayOfWeek.SUNDAY))
                    DateRangeFilter(preset, start, end)
                }
                DateRangePreset.THIS_MONTH -> {
                    val start = today.withDayOfMonth(1)
                    val end = today.withDayOfMonth(today.lengthOfMonth())
                    DateRangeFilter(preset, start, end)
                }
                DateRangePreset.LAST_MONTH -> {
                    val lastMonth = YearMonth.from(today).minusMonths(1)
                    DateRangeFilter(preset, lastMonth.atDay(1), lastMonth.atEndOfMonth())
                }
                DateRangePreset.LAST_3_MONTHS -> {
                    val threeMonthsAgo = YearMonth.from(today).minusMonths(2)
                    val end = today.withDayOfMonth(today.lengthOfMonth())
                    DateRangeFilter(preset, threeMonthsAgo.atDay(1), end)
                }
                DateRangePreset.THIS_YEAR -> {
                    val start = today.withDayOfYear(1)
                    val end = today.withDayOfYear(today.lengthOfYear())
                    DateRangeFilter(preset, start, end)
                }
                DateRangePreset.CUSTOM -> {
                    DateRangeFilter(preset, today.withDayOfMonth(1), today)
                }
            }
        }
    }
}

/**
 * Spending breakdown per category.
 */
data class CategorySpending(
    val categoryId: EntityId?,
    val categoryName: String,
    val iconKey: String,
    val colorKey: String,
    val amount: Money,
    val percentage: Float,
    val transactionCount: Int
)


/**
 * Extension helper to format Money for display in charts and labels.
 */
fun Money.toFormattedString(): String = "${currency.symbol}${amount.toPlainString()}"

/**
 * Data point in the spending and income time-series chart.
 */
data class TimeSeriesPoint(
    val label: String,
    val date: LocalDate,
    val totalExpense: Money,
    val totalIncome: Money
)

/**
 * Largest recorded expense in the active filter.
 */
data class LargestTransaction(
    val id: EntityId,
    val amount: Money,
    val type: TransactionType,
    val categoryName: String,
    val categoryColorKey: String = "category_gray",
    val accountName: String,
    val date: LocalDate,
    val note: String
) {
    val dateFormatted: String get() = date.format(DateTimeFormatter.ofPattern("MMM d, yyyy"))
}

/**
 * Currency option discovered in the active transaction store.
 */
data class CurrencyOption(
    val currency: Currency,
    val transactionCount: Int
)
