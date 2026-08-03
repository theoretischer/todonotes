package com.earendil.todonotes

import android.Manifest
import android.app.AlarmManager
import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.PowerManager
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.ui.unit.dp
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountCircle
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Repeat
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.History
import androidx.compose.material.icons.outlined.Repeat
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.foundation.layout.WindowInsets
import androidx.core.content.ContextCompat
import androidx.lifecycle.viewmodel.compose.viewModel
import com.earendil.todonotes.data.repo.ChatMessageRepository
import com.earendil.todonotes.data.repo.FolderRepository
import com.earendil.todonotes.data.repo.HabitRepository
import com.earendil.todonotes.data.repo.NoteRepository
import com.earendil.todonotes.data.repo.TodoRepository
import com.earendil.todonotes.data.entity.NoteType
import com.earendil.todonotes.ui.HabitViewModel
import com.earendil.todonotes.ui.NotesViewModel
import com.earendil.todonotes.ui.SyncViewModel
import com.earendil.todonotes.ui.TodoViewModel
import com.earendil.todonotes.ui.chat.ChatScreen
import com.earendil.todonotes.ui.habits.HabitsScreen
import com.earendil.todonotes.ui.history.HistoryScreen
import com.earendil.todonotes.ui.notes.NoteEditorScreen
import com.earendil.todonotes.ui.notes.NotesScreen
import com.earendil.todonotes.ui.settings.SettingsScreen
import com.earendil.todonotes.ui.theme.TodoNotesTheme
import com.earendil.todonotes.ui.todos.TodosScreen

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Edge-to-Edge mit korrekten Systemleisten-Farben.
        // Icon-Farbe (hell/dunkel) folgt der Hell/Dunkel-Erscheinung des Systems,
        // damit die Android-Elemente (Status-/Navigationsleiste) im Dark Mode
        // lesbar sind (weisse Icons auf schwarzem Hintergrund).
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.auto(
                android.graphics.Color.TRANSPARENT,
                android.graphics.Color.TRANSPARENT
            ),
            navigationBarStyle = SystemBarStyle.auto(
                android.graphics.Color.TRANSPARENT,
                android.graphics.Color.TRANSPARENT
            )
        )

        // Erster Start: Notification-Permission anfragen
        val requestNotificationPermission = registerForActivityResult(
            ActivityResultContracts.RequestPermission()
        ) { }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS)
            != PackageManager.PERMISSION_GRANTED
        ) {
            requestNotificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
        }

        val repo = TodoRepository(applicationContext)
        val habitRepo = HabitRepository(applicationContext)
        val folderRepo = FolderRepository(applicationContext)
        val noteRepo = NoteRepository(applicationContext)
        val chatRepo = ChatMessageRepository(applicationContext)

        setContent {
            TodoNotesTheme {
                TodoNotesApp(repo, habitRepo, folderRepo, noteRepo, chatRepo)
            }
        }
    }
}

