package com.example.expensetracker.core.database.entity

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.example.expensetracker.core.model.account.AccountType

@Entity(tableName = "accounts")
data class AccountEntity(
    @PrimaryKey
    val id: String,
    val name: String,
    val type: AccountType,
    val currencyCode: String,
    val initialBalanceMinor: Long,
    val currentBalanceMinor: Long,
    val isArchived: Boolean = false
)
