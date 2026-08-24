package com.earendil.todonotes.ui.components

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CloudOff
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.earendil.todonotes.data.sync.SyncManager
import kotlinx.coroutines.delay

/**
 * Sync-Indikator oben rechts in Editor/Chat: Save-Icon + Sekunden-Anzeige
 * ("vor 12s"). So sieht man, ob der Sync lebt — und kann bei Bedarf ein
 * Backup machen, wenn der Sync seit Minuten hängt.
 *
 * Zustände:
 * - syncing: Sync läuft gerade (rotierendes Sync-Icon)
 * - hasError: letzter Sync fehlgeschlagen (rotes CloudOff-Icon)
 * - pending: lokale Änderungen noch nicht gepusht (Save-Icon,Akzentfarbe)
 * - ok: alles gespeichert (Save-Icon, dezent + "vor Xs")
 */
@Composable
fun SyncIndicator(
    syncState: SyncManager.SyncState,
    modifier: Modifier = Modifier
) {
    // Tick jede Sekunde, damit die "vor Xs"-Anzeige aktuell bleibt.
    var now by remember { mutableLongStateOf(kotlinx.datetime.Clock.System.now().toEpochMilliseconds()) }
    LaunchedEffect(Unit) {
        while (true) {
            delay(1000)
            now = kotlinx.datetime.Clock.System.now().toEpochMilliseconds()
        }
    }

    val (icon, tint, label) = when {
        syncState.syncing -> Triple(
            Icons.Filled.Sync,
            MaterialTheme.colorScheme.primary,
            "läuft…"
        )
        syncState.hasError -> Triple(
            Icons.Filled.CloudOff,
            MaterialTheme.colorScheme.error,
            "Fehler"
        )
        syncState.pending -> Triple(
            Icons.Filled.Save,
            MaterialTheme.colorScheme.primary,
            "ausstehend"
        )
        syncState.lastSyncAt == 0L -> Triple(
            Icons.Filled.Save,
            MaterialTheme.colorScheme.onSurfaceVariant,
            "—"
        )
        else -> {
            val secs = ((now - syncState.lastSyncAt) / 1000).coerceAtLeast(0)
            val text = when {
                secs < 60 -> "vor ${secs}s"
                secs < 3600 -> "vor ${secs / 60}min"
                else -> "vor ${secs / 3600}h"
            }
            Triple(
                Icons.Filled.Save,
                MaterialTheme.colorScheme.onSurfaceVariant,
                text
            )
        }
    }

    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (syncState.syncing) {
            CircularProgressIndicator(
                modifier = Modifier.size(14.dp),
                strokeWidth = 2.dp,
                color = tint
            )
        } else {
            Icon(
                icon,
                contentDescription = "Sync: $label",
                modifier = Modifier.size(16.dp),
                tint = tint
            )
        }
        Spacer(Modifier.width(4.dp))
        Text(
            label,
            style = MaterialTheme.typography.labelSmall,
            color = tint
        )
    }
}
