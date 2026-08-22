@file:OptIn(
    androidx.compose.material3.ExperimentalMaterial3Api::class,
    androidx.compose.foundation.ExperimentalFoundationApi::class
)

package com.earendil.todonotes.ui.notes

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.union
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.FormatBold
import androidx.compose.material.icons.filled.FormatItalic
import androidx.compose.material.icons.filled.FormatListBulleted
import androidx.compose.material.icons.filled.FormatListNumbered
import androidx.compose.material.icons.filled.FormatQuote
import androidx.compose.material.icons.filled.FormatUnderlined
import androidx.compose.material.icons.filled.RadioButtonUnchecked
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.input.key.KeyEvent
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.OffsetMapping
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.input.TransformedText
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.earendil.todonotes.data.repo.NoteRepository
import com.earendil.todonotes.data.richtext.ListType
import com.earendil.todonotes.data.richtext.NoteLine
import com.earendil.todonotes.data.richtext.NoteTextBody
import com.earendil.todonotes.ui.BackHandler
import com.earendil.todonotes.ui.NoteEditorState
import com.earendil.todonotes.ui.NoteEditorViewModel
import androidx.compose.runtime.collectAsState

/** Eine editierbare Zeile im Editor. */
private data class EditLine(
    val id: Long,
    val type: ListType?,
    val checked: Boolean,
    val value: TextFieldValue
) {
    val content: String get() = value.text
}

/** Einheitliche Breite des Prefix-Bereichs (Absatz für alle Listentypen). */
private val PREFIX_WIDTH = 34.dp

/**
 * Blendet Inline-Stil-Marker (**fett**, *kursiv*, __unterstrichen__) aus
 * und rendert den Stil direkt (bold/italic/underline). Cursor-Positionen
 * werden zwischen sichtbarem und Roh-Text gemappt, sodass der Cursor nie
 * "in" den Markern landet.
 */
private class MarkdownVisualTransformation : VisualTransformation {

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

/** Kompakte Format-Leiste (sitzt über der Tastatur). */
@Composable
private fun CompactFormatBar(
    modifier: Modifier = Modifier,
    onBullet: () -> Unit,
    onOrdered: () -> Unit,
    onCheckbox: () -> Unit,
    onArrow: () -> Unit,
    onBold: () -> Unit,
    onItalic: () -> Unit,
    onUnderline: () -> Unit
) {
    androidx.compose.material3.Surface(
        modifier = modifier,
        color = MaterialTheme.colorScheme.surfaceContainer,
        tonalElevation = 2.dp,
        shadowElevation = 2.dp
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(48.dp)
                .padding(horizontal = 8.dp),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onBold, modifier = Modifier.size(32.dp)) {
                Icon(Icons.Filled.FormatBold, contentDescription = "Fett")
            }
            IconButton(onClick = onItalic, modifier = Modifier.size(32.dp)) {
                Icon(Icons.Filled.FormatItalic, contentDescription = "Kursiv")
            }
            IconButton(onClick = onUnderline, modifier = Modifier.size(32.dp)) {
                Icon(Icons.Filled.FormatUnderlined, contentDescription = "Unterstrichen")
            }
            IconButton(onClick = onBullet, modifier = Modifier.size(32.dp)) {
                Icon(Icons.Filled.FormatListBulleted, contentDescription = "Aufzählung")
            }
            IconButton(onClick = onOrdered, modifier = Modifier.size(32.dp)) {
                Icon(Icons.Filled.FormatListNumbered, contentDescription = "Nummerierung")
            }
            IconButton(onClick = onCheckbox, modifier = Modifier.size(32.dp)) {
                Icon(Icons.Filled.RadioButtonUnchecked, contentDescription = "Checkbox")
            }
            IconButton(onClick = onArrow, modifier = Modifier.size(32.dp)) {
                Icon(Icons.Filled.FormatQuote, contentDescription = "Pfeil-Liste")
            }
        }
    }
}

/**
 * Vollbild-Notiz-Editor — kontinuierlicher Textfluss wie Word/Samsung Notes.
 *
 * Der Body wird zeilenweise gerendert. Jede Zeile ist eine Row aus
 * einem visuellen Prefix (echte Checkbox, großer runder Bullet-Punkt,
 * Zahl, Pfeil) und einem BasicTextField. Der gespeicherte Text behält
 * die Markdown-ähnlichen Präfixe (siehe [NoteTextBody]), die UI zeigt
 * sie aber als grafische Elemente mit einheitlichem Absatz.
 */
