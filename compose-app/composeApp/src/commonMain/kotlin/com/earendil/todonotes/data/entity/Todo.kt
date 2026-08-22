package com.earendil.todonotes.data.entity

import androidx.room3.Entity
import androidx.room3.PrimaryKey

/**
 * Ein Todo-Aufgabe.
 *
 * - id: UUID, client-generiert (für Offline-Erstellung & Sync)
 * - dueAt: null = zeitloses Todo (erscheint unten); !=null = zeitgesteuert (oben gruppiert)
 * - recurrence: RFC 5545 RRULE-String, z.B. "FREQ=DAILY" (null = einmalig)
 * - completedAt: null = offen; gesetzt = erledigt (wird ins Verlauf verschoben)
 * - deletedAt: Soft-Delete
 * - updatedAt: Last-Write-Wins beim Sync
 * - logToHistory: Nutzer-Setting pro Todo "Nach Abschluss in Verlauf eintragen"
 * - userId: Multi-User (M1). Default "legacy-user" bei Migration v9→v10.
 */
@Entity(tableName = "todos")
data class Todo(
    @PrimaryKey
    val id: String,
    val title: String,
    val notes: String = "",
    val dueAt: Long? = null,
    val recurrence: String? = null,
    val completedAt: Long? = null,
    val createdAt: Long,
    val updatedAt: Long,
    val deletedAt: Long? = null,
    val logToHistory: Boolean = true,
    val userId: String = "legacy-user"
) {
    val isOpen: Boolean get() = completedAt == null && deletedAt == null
    val isTimed: Boolean get() = dueAt != null
}
