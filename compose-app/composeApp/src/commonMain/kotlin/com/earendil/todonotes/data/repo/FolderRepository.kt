package com.earendil.todonotes.data.repo

import androidx.room3.withWriteTransaction
import com.earendil.todonotes.data.TodoNotesDatabase
import com.earendil.todonotes.data.entity.Folder
import com.earendil.todonotes.data.sync.SyncManager
import kotlinx.coroutines.flow.Flow

/**
 * Repository für Ordner (M7a — commonMain).
 *
 * Ordner bilden eine Hierarchie über parentId (null = Wurzel). Beim
 * Verschieben muss Zyklenfreiheit geprüft werden (ein Ordner darf nicht
 * in sich selbst oder einen seiner Nachfahren verschoben werden) — das
 * macht [canMoveFolderInto] via rekursiver CTE im DAO.
 */
class FolderRepository(
    private val db: TodoNotesDatabase,
    private val syncManager: SyncManager? = null
) {

    private val dao = db.folderDao()
    private fun dirty() { syncManager?.markDirty() }

    /** Ordner auf der Wurzel-Ebene (parentId == null). */
    fun observeRootFolders(): Flow<List<Folder>> = dao.observeFoldersIn(null)

    /** Ordner direkt unter [parentId]. */
    fun observeFoldersIn(parentId: String): Flow<List<Folder>> = dao.observeFoldersIn(parentId)

    suspend fun getById(id: String): Folder? = dao.getById(id)

    suspend fun createFolder(name: String, parentId: String? = null): Folder {
        val now = nowMs()
        val folder = Folder(
            id = randomUuidString(),
            parentId = parentId,
            name = name,
            createdAt = now,
            updatedAt = now,
            // Neuer Ordner ans Ende der Liste: höchste Position im Eltern-Ordner + 1.
            position = (dao.getInFolder(parentId).maxOfOrNull { it.position } ?: 0L) + 1
        )
        dao.insert(folder)
        dirty()
        return folder
    }

    /** Reihenfolge zweier Ordner tauschen (1D-Drag&Drop, F6).
     *
     * Normalisiert die komplette Eltern-Liste neu (Indizes × 10), damit der
     * Tausch auch bei nicht-eindeutigen Alt-Positionen (alle 0) greift. */
    suspend fun swapFolderOrder(idA: String, idB: String) {
        if (idA == idB) return
        val a = dao.getById(idA) ?: return
        val b = dao.getById(idB) ?: return
        if (a.parentId != b.parentId) return
        val list = dao.getInFolder(a.parentId).toMutableList()
        val ia = list.indexOfFirst { it.id == idA }
        val ib = list.indexOfFirst { it.id == idB }
        if (ia < 0 || ib < 0) return
        val tmp = list[ia]
        list[ia] = list[ib]
        list[ib] = tmp
        val now = nowMs()
        list.forEachIndexed { index, folder ->
            dao.setPosition(folder.id, (index + 1).toLong() * 10, now)
        }
        dirty()
    }

    /** Ordner in einen anderen verschieben (F6, null = Wurzel).
     *  Der Ordner landet am Ende der Ziel-Liste (höchste Position). */
    suspend fun moveFolder(id: String, newParentId: String?): Boolean {
        if (id == newParentId) return false
        if (newParentId != null && dao.isDescendantOf(id, newParentId) > 0) return false
        val folder = dao.getById(id) ?: return false
        val maxPos = (dao.getInFolder(newParentId).maxOfOrNull { it.position } ?: 0L) + 1
        dao.update(folder.copy(parentId = newParentId, updatedAt = nowMs(), position = maxPos))
        dirty()
        return true
    }

    /** Umbenennen. */
    suspend fun renameFolder(id: String, newName: String) {
        val folder = dao.getById(id) ?: return
        dao.update(folder.copy(name = newName, updatedAt = nowMs()))
        dirty()
    }

    /** Alle nicht-geloeschten Ordner flach (fuer Verschieben-Picker). */
    suspend fun getAllFolders(): List<Folder> = dao.getAllOnce()

    suspend fun deleteFolder(id: String) {
        dao.softDelete(id, nowMs())
        dirty()
    }

    /** Finale Reihenfolge als Batch schreiben (optimistic Reorder, M7d-rev). */
    suspend fun applyOrder(ids: List<String>) {
        if (ids.isEmpty()) return
        val now = nowMs()
        db.withWriteTransaction {
            ids.forEachIndexed { index, id ->
                dao.setPosition(id, (index + 1).toLong() * 10, now)
            }
        }
        dirty()
    }

    /** Ordner in einem Eltern-Ordner einmalig laden (fuer explicit Refresh). */
    suspend fun getFoldersIn(parentId: String?): List<Folder> =
        dao.getInFolder(parentId)
}
