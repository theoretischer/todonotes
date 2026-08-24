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

    /** Reaktive Beobachtung einer Notiz (für Editor — feuert bei Sync-UPDATE). */
    @Query("SELECT * FROM notes WHERE id = :id")
    fun observeNote(id: String): Flow<Note?>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(note: Note)

    @Update
    suspend fun update(note: Note)

    /** Soft-Delete (für Sync wichtig: deletedAt muss erhalten bleiben). */
    @Query("UPDATE notes SET deletedAt = :now, updatedAt = :now WHERE id = :id")
    suspend fun softDelete(id: String, now: Long)

    /** Sicheres Update: nur Titel/Body/updatedAt, bewahrt deletedAt/folderId
     *  etc. Verhindert dass ein flush eine gelöschte Notiz wiederherstellt. */
    @Query("UPDATE notes SET title = :title, bodyJson = :bodyJson, updatedAt = :now WHERE id = :id AND deletedAt IS NULL")
    suspend fun updateBody(id: String, title: String, bodyJson: String, now: Long)

    // ----- Sync-Queries (alle, inkl. gelöschte) -----

    @Query("SELECT * FROM notes")
    suspend fun getAllForSync(): List<Note>

    /** Nur Notizen, die seit :since geändert wurden (Sync-A+: effizienter Push). */
    @Query("SELECT * FROM notes WHERE updatedAt > :since")
    suspend fun getSince(since: Long): List<Note>

    /** Server-Änderungen einspielen (@Insert REPLACE — atomar, kein Transaktionsleck
     *  wie bei @Upsert auf Wasm). Kein FK/CASCADE → REPLACE sicher. */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(notes: List<Note>)

    /** Alle Zeilen löschen (lokaler Wipe nach Server-Wipe). */
    @Query("DELETE FROM notes")
    suspend fun clearAll()
}
