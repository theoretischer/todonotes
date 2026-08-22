package com.earendil.todonotes.ui

import com.earendil.todonotes.data.entity.Folder
import com.earendil.todonotes.data.entity.Note
import com.earendil.todonotes.data.repo.FolderRepository
import com.earendil.todonotes.data.repo.NoteRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.launch

/**
 * Ein Breadcrumb-Eintrag (Ordner-Id + Name) auf dem Pfad vom Wurzel- zum
 * aktuellen Ordner. Wurzel wird durch [ROOT_CRUMB] repräsentiert.
 */
data class Crumb(val folderId: String?, val name: String)

/** Aktueller Stand der Notizen-Ansicht: wo bin ich + was liegt dort. */
data class NotesBrowserState(
    val currentFolderId: String?,
    val breadcrumbs: List<Crumb>,
    val folders: List<Folder>,
    val notes: List<Note>
) {
    companion object {
        val EMPTY = NotesBrowserState(null, listOf(Crumb(null, "Notizen")), emptyList(), emptyList())
    }
}

/**
 * Plain-Kotlin-ViewModel für den Notizen-Tab (M7d — commonMain).
 *
 * Optimistic Reorder (M7d-rev): Auf Wasm ist jeder DB-Swap ein asynchroner
 * Round-Trip zum Web Worker (getById×2 + getInFolder + setPosition×N). Bei
 * 5 Swaps = dutzende Round-Trips = 3-5s Verzögerung, und der reaktive Flow
 * liefert zwischendurch alte Listen → die Row springt zurück.
 *
 * Lösung: browserState ist ein MutableStateFlow, der von der DB gespeist
 * wird. reorderNotes/reorderFolders mutieren ihn SOFORT (optimistic), der
 * DB-Batch passiert erst am Ende des Drags (commitReorder) in einem Aufruf.
 * Während des Drags wird die DB-Flow ignoriert (isReordering-Flag).
 */
