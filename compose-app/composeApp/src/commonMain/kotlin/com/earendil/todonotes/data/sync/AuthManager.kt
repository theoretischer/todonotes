package com.earendil.todonotes.data.sync

import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.bearerAuth
import io.ktor.client.request.delete
import io.ktor.client.request.get
import io.ktor.client.request.patch
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
            if (response.status.value !in 200..299) {
                return AuthResult.Error(extractErrorMessage(response))
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
            if (response.status.value !in 200..299) {
                return AuthResult.Error(extractErrorMessage(response))
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
            if (response.status.value !in 200..299) {
                return AuthResult.Error(extractErrorMessage(response))
            }
            val auth: AuthResponse = response.body()
            saveAuth(auth, username)
            AuthResult.Success(auth)
        } catch (e: Exception) {
            AuthResult.Error(e.message ?: e::class.simpleName ?: "Unbekannter Fehler")
        }
    }

    /** Extrahiert die Fehlermeldung aus einer HTTP-Response.
     *  FastAPI liefert {"detail": "..."} bei Fehlern. */
    private suspend fun extractErrorMessage(response: io.ktor.client.statement.HttpResponse): String {
        return try {
            val body: Map<String, String> = response.body()
            body["detail"] ?: "HTTP ${response.status.value}"
        } catch (e: Exception) {
            "HTTP ${response.status.value}"
        }
    }

    /** Logout: token + userId + username löschen. */
    fun logout() {
        prefs.logout()
    }

    // --- M7d-3: Setup + Profil + Admin ---


    /** Public: Setup-Status abrufen (admin_exists, open_registration). */
    suspend fun getSetupStatus(): SetupStatusResponse {
        return httpClient.get("${prefs.serverUrl}/auth/setup-status").body()
    }

    /** Ersten Admin erstellen + Legacy-Daten migrieren.
     *  Nur möglich wenn noch kein Admin existiert. */
    suspend fun setupAdmin(username: String, password: String, displayName: String): AuthResult {
        return try {
            val response = httpClient.post("${prefs.serverUrl}/auth/setup") {
                contentType(ContentType.Application.Json)
                setBody(SetupRequest(username, password, displayName))
            }
            if (response.status.value !in 200..299) {
                return AuthResult.Error(extractErrorMessage(response))
            }
            val auth: AuthResponse = response.body()
            saveAuth(auth, username)
            AuthResult.Success(auth)
        } catch (e: Exception) {
            AuthResult.Error(e.message ?: e::class.simpleName ?: "Unbekannter Fehler")
        }
    }

    /** Eigenes Profil abrufen. */
    suspend fun getProfile(): UserProfileResponse {
        return httpClient.get("${prefs.serverUrl}/auth/me") {
            bearerAuth(prefs.token)
        }.body()
    }

    /** Eigenes Profil bearbeiten (display_name und/oder passwort). */
    suspend fun updateProfile(displayName: String? = null, password: String? = null): UserProfileResponse {
        return httpClient.patch("${prefs.serverUrl}/auth/me") {
            bearerAuth(prefs.token)
            contentType(ContentType.Application.Json)
            setBody(UpdateProfileRequest(displayName, password))
        }.body()
    }

    /** Profilbild hochladen (Bytes als Base64-JSON). */
    @OptIn(kotlin.io.encoding.ExperimentalEncodingApi::class)
    suspend fun uploadAvatar(bytes: ByteArray, ext: String): String {
        val base64 = kotlin.io.encoding.Base64.encode(bytes)
        val response: AvatarUploadResponse = httpClient.post("${prefs.serverUrl}/auth/me/avatar") {
            bearerAuth(prefs.token)
            contentType(ContentType.Application.Json)
            setBody(AvatarUploadRequest(base64, ext))
        }.body()
        return response.filename
    }

    /** Avatar-URL für einen User (relativ zur serverUrl). */
    fun avatarUrl(userId: String): String {
        return "${prefs.serverUrl}/avatars/$userId"
    }

    /** Avatar-Bytes für einen User abrufen. null bei 404/Fehler. */
    suspend fun fetchAvatarBytes(userId: String): ByteArray? {
        return try {
            val response = httpClient.get("${prefs.serverUrl}/avatars/$userId")
            if (response.status.value !in 200..299) return null
            response.body<ByteArray>()
        } catch (e: Exception) {
            null
        }
    }

    // --- Admin ---

    /** Alle User auflisten (Admin only). */
    suspend fun adminListUsers(): List<AdminUserResponse> {
        return httpClient.get("${prefs.serverUrl}/admin/users") {
            bearerAuth(prefs.token)
        }.body()
    }

    /** User anlegen (Admin only). */
    suspend fun adminCreateUser(
        username: String, password: String, displayName: String, isAdmin: Boolean
    ): AdminUserResponse {
        return httpClient.post("${prefs.serverUrl}/admin/users") {
            bearerAuth(prefs.token)
            contentType(ContentType.Application.Json)
            setBody(AdminCreateUserRequest(username, password, displayName, isAdmin))
        }.body()
    }

    /** User löschen (Admin only, nicht sich selbst). */
    suspend fun adminDeleteUser(userId: String) {
        httpClient.delete("${prefs.serverUrl}/admin/users/$userId") {
            bearerAuth(prefs.token)
        }
    }

    /** open_registration setzen (Admin only). */
    suspend fun adminUpdateSettings(openRegistration: Boolean?): SettingsResponse {
        return httpClient.patch("${prefs.serverUrl}/admin/settings") {
            bearerAuth(prefs.token)
            contentType(ContentType.Application.Json)
            setBody(UpdateSettingsRequest(openRegistration))
        }.body()
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
