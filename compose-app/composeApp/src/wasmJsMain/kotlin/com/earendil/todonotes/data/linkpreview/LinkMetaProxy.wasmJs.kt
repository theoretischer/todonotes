package com.earendil.todonotes.data.linkpreview

import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.bearerAuth
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.contentType
import io.ktor.http.isSuccess

/**
 * Wasm: Der Browser blockt jeden externen og-Fetch (CORS — kein
 * Access-Control-Allow-Origin). Deshalb läuft die Metadaten-Suche
 * hier über den Backend-Proxy `/linkmeta` (same-origin, Bearer-Token
 * wie bei /sync; der Server folgt Redirects, löst Reddit-Share-Links
 * und lädt og-Bilder inline). Läuft der Server nicht / schlägt der
 * Request fehl, liefert [load] null → der Client cached das als
 * Fehlschlag (Negativ-Cache) und zeigt schlicht keine Karte.
 */
private class BackendLinkMetaProxy(
    private val httpClient: HttpClient,
    private val getServerUrl: () -> String,
    private val getToken: () -> String
) : LinkMetaProxy {
    override suspend fun load(url: String): LinkMetaProxyResponse? = try {
        // Bewusst GLEICH gebaut wie SyncManager.push/pull (kein
        // withContext, contentType explizit) — der Sync-Request funktioniert
        // auf Wasm nachweislich; hier keinen anderen Weg probieren.
        val serverUrl = getServerUrl().trimEnd('/')
        val token = getToken()
        println("LinkProxy.load: url=$url serverUrl='$serverUrl' tokenLen=${token.length}")
        if (serverUrl.isBlank() || token.isBlank()) return null
        val resp = httpClient.post("$serverUrl/linkmeta") {
            contentType(ContentType.Application.Json)
            bearerAuth(token)
            setBody(LinkMetaProxyRequest(url))
        }
        if (!resp.status.isSuccess()) {
            println("LinkProxy.load: HTTP ${resp.status.value} ${resp.status.description}")
            return null
        }
        println("LinkProxy.load: 200, decodiere …")
        resp.body<LinkMetaProxyResponse>()
    } catch (e: Throwable) {
        // Wasm-Ktor wirft bei Netzwerkfehlern kotlin.Error("Fail to
        // fetch") — Error wird von `catch (e: Exception)` NICHT
        // gefangen. Deshalb Throwable.
        println("LinkProxy.load FAILED: ${e::class.simpleName}: ${e.message}")
        null
    }
}

/** Wasm: Proxy auf die Backend-URL (default window.location.origin).
 *  serverUrl/token werden JEDES MAL frisch gelesen (getter statt
 *  Snapshot) — nach Logout + Neu-Login greift der Proxy mit dem
 *  aktuellen Token, sonst ginge /linkmeta auf 401. */
actual fun createLinkMetaProxy(
    httpClient: HttpClient,
    serverUrl: () -> String,
    token: () -> String
): LinkMetaProxy? = BackendLinkMetaProxy(httpClient, serverUrl, token)