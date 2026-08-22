@file:OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)

package com.earendil.todonotes.ui.notes

import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Chat
import androidx.compose.material.icons.filled.CreateNewFolder
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.NoteAdd
import androidx.compose.material.icons.filled.TextSnippet
import androidx.compose.material3.*
import androidx.compose.runtime.*
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
import com.earendil.todonotes.data.entity.Folder
import com.earendil.todonotes.data.entity.Note
import com.earendil.todonotes.data.entity.NoteType
import com.earendil.todonotes.data.richtext.NoteTextBody
import com.earendil.todonotes.ui.Crumb
import com.earendil.todonotes.ui.NotesViewModel
import com.earendil.todonotes.ui.components.SwipeToDeleteRow
import androidx.compose.runtime.collectAsState

/**
 * Special-Key im [folderBounds]-Registry für die Wurzel-Breadcrumb.
 */
internal const val ROOT_DROP_KEY = "__root__"

/**
 * Notizen-Tab: Ordner- & Notiz-Übersicht.
 *
 * Aufbau:
 *  - Breadcrumb-Pfad oben (Wurzel › Ordner › …) — tapbar zum Navigieren.
 *  - Liste: erst Ordner, dann Notizen des aktuellen Ordners.
 *  - FAB `+`: „Neue Notiz" / „Neuer Ordner" / „Neuer Chat".
 *  - Tippen Ordner → rein. Tippen Notiz → Editor/Chat.
 *  - Long-press + hoch/runter: Reorder. Notiz auf Ordner ziehen: Verschieben.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NotesScreen(
    notesVm: NotesViewModel,
    onOpenNote: (noteId: String, isNew: Boolean, type: NoteType) -> Unit,
    onOpenChat: (noteId: String, isNew: Boolean) -> Unit,
    modifier: Modifier = Modifier
) {
    val state by notesVm.browserState.collectAsState()

    var showCreateFolderDialog by remember { mutableStateOf(false) }
    var renameTarget by remember { mutableStateOf<Folder?>(null) }
    var moveTarget by remember { mutableStateOf<Note?>(null) }
    var showNewMenu by remember { mutableStateOf(false) }

    val rowHeights = remember { mutableStateMapOf<String, Int>() }
    var reorder by remember { mutableStateOf<ReorderSession?>(null) }
    val folderBounds = remember { mutableStateMapOf<String, Rect>() }

    Box(modifier = modifier.fillMaxSize()) {

        NotesBreadcrumb(
            crumbs = state.breadcrumbs,
            onCrumb = notesVm::navigateToCrumb,
            modifier = Modifier
                .align(Alignment.TopStart)
                .zIndex(5f)
                .onGloballyPositioned { coords ->
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
                                val target = if (folderIdOrRoot == ROOT_DROP_KEY) null else folderIdOrRoot
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

        FloatingActionButton(
            onClick = { showNewMenu = true },
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(16.dp)
                .zIndex(20f)
        ) {
            Icon(Icons.Default.Add, contentDescription = "Neu")
        }
    }

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
    val currentKind by rememberUpdatedState(kind)
    val currentRepos by rememberUpdatedState(repositories)
    val currentHeight by rememberUpdatedState(heightPx)
    val currentSetReorder by rememberUpdatedState(setReorder)
    val currentOnSwap by rememberUpdatedState(onSwap)
    val currentBounds by rememberUpdatedState(folderBounds)
    val currentDropFolder by rememberUpdatedState(onDropOnFolder)
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
                lastGlobal = nodeRoot + change.position
                val s = session ?: return@detectDragGesturesAfterLongPress
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
                if (currentKind == ReorderKind.NOTE) {
                    hit()?.let { currentDropFolder(itemId, it) }
                }
                currentSetReorder(null)
            },
            onDragCancel = { currentSetReorder(null) }
        )
    }
}

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

@Composable
private fun FolderRow(folder: Folder) {
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

@Composable
private fun NoteRow(note: Note) {
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

/** Extrahiert reinen Text aus dem ersten Paragraph als Listen-Vorschau. */
private fun notePreview(note: Note): String {
    if (note.type == NoteType.CHAT) return "Chat-Notiz"
    val text = NoteTextBody.migrateFromBlocks(note.bodyJson)
    return text.lineSequence()
        .firstOrNull { it.isNotBlank() }
        ?.trim()
        ?.let { NoteTextBody.stripPrefix(it).trim() }
        ?: ""
}

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
