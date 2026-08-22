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
 * Plain-Kotlin-ViewModel für den Notizen-Tab (M7d — commonMain).
 *
 * Kein androidx.lifecycle.ViewModel — stattdessen eigene CoroutineScope.
 * Hält den Traversal-State (aktueller Ordner + Breadcrumb-Pfad) und
 * observiert die Ordner + Notizen der aktuellen Ebene reaktiv.
 * Drag&Drop, Editor-Öffnen und Erstellen laufen über die Aktionsfunktionen.
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

    // Ordner + Notizen auf der aktuellen Ebene (reaktiv via flatMapLatest).
    @OptIn(ExperimentalCoroutinesApi::class)
    private val foldersInCurrent = currentFolderId.flatMapLatest { id ->
        if (id == null) folderRepo.observeRootFolders() else folderRepo.observeFoldersIn(id)
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    private val notesInCurrent = currentFolderId.flatMapLatest { id ->
        if (id == null) noteRepo.observeRootNotes() else noteRepo.observeNotesIn(id)
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    val browserState: StateFlow<NotesBrowserState> =
        combine(
            currentFolderId, breadcrumbs, foldersInCurrent, notesInCurrent
        ) { id, crumbs, folders, notes ->
            NotesBrowserState(id, crumbs, folders, notes)
        }.stateIn(vmScope, SharingStarted.Eagerly, NotesBrowserState.EMPTY)

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
        vmScope.launch { folderRepo.createFolder(name, currentFolderId.value) }
    }

    fun renameFolder(id: String, newName: String) {
        vmScope.launch { folderRepo.renameFolder(id, newName) }
    }

    fun deleteFolder(id: String) {
        vmScope.launch { folderRepo.deleteFolder(id) }
    }

    /** Neue leere Notiz im aktuellen Ordner anlegen. Liefert die neue Id
     *  via Callback, damit der Aufrufer direkt den Editor öffnen kann. */
    fun createNote(onCreated: (String) -> Unit) {
        vmScope.launch {
            val note = noteRepo.createNote(folderId = currentFolderId.value)
            onCreated(note.id)
        }
    }

    /** Neue Chat-Notiz anlegen. Liefert die neue Id via Callback. */
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

    /** Reihenfolge zweier Notizen tauschen (1D-Drag&Drop). */
    fun reorderNotes(idA: String, idB: String) {
        vmScope.launch { noteRepo.swapNoteOrder(idA, idB) }
    }

    /** Reihenfolge zweier Ordner tauschen (1D-Drag&Drop). */
    fun reorderFolders(idA: String, idB: String) {
        vmScope.launch { folderRepo.swapFolderOrder(idA, idB) }
    }

    /** Alle Ordner flach (für Verschieben-Picker). */
    suspend fun getAllFoldersForMove() = folderRepo.getAllFolders()

    fun deleteNote(id: String) {
        vmScope.launch { noteRepo.deleteNote(id) }
    }

    suspend fun getNote(id: String): Note? = noteRepo.getById(id)
}
