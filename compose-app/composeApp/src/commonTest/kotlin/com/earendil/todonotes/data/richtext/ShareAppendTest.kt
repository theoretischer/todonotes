package com.earendil.todonotes.data.richtext

import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * ShareAppend: geteilten Text ans Notizende anhängen.
 * Nutzer-Regeln (Zeilen-Sicht) → Block-Modell:
 * - "[ ]" leer            → Text HINTER das Präfix (Item füllen)
 * - "[ ] hund"            → neue Zeile "[ ] " (neues Item, gleicher Typ)
 * - "hund"                → neue Zeile ohne Präfix (Paragraph)
 * - mehrzeilig           → Zeile für Zeile durch dieselben Regeln
 */
class ShareAppendTest {

    private fun body(vararg blocks: Block) = NoteBody(blocks = blocks.toList())

    private fun para(text: String) = Block.Paragraph(listOf(Segment(text)))

    // Helfer: Body → "Zeilen"-Sicht (wie NoteTextBody.fromLines rueckwaerts):
    // ListBlock → Items mit Praefix, Paragraph → Text.
    private fun Block.toLineStrings(): List<String> = when (this) {
        is Block.Paragraph -> listOf(segments.joinToString("") { it.text })
        is Block.ListBlock -> items.map {
            when (type) {
                ListType.CHECKBOX -> (if (it.checked) "[x] " else "[ ] ") +
                        it.segments.joinToString("") { s -> s.text }
                ListType.BULLET -> "- " + it.segments.joinToString("") { s -> s.text }
                ListType.ARROW -> "→ " + it.segments.joinToString("") { s -> s.text }
                ListType.ORDERED -> "1. " + it.segments.joinToString("") { s -> s.text }
            }
        }
        is Block.ImageBlock -> listOf("[bild]")
    }

    private fun NoteBody.toLines(): List<String> = blocks.flatMap { it.toLineStrings() }

    @Test
    fun `leeres Checkbox-Item wird hinter dem Praefix gefuellt`() {
        // Letzte Zeile "[ ] " (leer, kein Inhalt) → Text dahinter.
        val b = body(Block.ListBlock(listOf(
            ListItem(listOf(Segment("hund"))),
            ListItem(listOf(Segment("")))
        ), ListType.CHECKBOX))
        val result = appendSharedTextToBody(b, "Gassi gehen")
        assertEquals(listOf("[ ] hund", "[ ] Gassi gehen"), result.toLines())
    }

    @Test
    fun `Checkbox-Item mit Inhalt bekommt neue Zeile gleichen Typs`() {
        // Letzte Zeile "[ ] hund" → neue Zeile "[ ] Gassi gehen".
        val b = body(Block.ListBlock(
            listOf(ListItem(listOf(Segment("hund")))),
            ListType.CHECKBOX
        ))
        val result = appendSharedTextToBody(b, "Gassi gehen")
        assertEquals(listOf("[ ] hund", "[ ] Gassi gehen"), result.toLines())
    }

    @Test
    fun `checked-Status des gefuellten Items bleibt`() {
        // Leeres ABGEHAKTES Item "[x] " → Text rein, checked bleibt True
        // (Status-Regel des Nutzers: nur Inhalt einfuegen).
        val b = body(Block.ListBlock(
            listOf(ListItem(listOf(Segment("")), checked = true)),
            ListType.CHECKBOX
        ))
        val result = appendSharedTextToBody(b, "erledigt?!")
        assertEquals(listOf("[x] erledigt?!"), result.toLines())
    }

    @Test
    fun `letzter Block ist Paragraph MIT Inhalt → neuer Paragraph`() {
        // Letzte Zeile "hund" → neue Zeile OHNE Praefix.
        val b = body(para("hund"))
        val result = appendSharedTextToBody(b, "Gassi gehen")
        assertEquals(listOf("hund", "Gassi gehen"), result.toLines())
    }

    @Test
    fun `letzter Block ist LEERER Paragraph → wird gefuellt`() {
        val b = body(para(""))
        val result = appendSharedTextToBody(b, "hallo")
        assertEquals(listOf("hallo"), result.toLines())
    }

    @Test
    fun `leere Notiz → neuer Paragraph`() {
        val result = appendSharedTextToBody(EMPTY_BODY, "hallo")
        assertEquals(listOf("hallo"), result.toLines())
    }

    @Test
    fun `letzter Block ist Bild → neuer Paragraph dahinter`() {
        val b = body(
            para("text"),
            Block.ImageBlock("img1", 100, 100)
        )
        val result = appendSharedTextToBody(b, "anhaengen")
        assertEquals(listOf("text", "[bild]", "anhaengen"), result.toLines())
    }

    @Test
    fun `mehrzeiliger geteilter Text geht Zeile fuer Zeile`() {
        // Zeile 1 fuellt das leere Item, Zeile 2+ werden regulär angehängt.
        val b = body(Block.ListBlock(
            listOf(ListItem(listOf(Segment("")), checked = false)),
            ListType.CHECKBOX
        ))
        val result = appendSharedTextToBody(b, "eins\n\nzwei\r\ndrei")
        assertEquals(listOf("[ ] eins", "[ ] zwei", "[ ] drei"), result.toLines())
    }

    @Test
    fun `bullet-liste kriegt neue bullet`() {
        val b = body(Block.ListBlock(
            listOf(ListItem(listOf(Segment("alt")))),
            ListType.BULLET
        ))
        val result = appendSharedTextToBody(b, "neu")
        assertEquals(listOf("- alt", "- neu"), result.toLines())
    }

    @Test
    fun `numerierung zählt implizit weiter`() {
        // ORDERED: Nummern sind Render-Sache, nicht im Item gespeichert —
        // ein neues Item haengt sich einfach an.
        val b = body(Block.ListBlock(
            listOf(ListItem(listOf(Segment("erstens")))),
            ListType.ORDERED
        ))
        val result = appendSharedTextToBody(b, "zweitens")
        assertEquals(2, (result.blocks.last() as Block.ListBlock).items.size)
    }

    @Test
    fun `komplett leerer geteilter Text laesst Body unveraendert`() {
        val b = body(para("hund"))
        val result = appendSharedTextToBody(b, "  \n \n")
        assertEquals(listOf("hund"), result.toLines())
        assertTrue(result === b || result == b)
    }
}