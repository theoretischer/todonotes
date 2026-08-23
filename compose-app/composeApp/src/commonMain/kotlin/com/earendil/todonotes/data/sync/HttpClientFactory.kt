package com.earendil.todonotes.data.sync

import io.ktor.client.HttpClient
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.plugins.logging.LogLevel
import io.ktor.client.plugins.logging.Logging
import io.ktor.client.plugins.sse.SSE
import io.ktor.serialization.kotlinx.json.json
import kotlinx.serialization.json.Json

/**
 * Erstellt den gemeinsamen HttpClient für Sync + Auth (M5).
 *
 * Nutzt den Default-Engine (wird automatisch pro Plattform gewählt:
 * OkHttp auf Android/Desktop, JS auf Wasm).
 *
 * ContentNegotiation mit kotlinx.serialization für JSON-Request/Response.
 * Logging auf INFO-Level (Body wird nicht geloggt — Datenschonung).
 */
fun createHttpClient(): HttpClient {
    return HttpClient {
        install(ContentNegotiation) {
            json(Json {
                ignoreUnknownKeys = true
                encodeDefaults = true
                explicitNulls = false
            })
        }
        install(Logging) {
            level = LogLevel.INFO
        }
        // Timeout: ohne das kann "Verbinde mit Server…" bei unerreichbarem
        // Server minutenlang hängen (Browser-Fetch ohne Default-Timeout).
        install(HttpTimeout) {
            requestTimeoutMillis = 10_000
            connectTimeoutMillis = 10_000
            socketTimeoutMillis = 10_000
        }
        install(SSE)
    }
}
