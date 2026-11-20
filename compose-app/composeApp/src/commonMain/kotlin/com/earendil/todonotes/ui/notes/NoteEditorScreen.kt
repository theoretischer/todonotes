@file:OptIn(
    androidx.compose.material3.ExperimentalMaterial3Api::class
)

package com.earendil.todonotes.ui.notes

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
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.OffsetMapping
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.input.TransformedText
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import com.earendil.todonotes.data.richtext.ListType
import com.earendil.todonotes.data.richtext.NoteTextBody
import com.earendil.todonotes.ui.BackHandler
import com.earendil.todonotes.ui.NoteEditorViewModel
import androidx.compose.runtime.collectAsState
import kotlinx.coroutines.flow.first

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
 * Vollbild-Notiz-Editor — EIN mehrzeiliges Textfeld (Bug-3-Fix).
 *
 * Vorher: jede Zeile war ein eigenes BasicTextField in einer Row mit
 * grafischem Prefix-Widget. Das sah hübsch aus, hatte aber zwei
 * Follow-Bugs: Pfeiltasten/Mehrzeilen-Markierung gingen nicht über
 * Feldgrenzen hinaus (Compose kann das prinzipiell nicht), und die
 * Zeilen-Reparatur-Logik (Enter/Backspace fängt, Zeile löschen) war
 * fehleranfällig.
 *
 * Jetzt: der komplette Body ist EIN mehrzeiliges BasicTextField.
 *  - Pfeiltasten, Maus-Markierung über Zeilen, Handy-Selektion: natives
 *    Verhalten, funktioniert automatisch.
 *  - Die Listen-Präfixe (- , [ ] , 1. , → ) bleiben als TEXT im Feld
 *    stehen (wie Markdown-Quelle) — KEIN hübsches Rendering im Editor
 *    (bewusst einfach gehalten, siehe PROGRESS.md). Die Notiz-Liste
 *    rendert die Vorschau weiterhin über [NoteTextBody].
 *  - Fett/Kursiv/Unterstrichen werden weiter über die
 *    [MarkdownVisualTransformation] direkt angezeigt.
 *  - Enter in einer Listen-Zeile: fügt \n + passenden Präfix ein
 *    (Checkbox → "[ ] ", geordnet → nächste Nummer). Enter in einer
 *    LEEREN Listen-Zeile: entfernt den Präfix (= Liste verlassen).
 *  - Toolbar: Typ-Toggle auf der Cursor-Zeile. Der Checkbox-Button ist
 *    schlau: auf einer Checkbox-Zeile toggled er [ ]↔[x] (abhaken!),
 *    sonst macht er die Zeile zur Checkbox (bzw. nimmt den Präfix weg).
 */
