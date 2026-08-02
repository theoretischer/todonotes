@file:OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)

package com.earendil.todonotes.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch

/**
 * Einheitliche Swipe-to-Reveal-Delete-Row (für alle 4 Tabs gleich).
 *
 * Nach-links-Wischen deckt rechts eine rote Mülleimer-Kachel auf.
 * Tap auf die Kachel → [onDelete]. Tap auf den Content (wenn nicht
 * revealed) → [onClick]. Wenn revealed, snapt ein Tap auf den Content
 * zurück zur Ruheposition (ohne onClick).
 *
 * Nicht sofort löschen beim Wischen — erst der Tap auf den Mülleimer
 * löst die Löschung aus.
 */
@Composable
fun SwipeToDeleteRow(
    onDelete: () -> Unit,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    onLongClick: (() -> Unit)? = null,
    deleteWidth: Dp = 80.dp,
    contentModifier: Modifier = Modifier,
    content: @Composable () -> Unit
) {
    val density = LocalDensity.current
    val maxOffset = with(density) { -deleteWidth.toPx() }
    val scope = rememberCoroutineScope()
    val offsetX = remember { Animatable(0f) }

    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(IntrinsicSize.Min)
    ) {
        // Roter Hintergrund + Mülleimer (rechts), vollflächig dahinter.
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.errorContainer),
            contentAlignment = Alignment.CenterEnd
        ) {
            Box(
                modifier = Modifier
                    .fillMaxHeight()
                    .width(deleteWidth)
                    .clickable {
                        scope.launch {
                            offsetX.animateTo(0f, tween(150))
                            onDelete()
                        }
                    },
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    Icons.Default.Delete,
                    contentDescription = "Löschen",
                    tint = MaterialTheme.colorScheme.onErrorContainer
                )
            }
        }

        // Content darüber, horizontal verschiebbar.
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .offset { IntOffset(offsetX.value.toInt(), 0) }
                .background(MaterialTheme.colorScheme.surface)
                .pointerInput(Unit) {
                    detectHorizontalDragGestures(
                        onDragEnd = {
                            val target = if (offsetX.value < maxOffset / 2f) maxOffset else 0f
                            scope.launch { offsetX.animateTo(target, tween(250)) }
                        }
                    ) { _, dragAmount ->
                        scope.launch {
                            offsetX.snapTo(
                                (offsetX.value + dragAmount).coerceIn(maxOffset, 0f)
                            )
                        }
                    }
                }
                .combinedClickable(
                    onClick = {
                        if (offsetX.value < -10f) {
                            // revealed → zurück snappen, kein onClick
                            scope.launch { offsetX.animateTo(0f, tween(250)) }
                        } else {
                            onClick()
                        }
                    },
                    onLongClick = onLongClick
                )
                .then(contentModifier)
        ) {
            content()
        }
    }
}
