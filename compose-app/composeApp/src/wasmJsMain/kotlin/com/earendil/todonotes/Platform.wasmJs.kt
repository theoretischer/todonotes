package com.earendil.todonotes

private val platform = object : Platform {
    override val name: String
        get() = "Web (Wasm)"
    override val isTouch: Boolean = false
}

actual fun getPlatform(): Platform = platform
