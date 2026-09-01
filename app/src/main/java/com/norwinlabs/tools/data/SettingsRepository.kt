package com.norwinlabs.tools.data

import androidx.appcompat.app.AppCompatDelegate
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton

/**
 * App settings, backed by DataStore.
 *
 * These used to be read straight off SharedPreferences inside fragments, which meant disk reads on
 * the main thread and no way to observe a change from another screen. Reads are Flows and writes
 * are suspending, so neither touches the main thread.
 */
@Singleton
class SettingsRepository @Inject constructor(
    private val dataStore: DataStore<Preferences>,
) {

    private val preferences: Flow<Preferences> = dataStore.data
        // A corrupt store should fall back to defaults rather than take the app down.
        .catch { cause -> if (cause is IOException) emit(emptyPreferences()) else throw cause }

    val themeMode: Flow<Int> = preferences.map {
        it[Keys.THEME] ?: AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM
    }

    val biometricEnabled: Flow<Boolean> = preferences.map { it[Keys.BIOMETRIC] ?: false }

    val aiAnalysisEnabled: Flow<Boolean> = preferences.map { it[Keys.AI_ANALYSIS] ?: true }

    val geminiApiKey: Flow<String> = preferences.map { it[Keys.API_KEY].orEmpty() }

    /**
     * The tools on the Home grid, in the user's order.
     *
     * Null means "never customised", which is not the same as an empty list: a user who removes
     * every tool should get an empty Home, not the default set handed back to them.
     */
    val homeToolIds: Flow<List<Int>?> = preferences.map { prefs ->
        prefs[Keys.HOME_TOOLS]?.split(",")?.mapNotNull { it.toIntOrNull() }
    }

    suspend fun setHomeToolIds(ids: List<Int>) =
        edit { it[Keys.HOME_TOOLS] = ids.joinToString(",") }

    suspend fun setThemeMode(mode: Int) = edit { it[Keys.THEME] = mode }

    suspend fun setBiometricEnabled(enabled: Boolean) = edit { it[Keys.BIOMETRIC] = enabled }

    suspend fun setAiAnalysisEnabled(enabled: Boolean) = edit { it[Keys.AI_ANALYSIS] = enabled }

    suspend fun setGeminiApiKey(key: String) = edit { it[Keys.API_KEY] = key }

    private suspend fun edit(block: (androidx.datastore.preferences.core.MutablePreferences) -> Unit) {
        dataStore.edit(block)
    }

    /**
     * Key names match the original SharedPreferences entries so the migration in DataModule
     * carries existing users' settings across untouched.
     */
    internal object Keys {
        val THEME = intPreferencesKey("app_theme")
        val BIOMETRIC = booleanPreferencesKey("enable_biometric")
        val AI_ANALYSIS = booleanPreferencesKey("enable_ai_analysis")
        val API_KEY = stringPreferencesKey("gemini_api_key")
        val HOME_TOOLS = stringPreferencesKey("home_tools_ids")

        val ALL_NAMES =
            setOf(THEME.name, BIOMETRIC.name, AI_ANALYSIS.name, API_KEY.name, HOME_TOOLS.name)
    }
}
