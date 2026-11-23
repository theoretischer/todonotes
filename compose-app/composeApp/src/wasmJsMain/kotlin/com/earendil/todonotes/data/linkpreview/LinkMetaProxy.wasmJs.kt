package com.earendil.todonotes.data.linkpreview

import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.bearerAuth
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.http.isSuccess
import kotlinx.coroutines.withContext
import kotlinx.coroutines.Dispatchers

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
    private val serverUrl: String,
    private val token: String
) : LinkMetaProxy {
    override suspend fun load(url: String): LinkMetaProxyResponse? = try {
        withContext(Dispatchers.Default) {
            val resp = httpClient.post("$serverUrl/linkmeta") {
                bearerAuth(token)
                setBody(LinkMetaProxyRequest(url))
            }
            if (!resp.status.isSuccess()) null
            else resp.body<LinkMetaProxyResponse>()
        }
    } catch (e: Throwable) {
        // Wasm-Ktor wirft bei Netzwerkfehlern kotlin.Error("Fail to
        // fetch") — Error wird von `catch (e: Exception)` NICHT
        // gefangen. Deshalb Throwable.
        null
    }
}

/** Wasm: Proxy auf die Backend-URL (default window.location.origin). */
actual fun createLinkMetaProxy(
    httpClient: HttpClient,
    serverUrl: String,
    token: String
): LinkMetaProxy? {
    if (serverUrl.isBlank() || token.isBlank()) return null
    return BackendLinkMetaProxy(httpClient, serverUrl.trimEnd('/'), token)
}