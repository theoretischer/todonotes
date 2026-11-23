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
 * Radio-Zeilen mit Inline-Zahlen-Pillen. M14m: die Stepper-Zeile
 * steht jetzt IMMER über den Chips (wie im Aufgaben-Menü), mit der
 * Zahl, die zum gewählten Chip passt:
 *
 *  - „Alle (− n +) Tage" bei NDAYS (n = Intervall)
 *  - „Ziel (− n +) pro Tag/Woche/Monat/Jahr" sonst (n = goalCount)
 *
 * Das alte separate „Ziel: n mal"-Feld im Dialog ist damit weg —
 * das Ziel wird hier editiert. Keine Wochentags-/Monats-Extras:
 * der Reset-Tag ergibt sich aus dem Startdatum.
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

        // Stepper-Zeile über den Chips — Zahl je nach Chip (M14m).
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            if (cadenceType == CadenceType.NDAYS) {
                Text("Alle", style = MaterialTheme.typography.bodyMedium)
                IntervalStepper(
                    value = interval,
                    onValueChange = onIntervalChange
                )
                Text("Tage", style = MaterialTheme.typography.bodyMedium)
            } else {
                Text("Ziel", style = MaterialTheme.typography.bodyMedium)
                IntervalStepper(
                    value = goalCount,
                    onValueChange = onGoalCountChange
                )
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