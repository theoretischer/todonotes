package com.earendil.todonotes.ui.todos

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.earendil.todonotes.data.entity.NotificationStyle
import com.earendil.todonotes.data.entity.Todo
import com.earendil.todonotes.data.repo.formatDateGerman
import com.earendil.todonotes.ui.components.RecurrenceCodec
import com.earendil.todonotes.ui.components.RecurrenceEditor
import com.earendil.todonotes.ui.components.RecurrenceState
import kotlinx.datetime.Instant
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.LocalTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.atStartOfDayIn
import kotlinx.datetime.toInstant
import kotlinx.datetime.toLocalDateTime

/** Payload, den der Dialog beim Bestätigen zurückliefert. */
data class TodoFormData(
    val title: String,
    val notes: String,
    val dueAt: Long?,
    val recurrence: String?,
    val logToHistory: Boolean,
    val notificationStyle: Int = NotificationStyle.FULLSCREEN.value
)

/**
 * Universeller Erstellen- & Bearbeiten-Dialog für Todos.
 * Wird im Erstellen-Fall mit `existing = null` aufgerufen.
 *
 * (M7b — commonMain, mit kotlinx.datetime statt java.util.Calendar)
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TodoEditDialog(
    existing: Todo? = null,
    onDismiss: () -> Unit,
    onSubmit: (TodoFormData) -> Unit
) {
    val isEdit = existing != null
    val tz = TimeZone.currentSystemDefault()

    // Felder
    var title by remember { mutableStateOf(existing?.title ?: "") }
    var notes by remember { mutableStateOf(existing?.notes ?: "") }
    var hasDate by remember { mutableStateOf(existing?.dueAt != null) }
    var logToHistory by remember { mutableStateOf(existing?.logToHistory ?: true) }
    var notificationStyle by remember {
        mutableStateOf(existing?.notificationStyle ?: NotificationStyle.FULLSCREEN.value)
    }

    // Wiederholung (UI-State)
    var recurrenceState by remember {
        mutableStateOf(RecurrenceCodec.decode(existing?.recurrence))
    }

    // Datum/Uhrzeit-State (voreingestellt aus existing oder "jetzt")
    val baseLdt = remember {
        existing?.dueAt?.let {
            Instant.fromEpochMilliseconds(it).toLocalDateTime(tz)
        } ?: kotlinx.datetime.Clock.System.now().toLocalDateTime(tz)
    }
    var year by remember { mutableStateOf(baseLdt.year) }
    var month by remember { mutableStateOf(baseLdt.monthNumber) } // 1-based
    var day by remember { mutableStateOf(baseLdt.dayOfMonth) }
    var hour by remember { mutableStateOf(baseLdt.hour) }
    var minute by remember { mutableStateOf(baseLdt.minute) }

    var showDatePicker by remember { mutableStateOf(false) }
    var showTimePicker by remember { mutableStateOf(false) }

    if (showDatePicker) {
        // DatePicker gibt UTC-Mitternacht → in lokales Datum umrechnen.
        val initialMs = remember {
            LocalDate(year, month, day).atStartOfDayIn(tz).toEpochMilliseconds()
        }
        val state = rememberDatePickerState(initialSelectedDateMillis = initialMs)
        DatePickerDialog(
            onDismissRequest = { showDatePicker = false },
            confirmButton = {
                TextButton(onClick = {
                    state.selectedDateMillis?.let { ms ->
                        val ldt = Instant.fromEpochMilliseconds(ms).toLocalDateTime(tz)
                        year = ldt.year; month = ldt.monthNumber; day = ldt.dayOfMonth
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
            confirmButton = {
                TextButton(onClick = {
                    hour = state.hour; minute = state.minute
                    showTimePicker = false
                }) { Text("OK") }
            },
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
                        val dueAt = if (hasDate) {
                            LocalDateTime(
                                LocalDate(year, month, day),
                                LocalTime(hour, minute, 0, 0)
                            ).toInstant(tz).toEpochMilliseconds()
                        } else null
                        val rrule = RecurrenceCodec.encode(recurrenceState)
                        onSubmit(
                            TodoFormData(
                                title = title.trim(),
                                notes = notes.trim(),
                                dueAt = dueAt,
                                recurrence = rrule,
                                logToHistory = logToHistory,
                                notificationStyle = notificationStyle
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
                        val dateStr = buildString {
                            append(year.toString().padStart(4, '0'))
                            append('-'); append(month.toString().padStart(2, '0'))
                            append('-'); append(day.toString().padStart(2, '0'))
                            append(' '); append(hour.toString().padStart(2, '0'))
                            append(':'); append(minute.toString().padStart(2, '0'))
                        }
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

                // Benachrichtigungs-Stil (M8)
                NotificationStyleDropdown(
                    selected = notificationStyle,
                    onSelect = { notificationStyle = it }
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


/** Dropdown für den Benachrichtigungs-Stil (Vollbild / Benachrichtigung / Stumm). */
@Composable
fun NotificationStyleDropdown(selected: Int, onSelect: (Int) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    val options = listOf(
        NotificationStyle.FULLSCREEN to "Vollbild",
        NotificationStyle.NOTIFICATION to "Benachrichtigung",
        NotificationStyle.SILENT to "Stumm"
    )
    val selectedName = options.first { it.first.value == selected }.second

    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            Icons.Default.Notifications,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(Modifier.width(16.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text("Benachrichtigung", style = MaterialTheme.typography.bodyMedium)
            Text(
                "Wie du erinnert wirst",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        TextButton(onClick = { expanded = true }) {
            Text(selectedName)
        }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            options.forEach { (style, name) ->
                DropdownMenuItem(
                    text = { Text(name) },
                    onClick = { onSelect(style.value); expanded = false }
                )
            }
        }
    }
}
