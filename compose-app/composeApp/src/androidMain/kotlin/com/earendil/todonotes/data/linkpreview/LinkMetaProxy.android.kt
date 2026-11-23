package com.earendil.todonotes.data.linkpreview

import io.ktor.client.HttpClient

/**
 * Android: KEIN Proxy — kein CORS, der Client fetcht direkt
 * ([LinkPreviewRepository]). Die expect-Funktion liefert null zurück.
 */
actual fun createLinkMetaProxy(
    httpClient: HttpClient,
    serverUrl: String,
    token: String
): LinkMetaProxy? = null