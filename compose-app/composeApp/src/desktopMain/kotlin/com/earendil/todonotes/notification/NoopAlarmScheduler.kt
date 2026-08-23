package com.earendil.todonotes.notification

/**
 * Noop-AlarmScheduler für Plattformen ohne native Alarme (Desktop, Wasm).
 * Alle Aufrufe sind keine Operationen. In M8 bekommt Android eine echte
 * Implementierung (AlarmManager + NotificationHelper).
 */
class NoopAlarmScheduler : AlarmScheduler {
    override fun scheduleAlarm(
        dueAt: Long,
        todoId: String,
        title: String,
        notes: String?,
        notificationStyle: Int
    ) {
        // noop
    }
    override fun cancelAlarm(todoId: String, dueAt: Long) {
        // noop
    }
}
