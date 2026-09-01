package com.norwinlabs.tools

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.norwinlabs.tools.search.SearchRepository
import com.norwinlabs.tools.search.SearchResult
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject

data class SearchUiState(
    val query: String = "",
    val results: List<SearchResult> = emptyList(),
)

@OptIn(FlowPreview::class, ExperimentalCoroutinesApi::class)
@HiltViewModel
class SearchViewModel @Inject constructor(
    private val searchRepository: SearchRepository,
) : ViewModel() {

    private val query = MutableStateFlow("")

    val uiState: StateFlow<SearchUiState> = query
        // Typing fast should not run a query per keystroke; flatMapLatest then drops any
        // in-flight search as soon as the text changes again, so results can never arrive out
        // of order and show matches for a query the user has already moved past.
        .debounce(QUERY_DEBOUNCE_MS)
        .flatMapLatest { text ->
            searchRepository.search(text).map { SearchUiState(text, it) }
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS),
            initialValue = SearchUiState(),
        )

    fun onQueryChanged(text: String) {
        query.value = text
    }

    private companion object {
        const val QUERY_DEBOUNCE_MS = 120L
        const val STOP_TIMEOUT_MS = 5_000L
    }
}
