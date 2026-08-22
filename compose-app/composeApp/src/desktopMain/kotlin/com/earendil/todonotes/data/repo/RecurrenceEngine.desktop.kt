package com.earendil.todonotes.data.repo

import org.dmfs.rfc5545.DateTime
import org.dmfs.rfc5545.recur.RecurrenceRule
import org.dmfs.rfc5545.recur.RecurrenceRuleIterator

private const val TAG = "RecurrenceEngine"

/**
 * Desktop (JVM) Implementierung: nutzt lib-recur für volle RFC 5545-Unterstützung.
 * Fällt bei Fehlern auf [RecurrenceCalculator] zurück.
 */
internal actual fun platformNextOccurrence(rrule: String, fromDue: Long?, now: Long): Long? {
    return try {
        val rule = RecurrenceRule(rrule)
        val startMillis = fromDue ?: now
        // Semantik: erste Occurrence streng nach max(fromDue, now).
        // - Abhaken am Fälligkeitstag → nächste Occurrence
        // - überfälliges Todo → springt in die Zukunft (nicht Schritt-für-Schritt abarbeiten)
        // - frühes Abhaken (now < fromDue) → Rhythmus bleibt (nächste nach fromDue)
        val afterMillis = maxOf(startMillis, now)
        val start = DateTime(startMillis).toAllDay() ?: DateTime(startMillis)

        val it: RecurrenceRuleIterator = rule.iterator(start)
        var safety = 0
        while (it.hasNext() && safety < 10000) {
            val nextInstance = it.nextDateTime()
            val nextMillis = nextInstance.getTimestamp()
            if (nextMillis > afterMillis) {
                return nextMillis
            }
            safety++
        }
        println("[$TAG] RRULE erschöpft oder keine Occurrence nach fromDue: $rrule")
        null
    } catch (e: Exception) {
        println("[$TAG] Fehler beim Parsen/Berechnen der RRULE '$rrule': ${e.message}")
        RecurrenceCalculator.nextOccurrence(rrule, fromDue, now)
    }
}
