package com.earendil.todonotes.data.sync

import io.ktor.client.HttpClient
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.plugins.logging.LogLevel
import io.ktor.client.plugins.logging.Logging
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
    }
}
