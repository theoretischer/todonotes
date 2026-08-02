package com.earendil.todonotes.data.repo

import com.earendil.todonotes.data.entity.CadenceType
import com.earendil.todonotes.data.entity.Habit
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Calendar
import java.util.TimeZone

class HabitEngineTest {

    private fun cal(y: Int, m: Int, d: Int, hh: Int = 12): Long {
        val c = Calendar.getInstance(TimeZone.getDefault())
        c.set(y, m - 1, d, hh, 0, 0)
        c.set(Calendar.MILLISECOND, 0)
        return c.timeInMillis
    }

    /** Tag-Teil eines Millis-Wertes als "Y-M-D". */
    private fun dayStr(ms: Long): String {
        val c = Calendar.getInstance(TimeZone.getDefault()).apply { timeInMillis = ms }
        return "${c.get(Calendar.YEAR)}-${c.get(Calendar.MONTH) + 1}-${c.get(Calendar.DAY_OF_MONTH)}"
    }

    @Test
    fun weekly_resetMonday_2perWeek_logsAccumulateUntilNextMonday() {
        // Start 1.8.2025 = Freitag (nicht Montag). Der Nutzer will reset am Montag.
        // Wir wählen als startDate den 4.8.2025 = Montag.
        val start = cal(2025, 8, 4) // Montag
        val habit = Habit(
            id = "h1",
            title = "Pizza",
            cadenceType = CadenceType.WEEK,
            interval = 1,
            resetWeekday = Calendar.MONDAY,
            goalCount = 2,
            startDate = start,
            createdAt = start,
            updatedAt = start
        )

        // Dienstag 5.8. 12:00 – eine Pizza gegessen, +1 log
        val dienstag = cal(2025, 8, 5)
        var count = 0
        val prog = HabitEngine.progress(habit, dienstag) { _, _ -> count }
        assertEquals(2, prog.goal)
        assertEquals(0, prog.count) // noch nichts geloggt
        count = 1
        val prog2 = HabitEngine.progress(habit, dienstag) { _, _ -> count }
        assertEquals(1, prog2.count)
        assertEquals(false, prog2.isComplete)
        // periodStart sollte Montag 4.8. sein (Tagesvergleich, nicht Millis wegen DST)
        assertEquals("2025-8-4", dayStr(prog2.periodStart))

        // Nächster Montag 11.8. 12:00 – Reset! Periode startet neu.
        val montag2 = cal(2025, 8, 11)
        val prog3 = HabitEngine.progress(habit, montag2) { _, _ -> 999 } // count ab neuem Start
        assertEquals("2025-8-11", dayStr(prog3.periodStart))
    }

    @Test
    fun monthly_resetFirst_logResetsOn1st() {
        val start = cal(2025, 8, 1) // 1.8.
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
        // 15.8. – Periode startet am 1.8.
        val mid = cal(2025, 8, 15)
        val prog = HabitEngine.progress(habit, mid) { _, _ -> 3 }
        assertEquals("2025-8-1", dayStr(prog.periodStart))
        assertEquals(3, prog.count)
        // 2.9. – neue Periode ab 1.9.
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
        // Tag 1 (1.8.): Periode Start
        assertEquals("2025-8-1", dayStr(HabitEngine.currentPeriodStart(habit, cal(2025, 8, 1))))
        // Tag 3 (3.8.): noch gleiche Periode
        assertEquals("2025-8-1", dayStr(HabitEngine.currentPeriodStart(habit, cal(2025, 8, 3))))
        // Tag 4 (4.8.): neue Periode
        assertEquals("2025-8-4", dayStr(HabitEngine.currentPeriodStart(habit, cal(2025, 8, 4))))
        // Tag 7: wieder neue Periode
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
            resetWeekday = Calendar.MONDAY,
            goalCount = 1,
            startDate = start,
            createdAt = start,
            updatedAt = start
        )
        // vor Start → Start
        assertEquals("2025-8-4", dayStr(HabitEngine.currentPeriodStart(habit, cal(2025, 7, 30))))
    }
}
