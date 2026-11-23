package com.earendil.todonotes.ui.components

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.LinkAnnotation
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLinkStyles
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.buildAnnotatedString

/**
 * URL-Klickbarkeit für Texte (M12): erkennt http(s)-Links im Text und
 * macht sie anklickbar ([LinkAnnotation.Url]). Text öffnet Links
 * automatisch über den Default-UriHandler (Android: Intent, Desktop:
 * Browser, Web: window.open).
 *
 * Angewandt auf: Todo-Titel/Notizen, Chat-Nachrichten, Verlauf,
 * Notiz-Lesemodus (dort via [appendLinkified] + eigene Tap-Logik,
 * weil der Lesemodus-Tap-Handler den Klick sonst konsumiert).
 * Der Edit-Modus zeigt bewusst den Roh-Text — dort wird getippt.
 */

private val URL_REGEX = Regex("""https?://\S+""")

/** Satzzeichen, die bei URL-Ende nicht mehr zur URL gehören (Satzende). */
private val TRAILING_PUNCTUATION = ".,;:!?…"

data class UrlMatch(val range: IntRange, val url: String)

/** Findet URLs in [text] (Satzzeichen am Ende abgestrippt). */
internal fun findUrls(text: String): List<UrlMatch> =
    URL_REGEX.findAll(text).mapNotNull { match ->
        var url = match.value
        while (url.isNotEmpty() && url.last() in TRAILING_PUNCTUATION) {
            url = url.dropLast(1)
        }
        // Unbalancierte Klammern abstrippten (Wikipedia "(Computer)"-URLs!).
        while (url.count { it == ')' } > url.count { it == '(' }) {
            url = url.dropLast(1)
        }
        if (url.isEmpty()) null else UrlMatch(match.range, url)
    }.toList()

/** Baut den [text] mit klickbaren Links in [linkColor].
 *  [underline]: Link unterstreichen (Chat — Extra-Kontrast auf der Blase). */
fun linkified(text: String, linkColor: Color, underline: Boolean = false): AnnotatedString =
    buildAnnotatedString { appendLinkified(text, linkColor, underline = underline) }

/**
 * Hängt [text] an den Builder an, http(s)-URLs darin als klickbare Links
 * in [linkColor]. [baseStyle] gilt für den Nicht-Link-Text (z. B. fett
 * aus einem Inline-Stil), [onLink] liefert die (Range im [text], URL)
 * zurück — für Tap-Handling, wenn ein eigener pointerInput die Taps
 * konsumiert (Notiz-Lesemodus).
 */
fun AnnotatedString.Builder.appendLinkified(
    text: String,
    linkColor: Color,
    baseStyle: SpanStyle? = null,
    underline: Boolean = false,
    onLink: ((UrlMatch) -> Unit)? = null
) {
    var pos = 0
    for (match in findUrls(text)) {
        if (match.range.first > pos) {
            val before = text.substring(pos, match.range.first)
            if (baseStyle != null) pushStyle(baseStyle)
            append(before)
            if (baseStyle != null) pop()
        }
        pushLink(
            LinkAnnotation.Url(
                url = match.url,
                styles = TextLinkStyles(
                    style = SpanStyle(
                        color = linkColor,
                        // Chat (M14k): Unterstreichung — Links klarer abheben.
                        textDecoration = if (underline) TextDecoration.Underline
                                         else baseStyle?.textDecoration,
                        // Basis-Stil (fett etc.) bleibt über die Link-Farbe erhalten.
                        fontStyle = baseStyle?.fontStyle,
                        fontWeight = baseStyle?.fontWeight,
                        fontFamily = baseStyle?.fontFamily
                    )
                )
            )
        )
        append(match.url)
        pop()
        onLink?.invoke(match)
        // Abgestrippte Satzzeichen (Regex-Match ≥ URL: „.../page." → Link
        // bis "/page") sind KEIN Teil der URL — aber Teil des Textes! Ohne
        // dieses append w\u00fcrden Satzzeichen hinter Links verschluckt.
        if (match.range.last - match.range.first + 1 > match.url.length) {
            val stripped = text.substring(match.range.first + match.url.length, match.range.last + 1)
            if (baseStyle != null) pushStyle(baseStyle)
            append(stripped)
            if (baseStyle != null) pop()
        }
        pos = match.range.last + 1
    }
    if (pos < text.length) {
        val rest = text.substring(pos)
        if (baseStyle != null) pushStyle(baseStyle)
        append(rest)
        if (baseStyle != null) pop()
    }
}