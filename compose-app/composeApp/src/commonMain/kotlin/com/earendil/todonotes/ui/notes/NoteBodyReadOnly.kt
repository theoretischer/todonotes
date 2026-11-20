package com.earendil.todonotes.ui.notes

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Checkbox
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import com.earendil.todonotes.data.richtext.ListType
import com.earendil.todonotes.data.richtext.NoteLine
import com.earendil.todonotes.data.richtext.NoteTextBody

/**
 * Lesemodus des Notiz-Bodies („wie der alte Per-Zeilen-Editor", nur nicht
 * editierbar).
 *
 * Pro Zeile eine Row: Prefix-Widget (ECHTE Checkbox, gezeichneter Bullet,
 * Canvas-Pfeil, Nummer) + Fließtext mit Inline-Stilen (fett/kursiv/
 * unterstrichen als SpanStyles). Checkboxes sind direkt klickbar
 * (togglen sofort, auto-save), Taps auf den Fließtext gehen an [modifier]
 * (der Editor hängt dort den Wechsel in den Edit-Modus dran).
 *
 * BUGFIX (Option-B-Runde 2): Vorher via Text + InlineTextContent
 * (Composables ALS Platzhalter im Text). Das funktionierte in dieser
 * Compose-Version nicht: die Platzhalter-\uFFFC-Zeichen wurden als
 * rohe „OBJ"-Boxen (Tofu) gerendert, Checkbox/Pfeil-Composables erschienen
 * nie — nur die SpanStyles (fett/kursiv) gingen, weil sie Teil des
 * normalen Paragraph-Renderings sind. Jetzt wieder das simple, robuste
 * Row-Layout — genau wie der alte Editor, der funktionierte ja.
 */
@Composable
internal fun NoteBodyReadOnly(
    text: String,
    onToggleCheckbox: (lineRawStart: Int) -> Unit,
    modifier: Modifier = Modifier
) {
    val contentColor = MaterialTheme.colorScheme.onSurface
    val dimColor = MaterialTheme.colorScheme.onSurfaceVariant
    val lines = NoteTextBody.toLines(text)
    // Zeilen-Anfänge im ROH-Text (für toggleCheckbox) parallel zu toLines:
    // jede Roh-Zeile ist prefix + content + "\n".
    val rawLines = if (text.isEmpty()) listOf("") else text.split("\n")

    Column(
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = 48.dp) // Tap-Fläche auch bei leerem Body
    ) {
        var rawStart = 0
        lines.forEachIndexed { index, line ->
            val lineStart = rawStart
            rawStart += (rawLines.getOrNull(index)?.length ?: 0) + 1
            ReadOnlyNoteLine(
                line = line,
                contentColor = contentColor,
                dimColor = dimColor,
                onToggleCheckbox = if (line.type == ListType.CHECKBOX) {
                    { onToggleCheckbox(lineStart) }
                } else null
            )
        }
    }
}

/** Eine Zeile im Lesemodus: Prefix-Widget + stylischer Fließtext. */
@Composable
private fun ReadOnlyNoteLine(
    line: NoteLine,
    contentColor: Color,
    dimColor: Color,
    onToggleCheckbox: (() -> Unit)?
) {
    val checked = line.type == ListType.CHECKBOX && line.checked
    val textColor = if (checked) dimColor else contentColor
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.Top
    ) {
        if (line.type == null) {
            Spacer(Modifier.width(0.dp))
        } else {
            Box(
                modifier = Modifier
                    .width(34.dp)
                    .padding(top = when (line.type) {
                        ListType.BULLET -> 10.dp
                        ListType.CHECKBOX -> 6.dp
                        ListType.ORDERED -> 6.dp
                        ListType.ARROW -> 8.dp
                        null -> 10.dp
                    }),
                contentAlignment = Alignment.CenterStart
            ) {
                when (line.type) {
                    ListType.BULLET -> BulletDot(textColor)
                    ListType.CHECKBOX -> Checkbox(
                        checked = line.checked,
                        onCheckedChange = { onToggleCheckbox?.invoke() },
                        modifier = Modifier.size(22.dp)
                    )
                    ListType.ORDERED -> Text(
                        "${line.number}.",
                        style = MaterialTheme.typography.bodyLarge,
                        color = textColor,
                        modifier = Modifier.width(34.dp)
                    )
                    ListType.ARROW -> ArrowCanvas(textColor)
                    null -> Unit
                }
            }
        }

        Text(
            text = styledContent(line.content),
            style = MaterialTheme.typography.bodyLarge.copy(color = textColor),
            modifier = Modifier.padding(vertical = 6.dp)
        )
    }
}

/** Zeilen-Inhalt mit Inline-Stilen (fett/kursiv/unterstrichen). */
private fun styledContent(content: String): AnnotatedString = buildAnnotatedString {
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