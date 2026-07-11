@file:OptIn(
    androidx.compose.foundation.layout.ExperimentalLayoutApi::class,
    androidx.compose.material3.ExperimentalMaterial3Api::class
)

package com.aryan.reader.whitebear

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Tag
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.InputChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.aryan.reader.CustomTopAppBar
import com.aryan.reader.shared.HighlightStyle
import kotlinx.coroutines.launch
import java.io.File

private enum class AnnotationGrouping(val label: String) {
    BOOK("Book"),
    TAG("Tag"),
    COLOR("Color")
}

private data class AnnotationGroup(
    val key: String,
    val title: String,
    val coverImagePath: String?,
    val entries: List<AnnotationLibraryEntry>
)

/**
 * Central annotation library: every text highlight (with notes) from every book,
 * searchable, tagged, grouped in collapsible sections, and each entry opens its
 * book at the annotated spot.
 */
@Composable
fun AnnotationLibraryScreen(
    onBackClick: () -> Unit,
    onOpenAnnotation: (AnnotationLibraryEntry) -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var entries by remember { mutableStateOf<List<AnnotationLibraryEntry>?>(null) }
    var reloadKey by remember { mutableIntStateOf(0) }
    LaunchedEffect(reloadKey) {
        entries = AnnotationLibrary.loadEntries(context)
    }

    var query by remember { mutableStateOf("") }
    var grouping by remember { mutableStateOf(AnnotationGrouping.BOOK) }
    var collapsedKeys by remember { mutableStateOf(setOf<String>()) }
    var entryForTagEdit by remember { mutableStateOf<AnnotationLibraryEntry?>(null) }

    val groups = remember(entries, query, grouping) {
        buildGroups(entries.orEmpty(), query, grouping)
    }

    Scaffold(
        topBar = {
            CustomTopAppBar(
                title = { Text("Annotations") },
                navigationIcon = {
                    IconButton(onClick = onBackClick) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            OutlinedTextField(
                value = query,
                onValueChange = { query = it },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                singleLine = true,
                placeholder = { Text("Search text, notes, tags, books…") },
                leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                trailingIcon = {
                    if (query.isNotEmpty()) {
                        IconButton(onClick = { query = "" }) {
                            Icon(Icons.Default.Close, contentDescription = "Clear search")
                        }
                    }
                }
            )

            Row(
                modifier = Modifier.padding(horizontal = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("Group by", style = MaterialTheme.typography.labelMedium)
                AnnotationGrouping.entries.forEach { mode ->
                    FilterChip(
                        selected = grouping == mode,
                        onClick = { grouping = mode },
                        label = { Text(mode.label) }
                    )
                }
            }

            when {
                entries == null -> {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator()
                    }
                }
                groups.isEmpty() -> {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Text(
                            if (query.isBlank()) "No annotations yet.\nHighlight text in any book and it will appear here."
                            else "Nothing matches the search.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                else -> {
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                        contentPadding = androidx.compose.foundation.layout.PaddingValues(
                            start = 16.dp, end = 16.dp, top = 8.dp, bottom = 24.dp
                        )
                    ) {
                        groups.forEach { group ->
                            val isCollapsed = group.key in collapsedKeys
                            item(key = "header_${grouping.name}_${group.key}") {
                                AnnotationGroupHeader(
                                    group = group,
                                    showCover = grouping == AnnotationGrouping.BOOK,
                                    isCollapsed = isCollapsed,
                                    onToggle = {
                                        collapsedKeys = if (isCollapsed) {
                                            collapsedKeys - group.key
                                        } else {
                                            collapsedKeys + group.key
                                        }
                                    }
                                )
                            }
                            if (!isCollapsed) {
                                items(
                                    count = group.entries.size,
                                    key = { index ->
                                        val e = group.entries[index]
                                        "entry_${grouping.name}_${group.key}_${e.bookId}_${e.annotationId}"
                                    }
                                ) { index ->
                                    val entry = group.entries[index]
                                    AnnotationEntryCard(
                                        entry = entry,
                                        showBookTitle = grouping != AnnotationGrouping.BOOK,
                                        onClick = { onOpenAnnotation(entry) },
                                        onEditTags = { entryForTagEdit = entry },
                                        onTagClick = { tag -> query = tag }
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    entryForTagEdit?.let { entry ->
        AnnotationTagDialog(
            entry = entry,
            onDismiss = { entryForTagEdit = null },
            onSave = { newTags ->
                scope.launch {
                    AnnotationTagStore.get(context).setTags(entry.bookId, entry.annotationId, newTags)
                    entryForTagEdit = null
                    reloadKey++
                }
            }
        )
    }
}

private fun buildGroups(
    entries: List<AnnotationLibraryEntry>,
    query: String,
    grouping: AnnotationGrouping
): List<AnnotationGroup> {
    val filtered = entries.filter { it.matches(query) }
    if (filtered.isEmpty()) return emptyList()
    val inBookOrder = compareBy<AnnotationLibraryEntry>({ it.bookTitle.lowercase() }, { it.sortInBook })
    return when (grouping) {
        AnnotationGrouping.BOOK -> filtered
            .groupBy { it.bookId }
            .map { (bookId, list) ->
                AnnotationGroup(
                    key = bookId,
                    title = list.first().bookTitle,
                    coverImagePath = list.first().coverImagePath,
                    entries = list.sortedBy { it.sortInBook }
                )
            }
            .sortedBy { it.title.lowercase() }
        AnnotationGrouping.TAG -> {
            val byTag = mutableMapOf<String, MutableList<AnnotationLibraryEntry>>()
            for (entry in filtered) {
                if (entry.tags.isEmpty()) {
                    byTag.getOrPut(UNTAGGED) { mutableListOf() }.add(entry)
                } else {
                    entry.tags.forEach { tag -> byTag.getOrPut(tag) { mutableListOf() }.add(entry) }
                }
            }
            byTag.map { (tag, list) ->
                AnnotationGroup(
                    key = "tag_$tag",
                    title = tag,
                    coverImagePath = null,
                    entries = list.sortedWith(inBookOrder)
                )
            }.sortedWith(
                compareBy({ it.title == UNTAGGED }, { it.title.lowercase() })
            )
        }
        AnnotationGrouping.COLOR -> filtered
            .groupBy { it.colorLabel }
            .map { (label, list) ->
                AnnotationGroup(
                    key = "color_$label",
                    title = label,
                    coverImagePath = null,
                    entries = list.sortedWith(inBookOrder)
                )
            }
            .sortedBy { it.title.lowercase() }
    }
}

private const val UNTAGGED = "Untagged"

@Composable
private fun AnnotationGroupHeader(
    group: AnnotationGroup,
    showCover: Boolean,
    isCollapsed: Boolean,
    onToggle: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .clickable(onClick = onToggle)
            .padding(vertical = 6.dp, horizontal = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            if (isCollapsed) Icons.Default.ExpandMore else Icons.Default.ExpandLess,
            contentDescription = if (isCollapsed) "Expand" else "Collapse"
        )
        Spacer(modifier = Modifier.width(8.dp))
        if (showCover && group.coverImagePath != null) {
            AsyncImage(
                model = File(group.coverImagePath),
                contentDescription = null,
                modifier = Modifier
                    .size(width = 26.dp, height = 38.dp)
                    .clip(RoundedCornerShape(3.dp))
            )
            Spacer(modifier = Modifier.width(10.dp))
        }
        if (group.entries.isNotEmpty()) {
            val sampleColor = group.entries.first().color
            if (group.key.startsWith("color_")) {
                Box(
                    modifier = Modifier
                        .size(14.dp)
                        .clip(CircleShape)
                        .background(sampleColor)
                )
                Spacer(modifier = Modifier.width(8.dp))
            }
        }
        Text(
            group.title,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.weight(1f),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        Text(
            "${group.entries.size}",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
    HorizontalDivider()
}

@Composable
private fun AnnotationEntryCard(
    entry: AnnotationLibraryEntry,
    showBookTitle: Boolean,
    onClick: () -> Unit,
    onEditTags: () -> Unit,
    onTagClick: (String) -> Unit
) {
    ElevatedCard(onClick = onClick, modifier = Modifier.fillMaxWidth()) {
        Row(modifier = Modifier.height(IntrinsicSize.Min)) {
            Box(
                modifier = Modifier
                    .width(5.dp)
                    .fillMaxHeight()
                    .background(entry.color)
            )
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 7.dp, end = 12.dp, top = 10.dp, bottom = 10.dp)
            ) {
                HighlightStyledText(entry)
                entry.note?.let { note ->
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        note,
                        style = MaterialTheme.typography.bodySmall,
                        fontStyle = FontStyle.Italic,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    if (showBookTitle) "${entry.bookTitle} · ${entry.locationLabel}" else entry.locationLabel,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalArrangement = Arrangement.spacedBy((-6).dp)
                ) {
                    entry.tags.forEach { tag ->
                        AssistChip(
                            onClick = { onTagClick(tag) },
                            label = { Text(tag, style = MaterialTheme.typography.labelSmall) }
                        )
                    }
                    AssistChip(
                        onClick = onEditTags,
                        label = {
                            Text(
                                if (entry.tags.isEmpty()) "Add tag" else "Edit",
                                style = MaterialTheme.typography.labelSmall
                            )
                        },
                        leadingIcon = {
                            Icon(
                                Icons.Default.Tag,
                                contentDescription = "Edit tags",
                                modifier = Modifier.size(14.dp)
                            )
                        }
                    )
                }
            }
        }
    }
}

@Composable
private fun HighlightStyledText(entry: AnnotationLibraryEntry) {
    val decoration = when (entry.style) {
        HighlightStyle.UNDERLINE, HighlightStyle.WAVY_UNDERLINE -> TextDecoration.Underline
        HighlightStyle.STRIKETHROUGH -> TextDecoration.LineThrough
        HighlightStyle.BACKGROUND -> TextDecoration.None
    }
    val background = if (entry.style == HighlightStyle.BACKGROUND) {
        entry.color.copy(alpha = 0.30f)
    } else {
        Color.Transparent
    }
    Text(
        entry.text,
        style = MaterialTheme.typography.bodyMedium,
        textDecoration = decoration,
        maxLines = 6,
        overflow = TextOverflow.Ellipsis,
        modifier = Modifier
            .clip(RoundedCornerShape(4.dp))
            .background(background)
            .padding(horizontal = if (background == Color.Transparent) 0.dp else 4.dp, vertical = 1.dp)
    )
}

@Composable
private fun AnnotationTagDialog(
    entry: AnnotationLibraryEntry,
    onDismiss: () -> Unit,
    onSave: (List<String>) -> Unit
) {
    val context = LocalContext.current
    var tags by remember(entry) { mutableStateOf(entry.tags) }
    var input by remember(entry) { mutableStateOf("") }
    var knownTags by remember { mutableStateOf(listOf<String>()) }
    LaunchedEffect(entry) {
        knownTags = AnnotationTagStore.get(context).allTags()
    }

    fun addFromInput() {
        val tag = input.trim()
        if (tag.isNotEmpty() && tag !in tags) tags = tags + tag
        input = ""
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Tags") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                if (tags.isNotEmpty()) {
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalArrangement = Arrangement.spacedBy((-4).dp)
                    ) {
                        tags.forEach { tag ->
                            InputChip(
                                selected = true,
                                onClick = { tags = tags - tag },
                                label = { Text(tag) },
                                trailingIcon = {
                                    Icon(
                                        Icons.Default.Close,
                                        contentDescription = "Remove tag",
                                        modifier = Modifier.size(16.dp)
                                    )
                                }
                            )
                        }
                    }
                }
                OutlinedTextField(
                    value = input,
                    onValueChange = { input = it },
                    singleLine = true,
                    label = { Text("Add tag") },
                    trailingIcon = {
                        if (input.isNotBlank()) {
                            TextButton(onClick = { addFromInput() }) { Text("Add") }
                        }
                    }
                )
                val suggestions = knownTags.filter { it !in tags }
                if (suggestions.isNotEmpty()) {
                    Text("Existing tags", style = MaterialTheme.typography.labelMedium)
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalArrangement = Arrangement.spacedBy((-4).dp)
                    ) {
                        suggestions.forEach { tag ->
                            AssistChip(
                                onClick = { tags = tags + tag },
                                label = { Text(tag) }
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                addFromInput()
                onSave(tags)
            }) { Text("Save") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}
