package com.earendil.todonotes.data.entity

/**
 * Art der Notiz (Block H). `NOTE` = klassische Rich-Text-Notiz (Editor F5),
 * `CHAT` = WhatsApp-Style Nachrichten-Verlauf ([ChatMessage]-Tabelle, H3).
 * Default `NOTE`, damit bestehende Notizen nach der Migration (v7→v8)
 * normal weiter funktionieren.
 */
enum class NoteType { NOTE, CHAT }

/**
 * Eine Notiz (Samsung-Notes-Style).
 *
 * - id: UUID, client-generiert (für Offline-Erstellung & Sync)
 * - folderId: null = Wurzel-Ebene; !=null = liegt im angegebenen Ordner
 * - type: NOTE = Rich-Text-Editor (F5), CHAT = Nachrichten-Verlauf (H3)
 * - title: erste Zeile des Body (beim Speichern auto extrahiert, F5) bzw.
 *   bei CHAT der Chat-Name
 * - bodyJson: serialisierter Rich-Text-Baum (F3) oder Plain-Text (F5)
 * - createdAt / updatedAt / deletedAt: wie bei Todo (Soft-Delete + LWW-Sync)
 */
data class Note(
    val id: String,
    val folderId: String? = null,
    val type: NoteType = NoteType.NOTE,
    val title: String = "",
    val bodyJson: String = "[]",
    val createdAt: Long,
    val updatedAt: Long,
    val deletedAt: Long? = null,
    val position: Long = 0L
)
