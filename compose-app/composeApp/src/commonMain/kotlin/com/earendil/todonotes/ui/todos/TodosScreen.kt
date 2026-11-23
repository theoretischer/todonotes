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
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.outlined.Circle
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.Modifier
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.earendil.todonotes.data.entity.Todo
import com.earendil.todonotes.ui.components.SwipeToDeleteRow
import com.earendil.todonotes.ui.components.ScreenTitle
import androidx.compose.ui.unit.sp
import com.earendil.todonotes.ui.components.linkified
import kotlinx.coroutines.delay
import kotlinx.datetime.Instant
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime

/**
 * Formatiert millis als "EEE, dd. MMM · HH:mm" (lokale System-TZ, deutsche Wochentage).
 * Ersatz für SimpleDateFormat("EEE, dd. MMM · HH:mm", Locale.GERMAN).
 * (M7b — commonMain)
 */
@Composable
private fun formatTodoDate(millis: Long): String {
    val ldt = Instant.fromEpochMilliseconds(millis).toLocalDateTime(TimeZone.currentSystemDefault())
    val wd = germanWeekdayShort(ldt.dayOfWeek.ordinal)
    val mon = germanMonthShort(ldt.monthNumber)
    val d = ldt.dayOfMonth.toString().padStart(2, '0')
    val h = ldt.hour.toString().padStart(2, '0')
    val min = ldt.minute.toString().padStart(2, '0')
    return "$wd, $d. $mon · $h:$min"
}

