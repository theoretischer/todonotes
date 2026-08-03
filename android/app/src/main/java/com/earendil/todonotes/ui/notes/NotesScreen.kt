@file:OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)

package com.earendil.todonotes.ui.notes

import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Chat
import androidx.compose.material.icons.filled.CreateNewFolder
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.DriveFileMove
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.NoteAdd
import androidx.compose.material.icons.filled.TextSnippet
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.earendil.todonotes.data.entity.Folder
import com.earendil.todonotes.data.entity.Note
import com.earendil.todonotes.data.entity.NoteType
import com.earendil.todonotes.data.richtext.NoteTextBody
import com.earendil.todonotes.ui.Crumb
import com.earendil.todonotes.ui.NotesViewModel
import com.earendil.todonotes.ui.components.SwipeToDeleteRow

/**
 * Special-Key im [folderBounds]-Registry für die Wurzel-Breadcrumb. Beim
 * Drag einer Notiz auf die Wurzel-Zeile wird statt einer echten Ordner-Id
 * dieser Key geliefert und vom Caller in `folderId = null` (Hauptebene)
 * übersetzt.
 */
internal const val ROOT_DROP_KEY = "__root__"

/**
 * Notizen-Tab (Block F4): Ordner- & Notiz-Übersicht.
 *
 * Aufbau:
 *  - Breadcrumb-Pfad oben (Wurzel › Ordner › …) — tapbar zum Navigieren.
 *  - Liste: erst Ordner, dann Notizen des aktuellen Ordners.
 *  - FAB-Menü `+`: „Neue Notiz" / „Neuer Ordner".
 *  - Tippen Ordner → rein. Tippen Notiz → Editor (F5).
 *  - Long-press / ⋮ → Löschen + Verschieben (Verschieben kommt mit F6,
 *    erstmal nur Löschen + Umbenennen).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NotesScreen(
    notesVm: NotesViewModel,
    onOpenNote: (noteId: String, isNew: Boolean, type: NoteType) -> Unit,
    onOpenChat: (noteId: String, isNew: Boolean) -> Unit,
    modifier: Modifier = Modifier
) {
    val state by notesVm.browserState.collectAsStateWithLifecycle()

    var showCreateFolderDialog by rememberSaveable { mutableStateOf(false) }
    var renameTarget by remember { mutableStateOf<Folder?>(null) }
    var moveTarget by remember { mutableStateOf<Note?>(null) }
    var showNewMenu by rememberSaveable { mutableStateOf(false) }

    // ---- 1D-Drag-Reorder + In-Ordner-Verschieben (Block F6) ----
    // Long-Press auf eine Zeile und dann hoch/runter: tauscht die gezogene
    // Zeile per Schwellwert (halbe Zeilenhöhe) mit dem Nachbarn. Die Reihen-
    // folge wird LIVE in der DB festgehalten (position-Spalte) — Room recom-
    // poniert, sodass die anderen Zeilen "zur Seite geschoben" werden, ganz
    // ohne Ghost-Overlay. Ordner sortieren sich nur unter Ordnern, Notizen
    // nur unter Notizen.
    //
    // Zusätzlich: Lässt man eine NOTIZ beim Ziehen über einem Ordner los,
    // wird sie in diesen Ordner verschoben (Drop-Target = globale Bounds).
    // Hoch/runter in der Leere = reines Reorder.
    val rowHeights = remember { mutableStateMapOf<String, Int>() }
    var reorder by remember { mutableStateOf<ReorderSession?>(null) }
    val folderBounds = remember { mutableStateMapOf<String, Rect>() }

    Box(modifier = modifier.fillMaxSize()) {

        // Breadcrumb-Pfad oben (immer sichtbar, auch auf der Wurzel-Ebene,
        // damit der Header "Notizen" überall steht und die Wurzel als
        // Drop-Ziel beim Verschieben dient).
        NotesBreadcrumb(
            crumbs = state.breadcrumbs,
            onCrumb = notesVm::navigateToCrumb,
            modifier = Modifier
                .align(Alignment.TopStart)
                .statusBarsPadding()
                .zIndex(5f) // über der Liste, damit der Tap immer ankommt
                .onGloballyPositioned { coords ->
                    // Wurzel-Breadcrumb als Drop-Ziel für "Notiz auf Root
                    // verschieben" registrieren (Special-Key __root__).
                    folderBounds[ROOT_DROP_KEY] = coords.boundsInRoot()
                }
        )

        if (state.folders.isEmpty() && state.notes.isEmpty()) {
            EmptyNotesHint()
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(start = 16.dp, top = 96.dp, end = 16.dp, bottom = 88.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                // Ordner zuerst
                items(state.folders, key = { it.id }) { folder ->
                    val isDragged = reorder?.draggedId == folder.id && reorder?.kind == ReorderKind.FOLDER
                    RowReorder(
                        isDragged = isDragged,
                        onSizeChanged = { rowHeights[folder.id] = it },
                        onGloballyPositioned = { pos -> folderBounds[folder.id] = pos.boundsInRoot() }
                    ) {
                        val dragModifier = Modifier.reorderDragGesture(
                            itemId = folder.id,
                            kind = ReorderKind.FOLDER,
                            repositories = state.folders.map { it.id },
                            heightPx = rowHeights[folder.id] ?: 0,
                            reorder = reorder,
                            setReorder = { reorder = it },
                            onSwap = { a, b -> notesVm.reorderFolders(a, b) }
                        )
                        SwipeToDeleteRow(
                            onDelete = { notesVm.deleteFolder(folder.id) },
                            onClick = { notesVm.openFolder(folder) },
                            onLongClick = null,
                            contentModifier = dragModifier
                        ) {
                            FolderRow(folder = folder)
                        }
                    }
                }
                if (state.folders.isNotEmpty() && state.notes.isNotEmpty()) {
                    item { Spacer(Modifier.height(4.dp)) }
                }
                // dann Notizen (Reorder nur unter Notizen)
                items(state.notes, key = { it.id }) { note ->
                    val isDragged = reorder?.draggedId == note.id && reorder?.kind == ReorderKind.NOTE
                    RowReorder(
                        isDragged = isDragged,
                        onSizeChanged = { rowHeights[note.id] = it },
                        onGloballyPositioned = {}
                    ) {
                        val dragModifier = Modifier.reorderDragGesture(
                            itemId = note.id,
                            kind = ReorderKind.NOTE,
                            repositories = state.notes.map { it.id },
                            heightPx = rowHeights[note.id] ?: 0,
                            reorder = reorder,
                            setReorder = { reorder = it },
                            onSwap = { a, b -> notesVm.reorderNotes(a, b) },
                            folderBounds = folderBounds,
                            onDropOnFolder = { noteId, folderIdOrRoot ->
                                // ROOT_DROP_KEY signalisiert "auf Hauptebene verschieben".
                                val target = if (folderIdOrRoot == ROOT_DROP_KEY) null else folderIdOrRoot
                                // "Auf den aktuellen Ordner" ist kein Verschieben — ignorieren.
                                if (target != state.currentFolderId) {
                                    notesVm.moveNote(noteId, target)
                                }
                            }
                        )
                        SwipeToDeleteRow(
                            onDelete = { notesVm.deleteNote(note.id) },
                            onClick = { onOpenNote(note.id, false, note.type) },
                            onLongClick = null,
                            contentModifier = dragModifier
                        ) {
                            NoteRow(note = note)
                        }
                    }
                }
            }
        }

        // FAB unten rechts (nur Plus-Icon, kein Text — wie die anderen Tabs).
        // Kein navigationBarsPadding: umgebendes Box hebt bereits über NavBar.
        FloatingActionButton(
            onClick = { showNewMenu = true },
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(16.dp)
                .zIndex(20f) // immer über Liste, klickbar
        ) {
            Icon(Icons.Default.Add, contentDescription = "Neu")
        }
    }

    // --- Dialoge ---

    if (showNewMenu) {
        val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
        ModalBottomSheet(
            onDismissRequest = { showNewMenu = false },
            sheetState = sheetState
        ) {
            Text(
                "Neu erstellen",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.padding(start = 24.dp, top = 4.dp, bottom = 8.dp)
            )
            NewMenuItem(
                icon = { Icon(Icons.Default.NoteAdd, contentDescription = null) },
                text = "Neue Notiz",
                onClick = {
                    showNewMenu = false
                    notesVm.createNote(onCreated = { id -> onOpenNote(id, true, NoteType.NOTE) })
                }
            )
            NewMenuItem(
                icon = { Icon(Icons.Default.Chat, contentDescription = null) },
                text = "Neuer Chat",
                onClick = {
                    showNewMenu = false
                    notesVm.createChatNote(onCreated = { id -> onOpenChat(id, true) })
                }
            )
            NewMenuItem(
                icon = { Icon(Icons.Default.CreateNewFolder, contentDescription = null) },
                text = "Neuer Ordner",
                onClick = {
                    showNewMenu = false
                    showCreateFolderDialog = true
                }
            )
            Spacer(Modifier.height(16.dp))
        }
    }

    if (showCreateFolderDialog) {
        CreateFolderDialog(
            onDismiss = { showCreateFolderDialog = false },
            onCreate = { name ->
                notesVm.createFolder(name)
                showCreateFolderDialog = false
            }
        )
    }

    renameTarget?.let { folder ->
        CreateFolderDialog(
            initialName = folder.name,
            title = "Ordner umbenennen",
            confirmLabel = "Speichern",
            onDismiss = { renameTarget = null },
            onCreate = { name ->
                notesVm.renameFolder(folder.id, name)
                renameTarget = null
            }
        )
    }

    moveTarget?.let { note ->
        MoveNoteSheet(
            note = note,
            currentFolderId = state.currentFolderId,
            onMove = { targetFolderId ->
                notesVm.moveNote(note.id, targetFolderId)
                moveTarget = null
            },
            onDismiss = { moveTarget = null },
            loadFolders = { notesVm.getAllFoldersForMove() }
        )
    }
}

// ---- 1D-Drag-Reorder (Block F6) ----
// ReorderKind / ReorderSession / reorderStep liegen in ReorderLogic.kt.

/**
 * Eine Zeile, die per Long-Press + vertikaler Geste neu sortiert werden kann.
 *
 * Die Zeile meldet ihre Höhe via [onSizeChanged] in einen gemeinsamen
 * Registry-State. Beim Long-Press wird eine [ReorderSession] gestartet;
 * jedes Überschreiten einer Zeilenhöhe [heightPx] tauscht [draggedId] mit
 * dem Nachbarn über [onSwap] (persistiert `position` in der DB) und rückt
 * den Session-Index weiter. Weil Room danach die Liste in neuer Reihenfolge
 * liefert, öffnet sich die Lücke live an der neuen Stelle — die übrigen
 * Zeilen werden "zur Seite geschoben", ganz ohne Ghost-Overlay.
 *
 * [repositories] = alle Ids der gleichen Art in der aktuellen Reihenfolge.
 */
