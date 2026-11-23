@file:OptIn(
    androidx.compose.material3.ExperimentalMaterial3Api::class
)

package com.earendil.todonotes.ui.notes

import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInParent
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.isShiftPressed
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.font.FontWeight
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
        // M14h: surface wie die TopBar (vorher surfaceContainer = das
        // Grau, das sonst nirgends als Fläche vorkommt).
        color = MaterialTheme.colorScheme.surface
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
    // Bug 1: Cursor-Position beim Verlassen des Edit-Modus („Fertig“-Check)
    // MERKEN und beim nächsten Edit-Wechsel wiederherstellen — sonst springt
    // der Cursor an den Zeilenanfang, obwohl man mitten in einer Zeile war.
    var savedCursor by remember { mutableStateOf(-1) }
    val bodyFocusRequester = remember { FocusRequester() }

    // --- Bug 5: Cursor-Sichtbarkeit beim Tippen (Nutzer-Report: „springt
    //     garnicht mehr, Getipptes wird von der BottomBar verdeckt“) ---
    // Das automatische bringIntoView klemmte im Scaffold-Inge-setup. Wir
    // messen die Cursor-Position SELBST (Feld-Textlayout + Feld-Offset in
    // der Column) und scrollen die Column manuell — mit Luft nach unten,
    // weil die Format-Leiste/Tastatur den unteren Teil verdeckt.
    val editorScroll = rememberScrollState()
    var editorViewportH by remember { mutableStateOf(0) }        // Column-Hoehe in px
    var fieldOffsetInColumn by remember { mutableStateOf(0f) }   // Feld-Top in Column-Px
    var fieldLayout by remember { mutableStateOf<TextLayoutResult?>(null) }
    var cursorOffset by remember { mutableStateOf(0) } // aktualisiert in commitBody()/Edit-Wechsel
    // Punkt 6: Zeitstempel des letzten Checkbox-Button-Taps (Doppeltipp-
    // Erkennung, < 400ms). Kein Compose-State nötig — wird nur im
    // onClick-Handler gelesen/geschrieben (keine Rekompilierung nötig).
    var lastCheckboxTapMs by remember { mutableStateOf(0L) }

    // --- Lokaler Editierzustand: EIN Body-Feld (Roh-Text) ---
    // Titel: NICHT mehr als Feld im Content, sondern INLINE in der TopBar
    // (wie Chat-Notizen — Konsistenz, Nutzer-Wunsch). Lesemodus = Text,
    // Tap → Edit; Enter/Fokus-Verlust → speichern.
    var bodyValue by remember { mutableStateOf<TextFieldValue?>(null) }
    var isEditingTitle by remember { mutableStateOf(false) }
    var titleText by remember { mutableStateOf(TextFieldValue("")) }
    val titleFocusRequester = remember { FocusRequester() }
    var hadTitleFocus by remember { mutableStateOf(false) }
    val dimColor = MaterialTheme.colorScheme.onSurfaceVariant

    // Bei Notizwechsel: Editor-State zurücksetzen und neu laden.
    LaunchedEffect(noteId) {
        bodyValue = null
        editing = false
        isEditingTitle = false
        hadTitleFocus = false
        vm.load(noteId, isNew, initialTitle, initialBody)
        val s = vm.state.first { it.loaded }
        bodyValue = TextFieldValue(s.body, TextRange(s.body.length))
        titleText = TextFieldValue(s.title)
        // Neue Notiz direkt in den Edit-Modus — Titel in der TopBar
        // fokussieren (wie im Chat).
        if (isNew) isEditingTitle = true
    }

    // Beim Wechsel in den Edit-Modus (Body): Fokus ins Body-Feld holen
    // (Cursor-Position kommt aus dem TextFieldValue selbst).
    LaunchedEffect(editing) {
        if (editing && focusEditOnSwitch) {
            focusEditOnSwitch = false
            bodyFocusRequester.requestFocus()
        }
        // Bug 1: Edit-Modus VERLASSEN („Fertig“-Check, Back) → Cursor merken.
        if (!editing) savedCursor = bodyValue?.selection?.min ?: -1
    }

    // Bug 5: Bei jeder Cursor-Bewegung (Tippen/Schreiben) die Cursor-Zeile
    // in den sichtbaren Bereich scrollen. Cursor auf ~35% der Viewport-Hoehe
    // — genug Abstand zur Format-Leiste/Tastatur darunter.
    LaunchedEffect(cursorOffset, editing, editorViewportH) {
        if (!editing) return@LaunchedEffect
        val layout = fieldLayout ?: return@LaunchedEffect
        if (editorViewportH <= 0) return@LaunchedEffect
        val cursorRect = try {
            layout.getCursorRect(cursorOffset)
        } catch (e: Throwable) {
            return@LaunchedEffect
        }
        val cursorY = fieldOffsetInColumn + (cursorRect.top + cursorRect.bottom) / 2f
        val target = (cursorY - editorViewportH * 0.35f)
            .coerceAtLeast(0f)
            .toInt()
            .coerceIn(0, editorScroll.maxValue)
        if (kotlin.math.abs(target - editorScroll.value) > 2) {
            try {
                editorScroll.animateScrollTo(target, androidx.compose.animation.core.tween(150))
            } catch (_: Throwable) { /* Wasm: Abbruch beim Neustart ist OK */ }
        }
    }

    // Fokus auf Titel-Feld wenn Titel-Edit startet.
    LaunchedEffect(isEditingTitle) {
        if (isEditingTitle) titleFocusRequester.requestFocus()
    }
    // Titel-Markierung EINEN FRAME NACH dem Fokus setzen (wie Chat):
    // Das TextField positioniert beim Tap/Fokus selbst den Cursor (im
    // selben Frame) und überschreibt eine synchron gesetzte Selection.
    // Ein Frame später gewinnt unsere Select-All-Selection → Tippen
    // überschreibt direkt.
    LaunchedEffect(isEditingTitle) {
        if (isEditingTitle) {
            kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Default) {
                androidx.compose.runtime.withFrameNanos { }
            }
            titleText = titleText.copy(selection = TextRange(0, titleText.text.length))
        }
    }

    // Verlässt das Titel-Feld den Fokus → speichern + Edit-Modus beenden.
    // (onFocusChanged feuert auch beim ERSTEN Zeichnen mit isFocused=false
    // — deshalb merken wir uns, ob das Feld VORHER fokussiert war.)
    val focusManager = LocalFocusManager.current
    fun commitTitleEdit() {
        val newTitle = titleText.text
        // Nur wirklich ändern/speichern, wenn was geändert wurde — sonst
        // würde ein reiner Fokus-Verlust (z.B. Back ins Chat) dirty machen
        // und den updatedAt-Stamp (LWW) anstoßen.
        if (newTitle != state.title) vm.updateTitle(newTitle)
        isEditingTitle = false
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
        if (state.body == v.text && state.title == titleText.text) {
            return@LaunchedEffect
        }
        val len = state.body.length
        val sel = v.selection
        bodyValue = TextFieldValue(
            state.body,
            TextRange(sel.min.coerceAtMost(len), sel.max.coerceAtMost(len))
        )
        titleText = TextFieldValue(state.title)
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
        cursorOffset = newValue.selection.min // Bug 5: Cursor-Scroll auslösen
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
     * CHECKBOX-Semantik (Punkt 6):
     *  - Einfach-Tipp: keine Checkbox → [ ] einfügen. Checkbox ([ ] ODER
     *    [x]) vorhanden → entfernen (Toggle aus).
     *  - Doppeltipp ohne Checkbox → direkt [x] (angehakt) einfügen.
     *  - Achtung Doppeltipp-Sequenz: Der 1. Tipp fügt bereits [ ] ein —
     *    der 2. Tipp (als Doppeltipp erkannt) muss das FRISCH eingefügte
     *    [ ] in [x] UMWANDELN, nicht entfernen. Deshalb: [checked] =
     *    „2. Tipp kurz nach dem 1., der [ ] neu eingefügt hat“ →
     *    Checkbox-Zeile wird zu [x] statt entfernt.
     */
    fun toggleListType(type: ListType, checked: Boolean = false) {
        val v = bodyValue ?: return
        val text = v.text
        val cursor = rawCursor(v)
        val lineStart = lineStartOf(text, cursor)
        val lineEnd = lineEndOf(text, lineStart)
        val fullLine = text.substring(lineStart, lineEnd)
        val currentType = NoteTextBody.detectListType(fullLine)
        val content = NoteTextBody.stripPrefix(fullLine)

        val newLine: String = when {
            // Checkbox-Zeile + [checked] (Doppeltipp-Sequenz):
            // frisch eingefügtes [ ] → zu [x] umwandeln
            type == ListType.CHECKBOX && currentType == ListType.CHECKBOX && checked ->
                NoteTextBody.CHECKBOX_DONE + content
            // Checkbox-Zeile (einfacher Tipp): [ ] oder [x] → entfernen
            type == ListType.CHECKBOX && currentType == ListType.CHECKBOX -> content
            // Doppeltipp ohne vorhandene Checkbox → direkt [x]
            type == ListType.CHECKBOX && checked -> NoteTextBody.CHECKBOX_DONE + content
            currentType == type -> content
            type == ListType.ORDERED -> "${orderedNumber(text, lineStart)}. $content"
            type == ListType.BULLET -> NoteTextBody.BULLET_PREFIX + content
            type == ListType.ARROW -> NoteTextBody.ARROW_PREFIX + content
            else -> NoteTextBody.CHECKBOX_OPEN + content
        }
        val newText = text.substring(0, lineStart) + newLine + text.substring(lineEnd)
        // Cursor-Position nach dem Toggle:
        // - Präfix wurde ENTFERNT (gleicher Typ nochmal) → Zeilenanfang
        // - sonst → HINTER den (neuen) Präfix, in den INHALT
        val prefixRemoved = newLine == content
        val newCursor = if (prefixRemoved) lineStart else when (type) {
            ListType.CHECKBOX -> lineStart + (
                if (newLine.startsWith(NoteTextBody.CHECKBOX_DONE)) NoteTextBody.CHECKBOX_DONE.length
                else NoteTextBody.CHECKBOX_OPEN.length
            )
            ListType.BULLET -> lineStart + NoteTextBody.BULLET_PREFIX.length
            ListType.ORDERED -> lineStart + orderedNumber(text, lineStart).let { "$it. ".length }
            ListType.ARROW -> lineStart + NoteTextBody.ARROW_PREFIX.length
        }
        commitBody(TextFieldValue(newText, TextRange(newCursor)))
    }

    fun toggleInlineStyleActive(style: NoteTextBody.InlineStyle) {
        val v = bodyValue ?: return
        val (newText, newStart, newEnd) =
            NoteTextBody.toggleInlineStyle(v.text, v.selection.min, v.selection.max, style)
        commitBody(TextFieldValue(newText, TextRange(newStart, newEnd)))
    }

    // --- UI ---

    if (!state.loaded || bodyValue == null) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            CircularProgressIndicator()
        }
        return
    }

    val currentBodyValue = bodyValue!!

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        topBar = {
            TopAppBar(
                // Titel INLINE in der TopBar wie Chat-Notizen (Konsistenz,
                // Nutzer-Wunsch). Lesemodus = Text (Tap → Edit);
                // Edit = BasicTextField (Enter/Fokus-Verlust → speichern).
                title = {
                    if (isEditingTitle) {
                        BasicTextField(
                            value = titleText,
                            onValueChange = { titleText = it },
                            modifier = Modifier
                                .fillMaxWidth()
                                .focusRequester(titleFocusRequester)
                                .onPreviewKeyEvent { event ->
                                    // Enter (ohne Shift) = speichern + Fokus weg
                                    if (event.type == KeyEventType.KeyDown && event.key == Key.Enter && !event.isShiftPressed) {
                                        focusManager.clearFocus()
                                        true
                                    } else false
                                }
                                .onFocusChanged { focusState ->
                                    if (hadTitleFocus && !focusState.isFocused) {
                                        commitTitleEdit()
                                    }
                                    hadTitleFocus = focusState.isFocused
                                },
                            textStyle = MaterialTheme.typography.titleLarge.copy(
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.onSurface
                            ),
                            cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                            singleLine = true
                        )
                    } else {
                        Text(
                            state.title.ifBlank { "Notiz" },
                            fontWeight = FontWeight.SemiBold,
                            modifier = Modifier
                                .clip(RoundedCornerShape(4.dp))
                                .clickable {
                                    // Tap → Edit-Modus (Select-All macht der
                                    // LaunchedEffect — einen Frame später).
                                    titleText = TextFieldValue(state.title)
                                    isEditingTitle = true
                                }
                                .padding(horizontal = 4.dp, vertical = 2.dp)
                        )
                    }
                },
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
                    onCheckbox = {
                        // Punkt 6: Doppeltipp → direkt ANGEHAKTE Checkbox ([x]).
                        // Sequenz: 1. Tipp fügt [ ] ein, 2. Tipp (Doppeltipp)
                        // wandelt das frische [ ] in [x] um (in toggleListType).
                        // Einfach-Tipp: [ ] einfügen bzw. vorhandene Checkbox
                        // ([ ] ODER [x]) entfernen.
                        val nowMs = kotlinx.datetime.Clock.System.now().toEpochMilliseconds()
                        val isDoubleTap = nowMs - lastCheckboxTapMs < 400
                        lastCheckboxTapMs = nowMs
                        toggleListType(ListType.CHECKBOX, checked = isDoubleTap)
                    },
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
                // KEIN imePadding() hier: Die Format-Leiste in der BottomBar
                // hat bereits windowInsetsPadding(ime) — das Scaffold hebt den
                // Content damit ueber die Tastatur. Ein ZUSAETZLICHES imePadding
                // wuerde die Tastatur-Hoehe DOPPELT abziehen → Mini-Viewport →
                // bringIntoView scrollt weit uebers Ziel (Nutzer-Report).
                .padding(horizontal = 16.dp)
                .onGloballyPositioned { editorViewportH = it.size.height }
                .verticalScroll(editorScroll)
                // Bugs 2+3: Tap-Flaeche ist jetzt die GANZE Column (fuellt den
                // Screen) — unter dem geschriebenen Text tippen geht auch in
                // den Edit-Modus. Kein clickable mehr auf dem Lese-Block:
                // kein Ripple/Hover-Layer mehr (die eckige Markierung am
                // Desktop). Taps auf Text/Checkboxen konsumieren die Kinder,
                // der aeussere Handler feuert nur im leeren Raum.
                .pointerInput(Unit) {
                    detectTapGestures { _ ->
                        if (!editing) {
                            // Edit-Modus mit Cursor ans Textende (wie vorher
                            // beim Tap auf leeren Raum).
                            val t = bodyValue?.text ?: ""
                            bodyValue = TextFieldValue(t, TextRange(t.length))
                            cursorOffset = t.length // Bug 5
                            focusEditOnSwitch = true
                            editing = true
                        }
                    }
                }
        ) {
            Spacer(Modifier.height(4.dp))

            if (!editing) {
                // ----- LESEMODUS (Option B): echte Widgets wie früher -----
                // GANZER Block klickbar → Edit-Modus. Checkboxen fangen ihren
                // Tap selbst ab (kein Modus-Wechsel, togglen direkt).
                NoteBodyReadOnly(
                    text = currentBodyValue.text,
                    onToggleCheckbox = { lineStart -> toggleCheckboxAt(lineStart) },
                    modifier = Modifier
                        .fillMaxWidth(),
                    onTextTap = { rawOffset ->
                        // Tap AUF den Text: Edit-Modus mit Cursor genau an
                        // der Tap-Position (Roh-Text-Offset aus dem
                        // Text-Layout des Lesemodus-Texts).
                        val t = bodyValue?.text ?: ""
                        val clamped = rawOffset.coerceIn(0, t.length)
                        bodyValue = TextFieldValue(t, TextRange(clamped))
                        cursorOffset = clamped // Bug 5
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
                    onTextLayout = { fieldLayout = it },
                    modifier = Modifier
                        .fillMaxWidth()
                        .onGloballyPositioned { coords ->
                            fieldOffsetInColumn = coords.positionInParent().y
                        }
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
