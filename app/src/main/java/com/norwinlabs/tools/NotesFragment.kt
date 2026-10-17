package com.norwinlabs.tools

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.norwinlabs.tools.data.db.NoteEntity
import com.norwinlabs.tools.databinding.DialogNoteBinding
import com.norwinlabs.tools.databinding.FragmentNotesBinding
import com.norwinlabs.tools.databinding.ItemNoteBinding
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.launch

@AndroidEntryPoint
class NotesFragment : Fragment() {

    private var _binding: FragmentNotesBinding? = null
    private val binding get() = _binding!!

    private val viewModel: NotesViewModel by viewModels()
    private lateinit var adapter: NotesAdapter

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentNotesBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        adapter = NotesAdapter(
            onNoteClick = { showNoteDialog(it) },
            onNoteLongClick = { showDeleteConfirm(it) },
        )
        binding.rvNotes.layoutManager = LinearLayoutManager(context)
        binding.rvNotes.adapter = adapter

        binding.fabAddNote.setOnClickListener { showNoteDialog(null) }

        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.notes.filterNotNull().collect { notes ->
                    adapter.submitList(notes)
                    binding.tvEmptyState.visibility =
                        if (notes.isEmpty()) View.VISIBLE else View.GONE
                }
            }
        }
    }

    private fun showNoteDialog(existing: NoteEntity?) {
        val form = DialogNoteBinding.inflate(layoutInflater)
        form.inputNoteTitle.setText(existing?.title ?: "")
        form.inputNoteBody.setText(existing?.body ?: "")

        MaterialAlertDialogBuilder(requireContext())
            .setTitle(if (existing == null) "New note" else "Edit note")
            .setView(form.root)
            .setPositiveButton("Save") { _, _ ->
                viewModel.save(
                    id = existing?.id,
                    title = form.inputNoteTitle.text.toString(),
                    body = form.inputNoteBody.text.toString(),
                )
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun showDeleteConfirm(note: NoteEntity) {
        MaterialAlertDialogBuilder(requireContext())
            .setTitle("Delete note")
            .setMessage("Delete \"${note.title}\"?")
            .setPositiveButton("Delete") { _, _ -> viewModel.delete(note.id) }
            .setNegativeButton("Cancel", null)
            .show()
    }

    /**
     * A ListAdapter so an edit animates just the row that changed. The previous version called
     * notifyDataSetChanged for every save and delete, which rebound the whole list.
     */
    class NotesAdapter(
        private val onNoteClick: (NoteEntity) -> Unit,
        private val onNoteLongClick: (NoteEntity) -> Unit,
    ) : ListAdapter<NoteEntity, NotesAdapter.ViewHolder>(DIFF) {

        class ViewHolder(val binding: ItemNoteBinding) : RecyclerView.ViewHolder(binding.root)

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder =
            ViewHolder(ItemNoteBinding.inflate(LayoutInflater.from(parent.context), parent, false))

        override fun onBindViewHolder(holder: ViewHolder, position: Int) {
            val note = getItem(position)
            holder.binding.tvNoteTitle.text = note.title
            holder.binding.tvNoteBody.text = note.body
            holder.binding.tvNoteBody.visibility = if (note.body.isBlank()) View.GONE else View.VISIBLE
            holder.binding.tvNoteTime.text = relativeTime(note.timestamp)
            holder.binding.cardNote.setOnClickListener { onNoteClick(note) }
            holder.binding.cardNote.setOnLongClickListener { onNoteLongClick(note); true }
        }

        private companion object {
            val DIFF = object : DiffUtil.ItemCallback<NoteEntity>() {
                override fun areItemsTheSame(old: NoteEntity, new: NoteEntity) = old.id == new.id
                override fun areContentsTheSame(old: NoteEntity, new: NoteEntity) = old == new
            }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}

/** "5 minutes ago" style stamp, shared by the Notes list and Home's recent-notes card. */
fun relativeTime(timestamp: Long): CharSequence =
    android.text.format.DateUtils.getRelativeTimeSpanString(
        timestamp,
        System.currentTimeMillis(),
        android.text.format.DateUtils.MINUTE_IN_MILLIS,
    )
