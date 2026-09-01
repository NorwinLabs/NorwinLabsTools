package com.example.norwinlabstools

import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.HapticFeedbackConstants
import androidx.fragment.app.Fragment
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.activity.OnBackPressedCallback
import androidx.appcompat.app.AlertDialog
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.ItemTouchHelper
import androidx.recyclerview.widget.RecyclerView
import com.example.norwinlabstools.data.SettingsRepository
import com.example.norwinlabstools.databinding.FragmentHomeBinding
import com.example.norwinlabstools.databinding.LayoutAddToolsBinding
import com.google.android.material.bottomsheet.BottomSheetDialog
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.util.Calendar
import javax.inject.Inject

@AndroidEntryPoint
class HomeFragment : Fragment() {

    private var _binding: FragmentHomeBinding? = null
    private val binding get() = _binding!!
    private lateinit var adapter: ToolsAdapter
    private var aiManager: VideoIdeaManager? = null

    @Inject lateinit var settingsRepository: SettingsRepository

    // The saved Home layout still lives in SharedPreferences; it moves to the data layer with the
    // Home ViewModel rather than here, where only the settings keys were migrated.
    private val PREFS_NAME = "norwin_prefs"
    private val KEY_HOME_TOOLS = "home_tools_ids"

    private var currentTools = mutableListOf<Tool>()

