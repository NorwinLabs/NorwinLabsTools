package com.norwinlabs.tools

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.norwinlabs.tools.data.NotesRepository
import com.norwinlabs.tools.data.db.NoteEntity
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class NotesViewModel @Inject constructor(
    private val repository: NotesRepository,
) : ViewModel() {

    /** Null while loading, so the empty-state text doesn't flash before the first read. */
    val notes: StateFlow<List<NoteEntity>?> = repository.notes
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS),
            initialValue = null,
        )

    init {
        viewModelScope.launch { repository.importLegacyNotesIfNeeded() }
    }

    fun save(id: String?, title: String, body: String) {
        viewModelScope.launch { repository.save(id, title, body) }
    }

    fun delete(id: String) {
        viewModelScope.launch { repository.delete(id) }
    }

    private companion object {
        const val STOP_TIMEOUT_MS = 5_000L
    }
}
