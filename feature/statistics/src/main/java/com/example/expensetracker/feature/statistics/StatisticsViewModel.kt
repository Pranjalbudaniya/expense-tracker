package com.example.expensetracker.feature.statistics

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.expensetracker.core.data.repository.AccountRepository
import com.example.expensetracker.core.data.repository.CategoryRepository
import com.example.expensetracker.core.data.repository.TransactionRepository
import com.example.expensetracker.core.model.money.Currency
import com.example.expensetracker.core.preferences.PreferencesRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import java.time.LocalDate
import java.time.ZoneId
import javax.inject.Inject

/**
 * ViewModel managing analytics calculations, date-range filtering,
 * multi-currency isolation, and state transitions for the Statistics screen.
 */
@HiltViewModel
class StatisticsViewModel @Inject constructor(
    private val transactionRepository: TransactionRepository,
    private val categoryRepository: CategoryRepository,
    private val accountRepository: AccountRepository,
    private val preferencesRepository: PreferencesRepository,
    private val useCases: StatisticsUseCases
) : ViewModel() {

    private val _dateRange = MutableStateFlow(DateRangeFilter.fromPreset(DateRangePreset.THIS_MONTH))
    private val _selectedCurrency = MutableStateFlow<Currency?>(null)
    private val _isCustomDatePickerOpen = MutableStateFlow(false)
    private val _isCurrencyMenuOpen = MutableStateFlow(false)

    private val _filterState = combine(
        _dateRange,
        _selectedCurrency,
        _isCustomDatePickerOpen,
        _isCurrencyMenuOpen
    ) { dateRange, currency, isPickerOpen, isMenuOpen ->
        FilterState(dateRange, currency, isPickerOpen, isMenuOpen)
    }

    val uiState: StateFlow<StatisticsUiState> = combine(
        transactionRepository.getActiveTransactions(),
        categoryRepository.getAllCategories(),
        accountRepository.getAllAccounts(),
        preferencesRepository.userPreferences,
        _filterState
    ) { allTransactions, categories, accounts, userPreferences, filters ->
        val defaultCurrency = try {
            Currency.fromCode(userPreferences.currencyCode)
        } catch (_: Exception) {
            Currency.USD
        }

        val availableCurrencies = useCases.getAvailableCurrencies(allTransactions)
        val activeCurrency = filters.chosenCurrency
            ?: availableCurrencies.firstOrNull()?.currency
            ?: defaultCurrency

        val zoneId = ZoneId.systemDefault()
        val rangeFilteredTxs = useCases.filterByDateRange(allTransactions, filters.dateRange, zoneId)
        val currencyMatchingTxs = useCases.filterByCurrency(rangeFilteredTxs, activeCurrency)

        val excludedCount = rangeFilteredTxs.count { it.amount.currency != activeCurrency }
        val hasExcluded = excludedCount > 0

        val (totalIncome, totalExpense, netChange) = useCases.calculateTotals(
            transactions = currencyMatchingTxs,
            currency = activeCurrency
        )

        val categoriesMap = categories.associateBy { it.id }
        val accountsMap = accounts.associateBy { it.id }

        val categoryBreakdown = useCases.calculateSpendingByCategory(
            transactions = currencyMatchingTxs,
            categories = categoriesMap,
            currency = activeCurrency
        )

        val timeSeries = useCases.calculateTimeSeries(
            transactions = currencyMatchingTxs,
            dateRange = filters.dateRange,
            currency = activeCurrency,
            zoneId = zoneId
        )

        val largestExpense = useCases.calculateLargestExpense(
            transactions = currencyMatchingTxs,
            accounts = accountsMap,
            categories = categoriesMap,
            currency = activeCurrency,
            zoneId = zoneId
        )

        StatisticsUiState(
            isLoading = false,
            selectedCurrency = activeCurrency,
            availableCurrencies = availableCurrencies,
            hasExcludedCurrencies = hasExcluded,
            excludedCurrenciesCount = excludedCount,
            selectedDateRange = filters.dateRange,
            totalIncome = totalIncome,
            totalExpense = totalExpense,
            netChange = netChange,
            transactionCount = currencyMatchingTxs.size,
            categoryBreakdown = categoryBreakdown,
            timeSeries = timeSeries,
            largestExpense = largestExpense,
            isCustomDatePickerOpen = filters.isCustomDatePickerOpen,
            isCurrencyMenuOpen = filters.isCurrencyMenuOpen
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = StatisticsUiState(isLoading = true)
    )

    fun selectDateRangePreset(preset: DateRangePreset) {
        if (preset == DateRangePreset.CUSTOM) {
            _isCustomDatePickerOpen.value = true
        } else {
            _dateRange.value = DateRangeFilter.fromPreset(preset)
        }
    }

    fun setCustomDateRange(startDate: LocalDate, endDate: LocalDate) {
        val (start, end) = if (startDate.isAfter(endDate)) {
            Pair(endDate, startDate)
        } else {
            Pair(startDate, endDate)
        }
        _dateRange.value = DateRangeFilter(DateRangePreset.CUSTOM, start, end)
        _isCustomDatePickerOpen.value = false
    }

    fun dismissCustomDatePicker() {
        _isCustomDatePickerOpen.value = false
    }

    fun selectCurrency(currency: Currency) {
        _selectedCurrency.value = currency
        _isCurrencyMenuOpen.value = false
    }

    fun toggleCurrencyMenu(open: Boolean) {
        _isCurrencyMenuOpen.value = open
    }
}

private data class FilterState(
    val dateRange: DateRangeFilter,
    val chosenCurrency: Currency?,
    val isCustomDatePickerOpen: Boolean,
    val isCurrencyMenuOpen: Boolean
)

