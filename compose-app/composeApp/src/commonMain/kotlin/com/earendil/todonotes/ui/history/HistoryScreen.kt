package com.earendil.todonotes.ui.history

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.earendil.todonotes.data.entity.HabitHistoryEntry
import com.earendil.todonotes.data.entity.Todo
import com.earendil.todonotes.data.repo.formatDateGerman
import com.earendil.todonotes.ui.components.SwipeToDeleteRow
import kotlinx.datetime.Instant
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime

@Composable
fun HistoryScreen(
    completedTodos: List<Todo>,
    habitHistory: List<HabitHistoryEntry>,
    onReopenTodo: (String) -> Unit,
    onDeleteTodo: (String) -> Unit,
    onDeleteHistoryEntry: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    if (completedTodos.isEmpty() && habitHistory.isEmpty()) {
        Box(
            modifier = modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text("Verlauf", style = MaterialTheme.typography.titleLarge)
                Text(
                    "Noch nichts erledigt.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    } else {
        LazyColumn(
            modifier = modifier.fillMaxSize(),
            contentPadding = PaddingValues(start = 16.dp, top = 96.dp, end = 16.dp, bottom = 88.dp)
        ) {
            if (habitHistory.isNotEmpty()) {
                item {
                    Text(
                        "Gewohnheiten",
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(bottom = 4.dp)
                    )
                }
                items(habitHistory, key = { it.id }) { entry ->
                    SwipeToDeleteRow(
                        onDelete = { onDeleteHistoryEntry(entry.id) },
                        onClick = {}
                    ) {
                        androidx.compose.material3.ListItem(
                            headlineContent = { Text(entry.title, fontWeight = FontWeight.Medium) },
                            supportingContent = {
                                Text("Periode ab ${formatDateGerman(entry.periodStart)}: " +
                                     "${entry.count}/${entry.goal} (${entry.cadenceLabel})")
                            },
                            trailingContent = {
                                Text(
                                    "${entry.count}/${entry.goal}",
                                    style = MaterialTheme.typography.titleSmall,
                                    color = if (entry.count >= entry.goal)
                                        MaterialTheme.colorScheme.primary
                                    else MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        )
                    }
                    HorizontalDivider()
                }
            }

            if (completedTodos.isNotEmpty()) {
                item {
                    Text(
                        "Aufgaben",
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(bottom = 4.dp)
                    )
                }
                items(completedTodos, key = { it.id }) { todo ->
                    SwipeToDeleteRow(
                        onDelete = { onDeleteTodo(todo.id) },
                        onClick = {}
                    ) {
                        androidx.compose.material3.ListItem(
                            headlineContent = { Text(todo.title, fontWeight = FontWeight.Medium) },
                            supportingContent = {
                                todo.completedAt?.let {
                                    Text("Erledigt: ${formatDateTime(it)}")
                                }
                            },
                            trailingContent = {
                                TextButton(onClick = { onReopenTodo(todo.id) }) { Text("Wieder öffnen") }
                            }
                        )
                    }
                    HorizontalDivider()
                }
            }
            item { Spacer(Modifier.height(80.dp)) }
        }
    }
}

/** "dd.MM.yyyy, HH:mm" — buildString statt SimpleDateFormat (Wasm-kompatibel). */
private fun formatDateTime(millis: Long): String {
    val tz = TimeZone.currentSystemDefault()
    val ldt = Instant.fromEpochMilliseconds(millis).toLocalDateTime(tz)
    val d = ldt.dayOfMonth.toString().padStart(2, '0')
    val m = ldt.monthNumber.toString().padStart(2, '0')
    val h = ldt.hour.toString().padStart(2, '0')
    val min = ldt.minute.toString().padStart(2, '0')
    return "$d.$m.${ldt.year}, $h:$min"
}
