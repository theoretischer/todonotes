package com.earendil.todonotes.data.entity

import androidx.room3.Entity
import androidx.room3.ForeignKey
import androidx.room3.Index
import androidx.room3.PrimaryKey

/**
 * Ein einzelner Log-Eintrag: "ich habe Habit X am timestamp gemacht".
 * Über die Logs in der aktuellen Periode wird der Count berechnet.
 *
 * M11 (Verlauf-Bugfix): updatedAt + deletedAt für LWW-Sync + Soft-Delete.
 * Vorher append-only → Undo (+1 wieder weg) und Perioden-Reset (Logs weg)
 * wurden per Full-Down-Sync zurückgeliefert → Count sprang wieder hoch.
 */
@Entity(
    tableName = "habit_logs",
    foreignKeys = [
        ForeignKey(
            entity = Habit::class,
            parentColumns = ["id"],
            childColumns = ["habitId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index("habitId"), Index("timestamp"), Index("deletedAt")]
)
data class HabitLog(
    @PrimaryKey
    val id: String,
    val habitId: String,
    val timestamp: Long,
    val note: String = "",
    /** M11: LWW-Change-Column (0 = unverändert seit Migration). */
    val updatedAt: Long = 0,
    /** M11: Soft-Delete — Zeile bleibt als Tombstone für den Sync erhalten. */
    val deletedAt: Long? = null,
    val userId: String = "legacy-user"
)
