package com.earendil.todonotes.data.repo

import com.earendil.todonotes.data.TodoNotesDatabase
import com.earendil.todonotes.data.entity.Todo
import com.earendil.todonotes.notification.AlarmScheduler
import kotlinx.coroutines.flow.Flow

/**
 * Repository für Todos (M7a — commonMain).
 *
 * Änderungen zur alten Android-Version:
 *  - Context → TodoNotesDatabase injected (Service-Locator)
 *  - java.util.UUID → kotlin.uuid.Uuid
 *  - System.currentTimeMillis() → Clock.System.now() (via [nowMs])
 *  - AlarmScheduler (static) → injected Interface
 *
 * Semantik bleibt 1:1 wie die alte App.
 */
class TodoRepository(
    private val db: TodoNotesDatabase,
    private val alarmScheduler: AlarmScheduler
) {
    private val dao = db.todoDao()

    fun observeOpenTodos(): Flow<List<Todo>> = dao.observeOpenTodos()
    fun observeCompletedTodos(): Flow<List<Todo>> = dao.observeCompletedTodos()

    suspend fun createTodo(
        title: String,
        notes: String = "",
        dueAt: Long?,
        recurrence: String? = null,
        logToHistory: Boolean = true
    ): Todo {
        val now = nowMs()
        val todo = Todo(
            id = randomUuidString(),
            title = title.trim(),
            notes = notes.trim(),
            dueAt = dueAt,
            recurrence = recurrence,
            createdAt = now,
            updatedAt = now,
            logToHistory = logToHistory
        )
        dao.upsert(todo)
        scheduleAlarmFor(todo)
        return todo
    }

    /**
     * Todo abhaken:
     * - completedAt setzen
     * - Falls logToHistory false → soft-delete (verschwindet aus Liste, nicht im Verlauf)
     * - Falls recurrence gesetzt → nächste Occurrence berechnen und als neues offenes Todo anlegen
     * - Alarm für das alte Todo canceln
     *
     * RRULE-Semantik (M6): nächste Occurrence streng nach max(fromDue, now) —
     * überfällige Todos springen in die Zukunft statt einzeln abgehakt zu werden.
     */
    suspend fun completeTodo(id: String) {
        val now = nowMs()
        val todo = dao.getById(id) ?: return
        alarmScheduler.cancelAlarm(id, todo.dueAt ?: now)

        if (todo.logToHistory) {
            dao.markCompleted(id, now)
        } else {
            dao.softDelete(id, now)
        }

        // Wiederkehrende Aufgabe: nächste Occurrence neu einplanen
        todo.recurrence?.let { rrule ->
            val nextDue = RecurrenceEngine.nextOccurrence(rrule, todo.dueAt, now)
            if (nextDue != null) {
                createTodo(
                    title = todo.title,
                    notes = todo.notes,
                    dueAt = nextDue,
                    recurrence = rrule,
                    logToHistory = todo.logToHistory
                )
            }
        }
    }

    /** Todo wieder öffnen (Verlauf → offene Liste). */
    suspend fun reopenTodo(id: String) {
        val now = nowMs()
        val todo = dao.getById(id) ?: return
        dao.upsert(todo.copy(completedAt = null, updatedAt = now))
        scheduleAlarmFor(todo.copy(completedAt = null))
    }

    suspend fun softDelete(id: String) {
        val todo = dao.getById(id) ?: return
        alarmScheduler.cancelAlarm(id, todo.dueAt ?: nowMs())
        dao.softDelete(id, nowMs())
    }

    suspend fun updateTodo(todo: Todo) {
        dao.upsert(todo.copy(updatedAt = nowMs()))
        alarmScheduler.cancelAlarm(todo.id, todo.dueAt ?: nowMs())
        scheduleAlarmFor(todo)
    }

    /** Bearbeiten über Form-Daten (wie im UI eingegeben). */
    suspend fun updateTodoFromForm(
        id: String,
        title: String,
        notes: String,
        dueAt: Long?,
        recurrence: String?,
        logToHistory: Boolean
    ) {
        val existing = dao.getById(id) ?: return
        val oldDue = existing.dueAt ?: nowMs()
        alarmScheduler.cancelAlarm(id, oldDue)
        val updated = existing.copy(
            title = title.trim(),
            notes = notes.trim(),
            dueAt = dueAt,
            recurrence = recurrence,
            logToHistory = logToHistory,
            updatedAt = nowMs()
        )
        dao.upsert(updated)
        scheduleAlarmFor(updated)
    }

    private fun scheduleAlarmFor(todo: Todo) {
        val due = todo.dueAt ?: return
        alarmScheduler.scheduleAlarm(due, todo.id, todo.title, todo.notes)
    }
}
