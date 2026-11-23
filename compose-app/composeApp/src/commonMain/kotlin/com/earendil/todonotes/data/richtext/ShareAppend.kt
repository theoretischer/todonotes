package com.earendil.todonotes.data.richtext

/**
 * "Teilen mit der App" — geteilten Text ans Ende einer Notiz anhängen.
 *
 * WICHTIG: Arbeitet auf der PLAIN-TEXT-Ebene (so wie der Body in der
 * DB liegt — Checkboxen etc. sind echte Präfixe im Text, das Block-JSON
 * ist Legacy und wird beim Laden via migrateFromBlocks migriert).
 * Der 1. Versuch decodete den Plain-Text als Block-JSON → decode
 * scheiterte still → EMPTY_BODY → **ganze Notiz gelöscht** (Nutzer-
 * Datenverlust-Report: „wenn als prefix [ ] steht löscht er das
 * ganze dokument").
 *
 * Nutzer-Regeln (Zeilen-Sicht, direkt auf dem Text):
 * - Letzte Zeile ist ein LEERES Listen-Item („[ ] ", „- ", „→ ", „1. "
 *   ohne Inhalt, nur Leerzeichen dahinter): Text kommt HINTER das
 *   Präfix — bei Checkbox BLEIBT der Haken-Status („[x] " → „[x] Text").
 * - Letzte Zeile ist ein Listen-Item MIT Inhalt („[ ] hund"): neue
 *   Zeile mit GLEICHEM Präfix-Typ; Nummerierung zählt weiter
 *   („3. " — beim Rendern wird ohnehin fortlaufend vergeben).
 * - Letzte Zeile ist Text MIT Inhalt („hund"): neue Zeile OHNE Präfix.
 * - Letzte Zeile LEER (kein Präfix, kein Inhalt): Text füllt sie.
 * - Notiz leer: Text füllt sie (nichts davor).
 *
 * Mehrzeiliger geteilter Text: Zeile für Zeile durch dieselben Regeln
 * (die erste füllt ggf. das leere letzte Item/Zeile, jede weitere wird
 * regulär angehängt). Leerzeilen im geteilten Text werden verworfen.
 */
fun appendSharedTextToText(bodyText: String, rawText: String): String {
    val shared = rawText
        .replace("\r\n", "\n")
        .split("\n")
        .map { it.trim() }
        .filter { it.isNotEmpty() }
    if (shared.isEmpty()) return bodyText

    // Bestehende Zeilen; NICHT trimmen — Einrückungen sind Inhalt.
    val existing = if (bodyText.isEmpty()) emptyList() else bodyText.split("\n")
    val result = existing.toMutableList()

    for (line in shared) {
        val last = result.lastOrNull()
        val lastType = last?.let { NoteTextBody.detectListType(it) }
        if (lastType != null) {
            val content = NoteTextBody.stripPrefix(last ?: "")
            if (content.isBlank()) {
                // Leeres Item → Text hinter das Präfix. Checkbox-Haken-
                // Status ([x] vs [ ]) bleibt wie er ist.
                val prefix = last!!.substring(0, last.length - content.length)
                result[result.lastIndex] = prefix + line
            } else {
                // Item mit Inhalt → neue Zeile mit gleichem Präfix-Typ.
                val newPrefix = when (lastType) {
                    ListType.CHECKBOX -> NoteTextBody.CHECKBOX_OPEN
                    ListType.BULLET -> NoteTextBody.BULLET_PREFIX
                    ListType.ARROW -> NoteTextBody.ARROW_PREFIX
                    // Nächste Nummer = letzte Nummer + 1 (beim Rendern wird
                    // fortlaufend vergeben — der gespeicherte Wert ist nur
                    // ein plausibler Startpunkt).
                    ListType.ORDERED -> {
                        val m = Regex("^(\\d+)\\. ").find(last)
                        ((m?.groupValues?.get(1)?.toIntOrNull() ?: 0) + 1).toString() + ". "
                    }
                }
                result += newPrefix + line
            }
        } else {
            // Kein Listen-Präfix auf der letzten Zeile: leer → füllen,
            // sonst neue Zeile ohne Präfix.
            if (last == null || last.isBlank()) {
                if (result.isEmpty()) result += line else result[result.lastIndex] = line
            } else {
                result += line
            }
        }
    }
    return result.joinToString("\n")
}