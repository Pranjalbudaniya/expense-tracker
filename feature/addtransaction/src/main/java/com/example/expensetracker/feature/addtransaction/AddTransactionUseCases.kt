package com.example.expensetracker.feature.addtransaction

import com.example.expensetracker.core.data.repository.AccountRepository
import com.example.expensetracker.core.data.repository.CategoryRepository
import com.example.expensetracker.core.data.repository.TransactionRepository
import com.example.expensetracker.core.model.account.Account
import com.example.expensetracker.core.model.category.Category
import com.example.expensetracker.core.model.common.EntityId
import com.example.expensetracker.core.model.money.Currency
import com.example.expensetracker.core.model.money.Money
import com.example.expensetracker.core.model.transaction.Transaction
import com.example.expensetracker.core.model.transaction.TransactionType
import kotlinx.coroutines.flow.Flow
import java.math.BigDecimal
import java.time.Instant
import java.util.UUID
import javax.inject.Inject

typealias AmountValidationResult = com.example.expensetracker.core.model.money.MonetaryValidationResult

data class FormValidationResult(
    val amountError: String? = null,
    val sourceAccountError: String? = null,
    val destinationAccountError: String? = null,
    val parsedAmount: BigDecimal? = null
) {
    val isValid: Boolean
        get() = amountError == null && sourceAccountError == null && destinationAccountError == null
}

/**
 * Use cases encapsulating business logic for creating and saving transactions.
 */
class AddTransactionUseCases @Inject constructor(
    private val accountRepository: AccountRepository,
    private val categoryRepository: CategoryRepository,
    private val transactionRepository: TransactionRepository
) {

    fun loadAccounts(): Flow<List<Account>> = accountRepository.getActiveAccounts()

    fun loadCategories(): Flow<List<Category>> = categoryRepository.getActiveCategories()

    suspend fun saveTransaction(transaction: Transaction) {
        transactionRepository.insertTransaction(transaction)
    }

    fun parseAndValidateAmount(
        amountInput: String,
        currency: Currency
    ): AmountValidationResult = Money.parseAndValidateAmount(amountInput, currency)

    fun validateForm(
        type: TransactionType,
        amountInput: String,
        sourceAccountId: EntityId?,
        destinationAccountId: EntityId?,
        currency: Currency
    ): FormValidationResult {
        val amountResult = parseAndValidateAmount(amountInput, currency)
        val amountError = when (amountResult) {
            is com.example.expensetracker.core.model.money.MonetaryValidationResult.Error -> amountResult.message
            is com.example.expensetracker.core.model.money.MonetaryValidationResult.Success -> null
        }
        val parsedAmount = when (amountResult) {
            is com.example.expensetracker.core.model.money.MonetaryValidationResult.Success -> amountResult.amount
            else -> null
        }

        var sourceError: String? = null
        var destError: String? = null

        when (type) {
            TransactionType.EXPENSE -> {
                if (sourceAccountId == null) {
                    sourceError = "Please select an account"
                }
            }
            TransactionType.INCOME -> {
                if (destinationAccountId == null) {
                    destError = "Please select an account"
                }
            }
            TransactionType.TRANSFER -> {
                if (sourceAccountId == null) {
                    sourceError = "Please select a source account"
                }
                if (destinationAccountId == null) {
                    destError = "Please select a destination account"
                } else if (sourceAccountId != null && sourceAccountId == destinationAccountId) {
                    destError = "Source and destination accounts must differ"
                }
            }
        }

        return FormValidationResult(
            amountError = amountError,
            sourceAccountError = sourceError,
            destinationAccountError = destError,
            parsedAmount = parsedAmount
        )
    }

    fun createTransaction(
        type: TransactionType,
        amount: Money,
        sourceAccountId: EntityId?,
        destinationAccountId: EntityId?,
        categoryId: EntityId?,
        timestamp: Instant,
        note: String
    ): Transaction {
        val trimmedNote = note.trim()
        val id = EntityId(UUID.randomUUID().toString())

        return when (type) {
            TransactionType.EXPENSE -> {
                Transaction(
                    id = id,
                    amount = amount,
                    type = type,
                    sourceAccountId = requireNotNull(sourceAccountId) { "Source account is required for Expense" },
                    destinationAccountId = null,
                    categoryId = categoryId,
                    timestamp = timestamp,
                    note = trimmedNote,
                    recurringTransactionId = null,
                    isDeleted = false
                )
            }
            TransactionType.INCOME -> {
                Transaction(
                    id = id,
                    amount = amount,
                    type = type,
                    sourceAccountId = requireNotNull(destinationAccountId) { "Destination account is required for Income" },
                    destinationAccountId = null,
                    categoryId = categoryId,
                    timestamp = timestamp,
                    note = trimmedNote,
                    recurringTransactionId = null,
                    isDeleted = false
                )
            }
            TransactionType.TRANSFER -> {
                Transaction(
                    id = id,
                    amount = amount,
                    type = type,
                    sourceAccountId = requireNotNull(sourceAccountId) { "Source account is required for Transfer" },
                    destinationAccountId = requireNotNull(destinationAccountId) { "Destination account is required for Transfer" },
                    categoryId = null,
                    timestamp = timestamp,
                    note = trimmedNote,
                    recurringTransactionId = null,
                    isDeleted = false
                )
            }
        }
    }

    private fun getCurrencyFractionDigits(currency: Currency): Int {
        return runCatching {
            java.util.Currency.getInstance(currency.code).defaultFractionDigits
        }.getOrDefault(2)
    }
}
