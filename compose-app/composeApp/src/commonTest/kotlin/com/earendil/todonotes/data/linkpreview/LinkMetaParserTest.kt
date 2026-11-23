package com.earendil.todonotes.data.linkpreview

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * F7 Link-Vorschau: Open-Graph-Parser (rein, ohne Netzwerk).
 */
class LinkMetaParserTest {

    @Test
    fun `liest og-Tags in beliebiger Attribut-Reihenfolge`() {
        val html = """
            <html><head>
              <meta content="Ein Titel" property="og:title">
              <meta property="og:description" content="Beschreibung hier">
              <meta property='og:site_name' content='Beispiel'>
              <meta property="og:image" content="https://cdn.example.com/i.png">
            </head><body>...</body></html>
        """.trimIndent()
        val meta = LinkMetaParser.parse(html)
        assertEquals("Ein Titel", meta.title)
        assertEquals("Beschreibung hier", meta.description)
        assertEquals("Beispiel", meta.siteName)
        assertEquals("https://cdn.example.com/i.png", meta.imageUrl)
    }

    @Test
    fun `faellt auf title-Tag zurueck wenn og fehlt`() {
        val html = """
            <html><head>
              <title>  Reddit – Das Herz des Internets  </title>
              <meta name="description" content="Mit Beschreibung">
            </head></html>
        """.trimIndent()
        val meta = LinkMetaParser.parse(html)
        assertEquals("Reddit – Das Herz des Internets", meta.title)
        assertEquals("Mit Beschreibung", meta.description)
    }

    @Test
    fun `twitter-Tags als Fallback`() {
        val html = """
            <head>
              <meta name="twitter:title" content="Tweet-Titel">
              <meta name="twitter:description" content="Tweet-Text">
              <meta name="twitter:image" content="https://x.com/a.jpg">
            </head>
        """.trimIndent()
        val meta = LinkMetaParser.parse(html)
        assertEquals("Tweet-Titel", meta.title)
        assertEquals("Tweet-Text", meta.description)
        assertEquals("https://x.com/a.jpg", meta.imageUrl)
    }

    @Test
    fun `löst HTML-Entities auf`() {
        val html = """<head><meta property="og:title" content="Tom &amp; Jerry &quot;Klassiker&quot;"></head>"""
        assertEquals("Tom & Jerry \"Klassiker\"", LinkMetaParser.parse(html).title)
    }

    @Test
    fun `og hat Vorrang vor title-Tag`() {
        val html = """
            <head>
              <title>Fallback-Titel</title>
              <meta property="og:title" content="OG-Titel">
            </head>
        """.trimIndent()
        assertEquals("OG-Titel", LinkMetaParser.parse(html).title)
    }

    @Test
    fun `kaputtes HTML crasht nicht`() {
        val meta = LinkMetaParser.parse("<head><meta property=\"og:title\" content=\"Offen")
        assertEquals("", meta.title)
    }

    @Test
    fun `nur im head-Bereich suchen - Body-Spam ignorieren`() {
        // Ein og:image im Body (z. B. in einem Code-Snippet) darf nicht zählen.
        // (Der Parser nimmt den ERSTEN Treffer — hier korrekt der head-Wert.)
        val html = """
            <head><meta property="og:image" content="https://gut.example/a.png"></head>
            <body><meta property="og:image" content="https://boese.example/b.png"></body>
        """.trimIndent()
        assertEquals("https://gut.example/a.png", LinkMetaParser.parse(html).imageUrl)
    }

    @Test
    fun `relative Bild-URLs werden absolut`() {
        assertEquals(
            "https://reddit.com/img/x.png",
            LinkMetaParser.resolveUrl("https://reddit.com/r/foo", "/img/x.png")
        )
        assertEquals(
            "https://cdn.example.com/x.png",
            LinkMetaParser.resolveUrl("https://reddit.com/r/foo", "//cdn.example.com/x.png")
        )
        assertEquals(
            "https://reddit.com/x.png",
            LinkMetaParser.resolveUrl("https://reddit.com/r/foo", "x.png")
        )
        // Absolute URLs bleiben unverändert
        assertEquals(
            "https://other.example/y.png",
            LinkMetaParser.resolveUrl("https://reddit.com/r/foo", "https://other.example/y.png")
        )
    }

    @Test
    fun `Domain ohne www`() {
        assertEquals("reddit.com", LinkMetaParser.domainOf("https://www.reddit.com/r/foo"))
        assertEquals("youtube.com", LinkMetaParser.domainOf("https://youtube.com/watch?v=1"))
        assertEquals("", LinkMetaParser.domainOf("kein-link"))
    }

    @Test
    fun `Fragment wird fuer den Cache normalisiert`() {
        assertEquals("https://x.com/a", LinkMetaParser.normalize("https://x.com/a#section"))
        assertEquals("https://x.com/a", LinkMetaParser.normalize("https://x.com/a"))
    }

    @Test
    fun `leerer Content wird verworfen`() {
        val html = """<head><meta property="og:title" content="   "></head>"""
        val meta = LinkMetaParser.parse(html)
        assertTrue(meta.title.isBlank())
    }
}
