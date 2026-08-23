package com.earendil.todonotes

private val platform = object : Platform {
    override val name: String
        get() = "Web (Wasm)"
    override val isTouch: Boolean = false
    // window.location.origin: In Produktion liefert das Backend die Web-App
    // selbst aus (gleiche Origin) → Server-URL steht automatisch fest, der
    // User muss NICHTS eingeben.
    // Lokales Dev (Web :8090, Backend :8001): Origin ist falsch → checkAuth
    // schlaegt fehl → ServerUrlForm erscheint, URL einmalig manuell eingeben.
    override val defaultServerUrl: String = getBaseUrl()
}

actual fun getPlatform(): Platform = platform
