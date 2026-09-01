package com.norwinlabs.tools.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * This import runs exactly once per user, against data written by a shipped version of the app.
 * If it drops or mangles an entry there is no second chance, so the awkward inputs are covered
 * deliberately.
 */
class NotesLegacyImportTest {

    @Test
    fun `reads a well formed note`() {
        val notes = NotesRepository.parseLegacyNotes(
            """[{"id":"abc","title":"Shopping","body":"Milk","timestamp":1700000000000}]"""
        )

        assertEquals(1, notes.size)
        assertEquals("abc", notes[0].id)
        assertEquals("Shopping", notes[0].title)
        assertEquals("Milk", notes[0].body)
        assertEquals(1700000000000L, notes[0].timestamp)
    }

    @Test
    fun `keeps the good entries when one is malformed`() {
        val notes = NotesRepository.parseLegacyNotes(
            """[{"id":"a","title":"Keep","body":"x","timestamp":1},"not-an-object",{"id":"b","title":"Also keep","body":"y","timestamp":2}]"""
        )

        assertEquals(listOf("Keep", "Also keep"), notes.map { it.title })
    }

    @Test
    fun `substitutes a title for an untitled note`() {
        val notes = NotesRepository.parseLegacyNotes("""[{"id":"a","title":"","body":"x","timestamp":1}]""")
        assertEquals("Untitled", notes[0].title)
    }

    @Test
    fun `generates an id when one is missing`() {
        val notes = NotesRepository.parseLegacyNotes("""[{"title":"No id","body":"x","timestamp":1}]""")

        assertEquals(1, notes.size)
        assertTrue("expected a generated id", notes[0].id.isNotBlank())
    }

    @Test
    fun `gives two id-less notes distinct ids`() {
        val notes = NotesRepository.parseLegacyNotes(
            """[{"title":"One","body":"","timestamp":1},{"title":"Two","body":"","timestamp":2}]"""
        )

        assertNotEquals(notes[0].id, notes[1].id)
    }

    @Test
    fun `tolerates missing body and timestamp`() {
        val notes = NotesRepository.parseLegacyNotes("""[{"id":"a","title":"Sparse"}]""")

        assertEquals(1, notes.size)
        assertEquals("", notes[0].body)
        assertTrue(notes[0].timestamp > 0)
    }

    @Test
    fun `returns nothing for unparseable or empty input`() {
        assertTrue(NotesRepository.parseLegacyNotes("").isEmpty())
        assertTrue(NotesRepository.parseLegacyNotes("{ not json").isEmpty())
        assertTrue(NotesRepository.parseLegacyNotes("[]").isEmpty())
    }
}
