@file:OptIn(
    androidx.compose.material3.ExperimentalMaterial3Api::class,
    androidx.compose.foundation.ExperimentalFoundationApi::class
)

package com.earendil.todonotes.ui.chat

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.union
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.FormatQuote
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.earendil.todonotes.data.entity.ChatMessage
import com.earendil.todonotes.data.repo.dayOfYearAndYear
import com.earendil.todonotes.data.repo.formatDateGerman
import com.earendil.todonotes.data.repo.formatTimeGerman
import com.earendil.todonotes.ui.BackHandler
import com.earendil.todonotes.ui.ChatViewModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * Chat-Bildschirm (Block H3, M7d-2 — commonMain) — WhatsApp-Style.
 *
 * - LazyColumn: älteste oben, neueste unten
 * - Datum-Trennzeile bei Tageswechsel (dd.MM.yyyy)
 * - Auto-Scroll nach unten bei neuen Nachrichten
 * - Swipe-nach-rechts auf Nachricht → Zitat-Modus
 * - Quote-Box in Blase + Tap → scroll zur zitierten Nachricht + Blink
 * - Tap auf Nachricht → Edit-Dialog (H5)
 * - Eingabefeld + Senden-Button, imePadding für Tastatur
 */
@Composable
fun ChatScreen(
    noteId: String,
    initialTitle: String?,
    vm: ChatViewModel,
    onBack: () -> Unit
) {
    val state by vm.state.collectAsState()
    val messages by vm.messages.collectAsState()

    LaunchedEffect(noteId) {
        vm.load(noteId, initialTitle)
    }

    BackHandler(enabled = state.loaded) {
        onBack()
    }

    var inputText by remember { mutableStateOf("") }
    var editingMessage by remember { mutableStateOf<ChatMessage?>(null) }
    var quotingMessage by remember { mutableStateOf<ChatMessage?>(null) }

    val messageMap = remember(messages) { messages.associateBy { it.id } }
    val listItems = remember(messages) { buildChatListItems(messages) }

    val listState = rememberLazyListState()
    val scope = rememberCoroutineScope()

    var blinkId by remember { mutableStateOf<String?>(null) }

    // Auto-Scroll nach unten bei neuen Nachrichten.
    LaunchedEffect(messages.size) {
        if (messages.isNotEmpty()) {
            listState.animateScrollToItem(listItems.lastIndex)
        }
    }

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        state.title.ifBlank { "Chat" },
                        fontWeight = FontWeight.SemiBold
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Zurück")
                    }
                }
            )
        },
        bottomBar = {
            Column(
                modifier = Modifier.windowInsetsPadding(
                    WindowInsets.ime.union(WindowInsets.navigationBars)
                )
            ) {
                quotingMessage?.let { quote ->
                    QuotePreviewBar(
                        text = quote.text,
                        onCancel = { quotingMessage = null }
                    )
                }
                ChatInputBar(
                    text = inputText,
                    onTextChange = { inputText = it },
                    onSend = {
                        if (inputText.isNotBlank()) {
                            vm.sendMessage(inputText, quotingMessage?.id)
                            inputText = ""
                            quotingMessage = null
                        }
                    }
                )
            }
        }
    ) { padding ->
        if (!state.loaded) {
            Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
            return@Scaffold
        }

        LazyColumn(
            state = listState,
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(
                start = 12.dp,
                end = 12.dp,
                top = 8.dp,
                bottom = 8.dp
            ),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            items(listItems, key = { it.key }) { item ->
                when (item) {
                    is ChatListItem.DateSeparator -> DateDivider(item.label)
                    is ChatListItem.Bubble -> ChatBubble(
                        message = item.message,
                        quotedMessage = item.message.quotedMessageId?.let { messageMap[it] },
                        isBlinking = blinkId == item.message.id,
                        onTap = { editingMessage = item.message },
                        onSwipeRight = { quotingMessage = item.message },
                        onQuoteTap = { quotedId ->
                            val targetIndex = listItems.indexOfFirst {
                                it is ChatListItem.Bubble && it.message.id == quotedId
                            }
                            if (targetIndex >= 0) {
                                scope.launch {
                                    listState.animateScrollToItem(targetIndex)
                                    blinkId = quotedId
                                    delay(1500)
                                    blinkId = null
                                }
                            }
                        }
                    )
                }
            }
        }
    }

    editingMessage?.let { msg ->
        EditMessageDialog(
            message = msg,
            onDismiss = { editingMessage = null },
            onConfirm = { newText ->
                vm.editMessage(msg.id, newText)
                editingMessage = null
            },
            onDelete = {
                vm.deleteMessage(msg.id)
                editingMessage = null
            }
        )
    }
}

