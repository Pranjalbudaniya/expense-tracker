package com.example.expensetracker.feature.export

import com.example.expensetracker.core.data.repository.AccountRepository
import com.example.expensetracker.core.data.repository.CategoryRepository
import com.example.expensetracker.core.data.repository.TransactionRepository
import com.example.expensetracker.core.model.transaction.Transaction
import com.example.expensetracker.core.model.transaction.TransactionType
import kotlinx.coroutines.flow.first
import java.io.InputStream
import java.io.OutputStream
import javax.inject.Inject
import javax.inject.Singleton

enum class ExportFilter {
    ALL,
    ACTIVE_ONLY,
    TRASH_ONLY
}

enum class ImportMode {
    MERGE,
    REPLACE
}

data class CsvImportAnalysis(
    val totalRows: Int,
    val validTransactions: List<Transaction>,
    val invalidRows: List<MalformedRow>,
    val newTransactionsCount: Int,
    val updateTransactionsCount: Int,
    val existingTransactionsCount: Int,
    val globalError: String? = null
)

data class ImportExecutionResult(
    val insertedCount: Int,
    val replacedCount: Int,
    val mode: ImportMode
)

@Singleton
class TransactionExportService @Inject constructor(
    private val transactionRepository: TransactionRepository,
    private val accountRepository: AccountRepository,
    private val categoryRepository: CategoryRepository
) {

    suspend fun exportTransactions(
        filter: ExportFilter,
        outputStream: OutputStream
    ): Result<Int> = runCatching {
        val transactions = when (filter) {
            ExportFilter.ALL -> transactionRepository.getAllTransactions().first()
            ExportFilter.ACTIVE_ONLY -> transactionRepository.getActiveTransactions().first()
            ExportFilter.TRASH_ONLY -> transactionRepository.getDeletedTransactions().first()
        }
        CsvTransactionWriter.write(transactions, outputStream)
        transactions.size
    }

    suspend fun analyzeCsvForImport(inputStream: InputStream): CsvImportAnalysis {
        val parseResult = CsvTransactionReader.read(inputStream)
        if (parseResult.globalError != null) {
            return CsvImportAnalysis(
                totalRows = 0,
                validTransactions = emptyList(),
                invalidRows = emptyList(),
                newTransactionsCount = 0,
                updateTransactionsCount = 0,
                existingTransactionsCount = 0,
                globalError = parseResult.globalError
            )
        }

        val allAccounts = accountRepository.getAllAccounts().first()
        val accountMap = allAccounts.associateBy { it.id }

        val allCategories = categoryRepository.getAllCategories().first()
        val categoryIds = allCategories.map { it.id }.toSet()

        val existingTxs = transactionRepository.getAllTransactions().first()
        val existingTxIds = existingTxs.map { it.id }.toSet()

        val verifiedValidTransactions = mutableListOf<Transaction>()
        val allInvalidRows = parseResult.invalidRows.toMutableList()

        var newCount = 0
        var updateCount = 0

        for (row in parseResult.validRows) {
            val tx = row.transaction
            val rawPreview = "${tx.id.value},${tx.type},${tx.amount.amount}"

            val srcAccount = accountMap[tx.sourceAccountId]
            if (srcAccount == null) {
                allInvalidRows.add(
                    MalformedRow(
                        row.lineNumber,
                        rawPreview,
                        "Source account '${tx.sourceAccountId.value}' does not exist."
                    )
                )
                continue
            }

            val destId = tx.destinationAccountId
            val destAccount = destId?.let { accountMap[it] }
            if (destId != null && destAccount == null) {
                allInvalidRows.add(
                    MalformedRow(
                        row.lineNumber,
                        rawPreview,
                        "Destination account '${destId.value}' does not exist."
                    )
                )
                continue
            }

            if (tx.type == TransactionType.TRANSFER) {
                if (destAccount != null && !srcAccount.currency.code.equals(destAccount.currency.code, ignoreCase = true)) {
                    allInvalidRows.add(
                        MalformedRow(
                            row.lineNumber,
                            rawPreview,
                            "Transfer between differing currencies is not supported (source: ${srcAccount.currency.code}, destination: ${destAccount.currency.code})."
                        )
                    )
                    continue
                }
            }

            val catId = tx.categoryId
            if (catId != null && !categoryIds.contains(catId)) {
                allInvalidRows.add(
                    MalformedRow(
                        row.lineNumber,
                        rawPreview,
                        "Category '${catId.value}' does not exist."
                    )
                )
                continue
            }

            verifiedValidTransactions.add(tx)
            if (existingTxIds.contains(tx.id)) {
                updateCount++
            } else {
                newCount++
            }
        }

        return CsvImportAnalysis(
            totalRows = parseResult.totalRowsProcessed,
            validTransactions = verifiedValidTransactions,
            invalidRows = allInvalidRows.sortedBy { it.lineNumber },
            newTransactionsCount = newCount,
            updateTransactionsCount = updateCount,
            existingTransactionsCount = existingTxs.size
        )
    }

    suspend fun executeImport(
        transactions: List<Transaction>,
        mode: ImportMode
    ): Result<ImportExecutionResult> = runCatching {
        val existingTxs = transactionRepository.getAllTransactions().first()
        val existingCount = existingTxs.size

        if (mode == ImportMode.REPLACE) {
            transactionRepository.deleteAllTransactions()
            transactionRepository.insertTransactions(transactions)
            ImportExecutionResult(
                insertedCount = transactions.size,
                replacedCount = existingCount,
                mode = mode
            )
        } else {
            // MERGE: Preserve local soft-deleted state if existing transaction is already deleted
            val existingDeletedMap = existingTxs.associate { it.id to it.isDeleted }
            val transactionsToInsert = transactions.map { tx ->
                val wasDeletedLocally = existingDeletedMap[tx.id] == true
                if (wasDeletedLocally || tx.isDeleted) {
                    tx.copy(isDeleted = true)
                } else {
                    tx
                }
            }
            transactionRepository.insertTransactions(transactionsToInsert)
            ImportExecutionResult(
                insertedCount = transactions.size,
                replacedCount = 0,
                mode = mode
            )
        }
    }
}
