package com.example.expensetracker.core.data.mapper

import com.example.expensetracker.core.database.converter.DatabaseConverters
import com.example.expensetracker.core.database.entity.TransactionEntity
import com.example.expensetracker.core.model.common.EntityId
import com.example.expensetracker.core.model.transaction.Transaction

fun TransactionEntity.toDomain(): Transaction {
    return Transaction(
        id = EntityId(id),
        amount = DatabaseConverters.toMoney(amountMinor, currencyCode),
        type = type,
        sourceAccountId = EntityId(sourceAccountId),
        destinationAccountId = destinationAccountId?.let { EntityId(it) },
        categoryId = categoryId?.let { EntityId(it) },
        timestamp = timestamp,
        note = note,
        recurringTransactionId = recurringTransactionId?.let { EntityId(it) },
        isDeleted = isDeleted
    )
}

fun Transaction.toEntity(): TransactionEntity {
    return TransactionEntity(
        id = id.value,
        amountMinor = DatabaseConverters.toMinorUnits(amount),
        currencyCode = amount.currency.code,
        type = type,
        sourceAccountId = sourceAccountId.value,
        destinationAccountId = destinationAccountId?.value,
        categoryId = categoryId?.value,
        timestamp = timestamp,
        note = note,
        recurringTransactionId = recurringTransactionId?.value,
        isDeleted = isDeleted
    )
}
