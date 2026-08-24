package com.earendil.todonotes.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.union
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AdminPanelSettings
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material.icons.filled.Upload
import androidx.compose.material.icons.outlined.Key
import androidx.compose.material.icons.outlined.Logout
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.unit.dp
import com.earendil.todonotes.data.sync.AdminUserResponse
import com.earendil.todonotes.data.sync.UserProfileResponse
import com.earendil.todonotes.rememberImagePicker
import com.earendil.todonotes.ui.AuthViewModel
import com.earendil.todonotes.ui.components.AvatarImage
import com.earendil.todonotes.ui.components.rememberImageBitmap

/**
 * Settings-Overlay: Profil + Admin-Sektion (M7d-3 Teil 2).
 *
 * Wird als Fullscreen-Overlay über dem Haupt-Scaffold gezeigt.
 */
@Composable
fun SettingsScreen(
    vm: AuthViewModel,
    onBack: () -> Unit,
    onLogout: () -> Unit,
    onWipeComplete: () -> Unit = {}
) {
    val profile by vm.profile.collectAsState()
    val error by vm.error.collectAsState()
    val settings by vm.settings.collectAsState()
    val adminUsers by vm.adminUsers.collectAsState()
    val avatarBytes by vm.avatarBytes.collectAsState()

    Scaffold(
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        topBar = {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .windowInsetsPadding(WindowInsets.statusBars)
                    .padding(start = 8.dp, end = 16.dp, top = 8.dp, bottom = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = onBack) {
                    Icon(
                        Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = "Zurück"
                    )
                }
                Text(
                    "Einstellungen",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.weight(1f)
                )
            }
        }
    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(top = padding.calculateTopPadding())
                .windowInsetsPadding(
                    WindowInsets.ime.union(WindowInsets.navigationBars)
                )
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                profile?.let { p ->
                    ProfileSection(
                        profile = p,
                        avatarBytes = avatarBytes,
                        onUploadAvatar = { bytes, ext ->
                            vm.uploadAvatar(bytes, ext)
                        },
                        onUpdateProfile = { displayName, password ->
                            vm.updateProfile(displayName, password)
                        },
                        onSync = { vm.triggerSync() },
                        onLogout = onLogout
                    )
                }

                if (profile?.isAdmin == true) {
                    AdminSection(
                        users = adminUsers,
                        settings = settings,
                        onToggleRegistration = { vm.adminToggleRegistration(it) },
                        onCreateUser = { username, password, displayName, isAdmin ->
                            vm.adminCreateUser(username, password, displayName, isAdmin)
                        },
                        onDeleteUser = { vm.adminDeleteUser(it) },
                        currentUserId = profile?.userId ?: ""
                    )
                }

                DangerZoneSection(
                    isAdmin = profile?.isAdmin == true,
                    onWipeMyData = { password ->
                        vm.wipeMyData(password) { onWipeComplete() }
                    },
                    onWipeAll = { password ->
                        vm.wipeAllData(password) { onWipeComplete() }
                    }
                )

                error?.let { e ->
                    Text(
                        e,
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            }
        }
    }
}

// ---- Profil-Sektion ----

@Composable
private fun ProfileSection(
    profile: UserProfileResponse,
    avatarBytes: ByteArray?,
    onUploadAvatar: (ByteArray, String) -> Unit,
    onUpdateProfile: (String?, String?) -> Unit,
    onSync: () -> Unit,
    onLogout: () -> Unit
) {
    var displayName by remember(profile.userId) { mutableStateOf(profile.displayName) }
    var newPassword by remember { mutableStateOf("") }
    var showPassword by remember { mutableStateOf(false) }
    val bitmap = rememberImageBitmap(avatarBytes)

    val pickImage = rememberImagePicker { bytes ->
        if (bytes != null) {
            val ext = if (bytes.size > 0 && bytes[0] == 0x89.toByte()) "png" else "jpg"
            onUploadAvatar(bytes, ext)
        }
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant
        )
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                AvatarImage(
                    bitmap = bitmap,
                    displayName = profile.displayName.ifBlank { profile.username },
                    sizeDp = 64
                )
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        profile.displayName.ifBlank { profile.username },
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        "@${profile.username}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    if (profile.isAdmin) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Icon(
                                Icons.Filled.AdminPanelSettings,
                                contentDescription = null,
                                modifier = Modifier.size(14.dp),
                                tint = MaterialTheme.colorScheme.primary
                            )
                            Text(
                                "Admin",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                    }
                }
            }

            OutlinedButton(
                onClick = pickImage,
                modifier = Modifier.fillMaxWidth()
            ) {
                Icon(Icons.Filled.Upload, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.height(4.dp))
                Text("Profilbild ändern")
            }

            HorizontalDivider()

            Text(
                "Profil bearbeiten",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold
            )

            OutlinedTextField(
                value = displayName,
                onValueChange = { displayName = it },
                label = { Text("Anzeigename") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                leadingIcon = {
                    Icon(Icons.Outlined.Person, contentDescription = null)
                }
            )

            OutlinedTextField(
                value = newPassword,
                onValueChange = { newPassword = it },
                label = { Text("Neues Passwort (optional)") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                visualTransformation = if (showPassword) VisualTransformation.None
                    else PasswordVisualTransformation(),
                leadingIcon = {
                    Icon(Icons.Outlined.Key, contentDescription = null)
                },
                trailingIcon = {
                    TextButton(onClick = { showPassword = !showPassword }) {
                        Text(if (showPassword) "Verbergen" else "Zeigen")
                    }
                },
                keyboardOptions = KeyboardOptions(
                    keyboardType = KeyboardType.Password,
                    imeAction = ImeAction.Done
                )
            )

            Button(
                onClick = {
                    val dn = displayName.trim().ifBlank { null }
                    val pw = newPassword.takeIf { it.isNotBlank() }
                    if (dn != null || pw != null) {
                        onUpdateProfile(dn, pw)
                        newPassword = ""
                    }
                },
                modifier = Modifier.fillMaxWidth(),
                enabled = displayName.isNotBlank()
            ) {
                Text("Speichern")
            }

            HorizontalDivider()

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                FilledTonalButton(
                    onClick = onSync,
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(Icons.Filled.Sync, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.height(4.dp))
                    Text("Sync")
                }
                OutlinedButton(
                    onClick = onLogout,
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(Icons.Outlined.Logout, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.height(4.dp))
                    Text("Abmelden")
                }
            }
        }
    }
}

