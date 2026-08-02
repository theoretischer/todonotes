package com.earendil.todonotes.ui.history

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.earendil.todonotes.data.entity.HabitHistoryEntry
import com.earendil.todonotes.data.entity.Todo
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun HistoryScreen(
    completedTodos: List<Todo>,
    habitHistory: List<HabitHistoryEntry>,
    onReopenTodo: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val dateFmt = SimpleDateFormat("dd.MM.yyyy, HH:mm", Locale.GERMAN)
    val periodFmt = SimpleDateFormat("dd.MM.yyyy", Locale.GERMAN)

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
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            if (habitHistory.isNotEmpty()) {
                item {
                    Text(
                        "Gewohnheiten",
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(top = 8.dp, bottom = 4.dp)
                    )
                }
                items(habitHistory, key = { it.id }) { entry ->
                    ListItem(
                        headlineContent = { Text(entry.title, fontWeight = FontWeight.Medium) },
                        supportingContent = {
                            Text("Periode ab ${periodFmt.format(Date(entry.periodStart))}: " +
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
                        modifier = Modifier.padding(top = 12.dp, bottom = 4.dp)
                    )
                }
                items(completedTodos, key = { it.id }) { todo ->
                    ListItem(
                        headlineContent = { Text(todo.title, fontWeight = FontWeight.Medium) },
                        supportingContent = {
                            todo.completedAt?.let {
                                Text("Erledigt: ${dateFmt.format(Date(it))}")
                            }
                        },
                        trailingContent = {
                            TextButton(onClick = { onReopenTodo(todo.id) }) { Text("Wieder öffnen") }
                        }
                    )
                    HorizontalDivider()
                }
            }
        }
    }
}
