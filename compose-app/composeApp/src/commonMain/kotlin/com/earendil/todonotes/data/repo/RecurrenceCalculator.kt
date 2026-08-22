package com.earendil.todonotes.data.repo

import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.DayOfWeek
import kotlinx.datetime.Instant
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.atStartOfDayIn
import kotlinx.datetime.atTime
import kotlinx.datetime.plus
import kotlinx.datetime.toInstant
import kotlinx.datetime.toLocalDateTime

/**
 * RRULE-Auswertung (RFC 5545-Subset) als Fallback für Plattformen ohne lib-recur
 * (z.B. Wasm). Deckt alle RRULE-Muster ab, die der Recurrence-Picker der App
 * erzeugen kann:
 *
 *  - FREQ = MINUTELY | HOURLY | DAILY | WEEKLY | MONTHLY | YEARLY
 *  - INTERVAL=n
 *  - BYDAY=MO,TU,...        (WEEKLY: mehrere Wochentage; MONTHLY: mit BYSETPOS)
 *  - BYMONTHDAY=1,15,28     (MONTHLY: bestimmte Monatstage)
 *  - BYSETPOS=n             (MONTHLY: n-ter BYDAY-Wochentag im Monat, z.B. 2. Montag)
 *  - COUNT=n                (Ende nach n Occurrences, gezählt ab fromDue inkl.)
 *
 * Nicht unterstützt (erzeugt die UI nicht): UNTIL, WKST, BYWEEKNO, BYYEARDAY,
 * BYHOUR/MINUTE/SECOND, negative BYSETPOS, mehrere BYxxx kombiniert (außer
 * BYDAY+BYSETPOS). Solche Regeln → beste Annäherung oder null.
 *
 * Semantik: liefert die erste Occurrence STRENG NACH [now]. Occurrences werden
 * ab [fromDue] (DTSTART-Äquivalent, inklusive) generiert; COUNT läuft ab fromDue.
 * Uhrzeit bleibt immer die von [fromDue]. Invalide Daten (z.B. 30. Feb,
 * 31. in kurzen Monaten) werden RFC-konform ÜBERSPRUNGEN (nicht geclampet).
 *
 * DST: Kandidaten werden in lokaler Zeit gerechnet (kotlinx.datetime),
 * MINUTELY/HOURLY epoch-basiert (an DST-Grenzen ggf. ±1h Abweichung zu lib-recur).
 */
object RecurrenceCalculator {

    private val TZ = TimeZone.currentSystemDefault()
    private const val MINUTE_MS = 60_000L
    private const val HOUR_MS = 60 * MINUTE_MS
    /** Sicherheitslimit für Generier-Schleifen (~500 Jahre bei MONTHLY). */
    private const val MAX_PERIODS = 6000

    private data class ParsedRule(
        val freq: Freq,
        val interval: Int = 1,
        val byDay: List<DayOfWeek> = emptyList(),
        val byMonthDay: List<Int> = emptyList(),
        val bySetPos: Int? = null,
        val count: Int? = null
    )

    private enum class Freq { MINUTELY, HOURLY, DAILY, WEEKLY, MONTHLY, YEARLY }

    /**
     * Erste Occurrence streng nach [now], oder null wenn die Regel abgelaufen
     * (COUNT erreicht) oder nicht parsebar ist.
     */
    fun nextOccurrence(rrule: String, fromDue: Long?, now: Long): Long? {
        val rule = parse(rrule) ?: return null
        val base = fromDue ?: now
        return generate(rule, base) { occ -> occ > now }
    }

    // ---------------------------------------------------------------
    // Generierung: prüft jede Occurrence ab base (inkl.) in chronologischer
    // Reihenfolge gegen [accept]. Die erste akzeptierte Occurrence ist das
    // Ergebnis. COUNT läuft ab base (inkl.) mit. null wenn erschöpft.
    // ---------------------------------------------------------------
    private fun generate(rule: ParsedRule, base: Long, accept: (Long) -> Boolean): Long? {
        var occurrences = 0
        var result: Long? = null
        var stopped = false
        val baseLdt = Instant.fromEpochMilliseconds(base).toLocalDateTime(TZ)
        val baseTime = baseLdt.time
        val baseDate = baseLdt.date

        fun emit(millis: Long) {
            if (stopped || millis < base) return // Occurrences vor DTSTART existieren nicht
            occurrences++
            if (rule.count != null && occurrences > rule.count) {
                stopped = true
                return
            }
            if (accept(millis)) {
                result = millis
                stopped = true
            }
        }

        fun millisOf(date: LocalDate): Long =
            date.atTime(baseTime).toInstant(TZ).toEpochMilliseconds()

        when (rule.freq) {
            Freq.MINUTELY, Freq.HOURLY -> {
                val stepMs = rule.interval * if (rule.freq == Freq.MINUTELY) MINUTE_MS else HOUR_MS
                // Epoch-basiert (kein Kalender nötig); stepMs-Granularität hält Iterationen klein
                var candidate = base
                var i = 0
                while (!stopped && i < MAX_PERIODS * 1000) {
                    emit(candidate)
                    candidate += stepMs
                    i++
                }
            }
            Freq.DAILY -> {
                var date = baseDate
                var i = 0
                while (!stopped && i < MAX_PERIODS) {
                    emit(millisOf(date))
                    date = date.plus(rule.interval, DateTimeUnit.DAY)
                    i++
                }
            }
            Freq.WEEKLY -> {
                val days = if (rule.byDay.isNotEmpty()) rule.byDay.sorted() else listOf(baseDate.dayOfWeek)
                var weekStart = mondayOfWeek(baseDate)
                var i = 0
                while (!stopped && i < MAX_PERIODS) {
                    for (d in days) {
                        if (stopped) break
                        emit(millisOf(weekStart.plus(isoOffset(d), DateTimeUnit.DAY)))
                    }
                    weekStart = weekStart.plus(rule.interval * 7, DateTimeUnit.DAY)
                    i++
                }
            }
            Freq.MONTHLY -> {
                var monthCursor = firstOfMonth(baseDate)
                var i = 0
                while (!stopped && i < MAX_PERIODS) {
                    for (date in monthlyCandidates(rule, monthCursor, baseDate.dayOfMonth)) {
                        if (stopped) break
                        emit(millisOf(date))
                    }
                    monthCursor = monthCursor.plus(rule.interval, DateTimeUnit.MONTH)
                    i++
                }
            }
            Freq.YEARLY -> {
                var yearCursor = baseDate
                var i = 0
                while (!stopped && i < MAX_PERIODS / 12) {
                    // Implizit: gleicher Monat+Tag wie base. 29. Feb → Nicht-Schaltjahr überspringen.
                    val date = runCatching {
                        LocalDate(yearCursor.year, baseDate.month, baseDate.dayOfMonth)
                    }.getOrNull()
                    if (date != null) emit(millisOf(date))
                    yearCursor = yearCursor.plus(rule.interval, DateTimeUnit.YEAR)
                    i++
                }
            }
        }
        return result
    }

