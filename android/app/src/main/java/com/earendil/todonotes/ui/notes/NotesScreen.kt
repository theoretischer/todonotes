@file:OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)

package com.earendil.todonotes.ui.notes

import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.earendil.todonotes.data.entity.Folder
import com.earendil.todonotes.data.entity.Note
import com.earendil.todonotes.data.richtext.NoteBodyJson
import com.earendil.todonotes.ui.Crumb
import com.earendil.todonotes.ui.NotesViewModel

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
    onOpenNote: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val state by notesVm.browserState.collectAsStateWithLifecycle()

    var showCreateFolderDialog by rememberSaveable { mutableStateOf(false) }
    var renameTarget by remember { mutableStateOf<Folder?>(null) }
    var deleteTarget by remember { mutableStateOf<Any?>(null) }
    var showNewMenu by rememberSaveable { mutableStateOf(false) }

    Scaffold(
        modifier = modifier,
        topBar = { NotesBreadcrumb(crumbs = state.breadcrumbs, onCrumb = notesVm::navigateToCrumb) },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = { showNewMenu = true },
                icon = { Icon(Icons.Default.Add, contentDescription = null) },
                text = { Text("Neu") }
            )
        }
    ) { padding ->
        if (state.folders.isEmpty() && state.notes.isEmpty()) {
            EmptyNotesHint(modifier = Modifier.padding(padding))
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize().padding(padding),
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                // Ordner zuerst
                items(state.folders, key = { it.id }) { folder ->
                    FolderRow(
                        folder = folder,
                        onOpen = { notesVm.openFolder(folder) },
                        onRename = { renameTarget = folder },
                        onDelete = { deleteTarget = folder }
                    )
                }
                if (state.folders.isNotEmpty() && state.notes.isNotEmpty()) {
                    item { Spacer(Modifier.height(4.dp)) }
                }
                // dann Notizen
                items(state.notes, key = { it.id }) { note ->
                    NoteRow(
                        note = note,
                        onOpen = { onOpenNote(note.id) },
                        onDelete = { deleteTarget = note }
                    )
                }
                item { Spacer(Modifier.height(80.dp)) }
            }
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
                    notesVm.createNote(onCreated = onOpenNote)
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

    deleteTarget?.let { target ->
        val name = when (target) {
            is Folder -> target.name
            is Note -> target.title.ifBlank { "Notiz" }
            else -> ""
        }
        AlertDialog(
            onDismissRequest = { deleteTarget = null },
            confirmButton = {
                TextButton(onClick = {
                    when (target) {
                        is Folder -> notesVm.deleteFolder(target.id)
                        is Note -> notesVm.deleteNote(target.id)
                    }
                    deleteTarget = null
                }) { Text("Löschen") }
            },
            dismissButton = {
                TextButton(onClick = { deleteTarget = null }) { Text("Abbrechen") }
            },
            title = { Text("Löschen?") },
            text = {
                val extra = if (target is Folder)
                    " Der Ordner \"$name\" und alle enthaltenen Notizen/Unterordner werden als gelöscht markiert."
                else ""
                Text("„$name\" wird gelöscht.$extra")
            }
        )
    }
}

// ---- Breadcrumb ----

@Composable
private fun NotesBreadcrumb(
    crumbs: List<Crumb>,
    onCrumb: (Crumb) -> Unit
) {
    Row(
        modifier = Modifier
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
    folder: Folder,
    onOpen: () -> Unit,
    onRename: () -> Unit,
    onDelete: () -> Unit
) {
    var menuExpanded by remember { mutableStateOf(false) }
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .combinedClickable(onClick = onOpen, onLongClick = { menuExpanded = true }),
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
                        leadingIcon = { Icon(Icons.Default.Edit, contentDescription = null) },
                        onClick = { menuExpanded = false; onRename() }
                    )
                    DropdownMenuItem(
                        text = { Text("Löschen") },
                        leadingIcon = { Icon(Icons.Default.Delete, contentDescription = null) },
                        onClick = { menuExpanded = false; onDelete() }
                    )
                }
            }
        }
    }
}

// ---- Notiz-Zeile ----

@Composable
private fun NoteRow(
    note: Note,
    onOpen: () -> Unit,
    onDelete: () -> Unit
) {
    val preview = remember(note.bodyJson) { notePreview(note) }
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .combinedClickable(onClick = onOpen, onLongClick = onDelete),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                Icons.Default.TextSnippet,
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
    val body = NoteBodyJson.decode(note.bodyJson)
    val firstPara = body.blocks.firstOrNull() as? com.earendil.todonotes.data.richtext.Block.Paragraph
        ?: return ""
    return firstPara.segments.joinToString("") { it.text }.trim()
}
