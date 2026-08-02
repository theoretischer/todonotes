package com.earendil.todonotes.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.earendil.todonotes.data.entity.Todo
import com.earendil.todonotes.data.repo.TodoRepository
import com.earendil.todonotes.ui.todos.TodoFormData
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class TodoViewModel(
    private val repo: TodoRepository
) : ViewModel() {

    val openTodos: StateFlow<List<Todo>> =
        repo.observeOpenTodos().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val completedTodos: StateFlow<List<Todo>> =
        repo.observeCompletedTodos().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    fun createTodo(form: TodoFormData) {
        viewModelScope.launch {
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
        viewModelScope.launch {
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
        viewModelScope.launch { repo.completeTodo(id) }
    }

    fun reopenTodo(id: String) {
        viewModelScope.launch { repo.reopenTodo(id) }
    }

    fun deleteTodo(id: String) {
        viewModelScope.launch { repo.softDelete(id) }
    }

    class Factory(private val repo: TodoRepository) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T = TodoViewModel(repo) as T
    }
}
