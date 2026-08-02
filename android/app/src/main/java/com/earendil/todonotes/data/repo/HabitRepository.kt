package com.earendil.todonotes.data.repo

import android.content.Context
import android.util.Log
import com.earendil.todonotes.data.TodoNotesDatabase
import com.earendil.todonotes.data.entity.Habit
import com.earendil.todonotes.data.entity.HabitHistoryEntry
import com.earendil.todonotes.data.entity.HabitLog
import kotlinx.coroutines.flow.Flow
import java.util.UUID

class HabitRepository(context: Context) {

    private val dao = TodoNotesDatabase.get(context).habitDao()

    fun observeHabits(): Flow<List<Habit>> = dao.observeHabits()

    fun observeHabitHistory(): Flow<List<HabitHistoryEntry>> = dao.observeHabitHistory()

    suspend fun getById(id: String): Habit? = dao.getById(id)

    suspend fun createHabit(habit: Habit): Habit {
        dao.upsert(habit)
        return habit
    }

    suspend fun updateHabit(habit: Habit) {
        dao.upsert(habit.copy(updatedAt = System.currentTimeMillis()))
    }

    suspend fun deleteHabit(id: String) {
        dao.softDelete(id, System.currentTimeMillis())
    }

    /** +1: neuen Log-Eintrag für jetzt anlegen. */
    suspend fun logHabit(habitId: String, now: Long = System.currentTimeMillis()) {
        dao.insertLog(
            HabitLog(id = UUID.randomUUID().toString(), habitId = habitId, timestamp = now)
        )
    }

    /** Letzten Log der aktuellen Periode löschen (Undo des letzten +1). */
    suspend fun undoLatestLog(habitId: String, now: Long = System.currentTimeMillis()) {
        val habit = dao.getById(habitId) ?: return
        val periodStart = HabitEngine.currentPeriodStart(habit, now)
        dao.deleteLatestLogSince(habitId, periodStart)
    }

    /**
     * Schließt die aktuelle Periode manuell ab („Periode abschließen & neustarten"):
     *  - legt einen Verlaufseintrag mit dem aktuellen Count an (wenn logToHistory)
     *  - löscht alle Logs der aktuellen Periode (Counter → 0)
     *  - setzt lastLoggedPeriodStart auf den nächsten Periodenstart
     * Nach dieser Aktion startet das Habit bei 0 in der neuen Periode.
     */
    suspend fun forceFinishCurrentPeriod(habitId: String, now: Long = System.currentTimeMillis()) {
        val habit = dao.getById(habitId) ?: run {
            android.util.Log.i("HabitRepo", "forceFinish: habit $habitId nicht gefunden")
            return
        }
        val currentStart = HabitEngine.currentPeriodStart(habit, now)
        val nextStart = HabitEngine.nextPeriodStart(habit, now)
        val count = dao.countBetween(habit.id, currentStart, now + 1)
        android.util.Log.i("HabitRepo", "forceFinish: habit=${habit.title} logToHistory=${habit.logToHistory} count=$count currentStart=$currentStart nextStart=$nextStart")
        if (habit.logToHistory) {
            dao.insertHistory(
                HabitHistoryEntry(
                    id = UUID.randomUUID().toString(),
                    habitId = habit.id,
                    title = habit.title,
                    cadenceLabel = cadenceLabel(habit),
                    periodStart = currentStart,
                    count = count,
                    goal = habit.goalCount,
                    loggedAt = now
                )
            )
            android.util.Log.i("HabitRepo", "forceFinish: History-Eintrag angelegt für ${habit.title}")
        } else {
            android.util.Log.i("HabitRepo", "forceFinish: KEIN History-Eintrag (logToHistory=false) für ${habit.title}")
        }
        // Logs der aktuellen Periode löschen (Counter reset).
        dao.deleteLogsSince(habit.id, currentStart)
        // Nächste Periode als „bereits protokolliert" markieren.
        // WICHTIG: dao.update (nicht upsert/REPLACE), sonst löscht CASCADE die History.
        dao.update(habit.copy(lastLoggedPeriodStart = nextStart, updatedAt = now))
    }

    /** Schließt die aktuelle Periode für ALLE aktiven Habits ab. */
    suspend fun forceFinishAll(now: Long = System.currentTimeMillis()) {
        dao.getAllHabitsOnce().forEach { forceFinishCurrentPeriod(it.id, now) }
    }

    /** Aktueller Count in der Periode, die [now] enthält. */
    suspend fun currentCount(habit: Habit, now: Long = System.currentTimeMillis()): Int {
        val start = HabitEngine.currentPeriodStart(habit, now)
        return dao.countSince(habit.id, start)
    }

    /**
     * Erkennt einen Periodenwechsel seit dem letzten protokollierten Periodenstart
     * und legt – falls habit.logToHistory – für jede abgelaufene Periode einen
     * Verlaufseintrag an. Aktualisiert dabei habit.lastLoggedPeriodStart.
     *
     * Wird beim Laden der Habits im ViewModel aufgerufen.
     *
     * @return das evtl. aktualisierte Habit (mit neuem lastLoggedPeriodStart).
     */
    suspend fun checkAndLogPeriodChange(habit: Habit, now: Long = System.currentTimeMillis()): Habit {
        val currentStart = HabitEngine.currentPeriodStart(habit, now)
        val lastLogged = habit.lastLoggedPeriodStart

        // Noch nie geloggt: nur merken, nichts loggen (erste Periode läuft noch).
        if (lastLogged == null) {
            val updated = habit.copy(lastLoggedPeriodStart = currentStart, updatedAt = now)
            dao.update(updated)
            return updated
        }

        // Kein Wechsel → nichts tun.
        if (currentStart <= lastLogged) return habit

        // Periodenwechsel: für die abgelaufene Periode [lastLogged, currentStart)
        // den Count ermitteln und History-Eintrag anlegen.
        if (habit.logToHistory) {
            val countPrev = dao.countBetween(habit.id, lastLogged, currentStart)
            dao.insertHistory(
                HabitHistoryEntry(
                    id = UUID.randomUUID().toString(),
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
            com.earendil.todonotes.data.entity.CadenceType.DAY -> "Tag"
            com.earendil.todonotes.data.entity.CadenceType.WEEK -> "Woche"
            com.earendil.todonotes.data.entity.CadenceType.MONTH -> "Monat"
            com.earendil.todonotes.data.entity.CadenceType.YEAR -> "Jahr"
            com.earendil.todonotes.data.entity.CadenceType.NDAYS -> "${habit.interval} Tage"
        }
        return if (habit.cadenceType == com.earendil.todonotes.data.entity.CadenceType.NDAYS) {
            "${habit.goalCount}x alle $per"
        } else {
            "${habit.goalCount}x pro $per"
        }
    }
}
