package com.earendil.todonotes.data.linkpreview

/**
 * Minimaler Open-Graph-/Meta-Tag-Parser (F7 Link-Vorschau).
 *
 * Bewusst KEIN jsoup: das ist JVM-only und die App läuft auch als
 * Wasm/Desktop-Build → gemeinsame Regex-Lösung in commonMain.
 *
 * Gesucht werden (in dieser Priorität):
 *  - Titel:       og:title → twitter:title → <title>
 *  - Beschreibung: og:description → twitter:description → description
 *  - Bild:        og:image → og:image:url → twitter:image → twitter:image:src
 *  - Site-Name:   og:site_name (sonst aus der URL-Host abgeleitet)
 *
 * NUR der <head> wird durchsucht (begrenzt auf die ersten 64 KB) —
 * Body-Spam und Riesen-Seiten sind damit egal.
 */
object LinkMetaParser {

    /** Obergrenze für den HTML-Ausschnitt, den wir ansehen. */
    private const val MAX_HTML_CHARS = 64_000

    data class Meta(
        val title: String = "",
        val description: String = "",
        val siteName: String = "",
        val imageUrl: String = ""
    )

    /** Meta-Tags: <meta property="og:title" content="..."> — Attribut-
     *  Reihenfolge ist beliebig, Quotes können ' oder " sein. */
    private val META_TAG = Regex(
        """<meta\s+([^>]*?)/?>""",
        RegexOption.IGNORE_CASE
    )
    private val ATTR = Regex(
        """(property|name|content)\s*=\s*("([^"]*)"|'([^']*)')""",
        RegexOption.IGNORE_CASE
    )
    private val TITLE_TAG = Regex(
        """<title[^>]*>(.*?)</title>""",
        setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL)
    )

    fun parse(html: String): Meta {
        val head = html.take(MAX_HTML_CHARS)

        // Alle meta-Tags einsammeln: key (og:title etc.) → content
        val props = LinkedHashMap<String, String>()
        for (m in META_TAG.findAll(head)) {
            var key: String? = null
            var content: String? = null
            for (a in ATTR.findAll(m.groupValues[1])) {
                val attrName = a.groupValues[1].lowercase()
                val value = a.groupValues[3].ifEmpty { a.groupValues[4] }
                when (attrName) {
                    "property", "name" -> if (key == null) key = value.lowercase()
                    "content" -> content = value
                }
            }
            if (key != null && content != null && propertiesToKeep(key!!)) {
                // Erster Treffer gewinnt (og vor twitter, s. Reihenfolge unten).
                if (!props.containsKey(key!!)) {
                    props[key!!] = decodeEntities(content!!).trim()
                }
            }
        }

        val siteName = props["og:site_name"].orEmpty()
        val title = firstNonBlank(
            props["og:title"],
            props["twitter:title"],
            titleFromTag(head)
        )
        val description = firstNonBlank(
            props["og:description"],
            props["twitter:description"],
            props["description"]
        )
        val imageUrl = firstNonBlank(
            props["og:image"],
            props["og:image:url"],
            props["og:image:secure_url"],
            props["twitter:image"],
            props["twitter:image:src"]
        )
        return Meta(title, description, siteName, imageUrl)
    }

    /** Nur die Tags, die wir wirklich brauchen — hält die Map klein. */
    private fun propertiesToKeep(key: String): Boolean =
        key == "og:title" || key == "og:description" || key == "og:site_name" ||
            key == "og:image" || key == "og:image:url" || key == "og:image:secure_url" ||
            key == "twitter:title" || key == "twitter:description" ||
            key == "twitter:image" || key == "twitter:image:src" ||
            key == "description"

    private fun titleFromTag(head: String): String =
        TITLE_TAG.find(head)?.groupValues?.get(1)?.let { decodeEntities(it).trim() }.orEmpty()

    private fun firstNonBlank(vararg candidates: String?): String =
        candidates.firstOrNull { !it.isNullOrBlank() }?.trim().orEmpty()

    /** Häufige HTML-Entities auflösen (der Rest bleibt roh — besser als
     *  kaputte Anzeige irgendwelcher Sonderfälle). */
    private fun decodeEntities(text: String): String =
        text.replace("&amp;", "&")
            .replace("&lt;", "<")
            .replace("&gt;", ">")
            .replace("&quot;", "\"")
            .replace("&#39;", "'")
            .replace("&apos;", "'")
            .replace("&nbsp;", " ")
            .replace("&#x27;", "'")
            .replace("&hellip;", "…")
            .replace("&mdash;", "—")
            .replace("&ndash;", "–")
            .replace(Regex("""\s+"""), " ")

    /** Relative Bild-URLs (z. B. "/img/x.png") zur absoluten machen. */
    fun resolveUrl(baseUrl: String, maybeRelative: String): String {
        if (maybeRelative.isBlank()) return ""
        if (maybeRelative.startsWith("http://") || maybeRelative.startsWith("https://")) {
            return maybeRelative
        }
        val origin = originOf(baseUrl) ?: return ""
        return when {
            maybeRelative.startsWith("//") -> "https:" + maybeRelative
            maybeRelative.startsWith("/") -> origin + maybeRelative
            else -> origin + "/" + maybeRelative
        }
    }

    private fun originOf(url: String): String? {
        val match = Regex("""^(https?://[^/]+)""").find(url) ?: return null
        return match.groupValues[1]
    }

    /** Domain ohne www. für die Anzeige („reddit.com"). */
    fun domainOf(url: String): String =
        Regex("""^https?://(?:www\.)?([^/?#]+)""").find(url)
            ?.groupValues?.get(1)
            ?.lowercase()
            .orEmpty()

    /** URL für den Cache normalisieren: Fragment weg (sonst teilen
     *  dieselbe Seite mehrere Cache-Einträge). */
    fun normalize(url: String): String = url.substringBefore('#')
}
