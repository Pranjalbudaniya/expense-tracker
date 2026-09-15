package com.example.expensetracker.feature.security

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class SecurityViewModel @Inject constructor(
    private val securityPreferencesRepository: SecurityPreferencesRepository,
    private val securityManager: SecurityManager
) : ViewModel() {

    private val _biometricStatus = MutableStateFlow(securityManager.checkBiometricStatus())
    private val _userMessage = MutableStateFlow<String?>(null)
    private val _errorMessage = MutableStateFlow<String?>(null)

    val uiState: StateFlow<SecurityUiState> = combine(
        combine(
            securityPreferencesRepository.isAppLockEnabled,
            securityPreferencesRepository.lockTimeoutSeconds,
            securityPreferencesRepository.lockMode,
            securityManager.hasPin,
            securityManager.hasPassword
        ) { isEnabled, timeout, mode, hasPin, hasPass ->
            Quint(isEnabled, timeout, mode, hasPin, hasPass)
        },
        combine(
            _biometricStatus,
            _userMessage,
            _errorMessage
        ) { status, userMsg, errorMsg ->
            Triple(status, userMsg, errorMsg)
        }
    ) { (isEnabled, timeout, mode, hasPin, hasPass), (status, userMsg, errorMsg) ->
        SecurityUiState(
            isAppLockEnabled = isEnabled,
            lockTimeoutSeconds = timeout,
            lockMode = mode,
            hasPin = hasPin,
            hasPassword = hasPass,
            biometricAuthStatus = status,
            isLoading = false,
            userMessage = userMsg,
            errorMessage = errorMsg
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = SecurityUiState(isLoading = true)
    )

    fun refreshBiometricStatus() {
        _biometricStatus.value = securityManager.checkBiometricStatus()
    }

    fun setAppLockEnabled(enabled: Boolean) {
        if (enabled && uiState.value.lockMode == AppLockMode.BIOMETRIC) {
            val status = securityManager.checkBiometricStatus()
            if (status == BiometricAuthStatus.NOT_ENROLLED) {
                _errorMessage.value = "Device has no screen lock or biometrics configured. Please set up PIN or Password lock, or configure Android device lock."
                return
            }
            if (status == BiometricAuthStatus.NO_HARDWARE) {
                _errorMessage.value = "Biometric hardware is not available on this device and no device lock is set."
                return
            }
        }

        viewModelScope.launch {
            try {
                securityPreferencesRepository.setAppLockEnabled(enabled)
                _userMessage.value = if (enabled) "App lock enabled" else "App lock disabled"
            } catch (e: Exception) {
                _errorMessage.value = e.message ?: "Failed to update app lock setting"
            }
        }
    }

    fun setLockMode(mode: AppLockMode) {
        viewModelScope.launch {
            try {
                securityPreferencesRepository.setLockMode(mode)
                _userMessage.value = "Lock method set to ${mode.name}"
            } catch (e: Exception) {
                _errorMessage.value = e.message ?: "Failed to update lock mode"
            }
        }
    }

    fun setPin(pin: String) {
        viewModelScope.launch {
            try {
                securityPreferencesRepository.setPin(pin)
                _userMessage.value = "PIN set successfully"
            } catch (e: Exception) {
                _errorMessage.value = e.message ?: "Failed to set PIN"
            }
        }
    }

    fun removePin() {
        viewModelScope.launch {
            try {
                securityPreferencesRepository.setPin(null)
                _userMessage.value = "PIN removed"
            } catch (e: Exception) {
                _errorMessage.value = e.message ?: "Failed to remove PIN"
            }
        }
    }

    fun setPassword(password: String) {
        viewModelScope.launch {
            try {
                securityPreferencesRepository.setPassword(password)
                _userMessage.value = "Password set successfully"
            } catch (e: Exception) {
                _errorMessage.value = e.message ?: "Failed to set password"
            }
        }
    }

    fun removePassword() {
        viewModelScope.launch {
            try {
                securityPreferencesRepository.setPassword(null)
                _userMessage.value = "Password removed"
            } catch (e: Exception) {
                _errorMessage.value = e.message ?: "Failed to remove password"
            }
        }
    }

    fun setLockTimeoutSeconds(seconds: Long) {
        viewModelScope.launch {
            try {
                securityPreferencesRepository.setLockTimeoutSeconds(seconds)
            } catch (e: Exception) {
                _errorMessage.value = e.message ?: "Failed to update lock timeout"
            }
        }
    }

    fun clearUserMessage() {
        _userMessage.value = null
    }

    fun clearErrorMessage() {
        _errorMessage.value = null
    }
}

private data class Quint<A, B, C, D, E>(val first: A, val second: B, val third: C, val fourth: D, val fifth: E)

