package com.earendil.todonotes.data.repo

import com.earendil.todonotes.data.entity.Todo
import com.earendil.todonotes.ui.todos.groupOpenTodos
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.Instant
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toInstant
import kotlinx.datetime.toLocalDateTime
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * Bug 7: "Diese Woche" muss bis SONNTAG 23:59 gehen (Wochenende), nicht
 * 7 volle Tage ab heute (vorher Montag +7 = uebernaechster Montag).
 */
class TodoGroupingTest {

    // System-TZ ist Europe/Berlin — alle Zeitpunkte BERLIN-lokal rechnen,
    // sonst landet 23:59 UTC schon am Folgetag.
    private fun local(y: Int, m: Int, d: Int, h: Int = 0, min: Int = 0): Long =
        kotlinx.datetime.LocalDateTime(y, m, d, h, min, 0, 0)
            .toInstant(TimeZone.of("Europe/Berlin")).toEpochMilliseconds()

    private fun todoDue(dueAt: Long) = Todo(
        id = "t-$dueAt",
        title = "T",
        notes = "",
        dueAt = dueAt,
        recurrence = null,
        completedAt = null,
        createdAt = 0L,
        updatedAt = 0L,
        deletedAt = null,
        logToHistory = true,
        userId = "u"
    )

    @Test
    fun monday_dueSundayIsThisWeek_dueNextMondayIsLater() {
        // Montag 2026-02-02 00:00 Uhr lokal (UTC-Zeiten, TZ egal solange konsistent)
        val monday = local(2026, 2, 2, 12)

        val groups = groupOpenTodos(listOf(), now = monday)

        // Der uebergebene Todo leer — wir pruefen die GRENZEN direkt:
        // Sonntags-Ende (2026-02-08 23:59:59.999) muss "Diese Woche" sein,
        // Montag-frueh (2026-02-09 00:00) muss "Spaeter" sein.
        val sundayEnd = local(2026, 2, 8, 23, 59) + 59_999
        val nextMonday = local(2026, 2, 9)

        val g1 = groupOpenTodos(listOf(todoDue(sundayEnd)), now = monday)
        assertEquals(1, g1.thisWeek.size, "Sonntag 23:59 muss 'Diese Woche' sein")
        val g2 = groupOpenTodos(listOf(todoDue(nextMonday)), now = monday)
        assertEquals(0, g2.thisWeek.size, "Montag naechster Woche muss 'Spaeter' sein")
        assertEquals(1, g2.later.size)
    }

    @Test
    fun sunday_todayTodoCountsAsToday() {
        // Sonntag 2026-02-08: ein Todo HEUTE zaehlt als "Heute" (die
        // when-Kette prueft Heute/Morgen zuerst — ein Sonntags-Todo ist
        // nie "Diese Woche", sondern "Heute"). Grenze unterstuetzt das:
        // endOfWeek = Sonntag 23:59 = heute → nicht "Spaeter".
        val sunday = local(2026, 2, 8, 12)
        val sundayNight = local(2026, 2, 8, 23, 59) + 59_999

        val g = groupOpenTodos(listOf(todoDue(sundayNight)), now = sunday)
        assertEquals(1, g.today.size, "Sonntagabend ist noch 'Heute'")
        assertEquals(0, g.later.size, "Sonntagabend faellt nicht in 'Spaeter'")
    }

    @Test
    fun midWeek_tuesdayToSundayIsThisWeek() {
        val tuesday = local(2026, 2, 3, 9)
        val saturday = local(2026, 2, 7, 18)

        val g = groupOpenTodos(listOf(todoDue(saturday)), now = tuesday)
        assertEquals(1, g.thisWeek.size, "Samstag (Dienstag-Woche) ist 'Diese Woche'")
    }
}