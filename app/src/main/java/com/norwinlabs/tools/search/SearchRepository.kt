package com.norwinlabs.tools.search

import com.norwinlabs.tools.ToolRegistry
import com.norwinlabs.tools.data.NotesRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Search across tools and the content inside them.
 *
 * The toolbar search only ever filtered the Home grid by tool name, so a user who remembered
 * writing something down had no way to find it without opening Notes and scrolling. Results are
 * merged from every source and ranked together, so the best match wins regardless of which
 * source it came from.
 */
@Singleton
class SearchRepository @Inject constructor(
    private val notesRepository: NotesRepository,
) {

    fun search(query: String): Flow<List<SearchResult>> {
        if (query.isBlank()) return flowOf(emptyList())

        return notesRepository.notes.map { notes ->
            val scored = buildList {
                ToolRegistry.all.forEach { tool ->
                    SearchRanker.quality(query, tool.name, tool.category)?.let { quality ->
                        add(SearchResult.ToolResult(tool) to quality)
                    }
                }

                notes.forEach { note ->
                    SearchRanker.quality(query, note.title, note.body)?.let { quality ->
                        add(
                            SearchResult.NoteResult(
                                noteId = note.id,
                                title = note.title,
                                subtitle = note.body.snippet(),
                            ) to quality
                        )
                    }
                }
            }

            SearchRanker.rank(scored) { it.title }
        }
    }

    private fun String.snippet(): String =
        replace('\n', ' ').trim().let { if (it.length > SNIPPET_LENGTH) it.take(SNIPPET_LENGTH) + "…" else it }

    private companion object {
        const val SNIPPET_LENGTH = 80
    }
}
