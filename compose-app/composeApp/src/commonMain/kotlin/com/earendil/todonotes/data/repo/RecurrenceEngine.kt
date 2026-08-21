package com.earendil.todonotes.data.repo

/**
 * RRULE-Engine (RFC 5545).
 *
 * **expect/actual**: Auf JVM (Android/Desktop) wird lib-recur (org.dmfs) genutzt
 * für volle RFC 5545-Unterstützung. Auf Wasm fällt [RecurrenceCalculator] ein,
 * der die häufigsten Fälle (FREQ, INTERVAL) abdeckt.
 *
 * WICHTIG: nextOccurrence liefert die nächste Occurrence, die STRENG NACH [now] liegt.
 * Beim Abhaken eines "jeden Tag ab heute"-Todos (now ≈ fromDue = heute) → morgen, nicht heute-nochmal.
 */
object RecurrenceEngine {

    /**
     * @param rrule RFC 5545 RRULE-String, z.B. "FREQ=DAILY;BYDAY=MO,TU"
     * @param fromDue Originaler Startzeitpunkt (millis). Die nächste Occurrence wird
     *        STRENG NACH fromDue berechnet – nicht nach now.
     * @param now Wird nur für den Fallback genutzt, falls fromDue null ist.
     * @return nächste Occurrence in millis, oder null wenn die RRULE abgelaufen ist (COUNT/UNTIL)
     */
    fun nextOccurrence(rrule: String, fromDue: Long?, now: Long): Long? =
        platformNextOccurrence(rrule, fromDue, now)
}

/**
 * Plattform-spezifische Implementierung.
 * - JVM: lib-recur (volle RFC 5545)
 * - Wasm: [RecurrenceCalculator] (FREQ + INTERVAL)
 */
internal expect fun platformNextOccurrence(rrule: String, fromDue: Long?, now: Long): Long?
