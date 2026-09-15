package com.example.expensetracker.feature.budgets

import com.example.expensetracker.core.model.budget.Budget
import com.example.expensetracker.core.model.category.Category
import com.example.expensetracker.core.model.common.EntityId
import com.example.expensetracker.core.model.money.Currency
import com.example.expensetracker.core.model.money.Money
import java.time.LocalDate

/**
 * Presentation item representing a calculated budget progress state.
 *
 * @property budget The underlying budget entity.
 * @property categoryName Display name of the assigned category (or null if overall budget).
 * @property categoryIcon Optional icon identifier for the assigned category.
 * @property spent Total expense spending matching this budget.
 * @property target The target spending limit.
 * @property remaining Remaining spending limit (negative if overspent).
 * @property progressFraction Progress ratio as a Float (e.g. 0.75f = 75%, 1.25f = 125%).
 * @property progressClampedFraction Progress ratio clamped to [0f, 1f] for visual indicators.
 * @property progressPercentage True percentage value (e.g. 75 or 125).
 * @property isOverspent Whether spending exceeds the target amount.
 * @property excludedDifferentCurrencyCount Number of transactions excluded due to currency mismatch.
 */
data class BudgetProgressItem(
    val budget: Budget,
    val categoryName: String? = null,
    val categoryIcon: String? = null,
    val spent: Money,
    val target: Money,
    val remaining: Money,
    val progressFraction: Float,
    val progressClampedFraction: Float,
    val progressPercentage: Int,
    val isOverspent: Boolean,
    val excludedDifferentCurrencyCount: Int = 0
)

/**
 * Form state for creating or editing a budget.
 *
 * @property budgetId Unique identifier if editing an existing budget; null for new.
 * @property name User-specified budget name.
 * @property amountInput String text input for budget amount.
 * @property currency Budget currency.
 * @property isCategorySpecific Whether this budget targets a specific category (false = overall).
 * @property selectedCategoryId Category ID if category-specific.
 * @property startDate Start date of the budget period.
 * @property endDate End date of the budget period.
 * @property isEnabled Whether the budget is active.
 * @property amountError Validation error for the amount field.
 * @property dateError Validation error for date range.
 * @property categoryError Validation error for category selection.
 */
data class BudgetFormState(
    val budgetId: EntityId? = null,
    val name: String = "",
    val amountInput: String = "",
    val currency: Currency = Currency.INR,
    val isCategorySpecific: Boolean = false,
    val selectedCategoryId: EntityId? = null,
    val startDate: LocalDate = LocalDate.now().withDayOfMonth(1),
    val endDate: LocalDate = LocalDate.now().withDayOfMonth(LocalDate.now().lengthOfMonth()),
    val isEnabled: Boolean = true,
    val amountError: String? = null,
    val dateError: String? = null,
    val categoryError: String? = null
) {
    val isEditing: Boolean get() = budgetId != null
}

/**
 * Comprehensive UI state for the Budgets screen.
 *
 * @property budgets Calculated list of active/existing budgets.
 * @property availableCategories Categories available for selection in the form.
 * @property availableCurrencies Currencies supported for budgeting.
 * @property isFormOpen Whether the BudgetFormScreen is currently displayed.
 * @property formState Active form editing state (or null if form is closed).
 * @property isLoading Whether data is initially loading.
 * @property deleteConfirmationBudget Budget pending deletion confirmation (or null).
 */
data class BudgetsUiState(
    val budgets: List<BudgetProgressItem> = emptyList(),
    val availableCategories: List<Category> = emptyList(),
    val availableCurrencies: List<Currency> = emptyList(),
    val isFormOpen: Boolean = false,
    val formState: BudgetFormState? = null,
    val isLoading: Boolean = false,
    val deleteConfirmationBudget: Budget? = null
)
