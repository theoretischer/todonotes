package com.earendil.todonotes.data.dao

import androidx.room3.Dao
import androidx.room3.Insert
import androidx.room3.OnConflictStrategy
import androidx.room3.Query
import androidx.room3.Update
import androidx.room3.Upsert
import com.earendil.todonotes.data.entity.Note
import kotlinx.coroutines.flow.Flow

@Dao
interface NoteDao {

    // ----- UI-Queries (nicht gelöschte) -----

    /** Alle Notizen im angegebenen Ordner (null = Wurzel). Soft-deletes ausgeblendet. */
    @Query("SELECT * FROM notes WHERE folderId IS :folderId AND deletedAt IS NULL ORDER BY position ASC")
    fun observeNotesInFolder(folderId: String?): Flow<List<Note>>

    /** Nicht-gelöschte Notizen eines Ordners, einmalig (für Reorder/Positions-Neuvergabe). */
    @Query("SELECT * FROM notes WHERE folderId IS :folderId AND deletedAt IS NULL ORDER BY position ASC")
    suspend fun getInFolder(folderId: String?): List<Note>

    /** Position eines Eintrags setzen (für das Reorder per Swaps). */
    @Query("UPDATE notes SET position = :position, updatedAt = :now WHERE id = :id")
    suspend fun setPosition(id: String, position: Long, now: Long)

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

    @Upsert
    suspend fun upsertAll(notes: List<Note>)

    /** Alle Zeilen löschen (lokaler Wipe nach Server-Wipe). */
    @Query("DELETE FROM notes")
    suspend fun clearAll()
}
