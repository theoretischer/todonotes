package com.earendil.todonotes.notification

/**
 * Plant/cancelt Alarme für fällige Todos (expect/actual, M7a).
 *
 * - Android: echte AlarmManager + Notification (folgt in M8)
 * - Desktop/Wasm: noop (keine nativen Alarme; später ggf. Browser-Notifications)
 *
 * Das Interface lebt in commonMain, damit [TodoRepository] plattformunabhängig
 * damit arbeiten kann. Die Implementierung wird injected (Service-Locator).
 */
interface AlarmScheduler {
    /** Alarm für [dueAt] (millis) planen. */
    fun scheduleAlarm(dueAt: Long, todoId: String, title: String, notes: String?)
    /** Alarm für dieses Todo canceln (falls vorhanden). */
    fun cancelAlarm(todoId: String, dueAt: Long)
}
