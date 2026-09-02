package com.norwinlabs.tools.search

/**
 * Scores and orders search results.
 *
 * Kept as pure functions with no Android or data-layer dependencies so the ordering rules - the
 * part users actually feel and the part easiest to get subtly wrong - can be tested directly.
 */
object SearchRanker {

    /**
     * Returns null when [query] does not match at all, so callers can filter and rank in one pass.
     * Matching is case-insensitive and ignores surrounding whitespace.
     */
    fun quality(query: String, title: String, body: String = ""): MatchQuality? {
        val needle = query.trim().lowercase()
        if (needle.isEmpty()) return null

        val haystack = title.lowercase()

        return when {
            haystack == needle -> MatchQuality.EXACT
            haystack.startsWith(needle) -> MatchQuality.TITLE_PREFIX
            haystack.hasWordStartingWith(needle) -> MatchQuality.WORD_PREFIX
            haystack.contains(needle) -> MatchQuality.TITLE_CONTAINS
            body.lowercase().contains(needle) -> MatchQuality.BODY
            else -> null
        }
    }

    /**
     * Orders by match quality, then by title. The alphabetical tie-break keeps results stable
     * between keystrokes; without it, equally-good matches reshuffle as the user types, and the
     * row they were reaching for moves.
     */
    fun <T> rank(scored: List<Pair<T, MatchQuality>>, titleOf: (T) -> String): List<T> =
        scored
            .sortedWith(
                compareBy<Pair<T, MatchQuality>> { it.second.ordinal }
                    .thenBy { titleOf(it.first).lowercase() }
            )
            .map { it.first }

    private fun String.hasWordStartingWith(needle: String): Boolean =
        split(' ', '-', '_', '/').any { it.startsWith(needle) }
}
