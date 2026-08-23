package com.earendil.todonotes.ui

import com.earendil.todonotes.data.sync.AdminUserResponse
import com.earendil.todonotes.data.sync.AuthManager
import com.earendil.todonotes.data.sync.AuthResult
import com.earendil.todonotes.data.sync.SettingsResponse
import com.earendil.todonotes.data.sync.SetupStatusResponse
import com.earendil.todonotes.data.sync.SyncManager
import com.earendil.todonotes.data.sync.SyncPrefs
import com.earendil.todonotes.data.sync.UserProfileResponse
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * UI-Zustand des Auth-Gate / Login-Flow (M7d-3).
 */
sealed class AuthUiState {
    /** Server-Status wird geladen. */
    data object Loading : AuthUiState()
    /** Keine Server-URL gesetzt → User muss IP eingeben (nur App). */
    data object NeedsServerUrl : AuthUiState()
    /** Noch kein Admin → Setup-Bildschirm. */
    data class NeedsSetup(val openRegistration: Boolean) : AuthUiState()
    /** Admin existiert, User nicht eingeloggt → Login. */
    data class NeedsLogin(val openRegistration: Boolean) : AuthUiState()
    /** Eingeloggt → App. */
    data object Authenticated : AuthUiState()
}

/**
 * Plain-Kotlin-ViewModel für Auth-Gate + Settings (M7d-3).
 *
 * Prüft beim Start: Token vorhanden? → Profil abrufen → Authenticated.
 * Kein Token → setup-status abrufen → NeedsSetup oder NeedsLogin.
 */
