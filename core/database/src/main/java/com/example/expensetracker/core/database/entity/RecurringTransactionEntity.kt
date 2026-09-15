package com.example.expensetracker.core.database.entity

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.example.expensetracker.core.model.recurring.RecurrenceFrequency
import com.example.expensetracker.core.model.transaction.TransactionType
import java.time.LocalDate

@Entity(tableName = "recurring_transactions")
data class RecurringTransactionEntity(
    @PrimaryKey
    val id: String,
    val amountMinor: Long,
    val currencyCode: String,
    val type: TransactionType,
    val sourceAccountId: String,
    val destinationAccountId: String? = null,
    val categoryId: String? = null,
    val note: String = "",
    val frequency: RecurrenceFrequency,
    val nextOccurrence: LocalDate,
    val startDate: LocalDate = nextOccurrence,
    val endDate: LocalDate? = null,
    val isEnabled: Boolean = true,
    val lastGeneratedOccurrence: LocalDate? = null
)

