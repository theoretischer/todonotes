package com.earendil.todonotes.ui.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.earendil.todonotes.ui.SyncViewModel

/** Profil-BottomSheet: öffnet beim Tap aufs Profil-Icon oben rechts.
 *  Zeigt Avatar + Namen + Schnellzugriff auf Einstellungen. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProfileSheet(
    syncVm: SyncViewModel,
    onOpenSettings: () -> Unit,
    onDismiss: () -> Unit
) {
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp)
                .padding(bottom = 32.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Avatar (Initialen-Placeholder)
            Box(
                modifier = Modifier
                    .size(72.dp)
                    .background(MaterialTheme.colorScheme.primaryContainer, CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    Icons.Filled.Person,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onPrimaryContainer,
                    modifier = Modifier.size(40.dp)
                )
            }
            Spacer(Modifier.height(12.dp))
            Text("Nutzer", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Medium)
            Text(
                "TodoNotes",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.height(20.dp))

            // Sync-Status-Kurzinfo
            val status = if (syncVm.isConfigured) "Verbunden" else "Nicht eingerichtet"
            Text(
                "Sync: $status",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.height(16.dp))

            // Button: Einstellungen öffnen
            FilledTonalButton(
                onClick = {
                    onDismiss()
                    onOpenSettings()
                },
                modifier = Modifier.fillMaxWidth()
            ) {
                Icon(Icons.Filled.Settings, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text("Einstellungen")
            }
        }
    }
}
