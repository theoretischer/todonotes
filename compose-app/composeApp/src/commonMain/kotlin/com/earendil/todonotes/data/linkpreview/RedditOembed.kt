package com.earendil.todonotes.data.linkpreview

/**
 * Reddit-Sonderweg für Link-Vorschauen.
 *
 * Warum? Reddit liefert an alles ohne echte Browser-Engine eine
 * Bot-Sperrseite: 403/200 mit leerem Rumpf, KEINE og-Tags — getestet
 * mit Browser-, Discord- und Twitter-UA, `.json`, `api.reddit.com`,
 * `old.reddit.com` und RSS (429/403). Reddits offene API ist tot.
 *
 * ABER: der öffentliche **oembed**-Endpunkt
 * `https://www.reddit.com/oembed?url=<postUrl>` antwortet ohne Auth
 * mit JSON und liefert `title`, `author_name` und den Subreddit-Namen
 * (im `html`-Feld verlinkt). Bilder gibt es dort nicht — die eigene
 * Domäne `i.redd.it`/`preview.redd.it` blockt ebenfalls.
 */
object RedditOembed {

    /** Ist das eine Reddit-URL, für die oembed sinnvoll ist? */
    fun applies(url: String): Boolean {
        val host = hostOf(url)
        if (!host.endsWith("reddit.com") && host != "redd.it") return false
        // Nur Post-URLs: /r/<sub>/comments/<id>/… oder /r/<sub>/s/<hash>.
        // Ausnahme redd.it/<id> — Kurzlink, immer ein Post (Subreddit-
        // Startseiten gibt es dort nicht).
        if (host == "redd.it") return SHORT_ID.matches(url.substringAfter("redd.it/").trimEnd('/'))
        return POST_PATH.containsMatchIn(url) || SHARE_PATH.containsMatchIn(url)
    }

    /** oembed-Endpunkt für eine Post-URL. */
    fun endpointFor(url: String): String =
        "https://www.reddit.com/oembed?url=" + url.encodeUtf8()

    /**
     * Liest Titel, Autor und Subreddit aus der oembed-Antwort.
     * Absichtlich mit Regex statt JSON-Parser: das Format ist winzig
     * und stabil, und so bleibt es plattformunabhängig ohne neue
     * Abhängigkeit.
     */
    fun parse(json: String): Meta? {
        val title = jsonString(json, "title") ?: return null
        if (title.isBlank()) return null
        val author = jsonString(json, "author_name").orEmpty()
        // Subreddit steckt im html-Feld: …/r/<name>/
        // Gruppe 1 = Subreddit-NAME (ohne "r/").
        val sub = SUBREDDIT_LINK.find(json)?.groupValues?.get(1)
        val site = if (!sub.isNullOrBlank()) "r/$sub" else "reddit"
        val description = buildString {
            if (author.isNotBlank()) append("u/").append(author)
        }
        return Meta(
            title = decodeEntities(title).trim(),
            description = description,
            siteName = site,
            imageUrl = ""
        )
    }

    data class Meta(
        val title: String,
        val description: String,
        val siteName: String,
        val imageUrl: String
    )

    private val POST_PATH = Regex("""/r/[^/]+/comments/[a-z0-9]+""", RegexOption.IGNORE_CASE)
    private val SHARE_PATH = Regex("""/r/[^/]+/s/[A-Za-z0-9]+""")
    private val SHORT_ID = Regex("""[A-Za-z0-9]+""")
    private val SUBREDDIT_LINK = Regex("""reddit\.com/r/([A-Za-z0-9_]+)/""")

    private fun hostOf(url: String): String =
        url.substringAfter("://", url).substringBefore('/').substringBefore(':').lowercase()

    /** Ein JSON-String-Feld (mit Unescaping der üblichen Sequenzen). */
    private fun jsonString(json: String, field: String): String? {
        val m = Regex("\"" + Regex.escape(field) + "\"\\s*:\\s*\"((?:[^\"\\\\]|\\\\.)*)\"").find(json)
            ?: return null
        return unescapeJson(m.groupValues[1])
    }

    private fun unescapeJson(s: String): String {
        if (!s.contains('\\')) return s
        val out = StringBuilder(s.length)
        var i = 0
        while (i < s.length) {
            val c = s[i]
            if (c != '\\' || i == s.length - 1) {
                out.append(c); i++; continue
            }
            when (val n = s[i + 1]) {
                'n' -> { out.append('\n'); i += 2 }
                't' -> { out.append('\t'); i += 2 }
                'r' -> { out.append('\r'); i += 2 }
                'b' -> { out.append('\b'); i += 2 }
                'f' -> { out.append('\u000C'); i += 2 }
                '"' -> { out.append('"'); i += 2 }
                '\\' -> { out.append('\\'); i += 2 }
                '/' -> { out.append('/'); i += 2 }
                'u' -> {
                    val hex = s.substring(i + 2, minOf(i + 6, s.length))
                    val cp = hex.toIntOrNull(16)
                    if (cp != null) { out.append(cp.toChar()); i += 6 } else { out.append(n); i += 2 }
                }
                else -> { out.append(n); i += 2 }
            }
        }
        return out.toString()
    }

    /** Minimales HTML-Entity-Decoding (Reddit-Titel enthalten oft &amp;). */
    private fun decodeEntities(text: String): String = text
        .replace("&amp;", "&")
        .replace("&lt;", "<")
        .replace("&gt;", ">")
        .replace("&quot;", "\"")
        .replace("&#39;", "'")
        .replace("&#x27;", "'")
        .replace("&apos;", "'")
        .replace("&nbsp;", " ")
}

/** Prozent-Kodierung für den Query-Teil (plattformunabhängig). */
private fun String.encodeUtf8(): String {
    val sb = StringBuilder(length + 16)
    for (b in this.encodeToByteArray()) {
        val v = b.toInt() and 0xFF
        val c = v.toChar()
        if (c.isLetterOrDigit() && v < 128 || c in "-._~") {
            sb.append(c)
        } else {
            sb.append('%').append(HEX[v shr 4]).append(HEX[v and 0x0F])
        }
    }
    return sb.toString()
}

private const val HEX = "0123456789ABCDEF"
