package com.earendil.todonotes.data.entity

/**
 * Ein einzelner Log-Eintrag: "ich habe Habit X am timestamp gemacht".
 * Über die Logs in der aktuellen Periode wird der Count berechnet.
 */
data class HabitLog(
    val id: String,
    val habitId: String,
    val timestamp: Long,
    val note: String = ""
)
