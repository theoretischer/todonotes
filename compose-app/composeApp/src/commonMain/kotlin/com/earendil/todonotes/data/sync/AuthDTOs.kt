package com.earendil.todonotes.data.sync

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Auth-DTOs — spiegeln backend/app/models.py Auth-Models wider.
 * M1: Multi-User-Auth (Register/Login/Migrate-Legacy).
 */

@Serializable
data class RegisterRequest(
    val username: String,
    val password: String
)

@Serializable
data class LoginRequest(
    val username: String,
    val password: String
)

/** Migrate-Legacy: wie Register, überträgt aber alle Legacy-Daten auf den neuen User. */
@Serializable
data class MigrateLegacyRequest(
    val username: String,
    val password: String
)

@Serializable
data class AuthResponse(
    @SerialName("user_id") val userId: String,
    val token: String
)

/** Health-Check Response (public, kein Token nötig). */
@Serializable
data class HealthResponse(
    val status: String,
    val time: Long
)

// --- M7d-3: Setup + Profil + Admin ---

@Serializable
data class SetupStatusResponse(
    @SerialName("admin_exists") val adminExists: Boolean,
    @SerialName("open_registration") val openRegistration: Boolean
)

@Serializable
data class SetupRequest(
    val username: String,
    val password: String,
    @SerialName("display_name") val displayName: String = ""
)

@Serializable
data class UserProfileResponse(
    @SerialName("user_id") val userId: String,
    val username: String,
    @SerialName("display_name") val displayName: String = "",
    @SerialName("is_admin") val isAdmin: Boolean,
    @SerialName("profile_picture") val profilePicture: String? = null
)

@Serializable
data class UpdateProfileRequest(
    @SerialName("display_name") val displayName: String? = null,
    val password: String? = null
)

@Serializable
data class AdminUserResponse(
    @SerialName("user_id") val userId: String,
    val username: String,
    @SerialName("display_name") val displayName: String = "",
    @SerialName("is_admin") val isAdmin: Boolean,
    @SerialName("created_at") val createdAt: Long
)

@Serializable
data class AdminCreateUserRequest(
    val username: String,
    val password: String,
    @SerialName("display_name") val displayName: String = "",
    @SerialName("is_admin") val isAdmin: Boolean = false
)

@Serializable
data class UpdateSettingsRequest(
    @SerialName("open_registration") val openRegistration: Boolean? = null
)

@Serializable
data class SettingsResponse(
    @SerialName("open_registration") val openRegistration: Boolean
)

/** Passwort-Bestätigung für destruktive Aktionen (Daten löschen). */
@Serializable
data class PasswordConfirmRequest(
    val password: String
)

@Serializable
data class SimpleResult(
    val ok: Boolean = false
)

/** Avatar-Upload: Bild als Base64-String + Dateiendung. */
@Serializable
data class AvatarUploadRequest(
    val data: String,
    val ext: String
)

@Serializable
data class AvatarUploadResponse(
    val filename: String
)