    /** Alle gültigen Kandidaten-Tage eines Monats, sortiert. */
    private fun monthlyCandidates(rule: ParsedRule, monthStart: LocalDate, baseDay: Int): List<LocalDate> {
        val dim = daysInMonth(monthStart.year, monthStart.monthNumber)
        val days = mutableListOf<Int>()
        when {
            rule.bySetPos != null && rule.byDay.isNotEmpty() -> {
                // n-ter Wochentag im Monat (z.B. BYDAY=MO;BYSETPOS=2 → 2. Montag)
                for (dow in rule.byDay) {
                    val matches = (1..dim).mapNotNull { day ->
                        val date = runCatching { LocalDate(monthStart.year, monthStart.monthNumber, day) }.getOrNull()
                        if (date?.dayOfWeek == dow) day else null
                    }
                    val picked = if (rule.bySetPos > 0) matches.getOrNull(rule.bySetPos - 1)
                    else matches.getOrNull(matches.size + rule.bySetPos)
                    if (picked != null) days += picked
                }
            }
            rule.byMonthDay.isNotEmpty() -> {
                days += rule.byMonthDay.filter { it in 1..dim }
            }
            else -> {
                // Vanilla MONTHLY: gleicher Monatstag wie base (RFC: implizites BYMONTHDAY)
                if (baseDay in 1..dim) days += baseDay
            }
        }
        return days.sorted().map { LocalDate(monthStart.year, monthStart.monthNumber, it) }
    }

    // ---------------------------------------------------------------
    // Parsing
    // ---------------------------------------------------------------
    private fun parse(rrule: String): ParsedRule? {
        val map = rrule.split(";").filter { it.contains("=") }.associate {
            val kv = it.split("=", limit = 2)
            kv[0].trim().uppercase() to kv[1].trim()
        }
        val freq = when (map["FREQ"]?.uppercase()) {
            "MINUTELY" -> Freq.MINUTELY
            "HOURLY" -> Freq.HOURLY
            "DAILY" -> Freq.DAILY
            "WEEKLY" -> Freq.WEEKLY
            "MONTHLY" -> Freq.MONTHLY
            "YEARLY" -> Freq.YEARLY
            else -> return null
        }
        val interval = map["INTERVAL"]?.toIntOrNull()?.coerceAtLeast(1) ?: 1
        val byDay = map["BYDAY"]?.split(",")?.mapNotNull { rruleToDayOfWeek(it) } ?: emptyList()
        val byMonthDay = map["BYMONTHDAY"]?.split(",")?.mapNotNull { it.toIntOrNull() } ?: emptyList()
        val bySetPos = map["BYSETPOS"]?.toIntOrNull()
        val count = map["COUNT"]?.toIntOrNull()?.takeIf { it > 0 }
        return ParsedRule(freq, interval, byDay, byMonthDay, bySetPos, count)
    }

    // ---------------------------------------------------------------
    // Helfer
    // ---------------------------------------------------------------
    private fun rruleToDayOfWeek(code: String): DayOfWeek? = when (code.trim().uppercase()) {
        "MO" -> DayOfWeek.MONDAY
        "TU" -> DayOfWeek.TUESDAY
        "WE" -> DayOfWeek.WEDNESDAY
        "TH" -> DayOfWeek.THURSDAY
        "FR" -> DayOfWeek.FRIDAY
        "SA" -> DayOfWeek.SATURDAY
        "SU" -> DayOfWeek.SUNDAY
        else -> null
    }

    /** Montag der Kalenderwoche, in der [date] liegt (RFC-Default WKST=MO). */
    private fun mondayOfWeek(date: LocalDate): LocalDate =
        date.plus(-isoOffset(date.dayOfWeek), DateTimeUnit.DAY)

    /** ISO-Offset: Montag=0 … Sonntag=6 (kotlinx DayOfWeek: MONDAY=ordinal 0). */
    private fun isoOffset(dow: DayOfWeek): Int = dow.ordinal

    private fun firstOfMonth(date: LocalDate): LocalDate = LocalDate(date.year, date.monthNumber, 1)

    private fun daysInMonth(year: Int, month: Int): Int = when (month) {
        1, 3, 5, 7, 8, 10, 12 -> 31
        4, 6, 9, 11 -> 30
        2 -> if (isLeapYear(year)) 29 else 28
        else -> 30
    }

    private fun isLeapYear(year: Int): Boolean =
        (year % 4 == 0 && year % 100 != 0) || year % 400 == 0
}
