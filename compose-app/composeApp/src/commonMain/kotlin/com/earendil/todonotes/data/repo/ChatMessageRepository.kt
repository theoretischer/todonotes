package com.earendil.todonotes.data.repo

import com.earendil.todonotes.data.TodoNotesDatabase
import com.earendil.todonotes.data.entity.ChatMessage
import kotlinx.coroutines.flow.Flow

/**
 * Repository für Chat-Nachrichten (M7a — commonMain).
 *
 * Jede Nachricht gehört zu einer Notiz mit `type = CHAT`. Nachrichten
 * werden chronologisch geordnet via [position] (älteste = kleinste).
 * Beim Senden wird eine neue UUID generiert + position = max+1.
 */
class ChatMessageRepository(private val db: TodoNotesDatabase) {

    private val chatDao = db.chatMessageDao()
    private val noteDao = db.noteDao()

    /** Alle nicht-gelöschten Nachrichten einer Chat-Notiz (älteste zuerst). */
    fun observeMessages(noteId: String): Flow<List<ChatMessage>> =
        chatDao.observeMessages(noteId)

    /** Neue Nachricht ans Ende anhängen. quotedMessageId optional für Zitate. */
    suspend fun sendMessage(noteId: String, text: String, quotedMessageId: String? = null) {
        val trimmed = text.trim()
        if (trimmed.isEmpty()) return
        val now = nowMs()
        val messages = chatDao.getMessages(noteId)
        val maxPos = messages.maxOfOrNull { it.position } ?: 0L
        val message = ChatMessage(
            id = randomUuidString(),
            noteId = noteId,
            text = trimmed,
            createdAt = now,
            updatedAt = now,
            position = maxPos + 1,
            quotedMessageId = quotedMessageId
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
        chatDao.update(msg.copy(text = trimmed, updatedAt = nowMs()))
    }

    /** Soft-Delete einer Nachricht. */
    suspend fun deleteMessage(messageId: String) {
        chatDao.softDelete(messageId, nowMs())
    }

    // ----- Sync -----

    suspend fun getAllForSync(): List<ChatMessage> = chatDao.getAllForSync()

    suspend fun upsertAll(messages: List<ChatMessage>) = chatDao.upsertAll(messages)
}
