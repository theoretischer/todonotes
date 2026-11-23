package com.earendil.todonotes.ui.habits

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material3.*
import com.earendil.todonotes.ui.components.RoundCheckbox
import com.earendil.todonotes.ui.theme.filledTextFieldColors
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.earendil.todonotes.data.entity.CadenceType
import com.earendil.todonotes.data.entity.Habit
import com.earendil.todonotes.data.entity.HabitType
import com.earendil.todonotes.data.repo.formatStartLabel
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.Instant
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.atStartOfDayIn
import kotlinx.datetime.toLocalDateTime

/**
 * Universeller Erstellen- & Bearbeiten-Dialog für Habits (M7c — commonMain).
 * Wird im Erstellen-Fall mit `existing = null` aufgerufen.
 *
 * Nutzt kotlinx-datetime statt java.util.Calendar und M3 DatePicker
 * (multiplatform).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HabitEditDialog(
    existing: Habit? = null,
    initialType: HabitType = HabitType.HABIT,
    onDismiss: () -> Unit,
    onSubmit: (HabitFormData) -> Unit
) {
    val isEdit = existing != null

    var title by remember { mutableStateOf(existing?.title ?: "") }
    var notes by remember { mutableStateOf(existing?.notes ?: "") }

    // Zeitraum
    var cadenceType by remember { mutableStateOf(existing?.cadenceType ?: CadenceType.WEEK) }
    var interval by remember { mutableStateOf(existing?.interval ?: 1) }

    // Ziel-Anzahl (goalCount) pro Zeitraum
    var goalCount by remember { mutableStateOf(existing?.goalCount ?: 1) }

    // Verlauf
    var logToHistory by remember { mutableStateOf(existing?.logToHistory ?: true) }

    // Typ (Gewohnheit vs. Zufriedenheits-Tracker)
    var type by remember { mutableStateOf(existing?.type ?: initialType) }
    // Startwert für Satisfaction (0-10)
    var rating by remember { mutableStateOf(existing?.currentRating ?: 5) }

    // Startdatum (bestimmt den Reset-Anchor)
    val tz = remember { TimeZone.currentSystemDefault() }
    val baseDate = remember {
        val millis = existing?.startDate ?: kotlinx.datetime.Clock.System.now().toEpochMilliseconds()
        Instant.fromEpochMilliseconds(millis).toLocalDateTime(tz).date
    }
    var startDate by remember { mutableStateOf(baseDate) }

    var showDatePicker by remember { mutableStateOf(false) }

    if (showDatePicker) {
        val initialMillis = remember(startDate) {
            startDate.atStartOfDayIn(tz).toEpochMilliseconds()
        }
        val state = rememberDatePickerState(
            initialSelectedDateMillis = initialMillis
        )
        DatePickerDialog(
            colors = DatePickerDefaults.colors(
                containerColor = MaterialTheme.colorScheme.surface
            ),
            onDismissRequest = { showDatePicker = false },
            confirmButton = {
                TextButton(onClick = {
                    state.selectedDateMillis?.let { ms ->
                        startDate = Instant.fromEpochMilliseconds(ms).toLocalDateTime(tz).date
                    }
                    showDatePicker = false
                }) { Text("OK") }
            },
            dismissButton = { TextButton(onClick = { showDatePicker = false }) { Text("Abbrechen") } }
        ) {
            DatePicker(
                state = state,
                colors = DatePickerDefaults.colors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            )
        }
    }

    AlertDialog(
        containerColor = MaterialTheme.colorScheme.surface,
        onDismissRequest = onDismiss,
        confirmButton = {
            Button(
                onClick = {
                    if (title.isNotBlank()) {
                        val startDateMillis = startDate.atStartOfDayIn(tz).toEpochMilliseconds()
                        onSubmit(
                            HabitFormData(
                                title = title.trim(),
                                notes = notes.trim(),
                                cadenceType = cadenceType,
                                interval = interval,
                                goalCount = goalCount,
                                startDate = startDateMillis,
                                logToHistory = logToHistory,
                                type = type,
                                rating = rating
                            )
                        )
                    }
                },
                enabled = title.isNotBlank()
            ) { Text(if (isEdit) "Speichern" else "Erstellen") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Abbrechen") } },
        title = {
            Text(
                when {
                    isEdit -> "Bearbeiten"
                    type == HabitType.SATISFACTION -> "Neues Zufriedenheits-Tracking"
                    else -> "Neue Gewohnheit"
                },
                fontWeight = FontWeight.SemiBold
            )
        },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth().verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                TextField(
                    value = title,
                    onValueChange = { title = it },
                    colors = filledTextFieldColors(),
                    shape = RoundedCornerShape(16.dp),
                    label = { Text("Gewohnheit") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                TextField(
                    value = notes,
                    onValueChange = { notes = it },
                    colors = filledTextFieldColors(),
                    shape = RoundedCornerShape(16.dp),
                    label = { Text("Notizen (optional)") },
                    modifier = Modifier.fillMaxWidth(),
                    minLines = 2
                )

                if (type == HabitType.HABIT) {
                    HorizontalDivider()

                    // Startdatum (Reset-Anchor)
                    OutlinedButton(
                        onClick = { showDatePicker = true },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Icon(Icons.Default.Schedule, contentDescription = null)
                        Spacer(Modifier.width(8.dp))
                        Text(formatStartLabel(startDate.year, startDate.monthNumber, startDate.dayOfMonth))
                    }

                    HorizontalDivider()

                    // Zeitraum (vereinfacht, inline n)
                    HabitCadencePicker(
                        cadenceType = cadenceType,
                        interval = interval,
                        onCadenceChange = { cadenceType = it },
                        onIntervalChange = { interval = it },
                        // M14m: Ziel (goalCount) wird im CadencePicker selbst
                        // editiert (Stepper-Zeile über den Chips) — das separate
                        // „Ziel: n mal"-Feld ist damit weg.
                        goalCount = goalCount,
                        onGoalCountChange = { goalCount = it }
                    )

                    HorizontalDivider()
                } else {
                    // SATISFACTION: Startwert-Slider 0-10
                    HorizontalDivider()
                    Column(modifier = Modifier.fillMaxWidth()) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text("Startwert", style = MaterialTheme.typography.bodyMedium)
                            Text(
                                "$rating / 10",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                        Slider(
                            value = rating.toFloat(),
                            onValueChange = { rating = it.toInt() },
                            valueRange = 0f..10f,
                            steps = 9
                        )
                    }
                }

                HorizontalDivider()

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    RoundCheckbox(checked = logToHistory, onCheckedChange = { logToHistory = it })
                    Column(modifier = Modifier.weight(1f)) {
                        Text("In Verlauf eintragen", style = MaterialTheme.typography.bodyMedium)
                        Text(
                            if (type == HabitType.SATISFACTION)
                                "Jede Änderung (+/−) wird im Verlauf eingetragen"
                            else
                                "Pro abgelaufener Periode wird der Stand eingetragen",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }
    )
}

