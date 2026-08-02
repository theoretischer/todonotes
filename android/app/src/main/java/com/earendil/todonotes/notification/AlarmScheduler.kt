package com.earendil.todonotes.notification

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log

/**
 * Plant und cancelt exakte Alarme für Todos.
 *
 * Request-Code = (dueAt/1000).toInt() macht Alarme eindeutig. Für Cancel nutzen wir
 * zusätzlich die todoId im Intent (Matcher über action+data), damit wir den richtigen finden.
 */
object AlarmScheduler {

    private const val TAG = "AlarmScheduler"
    const val ACTION_ALARM_FIRE = "com.earendil.todonotes.ALARM_FIRE"
    const val EXTRA_TODO_ID = "todo_id"
    const val EXTRA_TITLE = "title"
    const val EXTRA_BODY = "body"
    const val EXTRA_TRIGGER_AT = "trigger_at"

    fun scheduleAlarm(
        context: Context,
        triggerAtMillis: Long,
        todoId: String,
        title: String,
        body: String
    ) {
        val intent = Intent(context, AlarmReceiver::class.java).apply {
            action = ACTION_ALARM_FIRE
            putExtra(EXTRA_TODO_ID, todoId)
            putExtra(EXTRA_TITLE, title)
            putExtra(EXTRA_BODY, body)
            putExtra(EXTRA_TRIGGER_AT, triggerAtMillis)
        }

        val requestCode = (triggerAtMillis / 1000).toInt()
        val flags = pendingIntentFlags()
        val pendingIntent = PendingIntent.getBroadcast(context, requestCode, intent, flags)

        val alarmManager = context.getSystemService(AlarmManager::class.java)
        try {
            alarmManager.setExactAndAllowWhileIdle(
                AlarmManager.RTC_WAKEUP,
                triggerAtMillis,
                pendingIntent
            )
            Log.i(TAG, "Alarm geplant für todo=$todoId um $triggerAtMillis (title=$title)")
        } catch (se: SecurityException) {
            Log.e(TAG, "Keine Berechtigung für exakte Alarme!", se)
        }
    }

    /**
     * Cancelt den Alarm. Wir kennen den originalen triggerAtMillis und leiten
     * damit den Request-Code ab. Wenn unbekannt, versuchen wir es trotzdem mit 0.
     */
    fun cancelAlarm(context: Context, todoId: String, triggerAtMillis: Long) {
        val requestCode = (triggerAtMillis / 1000).toInt()
        val intent = Intent(context, AlarmReceiver::class.java).apply {
            action = ACTION_ALARM_FIRE
        }
        val pendingIntent = PendingIntent.getBroadcast(
            context, requestCode, intent,
            pendingIntentFlags() or PendingIntent.FLAG_NO_CREATE
        )
        if (pendingIntent != null) {
            context.getSystemService(AlarmManager::class.java).cancel(pendingIntent)
            Log.i(TAG, "Alarm gecancelt für todo=$todoId")
        }
    }

    private fun pendingIntentFlags(): Int =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S)
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        else PendingIntent.FLAG_UPDATE_CURRENT
}
