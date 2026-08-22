package com.earendil.todonotes.notification

import android.content.Context
import android.util.Log

/**
 * Android-AlarmScheduler (M7a: vorerst Noop, M8 macht echte Implementierung
 * mit AlarmManager + NotificationHelper aus android/app/src/main/.../notification/).
 *
 * Dieser Placeholder existiert, damit der Service-Locator auf Android dieselbe
 * Form hat wie auf Desktop/Wasm. In M8 wird er durch die echte Implementierung
 * ersetzt (dann mit Context-Übergabe via Factory).
 */
class AndroidAlarmScheduler(private val context: Context) : AlarmScheduler {
    override fun scheduleAlarm(dueAt: Long, todoId: String, title: String, notes: String?) {
        Log.i("AlarmScheduler", "M7a noop: schedule $todoId at $dueAt (M8 macht echte Alarme)")
    }
    override fun cancelAlarm(todoId: String, dueAt: Long) {
        Log.i("AlarmScheduler", "M7a noop: cancel $todoId (M8 macht echte Alarme)")
    }
}
