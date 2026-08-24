package com.earendil.todonotes.ui.habits

import com.earendil.todonotes.data.entity.CadenceType
import com.earendil.todonotes.data.entity.HabitType

/** Payload, den der Habit-Dialog beim Bestätigen zurückliefert. */
data class HabitFormData(
    val title: String,
    val notes: String,
    val cadenceType: CadenceType,
    val interval: Int = 1,
    val goalCount: Int,
    val startDate: Long,
    val logToHistory: Boolean = true,
    val type: HabitType = HabitType.HABIT,
    val rating: Int = 5
)
