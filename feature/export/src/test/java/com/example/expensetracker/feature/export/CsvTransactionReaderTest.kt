package com.example.expensetracker.feature.export

import com.example.expensetracker.core.model.common.EntityId
import com.example.expensetracker.core.model.transaction.Transaction
import com.example.expensetracker.core.model.transaction.TransactionType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.math.BigDecimal
import java.time.Instant

class CsvTransactionReaderTest {

    @Test
    fun readFromString_emptyContent_returnsGlobalError() {
        val result = CsvTransactionReader.readFromString("")
        assertNotNull(result.globalError)
        assertEquals(0, result.totalRowsProcessed)
        assertTrue(result.validRows.isEmpty())
    }

    @Test
    fun readFromString_missingRequiredHeader_returnsGlobalError() {
        val csv = "version,id,type\r\n1,tx_1,EXPENSE"
        val result = CsvTransactionReader.readFromString(csv)
        assertNotNull(result.globalError)
        assertTrue(result.globalError!!.contains("Missing required CSV header"))
    }

    @Test
    fun readFromString_validCsv_parsesTransactionsAccurately() {
        val csv = """
            version,id,type,amount,currency,source_account_id,destination_account_id,category_id,timestamp,note,recurring_id,is_deleted
            1,tx_1,EXPENSE,150.75,USD,acc_bank,,cat_food,2026-09-15T10:30:00Z,Grocery run,,false
            1,tx_2,TRANSFER,500.00,USD,acc_bank,acc_cash,,2026-09-15T11:00:00Z,ATM,,false
        """.trimIndent()

        val result = CsvTransactionReader.readFromString(csv)
        assertNull(result.globalError)
        assertEquals(2, result.totalRowsProcessed)
        assertEquals(2, result.validRows.size)
        assertEquals(0, result.invalidRows.size)

        val first = result.validRows[0].transaction
        assertEquals(EntityId("tx_1"), first.id)
        assertEquals(TransactionType.EXPENSE, first.type)
        assertEquals(BigDecimal("150.75"), first.amount.amount)
        assertEquals("USD", first.amount.currency.code)
        assertEquals(EntityId("acc_bank"), first.sourceAccountId)
        assertNull(first.destinationAccountId)
        assertEquals(EntityId("cat_food"), first.categoryId)
        assertEquals(Instant.parse("2026-09-15T10:30:00Z"), first.timestamp)
        assertEquals("Grocery run", first.note)
        assertEquals(false, first.isDeleted)

        val second = result.validRows[1].transaction
        assertEquals(EntityId("tx_2"), second.id)
        assertEquals(TransactionType.TRANSFER, second.type)
        assertEquals(EntityId("acc_cash"), second.destinationAccountId)
    }

    @Test
    fun readFromString_rfc4180QuotedFields_handlesCommasQuotesAndNewlines() {
        val csv = "version,id,type,amount,currency,source_account_id,destination_account_id,category_id,timestamp,note,recurring_id,is_deleted\r\n" +
                "1,tx_1,EXPENSE,20.00,USD,acc_1,,,2026-09-15T10:00:00Z,\"Coffee, tea, and cookies\",,false\r\n" +
                "1,tx_2,EXPENSE,30.00,USD,acc_1,,,2026-09-15T10:00:00Z,\"Book: \"\"Kotlin in Action\"\"\",,false\r\n" +
                "1,tx_3,EXPENSE,40.00,USD,acc_1,,,2026-09-15T10:00:00Z,\"Item 1\nItem 2\",,false"

        val result = CsvTransactionReader.readFromString(csv)
        assertEquals(3, result.validRows.size)
        assertEquals("Coffee, tea, and cookies", result.validRows[0].transaction.note)
        assertEquals("Book: \"Kotlin in Action\"", result.validRows[1].transaction.note)
        assertEquals("Item 1\nItem 2", result.validRows[2].transaction.note)
    }

