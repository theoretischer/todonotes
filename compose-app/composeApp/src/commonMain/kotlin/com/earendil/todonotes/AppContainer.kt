package com.earendil.todonotes

import com.earendil.todonotes.data.TodoNotesDatabase
import com.earendil.todonotes.data.buildDatabase
import com.earendil.todonotes.data.repo.ChatMessageRepository
import com.earendil.todonotes.data.repo.FolderRepository
import com.earendil.todonotes.data.repo.HabitRepository
import com.earendil.todonotes.data.repo.NoteRepository
import com.earendil.todonotes.data.repo.TodoRepository
import com.earendil.todonotes.data.sync.AuthManager
import com.earendil.todonotes.data.sync.SyncManager
import com.earendil.todonotes.data.sync.SseClient
import com.earendil.todonotes.data.sync.SyncPrefs
import com.earendil.todonotes.data.sync.createHttpClient
import com.earendil.todonotes.notification.AlarmScheduler
import io.ktor.client.HttpClient
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob

/**
 * Service-Locator für die App (M7a — commonMain).
 *
 * Zentrale Abhängigkeits-Erstellung: Datenbank, Repositories, Sync-Stack.
 * Wird pro Plattform-Einstiegspunkt einmal erstellt und an [App] übergeben.
 *
 * Da alles shared ist, ist das der einzige Platz, an dem die DB gebaut wird —
 * kein Context/Platform-spezifisches Wissen hier (das passiert in
 * [getDatabaseBuilder] expect/actual).
 */
class AppContainer(
    private val alarmScheduler: AlarmScheduler
) {
    val database: TodoNotesDatabase = buildDatabase()

    // Sync-Stack (M5) — vor Repos, damit diese markDirty() aufrufen können.
    val syncPrefs: SyncPrefs = SyncPrefs()
    val httpClient: HttpClient = createHttpClient()
    val syncManager: SyncManager = SyncManager(database, syncPrefs, httpClient)
    val authManager: AuthManager = AuthManager(syncPrefs, httpClient)
    val sseClient: SseClient = SseClient(httpClient, syncPrefs, syncManager)

    // Repositories (markDirty → Auto-Sync)
    val todoRepository: TodoRepository = TodoRepository(database, alarmScheduler, syncManager)
    val habitRepository: HabitRepository = HabitRepository(database, syncManager)
    val folderRepository: FolderRepository = FolderRepository(database, syncManager)
    val noteRepository: NoteRepository = NoteRepository(database, syncManager)
    val chatMessageRepository: ChatMessageRepository = ChatMessageRepository(database, syncManager)

    /** Globaler Coroutine-Scope für Hintergrund-Arbeit (z.B. Auto-Sync, M8). */
    val appScope: CoroutineScope = CoroutineScope(SupervisorJob())

    /** Aufräumen (z.B. HttpClient schließen). Bei App-Shutdown. */
    fun close() {
        httpClient.close()
    }
}
