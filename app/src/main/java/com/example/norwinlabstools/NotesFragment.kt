package com.example.norwinlabstools

import android.content.Context
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.LinearLayout
import androidx.appcompat.app.AlertDialog
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.norwinlabstools.databinding.FragmentNotesBinding
import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID

class NotesFragment : Fragment() {

    private var _binding: FragmentNotesBinding? = null
    private val binding get() = _binding!!

    private val prefsName = "notes_prefs"
    private val keyNotes = "notes_json"

    data class Note(val id: String, val title: String, val body: String, val timestamp: Long)

    private val notes = mutableListOf<Note>()
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

        adapter = NotesAdapter(notes, onNoteClick = { showNoteDialog(it) }, onNoteLongClick = { showDeleteConfirm(it) })
        binding.rvNotes.layoutManager = LinearLayoutManager(context)
        binding.rvNotes.adapter = adapter

        binding.fabAddNote.setOnClickListener { showNoteDialog(null) }

        loadNotes()
    }

    private fun loadNotes() {
        val prefs = requireContext().getSharedPreferences(prefsName, Context.MODE_PRIVATE)
        val json = prefs.getString(keyNotes, null)
        notes.clear()
        if (json != null) {
            val array = JSONArray(json)
            for (i in 0 until array.length()) {
                val obj = array.getJSONObject(i)
                notes.add(Note(
                    id = obj.getString("id"),
                    title = obj.getString("title"),
                    body = obj.getString("body"),
                    timestamp = obj.getLong("timestamp")
                ))
            }
        }
        notes.sortByDescending { it.timestamp }
        adapter.notifyDataSetChanged()
        updateEmptyState()
    }

    private fun saveNotes() {
        val prefs = requireContext().getSharedPreferences(prefsName, Context.MODE_PRIVATE)
        val array = JSONArray()
        notes.forEach { note ->
            val obj = JSONObject()
            obj.put("id", note.id)
            obj.put("title", note.title)
            obj.put("body", note.body)
            obj.put("timestamp", note.timestamp)
            array.put(obj)
        }
        prefs.edit().putString(keyNotes, array.toString()).apply()
    }

    private fun updateEmptyState() {
        binding.tvEmptyState.visibility = if (notes.isEmpty()) View.VISIBLE else View.GONE
    }

    private fun showNoteDialog(existing: Note?) {
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
                val title = titleInput.text.toString().ifBlank { "Untitled" }
                val body = bodyInput.text.toString()
                if (existing == null) {
                    notes.add(0, Note(UUID.randomUUID().toString(), title, body, System.currentTimeMillis()))
                } else {
                    val index = notes.indexOfFirst { it.id == existing.id }
                    if (index >= 0) notes[index] = existing.copy(title = title, body = body, timestamp = System.currentTimeMillis())
                }
                notes.sortByDescending { it.timestamp }
                adapter.notifyDataSetChanged()
                updateEmptyState()
                saveNotes()
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun showDeleteConfirm(note: Note) {
        AlertDialog.Builder(requireContext())
            .setTitle("Delete Note")
            .setMessage("Delete \"${note.title}\"?")
            .setPositiveButton("Delete") { _, _ ->
                notes.removeAll { it.id == note.id }
                adapter.notifyDataSetChanged()
                updateEmptyState()
                saveNotes()
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    class NotesAdapter(
        private val items: List<Note>,
        private val onNoteClick: (Note) -> Unit,
        private val onNoteLongClick: (Note) -> Unit
    ) : RecyclerView.Adapter<NotesAdapter.ViewHolder>() {

        class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
            val tvTitle: android.widget.TextView = view.findViewById(android.R.id.text1)
            val tvBody: android.widget.TextView = view.findViewById(android.R.id.text2)
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
            val view = LayoutInflater.from(parent.context)
                .inflate(android.R.layout.simple_list_item_2, parent, false)
            return ViewHolder(view)
        }

        override fun onBindViewHolder(holder: ViewHolder, position: Int) {
            val note = items[position]
            holder.tvTitle.text = note.title
            holder.tvBody.text = if (note.body.length > 80) "${note.body.take(80)}…" else note.body
            holder.itemView.setOnClickListener { onNoteClick(note) }
            holder.itemView.setOnLongClickListener { onNoteLongClick(note); true }
        }

        override fun getItemCount() = items.size
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
