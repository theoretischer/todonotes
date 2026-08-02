package com.earendil.todonotes.ui.notes

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import com.earendil.todonotes.ui.NotesViewModel

/**
 * Notizen-Tab (Block F4 baut die echte UI; F2 verdrahtet nur das ViewModel).
 *
 * Aktuell noch Platzhalter, aber das ViewModel hängt schon dran und wird
 * im nächsten Schritt (F4) für Ordner-/Notiz-Liste + Breadcrumb genutzt.
 */
@Composable
fun NotesScreen(
    notesVm: NotesViewModel,
    modifier: Modifier = Modifier
) {
    val state by notesVm.browserState.collectAsState()

    Box(
        modifier = modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text("Notizen", style = MaterialTheme.typography.titleLarge)
            Text(
                "Coming soon (F4)",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            // Debug-Anzeige, damit man sieht dass das ViewModel läuft:
            Text(
                "Ordner: ${state.folders.size} · Notizen: ${state.notes.size}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun Column(
    modifier: Modifier = Modifier,
    horizontalAlignment: Alignment.Horizontal = Alignment.Start,
    content: @Composable androidx.compose.foundation.layout.ColumnScope.() -> Unit
) {
    androidx.compose.foundation.layout.Column(
        modifier = modifier,
        horizontalAlignment = horizontalAlignment,
        content = content
    )
}
