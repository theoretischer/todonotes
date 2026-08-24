package com.earendil.todonotes.ui

import com.earendil.todonotes.data.repo.NoteRepository
import com.earendil.todonotes.data.richtext.NoteTextBody
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Zustand, den der Editor an die UI reicht.
 *
 * @param title      aktueller Titel-Text (editierbar)
 * @param body       Plain-Text-Body (kontinuierlicher Textfluss, wie Word)
 * @param loaded     false während des initialen Ladens (Loading-Indicator)
 * @param isNewNote  true wenn die Notiz gerade erst erstellt wurde →
 *                   Titel-Feld auto-select-all beim ersten Render.
 * @param deleted    true wenn die Notiz zwischenzeitlich gelöscht wurde
 *                   (z.B. von einem anderen Gerät via Sync). Der Editor
 *                   sollte sich schließen statt zu überschreiben.
 * @param remoteUpdate Marker der bei jedem vom Sync kommenden Update
 *                   hochgezählt wird. Der Screen nutzt das um den Body
 *                   neu zu parsen (nur wenn der Nutzer nicht gerade tippt).
 */
data class NoteEditorState(
    val title: String = "",
    val body: String = "",
    val loaded: Boolean = false,
    val isNewNote: Boolean = false,
    val deleted: Boolean = false,
    val remoteUpdate: Int = 0
)

/**
 * Plain-Kotlin-ViewModel für den Notiz-Editor (M7d — commonMain).
 *
 * Der Body wird als **Plain Text** gespeichert (keine Block-JSON mehr).
 * Listen-Formatierung passiert über Zeilen-Präfixe (-, 1., [ ], →) —
 * siehe [NoteTextBody]. Beim Laden werden alte Block-JSON-Notizen
 * automatisch migriert.
 *
 * Auto-Save: 500 ms nach letzter Änderung + sofort bei Back.
 *
 * **Reaktiv (Sync-A+):** Beobachtet `observeNote(id)` — kommt ein Sync-
 * Update rein, wird der Editor-Body aktualisiert (Live-Mitsehen am Handy).
 * Der Nutzer bekommt nichts mit, solange er nicht gerade tippt. Ist der
 * Editor dirty (Nutzer tippt), wird das Update auf später verschoben
 * (nach flush). Wird die Notiz gelöscht (von einem anderen Gerät),
 * geht der Editor in den `deleted`-Zustand → Screen schließt sich.
 *
 * **Sicher (Sync-A+):** `flush()` nutzt `updateNote` welches gelöschte
 * Notizen nicht überschreibt (`updateBody` filtert `deletedAt`).
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
    private var observeJob: Job? = null
    // Callback: wird bei flush aufgerufen mit (id, title, body) damit die
    // Liste sofort (optimistic) den neuen Titel/Body zeigen kann.
    var onNoteUpdated: ((id: String, title: String, body: String) -> Unit)? = null

    /** Notiz laden + reaktiv beobachten.
     *
     *  **Optimistic**: Wenn initialTitle/initialBody übergeben werden (aus der
     *  Notiz-Liste), setzt der Editor sofort loaded=true mit diesen Daten —
     *  kein Warten auf den DB-Roundtrip.
     *
     *  **Reaktiv**: Neben dem initialen Load wird `observeNote(id)` gestartet.
     *  Kommt ein Sync-Update rein, wird der State aktualisiert (wenn nicht
     *  dirty) → Live-Mitsehen. Wird die Notiz gelöscht → `deleted=true`. */
    fun load(noteId: String, isNew: Boolean, initialTitle: String? = null, initialBody: String? = null) {
        this.noteId = noteId
        observeJob?.cancel()
        val initialBodyParsed = initialBody?.let { NoteTextBody.migrateFromBlocks(it) }
        if (initialTitle != null && initialBodyParsed != null) {
            // Optimistic: sofort rendern mit bekannten Daten aus der Liste.
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
                if (note != null && note.deletedAt == null) {
                    _state.value = NoteEditorState(
                        title = note.title,
                        body = NoteTextBody.migrateFromBlocks(note.bodyJson),
                        loaded = true,
                        isNewNote = isNew
                    )
                } else {
                    _state.value = NoteEditorState(loaded = true, isNewNote = isNew, deleted = note?.deletedAt != null)
                }
            }
        }
        // Reaktiv beobachten — Sync-A+.
        observeJob = vmScope.launch {
            noteRepo.observeNote(noteId).collectLatest { note ->
                if (note == null) return@collectLatest // noch nicht da
                if (note.deletedAt != null) {
                    // Notiz wurde gelöscht (von anderem Gerät via Sync).
                    // Dirty flushen wäre Datenverlust — Editor schließen.
                    if (!dirty) {
                        _state.value = _state.value.copy(deleted = true)
                    }
                    return@collectLatest
                }
                if (dirty) {
                    // Nutzer tippt gerade — Sync-Update ignorieren, beim
                    // nächsten flush gewinnt der Nutzer (LWW, Stufe 1).
                    // Stufe 2 später: Konflikt-Dialog.
                    return@collectLatest
                }
                // Nicht dirty → Body/Titel übernehmen (Live-Mitsehen!).
                val migratedBody = NoteTextBody.migrateFromBlocks(note.bodyJson)
                val current = _state.value
                if (current.loaded && current.title == note.title && current.body == migratedBody) {
                    return@collectLatest // nichts zu tun
                }
                _state.value = current.copy(
                    title = note.title,
                    body = migratedBody,
                    loaded = true,
                    remoteUpdate = current.remoteUpdate + 1
                )
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
            delay(500)
            flush()
        }
    }

    /** Sofort speichern (bei Back). Ruft onNoteUpdated auf damit die
     *  Liste sofort (optimistic) den neuen Titel/Body zeigt — ohne auf
     *  den DB-Roundtrip zu warten.
     *  NonCancellable: flush läuft immer zu Ende, auch wenn der
     *  aufrufende Scope gecancelled wird — sonst Connection-Leak. */
    fun flushNow() {
        saveJob?.cancel()
        vmScope.launch { flush() }
    }

    private suspend fun flush() {
        // NonCancellable: wenn der saveJob (von scheduleSave) gecancelled
        // wird, während flush() die DB-Connection hält, würde die Connection
        // nicht freigegeben → Deadlock ("Timed out acquiring writer").
        // Mit NonCancellable läuft flush() immer zu Ende und gibt die
        // Connection frei. Das `dirty`-Flag wird atomar zurückgesetzt —
        // der nächste scheduleSave() startet einen neuen Job.
        withContext(NonCancellable) {
            if (!dirty) return@withContext
            val id = noteId ?: return@withContext
            val s = _state.value
            if (s.deleted) {
                dirty = false
                return@withContext
            }
            val title = s.title.ifBlank { "Ohne Titel" }
            noteRepo.updateNote(id, title, s.body)
            onNoteUpdated?.invoke(id, title, s.body)
            dirty = false
        }
    }
}
