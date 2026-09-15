package com.example.expensetracker.feature.backup

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class BackupReaderTest {

    private lateinit var reader: BackupReader

    @Before
    fun setUp() {
        reader = BackupReader()
    }

    @Test
    fun parseAndValidate_validBackup_returnsValidResult() {
        val validJson = """
            {
              "version": 1,
              "exportedAtEpochMillis": 1700000000000,
              "appVersionName": "1.0.0",
              "accounts": [
                {
                  "id": "acc_1",
                  "name": "Main Checking",
                  "type": "BANK",
                  "currencyCode": "USD",
                  "initialBalanceMinor": 500000,
                  "currentBalanceMinor": 450000,
                  "isArchived": false
                }
              ],
              "categories": [
                {
                  "id": "cat_1",
                  "name": "Dining",
                  "iconKey": "restaurant",
                  "colorKey": "orange",
                  "isDefault": false,
                  "isArchived": false,
                  "type": "EXPENSE",
                  "orderIndex": 0
                }
              ],
              "transactions": [
                {
                  "id": "tx_1",
                  "amountMinor": 2500,
                  "currencyCode": "USD",
                  "type": "EXPENSE",
                  "sourceAccountId": "acc_1",
                  "destinationAccountId": null,
                  "categoryId": "cat_1",
                  "timestampEpochMillis": 1700000050000,
                  "note": "Lunch with friends",
                  "recurringTransactionId": null,
                  "isDeleted": false
                }
              ],
              "budgets": [
                {
                  "id": "bgt_1",
                  "name": "Monthly Dining",
                  "targetAmountMinor": 30000,
                  "currencyCode": "USD",
                  "categoryId": "cat_1",
                  "startDateIso": "2026-09-01",
                  "endDateIso": "2026-09-30",
                  "isEnabled": true
                }
              ],
              "recurringTransactions": [
                {
                  "id": "rec_1",
                  "amountMinor": 10000,
                  "currencyCode": "USD",
                  "type": "EXPENSE",
                  "sourceAccountId": "acc_1",
                  "destinationAccountId": null,
                  "categoryId": "cat_1",
                  "note": "Monthly Subscription",
                  "frequency": "MONTHLY",
                  "interval": 1,
                  "nextOccurrenceIso": "2026-10-01",
                  "startDateIso": "2026-01-01",
                  "endDateIso": null,
                  "isEnabled": true
                }
              ],
              "preferences": {
                "themeMode": "DARK",
                "isDynamicColorEnabled": true,
                "customAccentColor": 4278190080,
                "currencyCode": "USD",
                "notificationsEnabled": true,
                "recurringNotificationsEnabled": true,
                "budgetAlertsEnabled": true,
                "budgetThresholdPercent": 85
              }
            }
        """.trimIndent()

        val result = reader.parseAndValidate(validJson)
        assertTrue(result is BackupValidationResult.Valid)
        val valid = result as BackupValidationResult.Valid
        assertEquals(1, valid.summary.accountsCount)
        assertEquals(1, valid.summary.categoriesCount)
        assertEquals(1, valid.summary.transactionsCount)
        assertEquals(1, valid.summary.activeTransactionsCount)
        assertEquals(0, valid.summary.trashTransactionsCount)
        assertEquals(1, valid.summary.budgetsCount)
        assertEquals(1, valid.summary.recurringCount)
        assertTrue(valid.summary.hasPreferences)
    }

    @Test
    fun parseAndValidate_emptyString_returnsInvalid() {
        val result = reader.parseAndValidate("   ")
        assertTrue(result is BackupValidationResult.Invalid)
        val invalid = result as BackupValidationResult.Invalid
        assertTrue(invalid.errors.any { it.contains("empty", ignoreCase = true) })
    }

    @Test
    fun parseAndValidate_malformedJson_returnsInvalid() {
        val result = reader.parseAndValidate("{ invalid json structure")
        assertTrue(result is BackupValidationResult.Invalid)
        val invalid = result as BackupValidationResult.Invalid
        assertTrue(invalid.errors.any { it.contains("Malformed", ignoreCase = true) })
    }

    @Test
    fun parseAndValidate_futureVersion_returnsInvalid() {
        val futureJson = """
            {
              "version": 999,
              "exportedAtEpochMillis": 1700000000000,
              "appVersionName": "99.0.0",
              "accounts": [],
              "categories": [],
              "transactions": [],
              "budgets": [],
              "recurringTransactions": [],
              "preferences": null
            }
        """.trimIndent()

        val result = reader.parseAndValidate(futureJson)
        assertTrue(result is BackupValidationResult.Invalid)
        val invalid = result as BackupValidationResult.Invalid
        assertTrue(invalid.errors.any { it.contains("version 999 is not supported", ignoreCase = true) })
    }

    @Test
    fun parseAndValidate_duplicateIds_reportsError() {
        val duplicateJson = """
            {
              "version": 1,
              "exportedAtEpochMillis": 1700000000000,
              "appVersionName": "1.0.0",
              "accounts": [
                { "id": "acc_dup", "name": "A1", "type": "BANK", "currencyCode": "USD", "initialBalanceMinor": 0, "currentBalanceMinor": 0, "isArchived": false },
                { "id": "acc_dup", "name": "A2", "type": "BANK", "currencyCode": "USD", "initialBalanceMinor": 0, "currentBalanceMinor": 0, "isArchived": false }
              ],
              "categories": [],
              "transactions": [],
              "budgets": [],
              "recurringTransactions": [],
              "preferences": null
            }
        """.trimIndent()

        val result = reader.parseAndValidate(duplicateJson)
        assertTrue(result is BackupValidationResult.Invalid)
        val invalid = result as BackupValidationResult.Invalid
        assertTrue(invalid.errors.any { it.contains("Duplicate ID", ignoreCase = true) && it.contains("acc_dup") })
    }

    @Test
    fun parseAndValidate_brokenForeignReferences_reportsErrors() {
        val brokenRefsJson = """
            {
              "version": 1,
              "exportedAtEpochMillis": 1700000000000,
              "appVersionName": "1.0.0",
              "accounts": [
                { "id": "acc_valid", "name": "Checking", "type": "BANK", "currencyCode": "USD", "initialBalanceMinor": 0, "currentBalanceMinor": 0, "isArchived": false }
              ],
              "categories": [
                { "id": "cat_valid", "name": "Food", "iconKey": "fastfood", "colorKey": "red", "isDefault": false, "isArchived": false, "type": "EXPENSE", "orderIndex": 0 }
              ],
              "transactions": [
                {
                  "id": "tx_broken_source",
                  "amountMinor": 1000,
                  "currencyCode": "USD",
                  "type": "EXPENSE",
                  "sourceAccountId": "acc_nonexistent",
                  "destinationAccountId": null,
                  "categoryId": "cat_valid",
                  "timestampEpochMillis": 1700000000000,
                  "isDeleted": false
                },
                {
                  "id": "tx_broken_dest",
                  "amountMinor": 1000,
                  "currencyCode": "USD",
                  "type": "TRANSFER",
                  "sourceAccountId": "acc_valid",
                  "destinationAccountId": "acc_missing_dest",
                  "categoryId": null,
                  "timestampEpochMillis": 1700000000000,
                  "isDeleted": false
                },
                {
                  "id": "tx_broken_category",
                  "amountMinor": 1000,
                  "currencyCode": "USD",
                  "type": "EXPENSE",
                  "sourceAccountId": "acc_valid",
                  "destinationAccountId": null,
                  "categoryId": "cat_missing",
                  "timestampEpochMillis": 1700000000000,
                  "isDeleted": false
                }
              ],
              "budgets": [
                {
                  "id": "bgt_broken",
                  "name": "Ghost Budget",
                  "targetAmountMinor": 5000,
                  "currencyCode": "USD",
                  "categoryId": "cat_ghost",
                  "startDateIso": "2026-09-01",
                  "endDateIso": "2026-09-30",
                  "isEnabled": true
                }
              ],
              "recurringTransactions": [
                {
                  "id": "rec_broken",
                  "amountMinor": 5000,
                  "currencyCode": "USD",
                  "type": "EXPENSE",
                  "sourceAccountId": "acc_ghost",
                  "destinationAccountId": null,
                  "categoryId": "cat_ghost_2",
                  "note": "Ghost Recurring",
                  "frequency": "MONTHLY",
                  "interval": 1,
                  "nextOccurrenceIso": "2026-10-01",
                  "startDateIso": "2026-01-01",
                  "endDateIso": null,
                  "isEnabled": true
                }
              ],
              "preferences": null
            }
        """.trimIndent()

        val result = reader.parseAndValidate(brokenRefsJson)
        assertTrue(result is BackupValidationResult.Invalid)
        val invalid = result as BackupValidationResult.Invalid
        assertTrue(invalid.errors.any { it.contains("tx_broken_source") && it.contains("acc_nonexistent") })
        assertTrue(invalid.errors.any { it.contains("tx_broken_dest") && it.contains("acc_missing_dest") })
        assertTrue(invalid.errors.any { it.contains("tx_broken_category") && it.contains("cat_missing") })
        assertTrue(invalid.errors.any { it.contains("bgt_broken") && it.contains("cat_ghost") })
        assertTrue(invalid.errors.any { it.contains("rec_broken") && it.contains("acc_ghost") })
        assertTrue(invalid.errors.any { it.contains("rec_broken") && it.contains("cat_ghost_2") })
    }

    @Test
    fun parseAndValidate_invalidDatesAndAmounts_reportsErrors() {
        val invalidDataJson = """
            {
              "version": 1,
              "exportedAtEpochMillis": 1700000000000,
              "appVersionName": "1.0.0",
              "accounts": [
                { "id": "acc_1", "name": "Checking", "type": "BANK", "currencyCode": "INVALID_CURRENCY", "initialBalanceMinor": 0, "currentBalanceMinor": 0, "isArchived": false }
              ],
              "categories": [
                { "id": "cat_1", "name": "Food", "iconKey": "restaurant", "colorKey": "red", "isDefault": false, "isArchived": false, "type": "EXPENSE", "orderIndex": 0 }
              ],
              "transactions": [
                {
                  "id": "tx_neg",
                  "amountMinor": -500,
                  "currencyCode": "USD",
                  "type": "EXPENSE",
                  "sourceAccountId": "acc_1",
                  "destinationAccountId": null,
                  "categoryId": "cat_1",
                  "timestampEpochMillis": -10,
                  "isDeleted": false
                }
              ],
              "budgets": [
                {
                  "id": "bgt_bad_dates",
                  "name": "Bad Budget",
                  "targetAmountMinor": -100,
                  "currencyCode": "USD",
                  "categoryId": "cat_1",
                  "startDateIso": "2026-09-30",
                  "endDateIso": "2026-09-01",
                  "isEnabled": true
                }
              ],
              "recurringTransactions": [],
              "preferences": null
            }
        """.trimIndent()

        val result = reader.parseAndValidate(invalidDataJson)
        assertTrue(result is BackupValidationResult.Invalid)
        val invalid = result as BackupValidationResult.Invalid
        assertTrue(invalid.errors.any { it.contains("INVALID_CURRENCY") })
        assertTrue(invalid.errors.any { it.contains("negative amount") })
        assertTrue(invalid.errors.any { it.contains("invalid timestamp") })
        assertTrue(invalid.errors.any { it.contains("non-positive target amount") })
        assertTrue(invalid.errors.any { it.contains("start date (2026-09-30) after end date (2026-09-01)") })
    }

    @Test
    fun parseAndValidate_mismatchedTransferCurrencies_reportsErrors() {
        val mismatchedTransferJson = """
            {
              "version": 1,
              "exportedAtEpochMillis": 1700000000000,
              "appVersionName": "1.0.0",
              "accounts": [
                { "id": "acc_usd", "name": "USD Account", "type": "BANK", "currencyCode": "USD", "initialBalanceMinor": 10000, "currentBalanceMinor": 10000, "isArchived": false },
                { "id": "acc_eur", "name": "EUR Account", "type": "BANK", "currencyCode": "EUR", "initialBalanceMinor": 10000, "currentBalanceMinor": 10000, "isArchived": false }
              ],
              "categories": [],
              "transactions": [
                {
                  "id": "tx_cross_transfer",
                  "amountMinor": 5000,
                  "currencyCode": "USD",
                  "type": "TRANSFER",
                  "sourceAccountId": "acc_usd",
                  "destinationAccountId": "acc_eur",
                  "categoryId": null,
                  "timestampEpochMillis": 1700000050000,
                  "isDeleted": false
                }
              ],
              "budgets": [],
              "recurringTransactions": [
                {
                  "id": "rec_cross_transfer",
                  "amountMinor": 5000,
                  "currencyCode": "USD",
                  "type": "TRANSFER",
                  "sourceAccountId": "acc_usd",
                  "destinationAccountId": "acc_eur",
                  "categoryId": null,
                  "frequency": "MONTHLY",
                  "interval": 1,
                  "nextOccurrenceIso": "2026-10-01",
                  "startDateIso": "2026-01-01",
                  "endDateIso": null,
                  "isEnabled": true
                }
              ],
              "preferences": null
            }
        """.trimIndent()

        val result = reader.parseAndValidate(mismatchedTransferJson)
        assertTrue(result is BackupValidationResult.Invalid)
        val invalid = result as BackupValidationResult.Invalid
        assertTrue(invalid.errors.any { it.contains("Transfer transaction 'tx_cross_transfer' has mismatched account currencies") })
        assertTrue(invalid.errors.any { it.contains("Recurring transfer 'rec_cross_transfer' has mismatched account currencies") })
    }
}
