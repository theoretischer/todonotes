package com.earendil.todonotes.ui.todos

import com.earendil.todonotes.data.entity.Todo
import java.util.Calendar

/**
 * Gruppiert offene Todos im Samsung-Reminder-Stil:
 * - Zeitgesteuerte Todos oben, gruppiert nach "Überfällig", "Heute", "Morgen", "Diese Woche", "Später"
 * - Zeitlose Todos darunter in einer eigenen Gruppe "Kein Datum"
 */
data class TodoGroups(
    val overdue: List<Todo>,
    val today: List<Todo>,
    val tomorrow: List<Todo>,
    val thisWeek: List<Todo>,
    val later: List<Todo>,
    val noDate: List<Todo>
) {
    fun isEmpty(): Boolean = listOf(overdue, today, tomorrow, thisWeek, later, noDate).all { it.isEmpty() }
}

fun groupOpenTodos(todos: List<Todo>, now: Long = System.currentTimeMillis()): TodoGroups {
    val calNow = Calendar.getInstance().apply {
        timeInMillis = now
        set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0); set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
    }
    val startOfToday = calNow.timeInMillis
    val startOfTomorrow = startOfToday + 24 * 60 * 60 * 1000L
    val startOfDayAfterTomorrow = startOfTomorrow + 24 * 60 * 60 * 1000L
    // "Diese Woche" = bis Ende der Woche (Sonntag)
    val endOfWeek = Calendar.getInstance().apply {
        timeInMillis = now
        set(Calendar.HOUR_OF_DAY, 23); set(Calendar.MINUTE, 59); set(Calendar.SECOND, 59); set(Calendar.MILLISECOND, 999)
        // Wochenbeginn = Montag → Differenz zu Sonntag
        val dayOfWeek = get(Calendar.DAY_OF_WEEK)
        val daysToSunday = if (dayOfWeek == Calendar.SUNDAY) 0 else (Calendar.SATURDAY - dayOfWeek + 1)
        add(Calendar.DAY_OF_MONTH, daysToSunday)
    }.timeInMillis

    val overdue = mutableListOf<Todo>()
    val today = mutableListOf<Todo>()
    val tomorrow = mutableListOf<Todo>()
    val thisWeek = mutableListOf<Todo>()
    val later = mutableListOf<Todo>()
    val noDate = mutableListOf<Todo>()

    todos.forEach { t ->
        val due = t.dueAt
        if (due == null) {
            noDate.add(t)
        } else when {
            due < startOfToday -> overdue.add(t)
            due < startOfTomorrow -> today.add(t)
            due < startOfDayAfterTomorrow -> tomorrow.add(t)
            due <= endOfWeek -> thisWeek.add(t)
            else -> later.add(t)
        }
    }
    return TodoGroups(overdue, today, tomorrow, thisWeek, later, noDate)
}
