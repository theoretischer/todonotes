package com.earendil.todonotes.data.entity

import androidx.room3.Entity
import androidx.room3.ForeignKey
import androidx.room3.Index
import androidx.room3.PrimaryKey

/**
 * Ein einzelner Log-Eintrag: "ich habe Habit X am timestamp gemacht".
 * Über die Logs in der aktuellen Periode wird der Count berechnet.
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
    indices = [Index("habitId"), Index("timestamp")]
)
data class HabitLog(
    @PrimaryKey
    val id: String,
    val habitId: String,
    val timestamp: Long,
    val note: String = "",
    val userId: String = "legacy-user"
)
