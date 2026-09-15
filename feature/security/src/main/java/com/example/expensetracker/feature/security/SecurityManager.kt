package com.example.expensetracker.feature.security

import android.app.KeyguardManager
import android.content.Context
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricPrompt
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

enum class BiometricAuthStatus {
    AVAILABLE,
    NOT_ENROLLED,
    NO_HARDWARE,
    UNAVAILABLE
}

interface SecurityManager {
    val isLocked: StateFlow<Boolean>
    val lockMode: Flow<AppLockMode>
    val hasPin: Flow<Boolean>
    val hasPassword: Flow<Boolean>
    fun checkBiometricStatus(): BiometricAuthStatus
    fun onAppBackgrounded()
    suspend fun onAppForegrounded()
    fun unlock()
    fun lock()
    suspend fun verifyAndUnlockWithPin(pin: String): Boolean
    suspend fun verifyAndUnlockWithPassword(password: String): Boolean
    fun authenticate(
        activity: FragmentActivity,
        title: String = "Unlock Expense Tracker",
        subtitle: String? = "Confirm your biometric or device credential to continue",
        onSuccess: () -> Unit,
        onError: (errorCode: Int, errString: CharSequence) -> Unit = { _, _ -> },
        onFailed: () -> Unit = {}
    )
}

@Singleton
class SecurityManagerImpl @Inject constructor(
    @ApplicationContext private val context: Context,
    private val securityPreferencesRepository: SecurityPreferencesRepository
) : SecurityManager {

    private val _isLocked = MutableStateFlow(false)
    override val isLocked: StateFlow<Boolean> = _isLocked.asStateFlow()

    override val lockMode: Flow<AppLockMode> = securityPreferencesRepository.lockMode
    override val hasPin: Flow<Boolean> = securityPreferencesRepository.pinHash.map { !it.isNullOrBlank() }
    override val hasPassword: Flow<Boolean> = securityPreferencesRepository.passwordHash.map { !it.isNullOrBlank() }

    override suspend fun verifyAndUnlockWithPin(pin: String): Boolean {
        val valid = securityPreferencesRepository.verifyPin(pin)
        if (valid) {
            unlock()
        }
        return valid
    }

    override suspend fun verifyAndUnlockWithPassword(password: String): Boolean {
        val valid = securityPreferencesRepository.verifyPassword(password)
        if (valid) {
            unlock()
        }
        return valid
    }

    private var lastBackgroundTimestamp: Long = 0L

    override fun checkBiometricStatus(): BiometricAuthStatus {
        val biometricManager = BiometricManager.from(context)
        val authenticators = BiometricManager.Authenticators.BIOMETRIC_STRONG or
                BiometricManager.Authenticators.DEVICE_CREDENTIAL

        return when (biometricManager.canAuthenticate(authenticators)) {
            BiometricManager.BIOMETRIC_SUCCESS -> BiometricAuthStatus.AVAILABLE
            BiometricManager.BIOMETRIC_ERROR_NONE_ENROLLED -> {
                val keyguardManager = context.getSystemService(Context.KEYGUARD_SERVICE) as? KeyguardManager
                if (keyguardManager?.isDeviceSecure == true) {
                    BiometricAuthStatus.AVAILABLE
                } else {
                    BiometricAuthStatus.NOT_ENROLLED
                }
            }
            BiometricManager.BIOMETRIC_ERROR_NO_HARDWARE -> {
                val keyguardManager = context.getSystemService(Context.KEYGUARD_SERVICE) as? KeyguardManager
                if (keyguardManager?.isDeviceSecure == true) {
                    BiometricAuthStatus.AVAILABLE
                } else {
                    BiometricAuthStatus.NO_HARDWARE
                }
            }
            BiometricManager.BIOMETRIC_ERROR_HW_UNAVAILABLE -> BiometricAuthStatus.UNAVAILABLE
            BiometricManager.BIOMETRIC_ERROR_SECURITY_UPDATE_REQUIRED -> BiometricAuthStatus.UNAVAILABLE
            else -> {
                val keyguardManager = context.getSystemService(Context.KEYGUARD_SERVICE) as? KeyguardManager
                if (keyguardManager?.isDeviceSecure == true) {
                    BiometricAuthStatus.AVAILABLE
                } else {
                    BiometricAuthStatus.UNAVAILABLE
                }
            }
        }
    }

    override fun onAppBackgrounded() {
        lastBackgroundTimestamp = System.currentTimeMillis()
    }

    override suspend fun onAppForegrounded() {
        val isLockEnabled = securityPreferencesRepository.isAppLockEnabled.first()
        if (!isLockEnabled) {
            _isLocked.value = false
            return
        }

        val timeoutSeconds = securityPreferencesRepository.lockTimeoutSeconds.first()
        val elapsedMillis = if (lastBackgroundTimestamp > 0L) {
            System.currentTimeMillis() - lastBackgroundTimestamp
        } else {
            Long.MAX_VALUE
        }

        if (elapsedMillis >= (timeoutSeconds * 1000L)) {
            _isLocked.value = true
        }
    }

    override fun unlock() {
        _isLocked.value = false
        lastBackgroundTimestamp = System.currentTimeMillis()
    }

    override fun lock() {
        _isLocked.value = true
    }

    override fun authenticate(
        activity: FragmentActivity,
        title: String,
        subtitle: String?,
        onSuccess: () -> Unit,
        onError: (errorCode: Int, errString: CharSequence) -> Unit,
        onFailed: () -> Unit
    ) {
        val executor = ContextCompat.getMainExecutor(activity)
        val promptInfo = BiometricPrompt.PromptInfo.Builder()
            .setTitle(title)
            .apply {
                if (!subtitle.isNullOrBlank()) {
                    setSubtitle(subtitle)
                }
                setAllowedAuthenticators(
                    BiometricManager.Authenticators.BIOMETRIC_STRONG or
                            BiometricManager.Authenticators.DEVICE_CREDENTIAL
                )
            }
            .build()

        val biometricPrompt = BiometricPrompt(
            activity,
            executor,
            object : BiometricPrompt.AuthenticationCallback() {
                override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                    super.onAuthenticationSucceeded(result)
                    unlock()
                    onSuccess()
                }

                override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
                    super.onAuthenticationError(errorCode, errString)
                    onError(errorCode, errString)
                }

                override fun onAuthenticationFailed() {
                    super.onAuthenticationFailed()
                    onFailed()
                }
            }
        )

        try {
            biometricPrompt.authenticate(promptInfo)
        } catch (e: Exception) {
            onError(-1, e.message ?: "Authentication failed to start")
        }
    }
}

@Module
@InstallIn(SingletonComponent::class)
abstract class SecurityManagerModule {
    @Binds
    abstract fun bindSecurityManager(
        impl: SecurityManagerImpl
    ): SecurityManager
}
