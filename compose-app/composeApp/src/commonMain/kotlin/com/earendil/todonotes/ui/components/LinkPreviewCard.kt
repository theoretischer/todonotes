package com.earendil.todonotes.ui.components

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Link
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.earendil.todonotes.data.entity.LinkPreview
import com.earendil.todonotes.decodeImageBitmap

/**
 * Link-Vorschau-Karte (F7): Bild + Site-Name + Titel + Beschreibung.
 *
 * Wird unter Chat-Nachrichten und in Todo-Zeilen eingeblendet, wenn der
 * Text eine URL enthält. Tap öffnet die URL im Browser (wie der
 * klickbare Link selbst).
 */
@Composable
fun LinkPreviewCard(
    preview: LinkPreview,
    url: String,
    modifier: Modifier = Modifier,
    /** In der Chat-Blase (primaryContainer) → leicht transparenter
     *  Hintergrund statt surfaceVariant, sonst wirkt die Karte wie ein Loch. */
    onBubble: Boolean = false,
    /** Todo-Zeilen sind schmal — Beschreibung weglassen, nur Titel. */
    compact: Boolean = false
) {
    if (preview.failed) return
    if (preview.title.isBlank() && preview.imageBytes == null && preview.siteName.isBlank()) return

    val bitmap = remember(preview.imageBytes) {
        preview.imageBytes?.let { decodeImageBitmap(it) }
    }
    val uriHandler = LocalUriHandler.current
    val bg = if (onBubble) {
        MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.10f)
    } else {
        MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f)
    }
    val textColor = if (onBubble) MaterialTheme.colorScheme.onPrimaryContainer
                    else MaterialTheme.colorScheme.onSurface
    val subColor = textColor.copy(alpha = 0.7f)

    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .background(bg)
            .clickable { uriHandler.openUri(url) }
    ) {
        if (bitmap != null) {
            Image(
                bitmap = bitmap,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(1.9f)
            )
        }
        Row(
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (bitmap == null) {
                Icon(
                    Icons.Filled.Link,
                    contentDescription = null,
                    tint = subColor,
                    modifier = Modifier.size(14.dp)
                )
                Spacer(Modifier.width(6.dp))
            }
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(1.dp)) {
                if (preview.siteName.isNotBlank()) {
                    Text(
                        text = preview.siteName,
                        style = MaterialTheme.typography.labelSmall,
                        color = subColor,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                if (preview.title.isNotBlank()) {
                    Text(
                        text = preview.title,
                        style = MaterialTheme.typography.bodySmall,
                        fontWeight = FontWeight.Medium,
                        color = textColor,
                        maxLines = if (compact) 1 else 2,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                if (!compact && preview.description.isNotBlank()) {
                    Text(
                        text = preview.description,
                        style = MaterialTheme.typography.labelSmall,
                        color = subColor,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                }            }
        }
    }
}

/** Lade-Platzhalter, solange der Fetch läuft (nur Chat — in Todo-Zeilen
 *  wäre er zu unruhig, dort erscheint die Karte einfach wenn sie da ist). */
@Composable
fun LinkPreviewPlaceholder(
    modifier: Modifier = Modifier,
    onBubble: Boolean = false
) {
    val bg = if (onBubble) {
        MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.08f)
    } else {
        MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
    }
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(38.dp)
            .clip(RoundedCornerShape(10.dp))
            .background(bg)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                Icons.Filled.Link,
                contentDescription = null,
                tint = (if (onBubble) MaterialTheme.colorScheme.onPrimaryContainer
                        else MaterialTheme.colorScheme.onSurface).copy(alpha = 0.4f),
                modifier = Modifier.size(14.dp)
            )
            Spacer(Modifier.width(6.dp))
            Text(
                "Link-Vorschau…",
                style = MaterialTheme.typography.labelSmall,
                color = (if (onBubble) MaterialTheme.colorScheme.onPrimaryContainer
                         else MaterialTheme.colorScheme.onSurface).copy(alpha = 0.5f)
            )
        }
    }
}
