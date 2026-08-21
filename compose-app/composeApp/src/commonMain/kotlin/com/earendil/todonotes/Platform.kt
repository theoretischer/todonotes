package com.earendil.todonotes

interface Platform {
    val name: String
}

expect fun getPlatform(): Platform
