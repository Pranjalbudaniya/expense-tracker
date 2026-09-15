package com.example.expensetracker.feature.budgets

import com.example.expensetracker.core.model.budget.Budget
import com.example.expensetracker.core.model.category.Category
import com.example.expensetracker.core.model.common.EntityId
import com.example.expensetracker.core.model.money.Currency
import com.example.expensetracker.core.model.money.Money
import com.example.expensetracker.core.model.money.MonetaryValidationResult
import com.example.expensetracker.core.model.transaction.Transaction
import com.example.expensetracker.core.model.transaction.TransactionType
import java.math.BigDecimal
import java.math.RoundingMode
import java.time.LocalDate
import java.time.ZoneId
import javax.inject.Inject

/**
 * Domain use cases for budget operations and financial calculations.
 */
class BudgetUseCases @Inject constructor() {

    /**
     * Calculates the spending progress, remaining balance, and overspending state for a [Budget].
     *
     * Rules:
     * - Only [TransactionType.EXPENSE] transactions are counted.
     * - Transfers and Income are strictly excluded.
     * - Deleted transactions are strictly excluded.
     * - Only transactions whose timestamp falls within [Budget.startDate, Budget.endDate] inclusive are counted.
     * - For category-specific budgets, only transactions matching [Budget.categoryId] are counted.
     * - Only transactions matching the budget's exact currency code are counted; different currency
     *   transactions are excluded from the total and counted separately for transparency.
     */
    fun calculateBudgetProgress(
        budget: Budget,
        transactions: List<Transaction>,
        category: Category?,
        zoneId: ZoneId = ZoneId.systemDefault()
    ): BudgetProgressItem {
        var totalSpent = BigDecimal.ZERO
        var excludedDifferentCurrencyCount = 0

        for (tx in transactions) {
            if (tx.isDeleted) continue
            if (tx.type != TransactionType.EXPENSE) continue

            val txDate = tx.timestamp.atZone(zoneId).toLocalDate()
            if (txDate.isBefore(budget.startDate) || txDate.isAfter(budget.endDate)) continue

            if (budget.categoryId != null && tx.categoryId != budget.categoryId) continue

            // Currency matching
            if (tx.amount.currency.code.equals(budget.targetAmount.currency.code, ignoreCase = true)) {
                totalSpent = totalSpent.add(tx.amount.amount)
            } else {
                excludedDifferentCurrencyCount++
            }
        }

        val spentMoney = Money(totalSpent, budget.targetAmount.currency)
        val remainingAmount = budget.targetAmount.amount.subtract(totalSpent)
        val remainingMoney = Money(remainingAmount, budget.targetAmount.currency)
        val isOverspent = totalSpent > budget.targetAmount.amount

        val targetAmount = budget.targetAmount.amount
        val (fraction, percentage) = if (targetAmount.compareTo(BigDecimal.ZERO) > 0) {
            val f = (totalSpent.toDouble() / targetAmount.toDouble()).toFloat()
            val p = totalSpent.multiply(BigDecimal(100))
                .divide(targetAmount, 0, RoundingMode.HALF_UP)
                .toInt()
            f to p
        } else {
            0f to 0
        }

        return BudgetProgressItem(
            budget = budget,
            categoryName = category?.name,
            categoryIcon = category?.iconKey,
            spent = spentMoney,
            target = budget.targetAmount,
            remaining = remainingMoney,
            progressFraction = fraction,
            progressClampedFraction = fraction.coerceIn(0f, 1f),
            progressPercentage = percentage,
            isOverspent = isOverspent,
            excludedDifferentCurrencyCount = excludedDifferentCurrencyCount
        )
    }

    /**
     * Validation result for budget form submissions.
     */
    sealed interface FormValidationResult {
        data class Success(val budget: Budget) : FormValidationResult
        data class Failure(
            val amountError: String? = null,
            val dateError: String? = null,
            val categoryError: String? = null
        ) : FormValidationResult
    }

    /**
     * Validates input fields and produces a verified [Budget] entity or errors.
     */
    fun validateAndCreateBudget(
        id: EntityId,
        name: String,
        amountInput: String,
        currency: Currency,
        isCategorySpecific: Boolean,
        selectedCategoryId: EntityId?,
        startDate: LocalDate,
        endDate: LocalDate,
        isEnabled: Boolean,
        categoryNameFallback: String? = null
    ): FormValidationResult {
        var amountError: String? = null
        var dateError: String? = null
        var categoryError: String? = null

        val validatedAmount = when (val result = Money.parseAndValidateAmount(amountInput, currency)) {
            is MonetaryValidationResult.Success -> result.amount
            is MonetaryValidationResult.Error -> {
                amountError = result.message
                null
            }
        }

        if (startDate.isAfter(endDate)) {
            dateError = "Start date must be before or equal to end date"
        }

        val categoryId = if (isCategorySpecific) {
            if (selectedCategoryId == null) {
                categoryError = "Please select a category for this budget"
                null
            } else {
                selectedCategoryId
            }
        } else {
            null
        }

        if (amountError != null || dateError != null || categoryError != null || validatedAmount == null) {
            return FormValidationResult.Failure(
                amountError = amountError,
                dateError = dateError,
                categoryError = categoryError
            )
        }

        val finalName = if (name.isNotBlank()) {
            name.trim()
        } else if (isCategorySpecific && categoryNameFallback != null) {
            "$categoryNameFallback Budget"
        } else {
            "Overall Budget"
        }

        val budget = Budget(
            id = id,
            name = finalName,
            targetAmount = Money(validatedAmount, currency),
            categoryId = categoryId,
            startDate = startDate,
            endDate = endDate,
            isEnabled = isEnabled
        )

        return FormValidationResult.Success(budget)
    }
}
