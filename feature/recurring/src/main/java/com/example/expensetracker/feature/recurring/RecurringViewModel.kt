package com.example.expensetracker.feature.recurring

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.expensetracker.core.data.repository.AccountRepository
import com.example.expensetracker.core.data.repository.CategoryRepository
import com.example.expensetracker.core.data.repository.RecurringTransactionRepository
import com.example.expensetracker.core.model.common.EntityId
import com.example.expensetracker.core.model.money.Currency
import com.example.expensetracker.core.model.recurring.RecurrenceFrequency
import com.example.expensetracker.core.model.recurring.RecurringTransaction
import com.example.expensetracker.core.model.transaction.TransactionType
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

private data class LocalRecurringState(
    val selectedTab: Int = 0,
    val isFormOpen: Boolean = false,
    val formState: RecurringFormState? = null,
    val recurringToDelete: RecurringTransaction? = null,
    val errorMessage: String? = null
)

/**
 * ViewModel for managing recurring transactions, validation, and recurrence calculation.
 */
@HiltViewModel
class RecurringViewModel @Inject constructor(
    private val recurringRepository: RecurringTransactionRepository,
    private val accountRepository: AccountRepository,
    private val categoryRepository: CategoryRepository
) : ViewModel() {

    private val localState = MutableStateFlow(LocalRecurringState())

    val uiState: StateFlow<RecurringUiState> = combine(
        recurringRepository.getAllRecurringTransactions(),
        accountRepository.getAllAccounts(),
        categoryRepository.getAllCategories(),
        localState
    ) { recurringList, accounts, categories, local ->
        val today = RecurrenceCalculator.today()
        val accountMap = accounts.associateBy { it.id }
        val categoryMap = categories.associateBy { it.id }

        val activeList = mutableListOf<RecurringItemUiState>()
        val pausedList = mutableListOf<RecurringItemUiState>()

        for (rec in recurringList) {
            val src = accountMap[rec.sourceAccountId]
            val dest = rec.destinationAccountId?.let { accountMap[it] }
            val cat = rec.categoryId?.let { categoryMap[it] }

            val isDue = RecurrenceCalculator.isDue(rec, today)
            val isCompleted = RecurrenceCalculator.isCompleted(rec)

            val item = RecurringItemUiState(
                recurring = rec,
                sourceAccountName = src?.name ?: "Unknown Account",
                destinationAccountName = dest?.name,
                categoryName = cat?.name,
                categoryIconKey = cat?.iconKey,
                categoryColorKey = cat?.colorKey,
                isSourceArchived = src?.isArchived == true,
                isDestinationArchived = dest?.isArchived == true,
                isCategoryArchived = cat?.isArchived == true,
                isDue = isDue,
                isCompleted = isCompleted
            )

            if (rec.isEnabled && !isCompleted) {
                activeList.add(item)
            } else {
                pausedList.add(item)
            }
        }

        // Available currencies from user accounts, fallback to standard set
        val currencies = (accounts.map { it.initialBalance.currency } + listOf(Currency.INR, Currency.USD, Currency.EUR))
            .distinctBy { it.code }

        RecurringUiState(
            isLoading = false,
            activeItems = activeList,
            pausedItems = pausedList,
            accounts = accounts,
            categories = categories,
            currencies = currencies,
            selectedTab = local.selectedTab,
            isFormOpen = local.isFormOpen,
            formState = local.formState,
            recurringToDelete = local.recurringToDelete,
            errorMessage = local.errorMessage
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = RecurringUiState(isLoading = true)
    )

    fun selectTab(tab: Int) {
        localState.update { it.copy(selectedTab = tab) }
    }

    fun openCreateForm() {
        val currentAccounts = uiState.value.accounts
        val activeAccounts = currentAccounts.filter { !it.isArchived }
        val defaultSrc = activeAccounts.firstOrNull() ?: currentAccounts.firstOrNull()
        val defaultCurrency = defaultSrc?.initialBalance?.currency ?: Currency.INR
        val today = RecurrenceCalculator.today()

        val form = RecurringFormState(
            currency = defaultCurrency,
            type = TransactionType.EXPENSE,
            sourceAccountId = defaultSrc?.id,
            frequency = RecurrenceFrequency.MONTHLY,
            startDate = today,
            nextOccurrence = today,
            hasEndDate = false,
            endDate = null,
            isEnabled = true
        )

        localState.update {
            it.copy(
                isFormOpen = true,
                formState = form
            )
        }
    }

    fun openEditForm(recurring: RecurringTransaction) {
        val form = RecurringFormState(
            id = recurring.id,
            amountText = recurring.amount.amount.toPlainString(),
            currency = recurring.amount.currency,
            type = recurring.type,
            sourceAccountId = recurring.sourceAccountId,
            destinationAccountId = recurring.destinationAccountId,
            categoryId = recurring.categoryId,
            note = recurring.note,
            frequency = recurring.frequency,
            startDate = recurring.startDate,
            nextOccurrence = recurring.nextOccurrence,
            hasEndDate = recurring.endDate != null,
            endDate = recurring.endDate,
            isEnabled = recurring.isEnabled
        )

        localState.update {
            it.copy(
                isFormOpen = true,
                formState = form
            )
        }
    }

    fun closeForm() {
        localState.update {
            it.copy(
                isFormOpen = false,
                formState = null
            )
        }
    }

    fun onFormAmountChange(text: String) {
        localState.update { state ->
            state.copy(formState = state.formState?.copy(amountText = text, amountError = null))
        }
    }

    fun onFormCurrencyChange(currency: Currency) {
        localState.update { state ->
            state.copy(formState = state.formState?.copy(currency = currency, amountError = null))
        }
    }

    fun onFormTypeChange(type: TransactionType) {
        localState.update { state ->
            state.copy(
                formState = state.formState?.copy(
                    type = type,
                    destinationAccountId = if (type == TransactionType.TRANSFER) state.formState.destinationAccountId else null,
                    categoryId = if (type == TransactionType.TRANSFER) null else state.formState.categoryId,
                    sourceAccountError = null,
                    destinationAccountError = null
                )
            )
        }
    }

    fun onFormSourceAccountChange(accountId: EntityId) {
        localState.update { state ->
            val form = state.formState ?: return@update state
            val account = uiState.value.accounts.firstOrNull { it.id == accountId }
            val dest = uiState.value.accounts.firstOrNull { it.id == form.destinationAccountId }
            val destErr = if (form.type == TransactionType.TRANSFER && form.destinationAccountId != null) {
                if (form.destinationAccountId == accountId) {
                    "Source and destination accounts must be different"
                } else if (account != null && dest != null && !account.initialBalance.currency.code.equals(dest.initialBalance.currency.code, ignoreCase = true)) {
                    "Source and destination accounts must have the same currency"
                } else null
            } else null

            state.copy(
                formState = form.copy(
                    sourceAccountId = accountId,
                    currency = account?.initialBalance?.currency ?: form.currency,
                    sourceAccountError = null,
                    destinationAccountError = destErr
                )
            )
        }
    }

    fun onFormDestinationAccountChange(accountId: EntityId?) {
        localState.update { state ->
            val form = state.formState ?: return@update state
            val src = uiState.value.accounts.firstOrNull { it.id == form.sourceAccountId }
            val dest = uiState.value.accounts.firstOrNull { it.id == accountId }
            val destErr = if (form.type == TransactionType.TRANSFER && accountId != null && form.sourceAccountId != null) {
                if (accountId == form.sourceAccountId) {
                    "Source and destination accounts must be different"
                } else if (src != null && dest != null && !src.initialBalance.currency.code.equals(dest.initialBalance.currency.code, ignoreCase = true)) {
                    "Source and destination accounts must have the same currency"
                } else null
            } else null

            state.copy(
                formState = form.copy(
                    destinationAccountId = accountId,
                    destinationAccountError = destErr
                )
            )
        }
    }

    fun onFormCategoryChange(categoryId: EntityId?) {
        localState.update { state ->
            state.copy(formState = state.formState?.copy(categoryId = categoryId))
        }
    }

    fun onFormNoteChange(note: String) {
        localState.update { state ->
            state.copy(formState = state.formState?.copy(note = note))
        }
    }

    fun onFormFrequencyChange(frequency: RecurrenceFrequency) {
        localState.update { state ->
            val form = state.formState ?: return@update state
            state.copy(formState = form.copy(frequency = frequency))
        }
    }

    fun onFormStartDateChange(date: LocalDate) {
        localState.update { state ->
            val form = state.formState ?: return@update state
            val nextOcc = if (!form.isEditing || form.nextOccurrence.isBefore(date)) date else form.nextOccurrence
            state.copy(
                formState = form.copy(
                    startDate = date,
                    nextOccurrence = nextOcc,
                    dateError = null
                )
            )
        }
    }

    fun onFormNextOccurrenceChange(date: LocalDate) {
        localState.update { state ->
            state.copy(formState = state.formState?.copy(nextOccurrence = date, dateError = null))
        }
    }

    fun onFormHasEndDateChange(hasEndDate: Boolean) {
        localState.update { state ->
            val form = state.formState ?: return@update state
            val end = if (hasEndDate) (form.endDate ?: form.startDate.plusMonths(6)) else null
            state.copy(formState = form.copy(hasEndDate = hasEndDate, endDate = end, dateError = null))
        }
    }

    fun onFormEndDateChange(date: LocalDate?) {
        localState.update { state ->
            state.copy(formState = state.formState?.copy(endDate = date, dateError = null))
        }
    }

    fun onFormEnabledChange(isEnabled: Boolean) {
        localState.update { state ->
            state.copy(formState = state.formState?.copy(isEnabled = isEnabled))
        }
    }

    fun saveRecurring() {
        val form = localState.value.formState ?: return
        val amount = RecurringUseCases.parseAmount(form.amountText)
        val effectiveEndDate = if (form.hasEndDate) form.endDate else null

        val src = uiState.value.accounts.firstOrNull { it.id == form.sourceAccountId }
        val dest = uiState.value.accounts.firstOrNull { it.id == form.destinationAccountId }

        val validation = RecurringUseCases.validate(
            amount = amount,
            currency = form.currency,
            type = form.type,
            sourceAccountId = form.sourceAccountId,
            destinationAccountId = form.destinationAccountId,
            startDate = form.startDate,
            nextOccurrence = form.nextOccurrence,
            endDate = effectiveEndDate,
            sourceAccountCurrency = src?.initialBalance?.currency,
            destinationAccountCurrency = dest?.initialBalance?.currency
        )

        if (!validation.isValid) {
            localState.update { state ->
                state.copy(
                    formState = form.copy(
                        amountError = validation.amountError,
                        sourceAccountError = validation.sourceAccountError,
                        destinationAccountError = validation.destinationAccountError,
                        dateError = validation.dateError
                    )
                )
            }
            return
        }

        viewModelScope.launch {
            if (form.isEditing && form.id != null) {
                val existing = recurringRepository.getRecurringTransactionById(form.id)
                if (existing != null) {
                    val updated = existing.copy(
                        amount = com.example.expensetracker.core.model.money.Money(amount!!, form.currency),
                        type = form.type,
                        sourceAccountId = form.sourceAccountId!!,
                        destinationAccountId = if (form.type == TransactionType.TRANSFER) form.destinationAccountId else null,
                        categoryId = if (form.type == TransactionType.TRANSFER) null else form.categoryId,
                        note = form.note.trim(),
                        frequency = form.frequency,
                        startDate = form.startDate,
                        nextOccurrence = form.nextOccurrence,
                        endDate = effectiveEndDate,
                        isEnabled = form.isEnabled
                    )
                    recurringRepository.updateRecurringTransaction(updated)
                }
            } else {
                val newRec = RecurringUseCases.createRecurringTransaction(
                    id = EntityId(UUID.randomUUID().toString()),
                    amount = amount!!,
                    currency = form.currency,
                    type = form.type,
                    sourceAccountId = form.sourceAccountId!!,
                    destinationAccountId = form.destinationAccountId,
                    categoryId = form.categoryId,
                    note = form.note,
                    frequency = form.frequency,
                    startDate = form.startDate,
                    nextOccurrence = form.nextOccurrence,
                    endDate = effectiveEndDate,
                    isEnabled = form.isEnabled
                )
                recurringRepository.insertRecurringTransaction(newRec)
            }

            closeForm()
        }
    }

    fun toggleEnabled(recurring: RecurringTransaction) {
        viewModelScope.launch {
            recurringRepository.setEnabled(recurring.id, !recurring.isEnabled)
        }
    }

    fun requestDelete(recurring: RecurringTransaction) {
        localState.update { it.copy(recurringToDelete = recurring) }
    }

    fun confirmDelete() {
        val toDelete = localState.value.recurringToDelete ?: return
        viewModelScope.launch {
            recurringRepository.deleteRecurringTransaction(toDelete)
            localState.update { it.copy(recurringToDelete = null) }
        }
    }

    fun dismissDeleteDialog() {
        localState.update { it.copy(recurringToDelete = null) }
    }

    fun clearError() {
        localState.update { it.copy(errorMessage = null) }
    }
}
