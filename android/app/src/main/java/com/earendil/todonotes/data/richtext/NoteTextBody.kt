package com.earendil.todonotes.data.richtext

/**
 * Plain-Text-Notiz-Body (Block F5, kontinuierlicher Textfluss wie Word).
 *
 * Der Body ist ein einziger Text-String, zeilenweise mit `\n` getrennt.
 * Listen-Formatierung wird über **Marker-Präfixe** gespeichert, die die
 * UI visuell interpretiert (echte Checkbox, runder Bullet-Punkt etc.):
 *
 *   - Eintrag            → Bullet-Liste
 *   1. Eintrag           → Nummerierte Liste (Nummer im Text gespeichert)
 *   [ ] Eintrag          → Checkbox (offen)
 *   [x] Eintrag          → Checkbox (erledigt)
 *   → Eintrag            → Pfeil-Liste
 *
 * Die Präfixe bleiben im Text (für Sync/Kompatibilität), werden aber
 * in der Editor-UI nicht als Text angezeigt, sondern als grafische
 * Elemente gerendert (Bullet-Punkt, Checkbox-Widget, Pfeil-Icon, Zahl).
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
    // Nummeriert: "1. " … "99. " – Zahl wird gespeichert, beim Rendern
    // aber fortlaufend neu vergeben.

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
        if (line.startsWith(BULLET_PREFIX)) return line.removePrefix(BULLET_PREFIX)
        if (line.startsWith(CHECKBOX_OPEN)) return line.removePrefix(CHECKBOX_OPEN)
        if (line.startsWith(CHECKBOX_DONE)) return line.removePrefix(CHECKBOX_DONE)
        if (line.startsWith(ARROW_PREFIX)) return line.removePrefix(ARROW_PREFIX)
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

    /** Liefert true wenn die Checkbox-Zeile angehakt ist. */
    fun isCheckboxChecked(line: String): Boolean = line.startsWith(CHECKBOX_DONE)

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

    /** Entfernt den Prefix einer Zeile an Position [lineStart]. */
    fun removePrefixAt(text: String, lineStart: Int): String {
        val lineEnd = text.indexOf('\n', lineStart).let { if (it < 0) text.length else it }
        val line = text.substring(lineStart, lineEnd)
        val newLine = stripPrefix(line)
        return text.substring(0, lineStart) + newLine + text.substring(lineEnd)
    }

    // ---- Zeilen-Splitting für die UI ----

    /**
     * Zerlegt den Body-Text in Zeilen und liefert pro Zeile den
     * reinen Inhalt (ohne Prefix) + den erkannten [ListType] (oder null).
     *
     * Die UI nutzt das, um jede Zeile als Row(Prefix-Widget + TextField)
     * zu rendern. Die Zeilen-Nummer wird bei ORDERED fortlaufend vergeben.
     */
    fun toLines(text: String): List<NoteLine> {
        if (text.isEmpty()) return listOf(NoteLine("", null, 0))
        val rawLines = text.split("\n")
        val result = mutableListOf<NoteLine>()
        var orderedCounter = 0
        for (rawLine in rawLines) {
            val type = detectListType(rawLine)
            val content = stripPrefix(rawLine)
            val number = when (type) {
                ListType.ORDERED -> {
                    orderedCounter++
                    orderedCounter
                }
                else -> { orderedCounter = 0; 0 }
            }
            result.add(NoteLine(content, type, number, checked = type == ListType.CHECKBOX && isCheckboxChecked(rawLine)))
        }
        return result
    }

    /**
     * Baut aus einer Liste von [NoteLine] den Body-Text mit Präfixen
     * wieder zusammen. (Gegenstück zu [toLines].)
     */
    fun fromLines(lines: List<NoteLine>): String = buildString {
        var orderedCounter = 0
        for (line in lines) {
            when (line.type) {
                null -> { orderedCounter = 0 }
                ListType.ORDERED -> {
                    orderedCounter++
                    append("$orderedCounter. ")
                }
                ListType.BULLET -> { orderedCounter = 0; append(BULLET_PREFIX) }
                ListType.CHECKBOX -> {
                    orderedCounter = 0
                    append(if (line.checked) CHECKBOX_DONE else CHECKBOX_OPEN)
                }
                ListType.ARROW -> { orderedCounter = 0; append(ARROW_PREFIX) }
            }
            append(line.content)
            append("\n")
        }
        // Letzten \n entfernen
        if (endsWith("\n")) deleteCharAt(length - 1)
    }

    // ===================== Inline-Stil (B/I/U) =====================
    //
    // Fett/Kursiv/Unterstrichen wird mit Markdown-ähnlichen Markern im
    // Text gespeichert (sync-freundlich, wie die Listen-Präfixe):
    //   **fett**   *kursiv*   __unterstrichen__
    // Die UI blenden die Marker aus und zeigen den Stil direkt an
    // (siehe MarkdownVisualTransformation). Pro Auswahl wird maximal
    // EIN Stil gesetzt (Toggle) – Kombinationen entstehen nicht durch
    // die UI, der Parser ist trotzdem robust dagegen.

    const val MD_BOLD = "**"
    const val MD_ITALIC = "*"
    const val MD_UNDERLINE = "__"

    private val BOLD_REGEX = Regex("\\*\\*([^*]+)\\*\\*")
    private val ITALIC_REGEX = Regex("\\*([^*]+)\\*")
    private val UNDERLINE_REGEX = Regex("__([^_]+)__")

    /** Marker-Paar für einen Stil (open == close). */
    private fun marker(style: InlineStyle): String = when (style) {
        InlineStyle.BOLD -> MD_BOLD
        InlineStyle.ITALIC -> MD_ITALIC
        InlineStyle.UNDERLINE -> MD_UNDERLINE
    }

    /**
     * Zerlegt [text] in sichtbare Segmente (Marker entfernt) und liefert
     * pro Segment den erkannten Stil (oder null). Fürs Rendering.
     */
    fun parseInlineStyles(text: String): List<InlineSegment> {
        val vis = buildVisible(text)
        if (vis.text.isEmpty()) return emptyList()
        val result = mutableListOf<InlineSegment>()
        var pos = 0
        for ((range, style) in vis.styles) {
            if (pos < range.first) result.add(InlineSegment(vis.text.substring(pos, range.first), null))
            result.add(InlineSegment(vis.text.substring(range.first, range.last + 1), style))
            pos = range.last + 1
        }
        if (pos < vis.text.length) result.add(InlineSegment(vis.text.substring(pos), null))
        return result.ifEmpty { listOf(InlineSegment(text, null)) }
    }

    /**
     * Mappt eine Cursor-Position [visualOffset] im sichtbaren Text (ohne
     * Marker) auf den ursprünglichen Roh-Text-Index. Für TextFieldValue-Offsets.
     *
     * Exakte inverse Abbildung zu [rawToVisualOffset]: es wird der kleinste
     * Roh-Index gesucht, dessen visueller Offset ≥ [visualOffset] ist.
     * Dadurch steht der Cursor direkt nach einem formatierten Wort VOR den
     * schließenden Markern — weitergetippter Text bleibt formatiert.
     */
    fun visualToRawOffset(text: String, visualOffset: Int): Int {
        if (visualOffset <= 0) return 0
        if (visualOffset >= text.length) return text.length
        // buildVisible nur einmal berechnen und Roh-Indizes manuell zählen.
        val vis = buildVisible(text)
        if (visualOffset >= vis.text.length) return text.length
        var visual = 0
        var i = 0
        while (i < text.length) {
            if (visual >= visualOffset) return i
            if (i !in vis.removedRaw) visual++
            i++
        }
        return text.length
    }

    /**
     * Mappt einen Roh-Text-Index [rawOffset] auf die Position im
     * sichtbaren Text (ohne Marker). Für die Anzeige der Auswahl.
     */
    fun rawToVisualOffset(text: String, rawOffset: Int): Int {
        if (rawOffset <= 0) return 0
        if (rawOffset >= text.length) return buildVisible(text).text.length
        val vis = buildVisible(text)
        var visual = 0
        var i = 0
        while (i < rawOffset) {
            if (i !in vis.removedRaw) visual++
            i++
        }
        return visual
    }

    /**
     * Togglet [style] um die Auswahl [start..end).
     *
     * Es werden drei Fälle unterschieden, damit die UI robust gegen
     * unterschiedliche Auswahl-Bereiche ist (die visuelle Transformation
     * kann die Auswahl inkl. Marker liefern ODER nur den Inhalt):
     *  A) Auswahl selbst ist von Markern umschlossen → entfernen (aus)
     *  B) Auswahl ist der Inhalt zwischen Markern direkt davor/danach → entfernen
     *  C) sonst → Marker einfügen (an)
     *
     * @return Triple(neuerText, neueStart, neueEnd) der Auswahl im neuen Text
     */
    fun toggleInlineStyle(text: String, start: Int, end: Int, style: InlineStyle): Triple<String, Int, Int> {
        val m = marker(style)
        val before = text.substring(0, start)
        val sel = text.substring(start, end)
        val after = text.substring(end)

        // Fall A: Auswahl enthält die Marker selbst
        if (sel.startsWith(m) && sel.endsWith(m) && sel.length > 2 * m.length) {
            val inner = sel.removePrefix(m).removeSuffix(m)
            return Triple(before + inner + after, start, start + inner.length)
        }
        // Fall B: Marker umschließen die Auswahl von außen
        if (before.endsWith(m) && after.startsWith(m)) {
            return Triple(
                before.removeSuffix(m) + sel + after.removePrefix(m),
                start - m.length,
                start - m.length + sel.length
            )
        }
        // Fall C: einschalten
        val newText = before + m + sel + m + after
        return Triple(newText, start + m.length, start + m.length + sel.length)
    }

    /**
     * Interne Darstellung: sichtbarer Text (ohne Marker) + entfernte
     * Roh-Indizes UND visuelle Anker (für Offset-Mapping) + Stil-Bereiche.
     */
    private fun buildVisible(text: String): VisibleText {
        val sb = StringBuilder()
        val removedRaw = mutableListOf<Int>()
        val styles = mutableListOf<Pair<IntRange, InlineStyle>>()

        data class M(val range: IntRange, val style: InlineStyle, val content: String)
        val matches = mutableListOf<M>()
        BOLD_REGEX.findAll(text).forEach { matches.add(M(it.range, InlineStyle.BOLD, it.groupValues[1])) }
        ITALIC_REGEX.findAll(text).forEach { matches.add(M(it.range, InlineStyle.ITALIC, it.groupValues[1])) }
        UNDERLINE_REGEX.findAll(text).forEach { matches.add(M(it.range, InlineStyle.UNDERLINE, it.groupValues[1])) }
        matches.sortBy { it.range.first }

        var pos = 0
        val used = mutableListOf<IntRange>()
        for (m in matches) {
            // Überlappende Matches (z.B. __a**b**a__) überspringen.
            if (used.any { it.first < m.range.last && m.range.first < it.last }) continue
            val openLen = marker(m.style).length
            sb.append(text, pos, m.range.first)
            removedRaw.addAll(m.range.first until m.range.first + openLen)
            val vStart = sb.length
            sb.append(m.content)
            val vEnd = sb.length - 1  // inklusiv
            removedRaw.addAll(m.range.last + 1 - openLen..m.range.last)
            styles.add(vStart..vEnd to m.style)
            pos = m.range.last + 1
            used.add(m.range)
        }
        sb.append(text, pos, text.length)
        return VisibleText(sb.toString(), removedRaw, styles)
    }

    private data class VisibleText(
        val text: String,
        val removedRaw: List<Int>,
        val styles: List<Pair<IntRange, InlineStyle>>
    )

    /** Stil-Einstellung für Inline-Formatierung. */
    enum class InlineStyle { BOLD, ITALIC, UNDERLINE }

    /** Ein sichtbares Textsegment: [text] mit optionalem [style] (Marker entfernt). */
    data class InlineSegment(val text: String, val style: InlineStyle?)

    // ---- Migration: Block-JSON → Plain Text ----

    /**
     * Migriert eine bestehende Notiz: falls der [bodyJson] im alten
     * Block-Format (F3) gespeichert ist, wird er zu Plain Text
     * konvertiert. Ist er bereits Plain Text, wird er unverändert
     * zurückgegeben.
     */
    fun migrateFromBlocks(bodyJson: String): String {
        if (bodyJson.isBlank()) return ""
        val trimmed = bodyJson.trimStart()
        // Block-JSON erkannt: entweder `[` (Block-Array) oder `{"blocks":`
        // (NoteBodyJson.encode-Form mit dem blocks-Feld)
        val looksLikeJson = trimmed.startsWith("[") || trimmed.startsWith("{")
        if (looksLikeJson) {
            return runCatching { blocksToText(NoteBodyJson.decode(bodyJson)) }
                .getOrElse { bodyJson }
        }
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
        if (endsWith("\n")) deleteCharAt(length - 1)
    }
}

/**
 * Eine Zeile der Notiz: reiner Inhalt + optionaler Listen-Typ.
 *
 * @param content  Text ohne Prefix
 * @param type     [ListType] oder null (Plain-Text-Zeile)
 * @param number   fortlaufende Nummer bei ORDERED (sonst 0)
 * @param checked  Checkbox-Zustand (nur bei [ListType.CHECKBOX])
 */
data class NoteLine(
    val content: String,
    val type: ListType?,
    val number: Int = 0,
    val checked: Boolean = false
)