@Composable
fun NoteEditorScreen(
    noteId: String,
    isNew: Boolean,
    vm: NoteEditorViewModel,
    onBack: () -> Unit,
    initialTitle: String? = null,
    initialBody: String? = null,
    syncState: com.earendil.todonotes.data.sync.SyncManager.SyncState =
        com.earendil.todonotes.data.sync.SyncManager.SyncState()
) {
    val state by vm.state.collectAsState()

    // --- Lokaler Editierzustand: Titel + EIN Body-Feld ---
    var titleValue by remember { mutableStateOf<TextFieldValue?>(null) }
    var bodyValue by remember { mutableStateOf<TextFieldValue?>(null) }
    var titleSelectedOnce by remember { mutableStateOf(false) }
    val titleFocusRequester = remember { FocusRequester() }

    // Bei Notizwechsel: Editor-State zurücksetzen und neu laden.
    LaunchedEffect(noteId) {
        titleValue = null
        bodyValue = null
        titleSelectedOnce = false
        vm.load(noteId, isNew, initialTitle, initialBody)
        // Warten bis VM loaded=true, dann Titel + Body setzen.
        val s = vm.state.first { it.loaded }
        titleValue = TextFieldValue(s.title)
        bodyValue = TextFieldValue(s.body, TextRange(s.body.length))
    }

    // Neue Notiz: Titel fokussieren (Select-all via onFocusChanged unten),
    // dann per Tastatur "Next" in den Body.
    LaunchedEffect(state.loaded, state.isNewNote) {
        if (state.loaded && state.isNewNote) {
            titleFocusRequester.requestFocus()
        }
    }

    // Sync-A+: Notiz wurde von einem anderen Gerät gelöscht → Editor
    // schließen (statt zu überschreiben = Datenverlust).
    LaunchedEffect(state.deleted) {
        if (state.deleted) {
            // Nicht flushen — die Notiz ist weg.
            onBack()
        }
    }

    // Sync-A+: Remote-Update übernehmen. Das VM gated das bereits über
    // dirty (kein Update während der Nutzer tippt / 500ms-Debounce läuft)
    // — hier ankommen heißt also: Nutzer war idle. Cursor behalten
    // (auf neue Textlänge geclampt).
    LaunchedEffect(state.remoteUpdate) {
        if (state.remoteUpdate == 0) return@LaunchedEffect
        if (!state.loaded || bodyValue == null) return@LaunchedEffect
        val v = bodyValue!!
        if (state.body == v.text && state.title == (titleValue?.text ?: "")) {
            return@LaunchedEffect // nichts zu tun
        }
        val len = state.body.length
        val sel = v.selection
        bodyValue = TextFieldValue(
            state.body,
            TextRange(sel.min.coerceAtMost(len), sel.max.coerceAtMost(len))
        )
        titleValue = TextFieldValue(state.title)
    }

    // System-Back: speichern + schließen
    BackHandler(enabled = state.loaded) {
        vm.flushNow()
        onBack()
    }

    // --- Mutations-Helfer (alles in Roh-Text-Koordinaten — das Feld
    //     arbeitet in Roh-Koordinaten, die Transformation ist Anzeige) ---

    fun commitBody(newValue: TextFieldValue) {
        bodyValue = newValue
        vm.updateBody(newValue.text)
    }

    /** Zeilen-Anfang der Zeile, in der [offset] liegt. */
    fun lineStartOf(text: String, offset: Int): Int =
        if (offset <= 0) 0 else text.lastIndexOf('\n', offset - 1) + 1

    /** Zeilen-Ende (exklusiv, ohne \n) ab [lineStart]. */
    fun lineEndOf(text: String, lineStart: Int): Int =
        text.indexOf('\n', lineStart).let { if (it < 0) text.length else it }

    /** Laufende Nummer einer geordneten Zeile = Zahl vorheriger
     *  aufeinanderfolgender ORDERED-Zeilen + 1 (für Enter-Fortsetzung
     *  und Toolbar-Nummerierung). */
    fun orderedNumber(text: String, lineStart: Int): Int {
        var n = 0
        var s = lineStart
        while (s > 0) {
            val prevStart = lineStartOf(text, s - 1)
            val prev = text.substring(prevStart, s)
            if (NoteTextBody.detectListType(prev) != ListType.ORDERED) break
            n++
            s = prevStart
        }
        return n + 1
    }

    /**
     * Toolbar: Listen-Typ der Cursor-Zeile togglen.
     *  - Zeile hat schon den Typ → Präfix entfernen (aus)
     *  - sonst → Präfix setzen (Checkbox: [ ] ; geordnet: nächste Nummer)
     *  - Checkbox-Sonderfall: Zeile IST schon eine Checkbox → [ ]↔[x]
     *    togglen (abhaken/anhacken!)
     */
    fun toggleListType(type: ListType) {
        val v = bodyValue ?: return
        val text = v.text
        val lineStart = lineStartOf(text, v.selection.min)
        val lineEnd = lineEndOf(text, lineStart)
        val fullLine = text.substring(lineStart, lineEnd)
        val currentType = NoteTextBody.detectListType(fullLine)
        val content = NoteTextBody.stripPrefix(fullLine)

        val newLine: String = when {
            type == ListType.CHECKBOX && currentType == ListType.CHECKBOX -> {
                // Abhaken / anhacken über den Toolbar-Button
                if (NoteTextBody.isCheckboxChecked(fullLine))
                    NoteTextBody.CHECKBOX_OPEN + content
                else NoteTextBody.CHECKBOX_DONE + content
            }
            currentType == type -> content // schon der Typ → Präfix raus
            type == ListType.ORDERED -> "${orderedNumber(text, lineStart)}. $content"
            type == ListType.BULLET -> NoteTextBody.BULLET_PREFIX + content
            type == ListType.ARROW -> NoteTextBody.ARROW_PREFIX + content
            else -> NoteTextBody.CHECKBOX_OPEN + content // CHECKBOX neu setzen
        }
        val newText = text.substring(0, lineStart) + newLine + text.substring(lineEnd)
        // Cursor mitverschieben: vor der Zeile unverändert, in der Zeile
        // ans neue Zeilenende, dahinter um die Längen-Differenz.
        val delta = newLine.length - fullLine.length
        fun map(offset: Int): Int = when {
            offset <= lineStart -> offset
            offset >= lineEnd -> offset + delta
            else -> lineStart + newLine.length
        }
        commitBody(TextFieldValue(newText, TextRange(map(v.selection.min), map(v.selection.max))))
    }

    fun toggleInlineStyleActive(style: NoteTextBody.InlineStyle) {
        val v = bodyValue ?: return
        val (newText, newStart, newEnd) =
            NoteTextBody.toggleInlineStyle(v.text, v.selection.min, v.selection.max, style)
        commitBody(TextFieldValue(newText, TextRange(newStart, newEnd)))
    }

    // --- UI ---

    if (!state.loaded || titleValue == null || bodyValue == null) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            CircularProgressIndicator()
        }
        return
    }

    val currentTitleValue = titleValue!!
    val currentBodyValue = bodyValue!!

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
                },
                actions = {
                    com.earendil.todonotes.ui.components.SyncIndicator(
                        syncState = syncState,
                        modifier = Modifier.padding(end = 12.dp)
                    )
                }
            )
        },
        bottomBar = {
            CompactFormatBar(
                modifier = Modifier.windowInsetsPadding(
                    WindowInsets.ime.union(WindowInsets.navigationBars)
                ),
                onBullet = { toggleListType(ListType.BULLET) },
                onOrdered = { toggleListType(ListType.ORDERED) },
                onCheckbox = { toggleListType(ListType.CHECKBOX) },
                onArrow = { toggleListType(ListType.ARROW) },
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

            // EIN mehrzeiliges Feld für den kompletten Body (Bug-3-Fix):
            // Pfeiltasten + Mehrzeilen-Markierung sind natives Verhalten.
            BasicTextField(
                value = currentBodyValue,
                onValueChange = { commitBody(it) },
                modifier = Modifier
                    .fillMaxWidth()
                    .onPreviewKeyEvent { event ->
                        if (event.type != KeyEventType.KeyDown || event.key != Key.Enter) {
                            return@onPreviewKeyEvent false
                        }
                        val v = bodyValue ?: return@onPreviewKeyEvent false
                        val text = v.text
                        val cursor = v.selection.min
                        val lineStart = lineStartOf(text, cursor)
                        val lineEnd = lineEndOf(text, lineStart)
                        val fullLine = text.substring(lineStart, lineEnd)
                        val type = NoteTextBody.detectListType(fullLine)
                        val content = NoteTextBody.stripPrefix(fullLine)
                        when {
                            type == null -> false // normale Zeile: Default-\n
                            content.isBlank() -> {
                                // Leere Listen-Zeile: Präfix raus (= Liste
                                // verlassen), KEIN Zeilenumbruch — wie vorher.
                                val newText = text.substring(0, lineStart) + text.substring(lineEnd)
                                commitBody(TextFieldValue(newText, TextRange(lineStart)))
                                true
                            }
                            else -> {
                                // Listen-Fortsetzung: \n + passender Präfix.
                                // Checkbox: immer [ ] (neu abhakbar). Geordnet:
                                // nächste Nummer (Vorzeilen zählen).
                                val prefix = when (type) {
                                    ListType.BULLET -> NoteTextBody.BULLET_PREFIX
                                    ListType.CHECKBOX -> NoteTextBody.CHECKBOX_OPEN
                                    ListType.ARROW -> NoteTextBody.ARROW_PREFIX
                                    // Fortsetzung: NÄCHSTE Nummer = aktuelle + 1
                                    ListType.ORDERED -> "${orderedNumber(text, lineStart) + 1}. "
                                }
                                val newText = text.substring(0, cursor) + "\n" + prefix + text.substring(cursor)
                                val newCursor = cursor + 1 + prefix.length
                                commitBody(TextFieldValue(newText, TextRange(newCursor)))
                                true
                            }
                        }
                    }
                    .padding(vertical = 6.dp),
                textStyle = MaterialTheme.typography.bodyLarge.copy(
                    color = MaterialTheme.colorScheme.onSurface
                ),
                visualTransformation = remember { MarkdownVisualTransformation() },
                cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                keyboardOptions = KeyboardOptions(
                    capitalization = KeyboardCapitalization.Sentences,
                    imeAction = ImeAction.Default
                )
            )

            Spacer(Modifier.height(80.dp))
        }
    }
}

@Composable
private fun transparentTextFieldColors() = TextFieldDefaults.colors(
    focusedContainerColor = Color.Transparent,
    unfocusedContainerColor = Color.Transparent,
    focusedIndicatorColor = Color.Transparent,
    unfocusedIndicatorColor = Color.Transparent
)