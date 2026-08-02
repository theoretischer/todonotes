@file:OptIn(
    androidx.compose.material3.ExperimentalMaterial3Api::class,
    androidx.compose.foundation.ExperimentalFoundationApi::class
)

package com.earendil.todonotes.ui.notes

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.FormatListBulleted
import androidx.compose.material.icons.filled.FormatListNumbered
import androidx.compose.material.icons.filled.FormatQuote
import androidx.compose.material.icons.filled.RadioButtonUnchecked
import androidx.compose.material3.BottomAppBar
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
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
import androidx.compose.runtime.collectAsState
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
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.earendil.todonotes.data.repo.NoteRepository
import com.earendil.todonotes.data.richtext.NoteTextBody
import com.earendil.todonotes.data.richtext.ListType
import com.earendil.todonotes.ui.NoteEditorViewModel

/**
 * Vollbild-Notiz-Editor (Block F5) — kontinuierlicher Textfluss wie Word.
 *
 * Der gesamte Body ist ein einziges Textfeld. Listen-Formatierung
 * passiert über Zeilen-Präfixe (-, 1., [ ], →), die über die Toolbar
 * auf der aktuellen Cursor-Zeile umgeschaltet werden. Enter auto-
 * continue: eine neue Zeile in einer Liste übernimmt den Prefix.
 */
