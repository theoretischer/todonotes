package com.earendil.todonotes

private val platform = object : Platform {
    override val name: String
        get() = "Web (Wasm)"
    override val isTouch: Boolean = false
    override val defaultServerUrl: String
        get() = getBaseUrl()
}

actual fun getPlatform(): Platform = platform
