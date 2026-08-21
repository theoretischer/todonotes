package com.earendil.todonotes.data.repo

/**
 * Wasm-Implementierung: lib-recur ist JVM-only und steht auf Wasm nicht zur Verfügung.
 * Fällt auf [RecurrenceCalculator] zurück, der die häufigsten RRULE-Fälle
 * (FREQ=DAILY/WEEKLY/MONTHLY/YEARLY + INTERVAL) abdeckt.
 */
internal actual fun platformNextOccurrence(rrule: String, fromDue: Long?, now: Long): Long? =
    RecurrenceCalculator.nextOccurrence(rrule, fromDue, now)
