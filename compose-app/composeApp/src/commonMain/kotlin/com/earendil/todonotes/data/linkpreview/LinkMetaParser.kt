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
 * ACHTUNG: NICHT auf den <head> beschränken! YouTube z. B. setzt eine
 * Platzhalter-<title>YouTube</title> in den Head und die ECHTEN
 * og-Tags erst bei ~Zeichen 680.000 (hinter einem riesigen Inline-
 * Script) — ein Head-/64KB-Limit findet dort nur „YouTube" (Nutzer-
 * Report). Deshalb wird das ganze Dokument gescannt.
 */
object LinkMetaParser {

    data class Meta(
        val title: String = "",
        val description: String = "",
        val siteName: String = "",
        val imageUrl: String = ""
    )

    /** Meta-Tags: <meta property="og:title" content="..."> — Attribut-
     *  Reihenfolge ist beliebig, Quotes können ' oder " sein. */
    /** Das Dollar-Zeichen — als escape geschrieben, damit Kotlin-Raw-
     *  Strings drumherum nicht verwirren. */
    private val DOLLAR = '\u0024'

    /** Literale Escape-Schreibweise des Dollars, die unaufgelöst in
     *  manchen SPAs steht: Backslash + "u0024" (case-insensitiv). */
    private const val DOLLAR_ESCAPE = "\\" + "u0024"

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

        // Alle meta-Tags einsammeln: key (og:title etc.) → content
        val props = LinkedHashMap<String, String>()
        for (m in META_TAG.findAll(html)) {
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
            titleFromTag(html)
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

    /**
     * Fallback-Titel aus <title>-Tags: der LÄNGSTE gewinnt.
     *
     * Grund (Nutzer-Report YouTube): SPAs setzen oft einen Platzhalter
     * wie <title>YouTube</title> und den echten Titel erst später —
     * der erste Treffer ist dann Müll. Platzhalter sind kurz, echte
     * Titel lang. Unaufgelöste JS-Templates im Titel fliegen raus.
     */
    private fun titleFromTag(html: String): String =
        TITLE_TAG.findAll(html)
            .map { decodeEntities(it.groupValues[1]).trim() }
            .filter { it.isNotEmpty() && !isTemplate(it) }
            .maxByOrNull { it.length }
            .orEmpty()

    /**
     * Unaufgelöster JS-Template-Platzhalter im Titel? Erkannt wird ein `{`
     * direkt nach einem Dollar — sowohl als echtes `$` (unescaptes JS)
     * als auch als literale Escape-Sequenz (Backslash, u, 0, 0, 2, 4),
     * die manche Server unaufgelöst ausliefern (z. B. YouTube-SPA).
     */
    private fun isTemplate(title: String): Boolean {
        var i = title.indexOf('{')
        while (i > 0) {
            if (title[i - 1] == DOLLAR) return true
            // Literale Escape-Sequenz vor der Klammer?
            if (i >= 6 && title.substring(i - 6, i).equals(DOLLAR_ESCAPE, ignoreCase = true)) {
                return true
            }
            i = title.indexOf('{', i + 1)
        }
        return false
    }

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
