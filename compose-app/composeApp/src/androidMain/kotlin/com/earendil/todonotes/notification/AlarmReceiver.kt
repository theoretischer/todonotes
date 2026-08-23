package com.earendil.todonotes.notification

import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.earendil.todonotes.ContainerAccess
import com.earendil.todonotes.MainActivity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/**
 * Wird vom AlarmManager geweckt (M8, Port aus altem android/-Projekt).
 * Baut die Notification mit Fullscreen-Intent und bietet "Erledigt"
 * als Direkt-Action an.
 *
 * "Erledigt" läuft über TodoRepository.completeTodo → markiert erledigt
 * (oder soft-delete), plant wiederkehrende Folge-Tasks neu und markiert
 * den Sync dirty. Zusätzlich wird direkt ein Sync angestoßen — der
 * Receiver kann laufen, ohne dass die App/MainActivity offen ist
 * (Auto-Sync läuft sonst nur bei geöffneter App).
 */
class AlarmReceiver : BroadcastReceiver() {

    companion object {
        const val CHANNEL_ID = "todo_alarms"
        const val NOTIFICATION_ID = 1001
        const val ACTION_COMPLETE = "com.earendil.todonotes.ACTION_COMPLETE"
        const val EXTRA_TODO_ID = "todo_id"
    }

    override fun onReceive(context: Context, intent: Intent) {
        val action = intent.action
        Log.i("AlarmReceiver", "onReceive action=$action")

        if (action == ACTION_COMPLETE) {
            val todoId = intent.getStringExtra(EXTRA_TODO_ID)
            if (todoId != null) {
                CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
                    val container = ContainerAccess.get(context)
                    container.todoRepository.completeTodo(todoId)
                    // App evtl. nicht offen → Auto-Sync läuft nicht. Direkt
                    // pushen, damit andere Geräte die Erledigung sofort sehen.
                    container.syncManager.sync()
                    Log.i("AlarmReceiver", "Todo $todoId abgehakt via Notification-Action")
                }
                // Notification erst JETZT entfernen — sie bleibt bis zur
                // Erledigung im Benachrichtigungsmenü liegen (ongoing).
                NotificationManagerCompat.from(context).cancel(NOTIFICATION_ID)
                return
            }
        }

        if (action != AndroidAlarmScheduler.ACTION_ALARM_FIRE) return

        val title = intent.getStringExtra(AndroidAlarmScheduler.EXTRA_TITLE) ?: "Aufgabe"
        val body = intent.getStringExtra(AndroidAlarmScheduler.EXTRA_BODY) ?: ""
        val todoId = intent.getStringExtra(AndroidAlarmScheduler.EXTRA_TODO_ID) ?: ""
        Log.i("AlarmReceiver", "Alarm gefeuert: todo=$todoId title=$title")

        NotificationHelper.ensureChannel(context)

        // Fullscreen-Intent → FullScreenAlarmActivity
        val fullScreenIntent = Intent(context, FullScreenAlarmActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK
            putExtra("title", title)
            putExtra("body", body)
            putExtra(EXTRA_TODO_ID, todoId)
        }
        val fullScreenPending = PendingIntent.getActivity(
            context, todoId.hashCode(), fullScreenIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or
                    if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.S)
                        PendingIntent.FLAG_IMMUTABLE else 0
        )

        // "Erledigt"-Action direkt in der Notification
        val completeIntent = Intent(context, AlarmReceiver::class.java).apply {
            this.action = ACTION_COMPLETE
            putExtra(EXTRA_TODO_ID, todoId)
        }
        val completePending = PendingIntent.getBroadcast(
            context, todoId.hashCode() xor 0x1000, completeIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or
                    if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.S)
                        PendingIntent.FLAG_IMMUTABLE else 0
        )

        // Tap auf Notification (ohne Fullscreen) → App öffnen
        val openIntent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val openPending = PendingIntent.getActivity(
            context, 0, openIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or
                    if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.S)
                        PendingIntent.FLAG_IMMUTABLE else 0
        )

        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentTitle(title)
            .setContentText(body)
            .setPriority(NotificationCompat.PRIORITY_MAX)
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setFullScreenIntent(fullScreenPending, true)
            .setContentIntent(openPending)
            .addAction(android.R.drawable.checkbox_on_background, "Erledigt", completePending)
            // Ongoing: bleibt im Benachrichtigungsmenü, bis die Aufgabe
            // abgeschlossen ist ("Erledigt") — nicht wegswipbar.
            .setOngoing(true)
            .setAutoCancel(false)
            .build()

        try {
            NotificationManagerCompat.from(context).notify(NOTIFICATION_ID, notification)
            Log.i("AlarmReceiver", "Notification gepostet (fullscreenIntent gesetzt)")
        } catch (se: SecurityException) {
            Log.e("AlarmReceiver", "POST_NOTIFICATIONS fehlt", se)
        }
    }
}
