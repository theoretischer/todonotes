package com.earendil.todonotes.data.repo

import kotlinx.datetime.Clock
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.LocalTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toInstant
import kotlinx.datetime.toLocalDateTime
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

/**
 * Gemeinsame Zeit- + ID-Helfer für Repositories (M7a — commonMain).
 * Ersetzen System.currentTimeMillis(), java.text.SimpleDateFormat, java.util.UUID.
 */

/** Offset zwischen Client-Uhr und Server-Uhr (ms). Wird von SyncManager
 *  nach jedem Sync aktualisiert: serverTime - clientTime. So ist nowMs()
 *  immer ≈ Server-Zeit → LWW-Check funktioniert auch bei Clock-Skew. */
internal var serverTimeOffset: Long = 0

/** Aktuelle Zeit als Epoch-Millisekunden (Server-adjustiert via serverTimeOffset).
 *  Entspricht System.currentTimeMillis() + serverTimeOffset. */
internal fun nowMs(): Long = Clock.System.now().toEpochMilliseconds() + serverTimeOffset

/** Neue zufällige UUID als String (zentrale @OptIn-Stelle für ExperimentalUuidApi). */
@OptIn(ExperimentalUuidApi::class)
internal fun randomUuidString(): String = Uuid.random().toString()

/**
 * Formatiert ein Datum als "dd.MM.yyyy" in der lokalen System-TimeZone.
 * Ersatz für java.text.SimpleDateFormat("dd.MM.yyyy", Locale.GERMAN).
 */
internal fun formatDateGerman(millis: Long): String {
    val ldt = kotlinx.datetime.Instant.fromEpochMilliseconds(millis)
        .toLocalDateTime(TimeZone.currentSystemDefault())
    val d = ldt.dayOfMonth.toString().padStart(2, '0')
    val m = ldt.monthNumber.toString().padStart(2, '0')
    return "$d.$m.${ldt.year}"
}

/** Default-Titel: "Neue Notiz vom dd.MM.yyyy". */
internal fun defaultNoteTitle(): String = "Neue Notiz vom ${formatDateGerman(nowMs())}"

/** Default-Titel: "Neuer Chat vom dd.MM.yyyy". */
internal fun defaultChatTitle(): String = "Neuer Chat vom ${formatDateGerman(nowMs())}"

/** Formatiert eine Uhrzeit als "HH:mm" in der lokalen System-TimeZone.
 *  Ersatz für SimpleDateFormat("HH:mm", Locale.GERMAN). */
internal fun formatTimeGerman(millis: Long): String {
    val ldt = kotlinx.datetime.Instant.fromEpochMilliseconds(millis)
        .toLocalDateTime(TimeZone.currentSystemDefault())
    val h = ldt.hour.toString().padStart(2, '0')
    val min = ldt.minute.toString().padStart(2, '0')
    return "$h:$min"
}

/** Liefert (dayOfYear, year) für einen Zeitstempel — für Tageswechsel-
 *  Erkennung in der Chat-Liste. Ersatz für Calendar.get(DAY_OF_YEAR)/YEAR. */
internal fun dayOfYearAndYear(millis: Long): Pair<Int, Int> {
    val ldt = kotlinx.datetime.Instant.fromEpochMilliseconds(millis)
        .toLocalDateTime(TimeZone.currentSystemDefault())
    return ldt.dayOfYear to ldt.year
}
