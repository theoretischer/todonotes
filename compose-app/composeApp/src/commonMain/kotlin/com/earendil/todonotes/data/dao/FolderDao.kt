package com.earendil.todonotes.data.dao

import androidx.room3.Dao
import androidx.room3.Insert
import androidx.room3.OnConflictStrategy
import androidx.room3.Query
import androidx.room3.Update
import androidx.room3.Upsert
import com.earendil.todonotes.data.entity.Folder
import kotlinx.coroutines.flow.Flow

@Dao
interface FolderDao {

    // ----- UI-Queries -----

    /** Alle Ordner im angegebenen Eltern-Ordner (null = Wurzel). Soft-deletes ausgeblendet. */
    @Query("SELECT * FROM folders WHERE parentId IS :parentId AND deletedAt IS NULL ORDER BY position ASC")
    fun observeFoldersIn(parentId: String?): Flow<List<Folder>>

    /** Nicht-gelöschte Ordner eines Eltern-Ordners, einmalig (für Reorder/Positions-Neuvergabe). */
    @Query("SELECT * FROM folders WHERE parentId IS :parentId AND deletedAt IS NULL ORDER BY position ASC")
    suspend fun getInFolder(parentId: String?): List<Folder>

    /** Position eines Eintrags setzen (für das Reorder per Swaps). */
    @Query("UPDATE folders SET position = :position, updatedAt = :now WHERE id = :id")
    suspend fun setPosition(id: String, position: Long, now: Long)

    @Query("SELECT * FROM folders WHERE id = :id")
    suspend fun getById(id: String): Folder?

    /** Alle nicht-geloeschten Ordner (flach) — fuer den Verschieben-Picker (F6).
     *  Der Aufrufer filtert den aktuellen Ordner + seine Nachfahren heraus. */
    @Query("SELECT * FROM folders WHERE deletedAt IS NULL ORDER BY position ASC")
    suspend fun getAllOnce(): List<Folder>

    /** Wurzel-Ordner als Flow (fuer Drag auf Wurzel). */
    @Query("SELECT * FROM folders WHERE parentId IS NULL AND deletedAt IS NULL ORDER BY position ASC")
    fun observeRoots(): Flow<List<Folder>>

    /** Prüft, ob `candidate` ein direkter/indirekter Nachfahre von `folderId` ist
     *  (für Zyklen-Erkennung beim Verschieben, F6). Liefert >0, falls
     *  `folderId` ein Vorfahre von `candidate` ist — dann darf `candidate`
     *  nicht nach `folderId` verschoben werden. */
    @Query(
        """WITH RECURSIVE ancestors(id, parentId) AS (
            SELECT id, parentId FROM folders WHERE id = :candidate
            UNION ALL
            SELECT f.id, f.parentId FROM folders f
            JOIN ancestors a ON f.id = a.parentId
        )
        SELECT COUNT(*) FROM ancestors WHERE id = :folderId"""
    )
    suspend fun isDescendantOf(folderId: String, candidate: String): Int

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(folder: Folder)

    @Update
    suspend fun update(folder: Folder)

    @Query("UPDATE folders SET deletedAt = :now, updatedAt = :now WHERE id = :id")
    suspend fun softDelete(id: String, now: Long)

    // ----- Sync-Queries -----

    @Query("SELECT * FROM folders")
    suspend fun getAllForSync(): List<Folder>

    /** Nur Folder, die seit :since geändert wurden (Sync-A+: effizienter Push). */
    @Query("SELECT * FROM folders WHERE updatedAt > :since")
    suspend fun getSince(since: Long): List<Folder>

    @Upsert
    suspend fun upsertAll(folders: List<Folder>)

    /** Alle Zeilen löschen (lokaler Wipe nach Server-Wipe). */
    @Query("DELETE FROM folders")
    suspend fun clearAll()
}
