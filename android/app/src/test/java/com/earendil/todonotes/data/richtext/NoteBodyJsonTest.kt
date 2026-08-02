package com.earendil.todonotes.data.richtext

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Unit-Tests für das Rich-Text-Modell (Block F3).
 *
 * Fokus: Serialisierungs-Roundtrip (das, was in bodyJson landet und
 * wieder raus kommt), Defaults, Listen-Präfixe, Forward-Kompatibilität.
 * Diese Tests laufen als reine JVM-Tests (kein Device nötig).
 */
class NoteBodyJsonTest {

    @Test
    fun emptyBody_roundtripsAsEmptyArray() {
        val json = NoteBodyJson.encode(EMPTY_BODY)
        // NoteBody hat ein blocks-Feld → JSON ist {"blocks":[]}
        val decoded = NoteBodyJson.decode(json)
        assertTrue(decoded.blocks.isEmpty())
    }

    @Test
    fun newBodyWithEmptyParagraph_hasOneParagraphWithEmptySegment() {
        val body = newBodyWithEmptyParagraph()
        assertEquals(1, body.blocks.size)
        val p = body.blocks.first() as Block.Paragraph
        assertEquals(1, p.segments.size)
        assertEquals("", p.segments.first().text)
    }

    @Test
    fun paragraphWithMixedStyles_roundtrips() {
        val body = NoteBody(blocks = listOf(
            Block.Paragraph(listOf(
                Segment("Normal", TextStyle.NONE),
                Segment("Fett", TextStyle.BOLD),
                Segment("Kursiv", TextStyle.ITALIC, fontSize = 18),
                Segment("Unterstrichen", TextStyle.UNDERLINE, color = 0xFFFF0000)
            ))
        ))
        val decoded = NoteBodyJson.decode(NoteBodyJson.encode(body))
        val p = decoded.blocks.first() as Block.Paragraph
        assertEquals(4, p.segments.size)
        assertEquals("Fett", p.segments[1].text)
        assertEquals(TextStyle.BOLD, p.segments[1].style)
        assertEquals(18, p.segments[2].fontSize)
        assertEquals(0xFFFF0000, p.segments[3].color)
        // Defaults nicht explizit im JSON → decode liefert Default
        assertEquals(TextStyle.NONE, p.segments[0].style)
        assertNull(p.segments[0].fontSize)
        assertNull(p.segments[0].color)
    }

    @Test
    fun allListTypes_roundtrip() {
        ListType.entries.forEach { type ->
            val body = NoteBody(blocks = listOf(
                Block.ListBlock(
                    items = listOf(
                        ListItem(segments = listOf(Segment("erstes"))),
                        ListItem(segments = listOf(Segment("zweites")), checked = true)
                    ),
                    type = type
                )
            ))
            val decoded = NoteBodyJson.decode(NoteBodyJson.encode(body))
            val list = decoded.blocks.first() as Block.ListBlock
            assertEquals(type, list.type)
            assertEquals(2, list.items.size)
            assertTrue(list.items[1].checked)
            assertFalse(list.items[0].checked)
        }
    }

    @Test
    fun imageBlock_roundtripsWithOptionalCaption() {
        val withCaption = NoteBody(blocks = listOf(
            Block.ImageBlock("img-1", 800, 600, "Eine Blume")
        ))
        val decoded1 = NoteBodyJson.decode(NoteBodyJson.encode(withCaption))
        val img1 = decoded1.blocks.first() as Block.ImageBlock
        assertEquals("img-1", img1.imageId)
        assertEquals(800, img1.width)
        assertEquals(600, img1.height)
        assertEquals("Eine Blume", img1.caption)

        val withoutCaption = NoteBody(blocks = listOf(
            Block.ImageBlock("img-2", 100, 100)
        ))
        val decoded2 = NoteBodyJson.decode(NoteBodyJson.encode(withoutCaption))
        val img2 = decoded2.blocks.first() as Block.ImageBlock
        assertNull(img2.caption)
    }

    @Test
    fun mixedDocument_roundtripsAsWhole() {
        val body = NoteBody(blocks = listOf(
            Block.Paragraph(listOf(Segment("Überschrift", TextStyle.BOLD, fontSize = 22))),
            Block.Paragraph(listOf(Segment("Einleitung normal."))),
            Block.ListBlock(
                items = listOf(
                    ListItem(segments = listOf(Segment("Punkt 1"))),
                    ListItem(segments = listOf(Segment("Punkt 2", TextStyle.BOLD)), checked = false)
                ),
                type = ListType.BULLET
            ),
            Block.ImageBlock("img-x", 1024, 768, caption = null),
            Block.ListBlock(
                items = listOf(ListItem(segments = listOf(Segment("Erledigt")), checked = true)),
                type = ListType.CHECKBOX
            )
        ))
        val decoded = NoteBodyJson.decode(NoteBodyJson.encode(body))
        assertEquals(5, decoded.blocks.size)
        assertTrue(decoded.blocks[0] is Block.Paragraph)
        assertTrue(decoded.blocks[1] is Block.Paragraph)
        assertTrue(decoded.blocks[2] is Block.ListBlock)
        assertTrue(decoded.blocks[3] is Block.ImageBlock)
        assertTrue(decoded.blocks[4] is Block.ListBlock)
        assertEquals(ListType.BULLET, (decoded.blocks[2] as Block.ListBlock).type)
        assertEquals(ListType.CHECKBOX, (decoded.blocks[4] as Block.ListBlock).type)
    }

    @Test
    fun decode_blankString_returnsEmpty() {
        val body = NoteBodyJson.decode("")
        assertTrue(body.blocks.isEmpty())
    }

    @Test
    fun decode_garbageString_returnsEmptyInsteadOfThrowing() {
        val body = NoteBodyJson.decode("{not valid json")
        assertTrue(body.blocks.isEmpty())
    }

    @Test
    fun decode_unknownBlockType_doesNotThrow() {
        // Forward-Kompatibilität: ein Block mit unbekanntem "type" darf
        // nicht crashen. ignoreUnknownKeys=true hilft, aber der sealed-
        // class-Discriminator könnte trotzdem meckern → sicherstellen
        // dass wir graceful degraded.
        val json = """[{"kind":"unknown_future_block","foo":"bar"}]"""
        val body = NoteBodyJson.decode(json)
        // Entweder leer (unknown skipped) oder der Block fehlt — Hauptsache kein Crash.
        assertNotNull(body)
    }

    @Test
    fun decodeOrNew_emptyBody_returnsBodyWithParagraph() {
        val body = NoteBodyJson.decodeOrNew("[]")
        assertEquals(1, body.blocks.size)
        assertTrue(body.blocks.first() is Block.Paragraph)
    }

    @Test
    fun encode_isDeterministic() {
        val body = NoteBody(blocks = listOf(
            Block.Paragraph(listOf(Segment("Test", TextStyle.BOLD)))
        ))
        val json1 = NoteBodyJson.encode(body)
        val json2 = NoteBodyJson.encode(body)
        assertEquals(json1, json2)
    }
}
