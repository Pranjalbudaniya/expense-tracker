package com.example.expensetracker.core.data.mapper

import com.example.expensetracker.core.database.converter.DatabaseConverters
import com.example.expensetracker.core.database.entity.BudgetEntity
import com.example.expensetracker.core.model.budget.Budget
import com.example.expensetracker.core.model.common.EntityId

fun BudgetEntity.toDomain(): Budget {
    return Budget(
        id = EntityId(id),
        name = name,
        targetAmount = DatabaseConverters.toMoney(targetAmountMinor, currencyCode),
        categoryId = categoryId?.let { EntityId(it) },
        startDate = startDate,
        endDate = endDate,
        isEnabled = isEnabled
    )
}

fun Budget.toEntity(): BudgetEntity {
    return BudgetEntity(
        id = id.value,
        name = name,
        targetAmountMinor = DatabaseConverters.toMinorUnits(targetAmount),
        currencyCode = targetAmount.currency.code,
        categoryId = categoryId?.value,
        startDate = startDate,
        endDate = endDate,
        isEnabled = isEnabled
    )
}
