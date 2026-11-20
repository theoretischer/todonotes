package com.earendil.todonotes.ui

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.LinkAnnotation
import com.earendil.todonotes.ui.components.appendLinkified
import kotlin.test.Test
import kotlin.test.assertEquals

class LinkifyTest {

    private fun build(text: String): AnnotatedString {
        val b = AnnotatedString.Builder()
        b.appendLinkified(text, Color.Blue)
        return b.toAnnotatedString()
    }

    @Test
    fun plain_text_unchanged() {
        val a = build("Hallo Welt ohne Link")
        assertEquals("Hallo Welt ohne Link", a.text)
    }

    @Test
    fun url_becomes_link() {
        val a = build("Siehe https://example.com/page")
        assertEquals("Siehe https://example.com/page", a.text)
        val links = a.getLinkAnnotations(0, a.text.length)
        assertEquals(1, links.size)
        assertEquals("https://example.com/page", (links[0].item as LinkAnnotation.Url).url)
    }

    @Test
    fun trailing_punctuation_stays_in_text_but_not_in_url() {
        val a = build("Siehe https://example.com/page.")
        // Punkt bleibt im TEXT (Satzende), URL ohne Punkt:
        assertEquals("Siehe https://example.com/page.", a.text)
        val links = a.getLinkAnnotations(0, a.text.length)
        assertEquals(1, links.size)
        assertEquals("https://example.com/page", (links[0].item as LinkAnnotation.Url).url)
    }

    @Test
    fun multiple_links() {
        val a = build("a https://a.de und http://b.org b")
        assertEquals("a https://a.de und http://b.org b", a.text)
        assertEquals(2, a.getLinkAnnotations(0, a.text.length).size)
    }

    @Test
    fun balanced_parens_stay_in_url() {
        val a = build("Wiki https://de.wikipedia.org/wiki/Computer_(Computer)")
        assertEquals("Wiki https://de.wikipedia.org/wiki/Computer_(Computer)", a.text)
        val links = a.getLinkAnnotations(0, a.text.length)
        assertEquals(1, links.size)
        assertEquals(
            "https://de.wikipedia.org/wiki/Computer_(Computer)",
            (links[0].item as LinkAnnotation.Url).url
        )
    }

    @Test
    fun unbalanced_close_paren_is_stripped_from_url() {
        val a = build("Siehe (https://example.com/x).")
        val links = a.getLinkAnnotations(0, a.text.length)
        assertEquals(1, links.size)
        assertEquals("https://example.com/x", (links[0].item as LinkAnnotation.Url).url)
        // Klammer + Punkt bleiben im Text:
        assertEquals("Siehe (https://example.com/x).", a.text)
    }
}