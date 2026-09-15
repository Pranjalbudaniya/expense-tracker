package com.example.expensetracker.feature.export

import com.example.expensetracker.core.model.common.EntityId
import com.example.expensetracker.core.model.money.Currency
import com.example.expensetracker.core.model.money.Money
import com.example.expensetracker.core.model.transaction.Transaction
import com.example.expensetracker.core.model.transaction.TransactionType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.math.BigDecimal
import java.time.Instant

class CsvTransactionWriterTest {

    @Test
    fun write_emptyList_writesHeaderOnly() {
        val result = CsvTransactionWriter.writeToString(emptyList())
        val expected = "version,id,type,amount,currency,source_account_id,destination_account_id,category_id,timestamp,note,recurring_id,is_deleted\r\n"
        assertEquals(expected, result)
    }

    @Test
    fun write_singleExpense_formatsCorrectly() {
        val tx = Transaction(
            id = EntityId("tx_1"),
            amount = Money.of(BigDecimal("45.50"), Currency.USD),
            type = TransactionType.EXPENSE,
            sourceAccountId = EntityId("acc_cash"),
            destinationAccountId = null,
            categoryId = EntityId("cat_food"),
            timestamp = Instant.parse("2026-09-15T10:00:00Z"),
            note = "Lunch at cafe",
            recurringTransactionId = null,
            isDeleted = false
        )

        val result = CsvTransactionWriter.writeToString(listOf(tx))
        val lines = result.split("\r\n").filter { it.isNotEmpty() }

        assertEquals(2, lines.size)
        assertEquals(
            "1,tx_1,EXPENSE,45.50,USD,acc_cash,,cat_food,2026-09-15T10:00:00Z,Lunch at cafe,,false",
            lines[1]
        )
    }

    @Test
    fun write_transfer_formatsWithDestinationAccount() {
        val tx = Transaction(
            id = EntityId("tx_2"),
            amount = Money.of(BigDecimal("1000.00"), Currency.INR),
            type = TransactionType.TRANSFER,
            sourceAccountId = EntityId("acc_bank"),
            destinationAccountId = EntityId("acc_cash"),
            categoryId = null,
            timestamp = Instant.parse("2026-09-15T12:00:00Z"),
            note = "ATM Withdrawal",
            recurringTransactionId = null,
            isDeleted = false
        )

        val result = CsvTransactionWriter.writeToString(listOf(tx))
        val lines = result.split("\r\n").filter { it.isNotEmpty() }

        assertEquals(
            "1,tx_2,TRANSFER,1000.00,INR,acc_bank,acc_cash,,2026-09-15T12:00:00Z,ATM Withdrawal,,false",
            lines[1]
        )
    }

    @Test
    fun write_rfc4180Escaping_handlesCommasQuotesAndNewlines() {
        val txWithComma = Transaction(
            id = EntityId("tx_3"),
            amount = Money.of(BigDecimal("12.99"), Currency.USD),
            type = TransactionType.EXPENSE,
            sourceAccountId = EntityId("acc_1"),
            timestamp = Instant.parse("2026-09-15T00:00:00Z"),
            note = "Apples, Oranges, and Bananas"
        )
        val txWithQuote = Transaction(
            id = EntityId("tx_4"),
            amount = Money.of(BigDecimal("25.00"), Currency.USD),
            type = TransactionType.EXPENSE,
            sourceAccountId = EntityId("acc_1"),
            timestamp = Instant.parse("2026-09-15T00:00:00Z"),
            note = "Dinner with \"Family\""
        )
        val txWithNewline = Transaction(
            id = EntityId("tx_5"),
            amount = Money.of(BigDecimal("5.00"), Currency.USD),
            type = TransactionType.EXPENSE,
            sourceAccountId = EntityId("acc_1"),
            timestamp = Instant.parse("2026-09-15T00:00:00Z"),
            note = "Line 1\nLine 2"
        )

        val result = CsvTransactionWriter.writeToString(listOf(txWithComma, txWithQuote, txWithNewline))
        assertTrue(result.contains("\"Apples, Oranges, and Bananas\""))
        assertTrue(result.contains("\"Dinner with \"\"Family\"\"\""))
        assertTrue(result.contains("\"Line 1\nLine 2\""))
    }

    @Test
    fun write_preservesExactDecimalPrecision() {
        val tx = Transaction(
            id = EntityId("tx_exact"),
            amount = Money.of(BigDecimal("999999999999.123456"), Currency.USD),
            type = TransactionType.EXPENSE,
            sourceAccountId = EntityId("acc_1"),
            timestamp = Instant.parse("2026-09-15T00:00:00Z")
        )

        val result = CsvTransactionWriter.writeToString(listOf(tx))
        assertTrue(result.contains("999999999999.123456"))
    }
}
