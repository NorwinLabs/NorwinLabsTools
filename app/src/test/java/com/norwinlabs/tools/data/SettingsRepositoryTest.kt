package com.norwinlabs.tools.data

import androidx.appcompat.app.AppCompatDelegate
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

/**
 * Defaults matter here: an empty store has to read as "biometric off, AI analysis on, follow the
 * system theme", because that is what the SharedPreferences getters returned before the move.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class SettingsRepositoryTest {

    @get:Rule
    val temporaryFolder = TemporaryFolder()

    private lateinit var testScope: TestScope
    private lateinit var repository: SettingsRepository

    @Before
    fun setUp() {
        // The store and the test body must share one scheduler, otherwise a write dispatched to
        // DataStore's scope never runs while runTest waits on it.
        testScope = TestScope(UnconfinedTestDispatcher())
        val dataStore = PreferenceDataStoreFactory.create(scope = testScope) {
            File(temporaryFolder.root, "settings.preferences_pb")
        }
        repository = SettingsRepository(dataStore)
    }

    @Test
    fun `defaults match the previous SharedPreferences behaviour`() = testScope.runTest {
        assertEquals(AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM, repository.themeMode.first())
        assertFalse(repository.biometricEnabled.first())
        assertTrue(repository.aiAnalysisEnabled.first())
        assertEquals("", repository.geminiApiKey.first())
    }

    @Test
    fun `theme mode round trips`() = testScope.runTest {
        repository.setThemeMode(AppCompatDelegate.MODE_NIGHT_YES)
        assertEquals(AppCompatDelegate.MODE_NIGHT_YES, repository.themeMode.first())
    }

    @Test
    fun `biometric toggle round trips`() = testScope.runTest {
        repository.setBiometricEnabled(true)
        assertTrue(repository.biometricEnabled.first())

        repository.setBiometricEnabled(false)
        assertFalse(repository.biometricEnabled.first())
    }

    @Test
    fun `ai analysis toggle round trips`() = testScope.runTest {
        repository.setAiAnalysisEnabled(false)
        assertFalse(repository.aiAnalysisEnabled.first())
    }

    @Test
    fun `api key round trips and can be cleared`() = testScope.runTest {
        repository.setGeminiApiKey("secret-value")
        assertEquals("secret-value", repository.geminiApiKey.first())

        repository.setGeminiApiKey("")
        assertEquals("", repository.geminiApiKey.first())
    }
}
