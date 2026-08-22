package com.earendil.todonotes.data.sync

import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.contentType

/**
 * Authentifizierung gegen das Backend (M1: Multi-User-Auth).
 *
 * Endpunkte:
 *  - POST /auth/register {username, password} → {user_id, token}
 *  - POST /auth/login {username, password} → {user_id, token}
 *  - POST /auth/migrate-legacy {username, password} → {user_id, token}
 *
 * Nach erfolgreichem Login/Register werden token + userId + username
 * in [SyncPrefs] gespeichert. Danach kann [SyncManager] syncen.
 *
 * Der HttpClient wird von außen reingereicht (shared mit SyncManager).
 */
class AuthManager(
    private val prefs: SyncPrefs,
    private val httpClient: HttpClient
) {

    /** Registriert einen neuen Account. Liefert userId + token.
     *  Wirft bei Fehler (z.B. Username vergeben). */
    suspend fun register(username: String, password: String): AuthResult {
        return try {
            val response = httpClient.post("${prefs.serverUrl}/auth/register") {
                contentType(ContentType.Application.Json)
                setBody(RegisterRequest(username, password))
            }
            val auth: AuthResponse = response.body()
            saveAuth(auth, username)
            AuthResult.Success(auth)
        } catch (e: Exception) {
            AuthResult.Error(e.message ?: e::class.simpleName ?: "Unbekannter Fehler")
        }
    }

    /** Login mit bestehendem Account. Liefert userId + token.
     *  Wirft bei Fehler (z.B. falsches Passwort). */
    suspend fun login(username: String, password: String): AuthResult {
        return try {
            val response = httpClient.post("${prefs.serverUrl}/auth/login") {
                contentType(ContentType.Application.Json)
                setBody(LoginRequest(username, password))
            }
            val auth: AuthResponse = response.body()
            saveAuth(auth, username)
            AuthResult.Success(auth)
        } catch (e: Exception) {
            AuthResult.Error(e.message ?: e::class.simpleName ?: "Unbekannter Fehler")
        }
    }

    /** Registriert + überträgt alle Legacy-Daten auf den neuen User.
     *  Für die Migration von Static-Secret zu Login-Auth. */
    suspend fun migrateLegacy(username: String, password: String): AuthResult {
        return try {
            val response = httpClient.post("${prefs.serverUrl}/auth/migrate-legacy") {
                contentType(ContentType.Application.Json)
                setBody(MigrateLegacyRequest(username, password))
            }
            val auth: AuthResponse = response.body()
            saveAuth(auth, username)
            AuthResult.Success(auth)
        } catch (e: Exception) {
            AuthResult.Error(e.message ?: e::class.simpleName ?: "Unbekannter Fehler")
        }
    }

    /** Logout: token + userId + username löschen. */
    fun logout() {
        prefs.logout()
    }

    private fun saveAuth(auth: AuthResponse, username: String) {
        prefs.token = auth.token
        prefs.userId = auth.userId
        prefs.username = username
        // lastSyncedAt reset → beim neuen Login full-sync vom Server
        prefs.lastSyncedAt = 0L
    }
}

/** Ergebnis einer Auth-Operation. */
sealed class AuthResult {
    data class Success(val auth: AuthResponse) : AuthResult()
    data class Error(val message: String) : AuthResult()
}