@Composable
private fun RowReorder(
    isDragged: Boolean,
    onSizeChanged: (Int) -> Unit,
    onGloballyPositioned: (androidx.compose.ui.layout.LayoutCoordinates) -> Unit,
    content: @Composable () -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .zIndex(if (isDragged) 2f else 0f)
            .onSizeChanged { onSizeChanged(it.height) }
            .onGloballyPositioned(onGloballyPositioned)
    ) {
        content()
    }
}

/**
 * Erzeugt die Long-Press-Reordergeste als Modifier. Diese Geste MUSS am
 * Content direkt sitzen (innergster Pointer-Knoten), damit sie gegen die
 * horizontale Swipe-Delete-Geste von [com.earendil.todonotes.ui.components.SwipeToDeleteRow]
 * gewinnt — ein weiter außen liegender pointerInput wäre unterlegen.
 */
@Composable
fun Modifier.reorderDragGesture(
    itemId: String,
    kind: ReorderKind,
    repositories: List<String>,
    heightPx: Int,
    reorder: ReorderSession?,
    setReorder: (ReorderSession?) -> Unit,
    onSwap: (String, String) -> Unit,
    folderBounds: Map<String, Rect> = emptyMap(),
    onDropOnFolder: (id: String, folderId: String) -> Unit = { _, _ -> }
): Modifier {
    // Geste darf nicht an änderbaren Werten hängen (swaps bauen die Liste
    // um → neue repositories). Deshalb key nur auf itemId und die aktuellen
    // Werte über rememberUpdatedState nachziehen.
    val currentKind by androidx.compose.runtime.rememberUpdatedState(kind)
    val currentRepos by androidx.compose.runtime.rememberUpdatedState(repositories)
    val currentHeight by androidx.compose.runtime.rememberUpdatedState(heightPx)
    val currentSetReorder by androidx.compose.runtime.rememberUpdatedState(setReorder)
    val currentOnSwap by androidx.compose.runtime.rememberUpdatedState(onSwap)
    val currentBounds by androidx.compose.runtime.rememberUpdatedState(folderBounds)
    val currentDropFolder by androidx.compose.runtime.rememberUpdatedState(onDropOnFolder)
    var nodeRoot = Offset.Zero
    return this
        .onGloballyPositioned { nodeRoot = it.positionInRoot() }
        .pointerInput(itemId) {
        var session = reorder?.takeIf { it.draggedId == itemId }
        var lastGlobal = Offset.Zero
        fun hit(): String? {
            if (currentBounds.isEmpty()) return null
            var best: String? = null
            var bestArea = Float.MAX_VALUE
            for ((id, rect) in currentBounds) {
                if (!rect.contains(lastGlobal)) continue
                val area = rect.width * rect.height
                if (area < bestArea) { best = id; bestArea = area }
            }
            return best
        }
        detectDragGesturesAfterLongPress(
            onDragStart = {
                val idx = currentRepos.indexOf(itemId)
                if (idx < 0) return@detectDragGesturesAfterLongPress
                session = ReorderSession(itemId, currentKind, idx, 0f)
                currentSetReorder(session)
            },
            onDrag = { change, dragAmount ->
                change.consume()
                // globaler Fingerpunkt: lokaler Punkt im Node + Node-Anfang in Root
                lastGlobal = nodeRoot + change.position
                val s = session ?: return@detectDragGesturesAfterLongPress
                // nur vertikale Bewegung zählt (swipe-delete bleibt horizontal)
                val step = reorderStep(
                    session = s,
                    repositories = currentRepos,
                    heightPx = currentHeight,
                    dragAmountPx = dragAmount.y,
                    onSwap = currentOnSwap
                )
                session = ReorderSession(itemId, currentKind, step.newIndex, step.newAccumPx)
                currentSetReorder(session)
            },
            onDragEnd = {
                // Notiz auf einem Ordner losgelassen → in den Ordner verschieben
                if (currentKind == ReorderKind.NOTE) {
                    hit()?.let { currentDropFolder(itemId, it) }
                }
                currentSetReorder(null)
            },
            onDragCancel = { currentSetReorder(null) }
        )
    }
}

