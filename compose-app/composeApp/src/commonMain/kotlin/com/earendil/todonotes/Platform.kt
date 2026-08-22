package com.earendil.todonotes

interface Platform {
    val name: String
    /** true auf Touch-Geräten (Android) — Drag-Reorder braucht Long-Press.
     *  false auf Desktop/Wasm (Maus) — Drag-Reorder ohne Long-Press. */
    val isTouch: Boolean
}

expect fun getPlatform(): Platform
