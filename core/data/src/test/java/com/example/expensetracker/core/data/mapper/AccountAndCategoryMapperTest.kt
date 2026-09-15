package com.example.expensetracker.core.data.mapper

import com.example.expensetracker.core.database.entity.AccountEntity
import com.example.expensetracker.core.database.entity.BudgetEntity
import com.example.expensetracker.core.database.entity.CategoryEntity
import com.example.expensetracker.core.database.entity.RecurringTransactionEntity
import com.example.expensetracker.core.model.account.Account
import com.example.expensetracker.core.model.account.AccountType
import com.example.expensetracker.core.model.budget.Budget
import com.example.expensetracker.core.model.category.Category
import com.example.expensetracker.core.model.common.EntityId
import com.example.expensetracker.core.model.money.Currency
import com.example.expensetracker.core.model.money.Money
import com.example.expensetracker.core.model.recurring.RecurrenceFrequency
import com.example.expensetracker.core.model.recurring.RecurringTransaction
import com.example.expensetracker.core.model.transaction.TransactionType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.math.BigDecimal
import java.time.LocalDate

class AccountAndCategoryMapperTest {

    @Test
    fun testCategoryMapping() {
        val category = Category(
            id = EntityId("cat-groceries"),
            name = "Groceries",
            iconKey = "shopping_cart",
            colorKey = "emerald",
            isDefault = true,
            isArchived = false
        )

        val entity = category.toEntity()
        assertEquals("cat-groceries", entity.id)
        assertEquals("Groceries", entity.name)
        assertEquals("shopping_cart", entity.iconKey)
        assertEquals("emerald", entity.colorKey)
        assertTrue(entity.isDefault)
        assertFalse(entity.isArchived)

        val mappedBack = entity.toDomain()
        assertEquals(category, mappedBack)
    }

    @Test
    fun testAccountMapping() {
        val account = Account(
            id = EntityId("acc-wallet"),
            name = "Cash Wallet",
            type = AccountType.CASH,
            currency = Currency.USD,
            initialBalance = Money(BigDecimal("100.00"), Currency.USD),
            currentBalance = Money(BigDecimal("85.50"), Currency.USD),
            isArchived = false
        )

        val entity = account.toEntity()
        assertEquals("acc-wallet", entity.id)
        assertEquals("Cash Wallet", entity.name)
        assertEquals(AccountType.CASH, entity.type)
        assertEquals("USD", entity.currencyCode)
        assertEquals(10000L, entity.initialBalanceMinor)
        assertEquals(8550L, entity.currentBalanceMinor)
        assertFalse(entity.isArchived)

        val mappedBack = entity.toDomain()
        assertEquals(account.id, mappedBack.id)
        assertEquals(account.name, mappedBack.name)
        assertEquals(account.type, mappedBack.type)
        assertEquals(account.currency.code, mappedBack.currency.code)
        assertEquals(account.initialBalance.amount, mappedBack.initialBalance.amount)
        assertEquals(account.currentBalance.amount, mappedBack.currentBalance.amount)
    }

    @Test
    fun testBudgetMapping() {
        val budget = Budget(
            id = EntityId("bgt-monthly"),
            name = "Monthly Food Budget",
            targetAmount = Money(BigDecimal("500.00"), Currency.USD),
            categoryId = EntityId("cat-food"),
            startDate = LocalDate.of(2026, 9, 1),
            endDate = LocalDate.of(2026, 9, 30),
            isEnabled = true
        )

        val entity = budget.toEntity()
        assertEquals("bgt-monthly", entity.id)
        assertEquals(50000L, entity.targetAmountMinor)
        assertEquals("cat-food", entity.categoryId)

        val mappedBack = entity.toDomain()
        assertEquals(budget.id, mappedBack.id)
        assertEquals(budget.targetAmount.amount, mappedBack.targetAmount.amount)
        assertEquals(budget.categoryId, mappedBack.categoryId)
        assertTrue(mappedBack.isEnabled)

        // Overall budget (null categoryId)
        val overallBudget = budget.copy(categoryId = null)
        val overallEntity = overallBudget.toEntity()
        assertNull(overallEntity.categoryId)
        assertNull(overallEntity.toDomain().categoryId)
    }

    @Test
    fun testRecurringTransactionMapping() {
        val recurring = RecurringTransaction(
            id = EntityId("rec-rent"),
            amount = Money(BigDecimal("1500.00"), Currency.USD),
            type = TransactionType.EXPENSE,
            sourceAccountId = EntityId("acc-bank"),
            destinationAccountId = null,
            categoryId = EntityId("cat-housing"),
            note = "Monthly rent",
            frequency = RecurrenceFrequency.MONTHLY,
            startDate = LocalDate.of(2026, 9, 1),
            nextOccurrence = LocalDate.of(2026, 10, 1),
            endDate = LocalDate.of(2027, 9, 1),
            isEnabled = true,
            lastGeneratedOccurrence = LocalDate.of(2026, 9, 1)
        )

        val entity = recurring.toEntity()
        assertEquals("rec-rent", entity.id)
        assertEquals(150000L, entity.amountMinor)
        assertEquals(RecurrenceFrequency.MONTHLY, entity.frequency)
        assertEquals(LocalDate.of(2026, 9, 1), entity.startDate)
        assertEquals(LocalDate.of(2026, 10, 1), entity.nextOccurrence)
        assertEquals(LocalDate.of(2027, 9, 1), entity.endDate)
        assertEquals(LocalDate.of(2026, 9, 1), entity.lastGeneratedOccurrence)

        val mappedBack = entity.toDomain()
        assertEquals(recurring.id, mappedBack.id)
        assertEquals(recurring.amount.amount, mappedBack.amount.amount)
        assertEquals(recurring.frequency, mappedBack.frequency)
        assertEquals(recurring.startDate, mappedBack.startDate)
        assertEquals(recurring.nextOccurrence, mappedBack.nextOccurrence)
        assertEquals(recurring.endDate, mappedBack.endDate)
        assertEquals(recurring.lastGeneratedOccurrence, mappedBack.lastGeneratedOccurrence)
        assertTrue(mappedBack.isEnabled)
    }
}
