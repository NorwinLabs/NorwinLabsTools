package com.norwinlabs.tools.data

import android.content.Context
import com.norwinlabs.tools.data.db.NoteDao
import com.norwinlabs.tools.data.db.NoteEntity
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import org.json.JSONArray
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class NotesRepository @Inject constructor(
    private val noteDao: NoteDao,
    @ApplicationContext private val context: Context,
) {

    val notes: Flow<List<NoteEntity>> = noteDao.observeAll()

    suspend fun save(id: String?, title: String, body: String) {
        noteDao.upsert(
            NoteEntity(
                id = id ?: UUID.randomUUID().toString(),
                title = title.ifBlank { "Untitled" },
                body = body,
                timestamp = System.currentTimeMillis(),
            )
        )
    }

    suspend fun delete(id: String) = noteDao.deleteById(id)

    /**
     * Carries notes over from the old JSON-in-SharedPreferences store.
     *
     * Guarded on the table being empty rather than on a "migrated" flag, and the legacy blob is
     * cleared once it has been read, so a user who later deletes every note does not have their
     * old notes reappear.
     */
    suspend fun importLegacyNotesIfNeeded() {
        if (noteDao.count() > 0) return

        val prefs = context.getSharedPreferences(LEGACY_PREFS, Context.MODE_PRIVATE)
        val json = prefs.getString(LEGACY_KEY, null) ?: return

        val imported = parseLegacyNotes(json)
        if (imported.isNotEmpty()) noteDao.insertAll(imported)
        prefs.edit().remove(LEGACY_KEY).apply()
    }

    internal companion object {
        private const val LEGACY_PREFS = "notes_prefs"
        private const val LEGACY_KEY = "notes_json"

        /**
         * Kept separate from the database work so it can be tested directly: this runs exactly
         * once per user and a mistake silently loses every note they had.
         *
         * Malformed entries are skipped rather than aborting the whole import - salvaging most of
         * someone's notes beats discarding all of them.
         */
        internal fun parseLegacyNotes(json: String): List<NoteEntity> {
            val array = runCatching { JSONArray(json) }.getOrNull() ?: return emptyList()
            return buildList {
                for (index in 0 until array.length()) {
                    val obj = array.optJSONObject(index) ?: continue
                    val title = obj.optString("title").ifBlank { "Untitled" }
                    add(
                        NoteEntity(
                            id = obj.optString("id").ifBlank { UUID.randomUUID().toString() },
                            title = title,
                            body = obj.optString("body", ""),
                            timestamp = obj.optLong("timestamp", System.currentTimeMillis()),
                        )
                    )
                }
            }
        }
    }
}
