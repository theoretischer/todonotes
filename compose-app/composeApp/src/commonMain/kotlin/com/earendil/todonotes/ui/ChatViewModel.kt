package com.earendil.todonotes.ui

import com.earendil.todonotes.data.entity.ChatMessage
import com.earendil.todonotes.data.repo.ChatMessageRepository
import com.earendil.todonotes.data.repo.NoteRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/**
 * Zustand für den Chat-Bildschirm (Block H3, M7d-2 — commonMain).
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
 * Plain-Kotlin-ViewModel für den Chat-Bildschirm (M7d-2 — commonMain).
 *
 * Observiert reaktiv alle Nachrichten der Chat-Notiz (älteste zuerst).
 * Senden/Bearbeiten/Löschen läuft über [ChatMessageRepository].
 *
 * **Optimistic UI** (wie NoteEditor): [load] akzeptiert initialTitle aus
 * der Notiz-Liste → sofort loaded=true, kein DB-Roundtrip. [onNoteUpdated]
 * wird beim flush aufgerufen damit die Liste sofort den neuen Titel zeigt.
 */
class ChatViewModel(
    private val chatRepo: ChatMessageRepository,
    private val noteRepo: NoteRepository,
    scope: CoroutineScope
) {
    private val vmScope = CoroutineScope(scope.coroutineContext + SupervisorJob())

    private val _state = MutableStateFlow(ChatState())
    val state: StateFlow<ChatState> = _state.asStateFlow()

    // Callback: Titel-Änderung sofort in Liste übernehmen (optimistic).
    var onNoteUpdated: ((id: String, title: String) -> Unit)? = null

    @OptIn(ExperimentalCoroutinesApi::class)
    val messages: StateFlow<List<ChatMessage>> =
        _state.flatMapLatest { s ->
            if (s.noteId != null) chatRepo.observeMessages(s.noteId)
            else flowOf(emptyList())
        }.stateIn(vmScope, SharingStarted.Eagerly, emptyList())

    /** Chat-Notiz laden. Optimistic: initialTitle aus Liste → sofort loaded. */
    fun load(noteId: String, initialTitle: String? = null) {
        if (initialTitle != null) {
            _state.value = ChatState(
                noteId = noteId,
                title = initialTitle,
                loaded = true
            )
        } else {
            _state.value = ChatState(noteId = noteId, loaded = false)
            vmScope.launch {
                val note = noteRepo.getById(noteId)
                _state.value = ChatState(
                    noteId = noteId,
                    title = note?.title ?: "Chat",
                    loaded = true
                )
            }
        }
    }

    /** Titel ändern (TopBar inline-edit). */
    fun updateTitle(newTitle: String) {
        val id = _state.value.noteId ?: return
        _state.value = _state.value.copy(title = newTitle)
        vmScope.launch {
            val note = noteRepo.getById(id) ?: return@launch
            noteRepo.updateNote(id, newTitle, note.bodyJson)
            onNoteUpdated?.invoke(id, newTitle)
        }
    }

    fun sendMessage(text: String, quotedMessageId: String? = null) {
        val id = _state.value.noteId ?: return
        vmScope.launch { chatRepo.sendMessage(id, text, quotedMessageId) }
    }

    fun editMessage(messageId: String, newText: String) {
        vmScope.launch { chatRepo.editMessage(messageId, newText) }
    }

    fun deleteMessage(messageId: String) {
        vmScope.launch { chatRepo.deleteMessage(messageId) }
    }
}
