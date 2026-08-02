package com.earendil.todonotes.ui.todos

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.outlined.Circle
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.earendil.todonotes.data.entity.Todo
import com.earendil.todonotes.ui.components.SwipeToDeleteRow
import kotlinx.coroutines.delay
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TodosScreen(
    todos: List<Todo>,
    onCreateTodo: (TodoFormData) -> Unit,
    onEditTodo: (String, TodoFormData) -> Unit,
    onCompleteTodo: (String) -> Unit,
    onDeleteTodo: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    var showCreateDialog by rememberSaveable { mutableStateOf(false) }
    var editingTodo by remember { mutableStateOf<Todo?>(null) }
    val groups = remember(todos) { groupOpenTodos(todos) }
    val dateFmt = remember { SimpleDateFormat("EEE, dd. MMM · HH:mm", Locale.GERMAN) }

    Box(modifier = modifier.fillMaxSize()) {
        if (groups.isEmpty()) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("Keine Aufgaben", style = MaterialTheme.typography.titleMedium)
                    Spacer(Modifier.height(8.dp))
                    Text(
                        "Tippe auf + um eine neue Aufgabe zu erstellen.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(start = 16.dp, top = 96.dp, end = 16.dp, bottom = 88.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                if (groups.overdue.isNotEmpty()) {
                    item { SectionHeader("Überfällig", color = MaterialTheme.colorScheme.error) }
                    items(groups.overdue, key = { it.id }) { todo ->
                        SwipeToDeleteRow(
                            onDelete = { onDeleteTodo(todo.id) },
                            onClick = { editingTodo = todo }
                        ) {
                            TodoRow(
                                todo = todo,
                                dateFmt = dateFmt,
                                overdue = true,
                                onComplete = onCompleteTodo,
                                onEdit = { editingTodo = todo }
                            )
                        }
                    }
                }
                if (groups.today.isNotEmpty()) {
                    item { SectionHeader("Heute") }
                    items(groups.today, key = { it.id }) { todo ->
                        SwipeToDeleteRow(onDelete = { onDeleteTodo(todo.id) }, onClick = { editingTodo = todo }) {
                            TodoRow(todo, dateFmt, onComplete = onCompleteTodo, onEdit = { editingTodo = todo })
                        }
                    }
                }
                if (groups.tomorrow.isNotEmpty()) {
                    item { SectionHeader("Morgen") }
                    items(groups.tomorrow, key = { it.id }) { todo ->
                        SwipeToDeleteRow(onDelete = { onDeleteTodo(todo.id) }, onClick = { editingTodo = todo }) {
                            TodoRow(todo, dateFmt, onComplete = onCompleteTodo, onEdit = { editingTodo = todo })
                        }
                    }
                }
                if (groups.thisWeek.isNotEmpty()) {
                    item { SectionHeader("Diese Woche") }
                    items(groups.thisWeek, key = { it.id }) { todo ->
                        SwipeToDeleteRow(onDelete = { onDeleteTodo(todo.id) }, onClick = { editingTodo = todo }) {
                            TodoRow(todo, dateFmt, onComplete = onCompleteTodo, onEdit = { editingTodo = todo })
                        }
                    }
                }
                if (groups.later.isNotEmpty()) {
                    item { SectionHeader("Später") }
                    items(groups.later, key = { it.id }) { todo ->
                        SwipeToDeleteRow(onDelete = { onDeleteTodo(todo.id) }, onClick = { editingTodo = todo }) {
                            TodoRow(todo, dateFmt, onComplete = onCompleteTodo, onEdit = { editingTodo = todo })
                        }
                    }
                }
                if (groups.noDate.isNotEmpty()) {
                    item { SectionHeader("Kein Datum") }
                    items(groups.noDate, key = { it.id }) { todo ->
                        SwipeToDeleteRow(onDelete = { onDeleteTodo(todo.id) }, onClick = { editingTodo = todo }) {
                            TodoRow(todo, dateFmt, onComplete = onCompleteTodo, onEdit = { editingTodo = todo })
                        }
                    }
                }
            }
        }

        // FAB unten rechts (eigener Container, kein Scaffold nötig)
        FloatingActionButton(
            onClick = { showCreateDialog = true },
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(16.dp)
                .navigationBarsPadding()
        ) {
            Icon(Icons.Default.Add, contentDescription = "Neue Aufgabe")
        }
    }

    if (showCreateDialog) {
        TodoEditDialog(
            existing = null,
            onDismiss = { showCreateDialog = false },
            onSubmit = { form ->
                onCreateTodo(form)
                showCreateDialog = false
            }
        )
    }

    editingTodo?.let { todo ->
        TodoEditDialog(
            existing = todo,
            onDismiss = { editingTodo = null },
            onSubmit = { form ->
                onEditTodo(todo.id, form)
                editingTodo = null
            }
        )
    }
}

@Composable
private fun SectionHeader(title: String, color: Color = MaterialTheme.colorScheme.primary) {
    Text(
        text = title,
        style = MaterialTheme.typography.labelLarge,
        color = color,
        fontWeight = FontWeight.SemiBold,
        modifier = Modifier.padding(bottom = 4.dp)
    )
}

@Composable
private fun TodoRow(
    todo: Todo,
    dateFmt: SimpleDateFormat,
    overdue: Boolean = false,
    onComplete: (String) -> Unit,
    onEdit: () -> Unit
) {
    var completing by remember { mutableStateOf(false) }
    val strikeProgress by animateFloatAsState(
        targetValue = if (completing) 1f else 0f,
        animationSpec = tween(durationMillis = 350),
        label = "strike"
    )

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surface)
            .clickable { onEdit() }
            .padding(vertical = 8.dp, horizontal = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
            IconButton(onClick = {
                if (!completing) completing = true
            }) {
                Icon(
                    if (completing) Icons.Filled.CheckCircle else Icons.Outlined.Circle,
                    contentDescription = "Erledigt",
                    tint = if (completing) MaterialTheme.colorScheme.primary
                           else if (overdue) MaterialTheme.colorScheme.error
                           else MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(28.dp)
                )
            }

            Column(modifier = Modifier.weight(1f)) {
                Box {
                    Text(
                        text = todo.title,
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    if (strikeProgress > 0f) {
                        StrikeThroughOverlay(
                            progress = strikeProgress,
                            strikeColor = MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier.matchParentSize()
                        )
                    }
                }
                if (todo.notes.isNotBlank()) {
                    Text(
                        text = todo.notes,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                if (todo.dueAt != null) {
                    Text(
                        text = dateFmt.format(Date(todo.dueAt)),
                        style = MaterialTheme.typography.bodySmall,
                        color = if (overdue) MaterialTheme.colorScheme.error
                               else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            if (todo.recurrence != null) {
                Text(
                    "↻",
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(end = 8.dp)
                )
            }
    }

    // Nach Abschluss-Animation: echtes Complete auslösen
    LaunchedEffect(completing) {
        if (completing) {
            delay(400)
            onComplete(todo.id)
        }
    }
}

@Composable
private fun StrikeThroughOverlay(
    progress: Float,
    strikeColor: Color,
    modifier: Modifier = Modifier
) {
    Canvas(modifier = modifier) {
        val y = size.height / 2f
        val end = size.width * progress
        drawLine(
            color = strikeColor,
            start = Offset(0f, y),
            end = Offset(end, y),
            strokeWidth = 4f
        )
    }
}