    @Test
    fun readFromString_malformedRows_identifiesErrorsWithLineNumbers() {
        val csv = """
            version,id,type,amount,currency,source_account_id,destination_account_id,category_id,timestamp,note,recurring_id,is_deleted
            2,tx_wrong_ver,EXPENSE,50.00,USD,acc_1,,,2026-09-15T00:00:00Z,,,false
            1,,EXPENSE,50.00,USD,acc_1,,,2026-09-15T00:00:00Z,,,false
            1,tx_inv_type,UNKNOWN,50.00,USD,acc_1,,,2026-09-15T00:00:00Z,,,false
            1,tx_inv_amt,EXPENSE,-10.00,USD,acc_1,,,2026-09-15T00:00:00Z,,,false
            1,tx_transfer_no_dest,TRANSFER,50.00,USD,acc_1,,,2026-09-15T00:00:00Z,,,false
            1,tx_transfer_same_acc,TRANSFER,50.00,USD,acc_1,acc_1,,2026-09-15T00:00:00Z,,,false
            1,tx_inv_time,EXPENSE,50.00,USD,acc_1,,,NOT_A_TIMESTAMP,,,false
            1,tx_valid,EXPENSE,99.99,USD,acc_1,,,2026-09-15T00:00:00Z,Valid note,,false
        """.trimIndent()

        val result = CsvTransactionReader.readFromString(csv)
        assertEquals(8, result.totalRowsProcessed)
        assertEquals(1, result.validRows.size)
        assertEquals(7, result.invalidRows.size)

        assertEquals("tx_valid", result.validRows[0].transaction.id.value)

        val invalidReasons = result.invalidRows.map { it.reason }
        assertTrue(invalidReasons.any { it.contains("Unsupported CSV version") })
        assertTrue(invalidReasons.any { it.contains("ID cannot be blank") })
        assertTrue(invalidReasons.any { it.contains("Invalid transaction type") })
        assertTrue(invalidReasons.any { it.contains("Invalid amount") })
        assertTrue(invalidReasons.any { it.contains("destination_account_id") })
        assertTrue(invalidReasons.any { it.contains("cannot be the same") })
        assertTrue(invalidReasons.any { it.contains("Invalid timestamp") })
    }

    @Test
    fun roundTrip_writeAndRead_preservesAllDataExactly() {
        val originalTxs = listOf(
            Transaction(
                id = EntityId("tx_rt_1"),
                amount = com.example.expensetracker.core.model.money.Money.of(BigDecimal("1234567.89"), com.example.expensetracker.core.model.money.Currency.EUR),
                type = TransactionType.INCOME,
                sourceAccountId = EntityId("acc_salary"),
                destinationAccountId = null,
                categoryId = EntityId("cat_work"),
                timestamp = Instant.parse("2026-09-15T08:00:00Z"),
                note = "Monthly salary, bonus & overtime",
                recurringTransactionId = EntityId("rec_salary"),
                isDeleted = false
            ),
            Transaction(
                id = EntityId("tx_rt_2"),
                amount = com.example.expensetracker.core.model.money.Money.of(BigDecimal("42.00"), com.example.expensetracker.core.model.money.Currency.USD),
                type = TransactionType.TRANSFER,
                sourceAccountId = EntityId("acc_checking"),
                destinationAccountId = EntityId("acc_savings"),
                categoryId = null,
                timestamp = Instant.parse("2026-09-15T09:00:00Z"),
                note = "Savings transfer",
                recurringTransactionId = null,
                isDeleted = true
            )
        )

        val csvString = CsvTransactionWriter.writeToString(originalTxs)
        val parseResult = CsvTransactionReader.readFromString(csvString)

        assertEquals(2, parseResult.validRows.size)
        assertEquals(0, parseResult.invalidRows.size)

        val readTxs = parseResult.validRows.map { it.transaction }
        assertEquals(originalTxs, readTxs)
    }
}
