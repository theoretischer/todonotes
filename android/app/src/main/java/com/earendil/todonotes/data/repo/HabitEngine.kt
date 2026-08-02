package com.earendil.todonotes.data.repo

import com.earendil.todonotes.data.entity.CadenceType
import com.earendil.todonotes.data.entity.Habit
import java.util.Calendar
import java.util.TimeZone

/**
 * Berechnet für ein Habit den Start der aktuellen Periode (den Reset-Punkt),
 * ab dem die Logs für den Count gezählt werden.
 *
 * Reset-Logik:
 *  - DAY / NDAYS: Periode ist [interval] Tage lang, beginnt am startDate und wiederholt sich.
 *    Reset-Punkt = startDate + k*interval Tage, wobei k so gewählt, dass der Punkt <= now
 *    und der nächste Punkt > now ist.
 *  - WEEK: Periode ist [interval] Wochen, resetted am [resetWeekday]. Woche beginnt an diesem
 *    Wochentag (z.B. Montag). Reset-Punkt = der letzte resetWeekday <= now (modulo interval Wochen).
 *  - MONTH: Periode ist [interval] Monate, resetted am [resetAnchorDay]. des Monats.
 *  - YEAR: Periode ist [interval] Jahre, resetted am [resetAnchorDay]. des [resetAnchorMonth].
 */
object HabitEngine {

    /**
     * Start der aktuellen Periode (Millis), ab dem gezählt wird.
     * Liegt immer <= now.
     */
    fun currentPeriodStart(habit: Habit, now: Long): Long {
        val nowCal = Calendar.getInstance(TimeZone.getDefault()).apply { timeInMillis = now }
        val computed = when (habit.cadenceType) {
            CadenceType.DAY -> anchoredDayStart(habit, nowCal)
            CadenceType.NDAYS -> anchoredDayStart(habit, nowCal)
            CadenceType.WEEK -> weekStart(habit, nowCal)
            CadenceType.MONTH -> monthStart(habit, nowCal)
            CadenceType.YEAR -> yearStart(habit, nowCal)
        }
        // Vor dem Startdatum gibt es keine Periode → Start ist startDate.
        return if (computed < habit.startDate) habit.startDate else computed
    }

    /**
     * Start der *nächsten* Periode nach der aktuellen (die [now] enthält).
     * Wird für „Periode abschließen & neustarten" gebraucht.
     */
    fun nextPeriodStart(habit: Habit, now: Long): Long {
        val current = currentPeriodStart(habit, now)
        val cal = Calendar.getInstance(TimeZone.getDefault()).apply { timeInMillis = current }
        when (habit.cadenceType) {
            CadenceType.DAY -> cal.add(Calendar.DAY_OF_MONTH, habit.interval)
            CadenceType.NDAYS -> cal.add(Calendar.DAY_OF_MONTH, habit.interval)
            CadenceType.WEEK -> cal.add(Calendar.WEEK_OF_YEAR, habit.interval)
            CadenceType.MONTH -> cal.add(Calendar.MONTH, habit.interval)
            CadenceType.YEAR -> cal.add(Calendar.YEAR, habit.interval)
        }
        return cal.timeInMillis
    }

    /**
     * Anzahl Logs in der aktuellen Periode. Liefert (count, periodStart, goal).
     */
    fun progress(habit: Habit, now: Long, countSinceProvider: (String, Long) -> Int): HabitProgress {
        val start = currentPeriodStart(habit, now)
        val count = countSinceProvider(habit.id, start)
        return HabitProgress(
            count = count,
            goal = habit.goalCount,
            periodStart = start
        )
    }

    // ---- DAY / NDAYS: alle [interval] Tage ab startDate ----
    private fun anchoredDayStart(habit: Habit, nowCal: Calendar): Long {
        val startCal = Calendar.getInstance(TimeZone.getDefault()).apply {
            timeInMillis = habit.startDate
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }
        val startMillis = startCal.timeInMillis
        if (nowCal.timeInMillis <= startMillis) return startMillis
        val dayMs = 24L * 60 * 60 * 1000
        val elapsedDays = (nowCal.timeInMillis - startMillis) / dayMs
        val periodsPassed = elapsedDays / habit.interval
        val periodStartDays = periodsPassed * habit.interval
        return startMillis + periodStartDays * dayMs
    }

