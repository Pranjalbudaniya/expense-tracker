package com.example.expensetracker.feature.notifications

import com.example.expensetracker.core.preferences.PreferencesRepository
import com.example.expensetracker.core.preferences.ThemeMode
import com.example.expensetracker.core.preferences.UserPreferences
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
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
class NotificationPreferencesViewModelTest {

    private val testDispatcher = StandardTestDispatcher()

    private lateinit var fakePreferencesRepository: FakePreferencesRepository
    private lateinit var viewModel: NotificationPreferencesViewModel
    private var collectJob: Job? = null

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)

        fakePreferencesRepository = FakePreferencesRepository()
        viewModel = NotificationPreferencesViewModel(fakePreferencesRepository)

        collectJob = CoroutineScope(testDispatcher).launch {
            viewModel.uiState.collect {}
        }
    }

    @After
    fun tearDown() {
        collectJob?.cancel()
        Dispatchers.resetMain()
    }

    @Test
    fun defaultState_notificationsAreDisabledByDefault() = runTest(testDispatcher) {
        advanceUntilIdle()
        val state = viewModel.uiState.value

        assertFalse(state.isLoading)
        assertFalse(state.notificationsEnabled)
        assertFalse(state.recurringNotificationsEnabled)
        assertFalse(state.budgetAlertsEnabled)
        assertEquals(80, state.budgetThresholdPercent)
        assertTrue(state.isPermissionGranted)
        assertNull(state.userMessage)
        assertNull(state.errorMessage)
    }

    @Test
    fun setNotificationsEnabled_updatesPreferenceAndMessage() = runTest(testDispatcher) {
        viewModel.setNotificationsEnabled(true)
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertTrue(state.notificationsEnabled)
        assertEquals("Notifications enabled", state.userMessage)
        assertTrue(fakePreferencesRepository.currentPreferences.notificationsEnabled)

        viewModel.setNotificationsEnabled(false)
        advanceUntilIdle()

        val updatedState = viewModel.uiState.value
        assertFalse(updatedState.notificationsEnabled)
        assertEquals("Notifications disabled", updatedState.userMessage)
        assertFalse(fakePreferencesRepository.currentPreferences.notificationsEnabled)
    }

    @Test
    fun setRecurringNotificationsEnabled_updatesPreference() = runTest(testDispatcher) {
        viewModel.setRecurringNotificationsEnabled(true)
        advanceUntilIdle()

        assertTrue(viewModel.uiState.value.recurringNotificationsEnabled)
        assertEquals("Recurring alerts enabled", viewModel.uiState.value.userMessage)
        assertTrue(fakePreferencesRepository.currentPreferences.recurringNotificationsEnabled)

        viewModel.setRecurringNotificationsEnabled(false)
        advanceUntilIdle()

        assertFalse(viewModel.uiState.value.recurringNotificationsEnabled)
        assertEquals("Recurring alerts disabled", viewModel.uiState.value.userMessage)
        assertFalse(fakePreferencesRepository.currentPreferences.recurringNotificationsEnabled)
    }

    @Test
    fun setBudgetAlertsEnabled_updatesPreference() = runTest(testDispatcher) {
        viewModel.setBudgetAlertsEnabled(true)
        advanceUntilIdle()

        assertTrue(viewModel.uiState.value.budgetAlertsEnabled)
        assertEquals("Budget alerts enabled", viewModel.uiState.value.userMessage)
        assertTrue(fakePreferencesRepository.currentPreferences.budgetAlertsEnabled)

        viewModel.setBudgetAlertsEnabled(false)
        advanceUntilIdle()

        assertFalse(viewModel.uiState.value.budgetAlertsEnabled)
        assertEquals("Budget alerts disabled", viewModel.uiState.value.userMessage)
        assertFalse(fakePreferencesRepository.currentPreferences.budgetAlertsEnabled)
    }

    @Test
    fun setBudgetThresholdPercent_persistsValidPercent() = runTest(testDispatcher) {
        viewModel.setBudgetThresholdPercent(90)
        advanceUntilIdle()

        assertEquals(90, viewModel.uiState.value.budgetThresholdPercent)
        assertEquals("Budget threshold set to 90%", viewModel.uiState.value.userMessage)
        assertEquals(90, fakePreferencesRepository.currentPreferences.budgetThresholdPercent)
    }

    @Test
    fun updatePermissionGranted_whenFalse_disablesNotificationsAndSetsErrorMessage() = runTest(testDispatcher) {
        viewModel.setNotificationsEnabled(true)
        advanceUntilIdle()
        assertTrue(viewModel.uiState.value.notificationsEnabled)

        viewModel.updatePermissionGranted(false)
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertFalse(state.isPermissionGranted)
        assertFalse(state.notificationsEnabled)
        assertNotNull(state.errorMessage)
        assertTrue(state.errorMessage!!.contains("permission is required"))
    }

    @Test
    fun clearMessages_clearsUserAndErrorMessages() = runTest(testDispatcher) {
        viewModel.setNotificationsEnabled(true)
        advanceUntilIdle()
        assertNotNull(viewModel.uiState.value.userMessage)

        viewModel.clearUserMessage()
        advanceUntilIdle()
        assertNull(viewModel.uiState.value.userMessage)

        viewModel.updatePermissionGranted(false)
        advanceUntilIdle()
        assertNotNull(viewModel.uiState.value.errorMessage)

        viewModel.clearErrorMessage()
        advanceUntilIdle()
        assertNull(viewModel.uiState.value.errorMessage)
    }

    // --- Pure Kotlin Fake Repository ---

    private class FakePreferencesRepository : PreferencesRepository {
        var currentPreferences = UserPreferences()
            set(value) {
                field = value
                _preferencesFlow.value = value
            }

        private val _preferencesFlow = MutableStateFlow(currentPreferences)
        override val userPreferences: Flow<UserPreferences> = _preferencesFlow.asStateFlow()

        private val sentKeys = mutableSetOf<String>()

        override suspend fun setThemeMode(themeMode: ThemeMode) {
            currentPreferences = currentPreferences.copy(themeMode = themeMode)
        }

        override suspend fun setDynamicColorEnabled(enabled: Boolean) {
            currentPreferences = currentPreferences.copy(isDynamicColorEnabled = enabled)
        }

        override suspend fun setCustomAccentColor(color: Long?) {
            currentPreferences = currentPreferences.copy(customAccentColor = color)
        }

        override suspend fun clearCustomAccentColor() {
            currentPreferences = currentPreferences.copy(customAccentColor = null)
        }

        override suspend fun setCurrencyCode(currencyCode: String) {
            currentPreferences = currentPreferences.copy(currencyCode = currencyCode)
        }

        override suspend fun setNotificationsEnabled(enabled: Boolean) {
            currentPreferences = currentPreferences.copy(notificationsEnabled = enabled)
        }

        override suspend fun setRecurringNotificationsEnabled(enabled: Boolean) {
            currentPreferences = currentPreferences.copy(recurringNotificationsEnabled = enabled)
        }

        override suspend fun setBudgetAlertsEnabled(enabled: Boolean) {
            currentPreferences = currentPreferences.copy(budgetAlertsEnabled = enabled)
        }

        override suspend fun setBudgetThresholdPercent(percent: Int) {
            currentPreferences = currentPreferences.copy(budgetThresholdPercent = percent)
        }

        override suspend fun hasNotificationBeenSent(eventKey: String): Boolean {
            return sentKeys.contains(eventKey)
        }

        override suspend fun markNotificationSent(eventKey: String) {
            sentKeys.add(eventKey)
        }
    }
}
