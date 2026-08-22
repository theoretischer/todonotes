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
import androidx.compose.foundation.layout.padding
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
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import com.earendil.todonotes.getPlatform
import com.earendil.todonotes.ui.notes.ReorderSession
import com.earendil.todonotes.ui.notes.ReorderKind
import com.earendil.todonotes.ui.notes.reorderStep
import kotlinx.coroutines.launch
import kotlin.math.abs

/**
 * Einheitliche Row, die Swipe-to-Delete (horizontal) UND Drag-Reorder
 * (vertikal) in EINEM pointerInput kombiniert (M7d-Fix).
 *
 * Warum ein einziger Handler: Zwei separate pointerInput-Modifier
 * (horizontal + vertikal) konkurrieren — auf Wasm/Desktop gewinnt oft
 * die falsche Geste und die UI bricht. Ein einziger Handler entscheidet
 * nach dem ersten Drag-Betrag, welche Richtung gemeint ist:
 *   |dx| > |dy| → horizontal = Swipe-Delete
 *   |dy| ≥ |dx| → vertikal   = Reorder
 *
 * Touch (Android): Long-Press aktiviert den Drag (verhindert versehentliches
 * Verschieben beim Scrollen). Maus (Desktop/Wasm): Drag sofort ab Drag-Start
 * (kein Long-Press nötig — Maus ist präzise genug).
 *
 * Tap auf den Content (wenn nicht revealed/verschoben) → [onClick].
 * Tap auf die Mülleimer-Kachel → [onDelete].
 *
 * Reorder-Parameter werden nur gebraucht wenn [onSwap] != null.
 */
@Composable
fun SwipeOrReorderRow(
    onDelete: () -> Unit,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    deleteWidth: Dp = 80.dp,
    // Reorder-Parameter (optional — reorderEnabled=false = kein Reorder, nur Swipe)
    reorderEnabled: Boolean = false,
    itemId: String = "",
    repositories: List<String> = emptyList(),
    heightPx: Int = 0,
    reorder: ReorderSession? = null,
    setReorder: (ReorderSession?) -> Unit = {},
    onSwap: (String, String) -> Unit = { _, _ -> },
    content: @Composable () -> Unit
) {
    val density = LocalDensity.current
    val maxOffset = with(density) { -deleteWidth.toPx() }
    val scope = rememberCoroutineScope()
    val offsetX = remember { Animatable(0f) }
    val isTouch = getPlatform().isTouch

    // Reorder-State lokal (für die vertikale Verschiebung während Drag).
    var dragOffsetY by remember { mutableStateOf(0f) }
    var isReordering by remember { mutableStateOf(false) }
    var isSwipeDeleting by remember { mutableStateOf(false) }

    val isDragged = reorder?.draggedId == itemId

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

        // Content darüber, verschiebbar (horizontal für Delete, vertikal für Reorder).
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .offset {
                    IntOffset(
                        (offsetX.value + if (isReordering) 0f else 0f).toInt(),
                        if (isReordering) dragOffsetY.toInt() else 0
                    )
                }
                .background(MaterialTheme.colorScheme.surface)
                .pointerInput(itemId, isTouch) {
                    if (isTouch) {
                        // Touch: Long-Press aktiviert Drag (verhindert versehentliches Verschieben).
                        detectDragGesturesAfterLongPress(
                            onDragStart = {
                                isSwipeDeleting = false
                                isReordering = false
                            },
                            onDragEnd = {
                                scope.launch {
                                    if (isSwipeDeleting) {
                                        // Swipe-Delete: snappen oder zurück
                                        val target = if (offsetX.value < maxOffset / 2f) maxOffset else 0f
                                        offsetX.animateTo(target, tween(250))
                                        if (target == 0f) isSwipeDeleting = false
                                    } else if (isReordering) {
                                        // Reorder beenden
                                        setReorder(null)
                                    }
                                }
                                dragOffsetY = 0f
                                isReordering = false
                                isSwipeDeleting = false
                            },
                            onDragCancel = {
                                scope.launch { offsetX.animateTo(0f, tween(150)) }
                                dragOffsetY = 0f
                                isReordering = false
                                isSwipeDeleting = false
                                setReorder(null)
                            }
                        ) { change, dragAmount ->
                            change.consume()
                            // Erstes Drag-Betrag entscheidet die Richtung.
                            if (!isSwipeDeleting && !isReordering) {
                                if (abs(dragAmount.x) > abs(dragAmount.y)) {
                                    isSwipeDeleting = true
                                } else if (abs(dragAmount.y) > 0.5f && reorderEnabled) {
                                    isReordering = true
                                    // Reorder-Session starten
                                    val idx = repositories.indexOf(itemId)
                                    if (idx >= 0) {
                                        setReorder(ReorderSession(itemId, ReorderKind.NOTE, idx, 0f))
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
                                val s = reorder ?: return@detectDragGesturesAfterLongPress
                                val step = reorderStep(
                                    session = s,
                                    repositories = repositories,
                                    heightPx = heightPx,
                                    dragAmountPx = dragAmount.y,
                                    onSwap = onSwap
                                )
                                setReorder(ReorderSession(itemId, ReorderKind.NOTE, step.newIndex, step.newAccumPx))
                            }
                        }
                    } else {
                        // Maus: Drag sofort ab Start (kein Long-Press).
                        detectDragGestures(
                            onDragStart = {
                                isSwipeDeleting = false
                                isReordering = false
                            },
                            onDragEnd = {
                                scope.launch {
                                    if (isSwipeDeleting) {
                                        val target = if (offsetX.value < maxOffset / 2f) maxOffset else 0f
                                        offsetX.animateTo(target, tween(250))
                                        if (target == 0f) isSwipeDeleting = false
                                    } else if (isReordering) {
                                        setReorder(null)
                                    }
                                }
                                dragOffsetY = 0f
                                isReordering = false
                                isSwipeDeleting = false
                            },
                            onDragCancel = {
                                scope.launch { offsetX.animateTo(0f, tween(150)) }
                                dragOffsetY = 0f
                                isReordering = false
                                isSwipeDeleting = false
                                setReorder(null)
                            }
                        ) { change, dragAmount ->
                            change.consume()
                            if (!isSwipeDeleting && !isReordering) {
                                if (abs(dragAmount.x) > abs(dragAmount.y)) {
                                    isSwipeDeleting = true
                                } else if (abs(dragAmount.y) > 0.5f && reorderEnabled) {
                                    isReordering = true
                                    val idx = repositories.indexOf(itemId)
                                    if (idx >= 0) {
                                        setReorder(ReorderSession(itemId, ReorderKind.NOTE, idx, 0f))
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
                                val s = reorder ?: return@detectDragGestures
                                val step = reorderStep(
                                    session = s,
                                    repositories = repositories,
                                    heightPx = heightPx,
                                    dragAmountPx = dragAmount.y,
                                    onSwap = onSwap
                                )
                                setReorder(ReorderSession(itemId, ReorderKind.NOTE, step.newIndex, step.newAccumPx))
                            }
                        }
                    }
                }
                .clickable {
                    if (isSwipeDeleting || isReordering) return@clickable
                    if (offsetX.value < -10f) {
                        // revealed → zurück snappen, kein onClick
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

