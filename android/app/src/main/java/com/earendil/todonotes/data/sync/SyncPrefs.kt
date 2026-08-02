package com.earendil.todonotes.data.sync

import android.content.Context
import android.content.SharedPreferences

/**
 * Persistierte Sync-Einstellungen: Server-URL, Token, last_synced_at.
 * Liegen in SharedPreferences (private mode). Token im Klartext — für eine
 * Ein-Nutzer-App akzeptabel; später ggf. EncryptedSharedPreferences.
 */
class SyncPrefs(context: Context) {

    private val prefs: SharedPreferences =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    var serverUrl: String
        get() = prefs.getString(KEY_SERVER_URL, "") ?: ""
        set(value) = prefs.edit().putString(KEY_SERVER_URL, normalizeUrl(value)).apply()

    var token: String
        get() = prefs.getString(KEY_TOKEN, "") ?: ""
        set(value) = prefs.edit().putString(KEY_TOKEN, value.trim()).apply()

    var lastSyncedAt: Long
        get() = prefs.getLong(KEY_LAST_SYNC, 0L)
        set(value) = prefs.edit().putLong(KEY_LAST_SYNC, value).apply()

    var clientId: String = run {
        var id = prefs.getString(KEY_CLIENT_ID, null)
        if (id == null) {
            id = "android-" + System.currentTimeMillis().toString(36)
            prefs.edit().putString(KEY_CLIENT_ID, id).apply()
        }
        id
    }
        private set

    /** True, wenn Server-URL UND Token gesetzt sind → Sync möglich. */
    val isConfigured: Boolean
        get() = serverUrl.isNotBlank() && token.isNotBlank()

    var lastSyncResult: String
        get() = prefs.getString(KEY_LAST_RESULT, "") ?: ""
        set(value) = prefs.edit().putString(KEY_LAST_RESULT, value).apply()

    var lastSyncAt: Long
        get() = prefs.getLong(KEY_LAST_SYNC_AT, 0L)
        set(value) = prefs.edit().putLong(KEY_LAST_SYNC_AT, value).apply()

    private fun normalizeUrl(url: String): String {
        var u = url.trim().trimEnd('/')
        if (u.isNotEmpty() && !u.startsWith("http://") && !u.startsWith("https://")) {
            u = "https://$u"
        }
        return u
    }

    companion object {
        private const val PREFS_NAME = "todonotes_sync"
        private const val KEY_SERVER_URL = "server_url"
        private const val KEY_TOKEN = "token"
        private const val KEY_LAST_SYNC = "last_synced_at"
        private const val KEY_CLIENT_ID = "client_id"
        private const val KEY_LAST_RESULT = "last_sync_result"
        private const val KEY_LAST_SYNC_AT = "last_sync_at_wallclock"
    }
}
