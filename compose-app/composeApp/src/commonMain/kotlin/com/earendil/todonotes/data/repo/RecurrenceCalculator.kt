package com.earendil.todonotes.data.repo

import kotlinx.datetime.*

/**
 * Sehr einfache RRULE-Auswertung für die häufigsten Fälle
 * (FREQ=DAILY/WEEKLY/MONTHLY/YEARLY, optional INTERVAL).
 *
 * Dient als Fallback für Plattformen, auf denen lib-recur nicht verfügbar ist
 * (z.B. Wasm). Auf JVM (Android/Desktop) wird primär [RecurrenceEngine] mit
 * lib-recur genutzt.
 */
object RecurrenceCalculator {

    private val TZ = TimeZone.currentSystemDefault()
    private val DAY_MS = 24L * 60 * 60 * 1000

    /**
     * Berechnet die nächste Occurrence ab [now], basierend auf der ursprünglichen [fromDue].
     * Gibt null zurück, wenn keine RRULE erkannt wurde.
     */
    fun nextOccurrence(rrule: String, fromDue: Long?, now: Long): Long? {
        val base = fromDue ?: now
        val freq = parseFreq(rrule) ?: return null
        val interval = parseInterval(rrule) ?: 1

        var current = base
        var iterations = 0
        while (current <= now && iterations < 365 * 10) {
            current = advance(current, freq, interval)
            iterations++
        }
        return current
    }

    private fun advance(millis: Long, freq: Freq, interval: Int): Long {
        val ldt = Instant.fromEpochMilliseconds(millis).toLocalDateTime(TZ)
        val newDate = when (freq) {
            Freq.DAILY -> ldt.date.plus(interval, DateTimeUnit.DAY)
            Freq.WEEKLY -> ldt.date.plus(interval * 7, DateTimeUnit.DAY)
            Freq.MONTHLY -> ldt.date.plus(interval, DateTimeUnit.MONTH)
            Freq.YEARLY -> ldt.date.plus(interval, DateTimeUnit.YEAR)
        }
        val timeOfDayMs = millis - ldt.date.atStartOfDayIn(TZ).toEpochMilliseconds()
        return newDate.atStartOfDayIn(TZ).toEpochMilliseconds() + timeOfDayMs
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
