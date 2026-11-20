package com.earendil.todonotes.ui.notes

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.OffsetMapping
import androidx.compose.ui.text.input.TransformedText
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextDecoration
import com.earendil.todonotes.data.richtext.ListType
import com.earendil.todonotes.data.richtext.NoteTextBody

/**
 * Blendet Inline-Stil-Marker (**fett**, *kursiv*, __unterstrichen__) aus
 * und rendert den Stil direkt (bold/italic/underline). Cursor-Positionen
 * werden zwischen sichtbarem und Roh-Text gemappt, sodass der Cursor nie
 * "in" den Markern landet.
 *
 * (Hierher verschoben aus dem Editor, damit [NoteBodyVisualTransformation]
 * ihn verketten kann.)
 */
internal class MarkdownVisualTransformation : VisualTransformation {

    override fun filter(text: AnnotatedString): TransformedText {
        val raw = text.text
        val parsed = NoteTextBody.parseInlineStyles(raw)
        val annotated = buildAnnotatedString {
            parsed.forEach { seg ->
                val style = when (seg.style) {
                    NoteTextBody.InlineStyle.BOLD -> SpanStyle(fontWeight = FontWeight.Bold)
                    NoteTextBody.InlineStyle.ITALIC -> SpanStyle(fontStyle = FontStyle.Italic)
                    NoteTextBody.InlineStyle.UNDERLINE -> SpanStyle(textDecoration = TextDecoration.Underline)
                    null -> SpanStyle()
                }
                append(AnnotatedString(seg.text, style))
            }
        }
        val offsetMapping = object : OffsetMapping {
            override fun originalToTransformed(offset: Int): Int =
                NoteTextBody.rawToVisualOffset(raw, offset)
            override fun transformedToOriginal(offset: Int): Int =
                NoteTextBody.visualToRawOffset(raw, offset)
        }
        return TransformedText(annotated, offsetMapping)
    }
}

/** Zeilen-Info für die Präfix-Transformation (siehe [ListPrefixRenderer]). */
internal data class PrefixLineInfo(
    val rawStart: Int,
    /** Zeilen-Länge inkl. `\n` im Roh-Text. */
    val rawLen: Int,
    /** Präfix-Länge im Roh-Text (z.B. 4 für `[ ] `). */
    val rawPrefixLen: Int,
    val transfStart: Int,
    /** Zeilen-Länge inkl. `\n` nach der Präfix-Transformation. */
    val transfLen: Int,
    /** Präfix-Länge nach Transformation (z.B. 2 für `☐ `). */
    val transfPrefixLen: Int,
    /** Transformierter Präfix-String (z.B. `"☐ "`). */
    val transfPrefix: String,
    val type: ListType?,
    val checked: Boolean
)

/**
 * Rendert die Listen-Präfixe des Notiz-Body als hübsche Symbole — NUR für
 * die Anzeige ([VisualTransformation]), der Roh-Text bleibt unverändert:
 *
 *  - `- `    → `• ` (Bullet)
 *  - `[ ] `  → `☐ ` (offene Checkbox)
 *  - `[x] `  → `☑ ` (abhakte Checkbox — Zeile wird gedimmt)
 *  - `N. `   → fortlaufend NEU nummeriert (`1.`, `2.`, … nur Anzeige! der
 *              gespeicherten Nummer bleibt im Roh-Text)
 *  - `→ `    → bleibt (ist schon ein Pfeil)
 *
 * Cursor/Selektion werden über die Offset-Mappings korrekt hin- und
 * zurückgemappt — auch bei Präfixen mit ANDERER Länge (Checkbox 4→2).
 */
internal object ListPrefixRenderer {

    /** Zeilen-Zerlegung + transformierte Präfixe. Leerer Text → leere Liste. */
    fun analyze(raw: String): List<PrefixLineInfo> {
        if (raw.isEmpty()) return emptyList()
        val out = mutableListOf<PrefixLineInfo>()
        var rawStart = 0
        var transfStart = 0
        var seq = 0 // fortlaufende Nummer für ORDERED (nur Anzeige)
        while (rawStart < raw.length) {
            val nl = raw.indexOf('\n', rawStart)
            val rawLineEnd = if (nl < 0) raw.length else nl
            val rawNext = if (nl < 0) raw.length else nl + 1
            val line = raw.substring(rawStart, rawLineEnd)
            val type = NoteTextBody.detectListType(line)
            val checked = type == ListType.CHECKBOX && NoteTextBody.isCheckboxChecked(line)
            val content = NoteTextBody.stripPrefix(line)
            val rawPrefixLen = line.length - content.length
            val transfPrefix = when (type) {
                null -> ""
                ListType.BULLET -> "• "
                ListType.CHECKBOX -> if (checked) "☑ " else "☐ "
                ListType.ARROW -> line.substring(0, rawPrefixLen) // "→ " bleibt
                ListType.ORDERED -> {
                    seq += 1
                    "$seq. "
                }
            }
            if (type != ListType.ORDERED) seq = 0
            val rawLen = rawNext - rawStart
            val transfLen = transfPrefix.length + (rawLen - rawPrefixLen)
            out += PrefixLineInfo(
                rawStart = rawStart,
                rawLen = rawLen,
                rawPrefixLen = rawPrefixLen,
                transfStart = transfStart,
                transfLen = transfLen,
                transfPrefixLen = transfPrefix.length,
                transfPrefix = transfPrefix,
                type = type,
                checked = checked
            )
            rawStart = rawNext
            transfStart += transfLen
        }
        return out
    }

