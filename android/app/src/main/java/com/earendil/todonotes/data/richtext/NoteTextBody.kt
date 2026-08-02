package com.earendil.todonotes.data.richtext

/**
 * Plain-Text-Notiz-Body (Block F5, kontinuierlicher Textfluss wie Word).
 *
 * Statt einzelner Block-UI-Elemente ist der gesamte Body ein einziger
 * Text-String. Listen-Formatierung passiert über Zeilen-Präfixe:
 *
 *   - Eintrag            → Bullet-Liste
 *   1. Eintrag          → Nummerierte Liste (Nummer wird beim Rendern
 *                         automatisch vergeben, Prefix ist nur "1.")
 *   [ ] Eintrag         → Checkbox (offen)
 *   [x] Eintrag         → Checkbox (erledigt)
 *   → Eintrag           → Pfeil-Liste
 *
 * Die Präfixe sind Teil des Textes (wie Markdown) – so bleibt der Body
 * plain text, einfach zu sync-en und auch ohne spezielle UI lesbar.
 * Die Editor-UI interpretiert die Präfixe beim Rendern und beim Enter-
 * auto-continue (neue Zeile übernimmt den Prefix der vorherigen).
 *
 * [NoteTextBody] migriert beim Laden alte Block-JSON (F3-Format) zu
 * Plain Text, sodass bestehende Notizen nahtlos weiter funktionieren.
 */
object NoteTextBody {

    // ---- Präfix-Konstanten ----

    const val BULLET_PREFIX = "- "
    const val CHECKBOX_OPEN = "[ ] "
    const val CHECKBOX_DONE = "[x] "
    const val ARROW_PREFIX = "→ "
    // Nummeriert: "1. " … "99. " – wird beim Rendern fortlaufend vergeben

    /** Passt ein Zeilen-Prefix zur angegebenen Liste an. */
    fun setListPrefix(text: String, lineStart: Int, type: ListType, number: Int = 1): String {
        val lineEnd = text.indexOf('\n', lineStart).let { if (it < 0) text.length else it }
        val lineContent = text.substring(lineStart, lineEnd)
        val stripped = stripPrefix(lineContent)
        val prefix = when (type) {
            ListType.BULLET -> BULLET_PREFIX
            ListType.ORDERED -> "$number. "
            ListType.ARROW -> ARROW_PREFIX
            ListType.CHECKBOX -> CHECKBOX_OPEN
        }
        val newLine = prefix + stripped
        return text.substring(0, lineStart) + newLine + text.substring(lineEnd)
    }

    /** Entfernt den Listen-Prefix von einer Zeile. */
    fun stripPrefix(line: String): String {
        // Bullet
        if (line.startsWith(BULLET_PREFIX)) return line.removePrefix(BULLET_PREFIX)
        // Checkbox
        if (line.startsWith(CHECKBOX_OPEN)) return line.removePrefix(CHECKBOX_OPEN)
        if (line.startsWith(CHECKBOX_DONE)) return line.removePrefix(CHECKBOX_DONE)
        // Arrow
        if (line.startsWith(ARROW_PREFIX)) return line.removePrefix(ARROW_PREFIX)
        // Ordered: "<n>. "
        val orderedMatch = Regex("^(\\d+)\\. ").find(line)
        if (orderedMatch != null) return line.removePrefix(orderedMatch.value)
        return line
    }

    /** Erkennt den ListType einer Zeile (anhand ihres Prefix). */
    fun detectListType(line: String): ListType? = when {
        line.startsWith(BULLET_PREFIX) -> ListType.BULLET
        line.startsWith(CHECKBOX_OPEN) || line.startsWith(CHECKBOX_DONE) -> ListType.CHECKBOX
        line.startsWith(ARROW_PREFIX) -> ListType.ARROW
        Regex("^\\d+\\. ").containsMatchIn(line) -> ListType.ORDERED
        else -> null
    }

    /** Toggelt eine Checkbox-Zeile [ ] ↔ [x]. */
    fun toggleCheckbox(text: String, lineStart: Int): String {
        val lineEnd = text.indexOf('\n', lineStart).let { if (it < 0) text.length else it }
        val line = text.substring(lineStart, lineEnd)
        val newLine = when {
            line.startsWith(CHECKBOX_OPEN) -> CHECKBOX_DONE + line.removePrefix(CHECKBOX_OPEN)
            line.startsWith(CHECKBOX_DONE) -> CHECKBOX_OPEN + line.removePrefix(CHECKBOX_DONE)
            else -> return text
        }
        return text.substring(0, lineStart) + newLine + text.substring(lineEnd)
    }

    /** Liefert true, wenn die Zeile einen Listen-Prefix hat. */
    fun hasListPrefix(line: String): Boolean = detectListType(line) != null

    /** Entfernt den Prefix einer Zeile an Position [lineStart]. */
    fun removePrefixAt(text: String, lineStart: Int): String {
        val lineEnd = text.indexOf('\n', lineStart).let { if (it < 0) text.length else it }
        val line = text.substring(lineStart, lineEnd)
        val newLine = stripPrefix(line)
        return text.substring(0, lineStart) + newLine + text.substring(lineEnd)
    }

    // ---- Migration: Block-JSON → Plain Text ----

    /**
     * Migriert eine bestehende Notiz: falls der [bodyJson] im alten
     * Block-Format (F3) gespeichert ist, wird er zu Plain Text
     * konvertiert. Ist er bereits Plain Text (kein JSON-Array), wird
     * er unverändert zurückgegeben.
     */
    fun migrateFromBlocks(bodyJson: String): String {
        if (bodyJson.isBlank()) return ""
        // Heuristik: Block-JSON beginnt mit '[' oder '{"kind"'
        val trimmed = bodyJson.trimStart()
        if (trimmed.startsWith("[")) {
            return runCatching { blocksToText(NoteBodyJson.decode(bodyJson)) }
                .getOrElse { "" }
        }
        // schon Plain Text
        return bodyJson
    }

    /** Block-Modell → Plain-Text mit Präfixen. */
    private fun blocksToText(body: NoteBody): String = buildString {
        for (block in body.blocks) {
            when (block) {
                is Block.Paragraph -> {
                    append(block.segments.joinToString("") { it.text })
                    append("\n")
                }
                is Block.ListBlock -> {
                    block.items.forEachIndexed { idx, item ->
                        val text = item.segments.joinToString("") { it.text }
                        when (block.type) {
                            ListType.BULLET -> append(BULLET_PREFIX)
                            ListType.ORDERED -> append("${idx + 1}. ")
                            ListType.ARROW -> append(ARROW_PREFIX)
                            ListType.CHECKBOX ->
                                if (item.checked) append(CHECKBOX_DONE) else append(CHECKBOX_OPEN)
                        }
                        append(text)
                        append("\n")
                    }
                }
                is Block.ImageBlock -> {
                    append("[Bild: ${block.imageId}]")
                    if (!block.caption.isNullOrBlank()) append(" — ${block.caption}")
                    append("\n")
                }
            }
        }
        // Letzten Zeilenumbruch entfernen
        if (endsWith("\n")) deleteCharAt(length - 1)
    }
}
