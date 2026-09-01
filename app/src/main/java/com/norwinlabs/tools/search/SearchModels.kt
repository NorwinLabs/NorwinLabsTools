package com.norwinlabs.tools.search

import com.norwinlabs.tools.Tool

/** Something the user can jump to from search. */
sealed interface SearchResult {

    val title: String
    val subtitle: String

    data class ToolResult(val tool: Tool) : SearchResult {
        override val title get() = tool.name
        override val subtitle get() = tool.category
    }

    data class NoteResult(
        val noteId: String,
        override val title: String,
        override val subtitle: String,
    ) : SearchResult
}

/**
 * How well a result matches, best first.
 *
 * Ranking exists because searching "no" should offer the Notes tool before a note whose body
 * happens to contain the word "another". Without it, results are ordered by whichever source
 * was queried first, which reads as random.
 */
enum class MatchQuality {
    /** The whole title is the query. */
    EXACT,

    /** The title starts with the query - "net" for "Net Scanner". */
    TITLE_PREFIX,

    /** A later word in the title starts with the query - "scan" for "Net Scanner". */
    WORD_PREFIX,

    /** The query appears somewhere in the title. */
    TITLE_CONTAINS,

    /** Only the subtitle or body matched. */
    BODY,
}
