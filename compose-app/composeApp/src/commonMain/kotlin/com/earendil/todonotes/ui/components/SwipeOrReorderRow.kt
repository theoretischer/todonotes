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
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
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
 * Einheitliche Row, die Swipe-to-Delete (horizontal) UND Drag-Reorder
 * (vertikal) in EINEM pointerInput kombiniert (M7d-Fix).
 *
 * Ein einziger Handler entscheidet nach dem ersten Drag-Betrag, welche
 * Richtung gemeint ist:
 *   |dx| > |dy| → horizontal = Swipe-Delete
 *   |dy| ≥ |dx| → vertikal   = Reorder
 *
 * Touch (Android): Long-Press aktiviert den Drag (verhindert versehentliches
 * Verschieben beim Scrollen). Maus (Desktop/Wasm): Drag sofort ab Drag-Start.
 *
 * Während Reorder wird die Row mit [zIndex] nach oben gehoben (über den
 * anderen Rows) und der rote Delete-Hintergrund ausgeblendet.
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
    content: @Composable () -> Unit
) {
    val density = LocalDensity.current
    val maxOffset = with(density) { -deleteWidth.toPx() }
    val scope = rememberCoroutineScope()
    val offsetX = remember { Animatable(0f) }
    val isTouch = getPlatform().isTouch

    // Live-Werte für den pointerInput-Block (rememberUpdatedState, damit der
    // Block nicht re-launcht muss wenn sich repositories/heightPx ändern).
    val currentRepos by rememberUpdatedState(repositories)
    val currentHeight by rememberUpdatedState(heightPx)
    val currentOnSwap by rememberUpdatedState(onSwap)

    // Lokaler State: welche Geste läuft, wo ist der Finger.
    var isSwipeDeleting by remember { mutableStateOf(false) }
    var isReordering by remember { mutableStateOf(false) }
    var dragOffsetY by remember { mutableStateOf(0f) }
    // Lokale Reorder-Session (wird im pointerInput-Block gehalten und
    // weitergereicht — NICHT über externen State, das war der Bug).
    var session by remember { mutableStateOf<ReorderSession?>(null) }

    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(IntrinsicSize.Min)
            .zIndex(if (isReordering) 10f else 0f)
    ) {
        // Roter Hintergrund + Mülleimer — nur sichtbar bei Swipe-Delete.
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
                    val handleDrag: (dragAmount: androidx.compose.ui.geometry.Offset) -> Unit = { dragAmount ->
                        // Erstes Drag-Betrag entscheidet die Richtung.
                        if (!isSwipeDeleting && !isReordering) {
                            if (abs(dragAmount.x) > abs(dragAmount.y)) {
                                isSwipeDeleting = true
                            } else if (abs(dragAmount.y) > 0.5f && reorderEnabled) {
                                isReordering = true
                                val idx = currentRepos.indexOf(itemId)
                                if (idx >= 0) {
                                    session = ReorderSession(itemId, ReorderKind.NOTE, idx, 0f)
                                    onReorderBegin()  // DB-Flow ignorieren ab jetzt
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
                                val step = reorderStep(
                                    session = s,
                                    repositories = currentRepos,
                                    heightPx = currentHeight,
                                    dragAmountPx = dragAmount.y,
                                    onSwap = currentOnSwap
                                )
                                session = ReorderSession(itemId, ReorderKind.NOTE, step.newIndex, step.newAccumPx)
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
                                session = null
                                onReorderEnd()  // DB-Batch schreiben
                            }
                        }
                        dragOffsetY = 0f
                        isReordering = false
                        isSwipeDeleting = false
                    }

                    val handleCancel: () -> Unit = {
                        scope.launch { offsetX.animateTo(0f, tween(150)) }
                        dragOffsetY = 0f
                        isReordering = false
                        isSwipeDeleting = false
                        session = null
                        onReorderEnd()  // Drag abgebrochen → DB-Batch (mit orig. Reihenfolge)
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
