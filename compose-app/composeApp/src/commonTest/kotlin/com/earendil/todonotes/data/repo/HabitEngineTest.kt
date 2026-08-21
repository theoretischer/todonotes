package com.earendil.todonotes.data.repo

import com.earendil.todonotes.data.entity.CadenceType
import com.earendil.todonotes.data.entity.Habit
import kotlinx.datetime.Instant
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.LocalTime
import kotlinx.datetime.Month
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toInstant
import kotlinx.datetime.toLocalDateTime
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Portabilitäts-Test für HabitEngine (commonMain).
 * Entspricht den Android-Tests, nutzt aber kotlinx-datetime statt java.util.Calendar.
 */
class HabitEngineTest {

    private val TZ = TimeZone.currentSystemDefault()

    // Calendar.MONDAY = 2 (1=SO, 2=MO, ..., 7=SA) — so wie in der DB gespeichert
    private val CALENDAR_MONDAY = 2

    private fun cal(y: Int, m: Int, d: Int, hh: Int = 12): Long {
        val date = LocalDate(y, Month.entries[m - 1], d)
        return LocalDateTime(date, LocalTime(hh, 0, 0)).toInstant(TZ).toEpochMilliseconds()
    }

    /** Tag-Teil eines Millis-Wertes als "Y-M-D". */
    private fun dayStr(ms: Long): String {
        val ldt = Instant.fromEpochMilliseconds(ms).toLocalDateTime(TZ)
        return "${ldt.year}-${ldt.monthNumber}-${ldt.dayOfMonth}"
    }

    @Test
    fun weekly_resetMonday_2perWeek_logsAccumulateUntilNextMonday() {
        val start = cal(2025, 8, 4) // Montag
        val habit = Habit(
            id = "h1",
            title = "Pizza",
            cadenceType = CadenceType.WEEK,
            interval = 1,
            resetWeekday = CALENDAR_MONDAY,
            goalCount = 2,
            startDate = start,
            createdAt = start,
            updatedAt = start
        )

        val dienstag = cal(2025, 8, 5)
        var count = 0
        val prog = HabitEngine.progress(habit, dienstag) { _, _ -> count }
        assertEquals(2, prog.goal)
        assertEquals(0, prog.count)
        count = 1
        val prog2 = HabitEngine.progress(habit, dienstag) { _, _ -> count }
        assertEquals(1, prog2.count)
        assertEquals(false, prog2.isComplete)
        assertEquals("2025-8-4", dayStr(prog2.periodStart))

        val montag2 = cal(2025, 8, 11)
        val prog3 = HabitEngine.progress(habit, montag2) { _, _ -> 999 }
        assertEquals("2025-8-11", dayStr(prog3.periodStart))
    }

    @Test
    fun monthly_resetFirst_logResetsOn1st() {
        val start = cal(2025, 8, 1)
        val habit = Habit(
            id = "h2",
            title = "Sport",
            cadenceType = CadenceType.MONTH,
            interval = 1,
            resetAnchorDay = 1,
            goalCount = 5,
            startDate = start,
            createdAt = start,
            updatedAt = start
        )
        val mid = cal(2025, 8, 15)
        val prog = HabitEngine.progress(habit, mid) { _, _ -> 3 }
        assertEquals("2025-8-1", dayStr(prog.periodStart))
        assertEquals(3, prog.count)
        val sep = cal(2025, 9, 2)
        val prog2 = HabitEngine.progress(habit, sep) { _, _ -> 1 }
        assertEquals("2025-9-1", dayStr(prog2.periodStart))
    }

    @Test
    fun ndays_every3days_periodIs3Days() {
        val start = cal(2025, 8, 1)
        val habit = Habit(
            id = "h3",
            title = "Trinken",
            cadenceType = CadenceType.NDAYS,
            interval = 3,
            goalCount = 1,
            startDate = start,
            createdAt = start,
            updatedAt = start
        )
        assertEquals("2025-8-1", dayStr(HabitEngine.currentPeriodStart(habit, cal(2025, 8, 1))))
        assertEquals("2025-8-1", dayStr(HabitEngine.currentPeriodStart(habit, cal(2025, 8, 3))))
        assertEquals("2025-8-4", dayStr(HabitEngine.currentPeriodStart(habit, cal(2025, 8, 4))))
        assertEquals("2025-8-7", dayStr(HabitEngine.currentPeriodStart(habit, cal(2025, 8, 7))))
    }

