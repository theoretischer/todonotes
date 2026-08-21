package com.earendil.todonotes.data.repo

import com.earendil.todonotes.data.entity.CadenceType
import com.earendil.todonotes.data.entity.Habit
import kotlinx.datetime.*

/**
 * Berechnet für ein Habit den Start der aktuellen Periode (den Reset-Punkt),
 * ab dem die Logs für den Count gezählt werden.
 *
 * Reset-Logik:
 *  - DAY / NDAYS: Periode ist [interval] Tage lang, beginnt am startDate und wiederholt sich.
 *  - WEEK: Periode ist [interval] Wochen, resetted am [resetWeekday] (1=MO..7=SO, ISO).
 *  - MONTH: Periode ist [interval] Monate, resetted am [resetAnchorDay]. des Monats.
 *  - YEAR: Periode ist [interval] Jahre, resetted am [resetAnchorDay]. des [resetAnchorMonth].
 *
 * Diese Version nutzt kotlinx-datetime statt java.util.Calendar.
 * resetWeekday wird in ISO-Nummerierung gespeichert (1=MO..7=SO).
 */
object HabitEngine {

    private val TZ = TimeZone.currentSystemDefault()
    private val DAY_MS = 24L * 60 * 60 * 1000

    fun currentPeriodStart(habit: Habit, now: Long): Long {
        val nowLdt = millisToLdt(now)
        val computed = when (habit.cadenceType) {
            CadenceType.DAY -> anchoredDayStart(habit, now)
            CadenceType.NDAYS -> anchoredDayStart(habit, now)
            CadenceType.WEEK -> weekStart(habit, now, nowLdt)
            CadenceType.MONTH -> monthStart(habit, now, nowLdt)
            CadenceType.YEAR -> yearStart(habit, now, nowLdt)
        }
        return if (computed < habit.startDate) habit.startDate else computed
    }

    fun nextPeriodStart(habit: Habit, now: Long): Long {
        val current = currentPeriodStart(habit, now)
        val currentLdt = millisToLdt(current)
        return when (habit.cadenceType) {
            CadenceType.DAY, CadenceType.NDAYS -> addDays(current, habit.interval)
            CadenceType.WEEK -> addDays(current, habit.interval * 7)
            CadenceType.MONTH -> addMonths(currentLdt, habit.interval)
            CadenceType.YEAR -> addYears(currentLdt, habit.interval)
        }
    }

    fun progress(habit: Habit, now: Long, countSinceProvider: (String, Long) -> Int): HabitProgress {
        val start = currentPeriodStart(habit, now)
        val count = countSinceProvider(habit.id, start)
        return HabitProgress(count = count, goal = habit.goalCount, periodStart = start)
    }

    // ---- DAY / NDAYS ----
    private fun anchoredDayStart(habit: Habit, now: Long): Long {
        val startDay = millisToLdt(habit.startDate).date
        val startMillis = startDay.atStartOfDayIn(TZ).toEpochMilliseconds()
        if (now <= startMillis) return startMillis
        val elapsedDays = (now - startMillis) / DAY_MS
        val periodsPassed = elapsedDays / habit.interval
        val periodStartDays = periodsPassed * habit.interval
        return startMillis + periodStartDays * DAY_MS
    }

    // ---- WEEK ----
    // resetWeekday: Calendar-Nummerierung (1=SO, 2=MO, ..., 7=SA), wie in der DB gespeichert.
    // kotlinx DayOfWeek: ordinal+1 = 1=MO..7=SO. Konvertierung nötig.
    private fun weekStart(habit: Habit, now: Long, nowLdt: LocalDateTime): Long {
        val resetWdCal = habit.resetWeekday ?: 2 // Calendar.MONDAY = 2
        val resetWdKx = calToKotlinxWeekday(resetWdCal)
        val today = nowLdt.date
        val currentDowKx = today.dayOfWeek.ordinal + 1 // 1=MO..7=SO
        var diff = (currentDowKx - resetWdKx + 7) % 7
        var periodStart = today.plus(-diff, DateTimeUnit.DAY)
        if (habit.interval > 1) {
            val startDay = millisToLdt(habit.startDate).date
            val d0 = (startDay.dayOfWeek.ordinal + 1 - resetWdKx + 7) % 7
            val startWeekDay = startDay.plus(-d0, DateTimeUnit.DAY)
            val weeksBetween = daysBetween(startWeekDay, periodStart) / 7
            val periodsPassed = weeksBetween / habit.interval
            val periodStartWeeks = periodsPassed * habit.interval
            periodStart = startWeekDay.plus(periodStartWeeks.toInt(), DateTimeUnit.WEEK)
        }
        return periodStart.atStartOfDayIn(TZ).toEpochMilliseconds()
    }

