package com.example.expensetracker.feature.security

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton

interface SecurityPreferencesRepository {
    val isAppLockEnabled: Flow<Boolean>
    val lockTimeoutSeconds: Flow<Long>
    suspend fun setAppLockEnabled(enabled: Boolean)
    suspend fun setLockTimeoutSeconds(seconds: Long)
}

private val Context.securityDataStore: DataStore<Preferences> by preferencesDataStore(name = "security_preferences")

@Singleton
class SecurityPreferencesRepositoryImpl @Inject constructor(
    @ApplicationContext private val context: Context
) : SecurityPreferencesRepository {

    private val dataStore: DataStore<Preferences> = context.securityDataStore

    override val isAppLockEnabled: Flow<Boolean> = dataStore.data
        .catch { exception ->
            if (exception is IOException) emit(emptyPreferences()) else throw exception
        }
        .map { preferences ->
            preferences[KEY_APP_LOCK_ENABLED] ?: false
        }

    override val lockTimeoutSeconds: Flow<Long> = dataStore.data
        .catch { exception ->
            if (exception is IOException) emit(emptyPreferences()) else throw exception
        }
        .map { preferences ->
            preferences[KEY_LOCK_TIMEOUT_SECONDS] ?: DEFAULT_LOCK_TIMEOUT_SECONDS
        }

    override suspend fun setAppLockEnabled(enabled: Boolean) {
        dataStore.edit { preferences ->
            preferences[KEY_APP_LOCK_ENABLED] = enabled
        }
    }

    override suspend fun setLockTimeoutSeconds(seconds: Long) {
        dataStore.edit { preferences ->
            preferences[KEY_LOCK_TIMEOUT_SECONDS] = seconds
        }
    }

    companion object {
        val KEY_APP_LOCK_ENABLED = booleanPreferencesKey("app_lock_enabled")
        val KEY_LOCK_TIMEOUT_SECONDS = longPreferencesKey("lock_timeout_seconds")
        const val DEFAULT_LOCK_TIMEOUT_SECONDS = 0L // 0 means lock immediately on background
    }
}

@Module
@InstallIn(SingletonComponent::class)
abstract class SecurityPreferencesModule {
    @Binds
    abstract fun bindSecurityPreferencesRepository(
        impl: SecurityPreferencesRepositoryImpl
    ): SecurityPreferencesRepository
}
