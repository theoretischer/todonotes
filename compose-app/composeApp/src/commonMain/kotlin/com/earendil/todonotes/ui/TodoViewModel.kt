package com.earendil.todonotes.ui

import com.earendil.todonotes.data.entity.Todo
import com.earendil.todonotes.data.repo.TodoRepository
import com.earendil.todonotes.ui.todos.TodoFormData
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/**
 * Plain-Kotlin-ViewModel für Todos (M7b — commonMain).
 *
 * Kein androidx.lifecycle.ViewModel — stattdessen eigene CoroutineScope.
 * Die DB-Flows leben im Repository; das ViewModel ist nur ein dünner
 * State-Holder + Action-Dispatcher. Die Scope wird vom AppContainer
 * bereitgestellt und bei Bedarf gecancelt.
 */
class TodoViewModel(
    private val repo: TodoRepository,
    scope: CoroutineScope
) {
    private val vmScope = CoroutineScope(scope.coroutineContext + SupervisorJob())

    val openTodos: StateFlow<List<Todo>> =
        repo.observeOpenTodos().stateIn(vmScope, SharingStarted.Eagerly, emptyList())

    val completedTodos: StateFlow<List<Todo>> =
        repo.observeCompletedTodos().stateIn(vmScope, SharingStarted.Eagerly, emptyList())

    fun createTodo(form: TodoFormData) {
        vmScope.launch {
            repo.createTodo(
                title = form.title,
                notes = form.notes,
                dueAt = form.dueAt,
                recurrence = form.recurrence,
                logToHistory = form.logToHistory
            )
        }
    }

    fun updateTodo(id: String, form: TodoFormData) {
        vmScope.launch {
            repo.updateTodoFromForm(
                id = id,
                title = form.title,
                notes = form.notes,
                dueAt = form.dueAt,
                recurrence = form.recurrence,
                logToHistory = form.logToHistory
            )
        }
    }

    fun completeTodo(id: String) {
        vmScope.launch { repo.completeTodo(id) }
    }

    fun reopenTodo(id: String) {
        vmScope.launch { repo.reopenTodo(id) }
    }

    fun deleteTodo(id: String) {
        vmScope.launch { repo.softDelete(id) }
    }
}
