package com.example.expensetracker.feature.settings

import com.example.expensetracker.core.model.money.Currency
import com.example.expensetracker.core.preferences.FontSizePreference
import com.example.expensetracker.core.preferences.PreferencesRepository
import com.example.expensetracker.core.preferences.ThemeMode
import com.example.expensetracker.core.preferences.UserPreferences
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class SettingsViewModelTest {

    private val testDispatcher = StandardTestDispatcher()
    private lateinit var preferencesRepository: FakePreferencesRepository
    private lateinit var viewModel: SettingsViewModel

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        preferencesRepository = FakePreferencesRepository()
        viewModel = SettingsViewModel(preferencesRepository)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun defaultState_reflectsInitialPreferences() = runTest {
        val collectJob = launch { viewModel.uiState.collect {} }
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertFalse(state.isLoading)
        assertEquals(ThemeMode.SYSTEM, state.themeMode)
        assertTrue(state.isDynamicColorEnabled)
        assertNull(state.customAccentColor)
        assertEquals("INR", state.selectedCurrency.code)
        assertEquals("₹", state.selectedCurrency.symbol)
        assertTrue(state.availableCurrencies.isNotEmpty())
        assertTrue(state.availableAccentColors.isNotEmpty())

        collectJob.cancel()
    }

    @Test
    fun changingTheme_updatesRepositoryAndState() = runTest {
        val collectJob = launch { viewModel.uiState.collect {} }
        advanceUntilIdle()

        viewModel.setThemeMode(ThemeMode.DARK)
        advanceUntilIdle()

        assertEquals(ThemeMode.DARK, preferencesRepository.preferencesFlow.value.themeMode)
        assertEquals(ThemeMode.DARK, viewModel.uiState.value.themeMode)

        viewModel.setThemeMode(ThemeMode.LIGHT)
        advanceUntilIdle()

        assertEquals(ThemeMode.LIGHT, preferencesRepository.preferencesFlow.value.themeMode)
        assertEquals(ThemeMode.LIGHT, viewModel.uiState.value.themeMode)

        collectJob.cancel()
    }

    @Test
    fun enablingAndDisablingDynamicColor_updatesRepositoryAndState() = runTest {
        val collectJob = launch { viewModel.uiState.collect {} }
        advanceUntilIdle()

        viewModel.setDynamicColorEnabled(false)
        advanceUntilIdle()

        assertFalse(preferencesRepository.preferencesFlow.value.isDynamicColorEnabled)
        assertFalse(viewModel.uiState.value.isDynamicColorEnabled)

        viewModel.setDynamicColorEnabled(true)
        advanceUntilIdle()

        assertTrue(preferencesRepository.preferencesFlow.value.isDynamicColorEnabled)
        assertTrue(viewModel.uiState.value.isDynamicColorEnabled)

        collectJob.cancel()
    }

    @Test
    fun settingCustomAccent_updatesRepositoryAndState() = runTest {
        val collectJob = launch { viewModel.uiState.collect {} }
        advanceUntilIdle()

        val seedColor = 0xFF1976D2L
        viewModel.setCustomAccentColor(seedColor)
        advanceUntilIdle()

        assertEquals(seedColor, preferencesRepository.preferencesFlow.value.customAccentColor)
        assertEquals(seedColor, viewModel.uiState.value.customAccentColor)

        collectJob.cancel()
    }

    @Test
    fun clearingCustomAccent_setsCustomAccentToNull() = runTest {
        preferencesRepository.preferencesFlow.value = UserPreferences(
            customAccentColor = 0xFF3F51B5L
        )
        val collectJob = launch { viewModel.uiState.collect {} }
        advanceUntilIdle()

        assertEquals(0xFF3F51B5L, viewModel.uiState.value.customAccentColor)

        viewModel.clearCustomAccentColor()
        advanceUntilIdle()

        assertNull(preferencesRepository.preferencesFlow.value.customAccentColor)
        assertNull(viewModel.uiState.value.customAccentColor)

        collectJob.cancel()
    }

    @Test
    fun changingCurrency_updatesRepositoryAndSelectedCurrency() = runTest {
        val collectJob = launch { viewModel.uiState.collect {} }
        advanceUntilIdle()

        viewModel.setCurrencyCode("USD")
        advanceUntilIdle()

        assertEquals("USD", preferencesRepository.preferencesFlow.value.currencyCode)
        assertEquals("USD", viewModel.uiState.value.selectedCurrency.code)
        assertEquals("$", viewModel.uiState.value.selectedCurrency.symbol)

        viewModel.setCurrencyCode("EUR")
        advanceUntilIdle()

        assertEquals("EUR", preferencesRepository.preferencesFlow.value.currencyCode)
        assertEquals("EUR", viewModel.uiState.value.selectedCurrency.code)
        assertEquals("€", viewModel.uiState.value.selectedCurrency.symbol)

        collectJob.cancel()
    }

    @Test
    fun stateUpdates_whenUnderlyingPreferencesChange() = runTest {
        val collectJob = launch { viewModel.uiState.collect {} }
        advanceUntilIdle()

        // Directly update the repository stream
        preferencesRepository.preferencesFlow.value = UserPreferences(
            themeMode = ThemeMode.DARK,
            isDynamicColorEnabled = false,
            customAccentColor = 0xFF00796BL,
            currencyCode = "GBP"
        )
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertEquals(ThemeMode.DARK, state.themeMode)
        assertFalse(state.isDynamicColorEnabled)
        assertEquals(0xFF00796BL, state.customAccentColor)
        assertEquals("GBP", state.selectedCurrency.code)
        assertEquals("£", state.selectedCurrency.symbol)

        collectJob.cancel()
    }

    @Test
    fun invalidPreferenceFallback_handlesUnknownOrInvalidCurrencyCodeSafely() = runTest {
        // Stored preference has invalid or custom currency code
        preferencesRepository.preferencesFlow.value = UserPreferences(
            currencyCode = "XYZ"
        )
        val collectJob = launch { viewModel.uiState.collect {} }
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertNotNull(state.selectedCurrency)
        assertEquals("XYZ", state.selectedCurrency.code)
        // Verify it was appended to availableCurrencies so UI can display it
        assertTrue(state.availableCurrencies.any { it.code == "XYZ" })

        collectJob.cancel()
    }

    @Test
    fun rapidlyTogglingSettings_appliesAllChangesSequentially() = runTest {
        val collectJob = launch { viewModel.uiState.collect {} }
        advanceUntilIdle()

        viewModel.setThemeMode(ThemeMode.LIGHT)
        viewModel.setThemeMode(ThemeMode.DARK)
        viewModel.setDynamicColorEnabled(false)
        viewModel.setCustomAccentColor(0xFF388E3CL)
        viewModel.setCurrencyCode("JPY")
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertEquals(ThemeMode.DARK, state.themeMode)
        assertFalse(state.isDynamicColorEnabled)
        assertEquals(0xFF388E3CL, state.customAccentColor)
        assertEquals("JPY", state.selectedCurrency.code)

        collectJob.cancel()
    }

    @Test
    fun changingFontSize_updatesRepositoryAndState() = runTest {
        val collectJob = launch { viewModel.uiState.collect {} }
        advanceUntilIdle()

        viewModel.setFontSize(FontSizePreference.LARGE)
        advanceUntilIdle()

        assertEquals(FontSizePreference.LARGE, preferencesRepository.preferencesFlow.value.fontSize)
        assertEquals(FontSizePreference.LARGE, viewModel.uiState.value.fontSize)

        viewModel.setFontSize(FontSizePreference.SMALL)
        advanceUntilIdle()

        assertEquals(FontSizePreference.SMALL, preferencesRepository.preferencesFlow.value.fontSize)
        assertEquals(FontSizePreference.SMALL, viewModel.uiState.value.fontSize)

        collectJob.cancel()
    }
}

/**
 * Fake implementation of [PreferencesRepository] for unit testing.
 */
class FakePreferencesRepository(
    initialPreferences: UserPreferences = UserPreferences.DEFAULT
) : PreferencesRepository {

    val preferencesFlow = MutableStateFlow(initialPreferences)

    override val userPreferences: Flow<UserPreferences> = preferencesFlow.asStateFlow()

    override suspend fun setThemeMode(themeMode: ThemeMode) {
        preferencesFlow.update { it.copy(themeMode = themeMode) }
    }

    override suspend fun setDynamicColorEnabled(enabled: Boolean) {
        preferencesFlow.update { it.copy(isDynamicColorEnabled = enabled) }
    }

    override suspend fun setCustomAccentColor(color: Long?) {
        preferencesFlow.update { it.copy(customAccentColor = color) }
    }

    override suspend fun clearCustomAccentColor() {
        preferencesFlow.update { it.copy(customAccentColor = null) }
    }

    override suspend fun setCurrencyCode(currencyCode: String) {
        preferencesFlow.update { it.copy(currencyCode = currencyCode) }
    }

    override suspend fun setFontSize(fontSize: FontSizePreference) {
        preferencesFlow.update { it.copy(fontSize = fontSize) }
    }
}
