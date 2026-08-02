package com.earendil.todonotes.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.earendil.todonotes.data.entity.Habit
import com.earendil.todonotes.data.entity.HabitHistoryEntry
import com.earendil.todonotes.data.repo.HabitEngine
import com.earendil.todonotes.data.repo.HabitProgress
import com.earendil.todonotes.data.repo.HabitRepository
import com.earendil.todonotes.ui.habits.HabitFormData
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.util.UUID

/** Habit + aktueller Fortschritt in der Periode, die "jetzt" enthält. */
data class HabitWithProgress(
    val habit: Habit,
    val progress: HabitProgress
)

class HabitViewModel(
    private val repo: HabitRepository
) : ViewModel() {

    // Tick, der nach jeder Log-Aktion hochgezählt wird → reaktiviert den combine-Flow.
    private val refreshTick = MutableStateFlow(0L)

    val habitsWithProgress: StateFlow<List<HabitWithProgress>> =
        combine(repo.observeHabits(), refreshTick) { habits, _ ->
            val now = System.currentTimeMillis()
            habits.map { habit ->
                // Periodenwechsel erkennen + ggf. Verlaufseintrag.
                val checked = repo.checkAndLogPeriodChange(habit, now)
                val count = repo.currentCount(checked, now)
                HabitWithProgress(checked, HabitProgress(count, checked.goalCount, 0L))
            }
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val habitHistory: StateFlow<List<HabitHistoryEntry>> =
        repo.observeHabitHistory().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    fun createHabit(form: HabitFormData) {
        viewModelScope.launch {
            val now = System.currentTimeMillis()
            // Reset-Anchor aus dem gewählten Startdatum ableiten.
            val anchorCal = java.util.Calendar.getInstance().apply { timeInMillis = form.startDate }
            val resetWeekday = if (form.cadenceType == com.earendil.todonotes.data.entity.CadenceType.WEEK)
                anchorCal.get(java.util.Calendar.DAY_OF_WEEK) else null
            val resetAnchorDay = if (form.cadenceType == com.earendil.todonotes.data.entity.CadenceType.MONTH ||
                form.cadenceType == com.earendil.todonotes.data.entity.CadenceType.YEAR)
                anchorCal.get(java.util.Calendar.DAY_OF_MONTH) else null
            val resetAnchorMonth = if (form.cadenceType == com.earendil.todonotes.data.entity.CadenceType.YEAR)
                anchorCal.get(java.util.Calendar.MONTH) + 1 else null

            val habit = Habit(
                id = UUID.randomUUID().toString(),
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
            refreshTick.value = System.nanoTime()
        }
    }

    fun updateHabit(id: String, form: HabitFormData) {
        viewModelScope.launch {
            val existing = repo.getById(id) ?: return@launch
            val anchorCal = java.util.Calendar.getInstance().apply { timeInMillis = form.startDate }
            val resetWeekday = if (form.cadenceType == com.earendil.todonotes.data.entity.CadenceType.WEEK)
                anchorCal.get(java.util.Calendar.DAY_OF_WEEK) else null
            val resetAnchorDay = if (form.cadenceType == com.earendil.todonotes.data.entity.CadenceType.MONTH ||
                form.cadenceType == com.earendil.todonotes.data.entity.CadenceType.YEAR)
                anchorCal.get(java.util.Calendar.DAY_OF_MONTH) else null
            val resetAnchorMonth = if (form.cadenceType == com.earendil.todonotes.data.entity.CadenceType.YEAR)
                anchorCal.get(java.util.Calendar.MONTH) + 1 else null

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
            refreshTick.value = System.nanoTime()
        }
    }

    /** +1: Log-Eintrag anlegen, dann Counts aktualisieren. */
    fun logHabit(id: String) {
        viewModelScope.launch {
            repo.logHabit(id)
            refreshTick.value = System.nanoTime()
        }
    }

    /** Letzten +1 in der aktuellen Periode rückgängig. */
    fun undoLog(id: String) {
        viewModelScope.launch {
            repo.undoLatestLog(id)
            refreshTick.value = System.nanoTime()
        }
    }

    fun deleteHabit(id: String) {
        viewModelScope.launch {
            repo.deleteHabit(id)
            refreshTick.value = System.nanoTime()
        }
    }

    /** Schließt die aktuelle Periode EINES Habits ab → Verlauf + Counter reset. */
    fun finishCurrentPeriod(id: String) {
        viewModelScope.launch {
            repo.forceFinishCurrentPeriod(id)
            refreshTick.value = System.nanoTime()
        }
    }

    class Factory(private val repo: HabitRepository) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T = HabitViewModel(repo) as T
    }
}
