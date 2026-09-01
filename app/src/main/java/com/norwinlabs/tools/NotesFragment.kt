package com.norwinlabs.tools

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
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
import com.norwinlabs.tools.databinding.FragmentNotesBinding
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
        val layout = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(50, 20, 50, 0)
        }

        val titleInput = EditText(context).apply {
            hint = "Title"
            setText(existing?.title ?: "")
        }
        val bodyInput = EditText(context).apply {
            hint = "Note"
            setText(existing?.body ?: "")
            minLines = 4
        }
        layout.addView(titleInput)
        layout.addView(bodyInput)

        AlertDialog.Builder(requireContext())
            .setTitle(if (existing == null) "New Note" else "Edit Note")
            .setView(layout)
            .setPositiveButton("Save") { _, _ ->
                viewModel.save(
                    id = existing?.id,
                    title = titleInput.text.toString(),
                    body = bodyInput.text.toString(),
                )
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun showDeleteConfirm(note: NoteEntity) {
        AlertDialog.Builder(requireContext())
            .setTitle("Delete Note")
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

        class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
            val tvTitle: TextView = view.findViewById(android.R.id.text1)
            val tvBody: TextView = view.findViewById(android.R.id.text2)
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
            val view = LayoutInflater.from(parent.context)
                .inflate(android.R.layout.simple_list_item_2, parent, false)
            return ViewHolder(view)
        }

        override fun onBindViewHolder(holder: ViewHolder, position: Int) {
            val note = getItem(position)
            holder.tvTitle.text = note.title
            holder.tvBody.text =
                if (note.body.length > 80) "${note.body.take(80)}…" else note.body
            holder.itemView.setOnClickListener { onNoteClick(note) }
            holder.itemView.setOnLongClickListener { onNoteLongClick(note); true }
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
