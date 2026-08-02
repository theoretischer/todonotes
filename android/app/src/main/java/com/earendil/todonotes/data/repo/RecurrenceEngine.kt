package com.earendil.todonotes.data.repo

import android.util.Log
import org.dmfs.rfc5545.DateTime
import org.dmfs.rfc5545.recur.RecurrenceRule
import org.dmfs.rfc5545.recur.RecurrenceRuleIterator

/**
 * RRULE-Engine basierend auf dmfs/lib-recur (RFC 5545).
 *
 * Unterstützt: FREQ, INTERVAL, BYDAY, BYMONTHDAY, BYSETPOS, COUNT, UNTIL, ...
 *
 * WICHTIG: nextOccurrence liefert die nächste Occurrence, die STRENG NACH [now] liegt.
 * Beim Abhaken eines "jeden Tag ab heute"-Todos (now ≈ fromDue = heute) → morgen, nicht heute-nochmal.
 */
object RecurrenceEngine {

    private const val TAG = "RecurrenceEngine"

    /**
     * @param rrule RFC 5545 RRULE-String, z.B. "FREQ=DAILY;BYDAY=MO,TU"
     * @param fromDue Originaler Startzeitpunkt (millis). Die nächste Occurrence wird
     *        STRENG NACH fromDue berechnet – nicht nach now. So gilt: "die Fälligkeit ist
     *        erledigt, weiter geht's ab der nächsten geplanten Occurrence", egal wann genau
     *        der Nutzer abhakt (Samsung-Reminder-Stil / Option C).
     * @param now Wird nur für den Fallback genutzt, falls fromDue null ist.
     * @return nächste Occurrence in millis, oder null wenn die RRULE abgelaufen ist (COUNT/UNTIL)
     */
    fun nextOccurrence(rrule: String, fromDue: Long?, now: Long): Long? {
        return try {
            val rule = RecurrenceRule(rrule)
            // Start für die Iteration: fromDue falls gesetzt, sonst now.
            // Der Such-Zeitpunkt für "strikt nach" ist ebenfalls fromDue (falls gesetzt),
            // damit die Berechnung immer ab der Fälligkeit weitergeht.
            val startMillis = fromDue ?: now
            val afterMillis = fromDue ?: now
            val start = DateTime(startMillis).toAllDay() ?: DateTime(startMillis)

            val it: RecurrenceRuleIterator = rule.iterator(start)
            // Erste Occurrence, die strikt nach afterMillis liegt.
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
}