@Composable
fun NoteEditorScreen(
    noteId: String,
    isNew: Boolean,
    noteRepo: NoteRepository,
    onBack: () -> Unit
) {
    val vm: NoteEditorViewModel = viewModel(factory = NoteEditorViewModel.Factory(noteRepo))
    val state by vm.state.collectAsState()

    // --- Lokaler Editierzustand (UI ist Source of Truth) ---
    var bodyValue by remember { mutableStateOf<TextFieldValue?>(null) }
    var titleValue by remember { mutableStateOf<TextFieldValue?>(null) }
    var titleSelectedOnce by remember { mutableStateOf(false) }
    val titleFocusRequester = remember { FocusRequester() }
    val bodyFocusRequester = remember { FocusRequester() }

    // Einmalig laden, sobald das VM-State bereit ist.
    LaunchedEffect(state.loaded) {
        if (state.loaded && bodyValue == null) {
            bodyValue = TextFieldValue(state.body)
            titleValue = TextFieldValue(state.title)
        }
    }

    LaunchedEffect(noteId) {
        vm.load(noteId, isNew)
    }

    // System-Back: speichern + schließen
    BackHandler(enabled = state.loaded) {
        vm.flushNow()
        onBack()
    }

    // --- Hilfsfunktionen: Listen-Prefix auf Cursor-Zeile umschalten ---
    // (als top-level Funktionen weiter unten definiert)

    // --- UI ---

    if (!state.loaded || bodyValue == null || titleValue == null) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            CircularProgressIndicator()
        }
        return
    }

    val currentBodyValue = bodyValue!!
    val currentTitleValue = titleValue!!

    Scaffold(
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
            BottomAppBar {
                // Bullet-Liste
                IconButton(onClick = { toggleListPrefix(ListType.BULLET, bodyValue, vm, bodyValueSetter = { bodyValue = it }) }) {
                    Icon(Icons.Filled.FormatListBulleted, contentDescription = "Aufzählung")
                }
                // Nummerierte Liste
                IconButton(onClick = { toggleListPrefix(ListType.ORDERED, bodyValue, vm, bodyValueSetter = { bodyValue = it }) }) {
                    Icon(Icons.Filled.FormatListNumbered, contentDescription = "Nummerierung")
                }
                // Checkbox-Liste
                IconButton(onClick = { toggleListPrefix(ListType.CHECKBOX, bodyValue, vm, bodyValueSetter = { bodyValue = it }) }) {
                    Icon(Icons.Filled.RadioButtonUnchecked, contentDescription = "Checkbox")
                }
                // Checkbox toggeln (an/aus)
                IconButton(onClick = { toggleCheckboxAtCursor(bodyValue, vm, bodyValueSetter = { bodyValue = it }) }) {
                    Icon(Icons.Filled.Check, contentDescription = "Checkbox umschalten")
                }
                // Pfeil-Liste
                IconButton(onClick = { toggleListPrefix(ListType.ARROW, bodyValue, vm, bodyValueSetter = { bodyValue = it }) }) {
                    Icon(Icons.Filled.FormatQuote, contentDescription = "Pfeil-Liste")
                }
            }
        }
    ) { padding ->
        // Ein Scrollbares Column mit Titel + Body-Textfeld
        androidx.compose.foundation.layout.Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 16.dp)
                .verticalScroll(rememberScrollState())
        ) {
            // --- Titel ---
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
                        // Auto-select: bei neuer Notiz + erster Fokus →
                        // gesamten Text markieren, damit Nutzer sofort
                        // drüberschreiben kann.
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

            Spacer(Modifier.height(8.dp))

            // --- Body (ein einziges Textfeld, kontinuierlich) ---
            TextField(
                value = currentBodyValue,
                onValueChange = { newVal ->
                    // Enter auto-continue erkennen: wenn der neue Text
                    // einen Zeilenumbruch an der Cursor-Position hat,
                    // der im alten Text nicht war.
                    val oldText = currentBodyValue.text
                    val newText = newVal.text
                    if (newText.length == oldText.length + 1 &&
                        newText.substring(0, newVal.selection.min - 1) == oldText.substring(0, newVal.selection.min - 1) &&
                        newText[newVal.selection.min - 1] == '\n'
                    ) {
                        // Enter wurde gedrückt → auto-continue
                        // Aber nur, wenn die vorherige Zeile einen
                        // Listen-Prefix hat.
                        val beforeEnter = oldText.substring(0, newVal.selection.min - 1)
                        val cursor = newVal.selection.min - 1
                        val ls = run {
                            var i = cursor - 1
                            while (i >= 0 && beforeEnter[i] != '\n') i--
                            i + 1
                        }
                        val le = run {
                            var i = cursor
                            while (i < beforeEnter.length && beforeEnter[i] != '\n') i++
                            i
                        }
                        val line = beforeEnter.substring(ls, le)
                        if (NoteTextBody.detectListType(line) != null) {
                            // Den vom TextField bereits eingefügten \n
                            // nehmen und Prefix dahinter setzen.
                            val type = NoteTextBody.detectListType(line)!!
                            val content = NoteTextBody.stripPrefix(line).trim()
                            if (content.isEmpty()) {
                                // Leerzeile → Liste beenden, \n bleibt
                                val prefixLen = line.length - NoteTextBody.stripPrefix(line).length
                                val newLineText = NoteTextBody.stripPrefix(line)
                                val fixed = oldText.substring(0, ls) + newLineText + oldText.substring(le)
                                bodyValue = TextFieldValue(fixed, TextRange(ls + newLineText.length))
                                vm.updateBody(fixed)
                                return@TextField
                            }
                            val prefix = when (type) {
                                ListType.BULLET -> NoteTextBody.BULLET_PREFIX
                                ListType.CHECKBOX -> NoteTextBody.CHECKBOX_OPEN
                                ListType.ARROW -> NoteTextBody.ARROW_PREFIX
                                ListType.ORDERED -> {
                                    var count = 0
                                    var i = 0
                                    while (i < ls) {
                                        val end = oldText.indexOf('\n', i).let { if (it < 0) oldText.length else it }
                                        val l = oldText.substring(i, end)
                                        if (NoteTextBody.detectListType(l) == ListType.ORDERED) count++
                                        i = end + 1
                                    }
                                    "$count. "
                                }
                            }
                            val withPrefix = newText.substring(0, newVal.selection.min) + prefix + newText.substring(newVal.selection.min)
                            val newCursor = newVal.selection.min + prefix.length
                            bodyValue = TextFieldValue(withPrefix, TextRange(newCursor))
                            vm.updateBody(withPrefix)
                            return@TextField
                        }
                    }
                    bodyValue = newVal
                    vm.updateBody(newText)
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .focusRequester(bodyFocusRequester),
                placeholder = { Text("Notiz …", color = MaterialTheme.colorScheme.onSurfaceVariant) },
                textStyle = MaterialTheme.typography.bodyLarge,
                colors = transparentTextFieldColors(),
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

// ---- Hilfsfunktionen für Listen-Prefixe (top-level) ----

/** Findet den Start-Index der Zeile, in der der Cursor steht. */
private fun lineStart(text: String, cursor: Int): Int {
    var i = cursor - 1
    while (i >= 0 && text[i] != '\n') i--
    return i + 1
}

/** Findet das Ende der Zeile (exklusive \n). */
private fun lineEnd(text: String, cursor: Int): Int {
    var i = cursor
    while (i < text.length && text[i] != '\n') i++
    return i
}

/** Zählt wie viele nummerierte Zeilen vor [ls] stehen (für fortlaufende Nummerierung). */
private fun countOrderedBefore(text: String, ls: Int): Int {
    var count = 0
    var i = 0
    while (i < ls) {
        val end = text.indexOf('\n', i).let { if (it < 0) text.length else it }
        val line = text.substring(i, end)
        if (NoteTextBody.detectListType(line) == ListType.ORDERED) count++
        i = end + 1
    }
    return count
}

/** Schaltet die Cursor-Zeile auf einen Listen-Typ um.
 *  Hat sie schon diesen Prefix, wird er entfernt (Toggle). */
private fun toggleListPrefix(
    type: ListType,
    bodyValue: TextFieldValue?,
    vm: NoteEditorViewModel,
    bodyValueSetter: (TextFieldValue) -> Unit
) {
    val tv = bodyValue ?: return
    val text = tv.text
    val cursor = tv.selection.min
    val ls = lineStart(text, cursor)
    val le = lineEnd(text, cursor)
    val line = text.substring(ls, le)

    val currentType = NoteTextBody.detectListType(line)
    if (currentType == type) {
        // Toggle aus → Prefix entfernen
        val newLine = NoteTextBody.stripPrefix(line)
        val newText = text.substring(0, ls) + newLine + text.substring(le)
        bodyValueSetter(TextFieldValue(newText, TextRange(ls + newLine.length)))
        vm.updateBody(newText)
    } else {
        val number = countOrderedBefore(text, ls) + 1
        val newText = NoteTextBody.setListPrefix(text, ls, type, number)
        val newLineEnd = newText.indexOf('\n', ls).let { if (it < 0) newText.length else it }
        bodyValueSetter(TextFieldValue(newText, TextRange(newLineEnd)))
        vm.updateBody(newText)
    }
}

/** Toggelt die Checkbox auf der Cursor-Zeile. */
private fun toggleCheckboxAtCursor(
    bodyValue: TextFieldValue?,
    vm: NoteEditorViewModel,
    bodyValueSetter: (TextFieldValue) -> Unit
) {
    val tv = bodyValue ?: return
    val text = tv.text
    val cursor = tv.selection.min
    val ls = lineStart(text, cursor)
    val newText = NoteTextBody.toggleCheckbox(text, ls)
    bodyValueSetter(TextFieldValue(newText, tv.selection))
    vm.updateBody(newText)
}