@Composable
fun NoteEditorScreen(
    noteId: String,
    isNew: Boolean,
    vm: NoteEditorViewModel,
    onBack: () -> Unit
) {
    val state by vm.state.collectAsState()

    // --- Lokaler Editierzustand ---
    var lines by remember { mutableStateOf<List<EditLine>>(emptyList()) }
    var nextLineId by remember { mutableStateOf(0L) }
    var titleValue by remember { mutableStateOf<TextFieldValue?>(null) }
    var titleSelectedOnce by remember { mutableStateOf(false) }
    var focusTarget by remember { mutableStateOf<Long?>(null) }
    var activeLineId by remember { mutableStateOf<Long?>(null) }
    val titleFocusRequester = remember { FocusRequester() }

    fun genId(): Long {
        nextLineId++
        return nextLineId
    }

    // Einmalig laden, sobald das VM-State bereit ist.
    LaunchedEffect(state.loaded) {
        if (state.loaded && lines.isEmpty()) {
            val parsed = NoteTextBody.toLines(state.body)
            lines = if (parsed.isEmpty()) {
                listOf(EditLine(genId(), null, false, TextFieldValue("")))
            } else {
                parsed.map { li ->
                    EditLine(
                        id = genId(),
                        type = li.type,
                        checked = li.checked,
                        value = TextFieldValue(li.content, TextRange(li.content.length))
                    )
                }
            }
            titleValue = TextFieldValue(state.title)
            focusTarget = lines.firstOrNull()?.id
        }
    }

    // Bei Notizwechsel: Editor-State zurücksetzen und neu laden.
    // Wichtig: lines + titleValue werden erst geleert, damit der Laded-Effekt
    // oben (der auf lines.isEmpty() prüft) wieder anspringt.
    LaunchedEffect(noteId) {
        lines = emptyList()
        titleValue = null
        titleSelectedOnce = false
        focusTarget = null
        activeLineId = null
        vm.load(noteId, isNew)
    }

    // System-Back: speichern + schließen
    BackHandler(enabled = state.loaded) {
        vm.flushNow()
        onBack()
    }

    // --- Mutationen: neue Zeilen-Liste bauen + speichern ---
    fun commit(newLines: List<EditLine>) {
        lines = newLines
        val body = NoteTextBody.fromLines(
            newLines.map { NoteLine(it.content, it.type, 0, it.checked) }
        )
        vm.updateBody(body)
    }

    fun updateLine(idx: Int, newValue: TextFieldValue) {
        if (idx !in lines.indices) return
        commit(lines.mapIndexed { i, l -> if (i == idx) l.copy(value = newValue) else l })
    }

    fun insertAfter(idx: Int) {
        if (idx !in lines.indices) return
        val type = lines[idx].type
        val newLines = lines.toMutableList()
        val newLine = EditLine(genId(), type, false, TextFieldValue(""))
        newLines.add(idx + 1, newLine)
        commit(newLines)
        focusTarget = newLine.id
        activeLineId = newLine.id
    }

    fun removeLine(idx: Int) {
        if (idx !in lines.indices || lines.size <= 1) return
        val newLines = lines.toMutableList()
        newLines.removeAt(idx)
        commit(newLines)
        focusTarget = newLines[(idx - 1).coerceAtLeast(0)].id
    }

    fun stripPrefixFrom(idx: Int) {
        if (idx !in lines.indices) return
        commit(lines.mapIndexed { i, l -> if (i == idx) l.copy(type = null, checked = false) else l })
    }

    fun handleEnter(idx: Int) {
        if (idx !in lines.indices) return
        val line = lines[idx]
        if (line.type != null && line.content.isBlank()) {
            stripPrefixFrom(idx)
        } else {
            insertAfter(idx)
        }
    }

    fun handleBackspace(idx: Int) {
        if (idx !in lines.indices) return
        val line = lines[idx]
        when {
            line.type != null -> stripPrefixFrom(idx)
            idx > 0 -> removeLine(idx)
        }
    }

    fun toolbarToggleType(type: ListType) {
        val idx = lines.indexOfFirst { it.id == activeLineId }
        if (idx < 0) return
        val line = lines[idx]
        val newType = if (line.type == type) null else type
        val checked = if (newType == ListType.CHECKBOX) line.checked else false
        commit(lines.mapIndexed { i, l -> if (i == idx) l.copy(type = newType, checked = checked) else l })
    }

    fun toggleCheckboxAt(idx: Int) {
        if (idx !in lines.indices) return
        commit(lines.mapIndexed { i, l ->
            if (i == idx && l.type == ListType.CHECKBOX) l.copy(checked = !l.checked) else l
        })
    }

    fun toggleInlineStyleActive(style: NoteTextBody.InlineStyle) {
        val idx = lines.indexOfFirst { it.id == activeLineId }
        if (idx < 0) return
        val line = lines[idx]
        val sel = line.value.selection
        val (newText, newStart, newEnd) =
            NoteTextBody.toggleInlineStyle(line.value.text, sel.min, sel.max, style)
        commit(lines.mapIndexed { i, l ->
            if (i == idx) l.copy(value = TextFieldValue(newText, TextRange(newStart, newEnd))) else l
        })
    }

    // --- UI ---

    if (!state.loaded || titleValue == null) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            CircularProgressIndicator()
        }
        return
    }

    val currentTitleValue = titleValue!!

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        topBar = {
            TopAppBar(
                title = {},
                navigationIcon = {
                    IconButton(onClick = {
                        vm.flushNow()
                        onBack()
                    }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Zurück")
                    }
                }
            )
        },
        bottomBar = {
            CompactFormatBar(
                modifier = Modifier.windowInsetsPadding(
                    WindowInsets.ime.union(WindowInsets.navigationBars)
                ),
                onBullet = { toolbarToggleType(ListType.BULLET) },
                onOrdered = { toolbarToggleType(ListType.ORDERED) },
                onCheckbox = { toolbarToggleType(ListType.CHECKBOX) },
                onArrow = { toolbarToggleType(ListType.ARROW) },
                onBold = { toggleInlineStyleActive(NoteTextBody.InlineStyle.BOLD) },
                onItalic = { toggleInlineStyleActive(NoteTextBody.InlineStyle.ITALIC) },
                onUnderline = { toggleInlineStyleActive(NoteTextBody.InlineStyle.UNDERLINE) }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 16.dp)
                .verticalScroll(rememberScrollState())
        ) {
            TextField(
                value = currentTitleValue,
                onValueChange = { newVal ->
                    titleValue = newVal
                    vm.updateTitle(newVal.text)
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .focusRequester(titleFocusRequester)
                    .onFocusChanged { focusState ->
                        if (focusState.isFocused && state.isNewNote && !titleSelectedOnce) {
                            titleSelectedOnce = true
                            titleValue = currentTitleValue.copy(
                                selection = TextRange(0, currentTitleValue.text.length)
                            )
                        }
                    },
                placeholder = { Text("Titel") },
                textStyle = MaterialTheme.typography.titleLarge,
                singleLine = true,
                colors = transparentTextFieldColors(),
                keyboardOptions = KeyboardOptions(
                    capitalization = KeyboardCapitalization.Sentences,
                    imeAction = ImeAction.Next
                )
            )

            Spacer(Modifier.height(4.dp))

            var orderedCounter = 0
            lines.forEachIndexed { idx, line ->
                val number = if (line.type == ListType.ORDERED) {
                    orderedCounter++
                    orderedCounter
                } else {
                    orderedCounter = 0
                    0
                }
                NoteLineEditor(
                    line = line,
                    number = number,
                    isFocused = activeLineId == line.id,
                    onChange = { newValue ->
                        updateLine(idx, newValue)
                        activeLineId = line.id
                    },
                    onFocus = { activeLineId = line.id },
                    onToggleCheckbox = { toggleCheckboxAt(idx) },
                    onEnter = { handleEnter(idx) },
                    onBackspace = { handleBackspace(idx) },
                    focusTarget = focusTarget == line.id
                )
            }

            Spacer(Modifier.height(80.dp))
        }
    }
}

