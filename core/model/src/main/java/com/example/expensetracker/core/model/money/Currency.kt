package com.example.expensetracker.core.model.money

data class Currency(
    val code: String,
    val symbol: String,
    val displayName: String
) {
    companion object {
        val USD = Currency(code = "USD", symbol = "$", displayName = "US Dollar")
        val EUR = Currency(code = "EUR", symbol = "€", displayName = "Euro")
        val GBP = Currency(code = "GBP", symbol = "£", displayName = "British Pound")
        val INR = Currency(code = "INR", symbol = "₹", displayName = "Indian Rupee")
        val JPY = Currency(code = "JPY", symbol = "¥", displayName = "Japanese Yen")

        fun fromCode(code: String): Currency {
            return runCatching {
                val javaCurrency = java.util.Currency.getInstance(code.uppercase())
                Currency(
                    code = javaCurrency.currencyCode,
                    symbol = javaCurrency.symbol,
                    displayName = javaCurrency.displayName
                )
            }.getOrDefault(
                Currency(
                    code = code.uppercase(),
                    symbol = code.uppercase(),
                    displayName = code.uppercase()
                )
            )
        }
    }
}
