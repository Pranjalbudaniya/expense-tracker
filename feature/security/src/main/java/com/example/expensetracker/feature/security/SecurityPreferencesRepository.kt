package com.example.expensetracker.feature.security

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import java.io.IOException
import java.security.MessageDigest
import javax.inject.Inject
import javax.inject.Singleton

enum class AppLockMode {
    PIN,
    PASSWORD,
    BIOMETRIC
}

private fun hashSecret(secret: String): String {
    val digest = MessageDigest.getInstance("SHA-256")
    val salt = "ExpenseTracker_Security_Salt_v1"
    val bytes = digest.digest((salt + secret).toByteArray(Charsets.UTF_8))
    return bytes.joinToString("") { "%02x".format(it) }
}

interface SecurityPreferencesRepository {
    val isAppLockEnabled: Flow<Boolean>
    val lockTimeoutSeconds: Flow<Long>
    val lockMode: Flow<AppLockMode>
    val pinHash: Flow<String?>
    val passwordHash: Flow<String?>
    suspend fun setAppLockEnabled(enabled: Boolean)
    suspend fun setLockTimeoutSeconds(seconds: Long)
    suspend fun setLockMode(mode: AppLockMode)
    suspend fun setPin(pin: String?)
    suspend fun setPassword(password: String?)
    suspend fun verifyPin(pin: String): Boolean
    suspend fun verifyPassword(password: String): Boolean
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

    override val lockMode: Flow<AppLockMode> = dataStore.data
        .catch { exception ->
            if (exception is IOException) emit(emptyPreferences()) else throw exception
        }
        .map { preferences ->
            val modeStr = preferences[KEY_LOCK_MODE] ?: AppLockMode.PIN.name
            try {
                AppLockMode.valueOf(modeStr)
            } catch (_: Exception) {
                AppLockMode.PIN
            }
        }

    override val pinHash: Flow<String?> = dataStore.data
        .catch { exception ->
            if (exception is IOException) emit(emptyPreferences()) else throw exception
        }
        .map { preferences ->
            preferences[KEY_PIN_HASH]
        }

    override val passwordHash: Flow<String?> = dataStore.data
        .catch { exception ->
            if (exception is IOException) emit(emptyPreferences()) else throw exception
        }
        .map { preferences ->
            preferences[KEY_PASSWORD_HASH]
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

    override suspend fun setLockMode(mode: AppLockMode) {
        dataStore.edit { preferences ->
            preferences[KEY_LOCK_MODE] = mode.name
        }
    }

    override suspend fun setPin(pin: String?) {
        dataStore.edit { preferences ->
            if (pin.isNullOrBlank()) {
                preferences.remove(KEY_PIN_HASH)
            } else {
                preferences[KEY_PIN_HASH] = hashSecret(pin)
            }
        }
    }

    override suspend fun setPassword(password: String?) {
        dataStore.edit { preferences ->
            if (password.isNullOrBlank()) {
                preferences.remove(KEY_PASSWORD_HASH)
            } else {
                preferences[KEY_PASSWORD_HASH] = hashSecret(password)
            }
        }
    }

    override suspend fun verifyPin(pin: String): Boolean {
        val stored = pinHash.first() ?: return false
        return stored == hashSecret(pin)
    }

    override suspend fun verifyPassword(password: String): Boolean {
        val stored = passwordHash.first() ?: return false
        return stored == hashSecret(password)
    }

    companion object {
        val KEY_APP_LOCK_ENABLED = booleanPreferencesKey("app_lock_enabled")
        val KEY_LOCK_TIMEOUT_SECONDS = longPreferencesKey("lock_timeout_seconds")
        val KEY_LOCK_MODE = stringPreferencesKey("lock_mode")
        val KEY_PIN_HASH = stringPreferencesKey("pin_hash")
        val KEY_PASSWORD_HASH = stringPreferencesKey("password_hash")
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

