package com.earendil.todonotes.ui.components

import java.util.Calendar

/**
 * UI-State-Modell für den Recurrence-Editor (Samsung-Reminder-Stil).
 * Wird in beide Richtungen gemappt: <-> RRULE-String.
 */
enum class RecurFreq(val prefix: String, val suffix: String) {
    NONE("Nicht wieder anzeigen", ""),
    MINUTELY("Jede", "Minute"),
    HOURLY("Jede", "Stunde"),
    DAILY("Jeden", "Tag"),
    WEEKLY("Jede", "Woche"),
    MONTHLY("Jeden", "Monat"),
    YEARLY("Jedes", "Jahr");

    /** Label mit eingebettetem n (für nicht ausgewählte Zeilen, reiner Text). */
    fun labelWith(n: Int): String =
        if (this == RecurFreq.NONE) prefix else "$prefix $n $suffix"
}

/** Monats-Wiederholung: drei Modi wie bei Samsung Reminder. */
enum class MonthlyMode(val label: String) {
    /** "Am 15. wiederholen" – BYMONTHDAY=15 */
    DAY_OF_MONTH("Am %d. wiederholen"),
    /** "Am 1. Montag wiederholen" – BYDAY=MO;BYSETPOS=1 */
    NTH_WEEKDAY("Am %d. %s wiederholen"),
    /** "Datumsangabe" – mehrere Tage wählbar, BYMONTHDAY=1,15,28 */
    MULTIPLE_DAYS("Datumsangabe")
}

enum class RecurEnd(val label: String) {
    FOREVER("Für immer"),
    COUNT("%s mal wiederholen")
}

data class RecurrenceState(
    val freq: RecurFreq = RecurFreq.NONE,
    val interval: Int = 1,
    /** Wochentage für WEEKLY. Kalender-Indizes: SO=1, MO=2, DI=3, MI=4, DO=5, FR=6, SA=7 (Calendar.*DAY_OF_WEEK). */
    val weekDays: Set<Int> = emptySet(),
    val monthlyMode: MonthlyMode = MonthlyMode.DAY_OF_MONTH,
    /** Für NTH_WEEKDAY: n (1..5) */
    val monthlyNth: Int = 1,
    /** Für NTH_WEEKDAY: Wochentag-Index */
    val monthlyWeekday: Int = Calendar.MONDAY,
    /** Für MULTIPLE_DAYS: Tage 1..31 */
    val monthlyDays: Set<Int> = emptySet(),
    val end: RecurEnd = RecurEnd.FOREVER,
    val endCount: Int = 5
)

object RecurrenceCodec {

    /** UI-State -> RRULE-String (null = keine Wiederholung). */
    fun encode(state: RecurrenceState): String? {
        if (state.freq == RecurFreq.NONE) return null
        val parts = mutableListOf<String>()
        parts += "FREQ=" + when (state.freq) {
            RecurFreq.NONE -> return null
            RecurFreq.MINUTELY -> "MINUTELY"
            RecurFreq.HOURLY -> "HOURLY"
            RecurFreq.DAILY -> "DAILY"
            RecurFreq.WEEKLY -> "WEEKLY"
            RecurFreq.MONTHLY -> "MONTHLY"
            RecurFreq.YEARLY -> "YEARLY"
        }
        if (state.interval > 1) parts += "INTERVAL=${state.interval}"

        // WEEKLY: BYDAY aus weekDays
        if (state.freq == RecurFreq.WEEKLY && state.weekDays.isNotEmpty()) {
            parts += "BYDAY=" + state.weekDays.joinToString(",") { weekdayToRrule(it) }
        }

        // MONTHLY
        if (state.freq == RecurFreq.MONTHLY) {
            when (state.monthlyMode) {
                MonthlyMode.DAY_OF_MONTH -> {
                    // BYMONTHDAY vom Startdatum – wir nutzen monthlyDays falls gesetzt, sonst 1
                    val days = if (state.monthlyDays.isNotEmpty()) state.monthlyDays else setOf(1)
                    parts += "BYMONTHDAY=" + days.joinToString(",")
                }
                MonthlyMode.NTH_WEEKDAY -> {
                    parts += "BYDAY=${weekdayToRrule(state.monthlyWeekday)}"
                    parts += "BYSETPOS=${state.monthlyNth}"
                }
                MonthlyMode.MULTIPLE_DAYS -> {
                    if (state.monthlyDays.isNotEmpty()) {
                        parts += "BYMONTHDAY=" + state.monthlyDays.joinToString(",")
                    }
                }
            }
        }

        if (state.end == RecurEnd.COUNT && state.endCount > 0) {
            parts += "COUNT=${state.endCount}"
        }
        return parts.joinToString(";")
    }

