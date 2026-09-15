package com.example.expensetracker.feature.statistics

import com.example.expensetracker.core.model.money.Currency
import com.example.expensetracker.core.model.money.Money
import java.math.BigDecimal

/**
 * UI State for the Statistics and Analytics screen.
 */
data class StatisticsUiState(
    val isLoading: Boolean = true,
    val selectedCurrency: Currency = Currency.USD,
    val availableCurrencies: List<CurrencyOption> = emptyList(),
    val hasExcludedCurrencies: Boolean = false,
    val excludedCurrenciesCount: Int = 0,
    val selectedDateRange: DateRangeFilter = DateRangeFilter.fromPreset(DateRangePreset.THIS_MONTH),
    val totalIncome: Money = Money(BigDecimal.ZERO, selectedCurrency),
    val totalExpense: Money = Money(BigDecimal.ZERO, selectedCurrency),
    val netChange: Money = Money(BigDecimal.ZERO, selectedCurrency),
    val transactionCount: Int = 0,
    val categoryBreakdown: List<CategorySpending> = emptyList(),
    val timeSeries: List<TimeSeriesPoint> = emptyList(),
    val largestExpense: LargestTransaction? = null,
    val isCustomDatePickerOpen: Boolean = false,
    val isCurrencyMenuOpen: Boolean = false
) {
    /**
     * True if no active transactions matched the selected date range and currency filter.
     */
    val isEmpty: Boolean get() = !isLoading && transactionCount == 0 && categoryBreakdown.isEmpty()
}
