package com.earendil.todonotes.data.repo

import kotlinx.datetime.Instant
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.atTime
import kotlinx.datetime.toInstant
import kotlinx.datetime.toLocalDateTime
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Parity-Test: vergleicht den Wasm-Fallback ([RecurrenceCalculator]) mit der
 * JVM-Referenz ([RecurrenceEngine] → lib-recur).
 *
 * Läuft nur auf desktopTest, weil lib-recur JVM-only ist.
 *
 * Parity gilt für den Normalfall "Abhaken am Fälligkeitstag (now ≥ fromDue,
 * aber vor der nächsten Occurrence)". Die bekannte SEMANTIK-DISKREPANZ
 * (überfällige Todos / frühes Abhaken) wird hier bewusst NICHT getestet —
 * siehe M6-Doku in MIGRATION-CMP.md.
 *
 * Beide Engines nutzen dieselbe System-TZ, deshalb vergleichen wir das lokale
 * Datum (robust gegen DST-Mikro-Unterschiede).
 */
class RecurrenceParityTest {

    private val tz = TimeZone.currentSystemDefault()

    private fun noon(year: Int, month: Int, day: Int): Long =
        LocalDate(year, month, day).atTime(LocalTime(12, 0)).toInstant(tz).toEpochMilliseconds()

    private fun evening(year: Int, month: Int, day: Int): Long =
        LocalDate(year, month, day).atTime(LocalTime(18, 0)).toInstant(tz).toEpochMilliseconds()

    private fun dateOf(millis: Long): LocalDate =
        Instant.fromEpochMilliseconds(millis).toLocalDateTime(tz).date

    /**
     * Parity-Check für "abhaken am Fälligkeitstag": fromDue = Mittag, now = 18:00.
     * Erwartung: beide Engines liefern dieselbe nächste Occurrence.
     */
    private fun assertParityAtCompletion(label: String, rrule: String, base: Long) {
        val now = evening(
            dateOf(base).year, dateOf(base).monthNumber, dateOf(base).dayOfMonth
        )
        val jvm = RecurrenceEngine.nextOccurrence(rrule, base, now)
        val fallback = RecurrenceCalculator.nextOccurrence(rrule, base, now)
        if (jvm == null) {
            assertTrue(fallback == null, "$label: JVM=null aber fallback=$fallback")
            return
        }
        assertTrue(fallback != null, "$label: fallback=null aber JVM=${dateOf(jvm)}")
        assertEquals(
            dateOf(jvm), dateOf(fallback!!),
            "$label: rrule='$rrule' JVM=${dateOf(jvm)} fallback=${dateOf(fallback)}"
        )
    }

    @Test fun parity_daily() = assertParityAtCompletion("daily", "FREQ=DAILY", noon(2025, 1, 15))

    @Test fun parity_daily_interval_3() =
        assertParityAtCompletion("daily-3", "FREQ=DAILY;INTERVAL=3", noon(2025, 1, 15))

    @Test fun parity_weekly() = assertParityAtCompletion("weekly", "FREQ=WEEKLY", noon(2025, 1, 15))

    @Test fun parity_weekly_byday_mo_we_fr_from_monday() =
        assertParityAtCompletion("weekly-mwf", "FREQ=WEEKLY;BYDAY=MO,WE,FR", noon(2025, 1, 13))

    @Test fun parity_weekly_byday_tu_th_from_tuesday() =
        assertParityAtCompletion("weekly-th", "FREQ=WEEKLY;BYDAY=TU,TH", noon(2025, 1, 14))

    @Test fun parity_weekly_interval_2() =
        assertParityAtCompletion("weekly-2", "FREQ=WEEKLY;INTERVAL=2", noon(2025, 1, 15))

    @Test fun parity_monthly() = assertParityAtCompletion("monthly", "FREQ=MONTHLY", noon(2025, 1, 15))

    @Test fun parity_monthly_day_31_skips_feb() =
        assertParityAtCompletion("monthly-31", "FREQ=MONTHLY", noon(2025, 1, 31))

    @Test fun parity_monthly_bymonthday() =
        assertParityAtCompletion("monthly-md", "FREQ=MONTHLY;BYMONTHDAY=1,15", noon(2025, 1, 15))

    @Test fun parity_monthly_bysetpos_2nd_monday() =
        assertParityAtCompletion("monthly-setpos", "FREQ=MONTHLY;BYDAY=MO;BYSETPOS=2", noon(2025, 1, 13))

    @Test fun parity_monthly_interval_3() =
        assertParityAtCompletion("monthly-3", "FREQ=MONTHLY;INTERVAL=3", noon(2025, 1, 15))

    @Test fun parity_yearly() = assertParityAtCompletion("yearly", "FREQ=YEARLY", noon(2025, 1, 15))

    @Test fun parity_yearly_feb_29() =
        assertParityAtCompletion("yearly-leap", "FREQ=YEARLY", noon(2024, 2, 29))

    @Test fun parity_count_3() =
        assertParityAtCompletion("count-3", "FREQ=DAILY;COUNT=3", noon(2025, 1, 15))
}
