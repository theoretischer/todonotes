package com.earendil.todonotes.ui.habits

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.earendil.todonotes.data.entity.CadenceType
import com.earendil.todonotes.data.entity.Habit
import com.earendil.todonotes.ui.HabitWithProgress
import com.earendil.todonotes.ui.components.SwipeToDeleteRow
import com.earendil.todonotes.ui.components.Weekdays

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HabitsScreen(
    habits: List<HabitWithProgress>,
    onCreateHabit: (HabitFormData) -> Unit,
    onEditHabit: (String, HabitFormData) -> Unit,
    onLogHabit: (String) -> Unit,
    onDeleteHabit: (String) -> Unit,
    onFinishPeriod: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    var showCreateDialog by remember { mutableStateOf(false) }
    var editingHabit by remember { mutableStateOf<Habit?>(null) }
    var finishConfirmHabit by remember { mutableStateOf<Habit?>(null) }

    Box(modifier = modifier.fillMaxSize()) {
        if (habits.isEmpty()) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("Keine Gewohnheiten", style = MaterialTheme.typography.titleMedium)
                    Spacer(Modifier.height(8.dp))
                    Text(
                        "Tippe auf + um eine neue Gewohnheit zu erstellen.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(start = 16.dp, top = 96.dp, end = 16.dp, bottom = 88.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(habits, key = { it.habit.id }) { hwp ->
                    SwipeToDeleteRow(
                        onDelete = { onDeleteHabit(hwp.habit.id) },
                        onClick = { editingHabit = hwp.habit }
                    ) {
                        HabitCard(
                            hwp = hwp,
                            onLog = { onLogHabit(hwp.habit.id) },
                            onEdit = { editingHabit = hwp.habit },
                            onFinishPeriod = { finishConfirmHabit = hwp.habit }
                        )
                    }
                }
            }
        }

        // FAB unten rechts (kein Scaffold nötig, kein navigationBarsPadding
        // — das umgebende Box hebt bereits über die NavBar).
        FloatingActionButton(
            onClick = { showCreateDialog = true },
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(16.dp)
        ) {
            Icon(Icons.Default.Add, contentDescription = "Neue Gewohnheit")
        }
    }

    if (showCreateDialog) {
        HabitEditDialog(
            existing = null,
            onDismiss = { showCreateDialog = false },
            onSubmit = { form ->
                onCreateHabit(form)
                showCreateDialog = false
            }
        )
    }

    editingHabit?.let { habit ->
        HabitEditDialog(
            existing = habit,
            onDismiss = { editingHabit = null },
            onSubmit = { form ->
                onEditHabit(habit.id, form)
                editingHabit = null
            }
        )
    }

    if (finishConfirmHabit != null) {
        val h = finishConfirmHabit!!
        AlertDialog(
            onDismissRequest = { finishConfirmHabit = null },
            confirmButton = {
                TextButton(onClick = {
                    finishConfirmHabit = null
                    onFinishPeriod(h.id)
                }) { Text("Abschließen") }
            },
            dismissButton = {
                TextButton(onClick = { finishConfirmHabit = null }) { Text("Abbrechen") }
            },
            title = { Text("Periode abschließen?") },
            text = {
                Text("\"${h.title}\": die aktuelle Periode wird in den Verlauf " +
                     "eingetragen (mit aktuellem Stand) und der Zähler auf 0 " +
                     "zurückgesetzt. Die neue Periode beginnt jetzt.")
            }
        )
    }
}

@Composable
private fun HabitCard(
    hwp: HabitWithProgress,
    onLog: () -> Unit,
    onEdit: () -> Unit,
    onFinishPeriod: () -> Unit
) {
    val habit = hwp.habit
    val prog = hwp.progress
    var menuExpanded by remember { mutableStateOf(false) }

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = habit.title,
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    if (habit.notes.isNotBlank()) {
                        Text(
                            text = habit.notes,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                    Text(
                        text = cadenceLabel(habit) + " · Reset: " + resetLabel(habit),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                // Menü (Bearbeiten/Löschen)
                Box {
                    IconButton(onClick = { menuExpanded = true }) {
                        Icon(Icons.Default.MoreVert, contentDescription = "Optionen")
                    }
                    DropdownMenu(
                        expanded = menuExpanded,
                        onDismissRequest = { menuExpanded = false }
                    ) {
                        DropdownMenuItem(
                            text = { Text("Bearbeiten") },
                            onClick = { menuExpanded = false; onEdit() }
                        )
                        DropdownMenuItem(
                            text = { Text("Periode abschließen") },
                            onClick = { menuExpanded = false; onFinishPeriod() }
                        )
                    }
                }
            }

            Spacer(Modifier.height(8.dp))

            // Fortschritt: 0/n + Balken + + Button
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = "${prog.count}/${prog.goal}",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = if (prog.isComplete) MaterialTheme.colorScheme.primary
                            else MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.padding(end = 8.dp)
                )
                LinearProgressIndicator(
                    progress = { prog.ratio },
                    modifier = Modifier
                        .weight(1f)
                        .height(8.dp),
                )
                Spacer(Modifier.width(8.dp))
                FilledIconButton(onClick = onLog) {
                    Icon(Icons.Default.Add, contentDescription = "+1")
                }
            }
        }
    }
}

/** Label wie "2x pro Woche" / "1x pro Tag" / "1x alle 3 Tage". */
private fun cadenceLabel(habit: Habit): String {
    val per = when (habit.cadenceType) {
        CadenceType.DAY -> "Tag"
        CadenceType.WEEK -> "Woche"
        CadenceType.MONTH -> "Monat"
        CadenceType.YEAR -> "Jahr"
        CadenceType.NDAYS -> "${habit.interval} Tage"
    }
    return if (habit.cadenceType == CadenceType.NDAYS) {
        "${habit.goalCount}x alle $per"
    } else {
        "${habit.goalCount}x pro $per"
    }
}

/** Reset-Tag-Anzeige. Nutzt Weekdays-Konstanten (1=SO..7=SA, wie DB). */
private fun resetLabel(habit: Habit): String {
    val wdNames = mapOf(
        Weekdays.SUNDAY to "So",
        Weekdays.MONDAY to "Mo",
        Weekdays.TUESDAY to "Di",
        Weekdays.WEDNESDAY to "Mi",
        Weekdays.THURSDAY to "Do",
        Weekdays.FRIDAY to "Fr",
        Weekdays.SATURDAY to "Sa"
    )
    return when (habit.cadenceType) {
        CadenceType.WEEK -> wdNames[habit.resetWeekday ?: Weekdays.MONDAY] ?: "Mo"
        CadenceType.MONTH -> "${habit.resetAnchorDay ?: 1}."
        CadenceType.YEAR -> "${habit.resetAnchorDay ?: 1}.${habit.resetAnchorMonth ?: 1}."
        CadenceType.DAY -> "täglich"
        CadenceType.NDAYS -> "alle ${habit.interval} Tage"
    }
}
