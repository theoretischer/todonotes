package com.earendil.todonotes.data.linkpreview

import io.ktor.client.HttpClient
import kotlinx.serialization.Serializable

/**
 * F7 Link-Vorschau: Server-Proxy für die Web-App.
 *
 * Warum: Im Browser blockt CORS JEDEN externen og-Fetch (reddit.com,
 * youtube.com … senden keinen Access-Control-Allow-Origin-Header).
 * Auf Android/Desktop gibt es kein CORS — dort fetchet der Client
 * direkt ([com.earendil.todonotes.data.repo.LinkPreviewRepository]).
 * Auf Wasm liefert [createLinkMetaProxy] deshalb einen Proxy, der
 * `/linkmeta` auf dem Backend aufruft (same-origin, Bearer-Token wie
 * bei /sync); der Server holt die Metadaten ohne CORS-Beschränkung.
 */
@Serializable
data class LinkMetaProxyRequest(val url: String)

@Serializable
data class LinkMetaProxyResponse(
    val title: String = "",
    val description: String = "",
    val siteName: String = "",
    val imageUrl: String = "",
    val imageBase64: String = "",
    val imageMime: String = ""
)

/** Holt Metadaten über den Backend-Proxy (Wasm) — oder null, wenn
 *  der Weg auf dieser Plattform nicht existiert / fehlschlägt.
 *  serverUrl/token als getter: werden pro Aufruf frisch gelesen. */
interface LinkMetaProxy {
    suspend fun load(url: String): LinkMetaProxyResponse?
}

/**
 * Plattform-Hook: Wasm → Proxy auf window.location.origin (Server-URL
 * aus SyncPrefs), Android/Desktop → null (direkter Fetch, kein CORS).
 */
expect fun createLinkMetaProxy(
    httpClient: HttpClient,
    serverUrl: () -> String,
    token: () -> String
): LinkMetaProxy?