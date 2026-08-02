@file:OptIn(
    androidx.compose.material3.ExperimentalMaterial3Api::class,
    androidx.compose.foundation.ExperimentalFoundationApi::class
)

package com.earendil.todonotes.ui.notes

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.FormatListBulleted
import androidx.compose.material.icons.filled.FormatListNumbered
import androidx.compose.material.icons.filled.FormatQuote
import androidx.compose.material.icons.filled.TextFields
import androidx.compose.material.icons.filled.ToggleOff
import androidx.compose.material.icons.filled.ToggleOn
import androidx.compose.material3.BottomAppBar
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.earendil.todonotes.data.repo.NoteRepository
import com.earendil.todonotes.data.richtext.Block
import com.earendil.todonotes.data.richtext.ListItem
import com.earendil.todonotes.data.richtext.ListType
import com.earendil.todonotes.data.richtext.NoteBody
import com.earendil.todonotes.data.richtext.NoteBodyJson
import com.earendil.todonotes.data.richtext.Segment
import com.earendil.todonotes.ui.NoteEditorViewModel
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Vollbild-Notiz-Editor (Block F5).
 *
 * Aufbau (Samsung-Notes-Style):
 *  - TopAppBar mit Zurück-Pfeil (auto-save bei Back)
 *  - Titel-Feld (groß, fett); bei neuer Notiz beim ersten Fokussieren
 *    wird der Default-Titel ("Neue Notiz vom …") komplett markiert, damit
 *    der Nutzer sofort drüberschreiben kann.
 *  - Body: Block-Liste (Paragraph / ListBlock). Jeder Block editierbar.
 *  - BottomAppBar: neuer Paragraph, Bullet-Liste, Nummerierte Liste,
 *    Checkbox-Liste, Pfeil-Liste.
 *
 * Inline-Formatierung (bold/italic/underline) folgt als Verfeinerung –
 * das Block-Modell in [NoteBody] unterstützt es bereits (Segment.style).
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

    // --- Lokaler Editierzustand (UI ist Source of Truth während Editor offen) ---
    var body by remember { mutableStateOf<NoteBody?>(null) }
    var titleValue by remember { mutableStateOf<TextFieldValue?>(null) }
    var titleSelectedOnce by remember { mutableStateOf(false) }
    val titleFocusRequester = remember { FocusRequester() }

    // Einmalig laden, sobald das VM-State bereit ist.
    LaunchedEffect(state.loaded) {
        if (state.loaded && body == null) {
            body = NoteBodyJson.decodeOrNew(state.bodyJson)
            titleValue = TextFieldValue(state.title)
        }
    }

    // Notiz laden anstoßen
    LaunchedEffect(noteId) {
        vm.load(noteId, isNew)
    }

    // System-Back: speichern + schließen
    BackHandler(enabled = state.loaded) {
        vm.flushNow()
        onBack()
    }

    // --- Hilfsfunktionen: Body manipulieren + VM benachrichtigen ---
    fun commit(newBody: NoteBody) {
        body = newBody
        vm.updateBody(NoteBodyJson.encode(newBody))
    }

    fun updateBlock(index: Int, newBlock: Block) {
        val b = body ?: return
        commit(b.copy(blocks = b.blocks.toMutableList().also { it[index] = newBlock }))
    }

    fun deleteBlock(index: Int) {
        val b = body ?: return
        commit(b.copy(blocks = b.blocks.toMutableList().also { it.removeAt(index) }))
    }

    fun addParagraph() {
        val b = body ?: return
        commit(b.copy(blocks = b.blocks + Block.Paragraph(listOf(Segment("")))))
    }

    fun addList(type: ListType) {
        val b = body ?: return
        commit(b.copy(blocks = b.blocks + Block.ListBlock(items = listOf(ListItem(listOf(Segment("")))), type = type)))
    }

    fun addListItem(blockIndex: Int) {
        val b = body ?: return
        val block = b.blocks.getOrNull(blockIndex) as? Block.ListBlock ?: return
        val newItems = block.items + ListItem(listOf(Segment("")))
        updateBlock(blockIndex, block.copy(items = newItems))
    }

    fun updateListItem(blockIndex: Int, itemIndex: Int, text: String) {
        val b = body ?: return
        val block = b.blocks.getOrNull(blockIndex) as? Block.ListBlock ?: return
        val newItems = block.items.toMutableList()
        newItems[itemIndex] = newItems[itemIndex].copy(segments = listOf(Segment(text)))
        updateBlock(blockIndex, block.copy(items = newItems))
    }

    fun deleteListItem(blockIndex: Int, itemIndex: Int) {
        val b = body ?: return
        val block = b.blocks.getOrNull(blockIndex) as? Block.ListBlock ?: return
        if (block.items.size <= 1) {
            deleteBlock(blockIndex)
            return
        }
        val newItems = block.items.toMutableList()
        newItems.removeAt(itemIndex)
        updateBlock(blockIndex, block.copy(items = newItems))
    }

    fun toggleCheckbox(blockIndex: Int, itemIndex: Int) {
        val b = body ?: return
        val block = b.blocks.getOrNull(blockIndex) as? Block.ListBlock ?: return
        val newItems = block.items.toMutableList()
        newItems[itemIndex] = newItems[itemIndex].copy(checked = !newItems[itemIndex].checked)
        updateBlock(blockIndex, block.copy(items = newItems))
    }

    // --- UI ---

    if (!state.loaded || body == null || titleValue == null) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            CircularProgressIndicator()
        }
        return
    }

    val currentBody = body!!
    val currentTitleValue = titleValue!!
    val dateFmt = remember { SimpleDateFormat("dd.MM.yyyy", Locale.GERMAN) }
    val defaultTitle = remember(state.loaded) {
        "Neue Notiz vom ${dateFmt.format(Date())}"
    }

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
                IconButton(onClick = { addParagraph() }) {
                    Icon(Icons.Filled.TextFields, contentDescription = "Text")
                }
                IconButton(onClick = { addList(ListType.BULLET) }) {
                    Icon(Icons.Filled.FormatListBulleted, contentDescription = "Aufzählung")
                }
                IconButton(onClick = { addList(ListType.ORDERED) }) {
                    Icon(Icons.Filled.FormatListNumbered, contentDescription = "Nummerierung")
                }
                IconButton(onClick = { addList(ListType.CHECKBOX) }) {
                    Icon(Icons.Filled.Check, contentDescription = "Checkbox-Liste")
                }
                IconButton(onClick = { addList(ListType.ARROW) }) {
                    Icon(Icons.Filled.FormatQuote, contentDescription = "Pfeil-Liste")
                }
            }
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 16.dp)
        ) {
            // --- Titel ---
            item {
                val tv = currentTitleValue
                TextField(
                    value = tv,
                    onValueChange = { newVal ->
                        titleValue = newVal
                        vm.updateTitle(newVal.text)
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .focusRequester(titleFocusRequester)
                        .onFocusChanged { focusState ->
                            // Auto-select: bei neuer Notiz + Default-Titel + erster Fokus
                            // → gesamten Text markieren, damit Nutzer sofort tippen kann.
                            if (focusState.isFocused && state.isNewNote && !titleSelectedOnce) {
                                titleSelectedOnce = true
                                titleValue = tv.copy(
                                    selection = TextRange(0, tv.text.length)
                                )
                            }
                        },
                    placeholder = { Text("Titel") },
                    textStyle = MaterialTheme.typography.titleLarge,
                    singleLine = true,
                    colors = TextFieldDefaults.colors(
                        focusedContainerColor = androidx.compose.ui.graphics.Color.Transparent,
                        unfocusedContainerColor = androidx.compose.ui.graphics.Color.Transparent,
                        focusedIndicatorColor = androidx.compose.ui.graphics.Color.Transparent,
                        unfocusedIndicatorColor = androidx.compose.ui.graphics.Color.Transparent
                    ),
                    keyboardOptions = KeyboardOptions(
                        capitalization = KeyboardCapitalization.Sentences,
                        imeAction = ImeAction.Next
                    )
                )
            }

            // --- Blöcke ---
            itemsIndexed(currentBody.blocks, key = { idx, _ -> idx }) { index, block ->
                when (block) {
                    is Block.Paragraph -> ParagraphBlockEditor(
                        block = block,
                        onChange = { newText ->
                            updateBlock(index, Block.Paragraph(listOf(Segment(newText))))
                        },
                        onDelete = { deleteBlock(index) }
                    )
                    is Block.ListBlock -> ListBlockEditor(
                        block = block,
                        onItemChange = { itemIndex, text -> updateListItem(index, itemIndex, text) },
                        onItemDelete = { itemIndex -> deleteListItem(index, itemIndex) },
                        onToggleCheckbox = { itemIndex -> toggleCheckbox(index, itemIndex) },
                        onAddItem = { addListItem(index) },
                        onDelete = { deleteBlock(index) }
                    )
                    is Block.ImageBlock -> ImageBlockPlaceholder(
                        block = block,
                        onDelete = { deleteBlock(index) }
                    )
                }
            }

            // --- Footer-Spacer (damit Keyboard nicht Content überdeckt) ---
            item { Spacer(Modifier.height(80.dp)) }
        }
    }
}

