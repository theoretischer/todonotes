package com.earendil.todonotes.data.repo

import com.earendil.todonotes.data.TodoNotesDatabase
import com.earendil.todonotes.data.entity.CadenceType
import com.earendil.todonotes.data.entity.Habit
import com.earendil.todonotes.data.entity.HabitHistoryEntry
import com.earendil.todonotes.data.entity.HabitLog
import kotlinx.coroutines.flow.Flow

/**
 * Repository für Habits (M7a — commonMain).
 */
class HabitRepository(private val db: TodoNotesDatabase) {

    private val dao = db.habitDao()

    fun observeHabits(): Flow<List<Habit>> = dao.observeHabits()

    fun observeHabitHistory(): Flow<List<HabitHistoryEntry>> = dao.observeHabitHistory()

    /** Alle aktiven Habits einmalig (nicht reaktiv, für Perioden-Check). */
    suspend fun getAllActiveHabits(): List<Habit> = dao.getAllHabitsOnce()

    suspend fun getById(id: String): Habit? = dao.getById(id)

    suspend fun createHabit(habit: Habit): Habit {
        dao.upsert(habit)
        return habit
    }

    suspend fun updateHabit(habit: Habit) {
        dao.upsert(habit.copy(updatedAt = nowMs()))
    }

    suspend fun deleteHabit(id: String) {
        dao.softDelete(id, nowMs())
    }

    /** Verlaufseintrag löschen (Swipe-to-delete im Verlauf-Tab). */
    suspend fun deleteHistoryEntry(id: String) {
        dao.deleteHistoryEntry(id)
    }

    /** +1: neuen Log-Eintrag für jetzt anlegen. */
    suspend fun logHabit(habitId: String, now: Long = nowMs()) {
        dao.insertLog(
            HabitLog(id = randomUuidString(), habitId = habitId, timestamp = now)
        )
    }

    /** Letzten Log der aktuellen Periode löschen (Undo des letzten +1). */
    suspend fun undoLatestLog(habitId: String, now: Long = nowMs()) {
        val habit = dao.getById(habitId) ?: return
        val periodStart = HabitEngine.currentPeriodStart(habit, now)
        dao.deleteLatestLogSince(habitId, periodStart)
    }

    /**
     * Schließt die aktuelle Periode manuell ab („Periode abschließen & neustarten"):
     *  - legt einen Verlaufseintrag mit dem aktuellen Count an (wenn logToHistory)
     *  - löscht alle Logs der aktuellen Periode (Counter → 0)
     *  - setzt lastLoggedPeriodStart auf den nächsten Periodenstart
     */
    suspend fun forceFinishCurrentPeriod(habitId: String, now: Long = nowMs()) {
        val habit = dao.getById(habitId) ?: return
        val currentStart = HabitEngine.currentPeriodStart(habit, now)
        val nextStart = HabitEngine.nextPeriodStart(habit, now)
        val count = dao.countBetween(habit.id, currentStart, now + 1)
        if (habit.logToHistory) {
            dao.insertHistory(
                HabitHistoryEntry(
                    id = randomUuidString(),
                    habitId = habit.id,
                    title = habit.title,
                    cadenceLabel = cadenceLabel(habit),
                    periodStart = currentStart,
                    count = count,
                    goal = habit.goalCount,
                    loggedAt = now
                )
            )
        }
        // Logs der aktuellen Periode löschen (Counter reset).
        dao.deleteLogsSince(habit.id, currentStart)
        // WICHTIG: dao.update (nicht upsert/REPLACE), sonst löscht CASCADE die History.
        dao.update(habit.copy(lastLoggedPeriodStart = nextStart, updatedAt = now))
    }

    /** Schließt die aktuelle Periode für ALLE aktiven Habits ab. */
    suspend fun forceFinishAll(now: Long = nowMs()) {
        dao.getAllHabitsOnce().forEach { forceFinishCurrentPeriod(it.id, now) }
    }

    /** Aktueller Count in der Periode, die [now] enthält. */
    suspend fun currentCount(habit: Habit, now: Long = nowMs()): Int {
        val start = HabitEngine.currentPeriodStart(habit, now)
        return dao.countSince(habit.id, start)
    }

    /** Reaktiver Count in der Periode, die [now] enthält — feuert neu, wenn
     *  habit_logs sich ändert (z.B. +1 gedrückt). Für die Progress-Anzeige im UI.
     *  M7c-Fix: ersetzt den N×2 suspend-Query-Bottleneck im HabitViewModel. */
    fun observeCurrentCount(habit: Habit, now: Long = nowMs()): Flow<Int> {
        val start = HabitEngine.currentPeriodStart(habit, now)
        return dao.observeCountSince(habit.id, start)
    }

    /**
     * Erkennt einen Periodenwechsel seit dem letzten protokollierten Periodenstart
     * und legt – falls habit.logToHistory – für jede abgelaufene Periode einen
     * Verlaufseintrag an. Aktualisiert dabei habit.lastLoggedPeriodStart.
     */
    suspend fun checkAndLogPeriodChange(habit: Habit, now: Long = nowMs()): Habit {
        val currentStart = HabitEngine.currentPeriodStart(habit, now)
        val lastLogged = habit.lastLoggedPeriodStart

        if (lastLogged == null) {
            val updated = habit.copy(lastLoggedPeriodStart = currentStart, updatedAt = now)
            dao.update(updated)
            return updated
        }
        if (currentStart <= lastLogged) return habit

        if (habit.logToHistory) {
            val countPrev = dao.countBetween(habit.id, lastLogged, currentStart)
            dao.insertHistory(
                HabitHistoryEntry(
                    id = randomUuidString(),
                    habitId = habit.id,
                    title = habit.title,
                    cadenceLabel = cadenceLabel(habit),
                    periodStart = lastLogged,
                    count = countPrev,
                    goal = habit.goalCount,
                    loggedAt = now
                )
            )
        }
        val updated = habit.copy(lastLoggedPeriodStart = currentStart, updatedAt = now)
        dao.update(updated)
        return updated
    }

    /** Kurzes Label wie "2x pro Woche" / "1x pro Tag" / "1x alle 3 Tage". */
    private fun cadenceLabel(habit: Habit): String {
        val per = when (habit.cadenceType) {
            CadenceType.DAY -> "Tag"
            CadenceType.WEEK -> "Woche"
            CadenceType.MONTH -> "Monat"
            CadenceType.YEAR -> "Jahr"
            CadenceType.NDAYS -> "${habit.interval} Tage"
        }
        return if (habit.cadenceType == CadenceType.NDAYS) {
            "${habit.goalCount}x alle $per"
        } else {
            "${habit.goalCount}x pro $per"
        }
    }
}
