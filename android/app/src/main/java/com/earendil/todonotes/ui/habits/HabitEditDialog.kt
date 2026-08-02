package com.earendil.todonotes.ui.habits

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.earendil.todonotes.data.entity.CadenceType
import com.earendil.todonotes.data.entity.Habit
import java.util.Calendar

/**
 * Universeller Erstellen- & Bearbeiten-Dialog für Habits.
 * Wird im Erstellen-Fall mit `existing = null` aufgerufen.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HabitEditDialog(
    existing: Habit? = null,
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

    // Startdatum (bestimmt den Reset-Anchor)
    val baseCal = remember {
        existing?.startDate?.let { Calendar.getInstance().apply { timeInMillis = it } }
            ?: Calendar.getInstance()
    }
    var year by remember { mutableStateOf(baseCal.get(Calendar.YEAR)) }
    var month by remember { mutableStateOf(baseCal.get(Calendar.MONTH)) }
    var day by remember { mutableStateOf(baseCal.get(Calendar.DAY_OF_MONTH)) }

    var showDatePicker by remember { mutableStateOf(false) }

    if (showDatePicker) {
        val state = rememberDatePickerState(
            initialSelectedDateMillis = Calendar.getInstance().apply {
                set(year, month, day)
            }.timeInMillis
        )
        DatePickerDialog(
            onDismissRequest = { showDatePicker = false },
            confirmButton = {
                TextButton(onClick = {
                    state.selectedDateMillis?.let { ms ->
                        val c = Calendar.getInstance().apply { timeInMillis = ms }
                        year = c.get(Calendar.YEAR); month = c.get(Calendar.MONTH); day = c.get(Calendar.DAY_OF_MONTH)
                    }
                    showDatePicker = false
                }) { Text("OK") }
            },
            dismissButton = { TextButton(onClick = { showDatePicker = false }) { Text("Abbrechen") } }
        ) { DatePicker(state = state) }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            Button(
                onClick = {
                    if (title.isNotBlank()) {
                        val startDate = Calendar.getInstance().apply {
                            set(year, month, day, 0, 0, 0)
                            set(Calendar.MILLISECOND, 0)
                        }.timeInMillis
                        onSubmit(
                            HabitFormData(
                                title = title.trim(),
                                notes = notes.trim(),
                                cadenceType = cadenceType,
                                interval = interval,
                                goalCount = goalCount,
                                startDate = startDate,
                                logToHistory = logToHistory
                            )
                        )
                    }
                },
                enabled = title.isNotBlank()
            ) { Text(if (isEdit) "Speichern" else "Erstellen") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Abbrechen") } },
        title = { Text(if (isEdit) "Gewohnheit bearbeiten" else "Neue Gewohnheit", fontWeight = FontWeight.SemiBold) },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth().verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                OutlinedTextField(
                    value = title,
                    onValueChange = { title = it },
                    label = { Text("Gewohnheit") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                OutlinedTextField(
                    value = notes,
                    onValueChange = { notes = it },
                    label = { Text("Notizen (optional)") },
                    modifier = Modifier.fillMaxWidth(),
                    minLines = 2
                )

                // Startdatum (Reset-Anchor)
                OutlinedButton(
                    onClick = { showDatePicker = true },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Icon(Icons.Default.Schedule, contentDescription = null)
                    Spacer(Modifier.width(8.dp))
                    Text("Start: %04d-%02d-%02d".format(year, month + 1, day))
                }

                HorizontalDivider()

                // Zeitraum (vereinfacht, inline n)
                HabitCadencePicker(
                    cadenceType = cadenceType,
                    interval = interval,
                    onCadenceChange = { cadenceType = it },
                    onIntervalChange = { interval = it }
                )

                HorizontalDivider()

                // Ziel-Anzahl pro Zeitraum
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Ziel: ", style = MaterialTheme.typography.bodyMedium)
                    OutlinedTextField(
                        value = goalCount.toString(),
                        onValueChange = { v ->
                            val n = v.toIntOrNull()
                            if (n != null && n in 1..9999) goalCount = n
                        },
                        modifier = Modifier.width(80.dp).height(48.dp),
                        singleLine = true,
                        textStyle = MaterialTheme.typography.bodyMedium
                    )
                    Text(" mal", style = MaterialTheme.typography.bodyMedium)
                }

                HorizontalDivider()

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Checkbox(checked = logToHistory, onCheckedChange = { logToHistory = it })
                    Column(modifier = Modifier.weight(1f)) {
                        Text("In Verlauf eintragen", style = MaterialTheme.typography.bodyMedium)
                        Text(
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
