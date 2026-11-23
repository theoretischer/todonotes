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
 * "Teilen mit der App"-Endbedingung: die (durchs Teilen geöffnete)
 * Activity WIRKLICH schließen (Android: finish — sie läuft im Task der
 * absendenden App, damit landet man genau da wieder, woher der Text kam).
 *
 * Vorsicht beim replaces: moveTaskToBack ließ die Activity im Hintergrund
 * AM LEBEN → App mehrfach offen (Recents) + Share-Session überlebte
 * (Nutzer-Report). finish() schließt sie sauber; laufende Sync-Jobs
 * laufen im appScope (Container, nicht Activity) weiter.
 *
 * Desktop/Web: no-op (kein Task-Modell).
 */
expect fun closeShareActivity()
