@file:OptIn(kotlin.js.ExperimentalWasmJsInterop::class)

package com.earendil.todonotes

/**
 * Liefert die Basis-URL der aktuellen Seite (window.location.origin).
 * Auf Wasm: "http://localhost:8090" etc. — wird als defaultServerUrl
 * genutzt, damit der User keine Server-URL eingeben muss.
 */
private fun _getBaseUrlImpl(): String {
    js("return window.location.origin;")
}

fun getBaseUrl(): String {
    return _getBaseUrlImpl()
}
