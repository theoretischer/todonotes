package com.earendil.todonotes

interface Platform {
    val name: String
    /** true auf Touch-Geräten (Android) — Drag-Reorder braucht Long-Press.
     *  false auf Desktop/Wasm (Maus) — Drag-Reorder ohne Long-Press. */
    val isTouch: Boolean
    /** Server-Basis-URL für diese Plattform. Web = window.location.origin,
     *  Android/Desktop = "" (muss manuell eingegeben werden). */
    val defaultServerUrl: String
}

expect fun getPlatform(): Platform

/** Plattform-spezifische Lifecycle-Events registrieren (Android: ON_RESUME,
 *  Web: visibilitychange→visible, Desktop: Window-Focus). Ruft bei
 *  „App in den Vordergrund" [AppContainer.onAppResume] auf. */
expect fun setupAppLifecyclePlatform(container: AppContainer)

/**
 * "Teilen mit der App"-Endbedingung: App minimieren und zur
 * Herkunfts-App zurückkehren (Android: moveTaskToBack — die Share-Activity
 * läuft im Task der absendenden App, damit landet man genau da wieder,
 * woher der Text kam). Desktop/Web: no-op (kein Task-Modell).
 */
expect fun moveAppToBack()
