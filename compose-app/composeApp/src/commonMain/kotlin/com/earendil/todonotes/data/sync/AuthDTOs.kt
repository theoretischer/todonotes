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
