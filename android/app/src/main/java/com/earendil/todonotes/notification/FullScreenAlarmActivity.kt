package com.earendil.todonotes.notification

import android.os.Build
import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Alarm
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.app.NotificationManagerCompat
import com.earendil.todonotes.data.TodoNotesDatabase
import com.earendil.todonotes.ui.theme.TodoNotesTheme
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/**
 * Diese Activity erscheint über dem Sperrbildschirm, wenn der Fullscreen-Intent feuert.
 * Wichtig: sie bleibt ÜBER dem Keyguard liegen und drängt den Nutzer NICHT zum Entschlüsseln.
 * Erst beim Tap auf eine Aktion wird die Activity beendet und der Sperrbildschirm ist wieder da.
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
                                val dao = TodoNotesDatabase.get(this@FullScreenAlarmActivity).todoDao()
                                val now = System.currentTimeMillis()
                                val todo = dao.getById(todoId)
                                if (todo != null) {
                                    AlarmScheduler.cancelAlarm(this@FullScreenAlarmActivity, todoId, todo.dueAt ?: now)
                                    if (todo.logToHistory) dao.markCompleted(todoId, now)
                                    else dao.softDelete(todoId, now)
                                }
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
