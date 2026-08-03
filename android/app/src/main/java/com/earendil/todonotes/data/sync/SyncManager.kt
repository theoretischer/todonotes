package com.earendil.todonotes.data.sync

import android.content.Context
import android.util.Log
import com.earendil.todonotes.data.TodoNotesDatabase
import com.earendil.todonotes.data.entity.CadenceType
import com.earendil.todonotes.data.entity.Folder
import com.earendil.todonotes.data.entity.Habit
import com.earendil.todonotes.data.entity.HabitHistoryEntry
import com.earendil.todonotes.data.entity.HabitLog
import com.earendil.todonotes.data.entity.Note
import com.earendil.todonotes.data.entity.NoteType
import com.earendil.todonotes.data.entity.Todo
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import com.jakewharton.retrofit2.converter.kotlinx.serialization.asConverterFactory
import java.util.concurrent.TimeUnit

/**
 * Führt einen Sync-Zyklus aus:
 *  1. Alle lokalen Zeilen einsammeln → ChangesBundle
 *  2. POST /sync mit last_synced_at + clientId
 *  3. Server-Änderungen in Room einspielen (REPLACE)
 *  4. last_synced_at = newSyncedAt persistieren
 *
 * Conflict Resolution passiert SERVER-seitig (Last-Write-Wins über updated_at).
 * Client-seitig akzeptieren wir alles, was der Server zurückgibt (er ist Quelle
 * der Wahrheit nach Konflikt-Auflösung).
 */
class SyncManager(context: Context) {

