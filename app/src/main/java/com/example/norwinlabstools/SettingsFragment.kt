package com.example.norwinlabstools

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.widget.doAfterTextChanged
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.lifecycleScope
import com.example.norwinlabstools.databinding.FragmentSettingsBinding
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

@AndroidEntryPoint
class SettingsFragment : Fragment() {

    private var _binding: FragmentSettingsBinding? = null
    private val binding get() = _binding!!

    private val viewModel: SettingsViewModel by viewModels()

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentSettingsBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        // Bind once per view, from the first real value. Re-applying on every emission would
        // fight the user's typing, and re-running on each STARTED would stack a second text
        // watcher every time the screen came back from the background.
        viewLifecycleOwner.lifecycleScope.launch {
            bind(viewModel.uiState.filterNotNull().first())
        }
    }

    private fun bind(state: SettingsUiState) {
        when (state.themeMode) {
            AppCompatDelegate.MODE_NIGHT_NO -> binding.radioLight.isChecked = true
            AppCompatDelegate.MODE_NIGHT_YES -> binding.radioDark.isChecked = true
            else -> binding.radioSystem.isChecked = true
        }
        binding.switchBiometric.isChecked = state.biometricEnabled
        binding.switchAiAnalysis.isChecked = state.aiAnalysisEnabled
        binding.editApiKey.setText(state.geminiApiKey)

        // Listeners are attached only after the initial values are in place, so restoring state
        // can't be mistaken for the user changing it.
        binding.radioGroupTheme.setOnCheckedChangeListener { _, checkedId ->
            viewModel.setThemeMode(
                when (checkedId) {
                    R.id.radio_light -> AppCompatDelegate.MODE_NIGHT_NO
                    R.id.radio_dark -> AppCompatDelegate.MODE_NIGHT_YES
                    else -> AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM
                }
            )
        }
        binding.switchBiometric.setOnCheckedChangeListener { _, isChecked ->
            viewModel.setBiometricEnabled(isChecked)
        }
        binding.switchAiAnalysis.setOnCheckedChangeListener { _, isChecked ->
            viewModel.setAiAnalysisEnabled(isChecked)
        }
        binding.editApiKey.doAfterTextChanged { text ->
            viewModel.setGeminiApiKey(text?.toString().orEmpty())
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
