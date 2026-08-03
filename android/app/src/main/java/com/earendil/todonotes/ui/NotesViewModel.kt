package com.earendil.todonotes.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.earendil.todonotes.data.entity.Folder
import com.earendil.todonotes.data.entity.Note
import com.earendil.todonotes.data.repo.FolderRepository
import com.earendil.todonotes.data.repo.NoteRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
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
 * ViewModel für den Notizen-Tab (Block F2).
 *
 * Hält den Traversal-State (aktueller Ordner + Breadcrumb-Pfad) und
 * observiert die Ordner + Notizen der aktuellen Ebene reaktiv.
 * Drag&Drop (F6), Editor-Öffnen (F5) und Erstellen laufen über
 * die Aktionsfunktionen weiter unten.
 */
class NotesViewModel(
    private val folderRepo: FolderRepository,
    private val noteRepo: NoteRepository
) : ViewModel() {

    // aktuell geöffneter Ordner (null = Wurzel) + Pfad dorthin.
    private val currentFolderId = MutableStateFlow<String?>(null)
    private val breadcrumbs = MutableStateFlow<List<Crumb>>(listOf(Crumb(null, "Notizen")))

    // Ordner + Notizen auf der aktuellen Ebene (reaktiv via flatMapLatest).
    @kotlinx.coroutines.ExperimentalCoroutinesApi
    private val foldersInCurrent: kotlinx.coroutines.flow.Flow<List<Folder>> =
        currentFolderId.flatMapLatest { id ->
            if (id == null) folderRepo.observeRootFolders() else folderRepo.observeFoldersIn(id)
        }

    @kotlinx.coroutines.ExperimentalCoroutinesApi
    private val notesInCurrent: kotlinx.coroutines.flow.Flow<List<Note>> =
        currentFolderId.flatMapLatest { id ->
            if (id == null) noteRepo.observeRootNotes() else noteRepo.observeNotesIn(id)
        }

    @kotlinx.coroutines.ExperimentalCoroutinesApi
    val browserState: StateFlow<NotesBrowserState> =
        combine(
            currentFolderId, breadcrumbs, foldersInCurrent, notesInCurrent
        ) { id, crumbs, folders, notes ->
            NotesBrowserState(id, crumbs, folders, notes)
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), NotesBrowserState.EMPTY)

    // ---- Navigation ----

    /** Ordner öffnen (einen Level runter). */
    fun openFolder(folder: Folder) {
        currentFolderId.value = folder.id
        breadcrumbs.value = breadcrumbs.value + Crumb(folder.id, folder.name)
    }

    /** Über einen Breadcrumb-Eintrag zu einem höheren Level springen. */
    fun navigateToCrumb(crumb: Crumb) {
        val idx = breadcrumbs.value.indexOfFirst { it.folderId == crumb.folderId }
        if (idx < 0) return
        currentFolderId.value = crumb.folderId
        breadcrumbs.value = breadcrumbs.value.take(idx + 1)
    }

    /** Eine Ebene hoch (Back). Liefert false, wenn wir schon auf Wurzel sind. */
    fun goUp(): Boolean {
        if (breadcrumbs.value.size <= 1) return false
        val newCrumbs = breadcrumbs.value.dropLast(1)
        breadcrumbs.value = newCrumbs
        currentFolderId.value = newCrumbs.last().folderId
        return true
    }

    // ---- Erstellen / Löschen / Verschieben ----

    fun createFolder(name: String) {
        viewModelScope.launch {
            folderRepo.createFolder(name, currentFolderId.value)
        }
    }

    fun renameFolder(id: String, newName: String) {
        viewModelScope.launch { folderRepo.renameFolder(id, newName) }
    }

    fun deleteFolder(id: String) {
        viewModelScope.launch { folderRepo.deleteFolder(id) }
    }

    /** Ordner in einen anderen verschieben. null = auf Wurzel-Ebene. */
    fun moveFolder(id: String, newParentId: String?) {
        viewModelScope.launch { folderRepo.moveFolder(id, newParentId) }
    }

    /** Neue leere Notiz im aktuellen Ordner anlegen. Liefert die neue Id
     *  via Callback, damit der Aufrufer (F4) direkt den Editor (F5) öffnen kann. */
    fun createNote(onCreated: (String) -> Unit) {
        viewModelScope.launch {
            val note = noteRepo.createNote(folderId = currentFolderId.value)
            onCreated(note.id)
        }
    }

    /** Neue Chat-Notiz anlegen (Block H). Liefert die neue Id via Callback. */
    fun createChatNote(onCreated: (String) -> Unit) {
        viewModelScope.launch {
            val note = noteRepo.createChatNote(folderId = currentFolderId.value)
            onCreated(note.id)
        }
    }

    fun updateNote(id: String, title: String, bodyJson: String) {
        viewModelScope.launch { noteRepo.updateNote(id, title, bodyJson) }
    }

    fun moveNote(id: String, newFolderId: String?) {
        viewModelScope.launch { noteRepo.moveNote(id, newFolderId) }
    }

    /** Reihenfolge zweier Notizen tauschen (1D-Drag&Drop, F6). */
    fun reorderNotes(idA: String, idB: String) {
        viewModelScope.launch { noteRepo.swapNoteOrder(idA, idB) }
    }

    /** Reihenfolge zweier Ordner tauschen (1D-Drag&Drop, F6). */
    fun reorderFolders(idA: String, idB: String) {
        viewModelScope.launch { folderRepo.swapFolderOrder(idA, idB) }
    }

    /** Alle Ordner flach (fuer Verschieben-Picker). */
    suspend fun getAllFoldersForMove() = folderRepo.getAllFolders()

    fun deleteNote(id: String) {
        viewModelScope.launch { noteRepo.deleteNote(id) }
    }

    suspend fun getNote(id: String): Note? = noteRepo.getById(id)

    class Factory(
        private val folderRepo: FolderRepository,
        private val noteRepo: NoteRepository
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T =
            NotesViewModel(folderRepo, noteRepo) as T
    }
}
