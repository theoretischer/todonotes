package com.earendil.todonotes.data.entity

import androidx.room3.Entity
import androidx.room3.PrimaryKey

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
 * - bodyJson: serialisierter Rich-Text-Baum (F3): geordnete Liste von
 *   Blocks (Paragraph / ListBlock / ImageBlock / DrawingBlock).
 *   Bilder werden NICHT als Base64 hierin gespeichert — nur Referenzen
 *   {type:"image", imageId, width, height}. Die eigentlichen Bilddateien
 *   liegen unter files/notes/<noteId>/<imageId>.png (F7) und werden
 *   separat synchronisiert (F9).
 * - createdAt / updatedAt / deletedAt: wie bei Todo (Soft-Delete + LWW-Sync)
 * - userId: Multi-User (M1). Default "legacy-user" bei Migration v9→v10.
 */
@Entity(tableName = "notes")
data class Note(
    @PrimaryKey
    val id: String,
    val folderId: String? = null,
    val type: NoteType = NoteType.NOTE,
    val title: String = "",
    val bodyJson: String = "[]",
    val createdAt: Long,
    val updatedAt: Long,
    val deletedAt: Long? = null,
    val position: Long = 0L,
    val userId: String = "legacy-user"
)
