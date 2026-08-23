package com.earendil.todonotes.ui

import com.earendil.todonotes.data.repo.NoteRepository
import com.earendil.todonotes.data.richtext.NoteTextBody
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * Zustand, den der Editor an die UI reicht.
 *
 * @param title      aktueller Titel-Text (editierbar)
 * @param body       Plain-Text-Body (kontinuierlicher Textfluss, wie Word)
 * @param loaded     false während des initialen Ladens (Loading-Indicator)
 * @param isNewNote  true wenn die Notiz gerade erst erstellt wurde →
 *                   Titel-Feld auto-select-all beim ersten Render.
 */
data class NoteEditorState(
    val title: String = "",
    val body: String = "",
    val loaded: Boolean = false,
    val isNewNote: Boolean = false
)

/**
 * Plain-Kotlin-ViewModel für den Notiz-Editor (M7d — commonMain).
 *
 * Der Body wird als **Plain Text** gespeichert (keine Block-JSON mehr).
 * Listen-Formatierung passiert über Zeilen-Präfixe (-, 1., [ ], →) —
 * siehe [NoteTextBody]. Beim Laden werden alte Block-JSON-Notizen
 * automatisch migriert.
 *
 * Auto-Save: 1 s nach letzter Änderung + sofort bei Back.
 *
 * **Optimistic UI**: [load] akzeptiert initial-Daten (Titel+Body) aus der
 * Notiz-Liste. So kann der Editor sofort rendern (loaded=true) ohne auf
 * den DB-Roundtrip zu warten. Der DB-Refresh läuft im Hintergrund und
 * aktualisiert nur wenn abweichend. [onNoteUpdated] wird bei flush
 * aufgerufen damit die Liste sofort den neuen Titel/Body zeigt.
 */
class NoteEditorViewModel(
    private val noteRepo: NoteRepository,
    scope: CoroutineScope
) {
    private val vmScope = CoroutineScope(scope.coroutineContext + SupervisorJob())

    private val _state = MutableStateFlow(NoteEditorState())
    val state: StateFlow<NoteEditorState> = _state.asStateFlow()

    private var noteId: String? = null
    private var saveJob: Job? = null
    private var dirty = false
    // Callback: wird bei flush aufgerufen mit (id, title, body) damit die
    // Liste sofort (optimistic) den neuen Titel/Body zeigen kann.
    var onNoteUpdated: ((id: String, title: String, body: String) -> Unit)? = null

    /** Notiz laden.
     *
     *  **Optimistic**: Wenn initialTitle/initialBody übergeben werden (aus der
     *  Notiz-Liste), setzt der Editor sofort loaded=true mit diesen Daten —
     *  kein Warten auf den DB-Roundtrip. Der DB-Refresh läuft im Hintergrund.
     *
     *  Ohne initial-Daten (z.B. neue Notiz): klassisch loaded=false + DB-Load.
     *
     *  Alte Block-JSON wird zu Plain Text migriert. */
    fun load(noteId: String, isNew: Boolean, initialTitle: String? = null, initialBody: String? = null) {
        this.noteId = noteId
        val initialBodyParsed = initialBody?.let { NoteTextBody.migrateFromBlocks(it) }
        if (initialTitle != null && initialBodyParsed != null) {
            // Optimistic: sofort rendern mit bekannten Daten aus der Liste.
            // Die Liste wird selbst von der DB-Flow gespeist → Daten sind aktuell.
            // Kein Hintergrund-Refresh nötig (vermeidet Race-Conditions).
            _state.value = NoteEditorState(
                title = initialTitle,
                body = initialBodyParsed,
                loaded = true,
                isNewNote = isNew
            )
        } else {
            // Klassisch: laden von DB.
            _state.value = NoteEditorState(loaded = false, isNewNote = isNew)
            vmScope.launch {
                val note = noteRepo.getById(noteId)
                if (note != null) {
                    _state.value = NoteEditorState(
                        title = note.title,
                        body = NoteTextBody.migrateFromBlocks(note.bodyJson),
                        loaded = true,
                        isNewNote = isNew
                    )
                } else {
                    _state.value = NoteEditorState(loaded = true, isNewNote = isNew)
                }
            }
        }
    }

    fun updateTitle(newTitle: String) {
        _state.value = _state.value.copy(title = newTitle, isNewNote = false)
        scheduleSave()
    }

    fun updateBody(newBody: String) {
        _state.value = _state.value.copy(body = newBody, isNewNote = false)
        scheduleSave()
    }

    private fun scheduleSave() {
        dirty = true
        saveJob?.cancel()
        saveJob = vmScope.launch {
            delay(1000)
            flush()
        }
    }

    /** Sofort speichern (bei Back). Ruft onNoteUpdated auf damit die
     *  Liste sofort (optimistic) den neuen Titel/Body zeigt — ohne auf
     *  den DB-Roundtrip zu warten. */
    fun flushNow() {
        saveJob?.cancel()
        vmScope.launch { flush() }
    }

    private suspend fun flush() {
        if (!dirty) return
        val id = noteId ?: return
        val s = _state.value
        val title = s.title.ifBlank { "Ohne Titel" }
        noteRepo.updateNote(id, title, s.body)
        // Optimistic: Liste sofort updaten.
        onNoteUpdated?.invoke(id, title, s.body)
        dirty = false
    }
}
