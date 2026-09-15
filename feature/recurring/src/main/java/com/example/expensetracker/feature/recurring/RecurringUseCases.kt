package com.example.expensetracker.feature.recurring

import com.example.expensetracker.core.model.common.EntityId
import com.example.expensetracker.core.model.money.Currency
import com.example.expensetracker.core.model.money.Money
import com.example.expensetracker.core.model.recurring.RecurrenceFrequency
import com.example.expensetracker.core.model.recurring.RecurringTransaction
import com.example.expensetracker.core.model.transaction.TransactionType
import java.math.BigDecimal
import java.time.LocalDate

/**
 * Validation result for recurring transaction form inputs.
 */
data class RecurringValidationResult(
    val isValid: Boolean,
    val amountError: String? = null,
    val sourceAccountError: String? = null,
    val destinationAccountError: String? = null,
    val dateError: String? = null,
    val generalError: String? = null
)

/**
 * Domain use-cases and input validation for recurring transactions.
 */
object RecurringUseCases {

    /**
     * Parses an input string into a valid [BigDecimal] amount, or null if blank/malformed.
     */
    fun parseAmount(input: String): BigDecimal? {
        val trimmed = input.trim()
        if (trimmed.isBlank()) return null
        return runCatching {
            val bd = BigDecimal(trimmed)
            if (bd > BigDecimal.ZERO) bd else null
        }.getOrNull()
    }

    /**
     * Validates recurring transaction parameters.
     */
    fun validate(
        amount: BigDecimal?,
        currency: Currency,
        type: TransactionType,
        sourceAccountId: EntityId?,
        destinationAccountId: EntityId?,
        startDate: LocalDate?,
        nextOccurrence: LocalDate?,
        endDate: LocalDate?,
        sourceAccountCurrency: Currency? = null,
        destinationAccountCurrency: Currency? = null
    ): RecurringValidationResult {
        var amountErr: String? = null
        var sourceErr: String? = null
        var destErr: String? = null
        var dateErr: String? = null

        // Amount validation
        if (amount == null || amount <= BigDecimal.ZERO) {
            amountErr = "Amount must be greater than 0"
        } else {
            val maxDecimals = runCatching {
                java.util.Currency.getInstance(currency.code.uppercase()).defaultFractionDigits
            }.getOrDefault(2).let { if (it >= 0) it else 2 }

            if (amount.scale() > maxDecimals) {
                amountErr = "Maximum $maxDecimals decimal places allowed for ${currency.code}"
            }
        }

        // Account validation
        if (sourceAccountId == null) {
            sourceErr = "Source account is required"
        }

        if (type == TransactionType.TRANSFER) {
            if (destinationAccountId == null) {
                destErr = "Destination account is required for transfers"
            } else if (destinationAccountId == sourceAccountId) {
                destErr = "Source and destination accounts must be different"
            } else if (sourceAccountCurrency != null && destinationAccountCurrency != null &&
                !sourceAccountCurrency.code.equals(destinationAccountCurrency.code, ignoreCase = true)
            ) {
                destErr = "Source and destination accounts must have the same currency"
            }
        }

        // Date validation
        if (startDate == null) {
            dateErr = "Start date is required"
        } else if (nextOccurrence == null) {
            dateErr = "Next occurrence date is required"
        } else if (nextOccurrence.isBefore(startDate)) {
            dateErr = "Next occurrence cannot be before start date"
        } else if (endDate != null && endDate.isBefore(startDate)) {
            dateErr = "End date cannot be before start date"
        }

        val isValid = amountErr == null && sourceErr == null && destErr == null && dateErr == null

        return RecurringValidationResult(
            isValid = isValid,
            amountError = amountErr,
            sourceAccountError = sourceErr,
            destinationAccountError = destErr,
            dateError = dateErr
        )
    }

    /**
     * Constructs a validated [RecurringTransaction] domain model.
     */
    fun createRecurringTransaction(
        id: EntityId,
        amount: BigDecimal,
        currency: Currency,
        type: TransactionType,
        sourceAccountId: EntityId,
        destinationAccountId: EntityId?,
        categoryId: EntityId?,
        note: String,
        frequency: RecurrenceFrequency,
        startDate: LocalDate,
        nextOccurrence: LocalDate,
        endDate: LocalDate?,
        isEnabled: Boolean = true
    ): RecurringTransaction {
        return RecurringTransaction(
            id = id,
            amount = Money(amount, currency),
            type = type,
            sourceAccountId = sourceAccountId,
            destinationAccountId = if (type == TransactionType.TRANSFER) destinationAccountId else null,
            categoryId = if (type == TransactionType.TRANSFER) null else categoryId,
            note = note.trim(),
            frequency = frequency,
            startDate = startDate,
            nextOccurrence = nextOccurrence,
            endDate = endDate,
            isEnabled = isEnabled,
            lastGeneratedOccurrence = null
        )
    }
}
