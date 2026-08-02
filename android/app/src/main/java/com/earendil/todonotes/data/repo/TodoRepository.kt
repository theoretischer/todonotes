package com.earendil.todonotes.data.repo

import android.content.Context
import android.util.Log
import com.earendil.todonotes.data.TodoNotesDatabase
import com.earendil.todonotes.data.entity.Todo
import com.earendil.todonotes.notification.AlarmScheduler
import kotlinx.coroutines.flow.Flow
import java.util.UUID

class TodoRepository(private val context: Context) {

    private val dao = TodoNotesDatabase.get(context).todoDao()

    fun observeOpenTodos(): Flow<List<Todo>> = dao.observeOpenTodos()
    fun observeCompletedTodos(): Flow<List<Todo>> = dao.observeCompletedTodos()

    suspend fun createTodo(
        title: String,
        notes: String = "",
        dueAt: Long?,
        recurrence: String? = null,
        logToHistory: Boolean = true
    ): Todo {
        val now = System.currentTimeMillis()
        val todo = Todo(
            id = UUID.randomUUID().toString(),
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
     */
    suspend fun completeTodo(id: String) {
        val now = System.currentTimeMillis()
        val todo = dao.getById(id) ?: return
        AlarmScheduler.cancelAlarm(context, id, todo.dueAt ?: now)

        if (todo.logToHistory) {
            dao.markCompleted(id, now)
        } else {
            dao.softDelete(id, now)
        }

        // Wiederkehrende Aufgabe: nächste Occurrence neu einplanen
        todo.recurrence?.let { rrule ->
            Log.i("TodoRepository", "completeTodo recurrence: rrule=$rrule fromDue(todo.dueAt)=${todo.dueAt} now=$now")
            val nextDue = RecurrenceEngine.nextOccurrence(rrule, todo.dueAt, now)
            Log.i("TodoRepository", "completeTodo nextDue=$nextDue")
            if (nextDue != null) {
                createTodo(
                    title = todo.title,
                    notes = todo.notes,
                    dueAt = nextDue,
                    recurrence = rrule,
                    logToHistory = todo.logToHistory
                )
            } else {
                Log.i("TodoRepository", "RRULE hat keine weitere Occurrence: $rrule")
            }
        }
    }

    /** Todo wieder öffnen (Verlauf → offene Liste). */
    suspend fun reopenTodo(id: String) {
        val now = System.currentTimeMillis()
        val todo = dao.getById(id) ?: return
        dao.upsert(todo.copy(completedAt = null, updatedAt = now))
        scheduleAlarmFor(todo.copy(completedAt = null))
    }

    suspend fun softDelete(id: String) {
        val todo = dao.getById(id) ?: return
        AlarmScheduler.cancelAlarm(context, id, todo.dueAt ?: System.currentTimeMillis())
        dao.softDelete(id, System.currentTimeMillis())
    }

    suspend fun updateTodo(todo: Todo) {
        dao.upsert(todo.copy(updatedAt = System.currentTimeMillis()))
        // Alarm neu planen falls sich Fälligkeit geändert hat
        AlarmScheduler.cancelAlarm(context, todo.id, todo.dueAt ?: System.currentTimeMillis())
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
        val oldDue = existing.dueAt ?: System.currentTimeMillis()
        AlarmScheduler.cancelAlarm(context, id, oldDue)
        val updated = existing.copy(
            title = title.trim(),
            notes = notes.trim(),
            dueAt = dueAt,
            recurrence = recurrence,
            logToHistory = logToHistory,
            updatedAt = System.currentTimeMillis()
        )
        dao.upsert(updated)
        scheduleAlarmFor(updated)
    }

    private fun scheduleAlarmFor(todo: Todo) {
        val due = todo.dueAt ?: return
        AlarmScheduler.scheduleAlarm(context, due, todo.id, todo.title, todo.notes)
    }
}