// ---- Block-Editoren ----

@Composable
private fun ParagraphBlockEditor(
    block: Block.Paragraph,
    onChange: (String) -> Unit,
    onDelete: () -> Unit
) {
    val text = block.segments.joinToString("") { it.text }
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.Top
    ) {
        TextField(
            value = text,
            onValueChange = onChange,
            modifier = Modifier.weight(1f),
            textStyle = MaterialTheme.typography.bodyLarge,
            placeholder = { Text("Text …", color = MaterialTheme.colorScheme.onSurfaceVariant) },
            colors = TextFieldDefaults.colors(
                focusedContainerColor = androidx.compose.ui.graphics.Color.Transparent,
                unfocusedContainerColor = androidx.compose.ui.graphics.Color.Transparent,
                focusedIndicatorColor = androidx.compose.ui.graphics.Color.Transparent,
                unfocusedIndicatorColor = androidx.compose.ui.graphics.Color.Transparent
            ),
            keyboardOptions = KeyboardOptions(
                capitalization = KeyboardCapitalization.Sentences
            )
        )
        IconButton(onClick = onDelete, modifier = Modifier.size(32.dp)) {
            Icon(
                Icons.Filled.Close,
                contentDescription = "Block löschen",
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(18.dp)
            )
        }
    }
}

