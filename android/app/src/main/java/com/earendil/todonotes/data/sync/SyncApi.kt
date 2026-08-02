package com.earendil.todonotes.data.sync

import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.Header
import retrofit2.http.POST
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonObject

/** Retrofit-Interface für den Sync-Server. */
interface SyncApi {

    @GET("health")
    suspend fun health(): JsonObject

    @POST("sync")
    suspend fun sync(
        @Header("Authorization") authHeader: String,
        @Body request: SyncRequest
    ): SyncResponse
}