    /** Präfix-transformierter Text (Inline-Marker sind NOCH drin — die kommen danach). */
    fun prefixText(raw: String, lines: List<PrefixLineInfo>): String = buildString {
        for (l in lines) {
            append(l.transfPrefix)
            append(raw, l.rawStart + l.rawPrefixLen, l.rawStart + l.rawLen)
        }
    }

    private fun totalTransfLen(lines: List<PrefixLineInfo>): Int =
        lines.last().transfStart + lines.last().transfLen

    /** Roh-Offset → Präfix-transformierter Offset (monoton). */
    fun origToTransf(lines: List<PrefixLineInfo>, rawLen: Int, offset: Int): Int {
        if (lines.isEmpty()) return offset
        val o = offset.coerceIn(0, rawLen)
        val line = lines.lastOrNull { o >= it.rawStart } ?: lines.first()
        val rel = (o - line.rawStart).coerceIn(0, line.rawLen)
        return line.transfStart + (
            if (rel <= line.rawPrefixLen) minOf(rel, line.transfPrefixLen)
            else line.transfPrefixLen + rel - line.rawPrefixLen
        )
    }

    /** Präfix-transformierter Offset → Roh-Offset (monoton). */
    fun transfToOrig(lines: List<PrefixLineInfo>, offset: Int): Int {
        if (lines.isEmpty()) return offset
        val o = offset.coerceIn(0, totalTransfLen(lines))
        val line = lines.lastOrNull { o >= it.transfStart } ?: lines.first()
        val rel = (o - line.transfStart).coerceIn(0, line.transfLen)
        return line.rawStart + (
            if (rel < line.transfPrefixLen) rel
            else line.rawPrefixLen + rel - line.transfPrefixLen
        )
    }
}

/**
 * Komplette Body-Anzeige-Transformation: Listen-Präfixe (☐/•/fortlaufende
 * Nummer) + Inline-Stil-Marker (fett/kursiv/unterstrichen), verkettet.
 *
 * Kette: Roh-Text → (Präfix) → Präfix-Text → (Markdown) → Anzeige.
 * Die Offset-Mappings werden entsprechend komponiert. Gedimmte
 * Checkbox-Zeilen (☑) bekommen ihre Farbe über einen Style-Bereich.
 */
internal class NoteBodyVisualTransformation(
    private val dimColor: Color
) : VisualTransformation {

    private val markdown = MarkdownVisualTransformation()

    override fun filter(text: AnnotatedString): TransformedText {
        val raw = text.text
        val lines = ListPrefixRenderer.analyze(raw)
        // Ohne Präfixe: nur Markdown (wie vor dem Fix).
        if (lines.isEmpty()) return markdown.filter(text)

        val prefixAnnotated = AnnotatedString(ListPrefixRenderer.prefixText(raw, lines))
        val md = markdown.filter(prefixAnnotated)

        // Gedimmte Checkbox-Zeilen (☑ …): Farbe über die ganze Zeile.
        val builder2 = androidx.compose.ui.text.AnnotatedString.Builder()
        builder2.append(md.text)
        for (l in lines) {
            if (l.type == ListType.CHECKBOX && l.checked) {
                val s = md.offsetMapping.originalToTransformed(l.transfStart)
                val e = md.offsetMapping.originalToTransformed(l.transfStart + l.transfLen)
                if (e > s) builder2.addStyle(SpanStyle(color = dimColor), s, e)
            }
        }

        val offsetMapping = object : OffsetMapping {
            override fun originalToTransformed(offset: Int): Int =
                md.offsetMapping.originalToTransformed(
                    ListPrefixRenderer.origToTransf(lines, raw.length, offset)
                )
            override fun transformedToOriginal(offset: Int): Int =
                ListPrefixRenderer.transfToOrig(
                    lines, md.offsetMapping.transformedToOriginal(offset)
                )
        }
        return TransformedText(builder2.toAnnotatedString(), offsetMapping)
    }

    /**
     * Für Tap-auf-Checkbox-Symbol: transformierter Offset (aus dem
     * Text-Layout) → Zeile, wenn der Tap auf ein ☐/☑-Symbol fällt
     * (sonst null). [raw] = aktueller Roh-Text.
     */
    fun checkboxLineForTransf(raw: String, transfOffset: Int): PrefixLineInfo? {
        val lines = ListPrefixRenderer.analyze(raw)
        if (lines.isEmpty()) return null
        val p = NoteTextBody.visualToRawOffset(
            ListPrefixRenderer.prefixText(raw, lines), transfOffset
        )
        val line = lines.lastOrNull {
            p >= it.transfStart && p < it.transfStart + it.transfLen
        } ?: lines.last()
        if (line.type != ListType.CHECKBOX) return null
        val rel = p - line.transfStart
        if (rel < 0 || rel >= line.transfPrefixLen) return null
        return line
    }
}