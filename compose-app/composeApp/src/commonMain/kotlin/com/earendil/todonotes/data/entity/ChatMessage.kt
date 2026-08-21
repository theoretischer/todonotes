package com.earendil.todonotes.data.entity

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
 * - quotedMessageId: Id der zitierten Nachricht (optional, Block H-Quote). null = keine Zitat.
 */
data class ChatMessage(
    val id: String,
    val noteId: String,
    val text: String,
    val createdAt: Long,
    val updatedAt: Long,
    val deletedAt: Long? = null,
    val position: Long = 0L,
    val quotedMessageId: String? = null
)
