package com.earendil.todonotes.data.sync

import com.earendil.todonotes.data.TodoNotesDatabase
import com.earendil.todonotes.data.entity.CadenceType
import com.earendil.todonotes.data.entity.ChatMessage
import com.earendil.todonotes.data.entity.Folder
import com.earendil.todonotes.data.entity.Habit
import com.earendil.todonotes.data.entity.HabitHistoryEntry
import com.earendil.todonotes.data.entity.HabitLog
import com.earendil.todonotes.data.entity.Note
import com.earendil.todonotes.data.entity.NoteType
import com.earendil.todonotes.data.entity.Todo
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.bearerAuth
import io.ktor.client.request.get
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.contentType
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.datetime.Clock
import kotlinx.serialization.json.Json

/**
 * Führt einen Sync-Zyklus aus (M5: Ktor statt Retrofit):
 *  1. Alle lokalen Zeilen einsammeln → ChangesBundle
 *  2. POST /sync mit last_synced_at + clientId
 *  3. Server-Änderungen in Room einspielen (REPLACE)
 *  4. last_synced_at = newSyncedAt persistieren
 *
 * Conflict Resolution passiert SERVER-seitig (Last-Write-Wins über updated_at).
 * Client-seitig akzeptieren wir alles, was der Server zurückgibt.
 *
 * Der HttpClient wird von außen reingereicht (shared mit AuthManager),
 * damit Engine + Config nur einmal erstellt werden.
 */
class SyncManager(
    private val db: TodoNotesDatabase,
    private val prefs: SyncPrefs,
    private val httpClient: HttpClient
) {

    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
        explicitNulls = false
    }

    /** Aktuelle userId für lokale Entities (aus prefs, nicht hardcoded). */
    private val userId get() = prefs.userId

    /** Auto-Sync: debounced. Wenn lokale Änderung → markDirty() →
     *  nach 300ms sync(). Mehrere schnelle Änderungen werden gebündelt. */
    private val _dirty = MutableStateFlow(0)
    private var scope: CoroutineScope? = null

    /** Mutex: nur ein sync() zur Zeit (vermeidet SQLITE_BUSY auf Wasm
     *  mit single-connection-pool). Auto-Sync + SSE-Push konkurrieren sonst. */
    private val syncMutex = Mutex()

    /** Wird aufgerufen wenn lokale Daten geändert wurden (Repositories).
     *  Triggert einen debounced Auto-Sync (300ms). */
    fun markDirty() {
        _dirty.value++
    }

    /** SSE-Push: Server hat neue Daten → sofort pullen. */
    fun onRemoteChanged() {
        scope?.launch { sync() }
    }

    /** Auto-Sync starten. Einmal nach Login aufrufen. */
    fun startAutoSync(s: CoroutineScope) {
        scope = s
        s.launch {
            _dirty
                .filter { it > 0 }
                .debounce(300)
                .collect { sync() }
        }
    }

    /** Führt einen Sync aus. Liefert true bei Erfolg, false bei Fehler
     *  (Fehlermeldung steht dann in prefs.lastSyncResult). */
    suspend fun sync(): Boolean {
        return syncMutex.withLock { syncInternal() }
    }

    private suspend fun syncInternal(): Boolean {
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
            val response: SyncResponse = httpClient.post("${prefs.serverUrl}/sync") {
                contentType(ContentType.Application.Json)
                bearerAuth(prefs.token)
                setBody(request)
            }.body()
            applyServerChanges(response.serverChanges)
            prefs.lastSyncedAt = response.newSyncedAt
            prefs.lastSyncAt = Clock.System.now().toEpochMilliseconds()
            prefs.lastSyncResult = "OK"
            true
        } catch (e: Exception) {
            prefs.lastSyncResult = "Fehler: ${e.message ?: e::class.simpleName}"
            false
        }
    }

    /** Health-Check (ohne Token). Liefert true, wenn Server antwortet. */
    suspend fun health(): Boolean = try {
        httpClient.get("${prefs.serverUrl}/health")
        true
    } catch (e: Exception) {
        prefs.lastSyncResult = "Verbindung fehlgeschlagen: ${e.message ?: e::class.simpleName}"
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
        val chatMessages = db.chatMessageDao().getAllForSync().map { it.toDTO() }
        return ChangesBundle(
            todos = todos,
            habits = habits,
            habit_logs = logs,
            habit_history = history,
            folders = folders,
            notes = notes,
            chat_messages = chatMessages
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
        if (bundle.chat_messages.isNotEmpty()) db.chatMessageDao().upsertAll(bundle.chat_messages.map { it.toEntity() })
    }

    // --- Mapper (Instanz-Methoden, userId aus prefs) ---

    private fun Todo.toDTO() = TodoDTO(
        id, title, notes, dueAt, recurrence, completedAt, createdAt, updatedAt, deletedAt, logToHistory
    )

    private fun TodoDTO.toEntity() = Todo(
        id, title, notes, dueAt, recurrence, completedAt, createdAt, updatedAt, deletedAt, logToHistory, userId = userId
    )

    private fun Habit.toDTO() = HabitDTO(
        id, title, notes, cadenceType.name, interval, resetWeekday, resetAnchorDay,
        resetAnchorMonth, goalCount, startDate, logToHistory, lastLoggedPeriodStart,
        createdAt, updatedAt, deletedAt
    )

    private fun HabitDTO.toEntity() = Habit(
        id, title, notes, CadenceType.valueOf(cadenceType), interval, resetWeekday,
        resetAnchorDay, resetAnchorMonth, goalCount, startDate, logToHistory,
        lastLoggedPeriodStart, createdAt, updatedAt, deletedAt, userId = userId
    )

    private fun HabitLog.toDTO() = HabitLogDTO(id, habitId, timestamp, note)
    private fun HabitLogDTO.toEntity() = HabitLog(id, habitId, timestamp, note, userId = userId)

    private fun HabitHistoryEntry.toDTO() = HabitHistoryEntryDTO(
        id, habitId, title, cadenceLabel, periodStart, count, goal, loggedAt
    )

    private fun HabitHistoryEntryDTO.toEntity() = HabitHistoryEntry(
        id, habitId, title, cadenceLabel, periodStart, count, goal, loggedAt, userId = userId
    )

    private fun Folder.toDTO() = FolderDTO(id, parentId, name, createdAt, updatedAt, deletedAt)
    private fun FolderDTO.toEntity() = Folder(id, parentId, name, createdAt, updatedAt, deletedAt, userId = userId)

    private fun Note.toDTO() = NoteDTO(id, folderId, type.name, title, bodyJson, createdAt, updatedAt, deletedAt)
    private fun NoteDTO.toEntity() = Note(
        id, folderId,
        runCatching { NoteType.valueOf(type) }.getOrDefault(NoteType.NOTE),
        title, bodyJson, createdAt, updatedAt, deletedAt, userId = userId
    )

    private fun ChatMessage.toDTO() = ChatMessageDTO(
        id, noteId, text, createdAt, updatedAt, deletedAt, position, quotedMessageId
    )
    private fun ChatMessageDTO.toEntity() = ChatMessage(
        id, noteId, text, createdAt, updatedAt, deletedAt, position, quotedMessageId, userId = userId
    )
}