// ---- Breadcrumb ----

@Composable
private fun NotesBreadcrumb(
    crumbs: List<Crumb>,
    onCrumb: (Crumb) -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .height(48.dp)
            .padding(horizontal = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        crumbs.forEachIndexed { idx, crumb ->
            if (idx > 0) {
                Text(
                    "›",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 2.dp)
                )
            }
            val isLast = idx == crumbs.lastIndex
            Text(
                text = crumb.name,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = if (isLast) FontWeight.SemiBold else FontWeight.Normal,
                color = if (isLast) MaterialTheme.colorScheme.onSurface
                        else MaterialTheme.colorScheme.primary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier
                    .padding(horizontal = 4.dp, vertical = 6.dp)
                    .combinedClickable(onClick = { onCrumb(crumb) })
            )
        }
    }
}

// ---- FAB-Bottom-Sheet-Einträge ----

@Composable
private fun NewMenuItem(
    icon: @Composable () -> Unit,
    text: String,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 24.dp, vertical = 14.dp)
            .combinedClickable(onClick = onClick),
        verticalAlignment = Alignment.CenterVertically
    ) {
        icon()
        Spacer(Modifier.width(16.dp))
        Text(text, style = MaterialTheme.typography.bodyLarge)
    }
}

// ---- Ordner-Zeile ----

