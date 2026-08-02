package com.earendil.todonotes.data.richtext

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Unit-Tests für den Plain-Text-Body (Block F5 redsign).
 *
 * Fokus: Präfix-Erkennung, Zeilen-Splitting (toLines/fromLines) und
 * Migration von altem Block-JSON.
 */
class NoteTextBodyTest {

    @Test
    fun stripPrefix_removesAllListTypes() {
        assertEquals("Hallo", NoteTextBody.stripPrefix("- Hallo"))
        assertEquals("Hallo", NoteTextBody.stripPrefix("1. Hallo"))
        assertEquals("Hallo", NoteTextBody.stripPrefix("[ ] Hallo"))
        assertEquals("Hallo", NoteTextBody.stripPrefix("[x] Hallo"))
        assertEquals("Hallo", NoteTextBody.stripPrefix("→ Hallo"))
        assertEquals("Hallo", NoteTextBody.stripPrefix("Hallo"))
    }

    @Test
    fun detectListType_recognizesTypes() {
        assertEquals(ListType.BULLET, NoteTextBody.detectListType("- abc"))
        assertEquals(ListType.ORDERED, NoteTextBody.detectListType("3. abc"))
        assertEquals(ListType.CHECKBOX, NoteTextBody.detectListType("[ ] abc"))
        assertEquals(ListType.CHECKBOX, NoteTextBody.detectListType("[x] abc"))
        assertEquals(ListType.ARROW, NoteTextBody.detectListType("→ abc"))
        assertNull(NoteTextBody.detectListType("abc"))
    }

    @Test
    fun toggleCheckbox_switchesBetweenStates() {
        assertEquals("[x] abc", NoteTextBody.toggleCheckbox("[ ] abc", 0))
        assertEquals("[ ] abc", NoteTextBody.toggleCheckbox("[x] abc", 0))
    }

    @Test
    fun setListPrefix_replacesExistingPrefix() {
        val text = "- alt\nneue Zeile"
        val result = NoteTextBody.setListPrefix(text, 0, ListType.ORDERED, 1)
        assertEquals("1. alt\nneue Zeile", result)
    }

    @Test
    fun setListPrefix_addsPrefixToPlainLine() {
        val text = "normal\nnoch was"
        val result = NoteTextBody.setListPrefix(text, 0, ListType.BULLET)
        assertEquals("- normal\nnoch was", result)
    }

    @Test
    fun toLines_splitsAndDetectsMixedContent() {
        val text = "Zeile 1\n- Bullet\nNummeriert Plural unterbrochen\n2. Relativ\n[x] Erledigt\nZeile 2"
        // "2. Relativ" enthält zwar '2. ' mitten im Text, aber kein
        // listen-präfix am Zeilenanfang -> plain. „Nummeriert Plural …“
        // ist plain. Sauberer Test:
        val cleanText = "Zeile 1\n- Bullet\n1. Nummeriert\n[x] Erledigt\nZeile 2"
        val lines = NoteTextBody.toLines(cleanText)
        assertEquals(5, lines.size)

        assertEquals("Zeile 1", lines[0].content)
        assertNull(lines[0].type)

        assertEquals("Bullet", lines[1].content)
        assertEquals(ListType.BULLET, lines[1].type)

        assertEquals("Nummeriert", lines[2].content)
        assertEquals(ListType.ORDERED, lines[2].type)
        assertEquals(1, lines[2].number)

        assertEquals("Erledigt", lines[3].content)
        assertEquals(ListType.CHECKBOX, lines[3].type)
        assertTrue(lines[3].checked)

        assertEquals("Zeile 2", lines[4].content)
        assertNull(lines[4].type)
    }

    @Test
    fun fromLines_roundtripsWithPrefixes() {
        val lines = listOf(
            NoteLine("Zeile 1", null),
            NoteLine("Bullet", ListType.BULLET),
            NoteLine("Zweites", ListType.BULLET),
            NoteLine("Abhaken", ListType.CHECKBOX, checked = true),
            NoteLine("Ende", null)
        )
        val text = NoteTextBody.fromLines(lines)
        assertEquals(
            "Zeile 1\n- Bullet\n- Zweites\n[x] Abhaken\nEnde",
            text
        )
    }

    @Test
    fun fromLines_orderedNumberingIsSequential() {
        val lines = listOf(
            NoteLine("a", ListType.ORDERED),
            NoteLine("b", ListType.ORDERED),
            NoteLine("c", ListType.ORDERED)
        )
        assertEquals("1. a\n2. b\n3. c", NoteTextBody.fromLines(lines))
    }

    @Test
    fun toLines_thenFromLines_isIdentity() {
        val original = "Einführung\n- Punkt eins\n- Punkt zwei\n1. Erster\n2. Zweiter\n[x] Check\nganz zum Schluss"
        val lines = NoteTextBody.toLines(original)
        val rebuilt = NoteTextBody.fromLines(lines)
        assertEquals(original, rebuilt)
    }

    @Test
    fun emptyText_hasOneEmptyLine() {
        val lines = NoteTextBody.toLines("")
        assertEquals(1, lines.size)
        assertEquals("", lines[0].content)
        assertNull(lines[0].type)
    }

    @Test
    fun migrateFromBlocks_oldJson_becomesPlainText() {
        val oldJson = NoteBodyJson.encode(
            NoteBody(blocks = listOf(
                Block.Paragraph(listOf(Segment("Überschrift"))),
                Block.ListBlock(
                    items = listOf(
                        ListItem(segments = listOf(Segment("Punkt 1"))),
                        ListItem(segments = listOf(Segment("Punkt 2"))),
                        ListItem(segments = listOf(Segment("Abgehakt")), checked = true)
                    ),
                    type = ListType.BULLET
                ),
                Block.ListBlock(
                    items = listOf(
                        ListItem(segments = listOf(Segment("Betet"))),
                        ListItem(segments = listOf(Segment("Gebetet")))
                    ),
                    type = ListType.CHECKBOX
                )
            ))
        )
        val plain = NoteTextBody.migrateFromBlocks(oldJson)
        assertFalse(plain.startsWith("{"))
        val lines = NoteTextBody.toLines(plain)
        // Zeile 0: Paragraph
        assertEquals("Überschrift", lines[0].content)
        assertNull(lines[0].type)
        // Zeilen 1-3: BULLET-Liste (3 Items)
        assertEquals("Punkt 1", lines[1].content)
        assertEquals(ListType.BULLET, lines[1].type)
        assertEquals("Punkt 2", lines[2].content)
        assertEquals("Abgehakt", lines[3].content)
        // Zeilen 4-5: CHECKBOX-Liste
        assertEquals("Betet", lines[4].content)
        assertEquals(ListType.CHECKBOX, lines[4].type)
        assertFalse(lines[4].checked)
        assertEquals("Gebetet", lines[5].content)
        assertEquals(ListType.CHECKBOX, lines[5].type)
        assertFalse(lines[5].checked)
    }
}
