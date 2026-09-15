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
        securityPreferencesRepository.isAppLockEnabled,
        securityPreferencesRepository.lockTimeoutSeconds,
        _biometricStatus,
        _userMessage,
        _errorMessage
    ) { isEnabled, timeout, status, userMsg, errorMsg ->
        SecurityUiState(
            isAppLockEnabled = isEnabled,
            lockTimeoutSeconds = timeout,
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
        if (enabled) {
            val status = securityManager.checkBiometricStatus()
            if (status == BiometricAuthStatus.NOT_ENROLLED) {
                _errorMessage.value = "Device has no screen lock or biometrics configured. Please set up a screen lock in your device settings."
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
