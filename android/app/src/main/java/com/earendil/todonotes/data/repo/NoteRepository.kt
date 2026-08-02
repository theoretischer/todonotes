package com.earendil.todonotes.data.repo

import android.content.Context
import com.earendil.todonotes.data.TodoNotesDatabase
import com.earendil.todonotes.data.entity.Note
import kotlinx.coroutines.flow.Flow
import java.util.UUID

/**
 * Repository für Notizen (Block F2).
 *
 * Notizen leben in Ordnern (folderId, null = Wurzel). Der Body wird als
 * JSON-String gespeichert (bodyJson) — das Rich-Text-Modell aus F3
 * serialisiert/deserialisiert das. Hier im Repository ist der Body opak.
 *
 * Titel-Extraktion aus der ersten Body-Zeile passiert im Editor (F5),
 * nicht hier — das Repo nimmt title als fertigen String entgegen.
 */
class NoteRepository(context: Context) {

    private val dao = TodoNotesDatabase.get(context).noteDao()

    /** Notizen auf der Wurzel-Ebene (folderId == null). */
    fun observeRootNotes(): Flow<List<Note>> = dao.observeNotesInFolder(null)

    /** Notizen direkt im angegebenen Ordner. */
    fun observeNotesIn(folderId: String): Flow<List<Note>> = dao.observeNotesInFolder(folderId)

    suspend fun getById(id: String): Note? = dao.getById(id)

    /** Leere Notiz anlegen (im Editor wird dann weitergearbeitet).
     *  Default-Titel: "Neue Notiz vom dd.MM.yyyy". */
    suspend fun createNote(folderId: String? = null, title: String = defaultNoteTitle(), bodyJson: String = "[]"): Note {
        val now = System.currentTimeMillis()
        val note = Note(
            id = UUID.randomUUID().toString(),
            folderId = folderId,
            title = title,
            bodyJson = bodyJson,
            createdAt = now,
            updatedAt = now
        )
        dao.insert(note)
        return note
    }

    /** Speichert Titel + Body (wird vom Editor bei Back/auto-save gerufen, F5). */
    suspend fun updateNote(id: String, title: String, bodyJson: String) {
        val note = dao.getById(id) ?: return
        dao.update(note.copy(title = title, bodyJson = bodyJson, updatedAt = System.currentTimeMillis()))
    }

    /** Notiz in einen anderen Ordner verschieben (Drag&Drop, F6). null = Wurzel. */
    suspend fun moveNote(id: String, newFolderId: String?) {
        val note = dao.getById(id) ?: return
        dao.update(note.copy(folderId = newFolderId, updatedAt = System.currentTimeMillis()))
    }

    suspend fun deleteNote(id: String) {
        dao.softDelete(id, System.currentTimeMillis())
    }
}

/** Default-Titel fuer neue Notizen: "Neue Notiz vom dd.MM.yyyy". */
private fun defaultNoteTitle(): String {
    val fmt = java.text.SimpleDateFormat("dd.MM.yyyy", java.util.Locale.GERMAN)
    return "Neue Notiz vom ${fmt.format(java.util.Date())}"
}
