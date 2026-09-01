package com.example.norwinlabstools.data.db

import androidx.room.Database
import androidx.room.RoomDatabase

/**
 * Local store for user-created content.
 *
 * Notes and Budget were both serialised to JSON inside SharedPreferences, which meant the whole
 * collection was rewritten on every edit and parsed on the main thread on every open. Budget
 * follows Notes into here; until then it stays on its own legacy store.
 */
@Database(
    entities = [NoteEntity::class],
    version = 1,
    exportSchema = true,
)
abstract class NorwinDatabase : RoomDatabase() {
    abstract fun noteDao(): NoteDao

    companion object {
        const val NAME = "norwin.db"
    }
}
