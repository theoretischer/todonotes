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
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
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
 * Performance-Architektur (M7c-rev2 — Optimistic UI):
 *
 * Auf Wasm ist jede DB-Operation ein asynchroner Round-Trip zum Web Worker
 * (postMessage). Ein +1 besteht aus: INSERT (1 RT) + Room-Invalidation +
 * re-query COUNT (1 RT) = ~1s. Auf Android wäre SQLite synchron → sofort.
 *
 * Lösung: **Optimistic UI**. Die DB-backed Flow ([dbFlow]) liefert die
 * "source of truth" und speist [_state]. Aktionen wie +1 aktualisieren
 * [_state] SOFORT (gleicher Frame) — die DB schreibt asynchron nach, und
 * wenn der dbFlow die echte Zahl liefert, überschreibt er die optimistic.
 * Sollte die optimistic mit der echten übereinstimmen, merkt der Nutzer
 * nichts von der Verzögerung.
 *
 * Periodenwechsel (checkAndLogPeriodChange) wird beim App-Start geprüft
 * (DB-Schreibzugriff, nicht im Flow).
 */
class HabitViewModel(
    private val repo: HabitRepository,
    scope: CoroutineScope
) {
    private val vmScope = CoroutineScope(scope.coroutineContext + SupervisorJob())

    // DB-backed Flow: reaktive Habit-Liste + pro Habit ein reaktiver COUNT-Flow.
    // Feuert nur bei echten Tabellen-Änderungen (habits / habit_logs).
    @OptIn(ExperimentalCoroutinesApi::class)
    private val dbFlow = repo.observeHabits().flatMapLatest { habits ->
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
    }

    // Optimistic UI-State: wird von dbFlow gespeist, aber Aktionen mutieren
    // ihn sofort (optimistic). Der dbFlow korrigiert später mit der echten Zahl.
    private val _habitsWithProgress = MutableStateFlow<List<HabitWithProgress>>(emptyList())
    val habitsWithProgress: StateFlow<List<HabitWithProgress>> = _habitsWithProgress.asStateFlow()

    val habitHistory: StateFlow<List<HabitHistoryEntry>> =
        repo.observeHabitHistory().stateIn(vmScope, SharingStarted.Eagerly, emptyList())

    init {
        // dbFlow → _state (source of truth, überschreibt optimistic Updates).
        vmScope.launch {
            dbFlow.collect { list -> _habitsWithProgress.value = list }
        }
    }

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

    /** +1: Optimistic — Count sofort in UI erhöhen, DB schreibt asynchron nach. */
    fun logHabit(id: String) {
        _habitsWithProgress.update { list ->
            list.map { hwp ->
                if (hwp.habit.id == id) {
                    val p = hwp.progress
                    hwp.copy(progress = p.copy(count = p.count + 1))
                } else hwp
            }
        }
        vmScope.launch { repo.logHabit(id) }
    }

    /** -1 (Undo): Optimistic — Count sofort verringern (min 0). */
    fun undoLog(id: String) {
        _habitsWithProgress.update { list ->
            list.map { hwp ->
                if (hwp.habit.id == id) {
                    val p = hwp.progress
                    hwp.copy(progress = p.copy(count = (p.count - 1).coerceAtLeast(0)))
                } else hwp
            }
        }
        vmScope.launch { repo.undoLatestLog(id) }
    }

    /** Periode abschließen: Optimistic — Count auf 0 setzen. */
    fun finishCurrentPeriod(id: String) {
        _habitsWithProgress.update { list ->
            list.map { hwp ->
                if (hwp.habit.id == id) {
                    hwp.copy(progress = hwp.progress.copy(count = 0))
                } else hwp
            }
        }
        vmScope.launch { repo.forceFinishCurrentPeriod(id) }
    }

    fun deleteHabit(id: String) {
        // Optimistic: sofort aus Liste entfernen.
        _habitsWithProgress.update { list -> list.filter { it.habit.id != id } }
        vmScope.launch { repo.deleteHabit(id) }
    }

    fun deleteHistoryEntry(id: String) {
        vmScope.launch { repo.deleteHistoryEntry(id) }
    }
}
