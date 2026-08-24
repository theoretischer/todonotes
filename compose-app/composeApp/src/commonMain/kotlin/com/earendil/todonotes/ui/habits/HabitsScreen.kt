package com.earendil.todonotes.ui.habits

import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material.icons.filled.Repeat
import androidx.compose.material.icons.filled.Timeline
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.earendil.todonotes.data.entity.CadenceType
import com.earendil.todonotes.data.entity.Habit
import com.earendil.todonotes.data.entity.HabitType
import com.earendil.todonotes.ui.HabitWithProgress
import com.earendil.todonotes.ui.components.SwipeOrReorderRow
import com.earendil.todonotes.ui.components.Weekdays
import com.earendil.todonotes.ui.notes.ReorderKind

/**
 * Gewohnheiten-Tab: klassische Habits (n-mal pro Periode) und
 * Zufriedenheits-Tracker (0-10 Skala, +/−) in EINER Liste.
 *
 * - FAB `+`: Bottom-Sheet-Menü „Neue Gewohnheit" / „Neues Zufriedenheits-Tracking"
 * - Long-Press + vertikal ziehen = Reorder (wie Notizen, SwipeOrReorderRow)
 * - Swipe links = Löschen
 * - Satisfaction-Karte antippen → Tracker-Detail (Grafik)
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HabitsScreen(
    habits: List<HabitWithProgress>,
    onCreateHabit: (HabitFormData) -> Unit,
    onEditHabit: (String, HabitFormData) -> Unit,
    onLogHabit: (String) -> Unit,
    onDeleteHabit: (String) -> Unit,
    onFinishPeriod: (String) -> Unit,
    onRatingChange: (String, Int) -> Unit,
    onOpenTracker: (String) -> Unit,
    onSwapHabits: (String, String) -> Unit,
    onBeginHabitReorder: () -> Unit,
    onCommitHabitReorder: () -> Unit,
    modifier: Modifier = Modifier
) {
    var showNewMenu by remember { mutableStateOf(false) }
    var createType by remember { mutableStateOf(HabitType.HABIT) }
    var showCreateDialog by remember { mutableStateOf(false) }
    var editingHabit by remember { mutableStateOf<Habit?>(null) }
    var finishConfirmHabit by remember { mutableStateOf<Habit?>(null) }

    // Row-Höhen für die Reorder-Logik (Schwellwert = halbe Zeilenhöhe).
    val rowHeights = remember { mutableStateMapOf<String, Int>() }

    Box(modifier = modifier.fillMaxSize()) {
        if (habits.isEmpty()) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("Noch nichts zum Tracken", style = MaterialTheme.typography.titleMedium)
                    Spacer(Modifier.height(8.dp))
                    Text(
                        "Tippe auf + für eine neue Gewohnheit oder Zufriedenheits-Tracking.",
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
                    val habit = hwp.habit
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .onSizeChanged { rowHeights[habit.id] = it.height }
                    ) {
                        SwipeOrReorderRow(
                            onDelete = { onDeleteHabit(habit.id) },
                            onClick = {
                                if (habit.type == HabitType.SATISFACTION) {
                                    onOpenTracker(habit.id)
                                } else {
                                    editingHabit = habit
                                }
                            },
                            reorderEnabled = true,
                            reorderKind = ReorderKind.HABIT,
                            itemId = habit.id,
                            repositories = habits.map { it.habit.id },
                            heightPx = rowHeights[habit.id] ?: 0,
                            onSwap = onSwapHabits,
                            onReorderBegin = onBeginHabitReorder,
                            onReorderEnd = onCommitHabitReorder
                        ) {
                            if (habit.type == HabitType.SATISFACTION) {
                                SatisfactionCard(
                                    hwp = hwp,
                                    onRatingDown = { onRatingChange(habit.id, -1) },
                                    onRatingUp = { onRatingChange(habit.id, +1) }
                                )
                            } else {
                                HabitCard(
                                    hwp = hwp,
                                    onLog = { onLogHabit(habit.id) },
                                    onEdit = { editingHabit = habit },
                                    onFinishPeriod = { finishConfirmHabit = habit }
                                )
                            }
                        }
                    }
                }
            }
        }

        // FAB unten rechts (kein Scaffold nötig, kein navigationBarsPadding
        // — das umgebende Box hebt bereits über die NavBar).
        FloatingActionButton(
            onClick = { showNewMenu = true },
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(16.dp)
        ) {
            Icon(Icons.Default.Add, contentDescription = "Neu")
        }
    }

    // FAB-Menü: Typ-Auswahl (gleiche Bottom-Sheet-Optik wie bei Notizen).
    if (showNewMenu) {
        val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
        ModalBottomSheet(
            onDismissRequest = { showNewMenu = false },
            sheetState = sheetState
        ) {
            Text(
                "Neu erstellen",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.padding(start = 24.dp, top = 4.dp, bottom = 8.dp)
            )
            NewHabitMenuItem(
                icon = { Icon(Icons.Default.Repeat, contentDescription = null) },
                text = "Neue Gewohnheit",
                subtitle = "n-mal pro Zeitraum tracken",
                onClick = {
                    showNewMenu = false
                    createType = HabitType.HABIT
                    showCreateDialog = true
                }
            )
            NewHabitMenuItem(
                icon = { Icon(Icons.Default.Timeline, contentDescription = null) },
                text = "Neues Zufriedenheits-Tracking",
                subtitle = "0–10 Skala, Verlauf als Grafik",
                onClick = {
                    showNewMenu = false
                    createType = HabitType.SATISFACTION
                    showCreateDialog = true
                }
            )
            Spacer(Modifier.height(16.dp))
        }
    }

    if (showCreateDialog) {
        HabitEditDialog(
            existing = null,
            initialType = createType,
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

/** Menü-Eintrag im FAB-Bottom-Sheet (mit Untertitel). */
@Composable
private fun NewHabitMenuItem(
    icon: @Composable () -> Unit,
    text: String,
    subtitle: String,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 24.dp, vertical = 14.dp)
            .combinedClickable(onClick = onClick),
        verticalAlignment = Alignment.CenterVertically
    ) {
        icon()
        Spacer(Modifier.width(16.dp))
        Column {
            Text(text, style = MaterialTheme.typography.bodyLarge)
            Text(
                subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

/** Klassische Gewohnheit: 0/n + Balken + +1-Button. */
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

                // Menü (Bearbeiten/Periode abschließen)
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

/** Zufriedenheits-Tracker: aktueller Wert (x/10) + −/+ Buttons.
 *  Tap auf die Karte (außer Buttons) öffnet die Verlaufs-Grafik. */
@Composable
private fun SatisfactionCard(
    hwp: HabitWithProgress,
    onRatingDown: () -> Unit,
    onRatingUp: () -> Unit
) {
    val habit = hwp.habit
    val rating = habit.currentRating ?: 0

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
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

            Spacer(Modifier.height(8.dp))

            // Aktueller Wert + −/+ Buttons
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = "$rating",
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
                )
                Text(
                    text = " / 10",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(start = 2.dp)
                )
                Spacer(Modifier.weight(1f))
                FilledIconButton(
                    onClick = onRatingDown,
                    enabled = rating > 0
                ) {
                    Icon(Icons.Default.Remove, contentDescription = "Weniger")
                }
                Spacer(Modifier.width(8.dp))
                FilledIconButton(
                    onClick = onRatingUp,
                    enabled = rating < 10
                ) {
                    Icon(Icons.Default.Add, contentDescription = "Mehr")
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
