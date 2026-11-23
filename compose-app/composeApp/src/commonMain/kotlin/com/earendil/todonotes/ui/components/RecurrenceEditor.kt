package com.earendil.todonotes.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp


/**
 * Wiederholungs-Editor im Samsung-Reminder-Stil (M14h umgebaut):
 *
 * Statt Radio-Zeilen mit springenden Zahlen-Pillen ("Jeden 1 Tag"):
 * ein Stepper (− n +) für das Intervall und darunter Segment-Buttons
 * (Nie/Minütlich/Stündlich/Täglich/Wöchentlich/Monatlich/Jährlich).
 * Nur die ausgewählte Einheit ist aktiv — das Intervall steckt im
 * Stepper, es springt nichts mehr um.
 *
 * Bei WEEKLY klappt die Wochentag-Auswahl auf, bei MONTHLY die drei
 * Monats-Modi. Separate Laufzeit-Sektion darunter (Zeilen 48dp).
 */
@Composable
fun RecurrenceEditor(
    state: RecurrenceState,
    onStateChange: (RecurrenceState) -> Unit,
    modifier: Modifier = Modifier,
    showTitle: Boolean = true
) {
    Column(modifier = modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {

        if (showTitle) {
            Text(
                "Wiederholung",
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.SemiBold
            )
        }

        // Intervall-Stepper (nur sinnvoll bei aktiver Wiederholung)
        AnimatedVisibility(visible = state.freq != RecurFreq.NONE) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    "Alle",
                    style = MaterialTheme.typography.bodyMedium
                )
                IntervalStepper(
                    value = state.interval,
                    onValueChange = { n -> onStateChange(state.copy(interval = n)) }
                )
                Text(
                    state.freq.suffixPlural,
                    style = MaterialTheme.typography.bodyMedium
                )
            }
        }

        // Segment-Buttons: Einheit der Wiederholung
        RecurSegmented(
            state = state,
            onSelect = { freq ->
                onStateChange(
                    if (freq == RecurFreq.NONE) state.copy(freq = freq)
                    else state.copy(freq = freq)
                )
            }
        )

        // Untermenü: Wochentage bei WEEKLY
        AnimatedVisibility(visible = state.freq == RecurFreq.WEEKLY) {
            WeekdayPicker(
                selected = state.weekDays,
                onSelectionChange = { wd -> onStateChange(state.copy(weekDays = wd)) }
            )
        }

        // Untermenü: Monats-Modi bei MONTHLY
        AnimatedVisibility(visible = state.freq == RecurFreq.MONTHLY) {
            MonthlyOptions(
                state = state,
                onStateChange = onStateChange
            )
        }

        HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))

        // Laufzeit-Sektion
        Text(
            "Laufzeit",
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.SemiBold
        )
        Column(modifier = Modifier.selectableGroup()) {
            Row(
                modifier = Modifier.fillMaxWidth().height(48.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                RadioButton(
                    selected = state.end == RecurEnd.FOREVER,
                    onClick = { onStateChange(state.copy(end = RecurEnd.FOREVER)) }
                )
                Text(RecurEnd.FOREVER.label, style = MaterialTheme.typography.bodyMedium)
            }
            Row(
                modifier = Modifier.fillMaxWidth().height(48.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                RadioButton(
                    selected = state.end == RecurEnd.COUNT,
                    onClick = { onStateChange(state.copy(end = RecurEnd.COUNT)) }
                )
                Text("Wiederholen: ", style = MaterialTheme.typography.bodyMedium)
                IntervalStepper(
                    value = state.endCount,
                    onValueChange = { n -> onStateChange(state.copy(endCount = n)) },
                    range = 1..999
                )
                Text(" mal", style = MaterialTheme.typography.bodyMedium)
            }
        }
    }
}

/**
 * Segment-Buttons für die Frequenz-Einheit (M14h): eine Zeile,
 * jede Einheit ein Segment — kein "Jeden 1 Tag"-Pillen-Springen.
 * Zwei Zeilen, damit die Buttons nicht quetschen (7 Einheiten).
 */
