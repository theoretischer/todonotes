package com.earendil.todonotes.notification

import android.os.Build
import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Alarm
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.app.NotificationManagerCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
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
 * Wichtig:
 * - Bleibt ÜBER dem Keyguard und drängt den Nutzer NICHT zum Entschlüsseln.
 *   Erst beim Tap auf eine Aktion wird die Activity beendet und der
 *   Sperrbildschirm ist wieder da.
 * - System-Bars (Status/Navigation) werden komplett versteckt — der Alarm
 *   soll den ganzen Bildschirm füllen.
 * - Die Notification bleibt IMMER im Benachrichtigungsmenü liegen (bis
 *   "Erledigt") — das Vollbild wird nur hier nicht gecancelt.
 *
 * "Erledigt" läuft über TodoRepository.completeTodo (erledigt markieren
 * oder soft-delete, wiederkehrende Tasks neu planen, Sync dirty) + direkter
 * Sync-Push — die Activity kann ohne geöffnete App laufen.
 */
class FullScreenAlarmActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

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

        // System-Bars verstecken (Statusleiste, Navigationsleiste, Zurück-Geste-
        // Leiste) — der Alarm füllt den ganzen Bildschirm.
        WindowCompat.setDecorFitsSystemWindows(window, false)
        val controller = WindowInsetsControllerCompat(window, window.decorView)
        controller.hide(WindowInsetsCompat.Type.systemBars())
        controller.systemBarsBehavior =
            WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE

        val title = intent.getStringExtra("title") ?: "Aufgabe"
        val body = intent.getStringExtra("body") ?: ""
        val todoId = intent.getStringExtra(AlarmReceiver.EXTRA_TODO_ID) ?: ""

        setContent {
            TodoNotesTheme {
                FullScreenAlarmContent(
                    title = title,
                    body = body,
                    onDone = {
                        // Notification aus dem Benachrichtigungsmenü entfernen —
                        // erst jetzt ist die Aufgabe wirklich abgeschlossen.
                        NotificationManagerCompat.from(this).cancel(AlarmReceiver.NOTIFICATION_ID)
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
    val colors = MaterialTheme.colorScheme
    Surface(
        modifier = Modifier.fillMaxSize(),
        color = colors.background
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        listOf(
                            colors.primary.copy(alpha = 0.15f),
                            colors.background
                        )
                    )
                )
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 28.dp, vertical = 48.dp),
                verticalArrangement = Arrangement.SpaceBetween,
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // Oberer Bereich: Alarm-Icon + Titel
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(88.dp)
                            .clip(CircleShape)
                            .background(colors.primary.copy(alpha = 0.18f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            Icons.Filled.Alarm,
                            contentDescription = null,
                            tint = colors.primary,
                            modifier = Modifier.size(44.dp)
                        )
                    }
                    Text(
                        text = "Aufgabe fällig",
                        color = colors.primary,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Medium
                    )
                    Text(
                        text = title,
                        color = colors.onBackground,
                        fontSize = 28.sp,
                        fontWeight = FontWeight.Bold,
                        textAlign = TextAlign.Center
                    )
                    if (body.isNotBlank()) {
                        Text(
                            text = body,
                            color = colors.onBackground.copy(alpha = 0.75f),
                            fontSize = 18.sp,
                            textAlign = TextAlign.Center
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
                        modifier = Modifier.fillMaxWidth().height(64.dp)
                    ) {
                        Icon(Icons.Filled.Check, contentDescription = null)
                        Spacer(Modifier.width(8.dp))
                        Text("Erledigt", fontSize = 18.sp, fontWeight = FontWeight.SemiBold)
                    }
                    OutlinedButton(
                        onClick = onSnooze,
                        modifier = Modifier.fillMaxWidth().height(56.dp)
                    ) {
                        Icon(Icons.Filled.Close, contentDescription = null)
                        Spacer(Modifier.width(8.dp))
                        Text("Schließen", fontSize = 16.sp)
                    }
                }
            }
        }
    }
}
