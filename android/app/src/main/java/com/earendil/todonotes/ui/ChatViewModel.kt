package com.earendil.todonotes.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.earendil.todonotes.data.entity.ChatMessage
import com.earendil.todonotes.data.repo.ChatMessageRepository
import com.earendil.todonotes.data.repo.NoteRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/**
 * Zustand für den Chat-Bildschirm (Block H3).
 *
 * @param noteId    Chat-Notiz-Id
 * @param title     Chat-Name (für die TopBar)
 * @param loaded    false während initialen Ladens
 */
data class ChatState(
    val noteId: String? = null,
    val title: String = "",
    val loaded: Boolean = false
)

/**
 * ViewModel für den Chat-Bildschirm (Block H3).
 *
 * Observiert reaktiv alle Nachrichten der Chat-Notiz (älteste zuerst).
 * Senden/Bearbeiten/Löschen läuft über [ChatMessageRepository].
 */
class ChatViewModel(
    private val chatRepo: ChatMessageRepository,
    private val noteRepo: NoteRepository
) : ViewModel() {

    private val _state = MutableStateFlow(ChatState())
    val state: StateFlow<ChatState> = _state.asStateFlow()

    @kotlinx.coroutines.ExperimentalCoroutinesApi
    val messages: StateFlow<List<ChatMessage>> =
        _state.flatMapLatest { s ->
            if (s.noteId != null) chatRepo.observeMessages(s.noteId)
            else kotlinx.coroutines.flow.flowOf(emptyList())
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    /** Chat-Notiz laden (Titel holen). */
    fun load(noteId: String) {
        viewModelScope.launch {
            val note = noteRepo.getById(noteId)
            _state.value = ChatState(
                noteId = noteId,
                title = note?.title ?: "Chat",
                loaded = true
            )
        }
    }

    /** Titel ändern (TopBar inline-edit, H4 evtl.). */
    fun updateTitle(newTitle: String) {
        val id = _state.value.noteId ?: return
        _state.value = _state.value.copy(title = newTitle)
        viewModelScope.launch {
            val note = noteRepo.getById(id) ?: return@launch
            noteRepo.updateNote(id, newTitle, note.bodyJson)
        }
    }

    fun sendMessage(text: String, quotedMessageId: String? = null) {
        val id = _state.value.noteId ?: return
        viewModelScope.launch { chatRepo.sendMessage(id, text, quotedMessageId) }
    }

    fun editMessage(messageId: String, newText: String) {
        viewModelScope.launch { chatRepo.editMessage(messageId, newText) }
    }

    fun deleteMessage(messageId: String) {
        viewModelScope.launch { chatRepo.deleteMessage(messageId) }
    }

    class Factory(
        private val chatRepo: ChatMessageRepository,
        private val noteRepo: NoteRepository
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T =
            ChatViewModel(chatRepo, noteRepo) as T
    }
}
