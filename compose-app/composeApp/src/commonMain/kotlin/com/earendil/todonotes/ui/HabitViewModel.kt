package com.earendil.todonotes.ui

import com.earendil.todonotes.data.entity.CadenceType
import com.earendil.todonotes.data.entity.Habit
import com.earendil.todonotes.data.entity.HabitHistoryEntry
import com.earendil.todonotes.data.repo.HabitEngine
import com.earendil.todonotes.data.repo.HabitProgress
import com.earendil.todonotes.data.repo.HabitRepository
import com.earendil.todonotes.data.repo.nowMs
import com.earendil.todonotes.data.repo.randomUuidString
import com.earendil.todonotes.ui.habits.HabitFormData
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.datetime.Instant
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime

/** Habit + aktueller Fortschritt in der Periode, die "jetzt" enthält. */
data class HabitWithProgress(
    val habit: Habit,
    val progress: HabitProgress
)

/**
 * Plain-Kotlin-ViewModel für Habits (M7c — commonMain).
 *
 * Kein androidx.lifecycle.ViewModel — stattdessen eigene CoroutineScope.
 * Nutzt kotlinx-datetime statt java.util.Calendar und randomUuidString()
 * statt java.util.UUID.
 */
class HabitViewModel(
    private val repo: HabitRepository,
    scope: CoroutineScope
) {
    private val vmScope = CoroutineScope(scope.coroutineContext + SupervisorJob())

    // Tick, der nach jeder Log-Aktion hochgezählt wird → reaktiviert den combine-Flow.
    private val refreshTick = MutableStateFlow(0L)

    val habitsWithProgress: StateFlow<List<HabitWithProgress>> =
        combine(repo.observeHabits(), refreshTick) { habits, _ ->
            val now = nowMs()
            habits.map { habit ->
                // Periodenwechsel erkennen + ggf. Verlaufseintrag.
                val checked = repo.checkAndLogPeriodChange(habit, now)
                val count = repo.currentCount(checked, now)
                HabitWithProgress(checked, HabitProgress(count, checked.goalCount, 0L))
            }
        }.stateIn(vmScope, SharingStarted.Eagerly, emptyList())

    val habitHistory: StateFlow<List<HabitHistoryEntry>> =
        repo.observeHabitHistory().stateIn(vmScope, SharingStarted.Eagerly, emptyList())

    fun createHabit(form: HabitFormData) {
        vmScope.launch {
            val now = nowMs()
            val tz = TimeZone.currentSystemDefault()
            val startDateLdt = Instant.fromEpochMilliseconds(form.startDate).toLocalDateTime(tz)

            // Reset-Anchor aus dem gewählten Startdatum ableiten.
            // resetWeekday: Calendar-Nummerierung (1=SO, 2=MO, ..., 7=SA), wie in der DB gespeichert.
            // kotlinx DayOfWeek: MONDAY=0..SUNDAY=6 → Calendar = (ordinal+2)%7+1
            val resetWeekday = if (form.cadenceType == CadenceType.WEEK)
                (startDateLdt.dayOfWeek.ordinal + 2) % 7 + 1 else null
            val resetAnchorDay = if (form.cadenceType == CadenceType.MONTH ||
                form.cadenceType == CadenceType.YEAR)
                startDateLdt.dayOfMonth else null
            val resetAnchorMonth = if (form.cadenceType == CadenceType.YEAR)
                startDateLdt.monthNumber else null

            val habit = Habit(
                id = randomUuidString(),
                title = form.title,
                notes = form.notes,
                cadenceType = form.cadenceType,
                interval = form.interval,
                resetWeekday = resetWeekday,
                resetAnchorDay = resetAnchorDay,
                resetAnchorMonth = resetAnchorMonth,
                goalCount = form.goalCount,
                startDate = form.startDate,
                logToHistory = form.logToHistory,
                createdAt = now,
                updatedAt = now
            )
            repo.createHabit(habit)
            refreshTick.value = nowMs()
        }
    }

    fun updateHabit(id: String, form: HabitFormData) {
        vmScope.launch {
            val existing = repo.getById(id) ?: return@launch
            val tz = TimeZone.currentSystemDefault()
            val startDateLdt = Instant.fromEpochMilliseconds(form.startDate).toLocalDateTime(tz)

            val resetWeekday = if (form.cadenceType == CadenceType.WEEK)
                (startDateLdt.dayOfWeek.ordinal + 2) % 7 + 1 else null
            val resetAnchorDay = if (form.cadenceType == CadenceType.MONTH ||
                form.cadenceType == CadenceType.YEAR)
                startDateLdt.dayOfMonth else null
            val resetAnchorMonth = if (form.cadenceType == CadenceType.YEAR)
                startDateLdt.monthNumber else null

            repo.updateHabit(
                existing.copy(
                    title = form.title,
                    notes = form.notes,
                    cadenceType = form.cadenceType,
                    interval = form.interval,
                    resetWeekday = resetWeekday,
                    resetAnchorDay = resetAnchorDay,
                    resetAnchorMonth = resetAnchorMonth,
                    goalCount = form.goalCount,
                    startDate = form.startDate,
                    logToHistory = form.logToHistory
                )
            )
            refreshTick.value = nowMs()
        }
    }

    /** +1: Log-Eintrag anlegen, dann Counts aktualisieren. */
    fun logHabit(id: String) {
        vmScope.launch {
            repo.logHabit(id)
            refreshTick.value = nowMs()
        }
    }

    /** Letzten +1 in der aktuellen Periode rückgängig. */
    fun undoLog(id: String) {
        vmScope.launch {
            repo.undoLatestLog(id)
            refreshTick.value = nowMs()
        }
    }

    fun deleteHabit(id: String) {
        vmScope.launch {
            repo.deleteHabit(id)
            refreshTick.value = nowMs()
        }
    }

    /** Verlaufseintrag loeschen (Swipe im Verlauf-Tab). */
    fun deleteHistoryEntry(id: String) {
        vmScope.launch {
            repo.deleteHistoryEntry(id)
            refreshTick.value = nowMs()
        }
    }

    /** Schließt die aktuelle Periode EINES Habits ab → Verlauf + Counter reset. */
    fun finishCurrentPeriod(id: String) {
        vmScope.launch {
            repo.forceFinishCurrentPeriod(id)
            refreshTick.value = nowMs()
        }
    }
}