    @Test
    fun daily_everyDay_periodIsDay() {
        val start = cal(2025, 8, 1)
        val habit = Habit(
            id = "h4",
            title = "Zähne",
            cadenceType = CadenceType.DAY,
            interval = 1,
            goalCount = 1,
            startDate = start,
            createdAt = start,
            updatedAt = start
        )
        assertEquals("2025-8-1", dayStr(HabitEngine.currentPeriodStart(habit, cal(2025, 8, 1))))
        assertEquals("2025-8-1", dayStr(HabitEngine.currentPeriodStart(habit, cal(2025, 8, 1, 23))))
        assertEquals("2025-8-2", dayStr(HabitEngine.currentPeriodStart(habit, cal(2025, 8, 2))))
    }

    @Test
    fun weekly_resetMonday_beforeStartReturnsStart() {
        val start = cal(2025, 8, 4) // Montag
        val habit = Habit(
            id = "h5",
            title = "X",
            cadenceType = CadenceType.WEEK,
            interval = 1,
            resetWeekday = CALENDAR_MONDAY,
            goalCount = 1,
            startDate = start,
            createdAt = start,
            updatedAt = start
        )
        assertEquals("2025-8-4", dayStr(HabitEngine.currentPeriodStart(habit, cal(2025, 7, 30))))
    }

    @Test
    fun nextPeriodStart_weekly_is7DaysLater() {
        val start = cal(2025, 8, 4) // Montag
        val habit = Habit(
            id = "h6",
            title = "W",
            cadenceType = CadenceType.WEEK,
            interval = 1,
            resetWeekday = CALENDAR_MONDAY,
            goalCount = 1,
            startDate = start,
            createdAt = start,
            updatedAt = start
        )
        val now = cal(2025, 8, 5) // Dienstag
        val next = HabitEngine.nextPeriodStart(habit, now)
        assertEquals("2025-8-11", dayStr(next)) // nächster Montag
    }

    @Test
    fun nextPeriodStart_monthly_isNextMonth() {
        val start = cal(2025, 8, 1)
        val habit = Habit(
            id = "h7",
            title = "M",
            cadenceType = CadenceType.MONTH,
            interval = 1,
            resetAnchorDay = 1,
            goalCount = 1,
            startDate = start,
            createdAt = start,
            updatedAt = start
        )
        val now = cal(2025, 8, 15)
        val next = HabitEngine.nextPeriodStart(habit, now)
        assertEquals("2025-9-1", dayStr(next))
    }

    // ---- Smoke Tests ----

    @Test
    fun allCadenceTypes_startInFuture_noCrash() {
        val future = cal(2030, 1, 1)
        val now = cal(2026, 8, 2)
        CadenceType.entries.forEach { ct ->
            val h = smokeHabit(ct, future)
            val start = HabitEngine.currentPeriodStart(h, now)
            assertTrue(start >= future, "$ct: start sollte >= startDate sein")
        }
    }

    @Test
    fun allCadenceTypes_startInPast_noCrash() {
        val past = cal(2020, 1, 1)
        val now = cal(2026, 8, 2)
        CadenceType.entries.forEach { ct ->
            val start = HabitEngine.currentPeriodStart(smokeHabit(ct, past), now)
            assertTrue(start <= now, "$ct: start sollte <= now sein")
        }
    }

    @Test
    fun allCadenceTypes_nullAnchors_noCrash() {
        val now = cal(2026, 8, 2)
        CadenceType.entries.forEach { ct ->
            val h = Habit(
                id = "x", title = "T", cadenceType = ct, interval = 1,
                resetWeekday = null, resetAnchorDay = null, resetAnchorMonth = null,
                goalCount = 2, startDate = now, createdAt = now, updatedAt = now
            )
            HabitEngine.currentPeriodStart(h, now)
        }
    }

    private fun smokeHabit(type: CadenceType, start: Long, goal: Int = 2, interval: Int = 1) = Habit(
        id = "x", title = "T", cadenceType = type, interval = interval,
        resetWeekday = if (type == CadenceType.WEEK) CALENDAR_MONDAY else null,
        resetAnchorDay = if (type == CadenceType.MONTH || type == CadenceType.YEAR) 1 else null,
        resetAnchorMonth = if (type == CadenceType.YEAR) 1 else null,
        goalCount = goal, startDate = start, createdAt = start, updatedAt = start
    )
}
