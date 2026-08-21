package com.earendil.todonotes.data.richtext

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

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
        assertEquals("Zeile 1\n- Bullet\n- Zweites\n[x] Abhaken\nEnde", text)
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
        assertEquals("Überschrift", lines[0].content)
        assertNull(lines[0].type)
        assertEquals("Punkt 1", lines[1].content)
        assertEquals(ListType.BULLET, lines[1].type)
        assertEquals("Punkt 2", lines[2].content)
        assertEquals("Abgehakt", lines[3].content)
        assertEquals("Betet", lines[4].content)
        assertEquals(ListType.CHECKBOX, lines[4].type)
        assertFalse(lines[4].checked)
        assertEquals("Gebetet", lines[5].content)
        assertEquals(ListType.CHECKBOX, lines[5].type)
        assertFalse(lines[5].checked)
    }

    @Test
    fun parseInlineStyles_removesBoldMarkers() {
        val segs = NoteTextBody.parseInlineStyles("Hallo **fett** Welt")
        assertEquals(3, segs.size)
        assertEquals("Hallo ", segs[0].text)
        assertNull(segs[0].style)
        assertEquals("fett", segs[1].text)
        assertEquals(NoteTextBody.InlineStyle.BOLD, segs[1].style)
        assertEquals(" Welt", segs[2].text)
        assertNull(segs[2].style)
    }

    @Test
    fun parseInlineStyles_removesItalicAndUnderline() {
        val segs = NoteTextBody.parseInlineStyles("*kursiv* und __unter__")
        assertEquals(3, segs.size)
        assertEquals(NoteTextBody.InlineStyle.ITALIC, segs[0].style)
        assertEquals("kursiv", segs[0].text)
        assertEquals(NoteTextBody.InlineStyle.UNDERLINE, segs[2].style)
        assertEquals("unter", segs[2].text)
    }

    @Test
    fun parseInlineStyles_plainTextUntouched() {
        val segs = NoteTextBody.parseInlineStyles("ganz normaler Text")
        assertEquals(1, segs.size)
        assertNull(segs[0].style)
        assertEquals("ganz normaler Text", segs[0].text)
    }

    @Test
    fun toggleInlineStyle_addsMarkersAroundSelection() {
        val (text, s, e) = NoteTextBody.toggleInlineStyle("Hallo Welt", 0, 5, NoteTextBody.InlineStyle.BOLD)
        assertEquals("**Hallo** Welt", text)
        assertEquals(2, s)
        assertEquals(7, e)
    }

    @Test
    fun toggleInlineStyle_removesMarkersAgain() {
        val (text, s, e) = NoteTextBody.toggleInlineStyle("**Hallo** Welt", 2, 7, NoteTextBody.InlineStyle.BOLD)
        assertEquals("Hallo Welt", text)
        assertEquals(0, s)
        assertEquals(5, e)
    }

    @Test
    fun visualToRawOffset_mapsAcrossMarkers() {
        assertEquals(7, NoteTextBody.visualToRawOffset("**Hallo** Welt", 5))
        assertEquals(0, NoteTextBody.visualToRawOffset("**Hallo** Welt", 0))
        assertEquals(14, NoteTextBody.visualToRawOffset("**Hallo** Welt", 10))
    }

    @Test
    fun rawToVisualOffset_mapsAcrossMarkers() {
        assertEquals(5, NoteTextBody.rawToVisualOffset("**Hallo** Welt", 7))
        assertEquals(0, NoteTextBody.rawToVisualOffset("**Hallo** Welt", 0))
        assertEquals(10, NoteTextBody.rawToVisualOffset("**Hallo** Welt", 14))
    }
}