// ---- Listen-Items (Datum + Blasen) ----

private sealed class ChatListItem(val key: String) {
    data class DateSeparator(val label: String) : ChatListItem("date_$label")
    data class Bubble(val message: ChatMessage) : ChatListItem("msg_${message.id}")
}

/** Baut die flache Liste mit Datum-Trennzeilen zwischen verschiedenen Tagen. */
private fun buildChatListItems(messages: List<ChatMessage>): List<ChatListItem> {
    val items = mutableListOf<ChatListItem>()
    var lastDay: Int? = null
    var lastYear: Int? = null

    for (msg in messages) {
        val (day, year) = dayOfYearAndYear(msg.createdAt)
        if (day != lastDay || year != lastYear) {
            items.add(ChatListItem.DateSeparator(formatDateGerman(msg.createdAt)))
            lastDay = day
            lastYear = year
        }
        items.add(ChatListItem.Bubble(msg))
    }
    return items
}

// ---- Datum-Trennzeile ----

@Composable
private fun DateDivider(label: String) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(top = 8.dp, bottom = 4.dp),
        horizontalArrangement = Arrangement.Center
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.Medium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier
                .clip(RoundedCornerShape(8.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant)
                .padding(horizontal = 10.dp, vertical = 3.dp)
        )
    }
}

// ---- Nachrichten-Blase ----

@Composable
private fun ChatBubble(
    message: ChatMessage,
    quotedMessage: ChatMessage?,
    isBlinking: Boolean,
    onTap: () -> Unit,
    onSwipeRight: () -> Unit,
    onQuoteTap: (String) -> Unit
) {
    val density = LocalDensity.current
    val swipeThresholdPx = with(density) { 60.dp.toPx() }

    var rawSwipe by remember { mutableStateOf(0f) }
    var isDragging by remember { mutableStateOf(false) }
    val swipeOffset by animateFloatAsState(
        targetValue = if (isDragging) rawSwipe else 0f,
        animationSpec = if (isDragging) snap() else tween(200),
        label = "swipe"
    )

    val blinkColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.3f)
    var blinkPhase by remember { mutableStateOf(false) }
    LaunchedEffect(isBlinking) {
        if (isBlinking) {
            repeat(3) {
                blinkPhase = true
                delay(200)
                blinkPhase = false
                delay(200)
            }
        }
    }
    val blinkBg by animateColorAsState(
        targetValue = if (blinkPhase) blinkColor else Color.Transparent,
        animationSpec = tween(200),
        label = "blink"
    )

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .pointerInput(message.id) {
                detectHorizontalDragGestures(
                    onDragStart = { isDragging = true },
                    onDragEnd = {
                        if (rawSwipe > swipeThresholdPx) {
                            onSwipeRight()
                        }
                        isDragging = false
                        rawSwipe = 0f
                    }
                ) { _, dragAmount ->
                    rawSwipe = (rawSwipe + dragAmount).coerceAtLeast(0f)
                }
            },
        horizontalArrangement = Arrangement.End
    ) {
        Column(
            modifier = Modifier
                .widthIn(max = 280.dp)
                .graphicsLayer { translationX = swipeOffset }
                .clip(
                    RoundedCornerShape(
                        topStart = 16.dp,
                        topEnd = 16.dp,
                        bottomStart = 16.dp,
                        bottomEnd = 4.dp
                    )
                )
                .background(
                    if (isBlinking) blinkBg
                    else MaterialTheme.colorScheme.primaryContainer
                )
                .combinedClickable(onClick = onTap)
                .padding(horizontal = 12.dp, vertical = 8.dp)
        ) {
            if (quotedMessage != null) {
                QuoteBox(
                    text = quotedMessage.text,
                    onTap = { onQuoteTap(quotedMessage.id) }
                )
                Spacer(Modifier.height(4.dp))
            }
            Text(
                text = message.text,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onPrimaryContainer
            )
            Spacer(Modifier.height(2.dp))
            Text(
                text = formatTimeGerman(message.createdAt),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.6f),
                modifier = Modifier.align(Alignment.End)
            )
        }
    }
}

