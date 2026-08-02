package com.earendil.todonotes.data.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.earendil.todonotes.data.entity.Folder
import kotlinx.coroutines.flow.Flow

@Dao
interface FolderDao {

    // ----- UI-Queries -----

    /** Alle Ordner im angegebenen Eltern-Ordner (null = Wurzel). Soft-deletes ausgeblendet. */
    @Query("SELECT * FROM folders WHERE parentId IS :parentId AND deletedAt IS NULL ORDER BY name COLLATE NOCASE ASC")
    fun observeFoldersIn(parentId: String?): Flow<List<Folder>>

    @Query("SELECT * FROM folders WHERE id = :id")
    suspend fun getById(id: String): Folder?

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

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(folders: List<Folder>)
}
