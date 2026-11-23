package com.earendil.todonotes.data.richtext

/**
 * "Teilen mit der App" — geteilten Text ans Ende einer Notiz anhängen.
 *
 * Nutzer-Regeln (an Textzeilen formuliert, hier aufs Block-Modell
 * übersetzt — Präfixe sind bei Listen implizit, nicht echt im Text):
 * - Letzte Zeile ist ein LEERES Listen-Item (z. B. "[ ] " ohne Inhalt):
 *   Text kommt HINTER das Präfix (Item wird gefüllt, Checked-Status
 *   bleibt wie er war).
 * - Letzte Zeile ist ein Listen-Item MIT Inhalt ("[ ] hund"):
 *   neue Zeile mit GLEICHEM Präfix-Typ (neues Item; Nummerierung
 *   zählt beim Rendern automatisch weiter).
 * - Letzte Zeile ist normaler Text mit Inhalt ("hund"):
 *   neue Zeile OHNE Präfix (neuer Paragraph).
 * - Letzte Zeile ist ein LEERER Paragraph: Text füllt ihn.
 * - Notiz leer / letzter Block ist ein Bild: neuer Paragraph.
 *
 * Mehrzeiliger geteilter Text: eine Zeile pro Durchlauf — die erste
 * füllt ggf. das leere letzte Item/Paragraph, jede weitere wird
 * regulär angehängt. Leerzeilen werden verworfen.
 */
fun appendSharedTextToBody(body: NoteBody, rawText: String): NoteBody {
    val lines = rawText
        .replace("\r\n", "\n")
        .split("\n")
        .map { it.trim() }
        .filter { it.isNotEmpty() }
    if (lines.isEmpty()) return body

    val blocks = body.blocks.toMutableList()
    for (line in lines) {
        when (val last = blocks.lastOrNull()) {
            is Block.ListBlock -> {
                val lastItem = last.items.lastOrNull()
                if (lastItem != null && lastItem.segments.isBlankText()) {
                    // Leeres Item ("[ ] " ohne Inhalt) → Text dahinter.
                    // Checked-Status des Items bleibt erhalten.
                    blocks[blocks.lastIndex] = last.copy(
                        items = last.items.dropLast(1) +
                                lastItem.copy(segments = listOf(Segment(line)))
                    )
                } else {
                    // Item mit Inhalt → neues Item desselben Typs
                    // (Nummerierung zählt beim Rendern weiter).
                    blocks[blocks.lastIndex] = last.copy(
                        items = last.items + ListItem(segments = listOf(Segment(line)))
                    )
                }
            }

            is Block.Paragraph -> {
                if (last.segments.isBlankText()) {
                    // Leerer Paragraph → Text füllt ihn.
                    blocks[blocks.lastIndex] = Block.Paragraph(listOf(Segment(line)))
                } else {
                    // Inhalt → neuer Paragraph dahinter.
                    blocks += Block.Paragraph(listOf(Segment(line)))
                }
            }

            else -> {
                // Leerer Body oder letzter Block ist ein Bild:
                // neuer Paragraph.
                blocks += Block.Paragraph(listOf(Segment(line)))
            }
        }
    }
    return NoteBody(blocks = blocks)
}

/** True, wenn die Segmente zusammen nur leeren Text (leer/Whitespace) ergeben. */
private fun List<Segment>.isBlankText(): Boolean =
    joinToString("") { it.text }.isBlank()