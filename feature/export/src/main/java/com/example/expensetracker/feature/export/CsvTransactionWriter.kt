package com.example.expensetracker.feature.export

import com.example.expensetracker.core.model.transaction.Transaction
import java.io.BufferedWriter
import java.io.OutputStream
import java.io.OutputStreamWriter
import java.io.StringWriter
import java.io.Writer
import java.nio.charset.StandardCharsets

object CsvTransactionWriter {

    const val CURRENT_VERSION = 1

    val HEADERS = listOf(
        "version",
        "id",
        "type",
        "amount",
        "currency",
        "source_account_id",
        "destination_account_id",
        "category_id",
        "timestamp",
        "note",
        "recurring_id",
        "is_deleted"
    )

    fun write(transactions: List<Transaction>, outputStream: OutputStream) {
        val writer = BufferedWriter(OutputStreamWriter(outputStream, StandardCharsets.UTF_8))
        write(transactions, writer)
        writer.flush()
    }

    fun write(transactions: List<Transaction>, writer: Writer) {
        // Write header
        writer.write(HEADERS.joinToString(","))
        writer.write("\r\n")

        // Write rows
        for (tx in transactions) {
            val row = listOf(
                CURRENT_VERSION.toString(),
                tx.id.value,
                tx.type.name,
                tx.amount.amount.toPlainString(),
                tx.amount.currency.code,
                tx.sourceAccountId.value,
                tx.destinationAccountId?.value ?: "",
                tx.categoryId?.value ?: "",
                tx.timestamp.toString(),
                tx.note,
                tx.recurringTransactionId?.value ?: "",
                tx.isDeleted.toString()
            )
            val line = row.joinToString(",") { escapeCsvField(it) }
            writer.write(line)
            writer.write("\r\n")
        }
    }

    fun writeToString(transactions: List<Transaction>): String {
        val stringWriter = StringWriter()
        write(transactions, stringWriter)
        return stringWriter.toString()
    }

    fun escapeCsvField(value: String): String {
        val needsQuotes = value.contains(',') ||
                value.contains('"') ||
                value.contains('\n') ||
                value.contains('\r')
        return if (needsQuotes) {
            val escaped = value.replace("\"", "\"\"")
            "\"$escaped\""
        } else {
            value
        }
    }
}
