package com.example.expensetracker.core.data.mapper

import com.example.expensetracker.core.database.converter.DatabaseConverters
import com.example.expensetracker.core.database.entity.RecurringTransactionEntity
import com.example.expensetracker.core.model.common.EntityId
import com.example.expensetracker.core.model.recurring.RecurringTransaction

fun RecurringTransactionEntity.toDomain(): RecurringTransaction {
    return RecurringTransaction(
        id = EntityId(id),
        amount = DatabaseConverters.toMoney(amountMinor, currencyCode),
        type = type,
        sourceAccountId = EntityId(sourceAccountId),
        destinationAccountId = destinationAccountId?.let { EntityId(it) },
        categoryId = categoryId?.let { EntityId(it) },
        note = note,
        frequency = frequency,
        startDate = startDate,
        nextOccurrence = nextOccurrence,
        endDate = endDate,
        isEnabled = isEnabled,
        lastGeneratedOccurrence = lastGeneratedOccurrence
    )
}

fun RecurringTransaction.toEntity(): RecurringTransactionEntity {
    return RecurringTransactionEntity(
        id = id.value,
        amountMinor = DatabaseConverters.toMinorUnits(amount),
        currencyCode = amount.currency.code,
        type = type,
        sourceAccountId = sourceAccountId.value,
        destinationAccountId = destinationAccountId?.value,
        categoryId = categoryId?.value,
        note = note,
        frequency = frequency,
        startDate = startDate,
        nextOccurrence = nextOccurrence,
        endDate = endDate,
        isEnabled = isEnabled,
        lastGeneratedOccurrence = lastGeneratedOccurrence
    )
}
