package com.earendil.todonotes

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Repeat
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.History
import androidx.compose.material.icons.outlined.Repeat
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import com.earendil.todonotes.ui.AuthViewModel
import com.earendil.todonotes.ui.ChatViewModel
import com.earendil.todonotes.ui.HabitViewModel
import com.earendil.todonotes.ui.NoteEditorViewModel
import com.earendil.todonotes.ui.NotesViewModel
import com.earendil.todonotes.ui.TodoViewModel
import com.earendil.todonotes.ui.auth.AuthGate
import com.earendil.todonotes.ui.BackHandler
import com.earendil.todonotes.ui.chat.ChatScreen
import com.earendil.todonotes.ui.habits.HabitsScreen
import com.earendil.todonotes.ui.habits.TrackerDetailScreen
import com.earendil.todonotes.ui.history.HistoryScreen
import com.earendil.todonotes.ui.notes.NoteEditorScreen
import com.earendil.todonotes.ui.notes.NotesScreen
import com.earendil.todonotes.ui.settings.SettingsScreen
import com.earendil.todonotes.ui.todos.TodosScreen
import com.earendil.todonotes.ui.components.AvatarImage
import com.earendil.todonotes.ui.components.rememberImageBitmap
import androidx.compose.runtime.collectAsState
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.union
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.navigationBars

/**
 * Haupt-UI der App (M7b — commonMain).
 *
 * Navigation manuell: 4 Tabs via `when(currentTab)` + State-Flags für
 * NoteEditor/Settings/Chat (kommen in M7d).
 *
 * Momentan implementiert: nur der Todos-Tab + Profil-Icon oben rechts
 * (öffnet Settings — kommt M7d, vorerst noop).
 */