    // ---- MONTH ----
    private fun monthStart(habit: Habit, now: Long, nowLdt: LocalDateTime): Long {
        val anchorDay = (habit.resetAnchorDay ?: 1).coerceAtMost(28)
        var anchor = LocalDate(nowLdt.year, nowLdt.month, anchorDay)
        if (anchor.atStartOfDayIn(TZ).toEpochMilliseconds() > now) {
            anchor = anchor.plus(-1, DateTimeUnit.MONTH)
        }
        if (habit.interval > 1) {
            val startLdt = millisToLdt(habit.startDate)
            var startAnchor = LocalDate(startLdt.year, startLdt.month, anchorDay)
            if (startAnchor.atStartOfDayIn(TZ).toEpochMilliseconds() > habit.startDate) {
                startAnchor = startAnchor.plus(-1, DateTimeUnit.MONTH)
            }
            val monthsBetween = monthsBetween(startAnchor, anchor)
            val periodsPassed = monthsBetween / habit.interval
            val periodStartMonths = periodsPassed * habit.interval
            anchor = startAnchor.plus(periodStartMonths.toInt(), DateTimeUnit.MONTH)
        }
        return anchor.atStartOfDayIn(TZ).toEpochMilliseconds()
    }

    // ---- YEAR ----
    private fun yearStart(habit: Habit, now: Long, nowLdt: LocalDateTime): Long {
        val anchorDay = (habit.resetAnchorDay ?: 1).coerceAtMost(28)
        val anchorMonth = Month.entries[(habit.resetAnchorMonth ?: 1) - 1]
        var anchor = LocalDate(nowLdt.year, anchorMonth, anchorDay)
        if (anchor.atStartOfDayIn(TZ).toEpochMilliseconds() > now) {
            anchor = anchor.plus(-1, DateTimeUnit.YEAR)
        }
        if (habit.interval > 1) {
            val startLdt = millisToLdt(habit.startDate)
            var startAnchor = LocalDate(startLdt.year, anchorMonth, anchorDay)
            if (startAnchor.atStartOfDayIn(TZ).toEpochMilliseconds() > habit.startDate) {
                startAnchor = startAnchor.plus(-1, DateTimeUnit.YEAR)
            }
            val yearsBetween = (anchor.year - startAnchor.year).toLong()
            val periodsPassed = yearsBetween / habit.interval
            val periodStartYears = periodsPassed * habit.interval
            anchor = startAnchor.plus(periodStartYears.toInt(), DateTimeUnit.YEAR)
        }
        return anchor.atStartOfDayIn(TZ).toEpochMilliseconds()
    }

    // ---- Helpers ----

    /**
     * Calendar-Wochentag (1=SO, 2=MO, ..., 7=SA) → kotlinx-DayOfWeek-Nummer
     * (1=MO, ..., 7=SO).
     */
    private fun calToKotlinxWeekday(calDay: Int): Int = (calDay + 5) % 7 + 1

    private fun millisToLdt(millis: Long): LocalDateTime =
        Instant.fromEpochMilliseconds(millis).toLocalDateTime(TZ)

    private fun daysBetween(a: LocalDate, b: LocalDate): Long {
        val aMs = a.atStartOfDayIn(TZ).toEpochMilliseconds()
        val bMs = b.atStartOfDayIn(TZ).toEpochMilliseconds()
        return (bMs - aMs) / DAY_MS
    }

    private fun monthsBetween(a: LocalDate, b: LocalDate): Long =
        (b.year.toLong() - a.year) * 12 + (b.monthNumber - a.monthNumber)

    private fun addDays(millis: Long, days: Int): Long {
        val ldt = millisToLdt(millis)
        val newDate = ldt.date.plus(days, DateTimeUnit.DAY)
        val timeOfDayMs = millis - ldt.date.atStartOfDayIn(TZ).toEpochMilliseconds()
        return newDate.atStartOfDayIn(TZ).toEpochMilliseconds() + timeOfDayMs
    }

    private fun addMonths(ldt: LocalDateTime, months: Int): Long {
        val newDate = ldt.date.plus(months, DateTimeUnit.MONTH)
        val timeOfDayMs = ldt.toInstant(TZ).toEpochMilliseconds() - ldt.date.atStartOfDayIn(TZ).toEpochMilliseconds()
        return newDate.atStartOfDayIn(TZ).toEpochMilliseconds() + timeOfDayMs
    }

    private fun addYears(ldt: LocalDateTime, years: Int): Long {
        val newDate = ldt.date.plus(years, DateTimeUnit.YEAR)
        val timeOfDayMs = ldt.toInstant(TZ).toEpochMilliseconds() - ldt.date.atStartOfDayIn(TZ).toEpochMilliseconds()
        return newDate.atStartOfDayIn(TZ).toEpochMilliseconds() + timeOfDayMs
    }
}

data class HabitProgress(
    val count: Int,
    val goal: Int,
    val periodStart: Long
) {
    val isComplete: Boolean get() = count >= goal
    val ratio: Float get() = if (goal <= 0) 0f else count.toFloat() / goal
}
