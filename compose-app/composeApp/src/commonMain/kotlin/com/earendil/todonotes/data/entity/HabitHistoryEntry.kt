package com.earendil.todonotes.data.entity

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
 */
data class HabitHistoryEntry(
    val id: String,
    val habitId: String,
    val title: String,
    val cadenceLabel: String,
    val periodStart: Long,
    val count: Int,
    val goal: Int,
    val loggedAt: Long
)
