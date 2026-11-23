package com.earendil.todonotes.data.dao

import androidx.room3.Dao
import androidx.room3.Insert
import androidx.room3.OnConflictStrategy
import androidx.room3.Query
import com.earendil.todonotes.data.entity.LinkPreview

/**
 * Dao für den Link-Vorschau-Cache (F7). Nur lokal — kein Sync.
 */
@Dao
interface LinkPreviewDao {

    @Query("SELECT * FROM link_previews WHERE url = :url")
    suspend fun get(url: String): LinkPreview?

    /** Bulk-Lookup für Listen (Chat/Todos): alle vorhandenen Vorschauen
     *  zu den angegebenen URLs in EINER Query. */
    @Query("SELECT * FROM link_previews WHERE url IN (:urls)")
    suspend fun getMany(urls: List<String>): List<LinkPreview>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(preview: LinkPreview)

    @Query("DELETE FROM link_previews")
    suspend fun clearAll()
}