    private val db = TodoNotesDatabase.get(context.applicationContext)
    private val prefs = SyncPrefs(context.applicationContext)

    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
        explicitNulls = false
    }

    private val okHttp: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .addInterceptor(HttpLoggingInterceptor().apply {
                level = HttpLoggingInterceptor.Level.BASIC
            })
            .build()
    }

    private fun api(): SyncApi {
        val baseUrl = prefs.serverUrl.let { if (it.endsWith("/")) it else "$it/" }
        return Retrofit.Builder()
            .baseUrl(baseUrl)
            .client(okHttp)
            .addConverterFactory(json.asConverterFactory("application/json".toMediaType()))
            .build()
            .create(SyncApi::class.java)
    }

    /** Führt einen Sync aus. Liefert true bei Erfolg, false bei Fehler
     *  (Fehlermeldung steht dann in prefs.lastSyncResult). */
    suspend fun sync(): Boolean {
        if (!prefs.isConfigured) {
            prefs.lastSyncResult = "Nicht konfiguriert (Server-URL/Token fehlt)"
            return false
        }
        return try {
            val request = SyncRequest(
                lastSyncedAt = prefs.lastSyncedAt,
                clientId = prefs.clientId,
                changes = collectLocalChanges()
            )
            val response = api().sync("Bearer ${prefs.token}", request)
            applyServerChanges(response.serverChanges)
            prefs.lastSyncedAt = response.newSyncedAt
            prefs.lastSyncAt = System.currentTimeMillis()
            prefs.lastSyncResult = "OK"
            Log.i(TAG, "Sync erfolgreich: newSyncedAt=${response.newSyncedAt}")
            true
        } catch (e: Exception) {
            prefs.lastSyncResult = "Fehler: ${e.message ?: e.javaClass.simpleName}"
            Log.e(TAG, "Sync fehlgeschlagen", e)
            false
        }
    }

    /** Health-Check (ohne Token). Liefert true, wenn Server antwortet. */
    suspend fun health(): Boolean = try {
        api().health()
        true
    } catch (e: Exception) {
        Log.e(TAG, "Health-Check fehlgeschlagen", e)
        false
    }

    // --- lokal → DTO ---

    private suspend fun collectLocalChanges(): ChangesBundle {
        val todos = db.todoDao().getAllOnce().map { it.toDTO() }
        val habits = db.habitDao().getAllHabitsForSync().map { it.toDTO() }
        val logs = db.habitDao().getAllLogsForSync().map { it.toDTO() }
        val history = db.habitDao().getAllHistoryForSync().map { it.toDTO() }
        val folders = db.folderDao().getAllForSync().map { it.toDTO() }
        val notes = db.noteDao().getAllForSync().map { it.toDTO() }
        return ChangesBundle(
            todos = todos,
            habits = habits,
            habit_logs = logs,
            habit_history = history,
            folders = folders,
            notes = notes
        )
    }

    // --- DTO → lokal (Server gewinnt bei Konflikt) ---

    private suspend fun applyServerChanges(bundle: ChangesBundle) {
        if (bundle.todos.isNotEmpty()) db.todoDao().upsertAll(bundle.todos.map { it.toEntity() })
        if (bundle.habits.isNotEmpty()) db.habitDao().upsertAllHabits(bundle.habits.map { it.toEntity() })
        if (bundle.habit_logs.isNotEmpty()) db.habitDao().upsertAllLogs(bundle.habit_logs.map { it.toEntity() })
        if (bundle.habit_history.isNotEmpty()) db.habitDao().upsertAllHistory(bundle.habit_history.map { it.toEntity() })
        if (bundle.folders.isNotEmpty()) db.folderDao().upsertAll(bundle.folders.map { it.toEntity() })
        if (bundle.notes.isNotEmpty()) db.noteDao().upsertAll(bundle.notes.map { it.toEntity() })
    }

    companion object {
        private const val TAG = "SyncManager"

        // --- Mapper Todo <-> DTO ---
        fun Todo.toDTO() = TodoDTO(
            id, title, notes, dueAt, recurrence, completedAt, createdAt, updatedAt, deletedAt, logToHistory
        )

        fun TodoDTO.toEntity() = Todo(
            id, title, notes, dueAt, recurrence, completedAt, createdAt, updatedAt, deletedAt, logToHistory
        )

        // --- Mapper Habit <-> DTO ---
        fun Habit.toDTO() = HabitDTO(
            id, title, notes, cadenceType.name, interval, resetWeekday, resetAnchorDay,
            resetAnchorMonth, goalCount, startDate, logToHistory, lastLoggedPeriodStart,
            createdAt, updatedAt, deletedAt
        )

        fun HabitDTO.toEntity() = Habit(
            id, title, notes, CadenceType.valueOf(cadenceType), interval, resetWeekday,
            resetAnchorDay, resetAnchorMonth, goalCount, startDate, logToHistory,
            lastLoggedPeriodStart, createdAt, updatedAt, deletedAt
        )

        // --- Mapper HabitLog <-> DTO ---
        fun HabitLog.toDTO() = HabitLogDTO(id, habitId, timestamp, note)
        fun HabitLogDTO.toEntity() = HabitLog(id, habitId, timestamp, note)

        // --- Mapper HabitHistoryEntry <-> DTO ---
        fun HabitHistoryEntry.toDTO() = HabitHistoryEntryDTO(
            id, habitId, title, cadenceLabel, periodStart, count, goal, loggedAt
        )

        fun HabitHistoryEntryDTO.toEntity() = HabitHistoryEntry(
            id, habitId, title, cadenceLabel, periodStart, count, goal, loggedAt
        )

        // --- Mapper Folder <-> DTO ---
        fun Folder.toDTO() = FolderDTO(id, parentId, name, createdAt, updatedAt, deletedAt)
        fun FolderDTO.toEntity() = Folder(id, parentId, name, createdAt, updatedAt, deletedAt)

        // --- Mapper Note <-> DTO ---
        fun Note.toDTO() = NoteDTO(id, folderId, type.name, title, bodyJson, createdAt, updatedAt, deletedAt)
        fun NoteDTO.toEntity() = Note(
            id, folderId,
            runCatching { NoteType.valueOf(type) }.getOrDefault(NoteType.NOTE),
            title, bodyJson, createdAt, updatedAt, deletedAt
        )
    }
}
