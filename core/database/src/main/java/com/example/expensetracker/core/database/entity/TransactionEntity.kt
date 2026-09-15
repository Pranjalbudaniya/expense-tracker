package com.example.expensetracker.core.database.entity

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.example.expensetracker.core.model.transaction.TransactionType
import java.time.Instant

@Entity(tableName = "transactions")
data class TransactionEntity(
    @PrimaryKey
    val id: String,
    val amountMinor: Long,
    val currencyCode: String,
    val type: TransactionType,
    val sourceAccountId: String,
    val destinationAccountId: String? = null,
    val categoryId: String? = null,
    val timestamp: Instant,
    val note: String = "",
    val recurringTransactionId: String? = null,
    val isDeleted: Boolean = false
)
