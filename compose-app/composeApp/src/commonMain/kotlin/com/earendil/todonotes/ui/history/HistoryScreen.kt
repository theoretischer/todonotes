package com.earendil.todonotes.ui.history

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.earendil.todonotes.data.entity.HabitHistoryEntry
import com.earendil.todonotes.data.entity.Todo
import com.earendil.todonotes.data.repo.formatDateGerman
import com.earendil.todonotes.ui.components.SwipeToDeleteRow
import com.earendil.todonotes.ui.components.linkified
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
    Box(modifier = modifier.fillMaxSize()) {
        // Bug 6: Titel scrollt MIT der Liste (erstes Listenelement statt
        // fixiert) — Avatar oben rechts bleibt fix (TodoNotesApp).
        if (completedTodos.isEmpty() && habitHistory.isEmpty()) {
            Box(
                modifier = Modifier.fillMaxSize(),
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
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(start = 16.dp, top = 0.dp, end = 16.dp, bottom = 88.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                item(key = "title") {
                    com.earendil.todonotes.ui.components.ScreenTitle("Verlauf")
                }
                if (habitHistory.isNotEmpty()) {
                item {
                    Text(
                        "Gewohnheiten".uppercase(),
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.SemiBold,
                        letterSpacing = 1.sp,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(top = 8.dp, bottom = 4.dp)
                    )
                }
                items(habitHistory, key = { it.id }) { entry ->
                    SwipeToDeleteRow(
                        onDelete = { onDeleteHistoryEntry(entry.id) },
                        onClick = {}
                    ) {
                        HistoryCard {
                            if (entry.newRating != null) {
                                // Zufriedenheits-Tracker: "von 3 auf 4"
                                androidx.compose.material3.ListItem(
                                    headlineContent = { Text(entry.title, fontWeight = FontWeight.Medium) },
                                    supportingContent = {
                                        Text("Zufriedenheit: ${formatDateGerman(entry.loggedAt)}")
                                    },
                                    trailingContent = {
                                        val old = entry.count
                                        val new = entry.newRating ?: entry.count
                                        Text(
                                            "$old → $new",
                                            style = MaterialTheme.typography.titleSmall,
                                            color = when {
                                                new > old -> MaterialTheme.colorScheme.primary
                                                new < old -> MaterialTheme.colorScheme.error
                                                else -> MaterialTheme.colorScheme.onSurfaceVariant
                                            }
                                        )
                                    }
                                )
                            } else {
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
                        }
                    }
                }
            }

            if (completedTodos.isNotEmpty()) {
                item {
                    Text(
                        "Aufgaben".uppercase(),
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.SemiBold,
                        letterSpacing = 1.sp,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(top = 8.dp, bottom = 4.dp)
                    )
                }
                items(completedTodos, key = { it.id }) { todo ->
                    SwipeToDeleteRow(
                        onDelete = { onDeleteTodo(todo.id) },
                        onClick = {}
                    ) {
                        HistoryCard {
                            androidx.compose.material3.ListItem(
                                headlineContent = { Text(linkified(todo.title, MaterialTheme.colorScheme.primary), fontWeight = FontWeight.Medium) },
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
                    }
                }
            }

            item { Spacer(Modifier.height(80.dp)) }
        }
        }
    }
}

/**
 * Verlauf-Eintrag als Karte (M14f): 16dp-Radius wie alle Karten, 8dp
 * Abstand über das spacedBy der Liste. Zuvor: nacktes ListItem + Divider.
 */
@Composable
private fun HistoryCard(content: @Composable () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        )
    ) {
        content()
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
