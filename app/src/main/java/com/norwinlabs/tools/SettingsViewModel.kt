package com.norwinlabs.tools

import androidx.appcompat.app.AppCompatDelegate
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.norwinlabs.tools.data.SettingsRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

data class SettingsUiState(
    val themeMode: Int,
    val biometricEnabled: Boolean,
    val aiAnalysisEnabled: Boolean,
    val geminiApiKey: String,
)

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val repository: SettingsRepository,
) : ViewModel() {

    /**
     * Null until the store has been read once. The screen waits for a real value rather than
     * binding defaults first, which would otherwise echo an empty API key straight back to disk.
     */
    val uiState: StateFlow<SettingsUiState?> = combine(
        repository.themeMode,
        repository.biometricEnabled,
        repository.aiAnalysisEnabled,
        repository.geminiApiKey,
    ) { theme, biometric, aiAnalysis, apiKey ->
        SettingsUiState(theme, biometric, aiAnalysis, apiKey)
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS),
        initialValue = null,
    )

    fun setThemeMode(mode: Int) {
        viewModelScope.launch {
            repository.setThemeMode(mode)
            AppCompatDelegate.setDefaultNightMode(mode)
        }
    }

    fun setBiometricEnabled(enabled: Boolean) {
        viewModelScope.launch { repository.setBiometricEnabled(enabled) }
    }

    fun setAiAnalysisEnabled(enabled: Boolean) {
        viewModelScope.launch { repository.setAiAnalysisEnabled(enabled) }
    }

    fun setGeminiApiKey(key: String) {
        viewModelScope.launch { repository.setGeminiApiKey(key) }
    }

    private companion object {
        const val STOP_TIMEOUT_MS = 5_000L
    }
}
