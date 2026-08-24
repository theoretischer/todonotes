package com.earendil.todonotes.data.entity

import androidx.room3.Entity
import androidx.room3.PrimaryKey

/**
 * Eine Gewohnheit (Habit) mit Ziel-Häufigkeit pro Periode.
 *
 * - cadenceType: Art der Periode (DAY/WEEK/MONTH/YEAR/NDAYS)
 * - interval: z.B. "jede 2 Woche" -> interval=2. Bei NDAYS: "alle 3 Tage".
 * - resetAnchorDay: Tag des Monats (1..31), an dem die Periode bei MONTHLY/YEARLY resetted wird.
 * - resetAnchorMonth: Monat (1..12) für YEARLY-Reset. Bei MONTHLY nicht nötig.
 * - resetWeekday: bei WEEKLY der Wochentag, an dem die Woche resetted wird (Calendar.*DAY_OF_WEEK).
 *   Wird beim Erstellen aus dem Anfangsdatum abgeleitet.
 * - goalCount: Ziel pro Periode, z.B. 2 ("2x pro Woche")
 * - startDate: Anfangsdatum (millis) – bestimmt den Reset-Anchor (Wochentag/Tag des Monats/Monat)
 * - logToHistory: pro Periode beim Reset einen Verlaufseintrag erstellen ("2/2 Pizza")
 * - lastLoggedPeriodStart: Start der Periode, die bereits in den Verlauf eingetragen wurde.
 *   Dient der Erkennung eines Periodenwechsels beim nächsten App-Start.
 * - deletedAt: Soft-Delete
 * - updatedAt: Last-Write-Wins beim Sync
 * - userId: Multi-User (M1). Default "legacy-user" bei Migration v9→v10.
 * - type: Art der Gewohnheit (Habitype.HABIT / HabitType.SATISFACTION).
 *   HABIT = klassisch (n mal pro Periode + Reset), SATISFACTION = 0-10 Skala + +/- Buttons.
 * - currentRating: aktueller Zufriedenheitswert (0-10). Nur bei SATISFACTION relevant.
 * - position: Sortierposition (1D-Reorder via Drag-Drop). Default 0.
 */
@Entity(tableName = "habits")
data class Habit(
    @PrimaryKey
    val id: String,
    val title: String,
    val notes: String = "",
    val cadenceType: CadenceType,
    val interval: Int = 1,
    val resetWeekday: Int? = null,
    val resetAnchorDay: Int? = null,
    val resetAnchorMonth: Int? = null,
    val goalCount: Int,
    val startDate: Long,
    val logToHistory: Boolean = true,
    val lastLoggedPeriodStart: Long? = null,
    val type: HabitType = HabitType.HABIT,
    val currentRating: Int? = null,
    val position: Int = 0,
    val createdAt: Long,
    val updatedAt: Long,
    val deletedAt: Long? = null,
    val userId: String = "legacy-user"
) {
    val isOpen: Boolean get() = deletedAt == null
}

enum class HabitType(val label: String) {
    HABIT("Gewohnheit"),
    SATISFACTION("Zufriedenheit")
}

enum class CadenceType(val label: String) {
    DAY("Tag"),
    WEEK("Woche"),
    MONTH("Monat"),
    YEAR("Jahr"),
    NDAYS("Tage")
}