    // ---- WEEK: reset am resetWeekday, alle [interval] Wochen ----
    private fun weekStart(habit: Habit, nowCal: Calendar): Long {
        val resetWd = habit.resetWeekday ?: Calendar.MONDAY
        // Zurück zum letzten resetWd <= now
        val cal = (nowCal.clone() as Calendar).apply {
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }
        // Differenz in Tagen bis zum letzten resetWd
        var diff = (cal.get(Calendar.DAY_OF_WEEK) - resetWd + 7) % 7
        cal.add(Calendar.DAY_OF_MONTH, -diff)
        // Jetzt haben wir den letzten resetWd (0:00). Bei interval>1 müssen wir noch
        // ganze Wochen zurückspringen, damit wir in der "richtigen" Woche landen.
        if (habit.interval > 1) {
            val startCal = Calendar.getInstance(TimeZone.getDefault()).apply {
                timeInMillis = habit.startDate
                set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0)
                set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
                // auf den resetWd der Startwoche normieren
                val d0 = (get(Calendar.DAY_OF_WEEK) - resetWd + 7) % 7
                add(Calendar.DAY_OF_MONTH, -d0)
            }
            val weeksBetween = weeksBetween(startCal, cal)
            val periodsPassed = weeksBetween / habit.interval
            val periodStartWeeks = periodsPassed * habit.interval
            cal.timeInMillis = startCal.timeInMillis
            cal.add(Calendar.WEEK_OF_YEAR, periodStartWeeks.toInt())
        }
        return cal.timeInMillis
    }

    private fun weeksBetween(a: Calendar, b: Calendar): Long {
        val aMs = a.timeInMillis
        val bMs = b.timeInMillis
        val dayMs = 24L * 60 * 60 * 1000
        return ((bMs - aMs) / dayMs) / 7
    }

    // ---- MONTH: reset am resetAnchorDay., alle [interval] Monate ----
    private fun monthStart(habit: Habit, nowCal: Calendar): Long {
        val anchorDay = habit.resetAnchorDay ?: 1
        val cal = (nowCal.clone() as Calendar).apply {
            set(Calendar.DAY_OF_MONTH, anchorDay)
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }
        if (cal.timeInMillis > nowCal.timeInMillis) {
            cal.add(Calendar.MONTH, -1)
        }
        // Bei interval>1: zurückspringen in die richtige Periode relativ zum startDate
        if (habit.interval > 1) {
            val startCal = Calendar.getInstance(TimeZone.getDefault()).apply {
                timeInMillis = habit.startDate
                set(Calendar.DAY_OF_MONTH, anchorDay)
                set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0)
                set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
                if (timeInMillis > habit.startDate) add(Calendar.MONTH, -1)
            }
            val monthsBetween = monthsBetween(startCal, cal)
            val periodsPassed = monthsBetween / habit.interval
            val periodStartMonths = periodsPassed * habit.interval
            cal.timeInMillis = startCal.timeInMillis
            cal.add(Calendar.MONTH, periodStartMonths.toInt())
        }
        return cal.timeInMillis
    }

    private fun monthsBetween(a: Calendar, b: Calendar): Long {
        val yearDiff = b.get(Calendar.YEAR) - a.get(Calendar.YEAR)
        val monthDiff = b.get(Calendar.MONTH) - a.get(Calendar.MONTH)
        return yearDiff * 12L + monthDiff
    }

    // ---- YEAR: reset am resetAnchorDay. des resetAnchorMonth, alle [interval] Jahre ----
    private fun yearStart(habit: Habit, nowCal: Calendar): Long {
        val anchorDay = habit.resetAnchorDay ?: 1
        val anchorMonth = (habit.resetAnchorMonth ?: 0) - 1 // 0-based für Calendar
        val cal = (nowCal.clone() as Calendar).apply {
            set(Calendar.MONTH, anchorMonth)
            set(Calendar.DAY_OF_MONTH, anchorDay)
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }
        if (cal.timeInMillis > nowCal.timeInMillis) {
            cal.add(Calendar.YEAR, -1)
        }
        if (habit.interval > 1) {
            val startCal = Calendar.getInstance(TimeZone.getDefault()).apply {
                timeInMillis = habit.startDate
                set(Calendar.MONTH, anchorMonth)
                set(Calendar.DAY_OF_MONTH, anchorDay)
                set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0)
                set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
                if (timeInMillis > habit.startDate) add(Calendar.YEAR, -1)
            }
            val yearsBetween = (cal.get(Calendar.YEAR) - startCal.get(Calendar.YEAR)).toLong()
            val periodsPassed = yearsBetween / habit.interval
            val periodStartYears = periodsPassed * habit.interval
            cal.timeInMillis = startCal.timeInMillis
            cal.add(Calendar.YEAR, periodStartYears.toInt())
        }
        return cal.timeInMillis
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
