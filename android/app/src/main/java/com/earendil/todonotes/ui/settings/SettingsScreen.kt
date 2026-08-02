package com.earendil.todonotes.ui.settings

import android.Manifest
import android.app.AlarmManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.PowerManager
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Cloud
import androidx.compose.material.icons.filled.DarkMode
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.PowerSettingsNew
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.earendil.todonotes.data.sync.SyncWorker
import com.earendil.todonotes.ui.SyncViewModel
import java.text.DateFormat
import java.util.Date

/** Einstellungen-Screen mit Sektionen: Verbindung, Benachrichtigungen,
 *  Erscheinungsbild, Info. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    syncVm: SyncViewModel,
    onBack: () -> Unit
) {
    val context = LocalContext.current

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Einstellungen") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Zurück")
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
        ) {
            SectionVerbindung(syncVm)
            SectionBenachrichtigungen(context)
            SectionErscheinungsbild()
            SectionInfo()
            Spacer(Modifier.height(32.dp))
        }
    }
}

// ---------- 1. Verbindung ----------

@Composable
private fun SectionVerbindung(syncVm: SyncViewModel) {
    val context = LocalContext.current
    val serverUrl by syncVm.serverUrl.collectAsState()
    val token by syncVm.token.collectAsState()
    val isSyncing by syncVm.isSyncing.collectAsState()
    val lastResult by syncVm.lastResult.collectAsState()
    val lastSyncAt by syncVm.lastSyncAt.collectAsState()

    // WorkManager periodischen Sync starten, sobald konfiguriert.
    LaunchedEffect(syncVm.isConfigured) {
        if (syncVm.isConfigured) {
            SyncWorker.enqueuePeriodic(context)
        }
    }

    SectionHeader(icon = Icons.Filled.Cloud, title = "Verbindung")

    var urlDraft by remember(serverUrl) { mutableStateOf(serverUrl) }
    var tokenDraft by remember(token) { mutableStateOf(token) }
    var tokenVisible by remember { mutableStateOf(false) }

    OutlinedTextField(
        value = urlDraft,
        onValueChange = { urlDraft = it },
        label = { Text("Server-URL") },
        placeholder = { Text("https://todo.christopherh.de") },
        singleLine = true,
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp)
    )
    OutlinedTextField(
        value = tokenDraft,
        onValueChange = { tokenDraft = it },
        label = { Text("Sync-Token") },
        singleLine = true,
        visualTransformation = if (tokenVisible) VisualTransformation.None else PasswordVisualTransformation(),
        trailingIcon = {
            TextButton(onClick = { tokenVisible = !tokenVisible }) {
                Text(if (tokenVisible) "Verbergen" else "Zeigen")
            }
        },
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp)
    )
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Button(
            onClick = {
                syncVm.setServerUrl(urlDraft)
                syncVm.setToken(tokenDraft)
            },
            modifier = Modifier.weight(1f)
        ) { Text("Speichern") }
        OutlinedButton(
            onClick = { syncVm.testConnection() },
            enabled = !isSyncing && urlDraft.isNotBlank(),
            modifier = Modifier.weight(1f)
        ) { Text("Testen") }
    }
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Button(
            onClick = { syncVm.syncNow() },
            enabled = !isSyncing && syncVm.isConfigured,
            modifier = Modifier.weight(1f)
        ) {
            Icon(Icons.Filled.Sync, contentDescription = null, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(6.dp))
            Text(if (isSyncing) "Synchronisiere…" else "Jetzt synchronisieren")
        }
    }
    if (lastResult.isNotEmpty()) {
        Text(
            text = "Status: $lastResult" +
                if (lastSyncAt > 0) "  ·  zuletzt: " +
                    DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.SHORT)
                        .format(Date(lastSyncAt)) else "",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)
        )
    }
    HorizontalDivider(modifier = Modifier.padding(vertical = 16.dp))
}

// ---------- 2. Benachrichtigungen ----------

@Composable
private fun SectionBenachrichtigungen(context: Context) {
    SectionHeader(icon = Icons.Filled.Notifications, title = "Benachrichtigungen")

    val granted = remember {
        ContextCompat.checkSelfPermission(
            context, Manifest.permission.POST_NOTIFICATIONS
        ) == PackageManager.PERMISSION_GRANTED
    }
    val launcher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { }

    SettingRow(
        title = "Benachrichtigungen erlauben",
        subtitle = if (granted) "Erteilt" else "Nicht erteilt — Alarme werden nicht angezeigt",
        trailing = {
            if (granted) {
                Icon(Icons.Filled.Check, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
            } else {
                TextButton(onClick = {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                        launcher.launch(Manifest.permission.POST_NOTIFICATIONS)
                    }
                }) { Text("Erlauben") }
            }
        }
    )
    SettingRow(
        icon = Icons.Filled.PowerSettingsNew,
        title = "Akku-Ausnahme",
        subtitle = "Stelle sicher, dass Android die App nicht im Hintergrund beendet (für Alarme/Sync).",
        onClick = {
            // Akku-Optimierung-Einstellungen öffnen (falls nicht ausgenommen).
            val pm = context.getSystemService(Context.POWER_SERVICE) as PowerManager
            if (!pm.isIgnoringBatteryOptimizations(context.packageName)) {
                val intent = Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS).apply {
                    data = Uri.parse("package:${context.packageName}")
                }
                context.startActivity(intent)
            }
        }
    )
    SettingRow(
        icon = Icons.Filled.Build,
        title = "Exakte Alarme",
        subtitle = "Systemeinstellung für zeitgenaue Alarme prüfen.",
        onClick = {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                val am = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
                if (!am.canScheduleExactAlarms()) {
                    context.startActivity(Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM))
                }
            }
        }
    )
    HorizontalDivider(modifier = Modifier.padding(vertical = 16.dp))
}

// ---------- 3. Erscheinungsbild ----------

@Composable
private fun SectionErscheinungsbild() {
    SectionHeader(icon = Icons.Filled.DarkMode, title = "Erscheinungsbild")
    SettingRow(
        title = "Design",
        subtitle = "Folgt Systemsystem (Light/Dark folgt später)",
        trailing = { Text("System", color = MaterialTheme.colorScheme.onSurfaceVariant) }
    )
    HorizontalDivider(modifier = Modifier.padding(vertical = 16.dp))
}

// ---------- 4. Info ----------

@Composable
private fun SectionInfo() {
    SectionHeader(icon = Icons.Filled.Info, title = "Info")
    SettingRow(
        title = "TodoNotes",
        subtitle = "Version 0.6.0  ·  Daten lokal auf Gerät + Sync-Server",
        onClick = null
    )
    SettingRow(
        title = "Datenbank-Backup",
        subtitle = "Bei Schema-Updates wird automatisch ein Backup angelegt (todonotes.db.bak-v…).",
        onClick = null
    )
}

// ---------- Helpers ----------

@Composable
private fun SectionHeader(icon: ImageVector, title: String) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
        Spacer(Modifier.width(12.dp))
        Text(title, style = MaterialTheme.typography.titleMedium)
    }
}

@Composable
private fun SettingRow(
    icon: ImageVector? = null,
    title: String,
    subtitle: String? = null,
    trailing: @Composable (() -> Unit)? = null,
    onClick: (() -> Unit)? = null
) {
    val modifier = Modifier
        .fillMaxWidth()
        .padding(horizontal = 16.dp, vertical = 10.dp)
        .let { if (onClick != null) it.clickable { onClick() } else it }
    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (icon != null) {
            Icon(icon, contentDescription = null, modifier = Modifier.size(24.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.width(16.dp))
        }
        Column(modifier = Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge)
            if (subtitle != null) {
                Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        if (trailing != null) {
            Spacer(Modifier.width(8.dp))
            trailing()
        }
    }
}
