package com.norwinlabs.tools

import android.annotation.SuppressLint
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
import com.norwinlabs.tools.data.SettingsRepository
import com.norwinlabs.tools.databinding.FragmentHomeBinding
import com.norwinlabs.tools.databinding.LayoutAddToolsBinding
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

    @Inject lateinit var settingsRepository: SettingsRepository

    private lateinit var launcher: ToolLauncher

    private val currentTools = mutableListOf<Tool>()

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

        // Home keeps its own update handling so tapping the Update tool also refreshes the
        // status widget on this screen; every other action is the shared behaviour.
        launcher = ToolLauncher(this, settingsRepository, onCheckUpdates = { checkForUpdates() })

        adapter = ToolsAdapter(
            currentTools,
            onToolClick = { tool ->
                view.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
                if (adapter.isEditMode) {
                    adapter.isEditMode = false
                    updateToolbar()
                } else {
                    launcher.open(tool)
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

        loadHomeTools()

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
        val ids = adapter.getItems().map { it.id }
        viewLifecycleOwner.lifecycleScope.launch { settingsRepository.setHomeToolIds(ids) }
    }

    /**
     * Reads the saved layout off the main thread, then fills the grid. The list is only loaded
     * once rather than observed: the adapter owns the order while the user is dragging tiles
     * around, so re-submitting it from the store mid-gesture would fight them.
     */
    private fun loadHomeTools() {
        viewLifecycleOwner.lifecycleScope.launch {
            val savedIds = settingsRepository.homeToolIds.first()
            adapter.setTools(
                if (savedIds == null) ToolRegistry.all.take(DEFAULT_HOME_TOOL_COUNT)
                else ToolRegistry.byIds(savedIds)
            )
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

    private companion object {
        /** What a brand new install starts with before the user customises Home. */
        const val DEFAULT_HOME_TOOL_COUNT = 4
    }
}