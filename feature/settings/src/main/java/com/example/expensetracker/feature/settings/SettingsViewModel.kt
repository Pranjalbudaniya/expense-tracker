package com.example.expensetracker.feature.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.expensetracker.core.model.money.Currency
import com.example.expensetracker.core.preferences.FontSizePreference
import com.example.expensetracker.core.preferences.PreferencesRepository
import com.example.expensetracker.core.preferences.ThemeMode
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * ViewModel managing user preferences and UI state for the Settings screen.
 */
@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val preferencesRepository: PreferencesRepository
) : ViewModel() {

    val uiState: StateFlow<SettingsUiState> = preferencesRepository.userPreferences
        .map { preferences ->
            val currency = Currency.fromCode(preferences.currencyCode)
            // If the active currency is custom/not in the standard list, append it so it can be viewed
            val currencies = if (SUPPORTED_CURRENCIES.none { it.code.equals(currency.code, ignoreCase = true) }) {
                listOf(currency) + SUPPORTED_CURRENCIES
            } else {
                SUPPORTED_CURRENCIES
            }

            SettingsUiState(
                themeMode = preferences.themeMode,
                isDynamicColorEnabled = preferences.isDynamicColorEnabled,
                customAccentColor = preferences.customAccentColor,
                selectedCurrency = currency,
                availableCurrencies = currencies,
                fontSize = preferences.fontSize,
                isLoading = false
            )
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = SettingsUiState(isLoading = true)
        )

    /**
     * Updates the application text/font size preference.
     */
    fun setFontSize(fontSize: FontSizePreference) {
        viewModelScope.launch {
            preferencesRepository.setFontSize(fontSize)
        }
    }

    /**
     * Updates the application theme mode (System, Light, Dark).
     */
    fun setThemeMode(themeMode: ThemeMode) {
        viewModelScope.launch {
            preferencesRepository.setThemeMode(themeMode)
        }
    }

    /**
     * Enables or disables dynamic color (Material You).
     */
    fun setDynamicColorEnabled(enabled: Boolean) {
        viewModelScope.launch {
            preferencesRepository.setDynamicColorEnabled(enabled)
        }
    }

    /**
     * Sets or clears the custom accent/seed color.
     */
    fun setCustomAccentColor(color: Long?) {
        viewModelScope.launch {
            preferencesRepository.setCustomAccentColor(color)
        }
    }

    /**
     * Clears any active custom accent color, reverting to default theme behavior.
     */
    fun clearCustomAccentColor() {
        viewModelScope.launch {
            preferencesRepository.clearCustomAccentColor()
        }
    }

    /**
     * Changes the application default currency code for future transactions.
     */
    fun setCurrencyCode(currencyCode: String) {
        viewModelScope.launch {
            preferencesRepository.setCurrencyCode(currencyCode.trim().uppercase())
        }
    }
}
