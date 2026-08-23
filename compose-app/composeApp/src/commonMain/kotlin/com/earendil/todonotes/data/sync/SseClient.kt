package com.earendil.todonotes.data.sync

import io.ktor.client.HttpClient
import io.ktor.client.plugins.sse.sse
import io.ktor.client.request.url
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch

/**
 * SSE-Client: hält eine dauerhafte Verbindung zum Server offen.
 *
 * Wenn ein anderer Client synced, pusht der Server ein "sync"-Event →
 * [SyncManager.onRemoteChanged] wird aufgerufen → sofortiger Pull-Sync.
 *
 * Reconnect mit Backoff bei Verbindungsabbruch.
 *
 * Token als Query-Parameter (SSE kann keine Custom-Header senden).
 */
class SseClient(
    private val httpClient: HttpClient,
    private val prefs: SyncPrefs,
    private val syncManager: SyncManager
) {
    private var job: Job? = null

    /** SSE-Verbindung starten (nach Login). Idempotent. */
    fun start(scope: CoroutineScope) {
        if (job?.isActive == true) return
        job = scope.launch { runSseLoop() }
    }

    /** SSE-Verbindung stoppen (bei Logout). */
    fun stop() {
        job?.cancel()
        job = null
    }

    private suspend fun runSseLoop() {
        var backoff = 1000L
        while (true) {
            if (prefs.token.isBlank() || prefs.serverUrl.isBlank()) {
                delay(2000)
                continue
            }
            try {
                httpClient.sse(
                    request = {
                        url("${prefs.serverUrl}/sync/events?token=${prefs.token}&client_id=${prefs.clientId}")
                    }
                ) {
                    // Verbindung steht — Backoff zurücksetzen.
                    backoff = 1000L
                    // Events empfangen (blockiert bis Verbindung schließt).
                    incoming.collect { event ->
                        // Jedes Event = "sync" → sofort pullen.
                        syncManager.onRemoteChanged()
                    }
                }
                // Verbindung normal geschlossen → reconnect.
                delay(backoff)
            } catch (e: Exception) {
                // Fehler (Netzwerk, Server down) → Backoff.
                delay(backoff)
                backoff = (backoff * 2).coerceAtMost(30000L)
            }
        }
    }
}
