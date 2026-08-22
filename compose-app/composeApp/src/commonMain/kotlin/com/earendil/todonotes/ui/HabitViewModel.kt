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
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
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
 * Performance-Fix (M7c-rev): Der alte combine+refreshTick-Ansatz hat pro
 * Habit 2 suspend-DB-Queries durch den Web Worker gemacht (jeder ein
 * asynchroner Round-Trip). Bei 3 Habits = 6 Round-Trips = ~2s Verzögerung.
 *
 * Neuer Ansatz: observeHabits() liefert die Habit-Liste reaktiv. Pro Habit
 * wird observeCurrentCount() als Flow gesammelt — das ist eine reaktive
 * COUNT-Query, die nur feuert wenn habit_logs sich ändert. Alles über
 * flatMapLatest verknüpft, ein einziger Flow der pro Habit einen Sub-Flow
 * hat. Kein refreshTick mehr nötig.
 *
 * Periodenwechsel (checkAndLogPeriodChange) wird NICHT im Flow gemacht
 * (würde DB-Schreibzugriff bei jedem Collect verursachen), sondern beim
 * App-Start / wenn der Habits-Tab geöffnet wird.
 */
class HabitViewModel(
    private val repo: HabitRepository,
    scope: CoroutineScope
) {
    private val vmScope = CoroutineScope(scope.coroutineContext + SupervisorJob())

    @OptIn(ExperimentalCoroutinesApi::class)
    val habitsWithProgress: StateFlow<List<HabitWithProgress>> =
        repo.observeHabits().flatMapLatest { habits ->
            // Pro Habit: observeCurrentCount → HabitWithProgress.
            // Wenn habits leer ist, ein leerer Flow.
            if (habits.isEmpty()) {
                kotlinx.coroutines.flow.flowOf(emptyList())
            } else {
                combine(
                    habits.map { habit ->
                        repo.observeCurrentCount(habit).map { count ->
                            HabitWithProgress(
                                habit,
                                HabitProgress(count, habit.goalCount, 0L)
                            )
                        }
                    }
                ) { it.toList() }
            }
        }.stateIn(vmScope, SharingStarted.Eagerly, emptyList())

    val habitHistory: StateFlow<List<HabitHistoryEntry>> =
        repo.observeHabitHistory().stateIn(vmScope, SharingStarted.Eagerly, emptyList())

    /** Periodenwechsel für alle Habits prüfen (beim App-Start / Tab-Öffnen).
     *  Legt ggf. Verlaufseinträge an. NICHT im Flow — macht DB-Schreibzugriffe. */
    fun checkPeriodsOnStart() {
        vmScope.launch {
            val now = nowMs()
            repo.getAllActiveHabits().forEach { habit ->
                repo.checkAndLogPeriodChange(habit, now)
            }
        }
    }

    fun createHabit(form: HabitFormData) {
        vmScope.launch {
            val now = nowMs()
            val tz = TimeZone.currentSystemDefault()
            val startDateLdt = Instant.fromEpochMilliseconds(form.startDate).toLocalDateTime(tz)

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
        }
    }

    /** +1: Log-Eintrag anlegen. Flow feuert reaktiv → UI aktualisiert sofort. */
    fun logHabit(id: String) {
        vmScope.launch { repo.logHabit(id) }
    }

    /** Letzten +1 in der aktuellen Periode rückgängig. */
    fun undoLog(id: String) {
        vmScope.launch { repo.undoLatestLog(id) }
    }

    fun deleteHabit(id: String) {
        vmScope.launch { repo.deleteHabit(id) }
    }

    fun deleteHistoryEntry(id: String) {
        vmScope.launch { repo.deleteHistoryEntry(id) }
    }

    fun finishCurrentPeriod(id: String) {
        vmScope.launch { repo.forceFinishCurrentPeriod(id) }
    }
}