    /** RRULE-String -> UI-State. */
    fun decode(rrule: String?): RecurrenceState {
        if (rrule.isNullOrBlank()) return RecurrenceState()
        val s = RecurrenceState()
        val map = parseParts(rrule)
        val freqStr = map["FREQ"] ?: return s
        val freq = when (freqStr) {
            "MINUTELY" -> RecurFreq.MINUTELY
            "HOURLY" -> RecurFreq.HOURLY
            "DAILY" -> RecurFreq.DAILY
            "WEEKLY" -> RecurFreq.WEEKLY
            "MONTHLY" -> RecurFreq.MONTHLY
            "YEARLY" -> RecurFreq.YEARLY
            else -> RecurFreq.NONE
        }
        val interval = map["INTERVAL"]?.toIntOrNull() ?: 1
        val weekDays = map["BYDAY"]?.split(",")?.mapNotNull { rruleToWeekday(it) }?.toSet() ?: emptySet()
        val count = map["COUNT"]?.toIntOrNull()
        val bysetpos = map["BYSETPOS"]?.toIntOrNull()
        val bymonthday = map["BYMONTHDAY"]?.split(",")?.mapNotNull { it.toIntOrNull() }?.toSet() ?: emptySet()

        val monthlyMode = when {
            freq == RecurFreq.MONTHLY && bysetpos != null && weekDays.isNotEmpty() ->
                MonthlyMode.NTH_WEEKDAY
            freq == RecurFreq.MONTHLY && bymonthday.size > 1 ->
                MonthlyMode.MULTIPLE_DAYS
            freq == RecurFreq.MONTHLY ->
                MonthlyMode.DAY_OF_MONTH
            else -> MonthlyMode.DAY_OF_MONTH
        }

        return s.copy(
            freq = freq,
            interval = interval,
            weekDays = weekDays,
            monthlyMode = monthlyMode,
            monthlyNth = bysetpos ?: 1,
            monthlyWeekday = weekDays.firstOrNull() ?: Calendar.MONDAY,
            monthlyDays = bymonthday,
            end = if (count != null) RecurEnd.COUNT else RecurEnd.FOREVER,
            endCount = count ?: 5
        )
    }

    private fun parseParts(rrule: String): Map<String, String> =
        rrule.split(";").filter { it.contains("=") }.associate {
            val kv = it.split("=", limit = 2)
            kv[0].uppercase() to kv[1]
        }

    /** Calendar.Wochentag -> RRULE-Code. Calendar.MONDAY=2 -> "MO". */
    fun weekdayToRrule(calDay: Int): String = when (calDay) {
        Calendar.SUNDAY -> "SU"
        Calendar.MONDAY -> "MO"
        Calendar.TUESDAY -> "TU"
        Calendar.WEDNESDAY -> "WE"
        Calendar.THURSDAY -> "TH"
        Calendar.FRIDAY -> "FR"
        Calendar.SATURDAY -> "SA"
        else -> "MO"
    }

    fun rruleToWeekday(code: String): Int? = when (code.uppercase()) {
        "SU" -> Calendar.SUNDAY
        "MO" -> Calendar.MONDAY
        "TU" -> Calendar.TUESDAY
        "WE" -> Calendar.WEDNESDAY
        "TH" -> Calendar.THURSDAY
        "FR" -> Calendar.FRIDAY
        "SA" -> Calendar.SATURDAY
        else -> null
    }
}
