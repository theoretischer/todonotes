package com.earendil.todonotes.data.sync

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Sync-DTOs — spiegeln das Backend (backend/app/models.py) 1:1 wider.
 * Feldnamen camelCase wie in Kotlin/Room. Timestamps sind Millis (Long).
 * @Serializable für kotlinx.serialization (Retrofit-Converter).
 */

@Serializable
data class TodoDTO(
    val id: String,
    val title: String,
    val notes: String = "",
    val dueAt: Long? = null,
    val recurrence: String? = null,
    val completedAt: Long? = null,
    val createdAt: Long,
    val updatedAt: Long,
    val deletedAt: Long? = null,
    val logToHistory: Boolean = true
)

@Serializable
data class HabitDTO(
    val id: String,
    val title: String,
    val notes: String = "",
    val cadenceType: String,
    val interval: Int = 1,
    val resetWeekday: Int? = null,
    val resetAnchorDay: Int? = null,
    val resetAnchorMonth: Int? = null,
    val goalCount: Int,
    val startDate: Long,
    val logToHistory: Boolean = true,
    val lastLoggedPeriodStart: Long? = null,
    val createdAt: Long,
    val updatedAt: Long,
    val deletedAt: Long? = null
)

@Serializable
data class HabitLogDTO(
    val id: String,
    val habitId: String,
    val timestamp: Long,
    val note: String = ""
)

@Serializable
data class HabitHistoryEntryDTO(
    val id: String,
    val habitId: String,
    val title: String,
    val cadenceLabel: String,
    val periodStart: Long,
    val count: Int,
    val goal: Int,
    val loggedAt: Long
)

@Serializable
data class ChangesBundle(
    val todos: List<TodoDTO> = emptyList(),
    val habits: List<HabitDTO> = emptyList(),
    val habit_logs: List<HabitLogDTO> = emptyList(),
    val habit_history: List<HabitHistoryEntryDTO> = emptyList()
)

@Serializable
data class SyncRequest(
    @SerialName("last_synced_at") val lastSyncedAt: Long = 0,
    @SerialName("client_id") val clientId: String,
    val changes: ChangesBundle = ChangesBundle()
)

@Serializable
data class SyncResponse(
    @SerialName("new_synced_at") val newSyncedAt: Long,
    @SerialName("server_changes") val serverChanges: ChangesBundle
)
