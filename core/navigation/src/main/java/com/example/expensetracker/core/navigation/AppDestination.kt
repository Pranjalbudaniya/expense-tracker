package com.example.expensetracker.core.navigation

import kotlinx.serialization.Serializable

/**
 * Top-level application destinations for type-safe Navigation Compose.
 */
sealed interface AppDestination {

    /**
     * Root home destination.
     */
    @Serializable
    data object Home : AppDestination

    /**
     * Add transaction destination.
     */
    @Serializable
    data object AddTransaction : AppDestination

    /**
     * Transaction history destination.
     */
    @Serializable
    data object Transactions : AppDestination

    /**
     * Transaction details and editing destination.
     *
     * @property transactionId The ID of the transaction to view/edit.
     */
    @Serializable
    data class TransactionDetails(val transactionId: String) : AppDestination

    /**
     * Accounts management destination.
     */
    @Serializable
    data object Accounts : AppDestination

    /**
     * Categories management destination.
     */
    @Serializable
    data object Categories : AppDestination

    /**
     * Recurring transactions destination.
     */
    @Serializable
    data object Recurring : AppDestination

    /**
     * Statistics and spending analytics destination.
     */
    @Serializable
    data object Statistics : AppDestination

    /**
     * CSV Export and Import destination.
     */
    @Serializable
    data object Export : AppDestination

    /**
     * Dedicated Trash destination for deleted transactions.
     */
    @Serializable
    data object Trash : AppDestination

    /**
     * App lock and biometric security settings destination.
     */
    @Serializable
    data object Security : AppDestination

    /**
     * Notification preferences and alert configuration destination.
     */
    @Serializable
    data object Notifications : AppDestination

    /**
     * Full local backup and restore destination.
     */
    @Serializable
    data object Backup : AppDestination
}
