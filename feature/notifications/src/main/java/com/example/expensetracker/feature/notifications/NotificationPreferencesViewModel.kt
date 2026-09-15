package com.example.expensetracker.feature.notifications

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.expensetracker.core.preferences.PreferencesRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class NotificationPreferencesViewModel @Inject constructor(
    private val preferencesRepository: PreferencesRepository
) : ViewModel() {

    private val _isPermissionGranted = MutableStateFlow(true)
    private val _userMessage = MutableStateFlow<String?>(null)
    private val _errorMessage = MutableStateFlow<String?>(null)

    val uiState: StateFlow<NotificationPreferencesUiState> = combine(
        preferencesRepository.userPreferences,
        _isPermissionGranted,
        _userMessage,
        _errorMessage
    ) { preferences, permissionGranted, userMessage, errorMessage ->
        NotificationPreferencesUiState(
            isLoading = false,
            notificationsEnabled = preferences.notificationsEnabled,
            recurringNotificationsEnabled = preferences.recurringNotificationsEnabled,
            budgetAlertsEnabled = preferences.budgetAlertsEnabled,
            budgetThresholdPercent = preferences.budgetThresholdPercent,
            isPermissionGranted = permissionGranted,
            userMessage = userMessage,
            errorMessage = errorMessage
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = NotificationPreferencesUiState(isLoading = true)
    )

    fun setNotificationsEnabled(enabled: Boolean) {
        viewModelScope.launch {
            preferencesRepository.setNotificationsEnabled(enabled)
            if (enabled) {
                _userMessage.value = "Notifications enabled"
            } else {
                _userMessage.value = "Notifications disabled"
            }
        }
    }

    fun setRecurringNotificationsEnabled(enabled: Boolean) {
        viewModelScope.launch {
            preferencesRepository.setRecurringNotificationsEnabled(enabled)
            _userMessage.value = if (enabled) "Recurring alerts enabled" else "Recurring alerts disabled"
        }
    }

    fun setBudgetAlertsEnabled(enabled: Boolean) {
        viewModelScope.launch {
            preferencesRepository.setBudgetAlertsEnabled(enabled)
            _userMessage.value = if (enabled) "Budget alerts enabled" else "Budget alerts disabled"
        }
    }

    fun setBudgetThresholdPercent(percent: Int) {
        viewModelScope.launch {
            preferencesRepository.setBudgetThresholdPercent(percent)
            _userMessage.value = "Budget threshold set to $percent%"
        }
    }

    fun updatePermissionGranted(granted: Boolean) {
        _isPermissionGranted.value = granted
        if (!granted) {
            viewModelScope.launch {
                preferencesRepository.setNotificationsEnabled(false)
            }
            _errorMessage.value = "Notification permission is required to receive alerts"
        }
    }

    fun clearUserMessage() {
        _userMessage.value = null
    }

    fun clearErrorMessage() {
        _errorMessage.value = null
    }
}
