package com.earendil.todonotes.notification

/**
 * Noop-AlarmScheduler für Wasm (keine nativen Alarme im Browser).
 * Später evtl. Browser-Notifications (M9).
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
