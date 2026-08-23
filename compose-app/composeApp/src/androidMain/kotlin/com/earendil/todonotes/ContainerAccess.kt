package com.earendil.todonotes

import android.content.Context
import com.earendil.todonotes.data.setAppContext
import com.earendil.todonotes.notification.AndroidAlarmScheduler

/**
 * Globaler Zugriff auf den [AppContainer] (M8).
 *
 * Nötig für Komponenten, die ohne MainActivity laufen können:
 * [com.earendil.todonotes.notification.AlarmReceiver] und
 * [com.earendil.todonotes.notification.FullScreenAlarmActivity] —
 * der AlarmManager kann den App-Prozess starten und den Receiver
 * feuern, ohne dass MainActivity jemals onCreate durchlief.
 *
 * Lazy-Singleton: Der erste Aufrufer (meist MainActivity) erstellt
 * den Container; alle weiteren bekommen dieselbe Instanz (eine DB,
 * ein SyncManager, keine Duplikate).
 */
object ContainerAccess {

    @Volatile
    private var container: AppContainer? = null

    fun get(context: Context): AppContainer {
        container?.let { return it }
        synchronized(this) {
            container?.let { return it }
            // DB-Builder braucht den App-Context (getDatabasePath).
            setAppContext(context.applicationContext)
            val c = AppContainer(alarmScheduler = AndroidAlarmScheduler(context.applicationContext))
            container = c
            return c
        }
    }
}
