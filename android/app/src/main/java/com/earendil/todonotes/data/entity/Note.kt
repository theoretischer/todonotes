package com.earendil.todonotes.data.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Eine Notiz (Samsung-Notes-Style).
 *
 * - id: UUID, client-generiert (für Offline-Erstellung & Sync)
 * - folderId: null = Wurzel-Ebene; !=null = liegt im angegebenen Ordner
 * - title: erste Zeile des Body (beim Speichern auto extrahiert, F5)
 * - bodyJson: serialisierter Rich-Text-Baum (F3): geordnete Liste von
 *   Blocks (Paragraph / ListBlock / ImageBlock / DrawingBlock).
 *   Bilder werden NICHT als Base64 hierin gespeichert — nur Referenzen
 *   {type:"image", imageId, width, height}. Die eigentlichen Bilddateien
 *   liegen unter files/notes/<noteId>/<imageId>.png (F7) und werden
 *   separat synchronisiert (F9).
 * - createdAt / updatedAt / deletedAt: wie bei Todo (Soft-Delete + LWW-Sync)
 */
@Entity(tableName = "notes")
data class Note(
    @PrimaryKey
    val id: String,
    val folderId: String? = null,
    val title: String = "",
    val bodyJson: String = "[]",
    val createdAt: Long,
    val updatedAt: Long,
    val deletedAt: Long? = null
)
