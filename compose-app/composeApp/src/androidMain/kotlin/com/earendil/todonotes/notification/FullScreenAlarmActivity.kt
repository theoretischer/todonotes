package com.earendil.todonotes.notification

import android.os.Build
import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Alarm
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.app.NotificationManagerCompat
import com.earendil.todonotes.ContainerAccess
import com.earendil.todonotes.ui.theme.TodoNotesTheme
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/**
 * Erscheint über dem Sperrbildschirm, wenn der Fullscreen-Intent feuert
 * (M8, Port aus altem android/-Projekt).
 *
 * Wichtig: bleibt ÜBER dem Keyguard und drängt den Nutzer NICHT zum
 * Entschlüsseln. Erst beim Tap auf eine Aktion wird die Activity beendet
 * und der Sperrbildschirm ist wieder da.
 *
 * "Erledigt" läuft über TodoRepository.completeTodo (erledigt markieren
 * oder soft-delete, wiederkehrende Tasks neu planen, Sync dirty) + direkter
 * Sync-Push — die Activity kann ohne geöffnete App laufen.
 */
class FullScreenAlarmActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Notification canceln, da der Nutzer die Meldung ja jetzt sieht
        NotificationManagerCompat.from(this).cancel(AlarmReceiver.NOTIFICATION_ID)

        // Über dem Sperrbildschirm anzeigen, ohne den Nutzer zum Entschlüsseln zu zwingen.
        // NICHT requestDismissKeyguard aufrufen – das würde den Login-Screen aufdrängen.
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
            setShowWhenLocked(true)
            setTurnScreenOn(true)
        } else {
            @Suppress("DEPRECATION")
            window.addFlags(
                WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED or
                        WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON
            )
        }

        // Bildschirm anlassen, damit die Meldung lesbar bleibt
        window.addFlags(
            WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON
        )

        val title = intent.getStringExtra("title") ?: "Aufgabe"
        val body = intent.getStringExtra("body") ?: ""
        val todoId = intent.getStringExtra(AlarmReceiver.EXTRA_TODO_ID) ?: ""

        setContent {
            TodoNotesTheme {
                FullScreenAlarmContent(
                    title = title,
                    body = body,
                    onDone = {
                        if (todoId.isNotEmpty()) {
                            CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
                                val container = ContainerAccess.get(this@FullScreenAlarmActivity)
                                container.todoRepository.completeTodo(todoId)
                                // App evtl. nicht offen → Auto-Sync läuft nicht.
                                container.syncManager.sync()
                            }
                        }
                        finish()
                    },
                    onSnooze = { finish() }
                )
            }
        }
    }
}

@Composable
private fun FullScreenAlarmContent(
    title: String,
    body: String,
    onDone: () -> Unit,
    onSnooze: () -> Unit
) {
    Surface(
        modifier = Modifier.fillMaxSize(),
        color = Color.Black
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(24.dp),
            verticalArrangement = Arrangement.SpaceBetween,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Oberer Bereich: Alarm-Icon + Titel
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Icon(
                    Icons.Default.Alarm,
                    contentDescription = null,
                    tint = Color.White,
                    modifier = Modifier.size(56.dp)
                )
                Text(
                    text = "Aufgabe fällig",
                    color = Color.White.copy(alpha = 0.7f),
                    fontSize = 16.sp
                )
                Text(
                    text = title,
                    color = Color.White,
                    fontSize = 28.sp,
                    fontWeight = FontWeight.Bold
                )
                if (body.isNotBlank()) {
                    Text(
                        text = body,
                        color = Color.White.copy(alpha = 0.85f),
                        fontSize = 18.sp
                    )
                }
            }

            // Buttons unten
            Column(
                verticalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Button(
                    onClick = onDone,
                    modifier = Modifier.fillMaxWidth().height(64.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF22C55E))
                ) {
                    Icon(Icons.Default.Check, contentDescription = null, tint = Color.White)
                    Spacer(Modifier.width(8.dp))
                    Text("Erledigt", color = Color.White, fontSize = 18.sp, fontWeight = FontWeight.SemiBold)
                }
                OutlinedButton(
                    onClick = onSnooze,
                    modifier = Modifier.fillMaxWidth().height(56.dp),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = Color.White)
                ) {
                    Icon(Icons.Default.Close, contentDescription = null)
                    Spacer(Modifier.width(8.dp))
                    Text("Schließen")
                }
            }
        }
    }
}
