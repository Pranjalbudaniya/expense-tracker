package com.example.expensetracker.feature.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.expensetracker.core.data.repository.AccountRepository
import com.example.expensetracker.core.data.repository.BudgetRepository
import com.example.expensetracker.core.data.repository.CategoryRepository
import com.example.expensetracker.core.data.repository.TransactionRepository
import com.example.expensetracker.core.model.money.Currency
import com.example.expensetracker.core.model.money.Money
import com.example.expensetracker.core.preferences.PreferencesRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
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
    private val preferencesRepository: PreferencesRepository,
    private val homeUseCases: HomeUseCases
) : ViewModel() {

    val uiState: StateFlow<HomeUiState> = combine(
        transactionRepository.getActiveTransactions(),
        accountRepository.getActiveAccounts(),
        budgetRepository.getActiveBudgets(),
        categoryRepository.getAllCategories(),
        preferencesRepository.userPreferences
    ) { transactions, accounts, budgets, categories, userPreferences ->
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

        HomeUiState(
            isLoading = false,
            primaryCurrency = primaryBalance.currency,
            primaryBalance = primaryBalance,
            additionalCurrencyBalances = additionalBalances,
            budgetSnapshots = budgetSnapshots,
            hasBudgets = budgets.any { it.isEnabled },
            recentTransactions = recentTransactions,
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
