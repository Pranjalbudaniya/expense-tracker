package com.example.expensetracker.feature.budgets

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.expensetracker.core.common.time.DateTimeProvider
import com.example.expensetracker.core.common.time.DefaultDateTimeProvider
import com.example.expensetracker.core.data.repository.BudgetRepository
import com.example.expensetracker.core.data.repository.CategoryRepository
import com.example.expensetracker.core.data.repository.TransactionRepository
import com.example.expensetracker.core.model.budget.Budget
import com.example.expensetracker.core.model.common.EntityId
import com.example.expensetracker.core.model.money.Currency
import com.example.expensetracker.core.preferences.PreferencesRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.util.UUID
import javax.inject.Inject

/**
 * ViewModel managing budget list observation, progress calculations, and budget CRUD operations.
 */
@HiltViewModel
class BudgetsViewModel @Inject constructor(
    private val budgetRepository: BudgetRepository,
    private val transactionRepository: TransactionRepository,
    private val categoryRepository: CategoryRepository,
    private val preferencesRepository: PreferencesRepository,
    private val budgetUseCases: BudgetUseCases
) : ViewModel() {

    private var dateTimeProvider: DateTimeProvider = DefaultDateTimeProvider()

    internal constructor(
        budgetRepository: BudgetRepository,
        transactionRepository: TransactionRepository,
        categoryRepository: CategoryRepository,
        preferencesRepository: PreferencesRepository,
        dateTimeProvider: DateTimeProvider,
        budgetUseCases: BudgetUseCases
    ) : this(
        budgetRepository = budgetRepository,
        transactionRepository = transactionRepository,
        categoryRepository = categoryRepository,
        preferencesRepository = preferencesRepository,
        budgetUseCases = budgetUseCases
    ) {
        this.dateTimeProvider = dateTimeProvider
    }

    private val formStateFlow = MutableStateFlow<BudgetFormState?>(null)
    private val deleteConfirmationFlow = MutableStateFlow<Budget?>(null)
    private val _undoDeleteBudgetEvent = MutableStateFlow<Budget?>(null)
    val undoDeleteBudgetEvent: StateFlow<Budget?> = _undoDeleteBudgetEvent

    // Currencies supported in the application
    private val supportedCurrencies = listOf(
        Currency.INR,
        Currency.USD,
        Currency.EUR,
        Currency.GBP,
        Currency.JPY,
        Currency(code = "CAD", symbol = "CA$", displayName = "Canadian Dollar"),
        Currency(code = "AUD", symbol = "A$", displayName = "Australian Dollar"),
        Currency(code = "CHF", symbol = "CHF", displayName = "Swiss Franc"),
        Currency(code = "CNY", symbol = "CN¥", displayName = "Chinese Yuan"),
        Currency(code = "SGD", symbol = "S$", displayName = "Singapore Dollar")
    )

    val uiState: StateFlow<BudgetsUiState> = combine(
        budgetRepository.getAllBudgets(),
        transactionRepository.getActiveTransactions(),
        categoryRepository.getAllCategories(),
        formStateFlow,
        deleteConfirmationFlow
    ) { budgets, transactions, categories, formState, deleteConfirmation ->
        val zone = dateTimeProvider.currentZoneId()
        val categoryMap = categories.associateBy { it.id }

        val budgetItems = budgets.map { budget ->
            val category = budget.categoryId?.let { categoryMap[it] }
            budgetUseCases.calculateBudgetProgress(
                budget = budget,
                transactions = transactions,
                category = category,
                zoneId = zone
            )
        }

        // Active categories for the form selector
        val activeCategories = categories.filter { !it.isArchived }

        BudgetsUiState(
            budgets = budgetItems,
            availableCategories = activeCategories,
            availableCurrencies = supportedCurrencies,
            isFormOpen = formState != null,
            formState = formState,
            isLoading = false,
            deleteConfirmationBudget = deleteConfirmation
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = BudgetsUiState(isLoading = true)
    )

    /**
     * Opens the budget form for creating a new budget.
     */
    fun openCreateForm(preselectedCategoryId: EntityId? = null) {
        val today = dateTimeProvider.today()
        val firstDayOfMonth = today.withDayOfMonth(1)
        val lastDayOfMonth = today.withDayOfMonth(today.lengthOfMonth())

        formStateFlow.value = BudgetFormState(
            budgetId = null,
            name = "",
            amountInput = "",
            currency = Currency.INR,
            isCategorySpecific = preselectedCategoryId != null,
            selectedCategoryId = preselectedCategoryId,
            startDate = firstDayOfMonth,
            endDate = lastDayOfMonth,
            isEnabled = true
        )
    }

    /**
     * Opens the budget form for editing an existing budget.
     */
    fun openEditForm(budget: Budget) {
        formStateFlow.value = BudgetFormState(
            budgetId = budget.id,
            name = budget.name,
            amountInput = budget.targetAmount.amount.toPlainString(),
            currency = budget.targetAmount.currency,
            isCategorySpecific = budget.categoryId != null,
            selectedCategoryId = budget.categoryId,
            startDate = budget.startDate,
            endDate = budget.endDate,
            isEnabled = budget.isEnabled
        )
    }

    /**
     * Closes the active budget form.
     */
    fun closeForm() {
        formStateFlow.value = null
    }

    fun onFormNameChange(name: String) {
        formStateFlow.update { it?.copy(name = name) }
    }

    fun onFormAmountChange(amount: String) {
        formStateFlow.update { it?.copy(amountInput = amount, amountError = null) }
    }

    fun onFormCurrencyChange(currency: Currency) {
        formStateFlow.update { it?.copy(currency = currency, amountError = null) }
    }

    fun onFormTypeChange(isCategorySpecific: Boolean) {
        formStateFlow.update {
            it?.copy(
                isCategorySpecific = isCategorySpecific,
                selectedCategoryId = if (!isCategorySpecific) null else it.selectedCategoryId,
                categoryError = null
            )
        }
    }

    fun onFormCategoryChange(categoryId: EntityId?) {
        formStateFlow.update { it?.copy(selectedCategoryId = categoryId, categoryError = null) }
    }

    fun onFormStartDateChange(startDate: LocalDate) {
        formStateFlow.update { it?.copy(startDate = startDate, dateError = null) }
    }

    fun onFormEndDateChange(endDate: LocalDate) {
        formStateFlow.update { it?.copy(endDate = endDate, dateError = null) }
    }

    fun onFormEnabledChange(isEnabled: Boolean) {
        formStateFlow.update { it?.copy(isEnabled = isEnabled) }
    }

    /**
     * Validates and commits the budget to repository.
     */
    fun saveBudget() {
        val form = formStateFlow.value ?: return

        val categoryNameFallback = form.selectedCategoryId?.let { catId ->
            uiState.value.availableCategories.find { it.id == catId }?.name
        }

        val id = form.budgetId ?: EntityId(UUID.randomUUID().toString())

        val result = budgetUseCases.validateAndCreateBudget(
            id = id,
            name = form.name,
            amountInput = form.amountInput,
            currency = form.currency,
            isCategorySpecific = form.isCategorySpecific,
            selectedCategoryId = form.selectedCategoryId,
            startDate = form.startDate,
            endDate = form.endDate,
            isEnabled = form.isEnabled,
            categoryNameFallback = categoryNameFallback
        )

        when (result) {
            is BudgetUseCases.FormValidationResult.Success -> {
                viewModelScope.launch {
                    if (form.isEditing) {
                        budgetRepository.updateBudget(result.budget)
                    } else {
                        budgetRepository.insertBudget(result.budget)
                    }
                    closeForm()
                }
            }
            is BudgetUseCases.FormValidationResult.Failure -> {
                formStateFlow.update {
                    it?.copy(
                        amountError = result.amountError,
                        dateError = result.dateError,
                        categoryError = result.categoryError
                    )
                }
            }
        }
    }

    fun showDeleteConfirmation(budget: Budget) {
        deleteConfirmationFlow.value = budget
    }

    fun dismissDeleteConfirmation() {
        deleteConfirmationFlow.value = null
    }

    fun confirmDeleteBudget() {
        val budget = deleteConfirmationFlow.value ?: return
        viewModelScope.launch {
            budgetRepository.deleteBudget(budget)
            _undoDeleteBudgetEvent.value = budget
            dismissDeleteConfirmation()
        }
    }

    fun undoDeleteBudget(budget: Budget) {
        viewModelScope.launch {
            budgetRepository.insertBudget(budget)
            _undoDeleteBudgetEvent.value = null
        }
    }

    fun clearUndoDeleteBudgetEvent() {
        _undoDeleteBudgetEvent.value = null
    }

    fun toggleBudgetEnabled(budget: Budget) {
        viewModelScope.launch {
            budgetRepository.updateBudget(budget.copy(isEnabled = !budget.isEnabled))
        }
    }
}