    private val backPressedCallback = object : OnBackPressedCallback(false) {
        override fun handleOnBackPressed() {
            if (adapter.isEditMode) {
                adapter.isEditMode = false
                updateToolbar()
                isEnabled = false
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        loadHomeTools()
        requireActivity().onBackPressedDispatcher.addCallback(this, backPressedCallback)
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentHomeBinding.inflate(inflater, container, false)
        return binding.root
    }

    @SuppressLint("ClickableViewAccessibility")
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        adapter = ToolsAdapter(
            currentTools,
            onToolClick = { tool ->
                view.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
                if (adapter.isEditMode) {
                    adapter.isEditMode = false
                    updateToolbar()
                } else {
                    openTool(tool)
                }
            },
            onToolLongClick = { toolView, _ ->
                if (!adapter.isEditMode) {
                    toolView.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
                    adapter.isEditMode = true
                    updateToolbar()
                }
            },
            onRemoveClick = { tool ->
                adapter.removeTool(tool)
                saveHomeTools()
            }
        )

        binding.recyclerviewTools.layoutManager = GridLayoutManager(context, 2)
        binding.recyclerviewTools.adapter = adapter

        setupDragAndDrop()

        binding.layoutContainer.setOnTouchListener { _, event ->
            if (event.action == MotionEvent.ACTION_DOWN && adapter.isEditMode) {
                adapter.isEditMode = false
                updateToolbar()
                true
            } else false
        }
        
        binding.scrollviewHome.setOnTouchListener { _, event ->
             if (event.action == MotionEvent.ACTION_DOWN && adapter.isEditMode) {
                adapter.isEditMode = false
                updateToolbar()
                true
            } else false
        }

        binding.cardWidgetUpdates.setOnClickListener {
            view.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
            checkForUpdates()
        }

        setupHeaderAndFooter()
        // Without this, the widget's "Checking..." placeholder text just sits there forever
        // until the user happens to tap it - it looks like a check is already in progress.
        checkForUpdates(silent = true)
    }

    override fun onResume() {
        super.onResume()
        val fab = activity?.findViewById<View>(R.id.fab)
        fab?.setOnClickListener {
            it.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
            if (adapter.isEditMode) {
                adapter.isEditMode = false
                updateToolbar()
            } else {
                showAddToolsBottomSheet()
            }
        }
    }

    fun filterTools(query: String) {
        adapter.filter(query)
        binding.textviewNoToolsFound.visibility = if (adapter.itemCount == 0) View.VISIBLE else View.GONE
    }

    /**
     * The single dispatch point for a tool card tap. What a tool does is declared by its own
     * [ToolAction] in [ToolRegistry], so adding a tool never means editing this function.
     */
    private fun openTool(tool: Tool) {
        when (val action = tool.action) {
            is ToolAction.Navigate ->
                if (tool.requiresBiometric) checkBiometricAndNavigate(action.actionId)
                else findNavController().navigate(action.actionId)

            is ToolAction.OpenUrl ->
                startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(action.url)))

            is ToolAction.Local -> when (action.id) {
                LocalAction.IDEA_GENERATOR -> showIdeaGenerator()
                LocalAction.VIDEO_IDEAS -> showVideoIdeaCategoryDialog()
                LocalAction.CHECK_UPDATES -> checkForUpdates()
            }

            ToolAction.ComingSoon ->
                AlertDialog.Builder(requireContext())
                    .setTitle(tool.name)
                    .setMessage("${tool.name} module is coming soon!")
                    .setPositiveButton("OK", null)
                    .show()
        }
    }

    private fun checkBiometricAndNavigate(destinationId: Int) {
        viewLifecycleOwner.lifecycleScope.launch {
            val isBiometricEnabled = settingsRepository.biometricEnabled.first()
            val biometricHelper = BiometricHelper(requireActivity())

            if (!isBiometricEnabled || !biometricHelper.canAuthenticate()) {
                findNavController().navigate(destinationId)
                return@launch
            }

            biometricHelper.showBiometricPrompt(
                "Restricted Tool",
                "Authentication Required",
                "Please authenticate to access this tool.",
                object : BiometricHelper.BiometricCallback {
                    override fun onAuthenticationSuccess() {
                        findNavController().navigate(destinationId)
                    }
                    override fun onAuthenticationError(error: String) {
                        Toast.makeText(requireContext(), "Auth Error: $error", Toast.LENGTH_SHORT).show()
                    }
                    override fun onAuthenticationFailed() {
                        Toast.makeText(requireContext(), "Authentication failed", Toast.LENGTH_SHORT).show()
                    }
                }
            )
        }
    }

    private fun showVideoIdeaCategoryDialog() {
        val categories = arrayOf("Windhelm (Game)", "UE5 / Game Dev")
        AlertDialog.Builder(requireContext())
            .setTitle("Select Category")
            .setItems(categories) { _, which ->
                val category = if (which == 0) "Windhelm" else "General"
                showVideoIdeaTypeDialog(category)
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun showVideoIdeaTypeDialog(category: String) {
        val options = arrayOf("YouTube Short", "Long-Form Video")
        AlertDialog.Builder(requireContext())
            .setTitle("Select Format ($category)")
            .setItems(options) { _, which ->
                generateAIVideoIdea(isShort = (which == 0), category = category)
            }
            .setNegativeButton("Back") { _, _ -> showVideoIdeaCategoryDialog() }
            .show()
    }

    private fun generateAIVideoIdea(isShort: Boolean, category: String) {
        viewLifecycleOwner.lifecycleScope.launch {
            val apiKey = settingsRepository.geminiApiKey.first()

            if (apiKey.isEmpty()) {
                AlertDialog.Builder(requireContext())
                    .setTitle("API Key Required")
                    .setMessage("Please set your Gemini API key in Settings to use AI features.")
                    .setPositiveButton("Go to Settings") { _, _ ->
                        findNavController().navigate(R.id.action_HomeFragment_to_SettingsFragment)
                    }
                    .setNegativeButton("Cancel", null)
                    .show()
                return@launch
            }

            if (aiManager == null) {
                aiManager = VideoIdeaManager(apiKey)
            }

            val loadingDialog = AlertDialog.Builder(requireContext())
                .setTitle("Consulting AI...")
                .setMessage("Generating a custom idea for $category...")
                .setCancelable(false)
                .show()

            aiManager?.generateIdea(isShort, category, object : VideoIdeaManager.VideoIdeaCallback {
                override fun onSuccess(idea: String) {
                    loadingDialog.dismiss()
                    activity?.runOnUiThread {
                        AlertDialog.Builder(requireContext())
                            .setTitle(if (isShort) "AI Short Idea" else "AI Video Idea")
                            .setMessage(idea)
                            .setPositiveButton("Generate Another") { _, _ -> generateAIVideoIdea(isShort, category) }
                            .setNeutralButton("Change Settings") { _, _ -> showVideoIdeaCategoryDialog() }
                            .setNegativeButton("Close", null)
                            .show()
                    }
                }

                override fun onError(error: String) {
                    loadingDialog.dismiss()
                    activity?.runOnUiThread {
                        Toast.makeText(requireContext(), "AI Error: $error", Toast.LENGTH_LONG).show()
                    }
                }
            })
        }
    }

    private fun showIdeaGenerator() {
        val themes = listOf("Cyberpunk", "Medieval", "Underwater", "Space Western", "Post-Apocalyptic")
        val mechanics = listOf("Permadeath", "Time Loop", "Deck Building", "Base Management", "Grappling Hook")
        val goal = listOf("Escaping a prison", "Finding a cure", "Building an empire", "Revenge", "Exploration")

        val idea = "Theme: ${themes.random()}\nMechanic: ${mechanics.random()}\nGoal: ${goal.random()}"

        AlertDialog.Builder(requireContext())
            .setTitle("PC Game Mechanic Idea")
            .setMessage(idea)
            .setPositiveButton("New Idea") { _, _ -> showIdeaGenerator() }
            .setNegativeButton("Close", null)
            .show()
    }

    private fun updateToolbar() {
        val activity = activity as? MainActivity ?: return
        if (adapter.isEditMode) {
            activity.supportActionBar?.title = "Edit Home"
            backPressedCallback.isEnabled = true
        } else {
            activity.supportActionBar?.title = getString(R.string.app_name)
            backPressedCallback.isEnabled = false
        }
    }

    /**
     * @param silent When true (the automatic check on Home load), only the status text updates -
     * no Toasts or dialogs, so opening the app doesn't interrupt with update prompts the user
     * didn't ask for. Tapping the widget explicitly always gets the full interactive flow.
     */
    private fun checkForUpdates(silent: Boolean = false) {
        val updateManager = UpdateManager(requireContext())
        binding.textviewUpdateStatus.text = "Checking..."
        if (!silent) {
            Toast.makeText(requireContext(), "Checking for updates...", Toast.LENGTH_SHORT).show()
        }

        updateManager.checkForUpdates(object : UpdateManager.UpdateCallback {
            override fun onUpdateAvailable(latestVersion: String, downloadUrl: String) {
                activity?.runOnUiThread {
                    binding.textviewUpdateStatus.text = "New version: $latestVersion"
                    binding.textviewUpdateStatus.setTextColor(resources.getColor(android.R.color.holo_green_dark, null))
                    if (silent) return@runOnUiThread

                    AlertDialog.Builder(requireContext())
                        .setTitle("Update Available")
                        .setMessage("A new version ($latestVersion) is available. Would you like to download it?")
                        .setPositiveButton("Download") { _, _ ->
                            val progressDialog = AlertDialog.Builder(requireContext())
                                .setTitle("Downloading...")
                                .setMessage("Please wait while the update downloads.")
                                .setCancelable(false)
                                .show()

                            updateManager.downloadAndInstallApk(downloadUrl, "NorwinLabsTools-Update.apk", object : UpdateManager.UpdateCallback {
                                override fun onUpdateAvailable(latestVersion: String, downloadUrl: String) {}
                                override fun onNoUpdate() {}
                                override fun onError(error: String, url: String) {
                                    progressDialog.dismiss()
                                    Toast.makeText(requireContext(), "Download Error: $error", Toast.LENGTH_LONG).show()
                                }
                                override fun onDownloadProgress(progress: Int) {
                                    progressDialog.setMessage("Downloading: $progress%")
                                }
                            })
                        }
                        .setNegativeButton("Later", null)
                        .show()
                }
            }
            override fun onNoUpdate() {
                activity?.runOnUiThread {
                    binding.textviewUpdateStatus.text = "Up to date"
                    binding.textviewUpdateStatus.setTextColor(resources.getColor(android.R.color.darker_gray, null))
                    if (!silent) {
                        Toast.makeText(requireContext(), "You are on the latest version", Toast.LENGTH_SHORT).show()
                    }
                }
            }
            override fun onError(error: String, url: String) {
                activity?.runOnUiThread {
                    binding.textviewUpdateStatus.text = "Check failed"
                    if (!silent) {
                        AlertDialog.Builder(requireContext())
                            .setTitle("Update Failed")
                            .setMessage("$error\n\nChecked URL:\n$url")
                            .setPositiveButton("OK", null).show()
                    }
                }
            }
            override fun onDownloadProgress(progress: Int) {}
        })
    }

    private fun setupDragAndDrop() {
        val itemTouchHelper = ItemTouchHelper(object : ItemTouchHelper.SimpleCallback(
            ItemTouchHelper.UP or ItemTouchHelper.DOWN or ItemTouchHelper.START or ItemTouchHelper.END, 0) {
            override fun onMove(r: RecyclerView, v: RecyclerView.ViewHolder, t: RecyclerView.ViewHolder): Boolean {
                adapter.onItemMove(v.adapterPosition, t.adapterPosition)
                saveHomeTools()
                return true
            }
            override fun onSwiped(v: RecyclerView.ViewHolder, d: Int) {}
            override fun isLongPressDragEnabled(): Boolean = adapter.isEditMode
        })
        itemTouchHelper.attachToRecyclerView(binding.recyclerviewTools)
    }

    private fun showAddToolsBottomSheet() {
        val currentToolIds = adapter.getItems().map { it.id }.toSet()
        val availableToAdd = ToolRegistry.all.filter { it.id !in currentToolIds }.toMutableList()
        if (availableToAdd.isEmpty()) {
            AlertDialog.Builder(requireContext()).setTitle("No Tools Available").setMessage("All tools are already on your home screen.").setPositiveButton("OK", null).show()
            return
        }
        val dialog = BottomSheetDialog(requireContext())
        val bottomSheetBinding = LayoutAddToolsBinding.inflate(layoutInflater)
        val sheetAdapter = CategorizedToolsAdapter(availableToAdd, ToolRegistry.categoryOrder) { tool ->
            adapter.addTool(tool)
            saveHomeTools()
            dialog.dismiss()
        }
        val spanCount = 3
        val gridLayoutManager = GridLayoutManager(context, spanCount)
        gridLayoutManager.spanSizeLookup = sheetAdapter.spanSizeLookup(spanCount)
        bottomSheetBinding.recyclerviewAvailableTools.layoutManager = gridLayoutManager
        bottomSheetBinding.recyclerviewAvailableTools.adapter = sheetAdapter
        dialog.setContentView(bottomSheetBinding.root)
        dialog.show()
    }

    private fun saveHomeTools() {
        val ids = adapter.getItems().joinToString(",") { it.id.toString() }
        requireContext().getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).edit().putString(KEY_HOME_TOOLS, ids).apply()
    }

    private fun loadHomeTools() {
        val prefs = requireContext().getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val savedIds = prefs.getString(KEY_HOME_TOOLS, null)
        currentTools.clear()
        if (savedIds != null) {
            // toIntOrNull rather than toInt: a malformed prefs entry should drop that one tool,
            // not crash Home on launch.
            val idList = savedIds.split(",").mapNotNull { it.toIntOrNull() }
            currentTools.addAll(ToolRegistry.byIds(idList))
        } else {
            currentTools.addAll(ToolRegistry.all.take(4))
        }
    }

    private fun setupHeaderAndFooter() {
        val versionName = try {
            val pInfo = requireContext().packageManager.getPackageInfo(requireContext().packageName, 0)
            pInfo.versionName
        } catch (e: Exception) {
            "1.0.0"
        }
        
        binding.textviewHeaderVersion.text = "v$versionName"
        binding.textviewVersion.text = "Version $versionName"

        val year = Calendar.getInstance().get(Calendar.YEAR)
        binding.textviewCopyright.text = "© $year NorwinLabs"
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}