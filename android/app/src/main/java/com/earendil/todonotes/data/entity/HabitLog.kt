package com.earendil.todonotes.data.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

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
    val note: String = ""
)
