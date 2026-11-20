@file:OptIn(
    androidx.compose.material3.ExperimentalMaterial3Api::class
)

package com.earendil.todonotes.ui.notes

import androidx.compose.foundation.clickable
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
import androidx.compose.material.icons.filled.Check
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
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import com.earendil.todonotes.data.richtext.ListType
import com.earendil.todonotes.data.richtext.NoteTextBody
import com.earendil.todonotes.ui.BackHandler
import com.earendil.todonotes.ui.NoteEditorViewModel
import androidx.compose.runtime.collectAsState
import kotlinx.coroutines.flow.first

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
 * Vollbild-Notiz-Editor — EIN mehrzeiliges Textfeld (Bug-3-Fix) mit
 * hübscher Präfix-Darstellung (Bug-3b-Fix).
 *
 * Bug 3: Vorher war jede Zeile ein eigenes BasicTextField → Pfeiltasten
 * und Mehrzeilen-Markierung gingen prinzipbedingt nicht (Compose kann
 * Cursor/Selektion nicht über Feldgrenzen bewegen). Fix: EIN Feld.
 *
 * Bug 3b: Der erste Fix zeigte die Präfixe als reinen Text ("- ", "[ ] ").
 * Fix: [NoteBodyVisualTransformation] rendert die Präfixe NUR für die
 * Anzeige als Symbole (•, ☐/☑, fortlaufende Nummer) — der Roh-Text
 * (und damit DB/Sync) bleibt unverändert. Pfeiltasten/Markierung
 * funktionieren weiter nativ, weil die Cursor-Koordinaten über
 * Offset-Mappings zwischen Roh- und Anzeige-Text übersetzt werden.
 *
 * Toolbar + Enter arbeiten in ROH-Koordinaten (der gespeicherte Text),
 * die Cursor-Position wird dafür aus den Anzeige-Koordinaten
 * zurückgemappt ([ListPrefixRenderer.transfToOrig] via
 * [NoteTextBody.visualToRawOffset] und zurück).
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

    // --- Modus: Lesemodus (schöne Widgets) ↔ Edit-Modus (ein Textfeld) ---
    // Option B: Notiz standardmäßig LESBAR mit echten Checkboxen/Pfeil/
    // Bullet (wie der alte Per-Zeilen-Editor). Tap auf den Text → Edit-
    // Modus (EIN mehrzeiliges Feld: Pfeiltasten + Mehrzeilen-Markierung,
    // Bug 3). „Fertig“-Button (Häkchen) → zurück in den Lesemodus.
    var editing by remember { mutableStateOf(false) }
    // Beim Wechsel in den Edit-Modus: Cursor ans Textende + Fokus anfordern.
    var focusEditOnSwitch by remember { mutableStateOf(false) }
    val bodyFocusRequester = remember { FocusRequester() }

    // --- Lokaler Editierzustand: Titel + EIN Body-Feld (Roh-Text) ---
    var titleValue by remember { mutableStateOf<TextFieldValue?>(null) }
    var bodyValue by remember { mutableStateOf<TextFieldValue?>(null) }
    var titleSelectedOnce by remember { mutableStateOf(false) }
    val titleFocusRequester = remember { FocusRequester() }
    val dimColor = MaterialTheme.colorScheme.onSurfaceVariant

    // Bei Notizwechsel: Editor-State zurücksetzen und neu laden.
    LaunchedEffect(noteId) {
        titleValue = null
        bodyValue = null
        titleSelectedOnce = false
        editing = false
        vm.load(noteId, isNew, initialTitle, initialBody)
        val s = vm.state.first { it.loaded }
        titleValue = TextFieldValue(s.title)
        bodyValue = TextFieldValue(s.body, TextRange(s.body.length))
        // Neue Notiz direkt in den Edit-Modus (Titel fokussiert).
        if (isNew) editing = true
    }

    // Beim Wechsel in den Edit-Modus: Fokus ins Body-Feld holen (Cursor
    // ans Ende via TextFieldValue im Body selbst). Läuft nach dem
    // Rekompilieren von editing=true.
    LaunchedEffect(editing) {
        if (editing && focusEditOnSwitch) {
            focusEditOnSwitch = false
            bodyFocusRequester.requestFocus()
        }
    }

    // Neue Notiz: Titel fokussieren (Select-all via onFocusChanged unten).
    LaunchedEffect(state.loaded, state.isNewNote) {
        if (state.loaded && state.isNewNote) {
            titleFocusRequester.requestFocus()
        }
    }

    // Sync-A+: Notiz wurde von einem anderen Gerät gelöscht → Editor schließen.
    LaunchedEffect(state.deleted) {
        if (state.deleted) {
            onBack() // nicht flushen — die Notiz ist weg
        }
    }

    // Sync-A+: Remote-Update übernehmen (VM gated über dirty — hier ankommen
    // heißt: Nutzer war idle). Cursor behalten (auf neue Länge geclampt).
    LaunchedEffect(state.remoteUpdate) {
        if (state.remoteUpdate == 0) return@LaunchedEffect
        if (!state.loaded || bodyValue == null) return@LaunchedEffect
        val v = bodyValue!!
        if (state.body == v.text && state.title == (titleValue?.text ?: "")) {
            return@LaunchedEffect
        }
        val len = state.body.length
        val sel = v.selection
        bodyValue = TextFieldValue(
            state.body,
            TextRange(sel.min.coerceAtMost(len), sel.max.coerceAtMost(len))
        )
        titleValue = TextFieldValue(state.title)
    }

    // System-Back: Edit-Modus → erst zurück in den Lesemodus; sonst speichern + schließen
    BackHandler(enabled = state.loaded) {
        if (editing) {
            editing = false
            vm.flushNow()
        } else {
            vm.flushNow()
            onBack()
        }
    }

    // --- Mutations-Helfer (Roh-Koordinaten!) ---

    fun commitBody(newValue: TextFieldValue) {
        bodyValue = newValue
        vm.updateBody(newValue.text)
    }

    /** Lesemodus: Checkbox an Zeilen-Anfang [lineRawStart] togglen. */
    fun toggleCheckboxAt(lineRawStart: Int) {
        val text = bodyValue?.text ?: return
        val newText = NoteTextBody.toggleCheckbox(text, lineRawStart)
        val sel = bodyValue!!.selection
        val len = newText.length
        commitBody(TextFieldValue(newText, TextRange(sel.min.coerceAtMost(len), sel.max.coerceAtMost(len))))
    }

    fun lineStartOf(text: String, offset: Int): Int =
        if (offset <= 0) 0 else text.lastIndexOf('\n', offset - 1) + 1

    fun lineEndOf(text: String, lineStart: Int): Int =
        text.indexOf('\n', lineStart).let { if (it < 0) text.length else it }

    /** Fortlaufende NEUE Nummer für eine ORDERED-Zeile (wie die Anzeige). */
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

    /** Cursor-Position (Roh-Text — Selection in TextFieldValue ist bereits Roh). */
    fun rawCursor(v: TextFieldValue): Int = v.selection.min

    /**
     * Toolbar: Listen-Typ der Cursor-Zeile togglen.
     * Checkbox-Button auf einer Checkbox-Zeile: [ ]↔[x] togglen (abhaken!).
     */
    fun toggleListType(type: ListType) {
        val v = bodyValue ?: return
        val text = v.text
        val cursor = rawCursor(v)
        val lineStart = lineStartOf(text, cursor)
        val lineEnd = lineEndOf(text, lineStart)
        val fullLine = text.substring(lineStart, lineEnd)
        val currentType = NoteTextBody.detectListType(fullLine)
        val content = NoteTextBody.stripPrefix(fullLine)

        val newLine: String = when {
            type == ListType.CHECKBOX && currentType == ListType.CHECKBOX -> {
                if (NoteTextBody.isCheckboxChecked(fullLine))
                    NoteTextBody.CHECKBOX_OPEN + content
                else NoteTextBody.CHECKBOX_DONE + content
            }
            currentType == type -> content
            type == ListType.ORDERED -> "${orderedNumber(text, lineStart)}. $content"
            type == ListType.BULLET -> NoteTextBody.BULLET_PREFIX + content
            type == ListType.ARROW -> NoteTextBody.ARROW_PREFIX + content
            else -> NoteTextBody.CHECKBOX_OPEN + content
        }
        val newText = text.substring(0, lineStart) + newLine + text.substring(lineEnd)
        val delta = newLine.length - fullLine.length
        fun map(offset: Int): Int = when {
            offset <= lineStart -> offset
            offset >= lineEnd -> offset + delta
            else -> lineStart + newLine.length
        }
        commitBody(TextFieldValue(newText, TextRange(map(cursor), map(cursor))))
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
                    // Modus-Wechsel (Option B): Häkchen = zurück in den Lesemodus
                    // (schöne Widgets); im Lesemodus Tippen auf den Text → Edit.
                    if (editing) {
                        IconButton(onClick = {
                            editing = false
                            vm.flushNow()
                        }) {
                            Icon(Icons.Filled.Check, contentDescription = "Fertig")
                        }
                    }
                    com.earendil.todonotes.ui.components.SyncIndicator(
                        syncState = syncState,
                        modifier = Modifier.padding(end = 12.dp)
                    )
                }
            )
        },
        bottomBar = {
            // Format-Leiste nur im Edit-Modus (im Lesemodus gibt's nichts
            // zu formatieren — die Checkbox togglet direkt).
            if (editing) {
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

            if (!editing) {
                // ----- LESEMODUS (Option B): echte Widgets wie früher -----
                // GANZER Block klickbar → Edit-Modus. Checkboxen fangen ihren
                // Tap selbst ab (kein Modus-Wechsel, togglen direkt).
                NoteBodyReadOnly(
                    text = currentBodyValue.text,
                    onToggleCheckbox = { lineStart -> toggleCheckboxAt(lineStart) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable {
                            // In den Edit-Modus: Cursor ans Textende + Fokus.
                            val t = bodyValue?.text ?: ""
                            bodyValue = t.let { TextFieldValue(it, TextRange(it.length)) }
                            focusEditOnSwitch = true
                            editing = true
                        }
                )
            } else {
                // ----- EDIT-MODUS (Bug 3): EIN mehrzeiliges Feld -----
                // Pfeiltasten + Mehrzeilen-Markierung nativ.
                BasicTextField(
                    value = currentBodyValue,
                    onValueChange = { commitBody(it) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .focusRequester(bodyFocusRequester)
                        .onPreviewKeyEvent { event ->
                            if (event.type != KeyEventType.KeyDown || event.key != Key.Enter) {
                                return@onPreviewKeyEvent false
                            }
                            val v = bodyValue ?: return@onPreviewKeyEvent false
                            val text = v.text
                            val cursor = rawCursor(v)
                            val lineStart = lineStartOf(text, cursor)
                            val lineEnd = lineEndOf(text, lineStart)
                            val fullLine = text.substring(lineStart, lineEnd)
                            val type = NoteTextBody.detectListType(fullLine)
                            val content = NoteTextBody.stripPrefix(fullLine)
                            when {
                                type == null -> false // normale Zeile: Default-\n
                                content.isBlank() -> {
                                    // Leere Listen-Zeile: Präfix raus (= Liste
                                    // verlassen), kein Zeilenumbruch — wie vorher.
                                    val newText = text.substring(0, lineStart) + text.substring(lineEnd)
                                    commitBody(TextFieldValue(newText, TextRange(lineStart)))
                                    true
                                }
                                else -> {
                                    // Listen-Fortsetzung: \n + passender Präfix.
                                    // Checkbox: immer [ ] (frisch). Geordnet: nächste Nummer.
                                    val prefix = when (type) {
                                        ListType.BULLET -> NoteTextBody.BULLET_PREFIX
                                        ListType.CHECKBOX -> NoteTextBody.CHECKBOX_OPEN
                                        ListType.ARROW -> NoteTextBody.ARROW_PREFIX
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
                    cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                    keyboardOptions = KeyboardOptions(
                        capitalization = KeyboardCapitalization.Sentences,
                        imeAction = ImeAction.Default
                    )
                )
            }

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