package com.example.expensetracker.feature.export

import com.example.expensetracker.core.model.common.EntityId
import com.example.expensetracker.core.model.money.Currency
import com.example.expensetracker.core.model.money.Money
import com.example.expensetracker.core.model.transaction.Transaction
import com.example.expensetracker.core.model.transaction.TransactionType
import java.io.BufferedReader
import java.io.InputStream
import java.io.InputStreamReader
import java.io.Reader
import java.io.StringReader
import java.math.BigDecimal
import java.nio.charset.StandardCharsets
import java.time.Instant

data class ParsedTransactionRow(
    val lineNumber: Int,
    val transaction: Transaction
)

data class MalformedRow(
    val lineNumber: Int,
    val rawContent: String,
    val reason: String
)

data class CsvParseResult(
    val validRows: List<ParsedTransactionRow>,
    val invalidRows: List<MalformedRow>,
    val totalRowsProcessed: Int,
    val headerVersion: Int? = null,
    val globalError: String? = null
)

object CsvTransactionReader {

    const val SUPPORTED_VERSION = 1

    private val REQUIRED_HEADERS = setOf(
        "version",
        "id",
        "type",
        "amount",
        "currency",
        "source_account_id",
        "timestamp"
    )

    fun read(inputStream: InputStream): CsvParseResult {
        val reader = BufferedReader(InputStreamReader(inputStream, StandardCharsets.UTF_8))
        return read(reader)
    }

    fun readFromString(csvContent: String): CsvParseResult {
        return read(StringReader(csvContent))
    }

    fun read(reader: Reader): CsvParseResult {
        val rawRecords = parseRecords(reader)
        if (rawRecords.isEmpty()) {
            return CsvParseResult(
                validRows = emptyList(),
                invalidRows = emptyList(),
                totalRowsProcessed = 0,
                globalError = "CSV file is empty"
            )
        }

        val headerEntry = rawRecords.first()
        val headerFields = headerEntry.second.map { it.trim().lowercase() }
        val headerMap = headerFields.mapIndexed { index, name -> name to index }.toMap()

        val missingHeaders = REQUIRED_HEADERS.filter { !headerMap.containsKey(it) }
        if (missingHeaders.isNotEmpty()) {
            return CsvParseResult(
                validRows = emptyList(),
                invalidRows = emptyList(),
                totalRowsProcessed = 0,
                globalError = "Missing required CSV header columns: ${missingHeaders.joinToString(", ")}"
            )
        }

        val validRows = mutableListOf<ParsedTransactionRow>()
        val invalidRows = mutableListOf<MalformedRow>()
        var detectedVersion: Int? = null

        val dataRecords = rawRecords.drop(1)
        for ((lineNum, fields) in dataRecords) {
            val rawPreview = fields.joinToString(",")
            val getField: (String) -> String? = { colName ->
                headerMap[colName]?.let { idx -> fields.getOrNull(idx) }
            }

            // Validate version
            val versionStr = getField("version")?.trim()
            val version = versionStr?.toIntOrNull()
            if (version == null) {
                invalidRows.add(
                    MalformedRow(lineNum, rawPreview, "Invalid or missing format version: '$versionStr'")
                )
                continue
            }
            if (version != SUPPORTED_VERSION) {
                invalidRows.add(
                    MalformedRow(lineNum, rawPreview, "Unsupported CSV version '$version'. Only version $SUPPORTED_VERSION is supported.")
                )
                continue
            }
            if (detectedVersion == null) {
                detectedVersion = version
            }

            // Validate ID
            val idStr = getField("id")?.trim().orEmpty()
            if (idStr.isBlank()) {
                invalidRows.add(MalformedRow(lineNum, rawPreview, "Transaction ID cannot be blank"))
                continue
            }

            // Validate Type
            val typeStr = getField("type")?.trim().orEmpty()
            val type = runCatching { TransactionType.valueOf(typeStr.uppercase()) }.getOrNull()
            if (type == null) {
                invalidRows.add(
                    MalformedRow(
                        lineNum,
                        rawPreview,
                        "Invalid transaction type '$typeStr'. Must be EXPENSE, INCOME, or TRANSFER."
                    )
                )
                continue
            }

            // Validate Amount (preserving exact BigDecimal, no float conversion)
            val amountStr = getField("amount")?.trim().orEmpty()
            val amount = runCatching { BigDecimal(amountStr) }.getOrNull()
            if (amount == null || amount <= BigDecimal.ZERO) {
                invalidRows.add(
                    MalformedRow(
                        lineNum,
                        rawPreview,
                        "Invalid amount '$amountStr'. Amount must be a positive decimal number."
                    )
                )
                continue
            }

            // Validate Currency
            val currencyStr = getField("currency")?.trim().orEmpty()
            if (currencyStr.isBlank()) {
                invalidRows.add(MalformedRow(lineNum, rawPreview, "Currency cannot be blank"))
                continue
            }
            val currency = Currency.fromCode(currencyStr)

            // Validate Source Account ID
            val sourceAccountIdStr = getField("source_account_id")?.trim().orEmpty()
            if (sourceAccountIdStr.isBlank()) {
                invalidRows.add(MalformedRow(lineNum, rawPreview, "Source account ID cannot be blank"))
                continue
            }
            val sourceAccountId = EntityId(sourceAccountIdStr)

            // Destination Account ID
            val destinationAccountIdStr = getField("destination_account_id")?.trim().orEmpty()
            val destinationAccountId = if (destinationAccountIdStr.isNotBlank()) {
                EntityId(destinationAccountIdStr)
            } else {
                null
            }

            if (type == TransactionType.TRANSFER) {
                if (destinationAccountId == null) {
                    invalidRows.add(
                        MalformedRow(
                            lineNum,
                            rawPreview,
                            "Transfer transaction must specify a destination_account_id"
                        )
                    )
                    continue
                }
                if (destinationAccountId == sourceAccountId) {
                    invalidRows.add(
                        MalformedRow(
                            lineNum,
                            rawPreview,
                            "Transfer source and destination accounts cannot be the same"
                        )
                    )
                    continue
                }
            }

            // Category ID
            val categoryIdStr = getField("category_id")?.trim().orEmpty()
            val categoryId = if (categoryIdStr.isNotBlank()) EntityId(categoryIdStr) else null

            // Timestamp (ISO-8601)
            val timestampStr = getField("timestamp")?.trim().orEmpty()
            val timestamp = runCatching { Instant.parse(timestampStr) }.getOrNull()
            if (timestamp == null) {
                invalidRows.add(
                    MalformedRow(
                        lineNum,
                        rawPreview,
                        "Invalid timestamp '$timestampStr'. Expected ISO-8601 format (e.g. 2026-09-15T00:00:00Z)"
                    )
                )
                continue
            }

            // Note
            val note = getField("note").orEmpty()

            // Recurring ID
            val recurringIdStr = getField("recurring_id")?.trim().orEmpty()
            val recurringId = if (recurringIdStr.isNotBlank()) EntityId(recurringIdStr) else null

            // Is Deleted
            val isDeletedStr = getField("is_deleted")?.trim().orEmpty()
            val isDeleted = isDeletedStr.toBooleanStrictOrNull() ?: false

            val tx = Transaction(
                id = EntityId(idStr),
                amount = Money.of(amount, currency),
                type = type,
                sourceAccountId = sourceAccountId,
                destinationAccountId = if (type == TransactionType.TRANSFER) destinationAccountId else null,
                categoryId = categoryId,
                timestamp = timestamp,
                note = note,
                recurringTransactionId = recurringId,
                isDeleted = isDeleted
            )
            validRows.add(ParsedTransactionRow(lineNumber = lineNum, transaction = tx))
        }

        return CsvParseResult(
            validRows = validRows,
            invalidRows = invalidRows,
            totalRowsProcessed = dataRecords.size,
            headerVersion = detectedVersion ?: SUPPORTED_VERSION
        )
    }