@Composable
private fun ListBlockEditor(
    block: Block.ListBlock,
    onItemChange: (Int, String) -> Unit,
    onItemDelete: (Int) -> Unit,
    onToggleCheckbox: (Int) -> Unit,
    onAddItem: () -> Unit,
    onDelete: () -> Unit
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        block.items.forEachIndexed { itemIndex, item ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Präfix je ListType
                when (block.type) {
                    ListType.ORDERED -> Text(
                        "${itemIndex + 1}.",
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.width(28.dp)
                    )
                    ListType.BULLET -> Text(
                        "•",
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.width(28.dp)
                    )
                    ListType.ARROW -> Text(
                        "→",
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.width(28.dp)
                    )
                    ListType.CHECKBOX -> Checkbox(
                        checked = item.checked,
                        onCheckedChange = { onToggleCheckbox(itemIndex) },
                        modifier = Modifier.size(28.dp)
                    )
                }
                TextField(
                    value = item.segments.joinToString("") { it.text },
                    onValueChange = { onItemChange(itemIndex, it) },
                    modifier = Modifier.weight(1f),
                    textStyle = MaterialTheme.typography.bodyLarge,
                    placeholder = { Text("Eintrag …", color = MaterialTheme.colorScheme.onSurfaceVariant) },
                    colors = TextFieldDefaults.colors(
                        focusedContainerColor = androidx.compose.ui.graphics.Color.Transparent,
                        unfocusedContainerColor = androidx.compose.ui.graphics.Color.Transparent,
                        focusedIndicatorColor = androidx.compose.ui.graphics.Color.Transparent,
                        unfocusedIndicatorColor = androidx.compose.ui.graphics.Color.Transparent
                    ),
                    keyboardOptions = KeyboardOptions(
                        capitalization = KeyboardCapitalization.Sentences,
                        imeAction = ImeAction.Done
                    )
                )
                IconButton(onClick = { onItemDelete(itemIndex) }, modifier = Modifier.size(32.dp)) {
                    Icon(
                        Icons.Filled.Close,
                        contentDescription = "Eintrag löschen",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }
        }
        // Neuer Eintrag
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 28.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onAddItem, modifier = Modifier.size(32.dp)) {
                Icon(
                    Icons.Filled.Add,
                    contentDescription = "Eintrag hinzufügen",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(20.dp)
                )
            }
            Text(
                "Eintrag hinzufügen",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        // Block löschen
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.End
        ) {
            IconButton(onClick = onDelete, modifier = Modifier.size(32.dp)) {
                Icon(
                    Icons.Filled.Close,
                    contentDescription = "Liste löschen",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(18.dp)
                )
            }
        }
    }
}

@Composable
private fun ImageBlockPlaceholder(
    block: Block.ImageBlock,
    onDelete: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .weight(1f)
                .height(120.dp),
            contentAlignment = Alignment.Center
        ) {
            Text(
                "🖼 Bild (F7)",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        IconButton(onClick = onDelete, modifier = Modifier.size(32.dp)) {
            Icon(
                Icons.Filled.Close,
                contentDescription = "Block löschen",
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(18.dp)
            )
        }
    }
}
