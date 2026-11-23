package com.earendil.todonotes.data.repo

import com.earendil.todonotes.data.dao.LinkPreviewDao
import com.earendil.todonotes.data.entity.LinkPreview
import com.earendil.todonotes.data.linkpreview.LinkMetaParser
import com.earendil.todonotes.data.linkpreview.LinkMetaProxy
import com.earendil.todonotes.data.linkpreview.RedditOembed
import com.earendil.todonotes.data.linkpreview.createLinkMetaProxy
import io.ktor.client.HttpClient
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.statement.bodyAsBytes
import io.ktor.client.statement.bodyAsText
import io.ktor.http.isSuccess
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * Link-Vorschauen (F7): holt Open-Graph-Metadaten + Vorschaubild einer
 * URL und cached sie lokal in `link_previews`.
 *
 * - Cache-TTL: 7 Tage (danach im Hintergrund neu holen).
 * - Negativ-Cache: gescheiterte URLs werden 1 Tag nicht erneut angefragt.
 * - Kein Sync: reiner Client-Cache.
 * - [proxy]: Auf Wasm blockt CORS jeden externen Fetch — dort holt der
 *   Backend-Proxy `/linkmeta` die Metadaten. Android/Desktop: null →
 *   direkter Fetch (kein CORS).
 * - In-Flight-Dedupe: dieselbe URL wird nicht parallel mehrfach geholt
 *   (Chat/Liste zeigt dieselbe Vorschau oft mehrfach).
 */
