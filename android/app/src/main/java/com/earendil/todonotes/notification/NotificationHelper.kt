package com.earendil.todonotes.notification

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.os.Build

object NotificationHelper {

    const val CHANNEL_ID = "todo_alarms"

    fun ensureChannel(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Aufgaben-Erinnerungen",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Weckt dich zu fälligen Todos über den Sperrbildschirm"
                enableVibration(true)
                enableLights(true)
                lockscreenVisibility = NotificationManager.IMPORTANCE_HIGH
                // Wichtig: Category Alarm, damit Samsung/One UI sie nicht dämpft
                setBypassDnd(false)
            }
            val manager = context.getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(channel)
        }
    }
}
