package com.earendil.todonotes.ui.habits

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.earendil.todonotes.data.entity.CadenceType

/**
 * Zeitraum-Picker für Gewohnheiten (Samsung-Reminder-Stil, vereinfacht).
 *
 * Auswahl: [n] mal pro Tag | Woche | Monat | Jahr  |  [n] mal alle [m] Tage
 * Das n (Ziel-Anzahl) steht INLINE im Label, Default 1.
 *
 * Der Reset-Tag wird NICHT hier gewählt – er ergibt sich aus dem Startdatum
 * (Wochentag bei Woche, Tag des Monats bei Monat/Jahr).
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun HabitCadencePicker(
    cadenceType: CadenceType,
    interval: Int,
    onCadenceChange: (CadenceType) -> Unit,
    onIntervalChange: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {

        Text(
            "Zeitraum",
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.SemiBold
        )

        Column(modifier = Modifier.selectableGroup()) {
            CadenceRow(
                prefix = "Jeden",
                suffix = "Tag",
                isSelected = cadenceType == CadenceType.DAY,
                interval = interval,
                onSelect = { onCadenceChange(CadenceType.DAY) },
                onIntervalChange = onIntervalChange
            )
            CadenceRow(
                prefix = "Jede",
                suffix = "Woche",
                isSelected = cadenceType == CadenceType.WEEK,
                interval = interval,
                onSelect = { onCadenceChange(CadenceType.WEEK) },
                onIntervalChange = onIntervalChange
            )
            CadenceRow(
                prefix = "Jeden",
                suffix = "Monat",
                isSelected = cadenceType == CadenceType.MONTH,
                interval = interval,
                onSelect = { onCadenceChange(CadenceType.MONTH) },
                onIntervalChange = onIntervalChange
            )
            CadenceRow(
                prefix = "Jedes",
                suffix = "Jahr",
                isSelected = cadenceType == CadenceType.YEAR,
                interval = interval,
                onSelect = { onCadenceChange(CadenceType.YEAR) },
                onIntervalChange = onIntervalChange
            )
            CadenceRow(
                prefix = "Alle",
                suffix = "Tage",
                isSelected = cadenceType == CadenceType.NDAYS,
                interval = interval,
                onSelect = { onCadenceChange(CadenceType.NDAYS) },
                onIntervalChange = onIntervalChange
            )
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun CadenceRow(
    prefix: String,
    suffix: String,
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
        Text(prefix, style = MaterialTheme.typography.bodyMedium)
        InlineIntField(value = interval, onValueChange = onIntervalChange)
        Text(suffix, style = MaterialTheme.typography.bodyMedium)
    }
}

/** Kompaktes Inline-Int-Feld (fühlt sich wie Text an, PrimaryContainer-Hintergrund). */
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
