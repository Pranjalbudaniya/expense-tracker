package com.example.expensetracker.feature.accounts

import com.example.expensetracker.core.model.account.Account
import com.example.expensetracker.core.model.account.AccountType
import com.example.expensetracker.core.model.common.EntityId
import com.example.expensetracker.core.model.money.Currency
import com.example.expensetracker.core.model.money.Money
import com.example.expensetracker.core.model.transaction.Transaction
import com.example.expensetracker.core.model.transaction.TransactionType
import java.math.BigDecimal
import javax.inject.Inject

/**
 * Result of validating an initial balance input string.
 */
sealed interface BalanceValidationResult {
    data class Success(val amount: BigDecimal) : BalanceValidationResult
    data class Error(val message: String) : BalanceValidationResult
}

/**
 * Result of validating an account form input.
 */
sealed interface AccountValidationResult {
    data class Success(val account: Account) : AccountValidationResult
    data class Failure(
        val nameError: String? = null,
        val initialBalanceError: String? = null
    ) : AccountValidationResult
}

/**
 * Domain use cases for Account operations and balance calculations.
 */
class AccountUseCases @Inject constructor() {

    /**
     * Calculates the live balance for an [Account] based on its opening balance and active transactions.
     *
     * Rules:
     * - Income to this account adds to the balance.
     * - Expenses from this account deduct from the balance.
     * - Transfers from this account deduct from the balance.
     * - Transfers to this account add to the balance.
     * - Deleted transactions are strictly excluded.
     * - Only transactions whose currency matches the account's currency are considered.
     */
    fun calculateAccountBalance(
        account: Account,
        transactions: List<Transaction>
    ): Money {
        var balance = account.initialBalance.amount

        for (tx in transactions) {
            if (tx.isDeleted) continue
            // Multi-currency isolation: only matching currencies affect balance
            if (!tx.amount.currency.code.equals(account.currency.code, ignoreCase = true)) continue

            val amount = tx.amount.amount

            when (tx.type) {
                TransactionType.INCOME -> {
                    if (tx.sourceAccountId == account.id) {
                        balance = balance.add(amount)
                    }
                }
                TransactionType.EXPENSE -> {
                    if (tx.sourceAccountId == account.id) {
                        balance = balance.subtract(amount)
                    }
                }
                TransactionType.TRANSFER -> {
                    if (tx.sourceAccountId == account.id) {
                        balance = balance.subtract(amount)
                    }
                    if (tx.destinationAccountId == account.id) {
                        balance = balance.add(amount)
                    }
                }
            }
        }

        return Money(balance, account.currency)
    }

    /**
     * Validates an initial balance input string for a given currency.
     *
     * Initial balance can be zero, positive, or negative (e.g. credit card debt).
     */
    fun parseAndValidateInitialBalance(
        input: String,
        currency: Currency
    ): BalanceValidationResult {
        val trimmed = input.trim()
        if (trimmed.isEmpty()) {
            return BalanceValidationResult.Error("Initial balance is required")
        }

        val parsed = try {
            BigDecimal(trimmed)
        } catch (e: NumberFormatException) {
            return BalanceValidationResult.Error("Invalid balance format")
        }

        val maxFractionDigits = runCatching {
            java.util.Currency.getInstance(currency.code).defaultFractionDigits
        }.getOrDefault(2)

        val scale = parsed.stripTrailingZeros().scale()
        if (scale > maxFractionDigits) {
            return BalanceValidationResult.Error(
                if (maxFractionDigits == 0) {
                    "Currency ${currency.code} does not support decimal places"
                } else {
                    "Balance cannot exceed $maxFractionDigits decimal places"
                }
            )
        }

        return BalanceValidationResult.Success(parsed)
    }

    /**
     * Validates input fields and produces an [Account] entity or validation failures.
     */
    fun validateAndCreateAccount(
        id: EntityId,
        name: String,
        type: AccountType,
        currency: Currency,
        initialBalanceInput: String,
        isArchived: Boolean = false,
        currentBalance: Money? = null
    ): AccountValidationResult {
        var nameError: String? = null
        var initialBalanceError: String? = null

        val trimmedName = name.trim()
        if (trimmedName.isEmpty()) {
            nameError = "Account name cannot be empty"
        }

        val validatedBalance = when (val result = parseAndValidateInitialBalance(initialBalanceInput, currency)) {
            is BalanceValidationResult.Success -> result.amount
            is BalanceValidationResult.Error -> {
                initialBalanceError = result.message
                null
            }
        }

        if (nameError != null || initialBalanceError != null || validatedBalance == null) {
            return AccountValidationResult.Failure(
                nameError = nameError,
                initialBalanceError = initialBalanceError
            )
        }

        val initialMoney = Money(validatedBalance, currency)
        val finalCurrentBalance = currentBalance ?: initialMoney

        val account = Account(
            id = id,
            name = trimmedName,
            type = type,
            currency = currency,
            initialBalance = initialMoney,
            currentBalance = finalCurrentBalance,
            isArchived = isArchived
        )

        return AccountValidationResult.Success(account)
    }
}
