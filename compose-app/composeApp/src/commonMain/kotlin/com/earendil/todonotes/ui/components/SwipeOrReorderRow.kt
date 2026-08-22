@file:OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)

package com.earendil.todonotes.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import com.earendil.todonotes.getPlatform
import com.earendil.todonotes.ui.notes.ReorderKind
import com.earendil.todonotes.ui.notes.ReorderSession
import com.earendil.todonotes.ui.notes.reorderStep
import kotlinx.coroutines.launch
import kotlin.math.abs

/**
 * Einheitliche Row: Swipe-Delete (horizontal) + Drag-Reorder (vertikal) +
 * Drag-auf-Ordner (Notiz auf Ordner loslassen = Verschieben).
 *
 * Ein einziger pointerInput-Handler entscheidet nach dem ersten Drag-Betrag:
 *   |dx| > |dy| → horizontal = Swipe-Delete
 *   |dy| ≥ |dx| → vertikal   = Reorder (innerhalb der Liste)
 *
 * Beim Loslassen wird geprüft ob der Finger über einem Ordner war (via
 * [folderBounds]) → [onDropOnFolder]. Das ist der Drag-Verschiebe-Modus.
 *
 * Nach jedem Swap wird dragOffsetY um heightPx korrigiert, damit die
 * gezogene Row optisch mit dem Finger verbunden bleibt (nicht eins springt).
 *
 * Touch: Long-Press aktiviert Drag. Maus: Drag sofort.
 */
