package com.norwinlabs.tools

import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import com.norwinlabs.tools.data.SettingsRepository
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/**
 * Opens a tool from anywhere.
 *
 * This used to be a 24-branch `when (tool.id)` inside HomeFragment, which meant Home was the only
 * screen that could launch anything. Behaviour now comes from the tool's own [ToolAction], and
 * the launcher is shared by Home and the Tools browser.
 */
class ToolLauncher(
    private val fragment: Fragment,
    private val settingsRepository: SettingsRepository,
    /** Home overrides this so tapping Update also refreshes its status widget. */
    private val onCheckUpdates: (() -> Unit)? = null,
) {

    fun open(tool: Tool) {
        when (val action = tool.action) {
            is ToolAction.Navigate -> navigate(tool, action.destinationId)

            is ToolAction.OpenUrl ->
                fragment.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(action.url)))

            is ToolAction.Local -> when (action.id) {
                LocalAction.IDEA_GENERATOR -> showIdeaGenerator()
                LocalAction.VIDEO_IDEAS -> showVideoIdeaCategoryDialog()
                LocalAction.CHECK_UPDATES -> onCheckUpdates?.invoke() ?: showUpdateCheck()
            }

            ToolAction.ComingSoon ->
                AlertDialog.Builder(fragment.requireContext())
                    .setTitle(tool.name)
                    .setMessage("${tool.name} module is coming soon!")
                    .setPositiveButton("OK", null)
                    .show()
        }
    }

    private fun navigate(tool: Tool, destinationId: Int) {
        if (!tool.requiresBiometric) {
            fragment.findNavController().navigate(destinationId)
            return
        }

        fragment.viewLifecycleOwner.lifecycleScope.launch {
            val required = settingsRepository.biometricEnabled.first()
            val helper = BiometricHelper(fragment.requireActivity())

            if (!required || !helper.canAuthenticate()) {
                fragment.findNavController().navigate(destinationId)
                return@launch
            }

            helper.showBiometricPrompt(
                "Restricted Tool",
                "Authentication Required",
                "Please authenticate to access this tool.",
                object : BiometricHelper.BiometricCallback {
                    override fun onAuthenticationSuccess() {
                        fragment.findNavController().navigate(destinationId)
                    }

                    override fun onAuthenticationError(error: String) {
                        toast("Auth Error: $error")
                    }

                    override fun onAuthenticationFailed() {
                        toast("Authentication failed")
                    }
                },
            )
        }
    }

    private fun showIdeaGenerator() {
        val themes = listOf("Cyberpunk", "Medieval", "Underwater", "Space Western", "Post-Apocalyptic")
        val mechanics = listOf("Permadeath", "Time Loop", "Deck Building", "Base Management", "Grappling Hook")
        val goals = listOf("Escaping a prison", "Finding a cure", "Building an empire", "Revenge", "Exploration")

        val idea = "Theme: ${themes.random()}\nMechanic: ${mechanics.random()}\nGoal: ${goals.random()}"

        AlertDialog.Builder(fragment.requireContext())
            .setTitle("PC Game Mechanic Idea")
            .setMessage(idea)
            .setPositiveButton("New Idea") { _, _ -> showIdeaGenerator() }
            .setNegativeButton("Close", null)
            .show()
    }

    private fun showVideoIdeaCategoryDialog() {
        val categories = arrayOf("Windhelm (Game)", "UE5 / Game Dev")
        AlertDialog.Builder(fragment.requireContext())
            .setTitle("Select Category")
            .setItems(categories) { _, which ->
                showVideoIdeaTypeDialog(if (which == 0) "Windhelm" else "General")
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun showVideoIdeaTypeDialog(category: String) {
        val options = arrayOf("YouTube Short", "Long-Form Video")
        AlertDialog.Builder(fragment.requireContext())
            .setTitle("Select Format ($category)")
            .setItems(options) { _, which ->
                generateVideoIdea(isShort = which == 0, category = category)
            }
            .setNegativeButton("Back") { _, _ -> showVideoIdeaCategoryDialog() }
            .show()
    }

    private fun generateVideoIdea(isShort: Boolean, category: String) {
        fragment.viewLifecycleOwner.lifecycleScope.launch {
            val apiKey = settingsRepository.geminiApiKey.first()

            if (apiKey.isEmpty()) {
                AlertDialog.Builder(fragment.requireContext())
                    .setTitle("API Key Required")
                    .setMessage("Please set your Gemini API key in Settings to use AI features.")
                    .setPositiveButton("Go to Settings") { _, _ ->
                        fragment.findNavController().navigate(R.id.SettingsFragment)
                    }
                    .setNegativeButton("Cancel", null)
                    .show()
                return@launch
            }

            val loading = AlertDialog.Builder(fragment.requireContext())
                .setTitle("Consulting AI...")
                .setMessage("Generating a custom idea for $category...")
                .setCancelable(false)
                .show()

            VideoIdeaManager(apiKey).generateIdea(
                isShort,
                category,
                object : VideoIdeaManager.VideoIdeaCallback {
                    override fun onSuccess(idea: String) {
                        loading.dismiss()
                        if (!fragment.isAdded) return
                        AlertDialog.Builder(fragment.requireContext())
                            .setTitle(if (isShort) "AI Short Idea" else "AI Video Idea")
                            .setMessage(idea)
                            .setPositiveButton("Generate Another") { _, _ ->
                                generateVideoIdea(isShort, category)
                            }
                            .setNeutralButton("Change Settings") { _, _ ->
                                showVideoIdeaCategoryDialog()
                            }
                            .setNegativeButton("Close", null)
                            .show()
                    }

                    override fun onError(error: String) {
                        loading.dismiss()
                        toast("AI Error: $error")
                    }
                },
            )
        }
    }

    /** Generic update check, for screens with no update widget of their own to drive. */
    private fun showUpdateCheck() {
        val context = fragment.context ?: return
        toast("Checking for updates...")

        UpdateManager(context).checkForUpdates(object : UpdateManager.UpdateCallback {
            override fun onUpdateAvailable(latestVersion: String, downloadUrl: String) {
                if (!fragment.isAdded) return
                AlertDialog.Builder(fragment.requireContext())
                    .setTitle("Update Available")
                    .setMessage("Version $latestVersion is available.")
                    .setPositiveButton("OK", null)
                    .show()
            }

            override fun onNoUpdate() = toast("You are on the latest version")

            override fun onError(error: String, url: String) = toast("Update check failed: $error")

            override fun onDownloadProgress(progress: Int) = Unit
        })
    }

    private fun toast(message: String) {
        fragment.activity?.runOnUiThread {
            if (fragment.isAdded) {
                Toast.makeText(fragment.requireContext(), message, Toast.LENGTH_SHORT).show()
            }
        }
    }
}