class NotesViewModel(
    private val folderRepo: FolderRepository,
    private val noteRepo: NoteRepository,
    scope: CoroutineScope
) {
    private val vmScope = CoroutineScope(scope.coroutineContext + SupervisorJob())

    // aktuell geöffneter Ordner (null = Wurzel) + Pfad dorthin.
    private val currentFolderId = MutableStateFlow<String?>(null)
    private val breadcrumbs = MutableStateFlow<List<Crumb>>(listOf(Crumb(null, "Notizen")))

    @OptIn(ExperimentalCoroutinesApi::class)
    private val foldersInCurrent = currentFolderId.flatMapLatest { id ->
        if (id == null) folderRepo.observeRootFolders() else folderRepo.observeFoldersIn(id)
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    private val notesInCurrent = currentFolderId.flatMapLatest { id ->
        if (id == null) noteRepo.observeRootNotes() else noteRepo.observeNotesIn(id)
    }

    // DB-backed Flow → speist _state. Während isReordering=true wird die
    // DB-Flow ignoriert (optimistic Updates haben Vorrang).
    private val _browserState = MutableStateFlow(NotesBrowserState.EMPTY)
    val browserState: StateFlow<NotesBrowserState> = _browserState.asStateFlow()

    // True während eines Drag-Reorders → DB-Flow-Updates ignorieren.
    private var isReorderingNotes = false
    private var isReorderingFolders = false

    init {
        @OptIn(ExperimentalCoroutinesApi::class)
        vmScope.launch {
            combine(
                currentFolderId, breadcrumbs, foldersInCurrent, notesInCurrent
            ) { id, crumbs, folders, notes ->
                NotesBrowserState(id, crumbs, folders, notes)
            }.collect { state ->
                // Während Reorder: DB-Updates ignorieren (optimistic hat Vorrang).
                val skipNotes = isReorderingNotes
                val skipFolders = isReorderingFolders
                _browserState.value = _browserState.value.copy(
                    currentFolderId = state.currentFolderId,
                    breadcrumbs = state.breadcrumbs,
                    folders = if (skipFolders) _browserState.value.folders else state.folders,
                    notes = if (skipNotes) _browserState.value.notes else state.notes
                )
            }
        }
    }

    // ---- Navigation ----

    fun openFolder(folder: Folder) {
        currentFolderId.value = folder.id
        breadcrumbs.value = breadcrumbs.value + Crumb(folder.id, folder.name)
    }

    fun navigateToCrumb(crumb: Crumb) {
        val idx = breadcrumbs.value.indexOfFirst { it.folderId == crumb.folderId }
        if (idx < 0) return
        currentFolderId.value = crumb.folderId
        breadcrumbs.value = breadcrumbs.value.take(idx + 1)
    }

    fun goUp(): Boolean {
        if (breadcrumbs.value.size <= 1) return false
        val newCrumbs = breadcrumbs.value.dropLast(1)
        breadcrumbs.value = newCrumbs
        currentFolderId.value = newCrumbs.last().folderId
        return true
    }

    // ---- Erstellen / Löschen / Verschieben ----

    fun createFolder(name: String) {
        vmScope.launch { folderRepo.createFolder(name, currentFolderId.value) }
    }

    fun renameFolder(id: String, newName: String) {
        vmScope.launch { folderRepo.renameFolder(id, newName) }
    }

    fun deleteFolder(id: String) {
        vmScope.launch { folderRepo.deleteFolder(id) }
    }

    fun createNote(onCreated: (String) -> Unit) {
        vmScope.launch {
            val note = noteRepo.createNote(folderId = currentFolderId.value)
            onCreated(note.id)
        }
    }

    fun createChatNote(onCreated: (String) -> Unit) {
        vmScope.launch {
            val note = noteRepo.createChatNote(folderId = currentFolderId.value)
            onCreated(note.id)
        }
    }

    fun updateNote(id: String, title: String, bodyJson: String) {
        vmScope.launch { noteRepo.updateNote(id, title, bodyJson) }
    }

    fun moveNote(id: String, newFolderId: String?) {
        vmScope.launch { noteRepo.moveNote(id, newFolderId) }
    }

    fun moveFolder(id: String, newParentId: String?) {
        vmScope.launch { folderRepo.moveFolder(id, newParentId) }
    }

    // ---- Optimistic Reorder ----

    /** Notiz-Swap: sofort im lokalen State (optimistic), DB erst am Ende. */
    fun reorderNotes(idA: String, idB: String) {
        val notes = _browserState.value.notes
        val ia = notes.indexOfFirst { it.id == idA }
        val ib = notes.indexOfFirst { it.id == idB }
        if (ia < 0 || ib < 0) return
        val swapped = notes.toMutableList()
        val tmp = swapped[ia]
        swapped[ia] = swapped[ib]
        swapped[ib] = tmp
        _browserState.value = _browserState.value.copy(notes = swapped)
    }

    /** Ordner-Swap: sofort im lokalen State (optimistic), DB erst am Ende. */
    fun reorderFolders(idA: String, idB: String) {
        val folders = _browserState.value.folders
        val ia = folders.indexOfFirst { it.id == idA }
        val ib = folders.indexOfFirst { it.id == idB }
        if (ia < 0 || ib < 0) return
        val swapped = folders.toMutableList()
        val tmp = swapped[ia]
        swapped[ia] = swapped[ib]
        swapped[ib] = tmp
        _browserState.value = _browserState.value.copy(folders = swapped)
    }

    /** Drag beginnt → DB-Flow für Notizen ignorieren. */
    fun beginNoteReorder() { isReorderingNotes = true }

    /** Drag beendet → finale Reihenfolge als Batch in DB schreiben,
     *  dann DB-Flow wieder zulassen. WICHTIG: isReordering bleibt true
     *  bis der Batch fertig ist, sonst liefert die DB-Flow zwischendurch
     *  die alte Reihenfolge und ueberschreibt das optimistic Update. */
    fun commitNoteReorder() {
        val finalIds = _browserState.value.notes.map { it.id }
        vmScope.launch {
            noteRepo.applyOrder(finalIds)
            isReorderingNotes = false  // erst nach DB-Batch freigeben
        }
    }

    fun beginFolderReorder() { isReorderingFolders = true }

    fun commitFolderReorder() {
        val finalIds = _browserState.value.folders.map { it.id }
        vmScope.launch {
            folderRepo.applyOrder(finalIds)
            isReorderingFolders = false  // erst nach DB-Batch freigeben
        }
    }

    suspend fun getAllFoldersForMove() = folderRepo.getAllFolders()

    fun deleteNote(id: String) {
        vmScope.launch { noteRepo.deleteNote(id) }
    }

    suspend fun getNote(id: String): Note? = noteRepo.getById(id)
}
