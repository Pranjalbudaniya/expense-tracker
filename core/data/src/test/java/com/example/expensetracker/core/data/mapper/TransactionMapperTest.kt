package com.example.expensetracker.core.data.mapper

import com.example.expensetracker.core.database.entity.TransactionEntity
import com.example.expensetracker.core.model.common.EntityId
import com.example.expensetracker.core.model.money.Currency
import com.example.expensetracker.core.model.money.Money
import com.example.expensetracker.core.model.transaction.Transaction
import com.example.expensetracker.core.model.transaction.TransactionType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.math.BigDecimal
import java.time.Instant

class TransactionMapperTest {

    @Test
    fun testTransactionEntityToDomain() {
        val timestamp = Instant.ofEpochMilli(1720000000000L)
        val entity = TransactionEntity(
            id = "tx-100",
            amountMinor = 4599L,
            currencyCode = "USD",
            type = TransactionType.EXPENSE,
            sourceAccountId = "acc-1",
            destinationAccountId = null,
            categoryId = "cat-food",
            timestamp = timestamp,
            note = "Dinner",
            recurringTransactionId = "rec-1",
            isDeleted = false
        )

        val domain = entity.toDomain()

        assertEquals(EntityId("tx-100"), domain.id)
        assertEquals(BigDecimal("45.99"), domain.amount.amount)
        assertEquals("USD", domain.amount.currency.code)
        assertEquals(TransactionType.EXPENSE, domain.type)
        assertEquals(EntityId("acc-1"), domain.sourceAccountId)
        assertNull(domain.destinationAccountId)
        assertEquals(EntityId("cat-food"), domain.categoryId)
        assertEquals(timestamp, domain.timestamp)
        assertEquals("Dinner", domain.note)
        assertEquals(EntityId("rec-1"), domain.recurringTransactionId)
        assertFalse(domain.isDeleted)
    }

    @Test
    fun testTransactionDomainToEntity() {
        val timestamp = Instant.ofEpochMilli(1720000000000L)
        val domain = Transaction(
            id = EntityId("tx-200"),
            amount = Money(BigDecimal("1200.00"), Currency.EUR),
            type = TransactionType.INCOME,
            sourceAccountId = EntityId("acc-salary"),
            destinationAccountId = null,
            categoryId = EntityId("cat-work"),
            timestamp = timestamp,
            note = "Monthly salary",
            recurringTransactionId = null,
            isDeleted = false
        )

        val entity = domain.toEntity()

        assertEquals("tx-200", entity.id)
        assertEquals(120000L, entity.amountMinor)
        assertEquals("EUR", entity.currencyCode)
        assertEquals(TransactionType.INCOME, entity.type)
        assertEquals("acc-salary", entity.sourceAccountId)
        assertNull(entity.destinationAccountId)
        assertEquals("cat-work", entity.categoryId)
        assertEquals(timestamp, entity.timestamp)
        assertEquals("Monthly salary", entity.note)
        assertNull(entity.recurringTransactionId)
        assertFalse(entity.isDeleted)
    }

    @Test
    fun testNullableTransactionFields() {
        val timestamp = Instant.now()
        // Transfer without category, empty note, null recurring
        val domainTransfer = Transaction(
            id = EntityId("tx-transfer"),
            amount = Money(BigDecimal("500.00"), Currency.USD),
            type = TransactionType.TRANSFER,
            sourceAccountId = EntityId("acc-checking"),
            destinationAccountId = EntityId("acc-savings"),
            categoryId = null,
            timestamp = timestamp,
            note = "",
            recurringTransactionId = null,
            isDeleted = false
        )

        val entity = domainTransfer.toEntity()
        assertNull(entity.categoryId)
        assertEquals("acc-savings", entity.destinationAccountId)
        assertEquals("", entity.note)
        assertNull(entity.recurringTransactionId)

        val mappedBack = entity.toDomain()
        assertNull(mappedBack.categoryId)
        assertEquals(EntityId("acc-savings"), mappedBack.destinationAccountId)
        assertEquals("", mappedBack.note)
        assertNull(mappedBack.recurringTransactionId)
    }

    @Test
    fun testTransactionTypeMapping() {
        for (type in TransactionType.entries) {
            val domain = Transaction(
                id = EntityId("tx-$type"),
                amount = Money(BigDecimal("10.00"), Currency.USD),
                type = type,
                sourceAccountId = EntityId("acc-1"),
                timestamp = Instant.now()
            )
            val entity = domain.toEntity()
            assertEquals(type, entity.type)
            val mappedBack = entity.toDomain()
            assertEquals(type, mappedBack.type)
        }
    }

    @Test
    fun testMoneyRepresentationMappingAcrossCurrencies() {
        // Zero decimals: JPY 5000 -> 5000L
        val jpy = Transaction(
            id = EntityId("tx-jpy"),
            amount = Money(BigDecimal("5000"), Currency.JPY),
            type = TransactionType.EXPENSE,
            sourceAccountId = EntityId("acc-jpy"),
            timestamp = Instant.now()
        )
        val jpyEntity = jpy.toEntity()
        assertEquals(5000L, jpyEntity.amountMinor)
        assertEquals(BigDecimal("5000"), jpyEntity.toDomain().amount.amount)

        // Three decimals: KWD 15.500 -> 15500L
        val kwd = Transaction(
            id = EntityId("tx-kwd"),
            amount = Money(BigDecimal("15.500"), Currency.fromCode("KWD")),
            type = TransactionType.EXPENSE,
            sourceAccountId = EntityId("acc-kwd"),
            timestamp = Instant.now()
        )
        val kwdEntity = kwd.toEntity()
        assertEquals(15500L, kwdEntity.amountMinor)
        assertEquals(BigDecimal("15.500"), kwdEntity.toDomain().amount.amount)

        // Negative balance/amount: USD -25.50 -> -2550L
        val negative = Transaction(
            id = EntityId("tx-neg"),
            amount = Money(BigDecimal("-25.50"), Currency.USD),
            type = TransactionType.EXPENSE,
            sourceAccountId = EntityId("acc-1"),
            timestamp = Instant.now()
        )
        val negEntity = negative.toEntity()
        assertEquals(-2550L, negEntity.amountMinor)
        assertEquals(BigDecimal("-25.50"), negEntity.toDomain().amount.amount)
    }

    @Test
    fun testSoftDeleteTrashStateMapping() {
        val deletedDomain = Transaction(
            id = EntityId("tx-deleted"),
            amount = Money(BigDecimal("10.00"), Currency.USD),
            type = TransactionType.EXPENSE,
            sourceAccountId = EntityId("acc-1"),
            timestamp = Instant.now(),
            isDeleted = true
        )

        val entity = deletedDomain.toEntity()
        assertTrue(entity.isDeleted)

        val mappedBack = entity.toDomain()
        assertTrue(mappedBack.isDeleted)
    }
}
