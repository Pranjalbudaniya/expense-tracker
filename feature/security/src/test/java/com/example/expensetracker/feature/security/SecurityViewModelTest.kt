package com.example.expensetracker.feature.security

import androidx.fragment.app.FragmentActivity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
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
class SecurityViewModelTest {

    private val testDispatcher = StandardTestDispatcher()

    private lateinit var fakePreferencesRepo: FakeSecurityPreferencesRepository
    private lateinit var fakeSecurityManager: FakeSecurityManager
    private lateinit var viewModel: SecurityViewModel
    private var collectJob: Job? = null

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)

        fakePreferencesRepo = FakeSecurityPreferencesRepository()
        fakeSecurityManager = FakeSecurityManager(fakePreferencesRepo)

        viewModel = SecurityViewModel(
            securityPreferencesRepository = fakePreferencesRepo,
            securityManager = fakeSecurityManager
        )

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
    fun defaultState_appLockIsDisabled() = runTest(testDispatcher) {
        advanceUntilIdle()
        val state = viewModel.uiState.value

        assertFalse(state.isLoading)
        assertFalse(state.isAppLockEnabled)
        assertEquals(0L, state.lockTimeoutSeconds)
        assertEquals(BiometricAuthStatus.AVAILABLE, state.biometricAuthStatus)
        assertNull(state.userMessage)
        assertNull(state.errorMessage)
    }

    @Test
    fun setAppLockEnabled_availableBiometrics_updatesPreference() = runTest(testDispatcher) {
        fakeSecurityManager.status = BiometricAuthStatus.AVAILABLE
        viewModel.refreshBiometricStatus()
        advanceUntilIdle()

        viewModel.setAppLockEnabled(true)
        advanceUntilIdle()

        assertTrue(viewModel.uiState.value.isAppLockEnabled)
        assertEquals("App lock enabled", viewModel.uiState.value.userMessage)
        assertTrue(fakePreferencesRepo.appLockEnabledFlow.value)
    }

    @Test
    fun setAppLockEnabled_disabling_updatesPreference() = runTest(testDispatcher) {
        fakePreferencesRepo.appLockEnabledFlow.value = true
        advanceUntilIdle()

        viewModel.setAppLockEnabled(false)
        advanceUntilIdle()

        assertFalse(viewModel.uiState.value.isAppLockEnabled)
        assertEquals("App lock disabled", viewModel.uiState.value.userMessage)
        assertFalse(fakePreferencesRepo.appLockEnabledFlow.value)
    }

    @Test
    fun setAppLockEnabled_notEnrolled_showsErrorMessageAndDoesNotEnable() = runTest(testDispatcher) {
        viewModel.setLockMode(AppLockMode.BIOMETRIC)
        fakeSecurityManager.status = BiometricAuthStatus.NOT_ENROLLED
        viewModel.refreshBiometricStatus()
        advanceUntilIdle()

        viewModel.setAppLockEnabled(true)
        advanceUntilIdle()

        assertFalse(viewModel.uiState.value.isAppLockEnabled)
        assertNotNull(viewModel.uiState.value.errorMessage)
        assertTrue(viewModel.uiState.value.errorMessage!!.contains("no screen lock"))
        assertFalse(fakePreferencesRepo.appLockEnabledFlow.value)
    }

    @Test
    fun setAppLockEnabled_noHardware_showsErrorMessageAndDoesNotEnable() = runTest(testDispatcher) {
        viewModel.setLockMode(AppLockMode.BIOMETRIC)
        fakeSecurityManager.status = BiometricAuthStatus.NO_HARDWARE
        viewModel.refreshBiometricStatus()
        advanceUntilIdle()

        viewModel.setAppLockEnabled(true)
        advanceUntilIdle()

        assertFalse(viewModel.uiState.value.isAppLockEnabled)
        assertNotNull(viewModel.uiState.value.errorMessage)
        assertTrue(viewModel.uiState.value.errorMessage!!.contains("Biometric hardware is not available"))
        assertFalse(fakePreferencesRepo.appLockEnabledFlow.value)
    }

    @Test
    fun setLockTimeoutSeconds_persistsTimeout() = runTest(testDispatcher) {
        viewModel.setLockTimeoutSeconds(60L)
        advanceUntilIdle()

        assertEquals(60L, viewModel.uiState.value.lockTimeoutSeconds)
        assertEquals(60L, fakePreferencesRepo.lockTimeoutSecondsFlow.value)
    }

    @Test
    fun clearMessages_clearsUserAndErrorMessages() = runTest(testDispatcher) {
        viewModel.setLockMode(AppLockMode.BIOMETRIC)
        fakeSecurityManager.status = BiometricAuthStatus.NOT_ENROLLED
        viewModel.refreshBiometricStatus()
        advanceUntilIdle()

        viewModel.setAppLockEnabled(true)
        advanceUntilIdle()
        assertNotNull(viewModel.uiState.value.errorMessage)

        viewModel.clearErrorMessage()
        advanceUntilIdle()
        assertNull(viewModel.uiState.value.errorMessage)

        fakeSecurityManager.status = BiometricAuthStatus.AVAILABLE
        viewModel.refreshBiometricStatus()
        viewModel.setAppLockEnabled(true)
        advanceUntilIdle()
        assertNotNull(viewModel.uiState.value.userMessage)

        viewModel.clearUserMessage()
        advanceUntilIdle()
        assertNull(viewModel.uiState.value.userMessage)
    }

    @Test
    fun setLockMode_updatesModeAndMessage() = runTest(testDispatcher) {
        viewModel.setLockMode(AppLockMode.PASSWORD)
        advanceUntilIdle()

        assertEquals(AppLockMode.PASSWORD, viewModel.uiState.value.lockMode)
        assertEquals("Lock method set to PASSWORD", viewModel.uiState.value.userMessage)
    }

    @Test
    fun setPinAndRemovePin_updatesState() = runTest(testDispatcher) {
        viewModel.setPin("1234")
        advanceUntilIdle()

        assertTrue(viewModel.uiState.value.hasPin)
        assertEquals("PIN set successfully", viewModel.uiState.value.userMessage)

        viewModel.removePin()
        advanceUntilIdle()

        assertFalse(viewModel.uiState.value.hasPin)
        assertEquals("PIN removed", viewModel.uiState.value.userMessage)
    }

    @Test
    fun setPasswordAndRemovePassword_updatesState() = runTest(testDispatcher) {
        viewModel.setPassword("myPassword123")
        advanceUntilIdle()

        assertTrue(viewModel.uiState.value.hasPassword)
        assertEquals("Password set successfully", viewModel.uiState.value.userMessage)

        viewModel.removePassword()
        advanceUntilIdle()

        assertFalse(viewModel.uiState.value.hasPassword)
        assertEquals("Password removed", viewModel.uiState.value.userMessage)
    }

    // --- Pure Kotlin Fakes ---

    private class FakeSecurityPreferencesRepository : SecurityPreferencesRepository {
        val appLockEnabledFlow = MutableStateFlow(false)
        val lockTimeoutSecondsFlow = MutableStateFlow(0L)
        val lockModeFlow = MutableStateFlow(AppLockMode.PIN)
        val pinHashFlow = MutableStateFlow<String?>(null)
        val passwordHashFlow = MutableStateFlow<String?>(null)

        override val isAppLockEnabled: Flow<Boolean> = appLockEnabledFlow.asStateFlow()
        override val lockTimeoutSeconds: Flow<Long> = lockTimeoutSecondsFlow.asStateFlow()
        override val lockMode: Flow<AppLockMode> = lockModeFlow.asStateFlow()
        override val pinHash: Flow<String?> = pinHashFlow.asStateFlow()
        override val passwordHash: Flow<String?> = passwordHashFlow.asStateFlow()

        override suspend fun setAppLockEnabled(enabled: Boolean) {
            appLockEnabledFlow.value = enabled
        }

        override suspend fun setLockTimeoutSeconds(seconds: Long) {
            lockTimeoutSecondsFlow.value = seconds
        }

        override suspend fun setLockMode(mode: AppLockMode) {
            lockModeFlow.value = mode
        }

        override suspend fun setPin(pin: String?) {
            pinHashFlow.value = pin
        }

        override suspend fun setPassword(password: String?) {
            passwordHashFlow.value = password
        }

        override suspend fun verifyPin(pin: String): Boolean {
            return pinHashFlow.value == pin
        }

        override suspend fun verifyPassword(password: String): Boolean {
            return passwordHashFlow.value == password
        }
    }

    private class FakeSecurityManager(
        private val prefs: FakeSecurityPreferencesRepository = FakeSecurityPreferencesRepository()
    ) : SecurityManager {
        var status = BiometricAuthStatus.AVAILABLE
        private val _isLocked = MutableStateFlow(false)
        override val isLocked: StateFlow<Boolean> = _isLocked.asStateFlow()

        override val lockMode: Flow<AppLockMode> = prefs.lockMode
        override val hasPin: Flow<Boolean> = prefs.pinHash.map { !it.isNullOrBlank() }
        override val hasPassword: Flow<Boolean> = prefs.passwordHash.map { !it.isNullOrBlank() }

        override fun checkBiometricStatus(): BiometricAuthStatus = status
        override fun onAppBackgrounded() {}
        override suspend fun onAppForegrounded() {}
        override fun unlock() { _isLocked.value = false }
        override fun lock() { _isLocked.value = true }

        override suspend fun verifyAndUnlockWithPin(pin: String): Boolean {
            val valid = prefs.verifyPin(pin)
            if (valid) unlock()
            return valid
        }

        override suspend fun verifyAndUnlockWithPassword(password: String): Boolean {
            val valid = prefs.verifyPassword(password)
            if (valid) unlock()
            return valid
        }

        override fun authenticate(
            activity: FragmentActivity,
            title: String,
            subtitle: String?,
            onSuccess: () -> Unit,
            onError: (errorCode: Int, errString: CharSequence) -> Unit,
            onFailed: () -> Unit
        ) {
            onSuccess()
        }
    }
}
