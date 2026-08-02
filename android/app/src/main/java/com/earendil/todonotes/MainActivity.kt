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
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
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
import androidx.core.content.ContextCompat
import androidx.lifecycle.viewmodel.compose.viewModel
import com.earendil.todonotes.data.repo.FolderRepository
import com.earendil.todonotes.data.repo.HabitRepository
import com.earendil.todonotes.data.repo.NoteRepository
import com.earendil.todonotes.data.repo.TodoRepository
import com.earendil.todonotes.ui.HabitViewModel
import com.earendil.todonotes.ui.NotesViewModel
import com.earendil.todonotes.ui.SyncViewModel
import com.earendil.todonotes.ui.TodoViewModel
import com.earendil.todonotes.ui.habits.HabitsScreen
import com.earendil.todonotes.ui.history.HistoryScreen
import com.earendil.todonotes.ui.notes.NotesScreen
import com.earendil.todonotes.ui.settings.ProfileSheet
import com.earendil.todonotes.ui.settings.SettingsScreen
import com.earendil.todonotes.ui.theme.TodoNotesTheme
import com.earendil.todonotes.ui.todos.TodosScreen

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

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

        setContent {
            TodoNotesTheme {
                TodoNotesApp(repo, habitRepo, folderRepo, noteRepo)
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
    noteRepo: NoteRepository
) {
    val vm: TodoViewModel = viewModel(factory = TodoViewModel.Factory(repo))
    val habitVm: HabitViewModel = viewModel(factory = HabitViewModel.Factory(habitRepo))
    val notesVm: NotesViewModel = viewModel(factory = NotesViewModel.Factory(folderRepo, noteRepo))
    val syncVm: SyncViewModel = viewModel()
    var currentTab by remember { mutableStateOf(Tab.Todos) }
    var showProfile by remember { mutableStateOf(false) }
    var showSettings by remember { mutableStateOf(false) }

    val openTodos by vm.openTodos.collectAsState()
    val completedTodos by vm.completedTodos.collectAsState()
    val habits by habitVm.habitsWithProgress.collectAsState()
    val habitHistory by habitVm.habitHistory.collectAsState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("TodoNotes") },
                actions = {
                    IconButton(onClick = { showProfile = true }) {
                        Icon(Icons.Filled.AccountCircle, contentDescription = "Profil")
                    }
                }
            )
        },
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
        when (currentTab) {
            Tab.Todos -> TodosScreen(
                todos = openTodos,
                onCreateTodo = vm::createTodo,
                onEditTodo = vm::updateTodo,
                onCompleteTodo = vm::completeTodo,
                onDeleteTodo = vm::deleteTodo,
                modifier = Modifier.padding(padding)
            )
            Tab.Habits -> HabitsScreen(
                habits = habits,
                onCreateHabit = habitVm::createHabit,
                onEditHabit = habitVm::updateHabit,
                onLogHabit = habitVm::logHabit,
                onDeleteHabit = habitVm::deleteHabit,
                onFinishPeriod = habitVm::finishCurrentPeriod,
                modifier = Modifier.padding(padding)
            )
            Tab.Notes -> NotesScreen(
                notesVm = notesVm,
                modifier = Modifier.padding(padding)
            )
            Tab.History -> HistoryScreen(
                completedTodos = completedTodos,
                habitHistory = habitHistory,
                onReopenTodo = vm::reopenTodo,
                modifier = Modifier.padding(padding)
            )
        }
    }

    // Profil-BottomSheet (öffnet beim Tap aufs AccountCircle-Icon)
    if (showProfile) {
        ProfileSheet(
            syncVm = syncVm,
            onOpenSettings = { showSettings = true },
            onDismiss = { showProfile = false }
        )
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
