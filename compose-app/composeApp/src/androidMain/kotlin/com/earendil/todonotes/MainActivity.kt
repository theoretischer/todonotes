package com.earendil.todonotes

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.earendil.todonotes.data.setAppContext
import com.earendil.todonotes.notification.AndroidAlarmScheduler

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        // Context für Room-Builder bereitstellen (M4).
        setAppContext(applicationContext)
        // AppContainer erstellen: DB + Repos + Sync-Stack + AlarmScheduler.
        val container = AppContainer(alarmScheduler = AndroidAlarmScheduler(applicationContext))
        setContent {
            App(container)
        }
    }
}
