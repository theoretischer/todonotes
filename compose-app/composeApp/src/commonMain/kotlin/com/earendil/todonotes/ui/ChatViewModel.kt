package com.earendil.todonotes.ui

import com.earendil.todonotes.data.entity.ChatMessage
import com.earendil.todonotes.data.repo.ChatMessageRepository
import com.earendil.todonotes.data.repo.NoteRepository
import com.earendil.todonotes.data.repo.nowMs
import com.earendil.todonotes.data.repo.randomUuidString
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
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
 * **Optimistic UI**: [load] akzeptiert initialTitle aus der Notiz-Liste
 * → sofort loaded=true, kein DB-Roundtrip. sendMessage/editMessage/
 * deleteMessage mutieren sofort das lokale [_messages] → UI reagiert
 * sofort, DB-Write läuft async. DB-Flow überschreibt als Source of
 * Truth sobald sie feuert.
 */
class ChatViewModel(
    private val chatRepo: ChatMessageRepository,
    private val noteRepo: NoteRepository,
    scope: CoroutineScope
) {
    private val vmScope = CoroutineScope(scope.coroutineContext + SupervisorJob())

    private val _state = MutableStateFlow(ChatState())
    val state: StateFlow<ChatState> = _state.asStateFlow()

    // Optimistic messages: lokales MutableStateFlow, von DB-Flow gespeist.
    // sendMessage/edit/delete mutieren sofort, DB-Flow ueberschreibt als
    // Source of Truth.
    private val _messages = MutableStateFlow<List<ChatMessage>>(emptyList())
    val messages: StateFlow<List<ChatMessage>> = _messages.asStateFlow()

    var onNoteUpdated: ((id: String, title: String) -> Unit)? = null

    @OptIn(ExperimentalCoroutinesApi::class)
    private val dbMessages = _state.flatMapLatest { s ->
        if (s.noteId != null) chatRepo.observeMessages(s.noteId)
        else flowOf(emptyList())
    }

    init {
        // DB-Flow als Source of Truth → _messages updaten.
        @OptIn(ExperimentalCoroutinesApi::class)
        vmScope.launch {
            dbMessages.collect { dbMsgs -> _messages.value = dbMsgs }
        }
    }

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
        val trimmed = text.trim()
        if (trimmed.isEmpty()) return
        val now = nowMs()
        // Optimistic: sofort ans lokale _messages anhaengen.
        val msg = ChatMessage(
            id = randomUuidString(),
            noteId = id,
            text = trimmed,
            createdAt = now,
            updatedAt = now,
            position = (_messages.value.maxOfOrNull { it.position } ?: 0L) + 1L,
            quotedMessageId = quotedMessageId
        )
        _messages.value = _messages.value + msg
        vmScope.launch { chatRepo.sendMessage(id, text, quotedMessageId) }
    }

    fun editMessage(messageId: String, newText: String) {
        val trimmed = newText.trim()
        if (trimmed.isEmpty()) return
        // Optimistic: sofort lokal updaten.
        _messages.value = _messages.value.map {
            if (it.id == messageId) it.copy(text = trimmed, updatedAt = nowMs()) else it
        }
        vmScope.launch { chatRepo.editMessage(messageId, newText) }
    }

    fun deleteMessage(messageId: String) {
        // Optimistic: sofort lokal entfernen.
        _messages.value = _messages.value.filterNot { it.id == messageId }
        vmScope.launch { chatRepo.deleteMessage(messageId) }
    }
}
