package com.earendil.todonotes.data.sync

import com.russhwolf.settings.Settings

/**
 * Persistierte Sync-Einstellungen: Server-URL, Token, last_synced_at.
 *
 * Multiplatform (M5): nutzt multiplatform-settings-no-arg.
 * - Android: SharedPreferences
 * - Desktop (JVM): java.util.prefs.Preferences
 * - Wasm: localStorage
 *
 * Token im Klartext — für eine Ein-Nutzer-App akzeptabel; später ggf.
 * verschlüsselt (EncryptedSharedPreferences / Web Crypto).
 */
class SyncPrefs(private val settings: Settings = Settings()) {

    var serverUrl: String
        get() = settings.getStringOrNull(KEY_SERVER_URL) ?: ""
        set(value) = settings.putString(KEY_SERVER_URL, normalizeUrl(value))

    var token: String
        get() = settings.getStringOrNull(KEY_TOKEN) ?: ""
        set(value) = settings.putString(KEY_TOKEN, value.trim())

    /** userId des authentifizierten Users (vom Backend beim Login geliefert).
     *  Default "legacy-user" für Übergangs-Auth (Static-Secret). */
    var userId: String
        get() = settings.getStringOrNull(KEY_USER_ID) ?: "legacy-user"
        set(value) = settings.putString(KEY_USER_ID, value)

    /** username des eingeloggten Accounts (für Anzeige in Settings/Profile). */
    var username: String
        get() = settings.getStringOrNull(KEY_USERNAME) ?: ""
        set(value) = settings.putString(KEY_USERNAME, value)

    var lastSyncedAt: Long
        get() = settings.getLongOrNull(KEY_LAST_SYNC) ?: 0L
        set(value) = settings.putLong(KEY_LAST_SYNC, value)

    var clientId: String = ""
        get() {
            var id = settings.getStringOrNull(KEY_CLIENT_ID)
            if (id == null) {
                id = "client-" + kotlinx.datetime.Clock.System.now().toEpochMilliseconds().toString(36)
                settings.putString(KEY_CLIENT_ID, id)
            }
            return id
        }
        private set

    /** True, wenn Server-URL UND Token gesetzt sind → Sync möglich. */
    val isConfigured: Boolean
        get() = serverUrl.isNotBlank() && token.isNotBlank()

    var lastSyncResult: String
        get() = settings.getStringOrNull(KEY_LAST_RESULT) ?: ""
        set(value) = settings.putString(KEY_LAST_RESULT, value)

    var lastSyncAt: Long
        get() = settings.getLongOrNull(KEY_LAST_SYNC_AT) ?: 0L
        set(value) = settings.putLong(KEY_LAST_SYNC_AT, value)

    /** Server-Wipe-Epoch (0 = nie gewisped). Wenn der Server einen anderen
     *  Wert liefert, muss der Client seine lokale DB leeren (veraltete
     *  Daten nach Server-Wipe). */
    var wipeEpoch: Long
        get() = settings.getLongOrNull(KEY_WIPE_EPOCH) ?: 0L
        set(value) = settings.putLong(KEY_WIPE_EPOCH, value)

    /** True, wenn der User per Login authentifiziert ist (nicht Static-Secret). */
    val isLoggedIn: Boolean
        get() = username.isNotBlank() && token.isNotBlank()

    /** Logout: Token + userId + username löschen. Server-URL + Sync-State bleiben. */
    fun logout() {
        settings.remove(KEY_TOKEN)
        settings.remove(KEY_USER_ID)
        settings.remove(KEY_USERNAME)
        // lastSyncedAt zurücksetzen → beim nächsten Login full-sync
        settings.remove(KEY_LAST_SYNC)
    }

    private fun normalizeUrl(url: String): String {
        var u = url.trim().trimEnd('/')
        if (u.isNotEmpty() && !u.startsWith("http://") && !u.startsWith("https://")) {
            u = "https://$u"
        }
        return u
    }

    companion object {
        private const val KEY_SERVER_URL = "server_url"
        private const val KEY_TOKEN = "token"
        private const val KEY_USER_ID = "user_id"
        private const val KEY_USERNAME = "username"
        private const val KEY_LAST_SYNC = "last_synced_at"
        private const val KEY_CLIENT_ID = "client_id"
        private const val KEY_LAST_RESULT = "last_sync_result"
        private const val KEY_LAST_SYNC_AT = "last_sync_at_wallclock"
        private const val KEY_WIPE_EPOCH = "wipe_epoch"
    }
}
