package com.earendil.todonotes.data.entity

import androidx.room3.Entity
import androidx.room3.ForeignKey
import androidx.room3.Index
import androidx.room3.PrimaryKey

/**
 * Verlaufseintrag für ein Habit pro abgelaufener Periode.
 *
 * Wird beim Periodenwechsel automatisch erstellt (falls habit.logToHistory==true).
 * Speichert den Snapshot von Titel/Ziel, damit der Verlauf auch nach Löschen
 * oder Umbenennen des Habits aussagekräftig bleibt.
 *
 * - periodStart: Start der abgelaufenen Periode
 * - count: wie oft in dieser Periode erledigt
 * - goal: Ziel der Periode (Snapshot)
 * - newRating: neuer Rating-Wert bei Satisfaction-Trackern (0-10).
 *   null bei klassischen Habits.
 * - userId: Multi-User (M1). Default "legacy-user" bei Migration v9→v10.
 *
 * M11 (Verlauf-Bugfix): updatedAt + deletedAt für LWW-Sync + Soft-Delete.
 * Vorher war die Tabelle append-only → Löschen wurde nie mitgesynct und
 * Perioden-Einträge bekamen Zufalls-IDs → Race-Duplikate über Geräte.
 */
@Entity(
    tableName = "habit_history",
    foreignKeys = [
        ForeignKey(
            entity = Habit::class,
            parentColumns = ["id"],
            childColumns = ["habitId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index("habitId"), Index("loggedAt"), Index("deletedAt")]
)
data class HabitHistoryEntry(
    @PrimaryKey
    val id: String,
    val habitId: String,
    val title: String,
    val cadenceLabel: String,
    val periodStart: Long,
    val count: Int,
    val goal: Int,
    val newRating: Int? = null,
    val loggedAt: Long,
    /** M11: LWW-Change-Column (0 = unverändert seit Migration). */
    val updatedAt: Long = 0,
    /** M11: Soft-Delete — Zeile bleibt als Tombstone für den Sync erhalten. */
    val deletedAt: Long? = null,
    val userId: String = "legacy-user"
)
