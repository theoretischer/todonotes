@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package com.earendil.todonotes.ui.todos

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Event
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Notes
import androidx.compose.material.icons.filled.Repeat
import androidx.compose.material.icons.outlined.Event
import androidx.compose.material.icons.outlined.History
import androidx.compose.material.icons.outlined.Notifications
import androidx.compose.material.icons.outlined.Notes
import androidx.compose.material.icons.outlined.Repeat
import androidx.compose.material.icons.outlined.Remove
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.earendil.todonotes.data.entity.NotificationStyle
import com.earendil.todonotes.ui.components.RecurrenceCodec
import com.earendil.todonotes.ui.components.RecurrenceEditor
import com.earendil.todonotes.ui.components.RecurrenceState
import kotlinx.datetime.Clock
import kotlinx.datetime.Instant
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.LocalTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.atStartOfDayIn
import kotlinx.datetime.toInstant
import kotlinx.datetime.toLocalDateTime

/**
 * Quick-Add-Bar (M13): schwebende Box über der Bottom-Bar im Aufgaben-Tab.
 *
 * ersetzt den FAB — eine Aufgabe zu erstellen braucht jetzt EIN Textfeld +
 * Enter/Häkchen, ohne Dialog. Darunter runde Knöpfe für die Optionen:
 *   [📅 Datum & Uhrzeit] [↻ Wiederholung] [🔔 Benachrichtigung] [ clock Verlauf] [📝 Notizen]
 *
 * Die Knöpfe sind Toggles/Kurz-Menüs:
 *   - Datum: DatePicker → TimePicker → gesetzt (Toggle: nochmal tippen = entfernen)
 *   - Wiederholung: Bottom-Sheet mit RecurrenceEditor (Samsung-Reminder-Stil)
 *   - Benachrichtigung: Dropdown Vollbild/Benachrichtigung/Stumm
 *   - Verlauf: Toggle (landet nach Abschluss im Verlauf)
 *   - Notizen: klappt ein Mini-Textfeld auf
 */
