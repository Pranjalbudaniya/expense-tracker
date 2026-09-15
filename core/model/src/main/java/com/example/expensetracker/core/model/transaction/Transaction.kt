package com.example.expensetracker.core.model.transaction

import com.example.expensetracker.core.model.common.EntityId
import com.example.expensetracker.core.model.money.Money
import java.time.Instant

data class Transaction(
    val id: EntityId,
    val amount: Money,
    val type: TransactionType,
    val sourceAccountId: EntityId,
    val destinationAccountId: EntityId? = null,
    val categoryId: EntityId? = null,
    val timestamp: Instant,
    val note: String = "",
    val recurringTransactionId: EntityId? = null,
    val isDeleted: Boolean = false
)