/** Eine Zeile des Editors: Prefix-Widget + Textfeld. */
@Composable
private fun NoteLineEditor(
    line: EditLine,
    number: Int,
    isFocused: Boolean,
    onChange: (TextFieldValue) -> Unit,
    onFocus: () -> Unit,
    onToggleCheckbox: () -> Unit,
    onEnter: () -> Unit,
    onBackspace: () -> Unit,
    focusTarget: Boolean
) {
    val focusRequester = remember(line.id) { FocusRequester() }
    LaunchedEffect(focusTarget, line.id) {
        if (focusTarget) {
            focusRequester.requestFocus()
        }
    }

    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.Top
    ) {
        if (line.type == null) {
            Spacer(Modifier.width(0.dp))
        } else {
            Box(
                modifier = Modifier
                    .width(PREFIX_WIDTH)
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
                    ListType.BULLET -> BulletDot()
                    ListType.CHECKBOX -> Checkbox(
                        checked = line.checked,
                        onCheckedChange = { onToggleCheckbox() },
                        modifier = Modifier.size(22.dp)
                    )
                    ListType.ORDERED -> Text(
                        "$number.",
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.width(PREFIX_WIDTH)
                    )
                    ListType.ARROW -> ArrowIcon()
                    null -> Unit
                }
            }
        }

        BasicTextField(
            value = line.value,
            onValueChange = onChange,
            modifier = Modifier
                .weight(1f)
                .focusRequester(focusRequester)
                .onFocusChanged { focusState ->
                    if (focusState.isFocused) onFocus()
                }
                .onPreviewKeyEvent { event ->
                    if (event.type == KeyEventType.KeyDown) {
                        when (event.key) {
                            Key.Enter -> {
                                onEnter()
                                true
                            }
                            Key.Backspace -> {
                                if (line.value.text.isEmpty()) {
                                    onBackspace()
                                    true
                                } else false
                            }
                            else -> false
                        }
                    } else false
                }
                .padding(vertical = 6.dp),
            textStyle = MaterialTheme.typography.bodyLarge.copy(
                color = if (line.type == ListType.CHECKBOX && line.checked)
                    MaterialTheme.colorScheme.onSurfaceVariant
                else MaterialTheme.colorScheme.onSurface
            ),
            visualTransformation = remember(line.id) { MarkdownVisualTransformation() },
            cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
            keyboardOptions = KeyboardOptions(
                capitalization = KeyboardCapitalization.Sentences,
                imeAction = ImeAction.Default
            )
        )
    }
}

