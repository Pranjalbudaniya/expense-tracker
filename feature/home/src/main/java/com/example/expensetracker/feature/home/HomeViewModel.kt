package com.example.expensetracker.feature.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.expensetracker.core.data.repository.AccountRepository
import com.example.expensetracker.core.data.repository.BudgetRepository
import com.example.expensetracker.core.data.repository.CategoryRepository
import com.example.expensetracker.core.data.repository.RecurringTransactionRepository
import com.example.expensetracker.core.data.repository.TransactionRepository
import com.example.expensetracker.core.model.money.Currency
import com.example.expensetracker.core.model.money.Money
import com.example.expensetracker.core.model.recurring.RecurrenceFrequency
import com.example.expensetracker.core.preferences.PreferencesRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit
import javax.inject.Inject

/**
 * ViewModel responsible for observing financial repositories and exposing [HomeUiState].
 */
@HiltViewModel
class HomeViewModel @Inject constructor(
    private val transactionRepository: TransactionRepository,
    private val accountRepository: AccountRepository,
    private val budgetRepository: BudgetRepository,
    private val categoryRepository: CategoryRepository,
    private val recurringTransactionRepository: RecurringTransactionRepository,
    private val preferencesRepository: PreferencesRepository,
    private val homeUseCases: HomeUseCases
) : ViewModel() {

    val uiState: StateFlow<HomeUiState> = combine(
        combine(
            transactionRepository.getActiveTransactions(),
            accountRepository.getActiveAccounts(),
            budgetRepository.getActiveBudgets()
        ) { txs, accs, budgets -> Triple(txs, accs, budgets) },
        combine(
            categoryRepository.getAllCategories(),
            recurringTransactionRepository.getActiveRecurringTransactions(),
            preferencesRepository.userPreferences
        ) { cats, recurring, prefs -> Triple(cats, recurring, prefs) }
    ) { (transactions, accounts, budgets), (categories, recurringList, userPreferences) ->
        val preferredCurrency = try {
            Currency.fromCode(userPreferences.currencyCode)
        } catch (_: Exception) {
            accounts.firstOrNull()?.currency
                ?: transactions.firstOrNull()?.amount?.currency
                ?: Currency.INR
        }

        val currencyBalances = homeUseCases.calculateCurrencyBalances(
            accounts = accounts,
            transactions = transactions,
            preferredCurrency = preferredCurrency
        )

        val primaryBalance = currencyBalances.firstOrNull() ?: CurrencyBalanceItem(
            currency = preferredCurrency,
            totalBalance = Money.zero(preferredCurrency),
            totalIncome = Money.zero(preferredCurrency),
            totalExpenses = Money.zero(preferredCurrency)
        )

        val additionalBalances = if (currencyBalances.size > 1) {
            currencyBalances.drop(1)
        } else {
            emptyList()
        }

        val categoriesMap = categories.associateBy { it.id }
        val accountsMap = accounts.associateBy { it.id }

        val recentTransactions = homeUseCases.getRecentTransactions(
            transactions = transactions,
            accounts = accountsMap,
            categories = categoriesMap,
            limit = 5
        )

        val budgetSnapshots = homeUseCases.calculateBudgetSnapshots(
            budgets = budgets,
            transactions = transactions
        )

        val spendingSnapshot = homeUseCases.calculateSpendingSnapshot(
            transactions = transactions,
            categories = categoriesMap,
            currency = primaryBalance.currency
        )

        val today = LocalDate.now()
        val dateFormatter = DateTimeFormatter.ofPattern("MMM dd")
        val fullDateFormatter = DateTimeFormatter.ofPattern("MMM dd, yyyy")

        val recurringItems = recurringList
            .filter { it.isEnabled && (it.endDate == null || !it.nextOccurrence.isAfter(it.endDate)) }
            .sortedBy { it.nextOccurrence }
            .map { rec ->
                val category = rec.categoryId?.let { categoriesMap[it] }
                val daysUntil = ChronoUnit.DAYS.between(today, rec.nextOccurrence)
                val dueStatusText = when {
                    daysUntil < 0 -> "Overdue"
                    daysUntil == 0L -> "Due Today"
                    daysUntil == 1L -> "Due Tomorrow"
                    daysUntil in 2..7 -> "Due in $daysUntil days"
                    else -> rec.nextOccurrence.format(dateFormatter)
                }

                val frequencyLabel = when (rec.frequency) {
                    RecurrenceFrequency.DAILY -> "Daily"
                    RecurrenceFrequency.WEEKLY -> "Weekly"
                    RecurrenceFrequency.BIWEEKLY -> "Bi-weekly"
                    RecurrenceFrequency.MONTHLY -> "Monthly"
                    RecurrenceFrequency.YEARLY -> "Yearly"
                }

                HomeRecurringItem(
                    id = rec.id,
                    amount = rec.amount,
                    type = rec.type,
                    frequency = rec.frequency,
                    frequencyLabel = frequencyLabel,
                    nextOccurrence = rec.nextOccurrence,
                    nextOccurrenceFormatted = rec.nextOccurrence.format(fullDateFormatter),
                    dueStatusText = dueStatusText,
                    isDue = daysUntil <= 0,
                    categoryName = category?.name ?: "Recurring",
                    categoryColorKey = category?.colorKey ?: "category_blue",
                    categoryIconKey = category?.iconKey ?: "date_range",
                    note = rec.note.ifBlank { category?.name ?: "Recurring Payment" }
                )
            }

        HomeUiState(
            isLoading = false,
            primaryCurrency = primaryBalance.currency,
            primaryBalance = primaryBalance,
            additionalCurrencyBalances = additionalBalances,
            budgetSnapshots = budgetSnapshots,
            hasBudgets = budgets.any { it.isEnabled },
            recentTransactions = recentTransactions,
            recurringTransactions = recurringItems,
            spendingSnapshot = spendingSnapshot,
            errorMessage = null
        )
    }.catch { throwable ->
        emit(
            HomeUiState(
                isLoading = false,
                errorMessage = throwable.localizedMessage ?: "Failed to load home data"
            )
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = HomeUiState(isLoading = true)
    )
}

