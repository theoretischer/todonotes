package com.earendil.todonotes.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.BasicTextField
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
 * Wiederholungs-Editor im Samsung-Reminder-Stil.
 *
 * Vertikale Radio-Liste der Frequenz-Typen. Bei WEEKLY klappt die Wochentag-Auswahl auf,
 * bei MONTHLY die drei Monats-Modi. Separate Laufzeit-Sektion darunter.
 */
@Composable
fun RecurrenceEditor(
    state: RecurrenceState,
    onStateChange: (RecurrenceState) -> Unit,
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {

        Text(
            "Wiederholung",
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.SemiBold
        )

        Column(modifier = Modifier.selectableGroup()) {
            RecurFreq.entries.forEach { freq ->
                RecurFreqRow(
                    freq = freq,
                    isSelected = state.freq == freq,
                    interval = state.interval,
                    onSelect = { onStateChange(state.copy(freq = freq)) },
                    onIntervalChange = { n -> onStateChange(state.copy(interval = n)) }
                )
            }
        }

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
                InlineIntField(
                    value = state.endCount,
                    onValueChange = { n -> onStateChange(state.copy(endCount = n)) },
                    modifier = Modifier.width(48.dp)
                )
                Text(" mal", style = MaterialTheme.typography.bodyMedium)
            }
        }
    }
}

@OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
@Composable
private fun RecurFreqRow(
    freq: RecurFreq,
    isSelected: Boolean,
    interval: Int,
    onSelect: () -> Unit,
    onIntervalChange: (Int) -> Unit
) {
    FlowRow(
        modifier = Modifier
            .fillMaxWidth()
            .clip(MaterialTheme.shapes.small)
            .selectable(selected = isSelected, onClick = onSelect),
        verticalArrangement = Arrangement.Center,
        horizontalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        RadioButton(selected = isSelected, onClick = null)
        if (freq == RecurFreq.NONE) {
            Text(freq.prefix, style = MaterialTheme.typography.bodyMedium)
        } else if (isSelected) {
            // Inline: "Jede [1] Woche"
            Text(freq.prefix, style = MaterialTheme.typography.bodyMedium)
            InlineIntField(value = interval, onValueChange = onIntervalChange)
            Text(freq.suffix, style = MaterialTheme.typography.bodyMedium)
        } else {
            // Nicht ausgewählt: n als normaler Text
            Text(freq.labelWith(interval), style = MaterialTheme.typography.bodyMedium)
        }
    }
}

/** Kompaktes Inline-Int-Feld (kein eigener Rahmen-Text, fühlt sich wie Text an). */
@Composable
private fun InlineIntField(
    value: Int,
    onValueChange: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    BasicTextField(
        value = value.toString(),
        onValueChange = { v ->
            val n = v.toIntOrNull()
            if (n != null && n in 1..9999) onValueChange(n)
        },
        modifier = modifier.width(40.dp),
        singleLine = true,
        textStyle = MaterialTheme.typography.bodyMedium.copy(
            textAlign = TextAlign.Center,
            color = MaterialTheme.colorScheme.primary
        ),
        cursorBrush = androidx.compose.ui.graphics.SolidColor(MaterialTheme.colorScheme.primary),
        decorationBox = { innerTextField ->
            Box(
                modifier = Modifier
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.primaryContainer)
                    .padding(horizontal = 6.dp, vertical = 2.dp),
                contentAlignment = Alignment.Center
            ) {
                innerTextField()
            }
        }
    )
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
    Column(modifier = Modifier.fillMaxWidth().padding(start = 32.dp, top = 4.dp)) {
        Text("Wochentage", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Row(
            modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            days.forEach { (calDay, label) ->
                val isSelected = calDay in selected
                AssistChip(
                    onClick = {
                        onSelectionChange(
                            if (isSelected) selected - calDay else selected + calDay
                        )
                    },
                    label = { Text(label) },
                    colors = if (isSelected) AssistChipDefaults.assistChipColors(
                        containerColor = MaterialTheme.colorScheme.primaryContainer,
                        labelColor = MaterialTheme.colorScheme.onPrimaryContainer
                    ) else AssistChipDefaults.assistChipColors(),
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
    Column(modifier = Modifier.fillMaxWidth().padding(start = 32.dp, top = 4.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text("Im Monat", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)

        Column(modifier = Modifier.selectableGroup()) {
            // Am n. wiederholen
            Row(modifier = Modifier.fillMaxWidth().height(44.dp), verticalAlignment = Alignment.CenterVertically) {
                RadioButton(
                    selected = state.monthlyMode == MonthlyMode.DAY_OF_MONTH,
                    onClick = { onStateChange(state.copy(monthlyMode = MonthlyMode.DAY_OF_MONTH, monthlyDays = emptySet())) }
                )
                Text("Am ", style = MaterialTheme.typography.bodyMedium)
                InlineIntField(
                    value = state.monthlyDays.firstOrNull() ?: 1,
                    onValueChange = { n -> onStateChange(state.copy(monthlyDays = setOf(n.coerceIn(1, 31)))) },
                    modifier = Modifier.width(44.dp)
                )
                Text(". wiederholen", style = MaterialTheme.typography.bodyMedium)
            }

            // Am n. Wochentag wiederholen
            Row(modifier = Modifier.fillMaxWidth().height(44.dp), verticalAlignment = Alignment.CenterVertically) {
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
            Row(modifier = Modifier.fillMaxWidth().height(44.dp), verticalAlignment = Alignment.CenterVertically) {
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
    Column(modifier = Modifier.fillMaxWidth().padding(start = 16.dp, top = 4.dp)) {
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
