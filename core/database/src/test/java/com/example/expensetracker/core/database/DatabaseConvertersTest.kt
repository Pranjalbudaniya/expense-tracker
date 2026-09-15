package com.example.expensetracker.core.database

import com.example.expensetracker.core.database.converter.DatabaseConverters
import com.example.expensetracker.core.model.account.AccountType
import com.example.expensetracker.core.model.money.Currency
import com.example.expensetracker.core.model.money.Money
import com.example.expensetracker.core.model.recurring.RecurrenceFrequency
import com.example.expensetracker.core.model.transaction.TransactionType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.math.BigDecimal
import java.time.Instant
import java.time.LocalDate

class DatabaseConvertersTest {

    private val converters = DatabaseConverters()

    @Test
    fun testMoneyStoragePrecisionTwoDecimals() {
        val money = Money(BigDecimal("123.45"), Currency.USD)
        val minorUnits = DatabaseConverters.toMinorUnits(money)
        assertEquals(12345L, minorUnits)

        val restored = DatabaseConverters.toMoney(minorUnits, "USD")
        assertEquals(BigDecimal("123.45"), restored.amount)
        assertEquals("USD", restored.currency.code)
    }

    @Test
    fun testMoneyStoragePrecisionZeroDecimals() {
        val money = Money(BigDecimal("500"), Currency.JPY)
        val minorUnits = DatabaseConverters.toMinorUnits(money)
        assertEquals(500L, minorUnits)

        val restored = DatabaseConverters.toMoney(minorUnits, "JPY")
        assertEquals(BigDecimal("500"), restored.amount)
        assertEquals("JPY", restored.currency.code)
    }

    @Test
    fun testMoneyStoragePrecisionThreeDecimals() {
        val money = Money(BigDecimal("12.345"), Currency.fromCode("KWD"))
        val minorUnits = DatabaseConverters.toMinorUnits(money)
        assertEquals(12345L, minorUnits)

        val restored = DatabaseConverters.toMoney(minorUnits, "KWD")
        assertEquals(BigDecimal("12.345"), restored.amount)
        assertEquals("KWD", restored.currency.code)
    }

    @Test
    fun testMoneyStorageNegativeBalance() {
        val money = Money(BigDecimal("-99.99"), Currency.USD)
        val minorUnits = DatabaseConverters.toMinorUnits(money)
        assertEquals(-9999L, minorUnits)

        val restored = DatabaseConverters.toMoney(minorUnits, "USD")
        assertEquals(BigDecimal("-99.99"), restored.amount)
    }

    @Test
    fun testMoneyStorageZeroAmount() {
        val money = Money(BigDecimal.ZERO, Currency.INR)
        val minorUnits = DatabaseConverters.toMinorUnits(money)
        assertEquals(0L, minorUnits)

        val restored = DatabaseConverters.toMoney(minorUnits, "INR")
        assertEquals(BigDecimal.ZERO.setScale(2), restored.amount)
    }

    @Test
    fun testInstantConverter() {
        val now = Instant.ofEpochMilli(1700000000000L)
        val millis = converters.fromInstant(now)
        assertEquals(1700000000000L, millis)

        val restored = converters.toInstant(millis)
        assertEquals(now, restored)
        assertNull(converters.fromInstant(null))
        assertNull(converters.toInstant(null))
    }

    @Test
    fun testLocalDateConverter() {
        val date = LocalDate.of(2026, 9, 15)
        val str = converters.fromLocalDate(date)
        assertEquals("2026-09-15", str)

        val restored = converters.toLocalDate(str)
        assertEquals(date, restored)
        assertNull(converters.fromLocalDate(null))
        assertNull(converters.toLocalDate(null))
    }

    @Test
    fun testEnumConverters() {
        assertEquals("EXPENSE", converters.fromTransactionType(TransactionType.EXPENSE))
        assertEquals(TransactionType.TRANSFER, converters.toTransactionType("TRANSFER"))

        assertEquals("CREDIT_CARD", converters.fromAccountType(AccountType.CREDIT_CARD))
        assertEquals(AccountType.UPI, converters.toAccountType("UPI"))

        assertEquals("MONTHLY", converters.fromRecurrenceFrequency(RecurrenceFrequency.MONTHLY))
        assertEquals(RecurrenceFrequency.YEARLY, converters.toRecurrenceFrequency("YEARLY"))
    }
}
