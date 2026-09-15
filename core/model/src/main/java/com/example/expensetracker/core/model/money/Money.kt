package com.example.expensetracker.core.model.money

import java.math.BigDecimal

data class Money(
    val amount: BigDecimal,
    val currency: Currency
) : Comparable<Money> {

    val isZero: Boolean
        get() = amount.compareTo(BigDecimal.ZERO) == 0

    val isPositive: Boolean
        get() = amount > BigDecimal.ZERO

    val isNegative: Boolean
        get() = amount < BigDecimal.ZERO

    operator fun plus(other: Money): Money {
        require(currency == other.currency) {
            "Cannot add money with different currencies: ${currency.code} and ${other.currency.code}"
        }
        return copy(amount = amount.add(other.amount))
    }

    operator fun minus(other: Money): Money {
        require(currency == other.currency) {
            "Cannot subtract money with different currencies: ${currency.code} and ${other.currency.code}"
        }
        return copy(amount = amount.subtract(other.amount))
    }

    operator fun unaryMinus(): Money {
        return copy(amount = amount.negate())
    }

    operator fun times(multiplicand: Int): Money {
        return copy(amount = amount.multiply(BigDecimal(multiplicand)))
    }

    operator fun times(multiplicand: BigDecimal): Money {
        return copy(amount = amount.multiply(multiplicand))
    }

    override fun compareTo(other: Money): Int {
        require(currency == other.currency) {
            "Cannot compare money with different currencies: ${currency.code} and ${other.currency.code}"
        }
        return amount.compareTo(other.amount)
    }

    companion object {
        fun zero(currency: Currency): Money = Money(BigDecimal.ZERO, currency)

        fun of(amount: Long, currency: Currency): Money =
            Money(BigDecimal.valueOf(amount), currency)

        fun of(amount: String, currency: Currency): Money =
            Money(BigDecimal(amount), currency)

        fun of(amount: BigDecimal, currency: Currency): Money =
            Money(amount, currency)

        fun parseAndValidateAmount(
            amountInput: String,
            currency: Currency
        ): MonetaryValidationResult {
            val trimmed = amountInput.trim()
            if (trimmed.isEmpty()) {
                return MonetaryValidationResult.Error("Amount is required")
            }

            val parsed = try {
                BigDecimal(trimmed)
            } catch (e: NumberFormatException) {
                return MonetaryValidationResult.Error("Invalid amount format")
            }

            if (parsed <= BigDecimal.ZERO) {
                return MonetaryValidationResult.Error("Amount must be greater than zero")
            }

            val maxFractionDigits = runCatching {
                java.util.Currency.getInstance(currency.code).defaultFractionDigits
            }.getOrDefault(2)

            val scale = parsed.stripTrailingZeros().scale()
            if (scale > maxFractionDigits) {
                return MonetaryValidationResult.Error(
                    if (maxFractionDigits == 0) {
                        "Currency ${currency.code} does not support decimal places"
                    } else {
                        "Amount cannot exceed $maxFractionDigits decimal places"
                    }
                )
            }

            return MonetaryValidationResult.Success(parsed)
        }
    }
}

sealed interface MonetaryValidationResult {
    data class Success(val amount: BigDecimal) : MonetaryValidationResult
    data class Error(val message: String) : MonetaryValidationResult
}
