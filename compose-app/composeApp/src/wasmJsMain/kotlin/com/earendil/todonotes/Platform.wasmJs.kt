package com.earendil.todonotes

private val platform = object : Platform {
    override val name: String
        get() = "Web (Wasm)"
}

actual fun getPlatform(): Platform = platform
