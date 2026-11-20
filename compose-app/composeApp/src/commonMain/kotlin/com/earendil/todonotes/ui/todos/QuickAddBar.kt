@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package com.earendil.todonotes.ui.todos

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.material3.TextField
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
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

    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        // M14: EIN abgerundetes, gefülltes TextField (kein Outline, kein Karten-
        // Aufbau) — der Senden-Button sitzt DARIN (trailingIcon), Zeilenhöhe 56dp.
        TextField(
            value = title,
            onValueChange = { title = it },
            placeholder = { Text("Neue Aufgabe…") },
            singleLine = true,
            shape = RoundedCornerShape(20.dp),
            colors = TextFieldDefaults.colors(
                focusedContainerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                unfocusedContainerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                focusedIndicatorColor = Color.Transparent,
                unfocusedIndicatorColor = Color.Transparent,
                focusedTextColor = MaterialTheme.colorScheme.onSurface,
                unfocusedTextColor = MaterialTheme.colorScheme.onSurface
            ),
            trailingIcon = {
                // Erstellen-Knopf: nur ein Häkchen, klein & simpel.
                IconButton(
                    onClick = { submit() },
                    enabled = title.isNotBlank(),
                    modifier = Modifier.size(32.dp)
                ) {
                    Icon(
                        Icons.Default.Check,
                        contentDescription = "Erstellen",
                        tint = if (title.isNotBlank()) MaterialTheme.colorScheme.primary
                               else MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(22.dp)
                    )
                }
            },
            modifier = Modifier.fillMaxWidth().height(56.dp)
        )

        if (notesOpen) {
            TextField(
                value = notes,
                onValueChange = { notes = it },
                placeholder = { Text("Notizen…") },
                shape = RoundedCornerShape(20.dp),
                colors = TextFieldDefaults.colors(
                    focusedContainerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                    unfocusedContainerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                    focusedIndicatorColor = Color.Transparent,
                    unfocusedIndicatorColor = Color.Transparent
                ),
                modifier = Modifier.fillMaxWidth()
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
                    contentDescription = "Datum & Uhrzeit",
                    // Nur wenn gesetzt: gewähltes Datum zeigen (sonst kein Text —
                    // ausgewählt = farbiger Icon).
                    label = if (hasDate) formatShortDateTime(pickedLdt) else null,
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
                    contentDescription = "Wiederholung",
                    label = null,
                    selected = recurrenceState.freq != com.earendil.todonotes.ui.components.RecurFreq.NONE,
                    onClick = { showRecurrenceSheet = true }
                )
                Box {
                    QuickOptionButton(
                        icon = if (notificationStyle != NotificationStyle.SILENT.value) Icons.Filled.Notifications else Icons.Outlined.Notifications,
                        contentDescription = "Benachrichtigung",
                        label = null,
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
                    contentDescription = "In Verlauf eintragen",
                    label = null,
                    selected = logToHistory,
                    onClick = { logToHistory = !logToHistory }
                )
                QuickOptionButton(
                    icon = if (notesOpen) Icons.Filled.Notes else Icons.Outlined.Notes,
                    contentDescription = "Notizen",
                    label = null,
                    selected = notesOpen,
                    onClick = { notesOpen = !notesOpen }
                )
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

/** Runder Options-Knopf: nur Icon (44dp), ausgewählt = farbig. [label] nur
 *  beim Datum gesetzt (zeigt dann das gewählte Datum) — sonst kein Text. */
@Composable
private fun QuickOptionButton(
    icon: ImageVector,
    contentDescription: String,
    label: String?,
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
                    contentDescription = contentDescription,
                    tint = if (selected) MaterialTheme.colorScheme.onPrimaryContainer
                           else MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
        if (label != null) {
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
}

private fun formatShortDateTime(ldt: LocalDateTime): String {
    val d = ldt.dayOfMonth.toString().padStart(2, '0')
    val m = ldt.monthNumber.toString().padStart(2, '0')
    val h = ldt.hour.toString().padStart(2, '0')
    val min = ldt.minute.toString().padStart(2, '0')
    return "$d.$m. $h:$min"
}


private fun notificationStyleShortLabel(style: Int): String = when (style) {
    NotificationStyle.FULLSCREEN.value -> "Vollbild"
    NotificationStyle.NOTIFICATION.value -> "Benachr."
    else -> "Stumm"
}