@Composable
fun TodoNotesApp(
    container: AppContainer
) {
    // Auth-Gate: prüft beim Start ob eingeloggt → sonst Login/Setup.
    val authVm = remember { AuthViewModel(container.authManager, container.syncManager, container.syncPrefs, container.appScope) }
    var isAuthenticated by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        // Web: defaultServerUrl = window.location.origin → automatisch gesetzt.
        // App: defaultServerUrl = "" → NeedsServerUrl.
        if (container.syncPrefs.serverUrl.isBlank() && getPlatform().defaultServerUrl.isNotBlank()) {
            container.syncPrefs.serverUrl = getPlatform().defaultServerUrl
        }
        authVm.checkAuth()
    }

    if (!isAuthenticated) {
        AuthGate(vm = authVm, onAuthenticated = {
            isAuthenticated = true
            // Auto-Sync + SSE starten (Echtzeit-Sync).
            container.syncManager.startAutoSync(container.appScope)
            container.sseClient.start(container.appScope)
        })
        return
    }

    val todoVm = remember { TodoViewModel(container.todoRepository, container.appScope) }
    val openTodos by todoVm.openTodos.collectAsState()
    val completedTodos by todoVm.completedTodos.collectAsState()

    val habitVm = remember { HabitViewModel(container.habitRepository, container.appScope) }
    val habitsWithProgress by habitVm.habitsWithProgress.collectAsState()
    val habitHistory by habitVm.habitHistory.collectAsState()

    // Periodenwechsel beim App-Start prüfen (legt ggf. Verlaufseinträge an).
    LaunchedEffect(Unit) { habitVm.checkPeriodsOnStart() }

    val notesVm = remember { NotesViewModel(container.folderRepository, container.noteRepository, container.appScope) }

    // Editor-Overlay-State (M7d-4 verkabelt Settings/Chat später)
    var editorState by remember { mutableStateOf<EditorTarget?>(null) }
    val editorVm = remember { NoteEditorViewModel(container.noteRepository, container.appScope) }
    val chatVm = remember { ChatViewModel(container.chatMessageRepository, container.noteRepository, container.appScope) }

    // Optimistic UI: wenn der Editor/Chat eine Notiz speichert, die Liste sofort
    // (ohne DB-Roundtrip) updaten — der neue Titel/Body erscheint sofort.
    LaunchedEffect(editorVm, chatVm) {
        editorVm.onNoteUpdated = { id, title, body ->
            notesVm.updateNoteOptimistic(id, title, body)
        }
        chatVm.onNoteUpdated = { id, title ->
            notesVm.updateNoteOptimistic(id, title, notesVm.browserState.value.notes.firstOrNull { it.id == id }?.bodyJson ?: "")
        }
    }

    var currentTab by remember { mutableStateOf(Tab.Todos) }
    var showSettings by remember { mutableStateOf(false) }
    // Zufriedenheits-Tracker-Detail (Grafik-Overlay)
    var trackerDetailId by remember { mutableStateOf<String?>(null) }

    // Profil-Daten für Avatar oben rechts.
    val profile by authVm.profile.collectAsState()
    val avatarBytes by authVm.avatarBytes.collectAsState()

    Scaffold(
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        bottomBar = {
            NavigationBar(
                modifier = Modifier.windowInsetsPadding(WindowInsets.ime.union(WindowInsets.navigationBars))
            ) {
                Tab.entries.forEach { tab ->
                    NavigationBarItem(
                        selected = currentTab == tab,
                        onClick = { currentTab = tab },
                        icon = {
                            Icon(
                                if (currentTab == tab) tab.selected else tab.unselected,
                                contentDescription = tab.label
                            )
                        },
                        label = { Text(tab.label) }
                    )
                }
            }
        }
    ) { padding ->
        Box(Modifier.fillMaxSize().padding(bottom = padding.calculateBottomPadding())) {
            when (currentTab) {
                Tab.Todos -> TodosScreen(
                    todos = openTodos,
                    onCreateTodo = todoVm::createTodo,
                    onEditTodo = todoVm::updateTodo,
                    onCompleteTodo = todoVm::completeTodo,
                    onDeleteTodo = todoVm::deleteTodo,
                    modifier = Modifier.fillMaxSize()
                )
                Tab.Habits -> HabitsScreen(
                    habits = habitsWithProgress,
                    onCreateHabit = habitVm::createHabit,
                    onEditHabit = habitVm::updateHabit,
                    onLogHabit = habitVm::logHabit,
                    onDeleteHabit = habitVm::deleteHabit,
                    onFinishPeriod = habitVm::finishCurrentPeriod,
                    onRatingChange = habitVm::changeRating,
                    onOpenTracker = { trackerDetailId = it },
                    onSwapHabits = habitVm::reorderHabits,
                    onBeginHabitReorder = habitVm::beginHabitReorder,
                    onCommitHabitReorder = habitVm::commitHabitReorder,
                    modifier = Modifier.fillMaxSize()
                )
                Tab.Notes -> NotesScreen(
                    notesVm = notesVm,
                    onOpenNote = { id, isNew, type ->
                        // Optimistic: initial-Daten aus der Liste uebergeben,
                        // damit der Editor sofort rendert (kein DB-Roundtrip).
                        val note = notesVm.browserState.value.notes.firstOrNull { it.id == id }
                        val initialTitle = if (!isNew) note?.title else null
                        val initialBody = if (!isNew) note?.bodyJson else null
                        editorState = EditorTarget(id, isNew, isChat = type == com.earendil.todonotes.data.entity.NoteType.CHAT, initialTitle = initialTitle, initialBody = initialBody)
                    },
                    onOpenChat = { id, isNew ->
                        val note = notesVm.browserState.value.notes.firstOrNull { it.id == id }
                        val initialTitle = if (!isNew) note?.title else null
                        val initialBody = if (!isNew) note?.bodyJson else null
                        editorState = EditorTarget(id, isNew, isChat = true, initialTitle = initialTitle, initialBody = initialBody)
                    },
                    modifier = Modifier.fillMaxSize()
                )
                Tab.History -> HistoryScreen(
                    completedTodos = completedTodos,
                    habitHistory = habitHistory,
                    onReopenTodo = todoVm::reopenTodo,
                    onDeleteTodo = todoVm::deleteTodo,
                    onDeleteHistoryEntry = habitVm::deleteHistoryEntry,
                    modifier = Modifier.fillMaxSize()
                )
            }

            // Avatar oben rechts → öffnet Settings
            AvatarTopRight(
                avatarBytes = avatarBytes,
                displayName = profile?.displayName ?: profile?.username,
                onClick = { showSettings = true },
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .statusBarsPadding()
                    .padding(end = 12.dp, top = 8.dp)
            )
        }
    }

    // System-Back: Overlays schließen statt App zu beenden (Android-Back-Taste).
    // Zuletzt registrierter Handler gewinnt — Overlays liegen über dem Scaffold.
    BackHandler(enabled = editorState == null && trackerDetailId == null && showSettings) {
        showSettings = false
    }

    // Settings als Fullscreen-Overlay
    if (showSettings) {
        SettingsScreen(
            vm = authVm,
            onBack = { showSettings = false },
            onLogout = {
                showSettings = false
                container.sseClient.stop()
                authVm.logout()
                isAuthenticated = false
            }
        )
    }

    // Editor/Chat als Fullscreen-Overlay über dem Scaffold
    editorState?.let { target ->
        if (target.isChat) {
            ChatScreen(
                noteId = target.id,
                initialTitle = target.initialTitle,
                vm = chatVm,
                onBack = { editorState = null }
            )
        } else {
            NoteEditorScreen(
                noteId = target.id,
                isNew = target.isNew,
                initialTitle = target.initialTitle,
                initialBody = target.initialBody,
                vm = editorVm,
                onBack = { editorState = null }
            )
        }
    }

    // Zufriedenheits-Tracker-Detail als Fullscreen-Overlay.
    // Habit live aus dem State → optimistic Rating-Updates rendern sofort.
    trackerDetailId?.let { id ->
        val habit = habitsWithProgress.firstOrNull { it.habit.id == id }?.habit
        // Reaktive Log-Liste für die Perioden-Grafik (HABIT-Typ).
        val logs by remember(id) { habitVm.observeLogs(id) }.collectAsState(initial = emptyList())
        if (habit != null) {
            TrackerDetailScreen(
                habit = habit,
                history = habitHistory.filter { it.habitId == id },
                logs = logs,
                onRatingChange = habitVm::changeRating,
                onLogHabit = habitVm::logHabit,
                onEditHabit = habitVm::updateHabit,
                onBack = { trackerDetailId = null }
            )
        }
    }
    // Gelöschter Tracker → Overlay schließen.
    LaunchedEffect(trackerDetailId, habitsWithProgress) {
        if (trackerDetailId != null &&
            habitsWithProgress.none { it.habit.id == trackerDetailId }
        ) {
            trackerDetailId = null
        }
    }
}

