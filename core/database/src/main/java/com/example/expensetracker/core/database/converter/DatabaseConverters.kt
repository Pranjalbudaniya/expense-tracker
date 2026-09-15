package com.example.expensetracker.core.database.converter

import androidx.room.TypeConverter
import com.example.expensetracker.core.model.account.AccountType
import com.example.expensetracker.core.model.category.CategoryType
import com.example.expensetracker.core.model.money.Currency
import com.example.expensetracker.core.model.money.Money
import com.example.expensetracker.core.model.recurring.RecurrenceFrequency
import com.example.expensetracker.core.model.transaction.TransactionType
import java.math.BigDecimal
import java.math.RoundingMode
import java.time.Instant
import java.time.LocalDate

class DatabaseConverters {

    @TypeConverter
    fun fromInstant(instant: Instant?): Long? = instant?.toEpochMilli()

    @TypeConverter
    fun toInstant(millis: Long?): Instant? = millis?.let { Instant.ofEpochMilli(it) }

    @TypeConverter
    fun fromLocalDate(date: LocalDate?): String? = date?.toString()

    @TypeConverter
    fun toLocalDate(value: String?): LocalDate? = value?.let { LocalDate.parse(it) }

    @TypeConverter
    fun fromTransactionType(type: TransactionType?): String? = type?.name

    @TypeConverter
    fun toTransactionType(value: String?): TransactionType? = value?.let { TransactionType.valueOf(it) }

    @TypeConverter
    fun fromAccountType(type: AccountType?): String? = type?.name

    @TypeConverter
    fun toAccountType(value: String?): AccountType? = value?.let { AccountType.valueOf(it) }

    @TypeConverter
    fun fromRecurrenceFrequency(frequency: RecurrenceFrequency?): String? = frequency?.name

    @TypeConverter
    fun toRecurrenceFrequency(value: String?): RecurrenceFrequency? = value?.let { RecurrenceFrequency.valueOf(it) }

    @TypeConverter
    fun fromCategoryType(type: CategoryType?): String? = type?.name

    @TypeConverter
    fun toCategoryType(value: String?): CategoryType? = value?.let {
        runCatching { CategoryType.valueOf(it) }.getOrDefault(CategoryType.EXPENSE)
    }

    companion object {
        fun toMinorUnits(money: Money): Long {
            val fractionDigits = getCurrencyFractionDigits(money.currency.code)
            return money.amount
                .movePointRight(fractionDigits)
                .setScale(0, RoundingMode.HALF_UP)
                .longValueExact()
        }

        fun toMoney(minorUnits: Long, currencyCode: String): Money {
            val fractionDigits = getCurrencyFractionDigits(currencyCode)
            val amount = BigDecimal.valueOf(minorUnits).movePointLeft(fractionDigits)
            return Money(amount, Currency.fromCode(currencyCode))
        }

        fun getCurrencyFractionDigits(currencyCode: String): Int {
            return runCatching {
                val fractionDigits = java.util.Currency.getInstance(currencyCode.uppercase()).defaultFractionDigits
                if (fractionDigits >= 0) fractionDigits else 2
            }.getOrDefault(2)
        }
    }
}