    /**
     * Parses RFC 4180 CSV characters into a list of (startLineNumber, List<String> fields).
     */
    fun parseRecords(reader: Reader): List<Pair<Int, List<String>>> {
        val records = mutableListOf<Pair<Int, List<String>>>()
        var currentLineNumber = 1
        var recordStartLine = 1
        var inQuotes = false
        val currentRecord = mutableListOf<String>()
        val currentField = StringBuilder()

        val pushField = {
            currentRecord.add(currentField.toString())
            currentField.setLength(0)
        }

        val pushRecord = {
            pushField()
            // Ignore empty lines
            if (currentRecord.size > 1 || (currentRecord.size == 1 && currentRecord[0].isNotBlank())) {
                records.add(recordStartLine to currentRecord.toList())
            }
            currentRecord.clear()
        }

        var ch = reader.read()
        while (ch != -1) {
            val c = ch.toChar()

            if (inQuotes) {
                if (c == '"') {
                    // Peek next char for escaped quote `""`
                    val nextCh = reader.read()
                    if (nextCh != -1 && nextCh.toChar() == '"') {
                        currentField.append('"')
                    } else {
                        inQuotes = false
                        ch = nextCh
                        continue
                    }
                } else {
                    if (c == '\n') {
                        currentLineNumber++
                    }
                    currentField.append(c)
                }
            } else {
                when (c) {
                    '"' -> {
                        inQuotes = true
                    }
                    ',' -> {
                        pushField()
                    }
                    '\r' -> {
                        // Check if followed by \n
                        val nextCh = reader.read()
                        if (nextCh != -1) {
                            if (nextCh.toChar() == '\n') {
                                currentLineNumber++
                            } else {
                                // Put back by setting ch to nextCh
                                pushRecord()
                                recordStartLine = currentLineNumber
                                ch = nextCh
                                continue
                            }
                        }
                        pushRecord()
                        recordStartLine = currentLineNumber
                    }
                    '\n' -> {
                        currentLineNumber++
                        pushRecord()
                        recordStartLine = currentLineNumber
                    }
                    else -> {
                        currentField.append(c)
                    }
                }
            }
            ch = reader.read()
        }

        if (currentField.isNotEmpty() || currentRecord.isNotEmpty()) {
            pushRecord()
        }

        return records
    }
}