// ---- Admin-Sektion ----

@Composable
private fun AdminSection(
    users: List<AdminUserResponse>,
    settings: com.earendil.todonotes.data.sync.SettingsResponse?,
    onToggleRegistration: (Boolean) -> Unit,
    onCreateUser: (String, String, String, Boolean) -> Unit,
    onDeleteUser: (String) -> Unit,
    currentUserId: String
) {
    var showCreateDialog by remember { mutableStateOf(false) }

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.primaryContainer
        )
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    Icons.Filled.AdminPanelSettings,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onPrimaryContainer
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    "Admin-Panel",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onPrimaryContainer
                )
            }

            // open_registration Toggle
            val openReg = settings?.openRegistration ?: false
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text("Registrierung erlauben")
                    Text(
                        "Neue User können sich selbst registrieren",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                }
                Switch(
                    checked = openReg,
                    onCheckedChange = onToggleRegistration
                )
            }

            HorizontalDivider()

            Text(
                "Benutzer (${users.size})",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold
            )

            users.forEach { user ->
                UserRow(
                    user = user,
                    isSelf = user.userId == currentUserId,
                    onDelete = { onDeleteUser(user.userId) }
                )
            }

            if (users.isEmpty()) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    CircularProgressIndicator(modifier = Modifier.size(16.dp))
                    Text("Lade Benutzer…")
                }
            }

            Button(
                onClick = { showCreateDialog = true },
                modifier = Modifier.fillMaxWidth()
            ) {
                Icon(Icons.Filled.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.height(4.dp))
                Text("Neuen Benutzer")
            }
        }
    }

    if (showCreateDialog) {
        CreateUserDialog(
            onDismiss = { showCreateDialog = false },
            onCreate = { username, password, displayName, isAdmin ->
                onCreateUser(username, password, displayName, isAdmin)
                showCreateDialog = false
            }
        )
    }
}

