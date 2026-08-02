package com.earendil.todonotes.data.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.earendil.todonotes.data.entity.Note
import kotlinx.coroutines.flow.Flow

@Dao
interface NoteDao {

    // ----- UI-Queries (nicht gelöschte) -----

    /** Alle Notizen im angegebenen Ordner (null = Wurzel). Soft-deletes ausgeblendet. */
    @Query("SELECT * FROM notes WHERE folderId IS :folderId AND deletedAt IS NULL ORDER BY updatedAt DESC")
    fun observeNotesInFolder(folderId: String?): Flow<List<Note>>

    @Query("SELECT * FROM notes WHERE id = :id")
    suspend fun getById(id: String): Note?

    @Query("SELECT * FROM notes WHERE id = :id AND deletedAt IS NULL")
    suspend fun getLiveById(id: String): Note?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(note: Note)

    @Update
    suspend fun update(note: Note)

    /** Soft-Delete (für Sync wichtig: deletedAt muss erhalten bleiben). */
    @Query("UPDATE notes SET deletedAt = :now, updatedAt = :now WHERE id = :id")
    suspend fun softDelete(id: String, now: Long)

    // ----- Sync-Queries (alle, inkl. gelöschte) -----

    @Query("SELECT * FROM notes")
    suspend fun getAllForSync(): List<Note>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(notes: List<Note>)
}
