package com.earendil.todonotes.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.earendil.todonotes.data.entity.Note
import com.earendil.todonotes.data.repo.NoteRepository
import com.earendil.todonotes.data.richtext.NoteBodyJson
import com.earendil.todonotes.data.richtext.newBodyWithEmptyParagraph
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * Zustand, den der Editor an die UI reicht.
 *
 * @param title      aktueller Titel-Text (editierbar)
 * @param bodyJson   serialisierter Body – die UI decodiert ihn für die
 *                   Block-Darstellung und encodiert ihn nach jeder Änderung neu.
 * @param loaded     false während des initialen Ladens (Loading-Indicator)
 * @param isNewNote  true wenn die Notiz gerade erst erstellt wurde →
 *                   Titel-Feld auto-select-all beim ersten Render.
 */
data class NoteEditorState(
    val title: String = "",
    val bodyJson: String = "[]",
    val loaded: Boolean = false,
    val isNewNote: Boolean = false
)

/**
 * ViewModel für den Notiz-Editor (Block F5).
 *
 * Lädt die Notiz beim Öffnen, hält den Editierzustand im Memory und
 * speichert verzögert (auto-save: 1 s nach letzter Änderung + sofort
 * bei Back). Der Body wird als opaker JSON-String transportiert –
 * die Block-Logik liegt in der UI, das Repo/DB sieht nur den String.
 */
class NoteEditorViewModel(
    private val noteRepo: NoteRepository
) : ViewModel() {

    private val _state = MutableStateFlow(NoteEditorState())
    val state: StateFlow<NoteEditorState> = _state.asStateFlow()

    private var noteId: String? = null
    private var saveJob: Job? = null
    private var dirty = false

    /** Notiz laden (vom Browser aufgerufen, wenn "Neue Notiz" oder eine
     *  bestehende Notiz geöffnet wird). */
    fun load(noteId: String, isNew: Boolean) {
        this.noteId = noteId
        viewModelScope.launch {
            val note = noteRepo.getById(noteId)
            if (note != null) {
                val body = note.bodyJson.ifBlank { NoteBodyJson.encode(newBodyWithEmptyParagraph()) }
                _state.value = NoteEditorState(
                    title = note.title,
                    bodyJson = body,
                    loaded = true,
                    isNewNote = isNew
                )
            } else {
                // Notiz gelöscht während Editor offen → leerzustand
                _state.value = NoteEditorState(loaded = true, isNewNote = isNew)
            }
        }
    }

    /** Titel geändert → dirty + auto-save. */
    fun updateTitle(newTitle: String) {
        _state.value = _state.value.copy(title = newTitle, isNewNote = false)
        scheduleSave()
    }

    /** Body geändert → dirty + auto-save.
     *  Erste Änderung an einer neuen Notiz löscht das isNewNote-Flag
     *  (nur beim ersten Render soll auto-select greifen). */
    fun updateBody(newBodyJson: String) {
        _state.value = _state.value.copy(bodyJson = newBodyJson, isNewNote = false)
        scheduleSave()
    }

    /** Auto-save: 1 s nach letzter Änderung. */
    private fun scheduleSave() {
        dirty = true
        saveJob?.cancel()
        saveJob = viewModelScope.launch {
            delay(1000)
            flush()
        }
    }

    /** Sofort speichern (bei Back). Liefert true, wenn gespeichert wurde. */
    fun flushNow() {
        saveJob?.cancel()
        viewModelScope.launch { flush() }
    }

    private suspend fun flush() {
        if (!dirty) return
        val id = noteId ?: return
        val s = _state.value
        noteRepo.updateNote(id, s.title.ifBlank { "Ohne Titel" }, s.bodyJson)
        dirty = false
    }

    class Factory(
        private val noteRepo: NoteRepository
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T =
            NoteEditorViewModel(noteRepo) as T
    }
}