@Composable
private fun UserRow(
    user: AdminUserResponse,
    isSelf: Boolean,
    onDelete: () -> Unit
) {
    var showDeleteConfirm by remember { mutableStateOf(false) }

    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        AvatarImage(
            bitmap = null,
            displayName = user.displayName.ifBlank { user.username },
            sizeDp = 36
        )
        Spacer(Modifier.height(16.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                user.displayName.ifBlank { user.username },
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Medium
            )
            Text(
                "@${user.username}" + if (user.isAdmin) " · Admin" else "",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        if (isSelf) {
            Text(
                "(Du)",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        } else {
            IconButton(onClick = { showDeleteConfirm = true }) {
                Icon(
                    Icons.Filled.Delete,
                    contentDescription = "Löschen",
                    tint = MaterialTheme.colorScheme.error
                )
            }
        }
    }

    if (showDeleteConfirm) {
        androidx.compose.material3.AlertDialog(
            onDismissRequest = { showDeleteConfirm = false },
            title = { Text("Benutzer löschen?") },
            text = { Text("Soll @${user.username} wirklich gelöscht werden? Alle Daten dieses Users werden entfernt.") },
            confirmButton = {
                TextButton(onClick = { showDeleteConfirm = false; onDelete() }) {
                    Text("Löschen", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteConfirm = false }) { Text("Abbrechen") }
            }
        )
    }
}

@Composable
private fun CreateUserDialog(
    onDismiss: () -> Unit,
    onCreate: (String, String, String, Boolean) -> Unit
) {
    var username by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var displayName by remember { mutableStateOf("") }
    var isAdmin by remember { mutableStateOf(false) }
    var showPassword by remember { mutableStateOf(false) }

    androidx.compose.material3.AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Neuen Benutzer") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = username,
                    onValueChange = { username = it },
                    label = { Text("Benutzername") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = displayName,
                    onValueChange = { displayName = it },
                    label = { Text("Anzeigename (optional)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = password,
                    onValueChange = { password = it },
                    label = { Text("Passwort") },
                    singleLine = true,
                    visualTransformation = if (showPassword) VisualTransformation.None
                        else PasswordVisualTransformation(),
                    trailingIcon = {
                        TextButton(onClick = { showPassword = !showPassword }) {
                            Text(if (showPassword) "Verbergen" else "Zeigen")
                        }
                    },
                    modifier = Modifier.fillMaxWidth()
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text("Admin-Rechte")
                    Switch(checked = isAdmin, onCheckedChange = { isAdmin = it })
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = { onCreate(username, password, displayName, isAdmin) },
                enabled = username.isNotBlank() && password.length >= 6
            ) { Text("Erstellen") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Abbrechen") }
        }
    )
}

// ---- Danger-Zone (Daten löschen) ----

@Composable
private fun DangerZoneSection(
    isAdmin: Boolean,
    onWipeMyData: (String) -> Unit,
    onWipeAll: (String) -> Unit
) {
    var dialogMode by remember { mutableStateOf<DangerMode?>(null) }

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.errorContainer
        )
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                "Gefahrenzone",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onErrorContainer
            )

            OutlinedButton(
                onClick = { dialogMode = DangerMode.MY_DATA },
                modifier = Modifier.fillMaxWidth(),
                colors = androidx.compose.material3.ButtonDefaults.outlinedButtonColors(
                    contentColor = MaterialTheme.colorScheme.error
                )
            ) {
                Icon(Icons.Filled.Delete, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.height(4.dp))
                Text("Meine Daten löschen")
            }

            if (isAdmin) {
                OutlinedButton(
                    onClick = { dialogMode = DangerMode.ALL },
                    modifier = Modifier.fillMaxWidth(),
                    colors = androidx.compose.material3.ButtonDefaults.outlinedButtonColors(
                        contentColor = MaterialTheme.colorScheme.error
                    )
                ) {
                    Icon(Icons.Filled.Delete, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.height(4.dp))
                    Text("Alle Daten löschen (Admin)")
                }
            }
        }
    }

    dialogMode?.let { mode ->
        PasswordConfirmDialog(
            mode = mode,
            onDismiss = { dialogMode = null },
            onConfirm = { password ->
                when (mode) {
                    DangerMode.MY_DATA -> onWipeMyData(password)
                    DangerMode.ALL -> onWipeAll(password)
                }
                dialogMode = null
            }
        )
    }
}

private enum class DangerMode {
    MY_DATA,
    ALL
}

@Composable
private fun PasswordConfirmDialog(
    mode: DangerMode,
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit
) {
    var password by remember { mutableStateOf("") }
    var showPassword by remember { mutableStateOf(false) }

    val title = when (mode) {
        DangerMode.MY_DATA -> "Meine Daten löschen?"
        DangerMode.ALL -> "Alle Daten löschen?"
    }
    val message = when (mode) {
        DangerMode.MY_DATA ->
            "Alle deine Todos, Gewohnheiten, Notizen und Chats werden unwiderruflich gelöscht. Dein Account bleibt bestehen."
        DangerMode.ALL ->
            "WARNUNG: Alle Daten ALLER Benutzer sowie alle Accounts werden unwiderruflich gelöscht. Danach muss ein neuer Admin eingerichtet werden."
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(
                    message,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                OutlinedTextField(
                    value = password,
                    onValueChange = { password = it },
                    label = { Text("Passwort zur Bestätigung") },
                    singleLine = true,
                    visualTransformation = if (showPassword) VisualTransformation.None
                        else PasswordVisualTransformation(),
                    trailingIcon = {
                        TextButton(onClick = { showPassword = !showPassword }) {
                            Text(if (showPassword) "Verbergen" else "Zeigen")
                        }
                    },
                    keyboardOptions = KeyboardOptions(
                        keyboardType = KeyboardType.Password,
                        imeAction = ImeAction.Done
                    ),
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = { onConfirm(password) },
                enabled = password.isNotBlank(),
                colors = androidx.compose.material3.ButtonDefaults.textButtonColors(
                    contentColor = MaterialTheme.colorScheme.error
                )
            ) { Text("Löschen") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Abbrechen") }
        }
    )
}
