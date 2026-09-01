package com.example.norwinlabstools.data.db

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

@Entity(tableName = "notes")
data class NoteEntity(
    @PrimaryKey val id: String,
    val title: String,
    val body: String,
    val timestamp: Long,
)

@Dao
interface NoteDao {

    /** Newest first, matching how the list has always been ordered. */
    @Query("SELECT * FROM notes ORDER BY timestamp DESC")
    fun observeAll(): Flow<List<NoteEntity>>

    @Query("SELECT COUNT(*) FROM notes")
    suspend fun count(): Int

    @Upsert
    suspend fun upsert(note: NoteEntity)

    @Insert
    suspend fun insertAll(notes: List<NoteEntity>)

    @Delete
    suspend fun delete(note: NoteEntity)

    @Query("DELETE FROM notes WHERE id = :id")
    suspend fun deleteById(id: String)
}
