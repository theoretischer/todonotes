package com.earendil.todonotes.ui.todos

import com.earendil.todonotes.data.entity.Todo
import kotlinx.datetime.Clock
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.DayOfWeek
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.atStartOfDayIn
import kotlinx.datetime.minus
import kotlinx.datetime.plus
import kotlinx.datetime.toInstant
import kotlinx.datetime.toLocalDateTime

/**
 * Gruppiert offene Todos im Samsung-Reminder-Stil:
 * - Zeitgesteuerte Todos oben, gruppiert nach "Überfällig", "Heute", "Morgen", "Diese Woche", "Später"
 * - Zeitlose Todos darunter in einer eigenen Gruppe "Kein Datum"
 *
 * (M7b — commonMain, mit kotlinx.datetime statt java.util.Calendar)
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

fun groupOpenTodos(todos: List<Todo>, now: Long = Clock.System.now().toEpochMilliseconds()): TodoGroups {
    val tz = TimeZone.currentSystemDefault()
    val nowLdt = kotlinx.datetime.Instant.fromEpochMilliseconds(now).toLocalDateTime(tz)
    val today = nowLdt.date

    // Start der Tage (Mitternacht lokaler Zeit) als Epoch-Millis.
    val startOfToday = today.atStartOfDayIn(tz).toEpochMilliseconds()
    val startOfTomorrow = today.plus(1, DateTimeUnit.DAY).atStartOfDayIn(tz).toEpochMilliseconds()
    val startOfDayAfterTomorrow = today.plus(2, DateTimeUnit.DAY).atStartOfDayIn(tz).toEpochMilliseconds()
    // "Diese Woche" = bis Ende der Woche (Sonntag 23:59:59.999).
    // Wochenbeginn = Montag → Sonntag = heute + (7 - isoDayNumber(now)) Tage
    val daysToSunday = (7 - nowLdt.dayOfWeek.ordinal) // ordinal: MO=0..SU=6 → So = +6-ordinal
    val endOfWeekDate = today.plus(daysToSunday, DateTimeUnit.DAY)
    val endOfWeek = kotlinx.datetime.LocalDateTime(endOfWeekDate, kotlinx.datetime.LocalTime(23, 59, 59, 999_000_000))
        .toInstant(tz).toEpochMilliseconds()

    val overdue = mutableListOf<Todo>()
    val todayList = mutableListOf<Todo>()
    val tomorrowList = mutableListOf<Todo>()
    val thisWeekList = mutableListOf<Todo>()
    val later = mutableListOf<Todo>()
    val noDate = mutableListOf<Todo>()

    todos.forEach { t ->
        val due = t.dueAt
        if (due == null) {
            noDate.add(t)
        } else when {
            due < startOfToday -> overdue.add(t)
            due < startOfTomorrow -> todayList.add(t)
            due < startOfDayAfterTomorrow -> tomorrowList.add(t)
            due <= endOfWeek -> thisWeekList.add(t)
            else -> later.add(t)
        }
    }
    return TodoGroups(overdue, todayList, tomorrowList, thisWeekList, later, noDate)
}

// Hilfs-Extension: LocalDate.plus (DateTimeUnit.DAY) — in kotlinx-datetime 0.6 vorhanden.
// LocalTime(nanos) Konstruktor benötigt explizite nanos-Angabe (hier 999_000_000 für .999).
