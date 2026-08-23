package com.earendil.todonotes.data.entity

/**
 * Benachrichtigungs-Stil für ein zeitgesteuertes Todo (M8).
 *
 * - FULLSCREEN: Vollbild-Alarm über dem Sperrbildschirm + Notification (Standard)
 * - NOTIFICATION: nur Benachrichtigung im Benachrichtigungsmenü (kein Vollbild)
 * - SILENT: keine Benachrichtigung (Alarm feuert intern, aber nichts sichtbares)
 *
 * Gespeichert als Int im Todo (Room + Sync), hier als Enum für typsichere UI.
 */
enum class NotificationStyle(val value: Int) {
    FULLSCREEN(0),
    NOTIFICATION(1),
    SILENT(2);

    companion object {
        fun fromValue(value: Int): NotificationStyle =
            entries.firstOrNull { it.value == value } ?: FULLSCREEN
    }
}
