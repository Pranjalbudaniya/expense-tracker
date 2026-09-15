package com.example.expensetracker.core.data.defaults

import com.example.expensetracker.core.model.account.Account
import com.example.expensetracker.core.model.account.AccountType
import com.example.expensetracker.core.model.common.EntityId
import com.example.expensetracker.core.model.money.Currency
import com.example.expensetracker.core.model.money.Money

/**
 * Sensible default accounts and payment methods.
 */
object DefaultAccounts {

    val ID_CASH = EntityId("acc_default_cash")
    val ID_BANK = EntityId("acc_default_bank")
    val ID_UPI = EntityId("acc_default_upi")
    val ID_WALLET = EntityId("acc_default_wallet")

    fun getDefaultAccounts(currency: Currency = Currency.INR): List<Account> = listOf(
        Account(
            id = ID_CASH,
            name = "Cash",
            type = AccountType.CASH,
            currency = currency,
            initialBalance = Money.zero(currency),
            currentBalance = Money.zero(currency),
            isArchived = false
        ),
        Account(
            id = ID_BANK,
            name = "Bank Account",
            type = AccountType.BANK,
            currency = currency,
            initialBalance = Money.zero(currency),
            currentBalance = Money.zero(currency),
            isArchived = false
        ),
        Account(
            id = ID_UPI,
            name = "UPI",
            type = AccountType.UPI,
            currency = currency,
            initialBalance = Money.zero(currency),
            currentBalance = Money.zero(currency),
            isArchived = false
        ),
        Account(
            id = ID_WALLET,
            name = "Wallet",
            type = AccountType.WALLET,
            currency = currency,
            initialBalance = Money.zero(currency),
            currentBalance = Money.zero(currency),
            isArchived = false
        )
    )

    val ALL: List<Account> = getDefaultAccounts(Currency.INR)
}
