package com.earendil.todonotes.data.repo

import android.content.Context
import com.earendil.todonotes.data.TodoNotesDatabase
import com.earendil.todonotes.data.entity.Folder
import kotlinx.coroutines.flow.Flow
import java.util.UUID

/**
 * Repository für Ordner (Block F2).
 *
 * Ordner bilden eine Hierarchie über parentId (null = Wurzel). Beim
 * Verschieben muss Zyklenfreiheit geprüft werden (ein Ordner darf nicht
 * in sich selbst oder einen seiner Nachfahren verschoben werden) — das
 * macht [canMoveFolderInto] via rekursiver CTE im DAO.
 */
class FolderRepository(context: Context) {

    private val dao = TodoNotesDatabase.get(context).folderDao()

    /** Ordner auf der Wurzel-Ebene (parentId == null). */
    fun observeRootFolders(): Flow<List<Folder>> = dao.observeFoldersIn(null)

    /** Ordner direkt unter [parentId]. */
    fun observeFoldersIn(parentId: String): Flow<List<Folder>> = dao.observeFoldersIn(parentId)

    suspend fun getById(id: String): Folder? = dao.getById(id)

    suspend fun createFolder(name: String, parentId: String? = null): Folder {
        val now = System.currentTimeMillis()
        val folder = Folder(
            id = UUID.randomUUID().toString(),
            parentId = parentId,
            name = name,
            createdAt = now,
            updatedAt = now
        )
        dao.insert(folder)
        return folder
    }

    /** Umbenennen. */
    suspend fun renameFolder(id: String, newName: String) {
        val folder = dao.getById(id) ?: return
        dao.update(folder.copy(name = newName, updatedAt = System.currentTimeMillis()))
    }

    /**
     * Verschiebt [id] unter [newParentId] (null = Wurzel).
     * Verweigert die Aktion, wenn [newParentId] ein Nachfahre von [id] ist
     * (Zyklus) oder gleich [id] ist. Liefert true bei Erfolg.
     */
    suspend fun moveFolder(id: String, newParentId: String?): Boolean {
        if (id == newParentId) return false
        if (newParentId != null && dao.isDescendantOf(id, newParentId) > 0) return false
        val folder = dao.getById(id) ?: return false
        dao.update(folder.copy(parentId = newParentId, updatedAt = System.currentTimeMillis()))
        return true
    }

    suspend fun deleteFolder(id: String) {
        dao.softDelete(id, System.currentTimeMillis())
    }
}
