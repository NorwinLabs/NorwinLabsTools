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
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.ItemTouchHelper
import androidx.recyclerview.widget.RecyclerView
import com.example.norwinlabstools.databinding.FragmentFirstBinding
import com.example.norwinlabstools.databinding.LayoutAddToolsBinding
import com.google.android.material.bottomsheet.BottomSheetDialog
import java.util.Calendar

class FirstFragment : Fragment() {

    private var _binding: FragmentFirstBinding? = null
    private val binding get() = _binding!!
    private lateinit var adapter: ToolsAdapter

    private val PREFS_NAME = "norwin_prefs"
    private val KEY_HOME_TOOLS = "home_tools_ids"

    private val allTools = listOf(
        Tool(1, "Calendar", android.R.drawable.ic_menu_today),
        Tool(2, "Converter", android.R.drawable.ic_menu_compass),
        Tool(3, "Notes", android.R.drawable.ic_menu_edit),
        Tool(4, "Settings", android.R.drawable.ic_menu_manage),
        Tool(5, "About", android.R.drawable.ic_menu_info_details),
        Tool(9, "Idea Generator", android.R.drawable.ic_menu_send),
        Tool(10, "Color Picker", android.R.drawable.ic_menu_gallery),
        Tool(11, "Dice Roller", android.R.drawable.ic_menu_help),
        Tool(12, "Update", android.R.drawable.ic_menu_upload),
        Tool(13, "Windhelm", android.R.drawable.ic_menu_view),
        Tool(14, "Lore Gen", android.R.drawable.ic_menu_sort_alphabetically),
        Tool(15, "UE5 Guide", android.R.drawable.ic_menu_directions),
        Tool(16, "Trello", android.R.drawable.ic_menu_agenda),
        Tool(17, "SSH Client", android.R.drawable.ic_dialog_dialer),
        Tool(18, "Ping Tool", android.R.drawable.ic_menu_revert),
        Tool(19, "Pass Gen", android.R.drawable.ic_lock_lock),
        Tool(20, "Net Scanner", android.R.drawable.ic_menu_share)
    )

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
        _binding = FragmentFirstBinding.inflate(inflater, container, false)
        return binding.root
    }

    @SuppressLint("ClickableViewAccessibility")
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        adapter = ToolsAdapter(
            currentTools,
            onToolClick = { tool ->
                if (adapter.isEditMode) {
                    adapter.isEditMode = false
                    updateToolbar()
                } else {
                    when(tool.id) {
                        4 -> findNavController().navigate(R.id.action_FirstFragment_to_SettingsFragment)
                        1 -> { /* Navigate to Calendar if implemented */ }
                        9 -> showIdeaGenerator()
                        12 -> checkForUpdates()
                        13 -> {
                            val intent = Intent(Intent.ACTION_VIEW, Uri.parse("http://windhelmthegame.ddns.net"))
                            startActivity(intent)
                        }
                        16 -> {
                            val intent = Intent(Intent.ACTION_VIEW, Uri.parse("https://trello.com/b/SVY6LFSZ/windhelm-main-development"))
                            startActivity(intent)
                        }
                        else -> {
                             AlertDialog.Builder(requireContext())
                                .setTitle(tool.name)
                                .setMessage("${tool.name} module is coming soon!")
                                .setPositiveButton("OK", null)
                                .show()
                        }
                    }
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

        val fab = activity?.findViewById<View>(R.id.fab)
        fab?.setOnClickListener {
            if (adapter.isEditMode) {
                adapter.isEditMode = false
                updateToolbar()
            } else {
                showAddToolsBottomSheet()
            }
        }

        binding.layoutContainer.setOnTouchListener { _, event ->
            if (event.action == MotionEvent.ACTION_DOWN && adapter.isEditMode) {
                adapter.isEditMode = false
                updateToolbar()
                true
            } else false
        }
        
        binding.scrollviewFirst.setOnTouchListener { _, event ->
             if (event.action == MotionEvent.ACTION_DOWN && adapter.isEditMode) {
                adapter.isEditMode = false
                updateToolbar()
                true
            } else false
        }

        binding.cardWidgetUpdates.setOnClickListener {
            checkForUpdates()
        }

        setupFooter()
        autoCheckForUpdates()
        checkIntentAction()
    }

    fun filterTools(query: String) {
        adapter.filter(query)
    }

    private fun checkIntentAction() {
        val intent = activity?.intent
        val action = intent?.action ?: return
        
        when {
            action == "OPEN_ADD_TOOLS_ACTION" -> showAddToolsBottomSheet()
            action.startsWith("LAUNCH_TOOL_") -> {
                val toolId = action.removePrefix("LAUNCH_TOOL_").toIntOrNull()
                if (toolId != null) {
                    // Handle quick launch
                    val tool = allTools.find { it.id == toolId }
                    if (tool != null) {
                        // Logic to launch tool
                    }
                }
            }
        }
        intent.action = null
    }

    override fun onResume() {
        super.onResume()
        activity?.findViewById<View>(R.id.fab)?.setOnClickListener {
            if (adapter.isEditMode) {
                adapter.isEditMode = false
                updateToolbar()
            } else {
                showAddToolsBottomSheet()
            }
        }
        checkIntentAction()
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

    private fun autoCheckForUpdates() {
        val updateManager = UpdateManager(requireContext())
        updateManager.checkForUpdates(object : UpdateManager.UpdateCallback {
            override fun onUpdateAvailable(latestVersion: String, downloadUrl: String) {
                activity?.runOnUiThread {
                    binding.textviewUpdateStatus.text = "New version: $latestVersion"
                    binding.textviewUpdateStatus.setTextColor(resources.getColor(android.R.color.holo_green_dark, null))
                }
            }
            override fun onNoUpdate() {
                activity?.runOnUiThread { binding.textviewUpdateStatus.text = "Up to date" }
            }
            override fun onError(error: String, url: String) {
                activity?.runOnUiThread { binding.textviewUpdateStatus.text = "Check failed" }
            }
        })
    }

    private fun checkForUpdates() {
        val updateManager = UpdateManager(requireContext())
        Toast.makeText(requireContext(), "Checking for updates...", Toast.LENGTH_SHORT).show()
        updateManager.checkForUpdates(object : UpdateManager.UpdateCallback {
            override fun onUpdateAvailable(latestVersion: String, downloadUrl: String) {
                activity?.runOnUiThread {
                    AlertDialog.Builder(requireContext())
                        .setTitle("Update Available")
                        .setMessage("A new version ($latestVersion) is available. Would you like to download it?")
                        .setPositiveButton("Download") { _, _ ->
                            if (downloadUrl.isNotEmpty()) {
                                startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(downloadUrl)))
                            } else {
                                Toast.makeText(requireContext(), "Download URL not found", Toast.LENGTH_LONG).show()
                            }
                        }
                        .setNegativeButton("Later", null)
                        .show()
                }
            }
            override fun onNoUpdate() {
                activity?.runOnUiThread { Toast.makeText(requireContext(), "You are on the latest version", Toast.LENGTH_SHORT).show() }
            }
            override fun onError(error: String, url: String) {
                activity?.runOnUiThread {
                    AlertDialog.Builder(requireContext())
                        .setTitle("Update Failed")
                        .setMessage("$error\n\nChecked URL:\n$url")
                        .setPositiveButton("OK", null).show()
                }
            }
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
        val availableToAdd = allTools.filter { it.id !in currentToolIds }.toMutableList()
        if (availableToAdd.isEmpty()) {
            AlertDialog.Builder(requireContext()).setTitle("No Tools Available").setMessage("All tools are already on your home screen.").setPositiveButton("OK", null).show()
            return
        }
        val dialog = BottomSheetDialog(requireContext())
        val bottomSheetBinding = LayoutAddToolsBinding.inflate(layoutInflater)
        val sheetAdapter = ToolsAdapter(availableToAdd, { tool ->
            adapter.addTool(tool)
            saveHomeTools()
            dialog.dismiss()
        }, { _, _ -> }, { tool -> })
        bottomSheetBinding.recyclerviewAvailableTools.layoutManager = GridLayoutManager(context, 3)
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
            val idList = savedIds.split(",").filter { it.isNotEmpty() }.map { it.toInt() }
            idList.forEach { id -> allTools.find { it.id == id }?.let { currentTools.add(it) } }
        } else {
            // Default tools
            currentTools.addAll(allTools.take(4))
        }
    }

    private fun setupFooter() {
        try {
            val pInfo = requireContext().packageManager.getPackageInfo(requireContext().packageName, 0)
            binding.textviewVersion.text = "Version ${pInfo.versionName}"
        } catch (e: Exception) {
            binding.textviewVersion.text = "Version 1.0"
        }
        val year = Calendar.getInstance().get(Calendar.YEAR)
        binding.textviewCopyright.text = "© $year NorwinLabs"
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}