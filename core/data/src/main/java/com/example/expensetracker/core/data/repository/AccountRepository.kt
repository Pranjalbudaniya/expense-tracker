package com.example.expensetracker.core.data.repository

import com.example.expensetracker.core.model.account.Account
import com.example.expensetracker.core.model.common.EntityId
import kotlinx.coroutines.flow.Flow

interface AccountRepository {
    fun getActiveAccounts(): Flow<List<Account>>
    fun getAllAccounts(): Flow<List<Account>>
    fun getAccount(id: EntityId): Flow<Account?>
    suspend fun getAccountById(id: EntityId): Account?
    suspend fun insertAccount(account: Account)
    suspend fun updateAccount(account: Account)
    suspend fun archiveAccount(id: EntityId)
    suspend fun deleteAccount(account: Account)
}
