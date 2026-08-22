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

/** Aktuelle Zeit als Epoch-Millisekunden (entspricht System.currentTimeMillis()). */
internal fun nowMs(): Long = Clock.System.now().toEpochMilliseconds()

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
