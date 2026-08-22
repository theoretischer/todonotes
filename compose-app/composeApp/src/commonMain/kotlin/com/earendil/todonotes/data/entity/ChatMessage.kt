package com.earendil.todonotes.data.entity

import androidx.room3.Entity
import androidx.room3.Index
import androidx.room3.PrimaryKey

/**
 * Eine Chat-Nachricht (Block H — WhatsApp-Style Tracking-Notizen).
 *
 * Gehört zu einer Notiz mit `type = CHAT` (siehe [Note.type]). Die
 * einzelnen Nachrichten sind eine EIGENE Tabelle (nicht im note.bodyJson),
 * damit jede ihr eigenes unveränderliches [createdAt] hat — das bleibt
 * beim Bearbeiten (nur `text`/`updatedAt` ändern) gleich, sodass die
 * Uhrzeit-Anzeige stabil bleibt.
 *
 * - id: UUID, client-generiert (Offline + Sync)
 * - noteId: Eltern-Chat-Notiz
 * - text: Nachrichtentext (Plain, kein Rich-Text)
 * - createdAt: wann die Nachricht ursprünglich abgesetzt wurde (stabil)
 * - updatedAt: letzte Bearbeitung (für LWW-Sync)
 * - deletedAt: Soft-Delete (wie überall, sync-fähig)
 * - position: Reihenfolge innerhalb der Chat-Notiz (älteste = kleinste)
 * - userId: Multi-User (M1). Default "legacy-user" bei Migration v9→v10.
 */
@Entity(
    tableName = "chat_messages",
    indices = [Index(value = ["noteId"], name = "index_chat_messages_noteId")]
)
data class ChatMessage(
    @PrimaryKey
    val id: String,
    val noteId: String,
    val text: String,
    val createdAt: Long,
    val updatedAt: Long,
    val deletedAt: Long? = null,
    val position: Long = 0L,
    /** Id der zitierten Nachricht (optional, Block H-Quote). null = keine Zitat. */
    val quotedMessageId: String? = null,
    val userId: String = "legacy-user"
)
