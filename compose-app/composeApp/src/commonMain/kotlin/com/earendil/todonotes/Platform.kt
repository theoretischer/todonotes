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