private enum class Tab(val label: String, val selected: ImageVector, val unselected: ImageVector) {
    Todos("Aufgaben", Icons.Filled.CheckCircle, Icons.Outlined.CheckCircle),
    Habits("Gewohnheiten", Icons.Filled.Repeat, Icons.Outlined.Repeat),
    Notes("Notizen", Icons.Filled.Edit, Icons.Outlined.Edit),
    History("Verlauf", Icons.Filled.History, Icons.Outlined.History)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TodoNotesApp(
    repo: TodoRepository,
    habitRepo: HabitRepository,
    folderRepo: FolderRepository,
    noteRepo: NoteRepository,
    chatRepo: ChatMessageRepository
) {
    val vm: TodoViewModel = viewModel(factory = TodoViewModel.Factory(repo))
    val habitVm: HabitViewModel = viewModel(factory = HabitViewModel.Factory(habitRepo))
    val notesVm: NotesViewModel = viewModel(factory = NotesViewModel.Factory(folderRepo, noteRepo))
    val syncVm: SyncViewModel = viewModel()
    var currentTab by remember { mutableStateOf(Tab.Todos) }
    var showSettings by remember { mutableStateOf(false) }
    var openNoteId by remember { mutableStateOf<String?>(null) }
    var openNoteIsNew by remember { mutableStateOf(false) }
    var openNoteType by remember { mutableStateOf<NoteType>(NoteType.NOTE) }

    val openTodos by vm.openTodos.collectAsState()
    val completedTodos by vm.completedTodos.collectAsState()
    val habits by habitVm.habitsWithProgress.collectAsState()
    val habitHistory by habitVm.habitHistory.collectAsState()

    Scaffold(
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        bottomBar = {
            NavigationBar {
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
        // Nur unten paddeln: hebt die Screens (und ihre FABs unten rechts)
        // ueber die NavigationBar an, ohne den getunten Top-Abstand (96dp)
        // zu veraendern. komplettes padding.padding wuerde den Top-Abstand
        // der Screens um die Statusleisten-Hoehe verschieben.
        Box(Modifier.fillMaxSize().padding(bottom = padding.calculateBottomPadding())) {
            when (currentTab) {
            Tab.Todos -> TodosScreen(
                todos = openTodos,
                onCreateTodo = vm::createTodo,
                onEditTodo = vm::updateTodo,
                onCompleteTodo = vm::completeTodo,
                onDeleteTodo = vm::deleteTodo,
                modifier = Modifier.fillMaxSize()
            )
            Tab.Habits -> HabitsScreen(
                habits = habits,
                onCreateHabit = habitVm::createHabit,
                onEditHabit = habitVm::updateHabit,
                onLogHabit = habitVm::logHabit,
                onDeleteHabit = habitVm::deleteHabit,
                onFinishPeriod = habitVm::finishCurrentPeriod,
                modifier = Modifier.fillMaxSize()
            )
            Tab.Notes -> NotesScreen(
                notesVm = notesVm,
                onOpenNote = { noteId, isNew, type ->
                    openNoteId = noteId
                    openNoteIsNew = isNew
                    openNoteType = type
                },
                onOpenChat = { noteId, isNew ->
                    openNoteId = noteId
                    openNoteIsNew = isNew
                    openNoteType = NoteType.CHAT
                },
                modifier = Modifier.fillMaxSize()
            )
            Tab.History -> HistoryScreen(
                completedTodos = completedTodos,
                habitHistory = habitHistory,
                onReopenTodo = vm::reopenTodo,
                onDeleteTodo = vm::deleteTodo,
                onDeleteHistoryEntry = habitVm::deleteHistoryEntry,
                modifier = Modifier.fillMaxSize()
            )
            }

            // Rechte obere Ecke: User-Icon als unabhaengiger Kreis
            // (ohne TopAppBar / TodoNotes-Schriftzug), schwebt ueber dem
            // Inhalt (wird NACH dem Tab-Inhalt gezeichnet = oben drauf),
            // Tap oeffnet die Einstellungen (Sync/Backend).
            UserCircleIcon(
                onClick = { showSettings = true },
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .statusBarsPadding()
                    .padding(end = 12.dp, top = 8.dp)
            )
        }
    }

    // Notiz-Editor oder Chat (Vollbild, über alles gelegt)
    openNoteId?.let { id ->
        if (openNoteType == NoteType.CHAT) {
            ChatScreen(
                noteId = id,
                chatRepo = chatRepo,
                noteRepo = noteRepo,
                onBack = { openNoteId = null }
            )
        } else {
            NoteEditorScreen(
                noteId = id,
                isNew = openNoteIsNew,
                noteRepo = noteRepo,
                onBack = { openNoteId = null }
            )
        }
    }

    // Vollbild-Einstellungen (über alles gelegt)
    if (showSettings) {
        Box(modifier = Modifier.fillMaxSize()) {
            SettingsScreen(
                syncVm = syncVm,
                onBack = { showSettings = false }
            )
        }
    }
}

/** User-Icon als unabhaengiger Kreis oben rechts (ohne TopAppBar). */
@Composable
private fun UserCircleIcon(
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    androidx.compose.material3.Surface(
        onClick = onClick,
        modifier = modifier.size(40.dp),
        shape = CircleShape,
        color = MaterialTheme.colorScheme.primaryContainer,
        shadowElevation = 2.dp
    ) {
        Box(contentAlignment = Alignment.Center) {
            Icon(
                Icons.Filled.AccountCircle,
                contentDescription = "Profil",
                tint = MaterialTheme.colorScheme.onPrimaryContainer,
                modifier = Modifier.size(24.dp)
            )
        }
    }
}
