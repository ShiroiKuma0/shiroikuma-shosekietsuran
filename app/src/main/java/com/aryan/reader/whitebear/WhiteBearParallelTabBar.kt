package com.aryan.reader.whitebear

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex

/**
 * 白い熊 UI: parallel-reading tab bar shown under the reader's top bar while the chrome is
 * visible. Tap a tab to switch books; long-press then drag horizontally to reorder; each
 * tab carries a three-dot menu (remove from set, file info). A trailing "＋" tab arms
 * pick-from-library mode to add another book (max 3).
 */
@Composable
fun WhiteBearParallelTabBar(
    tabs: List<Pair<String, String>>,
    currentBookId: String?,
    onSwitch: (String) -> Unit,
    onReorder: (List<String>) -> Unit,
    onRemove: (String) -> Unit,
    onInfo: (String) -> Unit,
    onAddBook: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val wbFrame = remember { WhiteBearUiState.get(context) }
    val borderDp = wbFrame.borderWidth.coerceAtLeast(1f).dp

    var draggingIndex by remember { mutableIntStateOf(-1) }
    var dragOffsetX by remember { mutableFloatStateOf(0f) }
    var tabWidthPx by remember { mutableFloatStateOf(1f) }

    val haptics = LocalHapticFeedback.current
    Row(
        modifier = modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .padding(horizontal = 8.dp, vertical = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        tabs.forEachIndexed { index, (bookId, title) ->
            val selected = bookId == currentBookId
            val isDragging = draggingIndex == index
            var menuOpen by remember(bookId) { mutableStateOf(false) }
            Surface(
                shape = MaterialTheme.shapes.small,
                color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surface,
                contentColor = if (selected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface,
                border = androidx.compose.foundation.BorderStroke(borderDp, MaterialTheme.colorScheme.outline),
                modifier = Modifier
                    .weight(1f)
                    .zIndex(if (isDragging) 1f else 0f)
                    .graphicsLayer { translationX = if (isDragging) dragOffsetX else 0f }
                    .pointerInput(bookId, tabs.size) {
                        detectDragGesturesAfterLongPress(
                            onDragStart = {
                                haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                                draggingIndex = index
                                dragOffsetX = 0f
                                tabWidthPx = size.width.toFloat().coerceAtLeast(1f)
                            },
                            onDragEnd = {
                                val shift = (dragOffsetX / tabWidthPx).let {
                                    if (it >= 0) (it + 0.5f).toInt() else (it - 0.5f).toInt()
                                }
                                if (shift != 0) {
                                    val target = (index + shift).coerceIn(0, tabs.lastIndex)
                                    if (target != index) {
                                        val ids = tabs.map { it.first }.toMutableList()
                                        val moved = ids.removeAt(index)
                                        ids.add(target, moved)
                                        onReorder(ids)
                                    }
                                }
                                draggingIndex = -1
                                dragOffsetX = 0f
                            },
                            onDragCancel = {
                                draggingIndex = -1
                                dragOffsetX = 0f
                            },
                            onDrag = { change, dragAmount ->
                                change.consume()
                                dragOffsetX += dragAmount.x
                            }
                        )
                    }
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.padding(start = 10.dp, top = 2.dp, bottom = 2.dp, end = 2.dp)
                ) {
                    Text(
                        title,
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier
                            .weight(1f)
                            .clickable { if (!selected) onSwitch(bookId) }
                    )
                    Box {
                        Box(
                            modifier = Modifier
                                .size(28.dp)
                                .clip(CircleShape)
                                .clickable { menuOpen = true },
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                Icons.Default.MoreVert,
                                contentDescription = "Tab actions",
                                tint = if (selected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(16.dp)
                            )
                        }
                        DropdownMenu(
                            expanded = menuOpen,
                            onDismissRequest = { menuOpen = false },
                            modifier = Modifier.border(
                                borderDp,
                                MaterialTheme.colorScheme.outline,
                                MaterialTheme.shapes.extraSmall
                            )
                        ) {
                            DropdownMenuItem(
                                text = { Text("File info") },
                                leadingIcon = { Icon(Icons.Default.Info, contentDescription = null) },
                                onClick = { menuOpen = false; onInfo(bookId) }
                            )
                            HorizontalDivider()
                            DropdownMenuItem(
                                text = { Text("Remove from parallel read", color = MaterialTheme.colorScheme.error) },
                                leadingIcon = {
                                    Icon(Icons.Default.Delete, contentDescription = null, tint = MaterialTheme.colorScheme.error)
                                },
                                onClick = { menuOpen = false; onRemove(bookId) }
                            )
                        }
                    }
                }
            }
        }
        if (tabs.size < 3) {
            Surface(
                shape = MaterialTheme.shapes.small,
                color = MaterialTheme.colorScheme.surface,
                contentColor = MaterialTheme.colorScheme.primary,
                border = androidx.compose.foundation.BorderStroke(borderDp, MaterialTheme.colorScheme.outline),
                modifier = Modifier
                    .weight(if (tabs.size <= 1) 1f else 0.25f)
                    .clickable(onClick = onAddBook)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp)
                ) {
                    Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                    Text(
                        if (tabs.size <= 1) "  Add book for parallel reading" else "",
                        style = MaterialTheme.typography.labelLarge,
                        maxLines = 1
                    )
                }
            }
        }
    }
}