@Composable
fun QuickAddBar(
    onCreateTodo: (TodoFormData) -> Unit,
    modifier: Modifier = Modifier
) {
    val tz = TimeZone.currentSystemDefault()

    var title by remember { mutableStateOf("") }
    var notes by remember { mutableStateOf("") }
    var notesOpen by remember { mutableStateOf(false) }

    // „In Verlauf eintragen" — Default wie im alten Dialog: an.
    var logToHistory by remember { mutableStateOf(true) }

    // Datum/Uhrzeit (default: nächste volle Stunde „jetzt")
    var hasDate by remember { mutableStateOf(false) }
    var pickedLdt by remember {
        val now = Clock.System.now().toLocalDateTime(tz)
        val roundedMinute = ((now.minute + 14) / 15) * 15  // auf 15min aufrunden
        val hour = if (roundedMinute == 60) now.hour + 1 else now.hour
        val minute = if (roundedMinute == 60) 0 else roundedMinute
        mutableStateOf(LocalDateTime(now.date, LocalTime(hour % 24, minute)))
    }

    // Wiederholung (UI-State)
    var recurrenceState by remember { mutableStateOf(RecurrenceCodec.decode(null)) }
    var showRecurrenceSheet by remember { mutableStateOf(false) }

    // Benachrichtigung
    var notificationStyle by remember { mutableStateOf(NotificationStyle.FULLSCREEN.value) }
    var showNotificationMenu by remember { mutableStateOf(false) }

    // Datum/Uhrzeit-Picker
    var showDatePicker by remember { mutableStateOf(false) }
    var showTimePicker by remember { mutableStateOf(false) }

    fun submit() {
        val trimmed = title.trim()
        if (trimmed.isEmpty()) return
        val dueAt = if (hasDate) pickedLdt.toInstant(tz).toEpochMilliseconds() else null
        onCreateTodo(
            TodoFormData(
                title = trimmed,
                notes = notes.trim(),
                dueAt = dueAt,
                recurrence = RecurrenceCodec.encode(recurrenceState),
                logToHistory = logToHistory,
                notificationStyle = notificationStyle
            )
        )
        // Felder zurücksetzen (Optionen bleiben gesetzt — so erstellt man
        // mehrere Aufgaben hintereinander mit derselben Konfiguration).
        title = ""
        notes = ""
        notesOpen = false
    }

    Surface(
        shape = MaterialTheme.shapes.large,
        shadowElevation = 6.dp,
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        modifier = modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                OutlinedTextField(
                    value = title,
                    onValueChange = { title = it },
                    placeholder = { Text("Neue Aufgabe…") },
                    singleLine = true,
                    modifier = Modifier.weight(1f)
                )
                // Erstellen-Knopf: nur ein Häkchen, klein & simpel.
                FilledIconButton(
                    onClick = { submit() },
                    enabled = title.isNotBlank(),
                    modifier = Modifier.size(40.dp)
                ) {
                    Icon(Icons.Default.Check, contentDescription = "Erstellen")
                }
            }

            if (notesOpen) {
                OutlinedTextField(
                    value = notes,
                    onValueChange = { notes = it },
                    placeholder = { Text("Notizen…") },
                    modifier = Modifier.fillMaxWidth(),
                    minLines = 1,
                    maxLines = 3
                )
            }

            // Runde Options-Knöpfe
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                QuickOptionButton(
                    icon = if (hasDate) Icons.Filled.Event else Icons.Outlined.Event,
                    label = if (hasDate) formatShortDateTime(pickedLdt) else "Datum",
                    selected = hasDate,
                    onClick = {
                        if (hasDate) {
                            hasDate = false  // nochmal tippen = entfernen
                        } else {
                            showDatePicker = true
                        }
                    }
                )
                QuickOptionButton(
                    icon = if (recurrenceState.freq != com.earendil.todonotes.ui.components.RecurFreq.NONE) Icons.Filled.Repeat else Icons.Outlined.Repeat,
                    label = recurrenceShortLabel(recurrenceState),
                    selected = recurrenceState.freq != com.earendil.todonotes.ui.components.RecurFreq.NONE,
                    onClick = { showRecurrenceSheet = true }
                )
                Box {
                    QuickOptionButton(
                        icon = if (notificationStyle != NotificationStyle.SILENT.value) Icons.Filled.Notifications else Icons.Outlined.Notifications,
                        label = notificationStyleShortLabel(notificationStyle),
                        selected = notificationStyle != NotificationStyle.SILENT.value,
                        onClick = { showNotificationMenu = true }
                    )
                    DropdownMenu(expanded = showNotificationMenu, onDismissRequest = { showNotificationMenu = false }) {
                        DropdownMenuItem(
                            text = { Text("Vollbild") },
                            onClick = { notificationStyle = NotificationStyle.FULLSCREEN.value; showNotificationMenu = false },
                            leadingIcon = { Icon(Icons.Filled.Notifications, null) }
                        )
                        DropdownMenuItem(
                            text = { Text("Benachrichtigung") },
                            onClick = { notificationStyle = NotificationStyle.NOTIFICATION.value; showNotificationMenu = false },
                            leadingIcon = { Icon(Icons.Outlined.Notifications, null) }
                        )
                        DropdownMenuItem(
                            text = { Text("Stumm") },
                            onClick = { notificationStyle = NotificationStyle.SILENT.value; showNotificationMenu = false },
                            leadingIcon = { Icon(Icons.Outlined.Remove, null) }
                        )
                    }
                }
                QuickOptionButton(
                    icon = if (logToHistory) Icons.Filled.History else Icons.Outlined.History,
                    label = "Verlauf",
                    selected = logToHistory,
                    onClick = { logToHistory = !logToHistory }
                )
                QuickOptionButton(
                    icon = if (notesOpen) Icons.Filled.Notes else Icons.Outlined.Notes,
                    label = "Notizen",
                    selected = notesOpen,
                    onClick = { notesOpen = !notesOpen }
                )
            }
        }
    }

    // ---- DatePicker (→ TimePicker → gesetzt) ----
    if (showDatePicker) {
        val initialMs = remember {
            LocalDate(pickedLdt.year, pickedLdt.monthNumber, pickedLdt.dayOfMonth)
                .atStartOfDayIn(tz).toEpochMilliseconds()
        }
        val state = rememberDatePickerState(initialSelectedDateMillis = initialMs)
        DatePickerDialog(
            onDismissRequest = { showDatePicker = false },
            confirmButton = {
                TextButton(onClick = {
                    state.selectedDateMillis?.let { ms ->
                        val date = Instant.fromEpochMilliseconds(ms).toLocalDateTime(tz).date
                        pickedLdt = LocalDateTime(date, pickedLdt.time)
                    }
                    showDatePicker = false
                    showTimePicker = true
                }) { Text("Weiter") }
            },
            dismissButton = { TextButton(onClick = { showDatePicker = false }) { Text("Abbrechen") } }
        ) { DatePicker(state = state) }
    }
    if (showTimePicker) {
        val state = rememberTimePickerState(initialHour = pickedLdt.hour, initialMinute = pickedLdt.minute)
        AlertDialog(
            onDismissRequest = { showTimePicker = false },
            confirmButton = {
                TextButton(onClick = {
                    pickedLdt = LocalDateTime(pickedLdt.date, LocalTime(state.hour, state.minute))
                    hasDate = true
                    showTimePicker = false
                }) { Text("OK") }
            },
            dismissButton = { TextButton(onClick = { showTimePicker = false }) { Text("Abbrechen") } },
            title = { Text("Uhrzeit") },
            text = { TimePicker(state = state) }
        )
    }

    // ---- Wiederholungs-Bottom-Sheet ----
    if (showRecurrenceSheet) {
        val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
        ModalBottomSheet(
            onDismissRequest = { showRecurrenceSheet = false },
            sheetState = sheetState
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 24.dp)
                    .padding(bottom = 24.dp)
            ) {
                Text(
                    "Wiederholung",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.padding(bottom = 8.dp)
                )
                RecurrenceEditor(
                    state = recurrenceState,
                    onStateChange = { recurrenceState = it }
                )
                Row(
                    modifier = Modifier.fillMaxWidth().padding(top = 16.dp),
                    horizontalArrangement = Arrangement.End
                ) {
                    TextButton(onClick = {
                        recurrenceState = RecurrenceState()  // zurücksetzen
                        showRecurrenceSheet = false
                    }) { Text("Keine Wiederholung") }
                    Spacer(Modifier.width(8.dp))
                    Button(onClick = { showRecurrenceSheet = false }) {
                        Text("Fertig")
                    }
                }
            }
        }
    }
}

