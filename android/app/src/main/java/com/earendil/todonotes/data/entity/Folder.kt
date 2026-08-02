package com.earendil.todonotes.data.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * Ein Ordner für Notizen (Samsung-Notes-Style).
 *
 * - id: UUID, client-generiert
 * - parentId: null = Wurzel-Ebene; !=null = Unterordner.
 *   Ordner-in-Ordner-Verschachtelung (F6 Drag&Drop). Zyklen müssen
 *   client-seitig geprüft werden (ein Ordner darf nicht in sich selbst
 *   oder einen seiner Kind-Ordner verschoben werden).
 * - name: Anzeigename des Ordners
 * - createdAt / updatedAt / deletedAt: Soft-Delete + LWW-Sync wie überall
 *
 * Note.folderId referenziert Folder.id (kein harter FK, damit Sync ohne
 * Reihenfolgen-Probleme funktioniert — eine Notiz kann ankommen bevor
 * ihr Ordner da ist).
 */
@Entity(
    tableName = "folders",
    indices = [Index("parentId")]
)
data class Folder(
    @PrimaryKey
    val id: String,
    val parentId: String? = null,
    val name: String,
    val createdAt: Long,
    val updatedAt: Long,
    val deletedAt: Long? = null,
    val position: Long = 0L
)
