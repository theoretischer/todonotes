package com.earendil.todonotes.ui.notes

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.text.InlineTextContent
import androidx.compose.material3.Checkbox
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.Placeholder
import androidx.compose.ui.text.PlaceholderVerticalAlign
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.earendil.todonotes.data.richtext.ListType
import com.earendil.todonotes.data.richtext.NoteTextBody

/**
 * Lesemodus des Notiz-Bodies (Option B, „wie es vorher war").
 *
 * Rendert jede Zeile als: Prefix-Widget (ECHTE Checkbox, gezeichneter
 * Bullet-Punkt, Canvas-Pfeil — wie der alte Per-Zeilen-Editor) +
 * Fließtext mit Inline-Stilen (fett/kursiv/unterstrichen). Checkboxes
 * sind klickbar (togglen sofort, auto-save).
 *
 * Technik: [Text] mit [InlineTextContent] — echte Composables als
 * Platzhalter im Text. Der Platzhalter ist ein Object-Replacement-
 * Zeichen (\uFFFC) mit String-Annotation (id) — die Inline-Content-Map
 * keyt auf dieselbe id. (appendInlineContent gibt es erst ab
 * Compose 1.9 — hier 1.8.2, deshalb der klassische Weg.)
 *
 * Das geht NUR beim nicht-editierbaren Text (BasicTextField kann keine
 * Inline-Content-Composables) — deshalb der Modus-Wechsel im Editor.
 */
@Composable
internal fun NoteBodyReadOnly(
    text: String,
    onToggleCheckbox: (lineRawStart: Int) -> Unit,
    modifier: Modifier = Modifier
) {
    val contentColor = MaterialTheme.colorScheme.onSurface
    val dimColor = MaterialTheme.colorScheme.onSurfaceVariant
    val lines = ListPrefixRenderer.analyze(text)

    val inlineContent = mutableMapOf<String, InlineTextContent>()
    val annotated: AnnotatedString = buildAnnotatedString {
        lines.forEachIndexed { i, l ->
            val lineEnd = l.rawStart + l.rawLen
            val hasNewline = lineEnd > 0 && lineEnd <= text.length && text[lineEnd - 1] == '\n'
            val contentEnd = if (hasNewline) lineEnd - 1 else lineEnd
            val content = text.substring(l.rawStart + l.rawPrefixLen, contentEnd)

            // Abgehakte Checkbox-Zeile: gedimmt (wie der alte Editor).
            if (l.type == ListType.CHECKBOX && l.checked) {
                pushStyle(SpanStyle(color = dimColor))
            }

            // Prefix-Widget als Inline-Content: \uFFFC-Platzhalter +
            // String-Annotation mit der id — die Inline-Content-Map keyt darauf.
            if (l.type != null) {
                val pid = "prefix-$i"
                val start = length
                append("\uFFFC")
                addStringAnnotation(pid, pid, start, length)
                inlineContent[pid] = InlineTextContent(
                    placeholder = Placeholder(
                        width = 24.sp,
                        height = 24.sp,
                        placeholderVerticalAlign = PlaceholderVerticalAlign.Center
                    )
                ) {
                    ReadOnlyPrefix(
                        line = l,
                        contentColor = contentColor,
                        onToggle = { onToggleCheckbox(l.rawStart) }
                    )
                }
            }

            // Zeilen-Inhalt mit Inline-Stilen (fett/kursiv/unterstrichen).
            for (seg in NoteTextBody.parseInlineStyles(content)) {
                val style = when (seg.style) {
                    NoteTextBody.InlineStyle.BOLD -> SpanStyle(fontWeight = FontWeight.Bold)
                    NoteTextBody.InlineStyle.ITALIC -> SpanStyle(fontStyle = FontStyle.Italic)
                    NoteTextBody.InlineStyle.UNDERLINE -> SpanStyle(textDecoration = TextDecoration.Underline)
                    null -> SpanStyle()
                }
                pushStyle(style)
                append(seg.text)
                pop()
            }

            if (l.type == ListType.CHECKBOX && l.checked) pop()

            if (hasNewline) append("\n")
        }
    }

    Text(
        text = annotated,
        inlineContent = inlineContent,
        modifier = modifier,
        style = MaterialTheme.typography.bodyLarge.copy(color = contentColor)
    )
}

/** Das Prefix-Widget einer Zeile im Lesemodus. */
@Composable
private fun ReadOnlyPrefix(
    line: PrefixLineInfo,
    contentColor: Color,
    onToggle: () -> Unit
) {
    Box(
        modifier = Modifier.size(24.dp),
        contentAlignment = Alignment.Center
    ) {
        when (line.type) {
            null -> Unit
            ListType.CHECKBOX -> Checkbox(
                checked = line.checked,
                onCheckedChange = { onToggle() },
                modifier = Modifier.size(22.dp)
            )
            ListType.BULLET -> BulletDot(color = contentColor)
            // Fortlaufende Nummer (Anzeige) — transfPrefix ist "$seq. "
            ListType.ORDERED -> Text(
                text = line.transfPrefix,
                style = MaterialTheme.typography.bodyLarge,
                color = contentColor
            )
            ListType.ARROW -> ArrowCanvas(color = contentColor)
        }
    }
}

/** Großer runder Bullet-Punkt (wie der alte Per-Zeilen-Editor). */
@Composable
private fun BulletDot(color: Color) {
    Canvas(modifier = Modifier.size(20.dp)) {
        drawCircle(color = color, radius = size.minDimension * 0.30f)
    }
}

/** Pfeil als Canvas-Zeichnung (→ als Unicode wäre auf Wasm ggf. eine
 *  Fehler-Box — fehlender Glyph in manchen Schriftarten). */
@Composable
private fun ArrowCanvas(color: Color) {
    Canvas(modifier = Modifier.size(20.dp)) {
        val w = size.width
        val h = size.height
        val centerY = h / 2f
        val startX = w * 0.10f
        val endX = w * 0.90f
        drawLine(
            color = color,
            start = Offset(startX, centerY),
            end = Offset(endX, centerY),
            strokeWidth = w * 0.09f
        )
        val arrowSize = w * 0.28f
        drawLine(
            color = color,
            start = Offset(endX, centerY),
            end = Offset(endX - arrowSize, centerY - arrowSize * 0.7f),
            strokeWidth = w * 0.09f
        )
        drawLine(
            color = color,
            start = Offset(endX, centerY),
            end = Offset(endX - arrowSize, centerY + arrowSize * 0.7f),
            strokeWidth = w * 0.09f
        )
    }
}