/** Runder Options-Knopf mit Icon + Label darunter (wie WhatsApp-Attachment). */
@Composable
private fun QuickOptionButton(
    icon: ImageVector,
    label: String,
    selected: Boolean,
    onClick: () -> Unit
) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Surface(
            onClick = onClick,
            shape = CircleShape,
            color = if (selected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceContainerHigh,
            modifier = Modifier.size(44.dp)
        ) {
            Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxSize()) {
                Icon(
                    icon,
                    contentDescription = label,
                    tint = if (selected) MaterialTheme.colorScheme.onPrimaryContainer
                           else MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
        Text(
            label,
            style = MaterialTheme.typography.labelSmall,
            color = if (selected) MaterialTheme.colorScheme.primary
                    else MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            maxLines = 1,
            modifier = Modifier.padding(top = 2.dp)
        )
    }
}

private fun formatShortDateTime(ldt: LocalDateTime): String {
    val d = ldt.dayOfMonth.toString().padStart(2, '0')
    val m = ldt.monthNumber.toString().padStart(2, '0')
    val h = ldt.hour.toString().padStart(2, '0')
    val min = ldt.minute.toString().padStart(2, '0')
    return "$d.$m. $h:$min"
}

private fun recurrenceShortLabel(state: RecurrenceState): String {
    val n = if (state.interval > 1) "${state.interval} " else ""
    return when (state.freq) {
        com.earendil.todonotes.ui.components.RecurFreq.NONE -> "Wiederh."
        com.earendil.todonotes.ui.components.RecurFreq.MINUTELY -> "${n}Min"
        com.earendil.todonotes.ui.components.RecurFreq.HOURLY -> "${n}Std"
        com.earendil.todonotes.ui.components.RecurFreq.DAILY -> "täglich"
        com.earendil.todonotes.ui.components.RecurFreq.WEEKLY -> "woche"
        com.earendil.todonotes.ui.components.RecurFreq.MONTHLY -> "monatl."
        com.earendil.todonotes.ui.components.RecurFreq.YEARLY -> "jährl."
    }
}

private fun notificationStyleShortLabel(style: Int): String = when (style) {
    NotificationStyle.FULLSCREEN.value -> "Vollbild"
    NotificationStyle.NOTIFICATION.value -> "Benachr."
    else -> "Stumm"
}