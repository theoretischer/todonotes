package com.earendil.todonotes.ui.habits

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
 * Stepper-Zeile über den Chips (M14m/M14n):
 *  - NDAYS → „Ziel (− n +) über (− m +) Tage“: n = goalCount,
 *    m = interval — das Ziel ist über die n Tage verteilt.
 *  - Tag/Woche/Monat/Jahr → nur „Ziel (− n +) pro Woche“ (das
 *    Intervall ist dort die Periodenlänge 1, kein Extra-Stepper).
 *
 * Keine Wochentags-/Monats-Extras: der Reset-Tag ergibt sich aus
 * dem Startdatum.
 */
@Composable
fun HabitCadencePicker(
    cadenceType: CadenceType,
    interval: Int,
    goalCount: Int,
    onCadenceChange: (CadenceType) -> Unit,
    onIntervalChange: (Int) -> Unit,
    onGoalCountChange: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {

        Text(
            "Zeitraum",
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.SemiBold
        )

        // Stepper-Zeile über den Chips — Ziel immer, Intervall nur
        // bei „Alle n Tage“ (M14n: „Ziel 3 über 2 Tage verteilt“).
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text("Ziel", style = MaterialTheme.typography.bodyMedium)
            IntervalStepper(
                value = goalCount,
                onValueChange = onGoalCountChange
            )
            if (cadenceType == CadenceType.NDAYS) {
                Text("über", style = MaterialTheme.typography.bodyMedium)
                IntervalStepper(
                    value = interval,
                    onValueChange = onIntervalChange
                )
                Text("Tage", style = MaterialTheme.typography.bodyMedium)
            } else {
                Text(
                    "pro ${cadenceType.label}",
                    style = MaterialTheme.typography.bodyMedium
                )
            }
        }

        // Segment-Buttons: Tag / Woche / Monat / Jahr / Alle-n-Tage.
        // M14j-Fix: FlowRow mit NATÜRLICHER Breite + Umbruch (weight(1f)
        // quetscht alles in eine Zeile — gelernt aus M14i).
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