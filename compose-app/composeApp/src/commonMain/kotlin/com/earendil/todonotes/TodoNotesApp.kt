package com.earendil.todonotes

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
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
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import com.earendil.todonotes.ui.TodoViewModel
import com.earendil.todonotes.ui.todos.TodosScreen
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
    val todoVm = remember { TodoViewModel(container.todoRepository, container.appScope) }
    val openTodos by todoVm.openTodos.collectAsState()
    var currentTab by remember { mutableStateOf(Tab.Todos) }

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
                Tab.Habits -> ComingSoon("Gewohnheiten", Modifier.fillMaxSize())
                Tab.Notes -> ComingSoon("Notizen", Modifier.fillMaxSize())
                Tab.History -> ComingSoon("Verlauf", Modifier.fillMaxSize())
            }

            // Profil-Icon oben rechts (öffnet Settings — M7d)
            UserCircleIcon(
                onClick = { /* M7d: SettingsScreen */ },
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .statusBarsPadding()
                    .padding(end = 12.dp, top = 8.dp)
            )
        }
    }
}

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

/** User-Icon als unabhaengiger Kreis oben rechts (ohne TopAppBar). */
@Composable
private fun UserCircleIcon(
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
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
