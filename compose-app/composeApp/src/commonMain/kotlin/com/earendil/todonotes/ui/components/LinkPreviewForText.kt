package com.earendil.todonotes.ui.components

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import com.earendil.todonotes.data.entity.LinkPreview
import com.earendil.todonotes.data.repo.LinkPreviewRepository

/**
 * Erste URL in [text] (oder null) — für die Vorschau-Karte reicht eine
 * pro Eintrag (mehrere Karten unter einem Todo wären unruhig).
 */
fun firstUrlIn(text: String): String? =
    if (text.isBlank()) null else findUrls(text).firstOrNull()?.url

/**
 * Holt die Vorschau zur ersten URL im Text und zeigt die Karte
 * (bzw. den Lade-Platzhalter). Nichts, wenn kein Link im Text ist.
 *
 * - [compact] für schmale Todo-Zeilen (nur Titel, 1 Zeile).
 * - [onBubble] für Chat-Blasen (Kontrast auf primaryContainer).
 * - [showPlaceholder]: Lade-Platzhalter (Chat) — in Listen aus, damit
 *   nicht jede Zeile kurz aufpoppt.
 *
 * Der Fetch läuft nur einmal pro URL (Repository dedupliziert + cached);
 * die Karte erscheint, sobald die Daten da sind.
 */
@Composable
fun LinkPreviewForText(
    text: String,
    repository: LinkPreviewRepository?,
    modifier: Modifier = Modifier,
    onBubble: Boolean = false,
    compact: Boolean = false,
    showPlaceholder: Boolean = false
) {
    val url = remember(text) { firstUrlIn(text) }
    if (url == null || repository == null) return

    var preview by remember(url) { mutableStateOf<LinkPreview?>(null) }
    var loading by remember(url) { mutableStateOf(true) }

    LaunchedEffect(url) {
        // Sofort aus dem Cache (auch offline) …
        preview = repository.cached(url)?.takeUnless { it.failed }
        loading = preview == null
        // … und bei Bedarf im Hintergrund holen/auffrischen.
        val fetched = repository.ensure(url)
        if (fetched != null) preview = fetched
        loading = false
    }

    val current = preview
    if (current != null) {
        LinkPreviewCard(
            preview = current,
            url = url,
            modifier = modifier,
            onBubble = onBubble,
            compact = compact
        )
    } else if (loading && showPlaceholder) {
        LinkPreviewPlaceholder(modifier = modifier, onBubble = onBubble)
    }
}