@Composable
fun SwipeOrReorderRow(
    onDelete: () -> Unit,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    deleteWidth: Dp = 80.dp,
    reorderEnabled: Boolean = false,
    itemId: String = "",
    repositories: List<String> = emptyList(),
    heightPx: Int = 0,
    onSwap: (String, String) -> Unit = { _, _ -> },
    onReorderBegin: () -> Unit = {},
    onReorderEnd: () -> Unit = {},
    folderBounds: Map<String, Rect> = emptyMap(),
    onDropOnFolder: (itemId: String, folderId: String) -> Unit = { _, _ -> },
    content: @Composable () -> Unit
) {
    val density = LocalDensity.current
    val maxOffset = with(density) { -deleteWidth.toPx() }
    val scope = rememberCoroutineScope()
    val offsetX = remember { Animatable(0f) }
    val isTouch = getPlatform().isTouch

    val currentRepos by rememberUpdatedState(repositories)
    val currentHeight by rememberUpdatedState(heightPx)
    val currentOnSwap by rememberUpdatedState(onSwap)
    val currentBounds by rememberUpdatedState(folderBounds)
    val currentDropFolder by rememberUpdatedState(onDropOnFolder)

    var isSwipeDeleting by remember { mutableStateOf(false) }
    var isReordering by remember { mutableStateOf(false) }
    var dragOffsetY by remember { mutableStateOf(0f) }
    var session by remember { mutableStateOf<ReorderSession?>(null) }
    var nodeRoot by remember { mutableStateOf(Offset.Zero) }

    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(IntrinsicSize.Min)
            .zIndex(if (isReordering) 10f else 0f)
            .onGloballyPositioned { nodeRoot = it.positionInRoot() }
    ) {
        // Roter Hintergrund + Mülleimer — nur bei Swipe-Delete.
        if (isSwipeDeleting || offsetX.value < 0f) {
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
        }

        // Content darüber, verschiebbar.
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .offset {
                    IntOffset(
                        offsetX.value.toInt(),
                        if (isReordering) dragOffsetY.toInt() else 0
                    )
                }
                .background(MaterialTheme.colorScheme.surface)
                .pointerInput(itemId, isTouch) {
                    val handleDrag: (dragAmount: Offset) -> Unit = { dragAmount ->
                        if (!isSwipeDeleting && !isReordering) {
                            if (abs(dragAmount.x) > abs(dragAmount.y)) {
                                isSwipeDeleting = true
                            } else if (abs(dragAmount.y) > 0.5f && reorderEnabled) {
                                isReordering = true
                                val idx = currentRepos.indexOf(itemId)
                                if (idx >= 0) {
                                    session = ReorderSession(itemId, ReorderKind.NOTE, idx, 0f)
                                    onReorderBegin()
                                }
                            }
                        }
                        if (isSwipeDeleting) {
                            scope.launch {
                                offsetX.snapTo(
                                    (offsetX.value + dragAmount.x).coerceIn(maxOffset, 0f)
                                )
                            }
                        } else if (isReordering) {
                            dragOffsetY += dragAmount.y
                            val s = session
                            if (s != null) {
                                val oldIndex = s.index
                                val step = reorderStep(
                                    session = s,
                                    repositories = currentRepos,
                                    heightPx = currentHeight,
                                    dragAmountPx = dragAmount.y,
                                    onSwap = currentOnSwap
                                )
                                session = ReorderSession(itemId, ReorderKind.NOTE, step.newIndex, step.newAccumPx)
                                // Visuelle Korrektur: nach einem Swap wandert die Row in der
                                // Liste eins weiter — dragOffsetY muss um eine Zeilenhöhe
                                // zurückgesetzt werden, damit die Row mit dem Finger verbunden
                                // bleibt (sonst springt sie eins zu weit).
                                val swaps = step.newIndex - oldIndex
                                if (swaps != 0 && currentHeight > 0) {
                                    dragOffsetY -= swaps * currentHeight.toFloat()
                                }
                            }
                        }
                    }

                    val handleEnd: () -> Unit = {
                        scope.launch {
                            if (isSwipeDeleting) {
                                val target = if (offsetX.value < maxOffset / 2f) maxOffset else 0f
                                offsetX.animateTo(target, tween(250))
                                if (target == 0f) isSwipeDeleting = false
                            } else if (isReordering) {
                                // Prüfen ob über einem Ordner losgelassen wurde.
                                val fingerGlobal = nodeRoot + Offset(0f, dragOffsetY)
                                val hitId = currentBounds.entries.firstOrNull { (_, rect) ->
                                    rect.contains(fingerGlobal)
                                }?.key
                                if (hitId != null && hitId != itemId) {
                                    currentDropFolder(itemId, hitId)
                                }
                                session = null
                                onReorderEnd()
                            }
                            // WICHTIG: Resets ERST nach den Checks oben,
                            // innerhalb des Coroutines. Sonst sind sie schon
                            // false bevor der Coroutine-Body läuft (scope.launch
                            // ist async!).
                            dragOffsetY = 0f
                            isReordering = false
                            isSwipeDeleting = false
                        }
                    }

                    val handleCancel: () -> Unit = {
                        scope.launch {
                            offsetX.animateTo(0f, tween(150))
                            if (isReordering) {
                                session = null
                                onReorderEnd()
                            }
                            dragOffsetY = 0f
                            isReordering = false
                            isSwipeDeleting = false
                        }
                    }

                    if (isTouch) {
                        detectDragGesturesAfterLongPress(
                            onDragStart = {
                                isSwipeDeleting = false
                                isReordering = false
                                session = null
                            },
                            onDragEnd = handleEnd,
                            onDragCancel = handleCancel,
                            onDrag = { change, dragAmount ->
                                change.consume()
                                handleDrag(dragAmount)
                            }
                        )
                    } else {
                        detectDragGestures(
                            onDragStart = {
                                isSwipeDeleting = false
                                isReordering = false
                                session = null
                            },
                            onDragEnd = handleEnd,
                            onDragCancel = handleCancel,
                            onDrag = { change, dragAmount ->
                                change.consume()
                                handleDrag(dragAmount)
                            }
                        )
                    }
                }
                .clickable {
                    if (isSwipeDeleting || isReordering) return@clickable
                    if (offsetX.value < -10f) {
                        scope.launch { offsetX.animateTo(0f, tween(250)) }
                    } else {
                        onClick()
                    }
                }
        ) {
            content()
        }
    }
}
