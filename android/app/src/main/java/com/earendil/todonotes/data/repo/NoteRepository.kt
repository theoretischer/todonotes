package com.earendil.todonotes.data.repo

import android.content.Context
import com.earendil.todonotes.data.TodoNotesDatabase
import com.earendil.todonotes.data.entity.Note
import com.earendil.todonotes.data.entity.NoteType
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
        return createNote(folderId, title, bodyJson, NoteType.NOTE)
    }

    /** Neue Chat-Notiz anlegen (Block H — WhatsApp-Style Tracking-Notiz).
     *  Default-Titel: "Neuer Chat vom dd.MM.yyyy". */
    suspend fun createChatNote(folderId: String? = null): Note {
        val fmt = java.text.SimpleDateFormat("dd.MM.yyyy", java.util.Locale.GERMAN)
        return createNote(folderId, "Neuer Chat vom ${fmt.format(java.util.Date())}", "[]", NoteType.CHAT)
    }

    private suspend fun createNote(folderId: String?, title: String, bodyJson: String, type: NoteType): Note {
        val now = System.currentTimeMillis()
        val note = Note(
            id = UUID.randomUUID().toString(),
            folderId = folderId,
            type = type,
            title = title,
            bodyJson = bodyJson,
            createdAt = now,
            updatedAt = now,
            // Neue Notiz ans Ende der Liste: höchste Position im Ordner + 1.
            position = (dao.getInFolder(folderId).maxOfOrNull { it.position } ?: 0L) + 1
        )
        dao.insert(note)
        return note
    }

    /** Reihenfolge zweier Notizen tauschen (1D-Drag&Drop, F6).
     *
     * Positionen sind NICHT zwangsläufig eindeutig (Alt-Daten können alle
     * 0 sein). Daher normalisieren wir nach dem Tausch die komplette
     * Ordner-Liste neu (Indizes × 10), damit die neue Reihenfolge garantiert
     * eindeutig aufgeht und bei Room ORDER BY position ASC landet. */
    suspend fun swapNoteOrder(idA: String, idB: String) {
        if (idA == idB) return
        val a = dao.getById(idA) ?: return
        val b = dao.getById(idB) ?: return
        if (a.folderId != b.folderId) return
        val list = dao.getInFolder(a.folderId).toMutableList()
        val ia = list.indexOfFirst { it.id == idA }
        val ib = list.indexOfFirst { it.id == idB }
        if (ia < 0 || ib < 0) return
        // Reihenfolge der beiden Einträge tauschen
        val tmp = list[ia]
        list[ia] = list[ib]
        list[ib] = tmp
        val now = System.currentTimeMillis()
        list.forEachIndexed { index, note ->
            dao.setPosition(note.id, (index + 1).toLong() * 10, now)
        }
    }

    /** Speichert Titel + Body (wird vom Editor bei Back/auto-save gerufen, F5). */
    suspend fun updateNote(id: String, title: String, bodyJson: String) {
        val note = dao.getById(id) ?: return
        dao.update(note.copy(title = title, bodyJson = bodyJson, updatedAt = System.currentTimeMillis()))
    }

    /** Notiz in einen anderen Ordner verschieben (F6). null = Wurzel.
     *  Die Notiz landet am Ende der Ziel-Liste (höchste Position). */
    suspend fun moveNote(id: String, newFolderId: String?) {
        val note = dao.getById(id) ?: return
        val maxPos = (dao.getInFolder(newFolderId).maxOfOrNull { it.position } ?: 0L) + 1
        dao.update(note.copy(folderId = newFolderId, updatedAt = System.currentTimeMillis(), position = maxPos))
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
