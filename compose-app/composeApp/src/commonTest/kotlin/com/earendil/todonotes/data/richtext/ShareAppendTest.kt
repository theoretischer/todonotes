package com.earendil.todonotes.data.richtext

import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * ShareAppend (Plain-Text-Ebene — der Body liegt so in der DB):
 * Nutzer-Regeln:
 * - letzte Zeile "[ ] " leer → Text HINTER das Präfix, Haken-Status bleibt
 * - letzte Zeile "[ ] hund" → neue Zeile "[ ] Text" (gleicher Typ)
 * - letzte Zeile "hund" → neue Zeile OHNE Präfix
 * - letzte Zeile leer → füllen; Notiz leer → füllen
 * - mehrzeilig → Zeile für Zeile; Nummerierung zählt weiter
 */
class ShareAppendTest {

    @Test
    fun `leeres Checkbox-Item wird hinter dem Praefix gefuellt`() {
        val result = appendSharedTextToText("[ ] hund\n[ ] ", "Gassi gehen")
        assertEquals("[ ] hund\n[ ] Gassi gehen", result)
    }

    @Test
    fun `checked-Status des gefuellten Items bleibt`() {
        val result = appendSharedTextToText("[x] ", "erledigt?!")
        assertEquals("[x] erledigt?!", result)
    }

    @Test
    fun `Checkbox-Item mit Inhalt bekommt neue Zeile`() {
        val result = appendSharedTextToText("[ ] hund", "Gassi gehen")
        assertEquals("[ ] hund\n[ ] Gassi gehen", result)
    }

    @Test
    fun `Bullet-Item mit Inhalt bekommt neue Bullet`() {
        val result = appendSharedTextToText("- alt", "neu")
        assertEquals("- alt\n- neu", result)
    }

    @Test
    fun `Pfeil-Item bekommt neuen Pfeil`() {
        val result = appendSharedTextToText("→ erst", "zweit")
        assertEquals("→ erst\n→ zweit", result)
    }

    @Test
    fun `Nummerierung zaehlt weiter`() {
        val result = appendSharedTextToText("1. eins\n2. zwei", "drei")
        assertEquals("1. eins\n2. zwei\n3. drei", result)
    }

    @Test
    fun `letzte Zeile normaler Text → neue Zeile ohne Praefix`() {
        val result = appendSharedTextToText("hund", "Gassi gehen")
        assertEquals("hund\nGassi gehen", result)
    }

    @Test
    fun `letzte Zeile LEER ohne Praefix → wird gefuellt`() {
        val result = appendSharedTextToText("hund\n", "hallo")
        assertEquals("hund\nhallo", result)
    }

    @Test
    fun `leere Notiz → Text fuellt sie`() {
        val result = appendSharedTextToText("", "hallo")
        assertEquals("hallo", result)
    }

    @Test
    fun `mehrzeiliger Text geht Zeile fuer Zeile`() {
        val result = appendSharedTextToText("[ ] ", "eins\n\nzwei\r\ndrei")
        assertEquals("[ ] eins\n[ ] zwei\n[ ] drei", result)
    }

    @Test
    fun `Legacy-Block-JSON wird nie angefasst wenn Text leer`() {
        // Sicherheitsnetz: leerer geteilter Text → Body unveraendert
        // (auch wenn er (versehentlich) als JSON vorbekommen sollte).
        assertEquals("[]", appendSharedTextToText("[]", "  \n \n"))
    }

    @Test
    fun `bestehender Inhalt bleibt IMMER erhalten`() {
        // Regression zum Datenverlust-Bug: der alte Code decodete Plain-
        // Text als Block-JSON → leer → alles weg. Hier: viel Inhalt +
        // Anhaengen → alles + neuer Text (letzte Zeile ist ein Bullet-Item
        // MIT Inhalt → neue Zeile kriegt GLEICHES Praefix, Nutzer-Regel).
        val body = "zeile eins\nzeile zwei\n[ ] hund\n- kater"
        val result = appendSharedTextToText(body, "neu")
        assertEquals("zeile eins\nzeile zwei\n[ ] hund\n- kater\n- neu", result)
    }
}