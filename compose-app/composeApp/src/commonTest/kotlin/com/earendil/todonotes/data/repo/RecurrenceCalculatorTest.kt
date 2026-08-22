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
import kotlin.test.assertNull

/**
 * Standalone-Tests für [RecurrenceCalculator] (Wasm-Fallback).
 *
 * Alle Zeitpunkte werden als lokale Mittag (12:00) konstruiert und Ergebnisse
 * als LocalDate verglichen → Tests sind unabhängig von der System-TimeZone und
 * nicht von DST-Grenzen betroffen. MINUTELY/HOURLY vergleichen Millis-Differenz.
 */
class RecurrenceCalculatorTest {

    private val tz = TimeZone.currentSystemDefault()

    /** Lokales Mittag eines Datums → epochMillis. */
    private fun noon(year: Int, month: Int, day: Int): Long =
        LocalDate(year, month, day).atTime(LocalTime(12, 0)).toInstant(tz).toEpochMilliseconds()

    /** Später am selben Tag (18:00) — simuliert "Abhaken am Fälligkeitstag". */
    private fun evening(year: Int, month: Int, day: Int): Long =
        LocalDate(year, month, day).atTime(LocalTime(18, 0)).toInstant(tz).toEpochMilliseconds()

    /** epochMillis → lokales Datum. */
    private fun dateOf(millis: Long): LocalDate =
        Instant.fromEpochMilliseconds(millis).toLocalDateTime(tz).date

    private fun nextDate(rrule: String, base: Long, now: Long): LocalDate =
        dateOf(RecurrenceCalculator.nextOccurrence(rrule, base, now)!!)

    // --- DAILY ------------------------------------------------------

    @Test fun daily_next_day() {
        // base 15.1., abhaken am selben Tag 18:00 → 16.1.
        assertEquals(
            LocalDate(2025, 1, 16),
            nextDate("FREQ=DAILY", noon(2025, 1, 15), evening(2025, 1, 15))
        )
    }

    @Test fun daily_interval_3() {
        // base 15.1., alle 3 Tage, abhaken am 15. → 18.1.
        assertEquals(
            LocalDate(2025, 1, 18),
            nextDate("FREQ=DAILY;INTERVAL=3", noon(2025, 1, 15), evening(2025, 1, 15))
        )
    }

    @Test fun daily_overdue_skips_to_future() {
        // 3 Tage überfällig: base 12.1., now 15.1. → springt auf 16.1.
        // (Semantik: erste Occ. nach max(fromDue, now) — jetzt einheitlich auf allen Plattformen)
        assertEquals(
            LocalDate(2025, 1, 16),
            nextDate("FREQ=DAILY", noon(2025, 1, 12), evening(2025, 1, 15))
        )
    }

    @Test fun daily_early_completion_keeps_rhythm() {
        // Frühes Abhaken: base 16.1. (zukunft), now 15.1. → Rhythmus bleibt → 17.1.
        // (Semantik: erste Occ. nach max(fromDue=16., now=15.) = nach dem 16. → 17.)
        assertEquals(
            LocalDate(2025, 1, 17),
            nextDate("FREQ=DAILY", noon(2025, 1, 16), noon(2025, 1, 15))
        )
    }

    // --- WEEKLY -----------------------------------------------------

    @Test fun weekly_no_byday_same_weekday() {
        // base Mittwoch 15.1.2025, wöchentlich → 22.1.
        assertEquals(
            LocalDate(2025, 1, 22),
            nextDate("FREQ=WEEKLY", noon(2025, 1, 15), evening(2025, 1, 15))
        )
    }

    @Test fun weekly_byday_mo_we_fr_from_monday() {
        // base Mo 13.1., BYDAY=MO,WE,FR, abhaken Mo 18:00 → Mi 15.1.
        assertEquals(
            LocalDate(2025, 1, 15),
            nextDate("FREQ=WEEKLY;BYDAY=MO,WE,FR", noon(2025, 1, 13), evening(2025, 1, 13))
        )
    }

    @Test fun weekly_byday_mo_we_fr_from_friday_wraps_week() {
        // base Mo 13.1., abhaken Fr 17.1. 18:00 → Mo 20.1. (nächste Woche)
        assertEquals(
            LocalDate(2025, 1, 20),
            nextDate("FREQ=WEEKLY;BYDAY=MO,WE,FR", noon(2025, 1, 13), evening(2025, 1, 17))
        )
    }

    @Test fun weekly_interval_2() {
        // base Mi 15.1., alle 2 Wochen → 29.1.
        assertEquals(
            LocalDate(2025, 1, 29),
            nextDate("FREQ=WEEKLY;INTERVAL=2", noon(2025, 1, 15), evening(2025, 1, 15))
        )
    }

