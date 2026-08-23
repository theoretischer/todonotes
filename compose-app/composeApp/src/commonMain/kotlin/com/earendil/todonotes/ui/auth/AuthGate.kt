@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package com.earendil.todonotes.ui.auth

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.union
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
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
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import com.earendil.todonotes.ui.AuthUiState
import com.earendil.todonotes.ui.AuthViewModel

/**
 * Auth-Gate: wird vor der Haupt-App gezeigt.
 *
 * - Loading: Spinner
 * - NeedsServerUrl: nur IP/URL eingeben (nur App — Web nutzt window.location)
 * - NeedsSetup: erster Admin wird erstellt
 * - NeedsLogin: Login (+ Registrieren wenn open_registration)
 */
@Composable
fun AuthGate(
    vm: AuthViewModel,
    onAuthenticated: () -> Unit
) {
    val state by vm.state.collectAsState()
    val error by vm.error.collectAsState()

    if (state is AuthUiState.Authenticated) {
        onAuthenticated()
        return
    }

    Surface(
        modifier = Modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.background
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .windowInsetsPadding(WindowInsets.ime.union(WindowInsets.navigationBars)),
            contentAlignment = Alignment.Center
        ) {
            when (val s = state) {
                is AuthUiState.Loading -> LoadingView()
                is AuthUiState.NeedsServerUrl -> ServerUrlForm(vm, error)
                is AuthUiState.NeedsSetup -> SetupForm(vm, error)
                is AuthUiState.NeedsLogin -> LoginForm(vm, s, error)
                is AuthUiState.Authenticated -> Unit
            }
        }
    }
}

@Composable
private fun LoadingView() {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        CircularProgressIndicator()
        Spacer(Modifier.height(16.dp))
        Text("Verbinde mit Server…", color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

// ---- Logo-Header (geteilt) ----

@Composable
private fun LogoHeader(title: String, subtitle: String? = null) {
    Surface(
        modifier = Modifier.size(64.dp),
        shape = CircleShape,
        color = MaterialTheme.colorScheme.primaryContainer
    ) {
        Box(contentAlignment = Alignment.Center) {
            Icon(
                Icons.Filled.Lock,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onPrimaryContainer
            )
        }
    }
    Spacer(Modifier.height(16.dp))
    Text(title, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
    if (subtitle != null) {
        Spacer(Modifier.height(8.dp))
        Text(
            subtitle,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
    Spacer(Modifier.height(24.dp))
}

// ---- Stufe 1: Server-URL (nur App) ----

@Composable
private fun ServerUrlForm(vm: AuthViewModel, error: String?) {
    var serverUrl by remember { mutableStateOf("") }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 32.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        LogoHeader(
            "Server verbinden",
            "Gib die Adresse deines TodoNotes-Servers ein."
        )
        OutlinedTextField(
            value = serverUrl,
            onValueChange = { serverUrl = it },
            label = { Text("Server-URL") },
            placeholder = { Text("http://192.168.1.100:8001") },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            keyboardOptions = KeyboardOptions(
                capitalization = KeyboardCapitalization.None,
                keyboardType = KeyboardType.Uri,
                imeAction = ImeAction.Done
            )
        )
        Spacer(Modifier.height(24.dp))
        Button(
            onClick = { vm.connectToServer(serverUrl) },
            modifier = Modifier.fillMaxWidth(),
            enabled = serverUrl.isNotBlank()
        ) {
            Text("Verbinden")
        }
        error?.let {
            Spacer(Modifier.height(16.dp))
            Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
        }
    }
}

// ---- Stufe 2a: Setup (erster Admin) ----

@Composable
private fun SetupForm(vm: AuthViewModel, error: String?) {
    var username by remember { mutableStateOf("") }
    var displayName by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var showPassword by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 32.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        LogoHeader(
            "Admin einrichten",
            "Erstelle den ersten Admin-Account. Bestehende Daten werden migriert."
        )
        OutlinedTextField(
            value = username,
            onValueChange = { username = it },
            label = { Text("Benutzername") },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            keyboardOptions = KeyboardOptions(
                capitalization = KeyboardCapitalization.None,
                imeAction = ImeAction.Next
            )
        )
        Spacer(Modifier.height(12.dp))
        OutlinedTextField(
            value = displayName,
            onValueChange = { displayName = it },
            label = { Text("Anzeigename (optional)") },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next)
        )
        Spacer(Modifier.height(12.dp))
        OutlinedTextField(
            value = password,
            onValueChange = { password = it },
            label = { Text("Passwort") },
            modifier = Modifier.fillMaxWidth(),
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
            )
        )
        Spacer(Modifier.height(24.dp))
        Button(
            onClick = { vm.setupAdmin(username, password, displayName) },
            modifier = Modifier.fillMaxWidth(),
            enabled = username.isNotBlank() && password.length >= 6
        ) {
            Text("Admin erstellen")
        }
        error?.let {
            Spacer(Modifier.height(16.dp))
            Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
        }
    }
}

// ---- Stufe 2b: Login ----

@Composable
private fun LoginForm(vm: AuthViewModel, state: AuthUiState.NeedsLogin, error: String?) {
    var showRegister by remember { mutableStateOf(false) }
    var username by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var showPassword by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 32.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        LogoHeader(
            if (showRegister) "Account erstellen" else "Anmelden"
        )
        OutlinedTextField(
            value = username,
            onValueChange = { username = it },
            label = { Text("Benutzername") },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            keyboardOptions = KeyboardOptions(
                capitalization = KeyboardCapitalization.None,
                imeAction = ImeAction.Next
            )
        )
        Spacer(Modifier.height(12.dp))
        OutlinedTextField(
            value = password,
            onValueChange = { password = it },
            label = { Text("Passwort") },
            modifier = Modifier.fillMaxWidth(),
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
            )
        )
        Spacer(Modifier.height(24.dp))
        Button(
            onClick = {
                if (showRegister) vm.register(username, password)
                else vm.login(username, password)
            },
            modifier = Modifier.fillMaxWidth(),
            enabled = username.isNotBlank() && password.length >= 6
        ) {
            Text(if (showRegister) "Registrieren" else "Anmelden")
        }
        if (state.openRegistration) {
            Spacer(Modifier.height(8.dp))
            TextButton(onClick = { showRegister = !showRegister }) {
                Text(if (showRegister) "Stattdessen anmelden" else "Neuen Account erstellen")
            }
        }
        error?.let {
            Spacer(Modifier.height(16.dp))
            Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
        }
        Spacer(Modifier.height(24.dp))
        // Server-Wechsel nach Logout (URL bleibt sonst gespeichert und ist
        // nicht mehr änderbar)
        TextButton(onClick = { vm.resetServerUrl() }) {
            Text(
                "Anderen Server verbinden",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}