// ---- Quote-Box (in Blase, über dem Nachrichtentext) ----

@Composable
private fun QuoteBox(
    text: String,
    onTap: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(6.dp))
            .background(MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.12f))
            .combinedClickable(onClick = onTap)
            .padding(end = 8.dp, top = 4.dp, bottom = 4.dp)
    ) {
        Box(
            modifier = Modifier
                .width(3.dp)
                .height(36.dp)
                .background(MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.4f))
        )
        Text(
            text = text,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.7f),
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier
                .padding(start = 8.dp, end = 4.dp)
                .align(Alignment.CenterVertically)
        )
    }
}

// ---- Quote-Preview-Bar (über Eingabefeld) ----

@Composable
private fun QuotePreviewBar(
    text: String,
    onCancel: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            Icons.Default.FormatQuote,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(18.dp)
        )
        Spacer(Modifier.width(4.dp))
        Box(
            modifier = Modifier
                .width(3.dp)
                .height(28.dp)
                .background(MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f))
        )
        Text(
            text = text,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier
                .weight(1f)
                .padding(start = 8.dp, end = 4.dp)
        )
        IconButton(onClick = onCancel, modifier = Modifier.size(32.dp)) {
            Icon(
                Icons.Default.Close,
                contentDescription = "Zitat abbrechen",
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(18.dp)
            )
        }
    }
}

// ---- Eingabeleiste ----

@Composable
private fun ChatInputBar(
    text: String,
    onTextChange: (String) -> Unit,
    onSend: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        OutlinedTextField(
            value = text,
            onValueChange = onTextChange,
            modifier = Modifier.weight(1f),
            placeholder = { Text("Nachricht…") },
            maxLines = 5,
            keyboardOptions = KeyboardOptions(
                capitalization = KeyboardCapitalization.Sentences
            ),
            shape = RoundedCornerShape(24.dp)
        )
        Spacer(Modifier.padding(start = 4.dp))
        IconButton(
            onClick = onSend,
            enabled = text.isNotBlank()
        ) {
            Icon(
                Icons.AutoMirrored.Filled.Send,
                contentDescription = "Senden",
                tint = if (text.isNotBlank()) MaterialTheme.colorScheme.primary
                       else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f)
            )
        }
    }
}

// ---- Edit-Dialog (H5) ----

@Composable
private fun EditMessageDialog(
    message: ChatMessage,
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit,
    onDelete: () -> Unit
) {
    var editText by remember { mutableStateOf(message.text) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Nachricht bearbeiten") },
        text = {
            OutlinedTextField(
                value = editText,
                onValueChange = { editText = it },
                modifier = Modifier.fillMaxWidth(),
                minLines = 2,
                keyboardOptions = KeyboardOptions(
                    capitalization = KeyboardCapitalization.Sentences
                )
            )
        },
        confirmButton = {
            TextButton(
                onClick = { onConfirm(editText) },
                enabled = editText.isNotBlank() && editText != message.text
            ) { Text("Speichern") }
        },
        dismissButton = {
            Row {
                TextButton(onClick = onDelete) {
                    Text("Löschen", color = MaterialTheme.colorScheme.error)
                }
                TextButton(onClick = onDismiss) { Text("Abbrechen") }
            }
        }
    )
}
