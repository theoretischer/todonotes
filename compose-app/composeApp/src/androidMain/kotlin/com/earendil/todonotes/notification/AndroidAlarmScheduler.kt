package com.earendil.todonotes.notification

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log

/**
 * Plant und cancelt exakte Alarme für Todos (M8, Port aus altem
 * android/-Projekt — an commonMain-[AlarmScheduler]-Interface angepasst).
 *
 * Request-Code = todoId.hashCode() macht Alarme pro Todo eindeutig.
 * (Vorher: dueAt/1000 — aber Re-Arm verschiebt das Datum, dann hätte das
 * Cancel den re-ge-armten Alarm nicht mehr gefunden → Geister-Alarme.)
 */
class AndroidAlarmScheduler(private val context: Context) : AlarmScheduler {

    companion object {
        const val ACTION_ALARM_FIRE = "com.earendil.todonotes.ALARM_FIRE"
        const val EXTRA_TODO_ID = "todo_id"
        const val EXTRA_TITLE = "title"
        const val EXTRA_BODY = "body"
        const val EXTRA_TRIGGER_AT = "trigger_at"
        const val EXTRA_STYLE = "notification_style"

        /** Request-Code → deterministisch aus todoId (per Todo eindeutig). */
        internal fun requestCode(todoId: String): Int = todoId.hashCode() and Int.MAX_VALUE
    }

    override fun scheduleAlarm(
        dueAt: Long,
        todoId: String,
        title: String,
        notes: String?,
        notificationStyle: Int
    ) {
        val intent = Intent(context, AlarmReceiver::class.java).apply {
            action = ACTION_ALARM_FIRE
            putExtra(EXTRA_TODO_ID, todoId)
            putExtra(EXTRA_TITLE, title)
            putExtra(EXTRA_BODY, notes ?: "")
            putExtra(EXTRA_TRIGGER_AT, dueAt)
            putExtra(EXTRA_STYLE, notificationStyle)
        }

        val requestCode = requestCode(todoId)
        val pendingIntent = PendingIntent.getBroadcast(context, requestCode, intent, pendingIntentFlags())

        val alarmManager = context.getSystemService(AlarmManager::class.java)
        try {
            alarmManager.setExactAndAllowWhileIdle(
                AlarmManager.RTC_WAKEUP,
                dueAt,
                pendingIntent
            )
            Log.i("AlarmScheduler", "Alarm geplant für todo=$todoId um $dueAt (title=$title)")
        } catch (se: SecurityException) {
            Log.e("AlarmScheduler", "Keine Berechtigung für exakte Alarme!", se)
        }
    }

    override fun cancelAlarm(todoId: String, dueAt: Long) {
        val requestCode = requestCode(todoId)
        val intent = Intent(context, AlarmReceiver::class.java).apply {
            action = ACTION_ALARM_FIRE
        }
        val pendingIntent = PendingIntent.getBroadcast(
            context, requestCode, intent,
            pendingIntentFlags() or PendingIntent.FLAG_NO_CREATE
        )
        if (pendingIntent != null) {
            context.getSystemService(AlarmManager::class.java).cancel(pendingIntent)
            Log.i("AlarmScheduler", "Alarm gecancelt für todo=$todoId")
        }
    }

    private fun pendingIntentFlags(): Int =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S)
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        else
            PendingIntent.FLAG_UPDATE_CURRENT
}
