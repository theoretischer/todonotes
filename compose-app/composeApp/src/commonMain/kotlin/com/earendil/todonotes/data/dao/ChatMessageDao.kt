package com.earendil.todonotes.data.dao

import androidx.room3.Dao
import androidx.room3.Insert
import androidx.room3.OnConflictStrategy
import androidx.room3.Query
import androidx.room3.Update
import androidx.room3.Upsert
import com.earendil.todonotes.data.entity.ChatMessage
import kotlinx.coroutines.flow.Flow

@Dao
interface ChatMessageDao {

    /** Alle Nachrichten einer Chat-Notiz, nicht gelöscht, älteste zuerst. */
    @Query("SELECT * FROM chat_messages WHERE noteId = :noteId AND deletedAt IS NULL ORDER BY position ASC")
    fun observeMessages(noteId: String): Flow<List<ChatMessage>>

    /** Einmalig alle (für Reorder/Positions-Neuvergabe). */
    @Query("SELECT * FROM chat_messages WHERE noteId = :noteId AND deletedAt IS NULL ORDER BY position ASC")
    suspend fun getMessages(noteId: String): List<ChatMessage>

    @Query("SELECT * FROM chat_messages WHERE id = :id")
    suspend fun getById(id: String): ChatMessage?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(message: ChatMessage)

    @Update
    suspend fun update(message: ChatMessage)

    /** Soft-Delete (sync-fähig). */
    @Query("UPDATE chat_messages SET deletedAt = :now, updatedAt = :now WHERE id = :id")
    suspend fun softDelete(id: String, now: Long)

    // ----- Sync (alle inkl. gelöschte) -----

    @Query("SELECT * FROM chat_messages")
    suspend fun getAllForSync(): List<ChatMessage>

    @Upsert
    suspend fun upsertAll(messages: List<ChatMessage>)

    /** Alle Zeilen löschen (lokaler Wipe nach Server-Wipe). */
    @Query("DELETE FROM chat_messages")
    suspend fun clearAll()
}
