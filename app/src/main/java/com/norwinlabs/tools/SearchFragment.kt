package com.norwinlabs.tools

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.InputMethodManager
import androidx.core.content.getSystemService
import androidx.core.widget.doAfterTextChanged
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.norwinlabs.tools.data.SettingsRepository
import com.norwinlabs.tools.databinding.FragmentSearchBinding
import com.norwinlabs.tools.databinding.ItemSearchResultBinding
import com.norwinlabs.tools.search.SearchResult
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * One place to search everything.
 *
 * The toolbar previously held an inline SearchView that filtered whichever grid was on screen by
 * tool name. That could never find anything inside a tool, so a note you knew you had written was
 * unreachable without opening Notes and scrolling.
 */
@AndroidEntryPoint
class SearchFragment : Fragment() {

    private var _binding: FragmentSearchBinding? = null
    private val binding get() = _binding!!

    @Inject lateinit var settingsRepository: SettingsRepository

    private val viewModel: SearchViewModel by viewModels()
    private lateinit var launcher: ToolLauncher

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentSearchBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        launcher = ToolLauncher(this, settingsRepository)

        val adapter = ResultsAdapter(::onResultClicked)
        binding.rvResults.adapter = adapter

        binding.etQuery.doAfterTextChanged { viewModel.onQueryChanged(it?.toString().orEmpty()) }

        // Search is a destination the user chose to open, so put the caret and the keyboard where
        // they are going rather than making them tap the field as well.
        binding.etQuery.requestFocus()
        context?.getSystemService<InputMethodManager>()
            ?.showSoftInput(binding.etQuery, InputMethodManager.SHOW_IMPLICIT)

        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.uiState.collect { state ->
                    adapter.submitList(state.results)

                    // "No matches" only makes sense once something has been typed; before that
                    // the empty list is just the starting state.
                    val showEmpty = state.query.isNotBlank() && state.results.isEmpty()
                    binding.tvSearchEmpty.visibility = if (showEmpty) View.VISIBLE else View.GONE
                    binding.rvResults.visibility = if (showEmpty) View.GONE else View.VISIBLE
                }
            }
        }
    }

    private fun onResultClicked(result: SearchResult) {
        hideKeyboard()
        when (result) {
            is SearchResult.ToolResult -> launcher.open(result.tool)
            // Notes has no per-note destination yet, so this lands on the list rather than the
            // note itself.
            is SearchResult.NoteResult -> findNavController().navigate(R.id.NotesFragment)
        }
    }

    private fun hideKeyboard() {
        context?.getSystemService<InputMethodManager>()
            ?.hideSoftInputFromWindow(binding.etQuery.windowToken, 0)
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    private class ResultsAdapter(
        private val onClick: (SearchResult) -> Unit,
    ) : ListAdapter<SearchResult, ResultsAdapter.ViewHolder>(DIFF) {

        class ViewHolder(val binding: ItemSearchResultBinding) :
            RecyclerView.ViewHolder(binding.root)

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder =
            ViewHolder(
                ItemSearchResultBinding.inflate(
                    LayoutInflater.from(parent.context), parent, false
                )
            )

        override fun onBindViewHolder(holder: ViewHolder, position: Int) {
            val result = getItem(position)
            holder.binding.tvResultTitle.text = result.title
            holder.binding.tvResultSubtitle.text = result.subtitle
            holder.binding.ivResultIcon.setImageResource(
                when (result) {
                    is SearchResult.ToolResult -> result.tool.iconRes
                    is SearchResult.NoteResult -> android.R.drawable.ic_menu_edit
                }
            )
            holder.itemView.setOnClickListener { onClick(result) }
        }

        private companion object {
            val DIFF = object : DiffUtil.ItemCallback<SearchResult>() {
                override fun areItemsTheSame(old: SearchResult, new: SearchResult): Boolean =
                    when {
                        old is SearchResult.ToolResult && new is SearchResult.ToolResult ->
                            old.tool.id == new.tool.id
                        old is SearchResult.NoteResult && new is SearchResult.NoteResult ->
                            old.noteId == new.noteId
                        else -> false
                    }

                override fun areContentsTheSame(old: SearchResult, new: SearchResult) = old == new
            }
        }
    }
}
