@file:OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)

package com.earendil.todonotes.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectVerticalDragGestures
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
import androidx.compose.ui.input.pointer.PointerInputChange
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

/**
 * Einheitliche Row: Swipe-Delete (horizontal) + Drag-Reorder (vertikal) +
 * Drag-auf-Ordner (Notiz auf Ordner loslassen = Verschieben).
 *
 * ZWEI GETRENNTE Gesten-Erkenner (Fix „Scrollen kaputt“):
 *  - INSTANT horizontal wischen → Swipe-Delete (sofort, kein Long-Press —
 *    konsistent mit Aufgaben/Verlauf). Feuert NUR bei horizontal
 *    dominanter Bewegung.
 *  - Vertikal ziehen → Reorder: am Handy NUR nach Long-Press (sonst
 *    könnte man die Liste nicht mehr scrollen), mit der Maus sofort
 *    (vertikal-dominant — Desktop scrollt per Rad, nicht per Drag).
 *
 * Das vertikale Listen-Scrollen bleibt frei: Beide Erkenner konsumieren
 * erst, wenn ihre Richtung dominiert bzw. der Long-Press gefeuert ist.
 *
 * Beim Loslassen (Reorder) wird geprüft, ob der Finger über einem Ordner
 * war (via [folderBounds]) → [onDropOnFolder] (= Verschieben). Bei
 * Folder-Drop KEIN onReorderEnd — moveNote() macht den DB-Write.
 *
 * Nach jedem Swap wird dragOffsetY per Render-Zeit-Korrektur (im
 * offset-Modifier) angeglichen, damit die gezogene Row optisch mit
 * dem Finger verbunden bleibt (kein Einspringen).
 */
