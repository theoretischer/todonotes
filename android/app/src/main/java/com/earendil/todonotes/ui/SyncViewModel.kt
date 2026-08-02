package com.earendil.todonotes.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.earendil.todonotes.data.sync.SyncManager
import com.earendil.todonotes.data.sync.SyncPrefs
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** Zustand für Sync + Einstellungen. Hält Server-URL/Token als reaktive State. */
class SyncViewModel(app: Application) : AndroidViewModel(app) {

    private val prefs = SyncPrefs(app)
    private val manager = SyncManager(app)

    private val _serverUrl = MutableStateFlow(prefs.serverUrl)
    val serverUrl: StateFlow<String> = _serverUrl.asStateFlow()

    private val _token = MutableStateFlow(prefs.token)
    val token: StateFlow<String> = _token.asStateFlow()

    private val _isSyncing = MutableStateFlow(false)
    val isSyncing: StateFlow<Boolean> = _isSyncing.asStateFlow()

    private val _lastResult = MutableStateFlow(prefs.lastSyncResult)
    val lastResult: StateFlow<String> = _lastResult.asStateFlow()

    private val _lastSyncAt = MutableStateFlow(prefs.lastSyncAt)
    val lastSyncAt: StateFlow<Long> = _lastSyncAt.asStateFlow()

    val isConfigured: Boolean get() = prefs.isConfigured

    fun setServerUrl(url: String) {
        prefs.serverUrl = url
        _serverUrl.value = prefs.serverUrl
    }

    fun setToken(token: String) {
        prefs.token = token
        _token.value = prefs.token
    }

    /** Sync sofort ausführen (manueller Trigger). */
    fun syncNow(onDone: ((Boolean) -> Unit)? = null) {
        if (_isSyncing.value) return
        viewModelScope.launch {
            _isSyncing.value = true
            val ok = withContext(Dispatchers.IO) { manager.sync() }
            _lastResult.value = prefs.lastSyncResult
            _lastSyncAt.value = prefs.lastSyncAt
            _isSyncing.value = false
            onDone?.invoke(ok)
        }
    }

    /** Health-Check (Teste Verbindung). */
    fun testConnection(onDone: ((Boolean) -> Unit)? = null) {
        viewModelScope.launch {
            _isSyncing.value = true
            val ok = withContext(Dispatchers.IO) { manager.health() }
            _isSyncing.value = false
            _lastResult.value = if (ok) "Verbindung OK" else "Verbindung fehlgeschlagen"
            onDone?.invoke(ok)
        }
    }
}