private fun germanWeekdayShort(ordinal: Int): String = when (ordinal) {
    0 -> "Mo"; 1 -> "Di"; 2 -> "Mi"; 3 -> "Do"; 4 -> "Fr"; 5 -> "Sa"; 6 -> "So"
    else -> ""
}
private fun germanMonthShort(m: Int): String = when (m) {
    1 -> "Jan"; 2 -> "Feb"; 3 -> "Mär"; 4 -> "Apr"; 5 -> "Mai"; 6 -> "Jun"
    7 -> "Jul"; 8 -> "Aug"; 9 -> "Sep"; 10 -> "Okt"; 11 -> "Nov"; 12 -> "Dez"
    else -> ""
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TodosScreen(
    todos: List<Todo>,
    onCreateTodo: (TodoFormData) -> Unit,
    onEditTodo: (String, TodoFormData) -> Unit,
    onCompleteTodo: (String) -> Unit,
    onDeleteTodo: (String) -> Unit,
    modifier: Modifier = Modifier,
    /** Höhe der BottomBar in px (für die Quick-Add-Position). */
    bottomBarPadding: Int = 0
) {
    var editingTodo by remember { mutableStateOf<Todo?>(null) }
    val groups = remember(todos) { groupOpenTodos(todos) }
    val density = LocalDensity.current

    Box(modifier = modifier.fillMaxSize()) {
        // Bug 6: Titel scrollt MIT der Liste (Nutzer-Wunsch: hochgeschoben
        // statt verdeckt). Er ist jetzt erstes Listenelement, nicht fixiert;
        // sein statusBarsPadding sitzt er mitbringend ganz oben, das Avatar
        // rechts oben bleibt fix (liegt in TodoNotesApp).

        if (groups.isEmpty()) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                ScreenTitle("Aufgaben", modifier = Modifier.align(Alignment.TopStart))
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("Keine Aufgaben", style = MaterialTheme.typography.titleMedium)
                    Spacer(Modifier.height(8.dp))
                    Text(
                        "Schreibe unten in die Box, um eine Aufgabe zu erstellen.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(start = 16.dp, top = 0.dp, end = 16.dp, bottom = 220.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                item(key = "title") { ScreenTitle("Aufgaben") }
                if (groups.overdue.isNotEmpty()) {
                    item { SectionHeader("Überfällig", groups.overdue.size, color = MaterialTheme.colorScheme.error) }
                    items(groups.overdue, key = { it.id }) { todo ->
                        SwipeToDeleteRow(
                            onDelete = { onDeleteTodo(todo.id) },
                            onClick = { editingTodo = todo }
                        ) {
                            TodoRow(
                                todo = todo,
                                overdue = true,
                                onComplete = onCompleteTodo,
                                onEdit = { editingTodo = todo }
                            )
                        }
                    }
                }
                if (groups.today.isNotEmpty()) {
                    item { SectionHeader("Heute", groups.today.size) }
                    items(groups.today, key = { it.id }) { todo ->
                        SwipeToDeleteRow(onDelete = { onDeleteTodo(todo.id) }, onClick = { editingTodo = todo }) {
                            TodoRow(todo, onComplete = onCompleteTodo, onEdit = { editingTodo = todo })
                        }
                    }
                }
                if (groups.tomorrow.isNotEmpty()) {
                    item { SectionHeader("Morgen", groups.tomorrow.size) }
                    items(groups.tomorrow, key = { it.id }) { todo ->
                        SwipeToDeleteRow(onDelete = { onDeleteTodo(todo.id) }, onClick = { editingTodo = todo }) {
                            TodoRow(todo, onComplete = onCompleteTodo, onEdit = { editingTodo = todo })
                        }
                    }
                }
                if (groups.thisWeek.isNotEmpty()) {
                    item { SectionHeader("Diese Woche", groups.thisWeek.size) }
                    items(groups.thisWeek, key = { it.id }) { todo ->
                        SwipeToDeleteRow(onDelete = { onDeleteTodo(todo.id) }, onClick = { editingTodo = todo }) {
                            TodoRow(todo, onComplete = onCompleteTodo, onEdit = { editingTodo = todo })
                        }
                    }
                }
                if (groups.later.isNotEmpty()) {
                    item { SectionHeader("Später", groups.later.size) }
                    items(groups.later, key = { it.id }) { todo ->
                        SwipeToDeleteRow(onDelete = { onDeleteTodo(todo.id) }, onClick = { editingTodo = todo }) {
                            TodoRow(todo, onComplete = onCompleteTodo, onEdit = { editingTodo = todo })
                        }
                    }
                }
                if (groups.noDate.isNotEmpty()) {
                    item { SectionHeader("Kein Datum", groups.noDate.size) }
                    items(groups.noDate, key = { it.id }) { todo ->
                        SwipeToDeleteRow(onDelete = { onDeleteTodo(todo.id) }, onClick = { editingTodo = todo }) {
                            TodoRow(todo, onComplete = onCompleteTodo, onEdit = { editingTodo = todo })
                        }
                    }
                }
            }
        }

        // Quick-Add-Bar (M13): schwebend über der Bottom-Bar, ersetzt den FAB.
        //
        // Position = max(ime-Höhe, BottomBar-Höhe) + Normal-Abstand:
        // Die Tastatur-Animation (ime-Inset) läuft CONTINUERLICH (0→volle Höhe
        // animiert) — so gleitet die Box beim Öffnen/Schließen WEICH mit der
        // Tastatur mit. VORHER: binäres imePadding auf Content der eh schon
        // über der NavBar endete → Box sprang hinter die aufkommende Tastatur
        // (wurde hochgeschoben) und beim Schließen hinterhergezogen.
        // max() mit NavBar-Höhe: geschlossene Tastatur → Box bleibt über der
        // Bottom-Bar (Padding im Scaffold fällt dafür im Todos-Tab weg, s.
        // TodoNotesApp).
        val bottomBarPx = bottomBarPadding
        val imeBottomPx = WindowInsets.ime.getBottom(density)
        // max(ime, NavBar) in px → dp
        val liftPx = if (imeBottomPx > bottomBarPx) imeBottomPx else bottomBarPx
        QuickAddBar(
            onCreateTodo = onCreateTodo,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = with(density) { liftPx.toDp() } + 12.dp)  // + Luft zur BottomBar
                .padding(horizontal = 12.dp)
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
private fun SectionHeader(title: String, count: Int = 0, color: Color = MaterialTheme.colorScheme.primary) {
    // M14: dezente Abschnitts-Überschrift: KLEINE GROSSBUCHSTABEN, Anzahl daneben
    // ("KEIN DATUM · 2"). Gedämpftes Blau/Grau — Text im Vordergrund.
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 8.dp, bottom = 4.dp)) {
        Text(
            text = title.uppercase(),
            style = MaterialTheme.typography.labelMedium,
            color = color,
            fontWeight = FontWeight.SemiBold,
            letterSpacing = 1.sp
        )
        if (count > 0) {
            Text(
                text = " · $count",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontWeight = FontWeight.SemiBold
            )
        }
    }
}

@Composable
private fun TodoRow(
    todo: Todo,
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
            // M14f: KEIN eigener Hintergrund mehr — der SwipeToDeleteRow-
            // Wrapper liefert surface + 16dp-Rundung. Vorher: background-
            // Farbe → Todos waren "Karten" in exakt der Hintergrundfarbe
            // (unauffällig). Jetzt surface wie Notizen/Gewohnheiten/Verlauf.
            .clip(RoundedCornerShape(16.dp)) // Bug 8: Ripple in den Rundungen
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
                    // M14: kleinerer, dünnerer Kreis im gedämpften Grau —
                    // der Text steht im Vordergrund, nicht der Kreis.
                    tint = if (completing) MaterialTheme.colorScheme.primary
                           else MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(22.dp)
                )
            }

            Column(modifier = Modifier.weight(1f)) {
                Box {
                    Text(
                        // M12: Links im Titel klickbar (LinkAnnotation).
                        text = linkified(todo.title, MaterialTheme.colorScheme.primary),
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
                        // M12: Links in der Notiz klickbar (LinkAnnotation).
                        text = linkified(todo.notes, MaterialTheme.colorScheme.primary),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                if (todo.dueAt != null) {
                    Text(
                        text = formatTodoDate(todo.dueAt),
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
