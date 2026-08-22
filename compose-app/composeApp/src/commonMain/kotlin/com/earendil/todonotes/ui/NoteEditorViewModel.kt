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

    /** Notiz laden. Alte Block-JSON wird zu Plain Text migriert.
     *
     *  WICHTIG: Setzt _state sofort auf loaded=false, bevor der Coroutine
     *  startet. So wird state.loaded von true→false→true und der
     *  LaunchedEffect(state.loaded) im Screen feuert neu — sonst würde
     *  beim zweiten Öffnen der Editor im Lade-Kreisel stecken bleiben,
     *  weil sich state.loaded nicht geändert hat. */
    fun load(noteId: String, isNew: Boolean) {
        this.noteId = noteId
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

    /** Sofort speichern (bei Back). */
    fun flushNow() {
        saveJob?.cancel()
        vmScope.launch { flush() }
    }

    private suspend fun flush() {
        if (!dirty) return
        val id = noteId ?: return
        val s = _state.value
        noteRepo.updateNote(id, s.title.ifBlank { "Ohne Titel" }, s.body)
        dirty = false
    }
}
