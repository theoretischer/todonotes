package com.earendil.todonotes.data.repo

import java.util.Calendar
import java.util.TimeZone

/**
 * Sehr einfache RRULE-Auswertung für die häufigsten Fälle (FREQ=DAILY/WEEKLY/MONTHLY/YEARLY, optional INTERVAL).
 *
 * Für die volle RFC 5545 (BYDAY, COUNT, UNTIL, BYSETPOS etc.) kommt später dmfs/lib-recur rein.
 */
object RecurrenceCalculator {

    /**
     * Berechnet die nächste Occurrence ab [after], basierend auf der ursprünglichen [fromDue].
     * Gibt null zurück, wenn keine weitere Occurrence (z.B. COUNT erschöpft – wird hier noch
     * nicht ausgewertet, wir enden nie).
     */
    fun nextOccurrence(rrule: String, fromDue: Long?, now: Long): Long? {
        val base = fromDue ?: now
        val freq = parseFreq(rrule) ?: return null
        val interval = parseInterval(rrule) ?: 1

        val cal = Calendar.getInstance().apply {
            timeZone = TimeZone.getDefault()
            timeInMillis = base
        }

        // So lange weiterspringen, bis wir in der Zukunft sind
        var iterations = 0
        while (cal.timeInMillis <= now && iterations < 365 * 10) {
            advance(cal, freq, interval)
            iterations++
        }
        return cal.timeInMillis
    }

    private fun advance(cal: Calendar, freq: Freq, interval: Int) {
        when (freq) {
            Freq.DAILY -> cal.add(Calendar.DAY_OF_MONTH, interval)
            Freq.WEEKLY -> cal.add(Calendar.WEEK_OF_YEAR, interval)
            Freq.MONTHLY -> cal.add(Calendar.MONTH, interval)
            Freq.YEARLY -> cal.add(Calendar.YEAR, interval)
        }
    }

    private fun parseFreq(rrule: String): Freq? {
        val match = Regex("FREQ=(\\w+)").find(rrule) ?: return null
        return when (match.groupValues[1].uppercase()) {
            "DAILY" -> Freq.DAILY
            "WEEKLY" -> Freq.WEEKLY
            "MONTHLY" -> Freq.MONTHLY
            "YEARLY" -> Freq.YEARLY
            else -> null
        }
    }

    private fun parseInterval(rrule: String): Int? =
        Regex("INTERVAL=(\\d+)").find(rrule)?.groupValues?.get(1)?.toIntOrNull()

    private enum class Freq { DAILY, WEEKLY, MONTHLY, YEARLY }
}