class LinkPreviewRepository(
    private val dao: LinkPreviewDao,
    private val httpClient: HttpClient,
    private val proxy: LinkMetaProxy? = null
) {
    companion object {
        private const val TTL_MS = 7L * 24 * 60 * 60 * 1000
        private const val FAIL_TTL_MS = 24L * 60 * 60 * 1000
        private const val MAX_IMAGE_BYTES = 3 * 1024 * 1024
        /** Obergrenze für den HTML-String, den wir durchsuchen.
         *  YouTube setzt seine og-Tags erst bei ~Zeichen 680.000
         *  (hinter einem riesigen Inline-Script) → 1 MB Deckel. */
        private const val MAX_HTML_CHARS = 1_000_000
        /** oembed-Antworten sind winzig — 64 KB reichen dick. */
        private const val MAX_JSON_CHARS = 64_000

        /** Bot-artige Anfragen werden von Reddit & Co. gern blockiert —
         *  ein normaler Browser-UA liefert die og-Tags zuverlässig. */
        private const val USER_AGENT =
            "Mozilla/5.0 (Linux; Android 13) AppleWebKit/537.36 " +
                "(KHTML, like Gecko) Chrome/120.0 Mobile Safari/537.36"
    }

    private val fetchMutex = Mutex()
    private val inFlight = mutableSetOf<String>()

    /**
     * Zweiter Client OHNE Redirect-Following — nur zum Auflösen von
     * Share-Redirects (Location-Header lesen). Grund (Nutzer-Report):
     * Reddits /s/-Links sind 301-Redirects auf die echte Post-URL;
     * oembed gibt für rohe /s/-Links 400, braucht also die aufgelöste
     * Adresse. Der Hauptclient folgt Redirects engine-intern, dabei
     * ist die finale URL nicht abfragbar (response.request bleibt
     * die Original-URL).
     */
    private val noRedirectClient by lazy {
        HttpClient(httpClient.engine) { followRedirects = false }
    }

    /** Cache-Treffer (auch abgelaufene — die UI zeigt sofort was da ist,
     *  das Auffrischen läuft im Hintergrund). */
    suspend fun cached(url: String): LinkPreview? =
        dao.get(LinkMetaParser.normalize(url))

    /** Bulk für Listen. */
    suspend fun cachedMany(urls: List<String>): Map<String, LinkPreview> {
        if (urls.isEmpty()) return emptyMap()
        val normalized = urls.map { LinkMetaParser.normalize(it) }.distinct()
        return dao.getMany(normalized).associateBy { it.url }
    }

    /**
     * Holt (falls nötig) die Vorschau und liefert sie zurück.
     * Liefert den Cache-Treffer, wenn er frisch genug ist.
     * Netzwerkfehler → Negativ-Cache, es wird null geliefert.
     */
    suspend fun ensure(url: String, nowMs: Long = kotlin.time.Clock.System.now().toEpochMilliseconds()): LinkPreview? {
        val normalized = LinkMetaParser.normalize(url)
        if (normalized.isBlank()) return null

        val existing = dao.get(normalized)
        if (existing != null) {
            val age = nowMs - existing.fetchedAt
            val maxAge = if (existing.failed) FAIL_TTL_MS else TTL_MS
            if (age in 0..maxAge) {
                // Frühere Versionen haben Bot-Sperrseiten als gültige
                // Vorschau gespeichert — erkennbar am nutzlosen Muster
                // „kein Bild + Titel == Site-Name" (z. B. "youtube.com" /
                // "YouTube" oder "reddit.com" / "Reddit"). Die sind 7 Tage
                // gecacht und blockieren den besseren Weg (og/oembed).
                // Als veraltet behandeln und neu holen.
                val looksUseless = existing.imageBytes == null &&
                    (existing.title.isBlank() ||
                        existing.title.equals(existing.siteName, ignoreCase = true) ||
                        // Reddits Sperrseite: Titel "Reddit", Domain reddit.com
                        (RedditOembed.applies(normalized) && existing.siteName == "reddit.com"))
                if (!looksUseless) return existing.takeUnless { it.failed }
            }
        }

        // Doppel-Fetches derselben URL verhindern.
        fetchMutex.withLock {
            if (!inFlight.add(normalized)) return existing?.takeUnless { it.failed }
        }
        return try {
            try {
                fetch(normalized, nowMs)
            } catch (e: Throwable) {
                // Letzte Verteidigungslinie: ein Vorschau-Fetch darf die
                // App niemals crashen (Wasm: Fail to fetch ist Error).
                null
            }
        } finally {
            fetchMutex.withLock { inFlight.remove(normalized) }
        }
    }

    private suspend fun fetch(url: String, nowMs: Long): LinkPreview? {
        // Wasm: erst den Proxy probieren — CORS blockt den direkten
        // Weg im Browser immer. Liefert der Proxy nichts (Server down,
        // 404 …), fällt fetch() weiter unten in den direkten Weg, der
        // dann eh scheitert → Negativ-Cache. Auf Android/Desktop ist
        // der Proxy null und das wird übersprungen.
        proxy?.load(url)?.let { return storeProxyResult(url, it, nowMs) }
        return try {
            val response = httpClient.get(url) {
                header("User-Agent", USER_AGENT)
                header("Accept", "text/html,application/xhtml+xml")
                header("Accept-Language", "de-DE,de;q=0.9,en;q=0.8")
            }
            if (!response.status.isSuccess()) {
                storeFailed(url, nowMs)
                return null
            }
            val contentType = response.headers["Content-Type"].orEmpty()
            // Direkter Bild-Link (z. B. .png/.jpg geteilt) → kein HTML-Parsing.
            if (contentType.startsWith("image/")) {
                return store(url, LinkPreview(
                    url = url,
                    title = "",
                    siteName = LinkMetaParser.domainOf(url),
                    imageBytes = response.bodyAsBytes().takeIf { it.size <= MAX_IMAGE_BYTES },
                    fetchedAt = nowMs
                ))
            }
            val html = response.bodyAsText().take(MAX_HTML_CHARS)
            val meta = LinkMetaParser.parse(html)

            // Reddit liefert eine Bot-Sperrseite, die IMMER ein
            // <title>Reddit</title> hat — die Prüfung auf leeren Titel
            // greift dort also nie (Nutzer-Report). Erkennungsmerkmal:
            // Post-URL + kein og:image → oembed fragen. Klappt das
            // nicht, nehmen wir das, was das HTML hergab.
            if (RedditOembed.applies(url) && meta.imageUrl.isBlank()) {
                redditFallback(url, nowMs)?.let { return it }
            }

            if (meta.title.isBlank() && meta.imageUrl.isBlank()) {
                storeFailed(url, nowMs)
                return null
            }
            val imageBytes = if (meta.imageUrl.isNotBlank()) {
                loadImage(LinkMetaParser.resolveUrl(url, meta.imageUrl))
            } else null
            store(url, LinkPreview(
                url = url,
                title = meta.title,
                description = meta.description,
                siteName = meta.siteName.ifBlank { LinkMetaParser.domainOf(url) },
                imageBytes = imageBytes,
                fetchedAt = nowMs
            ))
        } catch (e: Throwable) {
            // Throwable statt Exception: der Wasm-Ktor wirft bei
            // CORS-Fehlschlag kotlin.Error("Fail to fetch") — Exception
            // fängt das nicht und die ganze App crasht.
            // Netzwerk/Timeout/Decode — als Fehlschlag cachen, nicht crashen.
            storeFailed(url, nowMs)
            null
        }
    }

    /**
     * Zweiter Versuch über Reddits oembed-Endpunkt.
     *
     * `url` ist dabei die ggf. durch Redirects aufgelöste Adresse
     * (Share-Links /s/<hash> müssen zuerst zu /comments/<id>/ werden,
     * sonst antwortet oembed mit 400). Liefert Titel + Subreddit,
     * aber kein Bild (Reddits Bild-CDNs blocken ebenfalls). Gibt null
     * zurück, wenn oembed nichts Brauchbares hat — der Aufrufer
     * entscheidet dann weiter.
     */
    private suspend fun redditFallback(url: String, nowMs: Long): LinkPreview? {
        if (!RedditOembed.applies(url)) return null
        return try {
            // /s/<hash>-Share-Links: erst per Location-Header auflösen,
            // oembed akzeptiert nur echte Post-URLs (400 sonst).
            val targetUrl = if (SHARE_LINK.containsMatchIn(url)) {
                resolveShareRedirect(url) ?: return null
            } else url
            val resp = httpClient.get(RedditOembed.endpointFor(targetUrl)) {
                header("User-Agent", USER_AGENT)
                header("Accept", "application/json")
            }
            if (!resp.status.isSuccess()) return null
            val meta = RedditOembed.parse(resp.bodyAsText().take(MAX_JSON_CHARS)) ?: return null
            store(url, LinkPreview(
                url = url,
                title = meta.title,
                description = meta.description,
                siteName = meta.siteName,
                imageBytes = null,
                fetchedAt = nowMs
            ))
        } catch (e: Throwable) {
            // Netzwerkfehler im oembed-Weg ist kein Grund, die URL
            // insgesamt als kaputt zu markieren.
            null
        }
    }

    /** Share-Redirect-URL (Reddit /r/x/s/<hash>). */
    private val SHARE_LINK = Regex("/r/[^/]+/s/[A-Za-z0-9]+")

    /**
     * Löst einen Share-Redirect per Location-Header auf (301) und
     * schneidet den Query-Müll (share_id, utm_*) ab.
     */
    private suspend fun resolveShareRedirect(url: String): String? = try {
        val resp = noRedirectClient.get(url) {
            header("User-Agent", USER_AGENT)
        }
        val location = resp.headers["Location"] ?: return null
        LinkMetaParser.resolveUrl(url, location).substringBefore('?')
    } catch (e: Throwable) {
        null
    }

    /** Ergebnis des Backend-Proxies in den Cache schreiben. Liefert
     *  null bei leerem Proxy-Ergebnis (keine Metadaten gefunden) →
     *  Negativ-Cache (wie bei jedem anderen Fehlschlag). */
    private suspend fun storeProxyResult(
        url: String,
        meta: com.earendil.todonotes.data.linkpreview.LinkMetaProxyResponse,
        nowMs: Long
    ): LinkPreview? {
        val imageBytes = if (meta.imageBase64.isNotBlank()) {
            decodeBase64Bytes(meta.imageBase64)?.takeIf { it.size <= MAX_IMAGE_BYTES }
        } else null
        if (meta.title.isBlank() && imageBytes == null) {
            storeFailed(url, nowMs)
            return null
        }
        return store(url, LinkPreview(
            url = url,
            title = meta.title,
            description = meta.description,
            siteName = meta.siteName.ifBlank { LinkMetaParser.domainOf(url) },
            imageBytes = imageBytes,
            fetchedAt = nowMs
        ))
    }

    /** Base64 → Bytes (Standard-Alphabet, mit Padding). */
    private fun decodeBase64Bytes(s: String): ByteArray? = try {
        kotlin.io.encoding.Base64.decode(s)
    } catch (e: Throwable) {
        null
    }

    private suspend fun loadImage(imageUrl: String): ByteArray? = try {
        val resp = httpClient.get(imageUrl) {
            header("User-Agent", USER_AGENT)
        }
        if (!resp.status.isSuccess()) null
        else {
            val type = resp.headers["Content-Type"].orEmpty()
            // Nur echte Bilder — sonst kommt bei Tracking-Pixeln HTML/Müll.
            if (type.startsWith("image/").not() && type.isNotBlank()) null
            else resp.bodyAsBytes().takeIf { it.size in 1..MAX_IMAGE_BYTES }
        }
    } catch (e: Throwable) {
        null
    }

    private suspend fun store(url: String, preview: LinkPreview): LinkPreview {
        dao.upsert(preview)
        return preview
    }

    private suspend fun storeFailed(url: String, nowMs: Long) {
        dao.upsert(
            LinkPreview(
                url = url,
                siteName = LinkMetaParser.domainOf(url),
                fetchedAt = nowMs,
                failed = true
            )
        )
    }

    /** Alle Vorschauen verwerfen (Einstellungen → „Cache leeren"). */
    suspend fun clearCache() = dao.clearAll()
}
