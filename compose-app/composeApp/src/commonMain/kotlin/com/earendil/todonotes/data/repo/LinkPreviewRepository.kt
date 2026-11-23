package com.earendil.todonotes.data.repo

import com.earendil.todonotes.data.dao.LinkPreviewDao
import com.earendil.todonotes.data.entity.LinkPreview
import com.earendil.todonotes.data.linkpreview.LinkMetaParser
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
 * - Bilder werden nur geladen, wenn sie nicht zu groß sind (max 3 MB)
 *   und halbwegs wie ein Bild aussehen.
 * - In-Flight-Dedupe: dieselbe URL wird nicht parallel mehrfach geholt
 *   (Chat/Liste zeigt dieselbe Vorschau oft mehrfach).
 */
class LinkPreviewRepository(
    private val dao: LinkPreviewDao,
    private val httpClient: HttpClient
) {
    companion object {
        private const val TTL_MS = 7L * 24 * 60 * 60 * 1000
        private const val FAIL_TTL_MS = 24L * 60 * 60 * 1000
        private const val MAX_IMAGE_BYTES = 3 * 1024 * 1024
        private const val MAX_HTML_CHARS = 200_000

        /** Bot-artige Anfragen werden von Reddit & Co. gern blockiert —
         *  ein normaler Browser-UA liefert die og-Tags zuverlässig. */
        private const val USER_AGENT =
            "Mozilla/5.0 (Linux; Android 13) AppleWebKit/537.36 " +
                "(KHTML, like Gecko) Chrome/120.0 Mobile Safari/537.36"
    }

    private val fetchMutex = Mutex()
    private val inFlight = mutableSetOf<String>()

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
            if (age in 0..maxAge) return existing.takeUnless { it.failed }
        }

        // Doppel-Fetches derselben URL verhindern.
        fetchMutex.withLock {
            if (!inFlight.add(normalized)) return existing?.takeUnless { it.failed }
        }
        return try {
            fetch(normalized, nowMs)
        } finally {
            fetchMutex.withLock { inFlight.remove(normalized) }
        }
    }

    private suspend fun fetch(url: String, nowMs: Long): LinkPreview? {
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
        } catch (e: Exception) {
            // Netzwerk/Timeout/Decode — als Fehlschlag cachen, nicht crashen.
            storeFailed(url, nowMs)
            null
        }
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
    } catch (e: Exception) {
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