/** Großer runder Bullet-Punkt. */
@Composable
private fun BulletDot() {
    val color = MaterialTheme.colorScheme.onSurfaceVariant
    Canvas(modifier = Modifier.size(20.dp)) {
        drawCircle(color = color, radius = size.minDimension * 0.30f)
    }
}

/** Pfeil-Icon (→) als Canvas-Zeichnung, da das Unicode-Zeichen in Wasm
 *  nicht in jeder Schriftart enthalten ist. */
@Composable
private fun ArrowIcon() {
    val color = MaterialTheme.colorScheme.onSurfaceVariant
    Canvas(modifier = Modifier.size(20.dp)) {
        val w = size.width
        val h = size.height
        val centerY = h / 2f
        // Linie: vom linken Rand (mit etwas Padding) nach rechts
        val startX = w * 0.15f
        val endX = w * 0.85f
        drawLine(
            color = color,
            start = androidx.compose.ui.geometry.Offset(startX, centerY),
            end = androidx.compose.ui.geometry.Offset(endX, centerY),
            strokeWidth = w * 0.08f
        )
        // Pfeilspitze: zwei Linien vom Endpunkt aus nach unten-links und oben-links
        val arrowSize = w * 0.25f
        drawLine(
            color = color,
            start = androidx.compose.ui.geometry.Offset(endX, centerY),
            end = androidx.compose.ui.geometry.Offset(endX - arrowSize, centerY - arrowSize * 0.7f),
            strokeWidth = w * 0.08f
        )
        drawLine(
            color = color,
            start = androidx.compose.ui.geometry.Offset(endX, centerY),
            end = androidx.compose.ui.geometry.Offset(endX - arrowSize, centerY + arrowSize * 0.7f),
            strokeWidth = w * 0.08f
        )
    }
}

@Composable
private fun transparentTextFieldColors() = TextFieldDefaults.colors(
    focusedContainerColor = Color.Transparent,
    unfocusedContainerColor = Color.Transparent,
    focusedIndicatorColor = Color.Transparent,
    unfocusedIndicatorColor = Color.Transparent
)
