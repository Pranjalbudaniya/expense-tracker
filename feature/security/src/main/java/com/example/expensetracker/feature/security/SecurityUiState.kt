package com.example.expensetracker.feature.security

/**
 * Immutable UI state for the Security and App Lock screen.
 */
data class SecurityUiState(
    val isAppLockEnabled: Boolean = false,
    val lockTimeoutSeconds: Long = 0L,
    val biometricAuthStatus: BiometricAuthStatus = BiometricAuthStatus.AVAILABLE,
    val isLoading: Boolean = true,
    val userMessage: String? = null,
    val errorMessage: String? = null
)