    // --- MONTHLY ----------------------------------------------------

    @Test fun monthly_day_of_month() {
        // base 15.1., monatlich → 15.2.
        assertEquals(
            LocalDate(2025, 2, 15),
            nextDate("FREQ=MONTHLY", noon(2025, 1, 15), evening(2025, 1, 15))
        )
    }

    @Test fun monthly_by_monthday_picks_first_in_next_month() {
        // base 15.1., BYMONTHDAY=1,15 → Jan-Kandidaten 1 (<base skip), 15; Feb: 1, 15 → 1.2.
        assertEquals(
            LocalDate(2025, 2, 1),
            nextDate("FREQ=MONTHLY;BYMONTHDAY=1,15", noon(2025, 1, 15), evening(2025, 1, 15))
        )
    }

    @Test fun monthly_bysetpos_second_monday() {
        // base = 2. Montag Jan 2025 = 13.1., BYDAY=MO;BYSETPOS=2 → 2. Montag Feb = 10.2.
        assertEquals(
            LocalDate(2025, 2, 10),
            nextDate("FREQ=MONTHLY;BYDAY=MO;BYSETPOS=2", noon(2025, 1, 13), evening(2025, 1, 13))
        )
    }

    @Test fun monthly_day_31_skips_february() {
        // base 31.1., monatlich → Feb hat keinen 31. → überspringen → 31.3.
        assertEquals(
            LocalDate(2025, 3, 31),
            nextDate("FREQ=MONTHLY", noon(2025, 1, 31), evening(2025, 1, 31))
        )
    }

    @Test fun monthly_interval_3() {
        // base 15.1., alle 3 Monate → 15.4.
        assertEquals(
            LocalDate(2025, 4, 15),
            nextDate("FREQ=MONTHLY;INTERVAL=3", noon(2025, 1, 15), evening(2025, 1, 15))
        )
    }

    // --- YEARLY -----------------------------------------------------

    @Test fun yearly_next_year() {
        assertEquals(
            LocalDate(2026, 1, 15),
            nextDate("FREQ=YEARLY", noon(2025, 1, 15), evening(2025, 1, 15))
        )
    }

    @Test fun yearly_feb_29_skips_non_leap_years() {
        // base 29.2.2024 (Schaltjahr) → 2025/2026/2027 überspringen → 29.2.2028
        assertEquals(
            LocalDate(2028, 2, 29),
            nextDate("FREQ=YEARLY", noon(2024, 2, 29), evening(2024, 2, 29))
        )
    }

    // --- COUNT ------------------------------------------------------

    @Test fun count_exhausted_returns_null() {
        // FREQ=DAILY;COUNT=3 → 15., 16., 17.1.; now=20.1. → erschöpft
        assertNull(
            RecurrenceCalculator.nextOccurrence("FREQ=DAILY;COUNT=3", noon(2025, 1, 15), evening(2025, 1, 20))
        )
    }

    @Test fun count_not_exhausted_returns_next() {
        // 2. von 3 Occurrences: 15. → 16.
        assertEquals(
            LocalDate(2025, 1, 16),
            nextDate("FREQ=DAILY;COUNT=3", noon(2025, 1, 15), evening(2025, 1, 15))
        )
    }

    // --- MINUTELY / HOURLY (Millis-Vergleich) -----------------------

    @Test fun minutely_next_minute() {
        val base = noon(2025, 1, 15)
        val now = base + 30_000 // 30s später
        val result = RecurrenceCalculator.nextOccurrence("FREQ=MINUTELY", base, now)!!
        assertEquals(60_000L, result - base)
    }

    @Test fun hourly_interval_2() {
        val base = noon(2025, 1, 15)
        val now = base + 30 * 60_000 // 30min später
        val result = RecurrenceCalculator.nextOccurrence("FREQ=HOURLY;INTERVAL=2", base, now)!!
        assertEquals(2 * 60 * 60_000L, result - base)
    }

    // --- Edge cases -------------------------------------------------

    @Test fun unparseable_returns_null() {
        assertNull(RecurrenceCalculator.nextOccurrence("not-a-rule", noon(2025, 1, 15), evening(2025, 1, 15)))
    }

    @Test fun from_due_null_uses_now_as_base() {
        // fromDue null → base = now; DAILY → now + 1 Tag
        val now = noon(2025, 1, 15)
        val result = RecurrenceCalculator.nextOccurrence("FREQ=DAILY", null, now)!!
        // base==now (12:00), occ base nicht > now → nächster Tag
        assertEquals(LocalDate(2025, 1, 16), dateOf(result))
    }
}
