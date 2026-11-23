package com.earendil.todonotes.data.linkpreview

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * F7: Reddit-Sonderweg über den öffentlichen oembed-Endpunkt.
 *
 * Hintergrund: Reddit liefert an Nicht-Browser-Clients Bot-Sperrseiten
 * ohne og-Tags (verifiziert mit Browser-/Discord-/Twitter-UA, .json,
 * api./old.reddit.com, RSS). oembed antwortet dagegen mit JSON.
 */
class RedditOembedTest {

    /** Echte Antwort von reddit.com/oembed (gekürzt auf die Felder). */
    private val realResponse = """{"author_name":"Nonsenseallstar","html":"\u003cblockquote class=\"reddit-embed-bq\" style=\"height:316px\" \u003e
<a href=\"https://www.reddit.com/r/spiritbox/comments/1czx7av/best_store/\">Best store selling sb records and stuff? </a><br> by
<a href=\"https://www.reddit.com/user/Nonsenseallstar/\">u/Nonsenseallstar</a> in
<a href=\"https://www.reddit.com/r/spiritbox/\">spiritbox</a>
</blockquote>","provider_name":"reddit","title":"Best store selling sb records and stuff? ","type":"rich"}"""

    @Test
    fun `parst echtes oembed-JSON`() {
        val meta = assertNotNull(RedditOembed.parse(realResponse))
        assertEquals("Best store selling sb records and stuff?", meta.title)
        assertEquals("u/Nonsenseallstar", meta.description)
        assertEquals("r/spiritbox", meta.siteName)
        // oembed liefert grundsätzlich kein Bild.
        assertEquals("", meta.imageUrl)
    }

    @Test
    fun `erkennt Post-URLs`() {
        assertTrue(RedditOembed.applies("https://www.reddit.com/r/ProgrammerHumor/comments/1czx7av/foo/"))
        assertTrue(RedditOembed.applies("https://reddit.com/r/aww/comments/abc123"))
        assertTrue(RedditOembed.applies("https://old.reddit.com/r/aww/comments/abc123/x/"))
        assertTrue(RedditOembed.applies("https://www.reddit.com/r/ProgrammerHumor/s/AbC123"))
        assertTrue(RedditOembed.applies("https://redd.it/abc123"))
    }

    @Test
    fun `keine Post-URLs werden nicht umgeleitet`() {
        // Subreddit-Startseite: oembed gibt dort 400 (invalid URL value).
        assertFalse(RedditOembed.applies("https://www.reddit.com/r/ProgrammerHumor/"))
        assertFalse(RedditOembed.applies("https://www.reddit.com/"))
        assertFalse(RedditOembed.applies("https://github.com/torvalds/linux"))
    }

    @Test
    fun `endpoint kodiert die URL`() {
        val ep = RedditOembed.endpointFor("https://www.reddit.com/r/aww/comments/abc/")
        assertEquals(
            "https://www.reddit.com/oembed?url=https%3A%2F%2Fwww.reddit.com%2Fr%2Faww%2Fcomments%2Fabc%2F",
            ep
        )
    }

    @Test
    fun `Unicode-Escapes im Titel werden aufgeloest`() {
        val json = """{"author_name":"","title":"so I post this on my door now \u2013 fertig","html":"x"}"""
        assertEquals("so I post this on my door now – fertig", assertNotNull(RedditOembed.parse(json)).title)
    }

    @Test
    fun `HTML-Entities im Titel`() {
        val json = """{"author_name":"x","title":"Tom &amp; Jerry &quot;Klassiker&quot;","html":"x"}"""
        assertEquals("Tom & Jerry \"Klassiker\"", assertNotNull(RedditOembed.parse(json)).title)
    }

    @Test
    fun `HTML fuer die Vorschau wird ignoriert`() {
        // Nur das title-Feld zählt — das html-Feld enthält Markup.
        val meta = assertNotNull(RedditOembed.parse(realResponse))
        assertFalse(meta.title.contains("<"))
    }

    @Test
    fun `Fehlerantworten liefern null`() {
        assertNull(RedditOembed.parse("""{"error":"invalid URL value"}"""))
        assertNull(RedditOembed.parse("""{"title":""}"""))
        assertNull(RedditOembed.parse("kein json"))
    }

    @Test
    fun `ohne Subreddit bleibt reddit als Absender`() {
        val json = """{"title":"Nur ein Titel","html":"","author_name":"jemand"}"""
        assertEquals("reddit", assertNotNull(RedditOembed.parse(json)).siteName)
    }
}
