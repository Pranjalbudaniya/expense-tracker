package com.example.expensetracker.core.data.repository.impl

import com.example.expensetracker.core.data.defaults.DefaultDataInitializer
import com.example.expensetracker.core.data.mapper.toDomain
import com.example.expensetracker.core.data.mapper.toEntity
import com.example.expensetracker.core.data.repository.AccountRepository
import com.example.expensetracker.core.database.dao.AccountDao
import com.example.expensetracker.core.model.account.Account
import com.example.expensetracker.core.model.common.EntityId
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Provider

class AccountRepositoryImpl @Inject constructor(
    private val accountDao: AccountDao,
    private val defaultDataInitializer: Provider<DefaultDataInitializer>? = null
) : AccountRepository {

    init {
        defaultDataInitializer?.let { provider ->
            CoroutineScope(Dispatchers.IO).launch {
                runCatching {
                    provider.get().initialize()
                }
            }
        }
    }

    override fun getActiveAccounts(): Flow<List<Account>> {
        return accountDao.getActiveAccounts().map { entities ->
            entities.map { it.toDomain() }
        }
    }

    override fun getAllAccounts(): Flow<List<Account>> {
        return accountDao.getAllAccounts().map { entities ->
            entities.map { it.toDomain() }
        }
    }

    override fun getAccount(id: EntityId): Flow<Account?> {
        return accountDao.getAllAccounts().map { list ->
            list.firstOrNull { it.id == id.value }?.toDomain()
        }
    }

    override suspend fun getAccountById(id: EntityId): Account? {
        return accountDao.getById(id.value)?.toDomain()
    }

    override suspend fun insertAccount(account: Account) {
        accountDao.insert(account.toEntity())
    }

    override suspend fun updateAccount(account: Account) {
        accountDao.update(account.toEntity())
    }

    override suspend fun archiveAccount(id: EntityId) {
        val existing = accountDao.getById(id.value) ?: return
        accountDao.update(existing.copy(isArchived = true))
    }

    override suspend fun deleteAccount(account: Account) {
        accountDao.delete(account.toEntity())
    }
}
