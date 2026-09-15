package com.example.expensetracker.core.model.account

import com.example.expensetracker.core.model.common.EntityId
import com.example.expensetracker.core.model.money.Currency
import com.example.expensetracker.core.model.money.Money

enum class AccountType {
    CASH,
    BANK,
    UPI,
    CREDIT_CARD,
    WALLET,
    OTHER
}

data class Account(
    val id: EntityId,
    val name: String,
    val type: AccountType,
    val currency: Currency,
    val initialBalance: Money,
    val currentBalance: Money,
    val isArchived: Boolean = false
)