@Composable
fun SwipeOrReorderRow(
    onDelete: () -> Unit,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    deleteWidth: Dp = 80.dp,
    reorderEnabled: Boolean = false,
    reorderKind: ReorderKind = ReorderKind.NOTE,
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
    // Y-Position der Row beim Drag-Start. Wird mit nodeRoot (aktuelle
    // Position via onGloballyPositioned) verglichen, um die tatsaechliche
    // Listen-Verschiebung zu berechnen — immer exakt, unabhaengig von
    // Row-Hoehe oder Spacing (kein Drift ueber lange Drags).
    var nodeRootStartY by remember { mutableStateOf(0f) }

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
                    .background(MaterialTheme.colorScheme.errorContainer, RoundedCornerShape(16.dp)),
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
                    // Positionsbasierte Korrektur: Die tatsaechliche Listen-
                    // Verschiebung = nodeRoot.y (jetzt) - nodeRootStartY (Start).
                    // Immer exakt, unabhaengig von Row-Hoehe oder spacedBy.
                    // Die Row folgt dem Finger 1:1 (kein Drift).
                    val indexCorrection = if (isReordering) nodeRoot.y - nodeRootStartY else 0f
                    IntOffset(
                        offsetX.value.toInt(),
                        if (isReordering) (dragOffsetY - indexCorrection).toInt() else 0
                    )
                }
                .background(MaterialTheme.colorScheme.surface, RoundedCornerShape(16.dp))
                // --- Geste A: INSTANT horizontales Wischen → Swipe-Delete ---
                // Feuert sofort (kein Long-Press), aber NUR wenn horizontal
                // dominiert. Vertikale Bewegung wird NICHT konsumiert →
                // LazyColumn scrollt frei.
                .pointerInput(itemId) {
                    detectHorizontalDragGestures(
                        onDragStart = {
                            isSwipeDeleting = true
                            isReordering = false
                            session = null
                        },
                        onDragEnd = {
                            scope.launch {
                                val target =
                                    if (offsetX.value < maxOffset / 2f) maxOffset else 0f
                                offsetX.animateTo(target, tween(250))
                                if (target == 0f) isSwipeDeleting = false
                            }
                        },
                        onDragCancel = {
                            scope.launch {
                                offsetX.animateTo(0f, tween(150))
                                isSwipeDeleting = false
                            }
                        }
                    ) { change: PointerInputChange, dragAmount: Float ->
                        change.consume()
                        scope.launch {
                            offsetX.snapTo(
                                (offsetX.value + dragAmount).coerceIn(maxOffset, 0f)
                            )
                        }
                    }
                }
                // --- Geste B: VERTIKALES Ziehen → Reorder ---
                // Touch: erst nach Long-Press aktiv (sonst scrollt die Liste
                // statt zu verschieben). Maus: sofort (vertikal-dominant,
                // Desktop scrollt per Mausrad).
                .pointerInput(itemId, isTouch, reorderEnabled) {
                    val startReorder: () -> Unit = {
                        isSwipeDeleting = false
                        isReordering = false
                        session = null
                        if (reorderEnabled) {
                            val idx = currentRepos.indexOf(itemId)
                            if (idx >= 0) {
                                nodeRootStartY = nodeRoot.y
                                session = ReorderSession(itemId, reorderKind, idx, idx, 0f)
                                isReordering = true
                                onReorderBegin()
                            }
                        }
                    }
                    val endReorder: () -> Unit = {
                        scope.launch {
                            if (isReordering) {
                                // Finger-Position in Root-Koordinaten:
                                // nodeRootStartY (Row-Oberkante beim Start)
                                // + dragOffsetY (Finger-Verschiebung seit Start)
                                // + currentHeight/2 (Row-Mitte — der Finger greift
                                // die Row typischerweise in der Mitte).
                                val fingerY =
                                    nodeRootStartY + dragOffsetY + currentHeight.toFloat() / 2f
                                val hitId = currentBounds.entries.firstOrNull { (_, rect) ->
                                    fingerY in rect.top..rect.bottom
                                }?.key
                                if (hitId != null && hitId != itemId) {
                                    currentDropFolder(itemId, hitId)
                                    // WICHTIG: Bei Folder-Drop KEIN onReorderEnd —
                                    // moveNote() macht den DB-Write, commitReorder
                                    // wuerde die alte Reihenfolge persistieren.
                                    session = null
                                } else {
                                    session = null
                                    onReorderEnd()
                                }
                            }
                            dragOffsetY = 0f
                            isReordering = false
                        }
                    }
                    val cancelReorder: () -> Unit = {
                        scope.launch {
                            if (isReordering) {
                                session = null
                                onReorderEnd()
                            }
                            dragOffsetY = 0f
                            isReordering = false
                        }
                    }
                    // dragOffsetY = reine Finger-Verschiebung seit Drag-Start.
                    // KEINE Korrektur bei Swaps — die visuelle Korrektur
                    // passiert zur Render-Zeit (siehe offset-Modifier oben).
                    // Das verhindert Rubberbanding: nodeRoot (onGlobally-
                    // Positioned) und dragOffsetY sind um einen Frame
                    // versetzt auf Wasm.
                    val dragReorder: (Offset) -> Unit = { dragAmount ->
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
                            session = ReorderSession(
                                itemId, reorderKind, step.newIndex, s.startIndex, step.newAccumPx
                            )
                        }
                    }

                    if (isTouch) {
                        detectDragGesturesAfterLongPress(
                            onDragStart = { startReorder() },
                            onDragEnd = { endReorder() },
                            onDragCancel = { cancelReorder() },
                            onDrag = { change, dragAmount ->
                                change.consume()
                                dragReorder(dragAmount)
                            }
                        )
                    } else {
                        detectVerticalDragGestures(
                            onDragStart = { startReorder() },
                            onDragEnd = { endReorder() },
                            onDragCancel = { cancelReorder() },
                            onVerticalDrag = { change, dragAmount ->
                                change.consume()
                                dragReorder(Offset(0f, dragAmount))
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