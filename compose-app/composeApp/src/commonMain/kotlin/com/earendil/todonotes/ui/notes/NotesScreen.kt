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
import com.earendil.todonotes.ui.components.SwipeOrReorderRow
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
    var moveNoteTarget by remember { mutableStateOf<Note?>(null) }
    var moveFolderTarget by remember { mutableStateOf<Folder?>(null) }
    var showNewMenu by remember { mutableStateOf(false) }

    val rowHeights = remember { mutableStateMapOf<String, Int>() }
    val folderBounds = remember { mutableStateMapOf<String, Rect>() }  // für Breadcrumb-Drop (unbenutzt, Drag deaktiviert)

    Box(modifier = modifier.fillMaxSize()) {

        // Header-Box: umschließt nur die Breadcrumb (48dp oben). So ist
        // die ROOT-Drop-Hitbox genau auf dem Root-Schriftzug.
        Box(
            modifier = Modifier
                .align(Alignment.TopStart)
                .fillMaxWidth()
                .zIndex(5f)
                .onGloballyPositioned { coords ->
                    folderBounds[ROOT_DROP_KEY] = coords.boundsInRoot()
                }
        ) {
            NotesBreadcrumb(
                crumbs = state.breadcrumbs,
                onCrumb = notesVm::navigateToCrumb
            )
        }

        if (state.folders.isEmpty() && state.notes.isEmpty()) {
            EmptyNotesHint()
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(start = 16.dp, top = 96.dp, end = 16.dp, bottom = 88.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(state.folders, key = { it.id }) { folder ->
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .onSizeChanged { rowHeights[folder.id] = it.height }
                            .onGloballyPositioned { folderBounds[folder.id] = it.boundsInRoot() }
                    ) {
                        SwipeOrReorderRow(
                            onDelete = { notesVm.deleteFolder(folder.id) },
                            onClick = { notesVm.openFolder(folder) },
                            reorderEnabled = true,
                            itemId = folder.id,
                            repositories = state.folders.map { it.id },
                            heightPx = rowHeights[folder.id] ?: 0,
                            onSwap = { a, b -> notesVm.reorderFolders(a, b) },
                            onReorderBegin = { notesVm.beginFolderReorder() },
                            onReorderEnd = { notesVm.commitFolderReorder() }
                        ) {
                            FolderRow(
                                folder = folder,
                                onRename = { renameTarget = folder },
                                onMove = { moveFolderTarget = folder }
                            )
                        }
                    }
                }
                if (state.folders.isNotEmpty() && state.notes.isNotEmpty()) {
                    item { Spacer(Modifier.height(4.dp)) }
                }
                items(state.notes, key = { it.id }) { note ->
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .onSizeChanged { rowHeights[note.id] = it.height }
                    ) {
                        SwipeOrReorderRow(
                            onDelete = { notesVm.deleteNote(note.id) },
                            onClick = { onOpenNote(note.id, false, note.type) },
                            reorderEnabled = true,
                            itemId = note.id,
                            repositories = state.notes.map { it.id },
                            heightPx = rowHeights[note.id] ?: 0,
                            onSwap = { a, b -> notesVm.reorderNotes(a, b) },
                            onReorderBegin = { notesVm.beginNoteReorder() },
                            onReorderEnd = { notesVm.commitNoteReorder() },
                            folderBounds = folderBounds,
                            onDropOnFolder = { noteId, folderId ->
                                // ROOT_DROP_KEY (Breadcrumb) bedeutet Root-Ordner = null
                                val targetFolderId = if (folderId == ROOT_DROP_KEY) null else folderId
                                notesVm.moveNote(noteId, targetFolderId)
                            }
                        ) {
                            NoteRow(
                                note = note,
                                onMove = { moveNoteTarget = note }
                            )
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

    moveNoteTarget?.let { note ->
        MoveNoteSheet(
            note = note,
            currentFolderId = state.currentFolderId,
            onMove = { targetFolderId ->
                notesVm.moveNote(note.id, targetFolderId)
                moveNoteTarget = null
            },
            onDismiss = { moveNoteTarget = null },
            loadFolders = { notesVm.getAllFoldersForMove() }
        )
    }

    moveFolderTarget?.let { folder ->
        MoveFolderSheet(
            folder = folder,
            currentParentId = folder.parentId,
            onMove = { targetParentId ->
                notesVm.moveFolder(folder.id, targetParentId)
                moveFolderTarget = null
            },
            onDismiss = { moveFolderTarget = null },
            loadFolders = { notesVm.getAllFoldersForMove() }
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
private fun FolderRow(
    folder: Folder,
    onRename: () -> Unit,
    onMove: () -> Unit
) {
    var menuExpanded by remember { mutableStateOf(false) }
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Row(
            modifier = Modifier.padding(start = 16.dp, top = 16.dp, bottom = 16.dp, end = 8.dp),
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
            // ⋮-Menü
            Box {
                IconButton(onClick = { menuExpanded = true }) {
                    Icon(Icons.Default.MoreVert, contentDescription = "Optionen")
                }
                DropdownMenu(
                    expanded = menuExpanded,
                    onDismissRequest = { menuExpanded = false }
                ) {
                    DropdownMenuItem(
                        text = { Text("Umbenennen") },
                        onClick = { menuExpanded = false; onRename() }
                    )
                    DropdownMenuItem(
                        text = { Text("Verschieben") },
                        onClick = { menuExpanded = false; onMove() }
                    )
                }
            }
        }
    }
}

@Composable
private fun NoteRow(
    note: Note,
    onMove: () -> Unit
) {
    val preview = remember(note.bodyJson) { notePreview(note) }
    var menuExpanded by remember { mutableStateOf(false) }
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Row(
            modifier = Modifier.padding(start = 16.dp, top = 16.dp, bottom = 16.dp, end = 8.dp),
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
            // ⋮-Menü
            Box {
                IconButton(onClick = { menuExpanded = true }) {
                    Icon(Icons.Default.MoreVert, contentDescription = "Optionen")
                }
                DropdownMenu(
                    expanded = menuExpanded,
                    onDismissRequest = { menuExpanded = false }
                ) {
                    DropdownMenuItem(
                        text = { Text("Verschieben") },
                        onClick = { menuExpanded = false; onMove() }
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun MoveFolderSheet(
    folder: Folder,
    currentParentId: String?,
    onMove: (String?) -> Unit,
    onDismiss: () -> Unit,
    loadFolders: suspend () -> List<Folder>
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var folders by remember { mutableStateOf<List<Folder>>(emptyList()) }
    LaunchedEffect(Unit) { folders = loadFolders() }

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState) {
        Text(
            "Ordner verschieben",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.padding(start = 24.dp, top = 4.dp, bottom = 8.dp)
        )
        if (currentParentId != null) {
            MoveTargetRow(
                icon = { Icon(Icons.Default.Folder, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant) },
                name = "Hauptebene",
                onClick = { onMove(null) }
            )
            HorizontalDivider()
        }
        // Eigenen Ordner + Nachfahren herausfiltern (Zyklen-Vermeidung).
        val candidates = folders.filter { it.id != folder.id && it.id != currentParentId }
        if (candidates.isEmpty() && currentParentId == null) {
            Text(
                "Keine Ziel-Ordner vorhanden.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(24.dp)
            )
        } else {
            candidates.forEach { target ->
                MoveTargetRow(
                    icon = { Icon(Icons.Default.Folder, contentDescription = null, tint = MaterialTheme.colorScheme.primary) },
                    name = target.name,
                    onClick = { onMove(target.id) }
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
