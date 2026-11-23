package com.earendil.todonotes.ui.habits

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.earendil.todonotes.data.entity.CadenceType
import com.earendil.todonotes.ui.components.IntervalStepper

/**
 * Zeitraum-Picker für Gewohnheiten — M14j: dasselbe Auswahl-System
 * wie der Recurrence-Editor (Stepper + Segment-Chips), statt der
 * Radio-Zeilen mit Inline-Zahlen-Pillen.
 *
 * Semantik:
 *  - Tag/Woche/Monat/Jahr → Ziel gilt PRO Zeitraum (Ziel-Anzahl
 *    steckt im eigenen Feld darunter, Default 1).
 *  - "Alle n Tage" (NDAYS) → n ist das Intervall → Stepper oben.
 *
 * Keine Wochentags-/Monats-Extras hier — der Reset-Tag ergibt sich
 * aus dem Startdatum.
 */
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

        // Intervall-Stepper nur bei "Alle n Tage"
        AnimatedVisibility(visible = cadenceType == CadenceType.NDAYS) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text("Alle", style = MaterialTheme.typography.bodyMedium)
                IntervalStepper(
                    value = interval,
                    onValueChange = onIntervalChange
                )
                Text("Tage", style = MaterialTheme.typography.bodyMedium)
            }
        }

        // Segment-Buttons: Tag / Woche / Monat / Jahr / Alle-n-Tage.
        // M14j-Fix: FlowRow mit NATÜRLICHER Breite + Umbruch (derselbe
        // Fehler wie M14i: weight(1f) quetscht alles in eine Zeile).
        FlowRow(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            val units = listOf(
                CadenceType.DAY to "Tag",
                CadenceType.WEEK to "Woche",
                CadenceType.MONTH to "Monat",
                CadenceType.YEAR to "Jahr",
                CadenceType.NDAYS to "Tage"
            )
            units.forEach { (type, label) ->
                FilterChip(
                    selected = cadenceType == type,
                    onClick = { onCadenceChange(type) },
                    label = {
                        Text(
                            label,
                            style = MaterialTheme.typography.bodyMedium
                        )
                    },
                    shape = CircleShape
                )
            }
        }
    }
}