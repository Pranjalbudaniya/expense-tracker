package com.example.expensetracker.core.preferences

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.util.UUID

@OptIn(ExperimentalCoroutinesApi::class)
class DataStorePreferencesRepositoryTest {

    @get:Rule
    val temporaryFolder = TemporaryFolder()

    private fun TestScope.createTestRepository(): Pair<DataStorePreferencesRepository, DataStore<Preferences>> {
        val file = temporaryFolder.newFile("test_prefs_${UUID.randomUUID()}.preferences_pb")
        val dataStore = PreferenceDataStoreFactory.create(
            scope = backgroundScope,
            produceFile = { file }
        )
        return Pair(DataStorePreferencesRepository(dataStore), dataStore)
    }

    @Test
    fun defaultPreferences_whenNoPreferencesStored_returnsDefaults() = runTest {
        val (repository, _) = createTestRepository()
        val preferences = repository.userPreferences.first()
        assertEquals(ThemeMode.SYSTEM, preferences.themeMode)
        assertTrue(preferences.isDynamicColorEnabled)
        assertNull(preferences.customAccentColor)
        assertEquals("INR", preferences.currencyCode)
    }

    @Test
    fun changeThemeMode_updatesThemeCorrectly() = runTest {
        val (repository, _) = createTestRepository()

        repository.setThemeMode(ThemeMode.DARK)
        assertEquals(ThemeMode.DARK, repository.userPreferences.first().themeMode)

        repository.setThemeMode(ThemeMode.LIGHT)
        assertEquals(ThemeMode.LIGHT, repository.userPreferences.first().themeMode)

        repository.setThemeMode(ThemeMode.SYSTEM)
        assertEquals(ThemeMode.SYSTEM, repository.userPreferences.first().themeMode)
    }

    @Test
    fun enableAndDisableDynamicColor_updatesCorrectly() = runTest {
        val (repository, _) = createTestRepository()

        repository.setDynamicColorEnabled(false)
        assertFalse(repository.userPreferences.first().isDynamicColorEnabled)

        repository.setDynamicColorEnabled(true)
        assertTrue(repository.userPreferences.first().isDynamicColorEnabled)
    }

    @Test
    fun setAndClearCustomAccentColor_updatesCorrectly() = runTest {
        val (repository, _) = createTestRepository()

        val accentColor = 0xFF6200EEL
        repository.setCustomAccentColor(accentColor)
        assertEquals(accentColor, repository.userPreferences.first().customAccentColor)

        repository.clearCustomAccentColor()
        assertNull(repository.userPreferences.first().customAccentColor)

        repository.setCustomAccentColor(0xFF00FF00L)
        assertEquals(0xFF00FF00L, repository.userPreferences.first().customAccentColor)

        repository.setCustomAccentColor(null)
        assertNull(repository.userPreferences.first().customAccentColor)
    }

    @Test
    fun changeCurrency_withValidCurrency_updatesCorrectly() = runTest {
        val (repository, _) = createTestRepository()

        repository.setCurrencyCode("USD")
        assertEquals("USD", repository.userPreferences.first().currencyCode)

        repository.setCurrencyCode("EUR")
        assertEquals("EUR", repository.userPreferences.first().currencyCode)
    }

    @Test
    fun changeCurrency_withBlankOrInvalidCode_throwsException() = runTest {
        val (repository, _) = createTestRepository()

        try {
            repository.setCurrencyCode("   ")
            fail("Expected IllegalArgumentException for blank currency")
        } catch (_: IllegalArgumentException) { }

        try {
            repository.setCurrencyCode("INVALID")
            fail("Expected IllegalArgumentException for invalid currency")
        } catch (_: IllegalArgumentException) { }

        // Currency should remain default
        assertEquals("INR", repository.userPreferences.first().currencyCode)
    }

    @Test
    fun corruptedOrInvalidStoredTheme_fallsBackToSystem() = runTest {
        val (repository, dataStore) = createTestRepository()

        dataStore.edit { preferences ->
            preferences[PreferencesKeys.THEME_MODE] = "NON_EXISTENT_THEME"
        }
        assertEquals(ThemeMode.SYSTEM, repository.userPreferences.first().themeMode)
    }

    @Test
    fun corruptedOrInvalidStoredCurrency_fallsBackToINR() = runTest {
        val (repository, dataStore) = createTestRepository()

        dataStore.edit { preferences ->
            preferences[PreferencesKeys.CURRENCY_CODE] = "XYZ999"
        }
        assertEquals("INR", repository.userPreferences.first().currencyCode)

        dataStore.edit { preferences ->
            preferences[PreferencesKeys.CURRENCY_CODE] = "   "
        }
        assertEquals("INR", repository.userPreferences.first().currencyCode)
    }

    @Test
    fun corruptedOrInvalidStoredAccent_fallsBackToNull() = runTest {
        val (repository, dataStore) = createTestRepository()

        dataStore.edit { preferences ->
            preferences[PreferencesKeys.CUSTOM_ACCENT_COLOR] = -1L
        }
        assertNull(repository.userPreferences.first().customAccentColor)

        dataStore.edit { preferences ->
            preferences[PreferencesKeys.CUSTOM_ACCENT_COLOR] = 0x100000000L
        }
        assertNull(repository.userPreferences.first().customAccentColor)
    }

    @Test
    fun preferences_surviveRepeatedReadsAndNewRepositoryInstance() = runTest {
        val (repository, dataStore) = createTestRepository()

        repository.setThemeMode(ThemeMode.DARK)
        repository.setDynamicColorEnabled(false)
        repository.setCustomAccentColor(0xFF336699L)
        repository.setCurrencyCode("EUR")

        // First read
        val firstRead = repository.userPreferences.first()
        assertEquals(ThemeMode.DARK, firstRead.themeMode)
        assertFalse(firstRead.isDynamicColorEnabled)
        assertEquals(0xFF336699L, firstRead.customAccentColor)
        assertEquals("EUR", firstRead.currencyCode)

        // Read through a newly constructed repository pointing to the same DataStore
        val newRepo = DataStorePreferencesRepository(dataStore)
        val secondRead = newRepo.userPreferences.first()
        assertEquals(firstRead, secondRead)
    }

    @Test
    fun changeFontSize_updatesCorrectly() = runTest {
        val (repository, _) = createTestRepository()

        assertEquals(FontSizePreference.DEFAULT, repository.userPreferences.first().fontSize)

        repository.setFontSize(FontSizePreference.LARGE)
        assertEquals(FontSizePreference.LARGE, repository.userPreferences.first().fontSize)

        repository.setFontSize(FontSizePreference.SMALL)
        assertEquals(FontSizePreference.SMALL, repository.userPreferences.first().fontSize)

        repository.setFontSize(FontSizePreference.EXTRA_LARGE)
        assertEquals(FontSizePreference.EXTRA_LARGE, repository.userPreferences.first().fontSize)
    }
}
