package com.example.expensetracker.core.data.mapper

import com.example.expensetracker.core.database.converter.DatabaseConverters
import com.example.expensetracker.core.database.entity.AccountEntity
import com.example.expensetracker.core.model.account.Account
import com.example.expensetracker.core.model.common.EntityId
import com.example.expensetracker.core.model.money.Currency

fun AccountEntity.toDomain(): Account {
    val currency = Currency.fromCode(currencyCode)
    return Account(
        id = EntityId(id),
        name = name,
        type = type,
        currency = currency,
        initialBalance = DatabaseConverters.toMoney(initialBalanceMinor, currencyCode),
        currentBalance = DatabaseConverters.toMoney(currentBalanceMinor, currencyCode),
        isArchived = isArchived
    )
}

fun Account.toEntity(): AccountEntity {
    return AccountEntity(
        id = id.value,
        name = name,
        type = type,
        currencyCode = currency.code,
        initialBalanceMinor = DatabaseConverters.toMinorUnits(initialBalance),
        currentBalanceMinor = DatabaseConverters.toMinorUnits(currentBalance),
        isArchived = isArchived
    )
}