@Composable
private fun RecurSegmented(
    state: RecurrenceState,
    onSelect: (RecurFreq) -> Unit
) {
    val units = listOf(
        RecurFreq.NONE to "Nie",
        RecurFreq.MINUTELY to "Min.",
        RecurFreq.HOURLY to "Std.",
        RecurFreq.DAILY to "Tag",
        RecurFreq.WEEKLY to "Woche",
        RecurFreq.MONTHLY to "Monat",
        RecurFreq.YEARLY to "Jahr"
    )
    // M14i: natürliche Breite pro Chip, Umbruch in die nächste Zeile
    // per FlowRow (statt weight(1f)-Reihe, die alles gleichmäßig quetscht).
    FlowRow(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        units.forEach { (freq, label) ->
            val selected = state.freq == freq
            FilterChip(
                selected = selected,
                onClick = { onSelect(freq) },
                label = { Text(label, style = MaterialTheme.typography.bodyMedium) },
                shape = CircleShape
            )
        }
    }
}

/** Stepper (− n +) für das Intervall (M14h). */
@Composable
private fun IntervalStepper(
    value: Int,
    onValueChange: (Int) -> Unit,
    modifier: Modifier = Modifier,
    range: IntRange = 1..9999
) {
    Row(
        modifier = modifier
            .clip(CircleShape)
            .background(MaterialTheme.colorScheme.surfaceContainerHigh)
            .height(32.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        IconButton(
            onClick = { onValueChange((value - 1).coerceAtLeast(range.first)) },
            modifier = Modifier.size(32.dp)
        ) {
            Icon(Icons.Filled.Remove, contentDescription = "Weniger", modifier = Modifier.size(18.dp))
        }
        Text(
            value.toString(),
            style = MaterialTheme.typography.bodyMedium.copy(
                textAlign = TextAlign.Center,
                color = MaterialTheme.colorScheme.primary
            ),
            modifier = Modifier.width(36.dp)
        )
        IconButton(
            onClick = { onValueChange((value + 1).coerceAtMost(range.last)) },
            modifier = Modifier.size(32.dp)
        ) {
            Icon(Icons.Filled.Add, contentDescription = "Mehr", modifier = Modifier.size(18.dp))
        }
    }
}

@Composable
private fun WeekdayPicker(
    selected: Set<Int>,
    onSelectionChange: (Set<Int>) -> Unit
) {
    val days = listOf(
        Weekdays.MONDAY to "M",
        Weekdays.TUESDAY to "D",
        Weekdays.WEDNESDAY to "M",
        Weekdays.THURSDAY to "D",
        Weekdays.FRIDAY to "F",
        Weekdays.SATURDAY to "S",
        Weekdays.SUNDAY to "S"
    )
    Column(modifier = Modifier.fillMaxWidth().padding(top = 4.dp)) {
        Text("Wochentage", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Row(
            modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            days.forEach { (calDay, label) ->
                val isSelected = calDay in selected
                // M14i: FilterChip (Label zentriert, wie die Segment-Buttons)
                // statt AssistChip (Label linksbuendig bei weight-Breite).
                FilterChip(
                    selected = isSelected,
                    onClick = {
                        onSelectionChange(
                            if (isSelected) selected - calDay else selected + calDay
                        )
                    },
                    label = {
                        Text(
                            label,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.fillMaxWidth()
                        )
                    },
                    shape = CircleShape,
                    modifier = Modifier.weight(1f)
                )
            }
        }
    }
}

@Composable
private fun MonthlyOptions(
    state: RecurrenceState,
    onStateChange: (RecurrenceState) -> Unit
) {
    Column(modifier = Modifier.fillMaxWidth().padding(top = 4.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text("Im Monat", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)

        Column(modifier = Modifier.selectableGroup()) {
            // Am n. wiederholen
            Row(modifier = Modifier.fillMaxWidth().height(48.dp), verticalAlignment = Alignment.CenterVertically) {
                RadioButton(
                    selected = state.monthlyMode == MonthlyMode.DAY_OF_MONTH,
                    onClick = { onStateChange(state.copy(monthlyMode = MonthlyMode.DAY_OF_MONTH, monthlyDays = emptySet())) }
                )
                Text("Am ", style = MaterialTheme.typography.bodyMedium)
                DayStepper(
                    value = state.monthlyDays.firstOrNull() ?: 1,
                    onValueChange = { n -> onStateChange(state.copy(monthlyDays = setOf(n.coerceIn(1, 31)))) }
                )
                Text(". wiederholen", style = MaterialTheme.typography.bodyMedium)
            }

            // Am n. Wochentag wiederholen
            Row(modifier = Modifier.fillMaxWidth().height(48.dp), verticalAlignment = Alignment.CenterVertically) {
                RadioButton(
                    selected = state.monthlyMode == MonthlyMode.NTH_WEEKDAY,
                    onClick = { onStateChange(state.copy(monthlyMode = MonthlyMode.NTH_WEEKDAY)) }
                )
                Text("Am ", style = MaterialTheme.typography.bodyMedium)
                val nthLabels = listOf(1 to "1.", 2 to "2.", 3 to "3.", 4 to "4.", 5 to "letzten")
                var expanded by remember { mutableStateOf(false) }
                Box {
                    TextButton(onClick = { expanded = true }) {
                        Text(nthLabels.first { it.first == state.monthlyNth }.second)
                    }
                    DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                        nthLabels.forEach { (n, lbl) ->
                            DropdownMenuItem(
                                text = { Text(lbl) },
                                onClick = {
                                    onStateChange(state.copy(monthlyNth = n))
                                    expanded = false
                                }
                            )
                        }
                    }
                }
                WeekdayDropdown(
                    selected = state.monthlyWeekday,
                    onSelect = { wd -> onStateChange(state.copy(monthlyWeekday = wd)) }
                )
                Text(" wiederholen", style = MaterialTheme.typography.bodyMedium)
            }

            // Datumsangabe (mehrere Tage)
            Row(modifier = Modifier.fillMaxWidth().height(48.dp), verticalAlignment = Alignment.CenterVertically) {
                RadioButton(
                    selected = state.monthlyMode == MonthlyMode.MULTIPLE_DAYS,
                    onClick = { onStateChange(state.copy(monthlyMode = MonthlyMode.MULTIPLE_DAYS)) }
                )
                Text("Datumsangabe", style = MaterialTheme.typography.bodyMedium)
            }
        }

        AnimatedVisibility(visible = state.monthlyMode == MonthlyMode.MULTIPLE_DAYS) {
            DayOfMonthGrid(
                selected = state.monthlyDays,
                onSelectionChange = { days -> onStateChange(state.copy(monthlyDays = days)) }
            )
        }
    }
}

/** Wie IntervalStepper, aber fürs "Am n. des Monats" (1–31). */
@Composable
private fun DayStepper(
    value: Int,
    onValueChange: (Int) -> Unit
) {
    IntervalStepper(value = value, onValueChange = onValueChange, range = 1..31)
}

@Composable
private fun WeekdayDropdown(selected: Int, onSelect: (Int) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    val names = listOf(
        Weekdays.MONDAY to "Montag", Weekdays.TUESDAY to "Dienstag", Weekdays.WEDNESDAY to "Mittwoch",
        Weekdays.THURSDAY to "Donnerstag", Weekdays.FRIDAY to "Freitag", Weekdays.SATURDAY to "Samstag",
        Weekdays.SUNDAY to "Sonntag"
    )
    Box {
        TextButton(onClick = { expanded = true }) {
            Text(names.first { it.first == selected }.second)
        }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            names.forEach { (d, name) ->
                DropdownMenuItem(text = { Text(name) }, onClick = { onSelect(d); expanded = false })
            }
        }
    }
}

@Composable
private fun DayOfMonthGrid(
    selected: Set<Int>,
    onSelectionChange: (Set<Int>) -> Unit
) {
    Column(modifier = Modifier.fillMaxWidth().padding(top = 4.dp)) {
        Text("Tage im Monat", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        // 7 Spalten für 31 Tage
        Column(verticalArrangement = Arrangement.spacedBy(2.dp), modifier = Modifier.padding(top = 4.dp)) {
            (1..31).chunked(7).forEach { week ->
                Row(horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                    week.forEach { day ->
                        val isSelected = day in selected
                        AssistChip(
                            onClick = {
                                onSelectionChange(if (isSelected) selected - day else selected + day)
                            },
                            label = { Text(day.toString()) },
                            colors = if (isSelected) AssistChipDefaults.assistChipColors(
                                containerColor = MaterialTheme.colorScheme.primaryContainer,
                                labelColor = MaterialTheme.colorScheme.onPrimaryContainer
                            ) else AssistChipDefaults.assistChipColors(),
                            modifier = Modifier.size(36.dp)
                        )
                    }
                    // Rest auffüllen
                    repeat(7 - week.size) { Spacer(Modifier.size(36.dp)) }
                }
            }
        }
    }
}