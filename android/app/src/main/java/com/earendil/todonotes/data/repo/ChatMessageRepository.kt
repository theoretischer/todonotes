package com.earendil.todonotes.data.repo

import android.content.Context
import com.earendil.todonotes.data.TodoNotesDatabase
import com.earendil.todonotes.data.entity.ChatMessage
import kotlinx.coroutines.flow.Flow
import java.util.UUID

/**
 * Repository für Chat-Nachrichten (Block H3).
 *
 * Jede Nachricht gehört zu einer Notiz mit `type = CHAT`. Nachrichten
 * werden chronologisch geordnet via [position] (älteste = kleinste).
 * Beim Senden wird eine neue UUID generiert + position = max+1.
 *
 * Bearbeiten (H5) ändert nur `text` + `updatedAt` — `createdAt` bleibt
 * stabil, damit die Uhrzeit-Anzeige nicht springt.
 */
class ChatMessageRepository(context: Context) {

    private val chatDao = TodoNotesDatabase.get(context).chatMessageDao()
    private val noteDao = TodoNotesDatabase.get(context).noteDao()

    /** Alle nicht-gelöschten Nachrichten einer Chat-Notiz (älteste zuerst). */
    fun observeMessages(noteId: String): Flow<List<ChatMessage>> =
        chatDao.observeMessages(noteId)

    /** Neue Nachricht ans Ende anhängen. */
    suspend fun sendMessage(noteId: String, text: String) {
        val trimmed = text.trim()
        if (trimmed.isEmpty()) return
        val now = System.currentTimeMillis()
        val messages = chatDao.getMessages(noteId)
        val maxPos = messages.maxOfOrNull { it.position } ?: 0L
        val message = ChatMessage(
            id = UUID.randomUUID().toString(),
            noteId = noteId,
            text = trimmed,
            createdAt = now,
            updatedAt = now,
            position = maxPos + 1
        )
        chatDao.insert(message)
        // Notiz-updatedAt anheben, damit Sync merkt, dass sich etwas geändert hat.
        val note = noteDao.getById(noteId) ?: return
        noteDao.update(note.copy(updatedAt = now))
    }

    /** Nachricht bearbeiten (nur text + updatedAt, createdAt bleibt). */
    suspend fun editMessage(messageId: String, newText: String) {
        val trimmed = newText.trim()
        if (trimmed.isEmpty()) return
        val msg = chatDao.getById(messageId) ?: return
        chatDao.update(msg.copy(text = trimmed, updatedAt = System.currentTimeMillis()))
    }

    /** Soft-Delete einer Nachricht. */
    suspend fun deleteMessage(messageId: String) {
        chatDao.softDelete(messageId, System.currentTimeMillis())
    }

    // ----- Sync -----

    suspend fun getAllForSync(): List<ChatMessage> = chatDao.getAllForSync()

    suspend fun upsertAll(messages: List<ChatMessage>) = chatDao.upsertAll(messages)
}
