package com.earendil.todonotes.data.repo

import androidx.room3.withWriteTransaction
import com.earendil.todonotes.data.TodoNotesDatabase
import com.earendil.todonotes.data.entity.Note
import com.earendil.todonotes.data.entity.NoteType
import com.earendil.todonotes.data.sync.SyncManager
import kotlinx.coroutines.flow.Flow

/**
 * Repository für Notizen (M7a — commonMain).
 *
 * Notizen leben in Ordnern (folderId, null = Wurzel). Der Body wird als
 * JSON-String gespeichert (bodyJson) — das Rich-Text-Modell aus F3
 * serialisiert/deserialisiert das. Hier im Repository ist der Body opak.
 */
class NoteRepository(
    private val db: TodoNotesDatabase,
    private val syncManager: SyncManager? = null
) {

    private val dao = db.noteDao()
    private fun dirty() { syncManager?.markDirty() }

    /** Notizen auf der Wurzel-Ebene (folderId == null). */
    fun observeRootNotes(): Flow<List<Note>> = dao.observeNotesInFolder(null)

    /** Notizen direkt im angegebenen Ordner. */
    fun observeNotesIn(folderId: String): Flow<List<Note>> = dao.observeNotesInFolder(folderId)

    suspend fun getById(id: String): Note? = dao.getById(id)

    suspend fun getLiveById(id: String): Note? = dao.getLiveById(id)

    /** Reaktive Beobachtung einer Notiz (für Editor — feuert bei Sync-UPDATE
     *  oder Löschen). Liefert null wenn die Notiz nicht existiert. */
    fun observeNote(id: String): Flow<Note?> = dao.observeNote(id)

    /** Leere Notiz anlegen (im Editor wird dann weitergearbeitet). */
    suspend fun createNote(folderId: String? = null, title: String = defaultNoteTitle(), bodyJson: String = "[]"): Note =
        createNote(folderId, title, bodyJson, NoteType.NOTE)

    /** Neue Chat-Notiz anlegen (Block H — WhatsApp-Style Tracking-Notiz). */
    suspend fun createChatNote(folderId: String? = null): Note =
        createNote(folderId, defaultChatTitle(), "[]", NoteType.CHAT)

    private suspend fun createNote(folderId: String?, title: String, bodyJson: String, type: NoteType): Note {
        val now = nowMs()
        val note = Note(
            id = randomUuidString(),
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
        dirty()
        return note
    }

    /** Reihenfolge zweier Notizen tauschen (1D-Drag&Drop, F6). */
    suspend fun swapNoteOrder(idA: String, idB: String) {
        if (idA == idB) return
        val a = dao.getById(idA) ?: return
        val b = dao.getById(idB) ?: return
        if (a.folderId != b.folderId) return
        val list = dao.getInFolder(a.folderId).toMutableList()
        val ia = list.indexOfFirst { it.id == idA }
        val ib = list.indexOfFirst { it.id == idB }
        if (ia < 0 || ib < 0) return
        val tmp = list[ia]
        list[ia] = list[ib]
        list[ib] = tmp
        val now = nowMs()
        list.forEachIndexed { index, note ->
            dao.setPosition(note.id, (index + 1).toLong() * 10, now)
        }
        dirty()
    }

    /** Speichert Titel + Body (wird vom Editor bei Back/auto-save gerufen).
     *  SICHER: nutzt `updateBody` (filtert deletedAt) — eine gelöschte Notiz
     *  wird NICHT wiederhergestellt. Liefert false wenn die Notiz gelöscht
     *  wurde (Aufrufer kann Editor schließen). */
    suspend fun updateNote(id: String, title: String, bodyJson: String): Boolean {
        dao.updateBody(id, title, bodyJson, nowMs())
        dirty()
        return true
    }

    /** Notiz in einen anderen Ordner verschieben (F6). null = Wurzel. */
    suspend fun moveNote(id: String, newFolderId: String?) {
        val note = dao.getById(id) ?: return
        val maxPos = (dao.getInFolder(newFolderId).maxOfOrNull { it.position } ?: 0L) + 1
        dao.update(note.copy(folderId = newFolderId, updatedAt = nowMs(), position = maxPos))
        dirty()
    }

    suspend fun deleteNote(id: String) {
        dao.softDelete(id, nowMs())
        dirty()
    }

    /** Finale Reihenfolge als Batch schreiben (optimistic Reorder, M7d-rev).
     *  Setzt position = (index+1)*10 für alle Notizen in der angegebenen
     *  Reihenfolge — ein Aufruf statt N Swaps. */
    suspend fun applyOrder(ids: List<String>) {
        if (ids.isEmpty()) return
        val now = nowMs()
        // Transaktion: alle setPosition atomar — die Invalidation-
        // Tracker feuert erst EINMAL am Ende (nicht nach jedem setPosition).
        // Auf Wasm sonst: N×postMessage + N×Flow-Update mit Zwischen-
        // reihenfolgen → Rubberbanding. Und bei Neuladen vor Abschluss
        // wuerde eine unvollstaendige Reihenfolge persistieren.
        db.withWriteTransaction {
            ids.forEachIndexed { index, id ->
                dao.setPosition(id, (index + 1).toLong() * 10, now)
            }
        }
        dirty()
    }

    /** Notizen in einem Ordner einmalig laden (fuer explicit Refresh nach
     *  Reorder-Batch — die reaktive Flow feuert auf Wasm evtl. nicht
     *  zuverlaessig nach mehreren setPosition-Aufrufen). */
    suspend fun getNotesInFolder(folderId: String?): List<Note> =
        dao.getInFolder(folderId)
}