/** Ziel des Editor-Overlays (Notiz-Editor oder Chat, M7d). */
private data class EditorTarget(
    val id: String,
    val isNew: Boolean,
    val isChat: Boolean,
    val initialTitle: String? = null,
    val initialBody: String? = null
)

private enum class Tab(val label: String, val selected: ImageVector, val unselected: ImageVector) {
    Todos("Aufgaben", Icons.Filled.CheckCircle, Icons.Outlined.CheckCircle),
    Habits("Gewohnheiten", Icons.Filled.Repeat, Icons.Outlined.Repeat),
    Notes("Notizen", Icons.Filled.Edit, Icons.Outlined.Edit),
    History("Verlauf", Icons.Filled.History, Icons.Outlined.History)
}

@Composable
private fun ComingSoon(label: String, modifier: Modifier = Modifier) {
    Box(modifier = modifier, contentAlignment = Alignment.Center) {
        Text(
            "$label — kommt in M7c/d",
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

/** Avatar oben rechts: Profilbild oder Initialen. */
@Composable
private fun AvatarTopRight(
    avatarBytes: ByteArray?,
    displayName: String?,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val bitmap = rememberImageBitmap(avatarBytes)
    Surface(
        onClick = onClick,
        modifier = modifier.size(40.dp),
        shape = CircleShape,
        shadowElevation = 2.dp
    ) {
        AvatarImage(
            bitmap = bitmap,
            displayName = displayName,
            sizeDp = 40
        )
    }
}
