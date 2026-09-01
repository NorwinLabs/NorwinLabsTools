package com.norwinlabs.tools.search

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * Ordering is the part of search users actually feel: if "net" does not put Net Scanner first,
 * the feature is useless however fast it is.
 */
class SearchRankerTest {

    @Test
    fun `an exact title beats a prefix`() {
        assertEquals(MatchQuality.EXACT, SearchRanker.quality("notes", "Notes"))
        assertEquals(MatchQuality.TITLE_PREFIX, SearchRanker.quality("note", "Notes"))
    }

    @Test
    fun `a later word in the title still matches`() {
        assertEquals(MatchQuality.WORD_PREFIX, SearchRanker.quality("scan", "Net Scanner"))
        assertEquals(MatchQuality.TITLE_PREFIX, SearchRanker.quality("net", "Net Scanner"))
    }

    @Test
    fun `a mid-word hit ranks below a word start`() {
        assertEquals(MatchQuality.TITLE_CONTAINS, SearchRanker.quality("ann", "Net Scanner"))
    }

    @Test
    fun `body matches rank last`() {
        assertEquals(
            MatchQuality.BODY,
            SearchRanker.quality("milk", title = "Shopping", body = "Remember the milk"),
        )
    }

    @Test
    fun `matching ignores case and surrounding space`() {
        assertEquals(MatchQuality.EXACT, SearchRanker.quality("  NoTeS  ", "notes"))
    }

    @Test
    fun `no match and empty queries return null`() {
        assertNull(SearchRanker.quality("zzz", "Net Scanner", "nothing here"))
        assertNull(SearchRanker.quality("", "Net Scanner"))
        assertNull(SearchRanker.quality("   ", "Net Scanner"))
    }

    @Test
    fun `hyphenated and slashed titles match on either part`() {
        assertEquals(MatchQuality.WORD_PREFIX, SearchRanker.quality("mortgage", "Rent/Mortgage"))
        assertEquals(MatchQuality.WORD_PREFIX, SearchRanker.quality("form", "Long-Form Video"))
    }

    @Test
    fun `rank orders by quality then alphabetically`() {
        val scored = listOf(
            "Port Scanner" to MatchQuality.WORD_PREFIX,
            "Net Scanner" to MatchQuality.WORD_PREFIX,
            "Scanner" to MatchQuality.EXACT,
            "Rescanned" to MatchQuality.TITLE_CONTAINS,
        )

        assertEquals(
            listOf("Scanner", "Net Scanner", "Port Scanner", "Rescanned"),
            SearchRanker.rank(scored) { it },
        )
    }

    @Test
    fun `equally good matches keep a stable order between keystrokes`() {
        // Without the alphabetical tie-break these would come back in insertion order, so the row
        // the user is reaching for can move as they type.
        val first = SearchRanker.rank(
            listOf("Budget" to MatchQuality.TITLE_PREFIX, "Bug Report" to MatchQuality.TITLE_PREFIX),
        ) { it }
        val second = SearchRanker.rank(
            listOf("Bug Report" to MatchQuality.TITLE_PREFIX, "Budget" to MatchQuality.TITLE_PREFIX),
        ) { it }

        assertEquals(first, second)
        assertEquals(listOf("Budget", "Bug Report"), first)
    }
}
