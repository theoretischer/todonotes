package com.earendil.todonotes

private val platform = object : Platform {
    override val name: String
        get() = "Web (Wasm)"
    override val isTouch: Boolean = false
    // Leer: lokales Dev hat Web (:8090) und Backend (:8001) getrennt.
    // Produktion: Backend liefert Web aus (gleiche Origin) → User gibt
    // dort die Backend-URL ein oder wir setzen sie spaeter ueber config.
    override val defaultServerUrl: String = ""
}

actual fun getPlatform(): Platform = platform
