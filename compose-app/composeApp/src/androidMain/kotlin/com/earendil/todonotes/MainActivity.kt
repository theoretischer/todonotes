package com.earendil.todonotes

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import com.earendil.todonotes.data.setAppContext

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        // Context für Room-Builder bereitstellen (M4).
        setAppContext(applicationContext)
        // AppContainer über ContainerAccess: Lazy-Singleton — auch
        // AlarmReceiver/FullScreenAlarmActivity (M8) greifen darüber zu,
        // selbst wenn der AlarmManager die App ohne MainActivity startet.
        val container = ContainerAccess.get(this)
        // Sync beim App-Resume: ON_RESUME → onAppResume feuern → SyncManager
        // pullt Änderungen vom anderen Gerät.
        lifecycle.addObserver(object : androidx.lifecycle.LifecycleEventObserver {
            override fun onStateChanged(
                source: androidx.lifecycle.LifecycleOwner,
                event: androidx.lifecycle.Lifecycle.Event
            ) {
                if (event == androidx.lifecycle.Lifecycle.Event.ON_RESUME) {
                    container.onAppResume?.invoke()
                }
            }
        })
        setContent {
            App(container)
        }
        // M8: Notification-Permission anfragen (ab Android 13 nötig, um
        // Alarm-Notifications zu sehen — der Alarm feuert auch ohne,
        // aber die Notification wird dann nicht angezeigt).
        val requestNotificationPermission = registerForActivityResult(
            ActivityResultContracts.RequestPermission()
        ) { }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS)
            != PackageManager.PERMISSION_GRANTED
        ) {
            requestNotificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }
}
