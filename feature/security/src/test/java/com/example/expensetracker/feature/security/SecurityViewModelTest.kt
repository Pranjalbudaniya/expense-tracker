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
        fakeSecurityManager = FakeSecurityManager()

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

    // --- Pure Kotlin Fakes ---

    private class FakeSecurityPreferencesRepository : SecurityPreferencesRepository {
        val appLockEnabledFlow = MutableStateFlow(false)
        val lockTimeoutSecondsFlow = MutableStateFlow(0L)

        override val isAppLockEnabled: Flow<Boolean> = appLockEnabledFlow.asStateFlow()
        override val lockTimeoutSeconds: Flow<Long> = lockTimeoutSecondsFlow.asStateFlow()

        override suspend fun setAppLockEnabled(enabled: Boolean) {
            appLockEnabledFlow.value = enabled
        }

        override suspend fun setLockTimeoutSeconds(seconds: Long) {
            lockTimeoutSecondsFlow.value = seconds
        }
    }

    private class FakeSecurityManager : SecurityManager {
        var status = BiometricAuthStatus.AVAILABLE
        private val _isLocked = MutableStateFlow(false)
        override val isLocked: StateFlow<Boolean> = _isLocked.asStateFlow()

        override fun checkBiometricStatus(): BiometricAuthStatus = status
        override fun onAppBackgrounded() {}
        override suspend fun onAppForegrounded() {}
        override fun unlock() { _isLocked.value = false }
        override fun lock() { _isLocked.value = true }
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
