package com.earendil.todonotes.data.dao

import androidx.room3.Dao
import androidx.room3.Insert
import androidx.room3.OnConflictStrategy
import androidx.room3.Query
import androidx.room3.Update
import com.earendil.todonotes.data.entity.Todo
import kotlinx.coroutines.flow.Flow

@Dao
interface TodoDao {

    /** Alle offenen Todos (nicht erledigt, nicht gelöscht), sortiert nach Fälligkeit. */
    @Query(
        """
        SELECT * FROM todos
        WHERE completedAt IS NULL AND deletedAt IS NULL
        ORDER BY
            CASE WHEN dueAt IS NULL THEN 1 ELSE 0 END,
            dueAt ASC,
            createdAt ASC
        """
    )
    fun observeOpenTodos(): Flow<List<Todo>>

    /** Alle abgeschlossenen Todos (Verlauf), neueste zuerst. */
    @Query(
        """
        SELECT * FROM todos
        WHERE completedAt IS NOT NULL AND deletedAt IS NULL
        ORDER BY completedAt DESC
        """
    )
    fun observeCompletedTodos(): Flow<List<Todo>>

    @Query("SELECT * FROM todos WHERE id = :id")
    suspend fun getById(id: String): Todo?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(todo: Todo)

    @Update
    suspend fun update(todo: Todo)

    @Query("UPDATE todos SET deletedAt = :now, updatedAt = :now WHERE id = :id")
    suspend fun softDelete(id: String, now: Long)

    @Query("UPDATE todos SET completedAt = :completedAt, updatedAt = :completedAt WHERE id = :id")
    suspend fun markCompleted(id: String, completedAt: Long)

    // --- Sync ---

    /** Alle Todos einmalig (für Sync-Upstream). */
    @Query("SELECT * FROM todos")
    suspend fun getAllOnce(): List<Todo>

    /** Server-Änderungen einspielen (REPLACE — Server ist Quelle der Wahrheit). */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(todos: List<Todo>)
}
