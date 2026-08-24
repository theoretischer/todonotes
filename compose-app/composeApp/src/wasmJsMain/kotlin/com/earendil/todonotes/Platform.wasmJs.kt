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

/** Wasm: visibilitychange-Event. Wenn der Tab wieder sichtbar wird
 *  (visible), den onAppResume-Callback feuern → SyncManager pullt
 *  Änderungen vom anderen Gerät. */
@OptIn(kotlin.js.ExperimentalWasmJsInterop::class)
actual fun setupAppLifecyclePlatform(container: AppContainer) {
    setupVisibilityListener { container.onAppResume?.invoke() }
}

@OptIn(kotlin.js.ExperimentalWasmJsInterop::class)
private fun setupVisibilityListener(callback: () -> Unit) {
    js("""
    document.addEventListener('visibilitychange', function() {
        if (document.visibilityState === 'visible') {
            callback();
        }
    });
    """)
}