class AuthViewModel(
    private val authManager: AuthManager,
    private val syncManager: SyncManager,
    private val prefs: SyncPrefs,
    scope: CoroutineScope
) {
    private val vmScope = CoroutineScope(scope.coroutineContext + SupervisorJob())

    private val _state = MutableStateFlow<AuthUiState>(AuthUiState.Loading)
    val state: StateFlow<AuthUiState> = _state.asStateFlow()

    private val _profile = MutableStateFlow<UserProfileResponse?>(null)
    val profile: StateFlow<UserProfileResponse?> = _profile.asStateFlow()

    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error.asStateFlow()

    /** Admin-User-Liste (für Admin-Panel). */
    private val _adminUsers = MutableStateFlow<List<AdminUserResponse>>(emptyList())
    val adminUsers: StateFlow<List<AdminUserResponse>> = _adminUsers.asStateFlow()

    private val _settings = MutableStateFlow<SettingsResponse?>(null)
    val settings: StateFlow<SettingsResponse?> = _settings.asStateFlow()

    /** Avatar-Bytes (roh) für die Anzeige. null = kein Avatar. */
    private val _avatarBytes = MutableStateFlow<ByteArray?>(null)
    val avatarBytes: StateFlow<ByteArray?> = _avatarBytes.asStateFlow()

    /** Beim Start prüfen: Token + Setup-Status.
     *  Wenn keine serverUrl: NeedsServerUrl (außer Web hat defaultServerUrl). */
    fun checkAuth() {
        _error.value = null
        vmScope.launch {
            // serverUrl pruefen (Web hat defaultServerUrl aus window.location).
            if (prefs.serverUrl.isBlank()) {
                _state.value = AuthUiState.NeedsServerUrl
                return@launch
            }
            if (prefs.isLoggedIn) {
                // Token vorhanden → Profil + Avatar + Admin-Daten laden.
                try {
                    loadProfile()
                    _state.value = AuthUiState.Authenticated
                    return@launch
                } catch (_: Exception) {
                    // Token ungültig → weiter zu setup-status pruefung.
                }
            }
            // Kein gueltiges Token → setup-status pruefen.
            try {
                val status = authManager.getSetupStatus()
                if (status.adminExists) {
                    _state.value = AuthUiState.NeedsLogin(status.openRegistration)
                } else {
                    _state.value = AuthUiState.NeedsSetup(status.openRegistration)
                }
            } catch (e: Exception) {
                _error.value = "Server nicht erreichbar: ${e.message}"
                _state.value = AuthUiState.NeedsServerUrl
            }
        }
    }

    /** Server-URL setzen (ohne checkAuth — fuer Login/Setup-Flow). */
    fun setServerUrl(url: String) {
        prefs.serverUrl = url
    }

    /** Server-URL setzen + setup-status pruefen → NeedsSetup oder NeedsLogin. */
    fun connectToServer(url: String) {
        _error.value = null
        prefs.serverUrl = url
        _state.value = AuthUiState.Loading
        vmScope.launch {
            try {
                val status = authManager.getSetupStatus()
                if (status.adminExists) {
                    _state.value = AuthUiState.NeedsLogin(status.openRegistration)
                } else {
                    _state.value = AuthUiState.NeedsSetup(status.openRegistration)
                }
            } catch (e: Exception) {
                _error.value = "Server nicht erreichbar: ${e.message}"
                _state.value = AuthUiState.NeedsServerUrl
            }
        }
    }

    // --- Setup (erster Admin) ---

    fun setupAdmin(username: String, password: String, displayName: String) {
        _error.value = null
        vmScope.launch {
            when (val r = authManager.setupAdmin(username, password, displayName)) {
                is AuthResult.Success -> {
                    // Nach Setup: initialen Sync vom Server (Legacy-Daten holen).
                    syncManager.sync()
                    try { loadProfile() } catch (_: Exception) { }
                    _state.value = AuthUiState.Authenticated
                }
                is AuthResult.Error -> _error.value = r.message
            }
        }
    }

    // --- Login / Registrieren ---

    fun login(username: String, password: String) {
        _error.value = null
        vmScope.launch {
            when (val r = authManager.login(username, password)) {
                is AuthResult.Success -> {
                    syncManager.sync()
                    try { loadProfile() } catch (_: Exception) { }
                    _state.value = AuthUiState.Authenticated
                }
                is AuthResult.Error -> _error.value = r.message
            }
        }
    }

    fun register(username: String, password: String) {
        _error.value = null
        vmScope.launch {
            when (val r = authManager.register(username, password)) {
                is AuthResult.Success -> {
                    syncManager.sync()
                    try { loadProfile() } catch (_: Exception) { }
                    _state.value = AuthUiState.Authenticated
                }
                is AuthResult.Error -> _error.value = r.message
            }
        }
    }

    // --- Profil ---

    /** Lädt Profil + Avatar + (falls Admin) User-Liste/Settings.
     *  Wirft bei Fehler (z.B. Token ungültig). Aufrufer muss fangen. */
    private suspend fun loadProfile() {
        val p = authManager.getProfile()
        _profile.value = p
        // Avatar laden (falls vorhanden).
        _avatarBytes.value = authManager.fetchAvatarBytes(p.userId)
        if (p.isAdmin == true) {
            _adminUsers.value = authManager.adminListUsers()
            _settings.value = authManager.adminUpdateSettings(null)
        }
    }

    fun updateProfile(displayName: String? = null, password: String? = null) {
        _error.value = null
        vmScope.launch {
            try {
                _profile.value = authManager.updateProfile(displayName, password)
            } catch (e: Exception) {
                _error.value = e.message
            }
        }
    }

    fun uploadAvatar(bytes: ByteArray, ext: String) {
        _error.value = null
        vmScope.launch {
            try {
                authManager.uploadAvatar(bytes, ext)
                _profile.value = authManager.getProfile()
                _avatarBytes.value = bytes // sofort anzeigen
            } catch (e: Exception) {
                _error.value = e.message
            }
        }
    }

    // --- Logout ---

    fun logout() {
        authManager.logout()
        _profile.value = null
        _avatarBytes.value = null
        _adminUsers.value = emptyList()
        _settings.value = null
        checkAuth()
    }

    // --- Sync ---

    fun triggerSync() {
        vmScope.launch { syncManager.sync() }
    }

    // --- Admin ---

    fun adminCreateUser(username: String, password: String, displayName: String, isAdmin: Boolean) {
        _error.value = null
        vmScope.launch {
            try {
                authManager.adminCreateUser(username, password, displayName, isAdmin)
                _adminUsers.value = authManager.adminListUsers()
            } catch (e: Exception) {
                _error.value = e.message
            }
        }
    }

    fun adminDeleteUser(userId: String) {
        _error.value = null
        vmScope.launch {
            try {
                authManager.adminDeleteUser(userId)
                _adminUsers.value = authManager.adminListUsers()
            } catch (e: Exception) {
                _error.value = e.message
            }
        }
    }

    fun adminToggleRegistration(open: Boolean) {
        _error.value = null
        vmScope.launch {
            try {
                _settings.value = authManager.adminUpdateSettings(open)
            } catch (e: Exception) {
                _error.value = e.message
            }
        }
    }
}
