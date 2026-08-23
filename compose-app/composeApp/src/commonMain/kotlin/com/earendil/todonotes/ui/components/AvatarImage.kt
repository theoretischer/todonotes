package com.earendil.todonotes.ui.components

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.earendil.todonotes.decodeImageBitmap

/**
 * Avatar-Kreis: zeigt das Profilbild (als decoded Bitmap) oder
 * den ersten Buchstaben des Anzeigenamens (Initialen-Placeholder).
 */
@Composable
fun AvatarImage(
    bitmap: ImageBitmap?,
    displayName: String?,
    modifier: Modifier = Modifier,
    sizeDp: Int = 40
) {
    Box(
        modifier = modifier
            .size(sizeDp.dp)
            .background(MaterialTheme.colorScheme.primaryContainer, CircleShape),
        contentAlignment = Alignment.Center
    ) {
        if (bitmap != null) {
            Image(
                bitmap = bitmap,
                contentDescription = "Avatar",
                contentScale = ContentScale.Crop,
                modifier = Modifier.size(sizeDp.dp)
            )
        } else {
            val initial = remember(displayName) {
                displayName?.firstOrNull { it.isLetterOrDigit() }?.uppercase()
            }
            if (initial != null) {
                Text(
                    initial,
                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                    fontWeight = FontWeight.Bold,
                    style = MaterialTheme.typography.titleMedium
                )
            } else {
                Icon(
                    Icons.Filled.Person,
                    contentDescription = "Avatar",
                    tint = MaterialTheme.colorScheme.onPrimaryContainer,
                    modifier = Modifier.size((sizeDp * 0.6f).dp)
                )
            }
        }
    }
}

/** Hilfsfunktion: ByteArray → ImageBitmap (cached via remember). */
@Composable
fun rememberImageBitmap(bytes: ByteArray?): ImageBitmap? {
    return remember(bytes) {
        bytes?.let { decodeImageBitmap(it) }
    }
}
