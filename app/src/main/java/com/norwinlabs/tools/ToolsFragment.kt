package com.norwinlabs.tools

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.GridLayoutManager
import com.norwinlabs.tools.data.SettingsRepository
import com.norwinlabs.tools.databinding.FragmentToolsBinding
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

/**
 * Browse every tool, grouped by category.
 *
 * Discovering a tool previously meant knowing to long-press or tap the FAB on Home to open the
 * "Add Tool" sheet - the full catalogue had no screen of its own. This is that screen, and it is
 * a top-level destination rather than something reached through Home.
 */
@AndroidEntryPoint
class ToolsFragment : Fragment() {

    private var _binding: FragmentToolsBinding? = null
    private val binding get() = _binding!!

    @Inject lateinit var settingsRepository: SettingsRepository

    private lateinit var launcher: ToolLauncher
    private var query: String = ""

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentToolsBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        launcher = ToolLauncher(this, settingsRepository)
        showTools(query)
    }

    /** Called by MainActivity as the toolbar search text changes. */
    fun filterTools(query: String) {
        this.query = query
        if (_binding != null) showTools(query)
    }

    private fun showTools(query: String) {
        val matches = if (query.isBlank()) {
            ToolRegistry.all
        } else {
            ToolRegistry.all.filter {
                it.name.contains(query, ignoreCase = true) ||
                    it.category.contains(query, ignoreCase = true)
            }
        }

        binding.tvNoTools.visibility = if (matches.isEmpty()) View.VISIBLE else View.GONE

        // Rebuilt rather than filtered in place: the adapter groups into category sections at
        // construction, and for a couple of dozen tools rebuilding is cheaper than keeping a
        // second mutable representation of the section list in sync.
        val adapter = CategorizedToolsAdapter(matches, ToolRegistry.categoryOrder) { launcher.open(it) }
        val spanCount = resources.getInteger(R.integer.tools_span_count)

        binding.rvTools.layoutManager = GridLayoutManager(context, spanCount).apply {
            spanSizeLookup = adapter.spanSizeLookup(spanCount)
        }
        binding.rvTools.adapter = adapter
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