@Composable
private fun FolderRow(
    folder: Folder
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                Icons.Default.Folder,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(28.dp)
            )
            Spacer(Modifier.width(16.dp))
            Text(
                folder.name,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.Medium,
                modifier = Modifier.weight(1f),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

// ---- Notiz-Zeile ----

@Composable
private fun NoteRow(
    note: Note
) {
    val preview = remember(note.bodyJson) { notePreview(note) }
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                if (note.type == NoteType.CHAT) Icons.Default.Chat else Icons.Default.TextSnippet,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(24.dp)
            )
            Spacer(Modifier.width(16.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    note.title.ifBlank { "Ohne Titel" },
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.Medium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                if (preview.isNotBlank()) {
                    Spacer(Modifier.height(2.dp))
                    Text(
                        preview,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }
    }
}

// ---- leerer Zustand ----

@Composable
private fun EmptyNotesHint(modifier: Modifier = Modifier) {
    Box(
        modifier = modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(
                Icons.Default.TextSnippet,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(48.dp)
            )
            Spacer(Modifier.height(12.dp))
            Text("Keine Notizen", style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(4.dp))
            Text(
                "Tippe auf „Neu“, um eine Notiz oder einen Ordner zu erstellen.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

// ---- Dialog: Ordner erstellen / umbenennen ----

@Composable
private fun CreateFolderDialog(
    initialName: String = "",
    title: String = "Neuer Ordner",
    confirmLabel: String = "Erstellen",
    onDismiss: () -> Unit,
    onCreate: (String) -> Unit
) {
    var name by remember { mutableStateOf(initialName) }
    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(
                onClick = { if (name.isNotBlank()) onCreate(name.trim()) },
                enabled = name.isNotBlank()
            ) { Text(confirmLabel) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Abbrechen") } },
        title = { Text(title) },
        text = {
            OutlinedTextField(
                value = name,
                onValueChange = { name = it },
                label = { Text("Name") },
                singleLine = true
            )
        }
    )
}

// ---- Helper: Vorschau-Text aus bodyJson ----

/** Extrahiert reinen Text aus dem ersten Paragraph als Listen-Vorschau. */
private fun notePreview(note: Note): String {
    if (note.type == NoteType.CHAT) return "Chat-Notiz"
    // Body kann noch altes Block-JSON sein → migrieren, sonst Plain Text
    val text = NoteTextBody.migrateFromBlocks(note.bodyJson)
    return text.lineSequence()
        .firstOrNull { it.isNotBlank() }
        ?.trim()
        ?.let { NoteTextBody.stripPrefix(it).trim() }
        ?: ""
}

// ---- Verschieben-Dialog (Notiz in Ordner schieben) ----

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun MoveNoteSheet(
    note: Note,
    currentFolderId: String?,
    onMove: (String?) -> Unit,
    onDismiss: () -> Unit,
    loadFolders: suspend () -> List<Folder>
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var folders by remember { mutableStateOf<List<Folder>>(emptyList()) }
    LaunchedEffect(Unit) { folders = loadFolders() }

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState) {
        Text(
            "Notiz verschieben",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.padding(start = 24.dp, top = 4.dp, bottom = 8.dp)
        )
        // Auf Wurzel-Ebene Option (nur sinnvoll wenn aktuelle Notiz in einem Ordner ist)
        if (currentFolderId != null) {
            MoveTargetRow(
                icon = { Icon(Icons.Default.TextSnippet, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant) },
                name = "Notizen (Hauptebene)",
                onClick = { onMove(null) }
            )
            HorizontalDivider()
        }
        if (folders.isEmpty() && currentFolderId == null) {
            Text(
                "Keine Ordner vorhanden.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(24.dp)
            )
        } else {
            folders.filter { it.id != currentFolderId }.forEach { folder ->
                MoveTargetRow(
                    icon = { Icon(Icons.Default.Folder, contentDescription = null, tint = MaterialTheme.colorScheme.primary) },
                    name = folder.name,
                    onClick = { onMove(folder.id) }
                )
            }
        }
        Spacer(Modifier.height(16.dp))
    }
}

@Composable
private fun MoveTargetRow(
    icon: @Composable () -> Unit,
    name: String,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .combinedClickable(onClick = onClick)
            .padding(horizontal = 24.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        icon()
        Spacer(Modifier.width(16.dp))
        Text(name, style = MaterialTheme.typography.bodyLarge)
    }
}
