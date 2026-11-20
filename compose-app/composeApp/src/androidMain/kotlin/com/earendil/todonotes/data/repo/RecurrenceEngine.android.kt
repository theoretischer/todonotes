package com.earendil.todonotes.data.repo

import android.util.Log
import org.dmfs.rfc5545.DateTime
import org.dmfs.rfc5545.recur.RecurrenceRule
import org.dmfs.rfc5545.recur.RecurrenceRuleIterator
import java.util.TimeZone

private const val TAG = "RecurrenceEngine"

/**
 * JVM (Android) Implementierung: nutzt lib-recur für volle RFC 5545-Unterstützung.
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
        // BUGFIX: DateTime(long) nutzt UTC und toAllDay() stript die Uhrzeit
        // (All-Day = Mitternacht UTC = 02:00 CEST). Start mit System-TZ bauen,
        // damit die Original-Uhrzeit (z.B. 09:00) erhalten bleibt.
        val start = DateTime(TimeZone.getDefault(), startMillis)

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
        Log.i(TAG, "RRULE erschöpft oder keine Occurrence nach fromDue: $rrule")
        null
    } catch (e: Exception) {
        Log.e(TAG, "Fehler beim Parsen/Berechnen der RRULE '$rrule'", e)
        RecurrenceCalculator.nextOccurrence(rrule, fromDue, now)
    }
}
