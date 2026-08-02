package com.earendil.todonotes.ui.todos

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
import com.earendil.todonotes.data.entity.Todo
import com.earendil.todonotes.ui.components.RecurrenceCodec
import com.earendil.todonotes.ui.components.RecurrenceEditor
import com.earendil.todonotes.ui.components.RecurrenceState
import java.util.Calendar

/** Payload, den der Dialog beim Bestätigen zurückliefert. */
data class TodoFormData(
    val title: String,
    val notes: String,
    val dueAt: Long?,
    val recurrence: String?,
    val logToHistory: Boolean
)

/**
 * Universeller Erstellen- & Bearbeiten-Dialog für Todos.
 * Wird im Erstellen-Fall mit `existing = null` aufgerufen.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TodoEditDialog(
    existing: Todo? = null,
    onDismiss: () -> Unit,
    onSubmit: (TodoFormData) -> Unit
) {
    val isEdit = existing != null

    // Felder
    var title by remember { mutableStateOf(existing?.title ?: "") }
    var notes by remember { mutableStateOf(existing?.notes ?: "") }
    var hasDate by remember { mutableStateOf(existing?.dueAt != null) }
    var logToHistory by remember { mutableStateOf(existing?.logToHistory ?: true) }

    // Wiederholung (UI-State)
    var recurrenceState by remember {
        mutableStateOf(RecurrenceCodec.decode(existing?.recurrence))
    }

    // Datum/Uhrzeit-State (voreingestellt aus existing oder "jetzt")
    val baseCal = remember {
        existing?.dueAt?.let { Calendar.getInstance().apply { timeInMillis = it } }
            ?: Calendar.getInstance()
    }
    var year by remember { mutableStateOf(baseCal.get(Calendar.YEAR)) }
    var month by remember { mutableStateOf(baseCal.get(Calendar.MONTH)) }
    var day by remember { mutableStateOf(baseCal.get(Calendar.DAY_OF_MONTH)) }
    var hour by remember { mutableStateOf(baseCal.get(Calendar.HOUR_OF_DAY)) }
    var minute by remember { mutableStateOf(baseCal.get(Calendar.MINUTE)) }

    var showDatePicker by remember { mutableStateOf(false) }
    var showTimePicker by remember { mutableStateOf(false) }

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
                    showTimePicker = true
                }) { Text("Weiter") }
            },
            dismissButton = { TextButton(onClick = { showDatePicker = false }) { Text("Abbrechen") } }
        ) { DatePicker(state = state) }
    }

    if (showTimePicker) {
        val state = rememberTimePickerState(initialHour = hour, initialMinute = minute)
        AlertDialog(
            onDismissRequest = { showTimePicker = false },
            confirmButton = { TextButton(onClick = {
                hour = state.hour; minute = state.minute
                showTimePicker = false
            }) { Text("OK") } },
            dismissButton = { TextButton(onClick = { showTimePicker = false }) { Text("Abbrechen") } },
            title = { Text("Uhrzeit") },
            text = { TimePicker(state = state) }
        )
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            Button(
                onClick = {
                    if (title.isNotBlank()) {
                        val dueAt = if (hasDate) Calendar.getInstance().apply {
                            set(year, month, day, hour, minute, 0)
                            set(Calendar.MILLISECOND, 0)
                        }.timeInMillis else null
                        val rrule = RecurrenceCodec.encode(recurrenceState)
                        onSubmit(
                            TodoFormData(
                                title = title.trim(),
                                notes = notes.trim(),
                                dueAt = dueAt,
                                recurrence = rrule,
                                logToHistory = logToHistory
                            )
                        )
                    }
                },
                enabled = title.isNotBlank()
            ) { Text(if (isEdit) "Speichern" else "Erstellen") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Abbrechen") } },
        title = { Text(if (isEdit) "Aufgabe bearbeiten" else "Neue Aufgabe", fontWeight = FontWeight.SemiBold) },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth().verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                OutlinedTextField(
                    value = title,
                    onValueChange = { title = it },
                    label = { Text("Aufgabe") },
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

                // Datum/Uhrzeit Toggle
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Checkbox(checked = hasDate, onCheckedChange = { hasDate = it })
                    Text("Datum & Uhrzeit", style = MaterialTheme.typography.bodyMedium)
                }

                if (hasDate) {
                    OutlinedButton(
                        onClick = { showDatePicker = true },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Icon(Icons.Default.Schedule, contentDescription = null)
                        Spacer(Modifier.width(8.dp))
                        val dateStr = "%04d-%02d-%02d %02d:%02d".format(year, month + 1, day, hour, minute)
                        Text(dateStr)
                    }
                }

                HorizontalDivider()

                // Wiederholung (Samsung-Reminder-Stil)
                RecurrenceEditor(
                    state = recurrenceState,
                    onStateChange = { recurrenceState = it }
                )

                HorizontalDivider()

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Checkbox(checked = logToHistory, onCheckedChange = { logToHistory = it })
                    Column(modifier = Modifier.weight(1f)) {
                        Text("In Verlauf eintragen", style = MaterialTheme.typography.bodyMedium)
                        Text(
                            "Nach Abschluss sichtbar im Verlauf",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }
    )
}
