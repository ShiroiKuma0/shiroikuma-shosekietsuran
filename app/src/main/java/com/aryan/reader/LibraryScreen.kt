/*
 * Episteme Reader - A native Android document reader.
 * Copyright (C) 2026 Episteme
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as
 * published by the Free Software Foundation, either version 3 of the
 * License, or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/>.
 *
 * mail: epistemereader@gmail.com
 */
// LibraryScreen.kt
@file:Suppress("KotlinConstantConditions")

package com.aryan.reader

import android.annotation.SuppressLint
import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.lazy.grid.items as gridItems
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.PagerDefaults
import androidx.compose.foundation.pager.PagerState
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.LibraryBooks
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.CollectionsBookmark
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.FilterList
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.FolderSpecial
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.AssistChip
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Slider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import com.aryan.reader.whitebear.WhiteBearLibraryState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.roundToInt
import androidx.core.net.toUri
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.media3.common.util.UnstableApi
import androidx.navigation.NavHostController
import coil.ImageLoader
import coil.compose.AsyncImage
import coil.decode.SvgDecoder
import com.aryan.reader.data.RecentFileItem
import com.aryan.reader.data.TagEntity
import com.aryan.reader.shared.AnnotationExportFormat
import com.aryan.reader.opds.OpdsAcquisition
import com.aryan.reader.opds.OpdsCatalog
import com.aryan.reader.opds.OpdsDownloadState
import com.aryan.reader.opds.OpdsEntry
import com.aryan.reader.opds.OpdsRepository
import com.aryan.reader.opds.OpdsViewModel
import com.aryan.reader.shared.LOCAL_FOLDER_SYNC_DATA_DIR
import com.aryan.reader.shared.opds.SharedOpdsLocalBookMatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.launch
import org.jsoup.Jsoup
import timber.log.Timber
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
private fun getBookCountString(count: Int): String {
    return pluralStringResource(id = R.plurals.book_count, count, count)
}

@UnstableApi
@SuppressLint("LocalContextGetResourceValueCall")
@Composable
fun LibraryScreen(
    viewModel: MainViewModel,
    navController: NavHostController,
) {
    val compStart = remember { System.currentTimeMillis() }
    LaunchedEffect(Unit) {
        ReaderPerfLog.d("LibraryScreen initial composition ${System.currentTimeMillis() - compStart}ms")
    }
    val context = LocalContext.current
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val selectedItems = uiState.contextualActionItems
    val isContextualModeActive = selectedItems.isNotEmpty()
    val selectedShelves = uiState.contextualActionShelfIds
    val isShelfContextualModeActive = selectedShelves.isNotEmpty()
    val sortOrder = uiState.sortOrder
    val shelves = uiState.shelves
    val rawLibraryFiles = uiState.rawLibraryFiles
    val tabTitles = remember {
        buildList {
            add(context.getString(R.string.tab_all_books))
            add(context.getString(R.string.tab_shelves))
            add(context.getString(R.string.tab_folders))
            if (!BuildConfig.IS_OFFLINE) {
                add(context.getString(R.string.tab_catalogs))
            }
        }
    }
    val pagerState = rememberPagerState(
        initialPage = uiState.libraryScreenStartPage,
        pageCount = { tabTitles.size }
    )

    val containsFolderItems = selectedItems.any { it.sourceFolderUri != null }

    val scope = rememberCoroutineScope()
    var showFilterSheet by remember { mutableStateOf(false) }

    val isSearchActive = uiState.isSearchActive
    val searchQuery = uiState.searchQuery

    val pickFolderLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocumentTree()
    ) { uri ->
        uri?.let {
            viewModel.addSyncedFolder(it)
        }
    }

    val onSelectSyncFolderClick = {
        try {
            pickFolderLauncher.launch(null)
        } catch (_: android.content.ActivityNotFoundException) {
            viewModel.showBanner(context.getString(R.string.error_folder_selection_unsupported), isError = true)
        }
    }

    val pickFileLauncher = rememberFilePickerLauncher { uris ->
        if (isContextualModeActive) {
            viewModel.clearContextualAction()
        }
        viewModel.onFilesSelected(uris)
    }

    val fallbackFilePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetMultipleContents()
    ) { uris ->
        if (isContextualModeActive) {
            viewModel.clearContextualAction()
        }
        viewModel.onFilesSelected(uris)
    }

    val onSelectFileClick = {
        if (isContextualModeActive) {
            viewModel.clearContextualAction()
        }
        val mimeTypes = if (uiState.useStrictFileFilter) MainViewModel.SUPPORTED_MIME_TYPES else arrayOf("*/*")
        try {
            pickFileLauncher.launch(mimeTypes)
        } catch (_: android.content.ActivityNotFoundException) {
            Timber.w("OpenDocument picker failed. Falling back to GetMultipleContents.")
            try {
                fallbackFilePickerLauncher.launch("*/*")
            } catch (_: android.content.ActivityNotFoundException) {
                viewModel.showBanner(context.getString(R.string.error_no_file_manager), isError = true)
            }
        }
    }

    LaunchedEffect(pagerState) {
        androidx.compose.runtime.snapshotFlow { pagerState.settledPage }
            .drop(1)
            .distinctUntilChanged()
            .collect { page ->
                viewModel.setLibraryScreenPage(page)
            }
    }

    var showDeleteConfirmDialog by remember { mutableStateOf(false) }
    var showDeleteShelvesDialog by remember { mutableStateOf(false) }
    var showParallelOrderDialog by remember { mutableStateOf(false) }
    var wbDeleteCandidate by remember { mutableStateOf<RecentFileItem?>(null) }
    var showInfoDialog by remember { mutableStateOf(false) }
    var itemForInfoDialog by remember { mutableStateOf<RecentFileItem?>(null) }
    var pendingSaveOriginalItem by remember { mutableStateOf<RecentFileItem?>(null) }
    var pendingAnnotationExportText by remember { mutableStateOf<String?>(null) }
    var showAnnotationExportFormatDialogFor by remember { mutableStateOf<RecentFileItem?>(null) }

    val saveOriginalLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("application/octet-stream")
    ) { uri ->
        val item = pendingSaveOriginalItem
        pendingSaveOriginalItem = null
        if (uri != null && item?.uriString != null) {
            viewModel.saveOriginalFile(item.uriString.toUri(), uri)
        }
    }

    fun saveOriginalItem(item: RecentFileItem) {
        if (!item.canExportOriginalFile()) return
        pendingSaveOriginalItem = item
        saveOriginalLauncher.launch(item.suggestedOriginalFileName())
    }

    val saveMarkdownAnnotationsLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument(AnnotationExportFormat.MARKDOWN.mimeType)
    ) { uri ->
        val exportText = pendingAnnotationExportText
        pendingAnnotationExportText = null
        if (uri != null && exportText != null) {
            viewModel.saveAnnotationExport(exportText, uri)
        }
    }

    val saveTextAnnotationsLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument(AnnotationExportFormat.TEXT.mimeType)
    ) { uri ->
        val exportText = pendingAnnotationExportText
        pendingAnnotationExportText = null
        if (uri != null && exportText != null) {
            viewModel.saveAnnotationExport(exportText, uri)
        }
    }

    fun exportAnnotationsItem(item: RecentFileItem, format: AnnotationExportFormat) {
        viewModel.prepareAnnotationExport(item, format) { prepared ->
            pendingAnnotationExportText = prepared.contents
            when (format) {
                AnnotationExportFormat.MARKDOWN -> saveMarkdownAnnotationsLauncher.launch(prepared.fileName)
                AnnotationExportFormat.TEXT -> saveTextAnnotationsLauncher.launch(prepared.fileName)
            }
        }
    }

    fun shareOriginalItem(item: RecentFileItem) {
        val uriString = item.uriString ?: return
        if (!item.canExportOriginalFile()) return
        scope.launch {
            viewModel.shareOriginalFile(
                activityContext = context,
                sourceUri = uriString.toUri(),
                fileType = item.type,
                filename = item.suggestedOriginalFileName()
            )
        }
    }

    BackHandler(enabled = isContextualModeActive) {
        viewModel.clearContextualAction()
    }

    BackHandler(enabled = isShelfContextualModeActive) {
        viewModel.clearShelfContextualAction()
    }

    BackHandler(enabled = isSearchActive) {
        viewModel.setSearchActive(false)
    }

    // 白い熊 UI: per-book cover-menu actions.
    val wbBookMenuActions = remember(context) {
        WhiteBearBookMenuActions(
            onInfo = { item ->
                itemForInfoDialog = item
                showInfoDialog = true
            },
            onTags = { item -> viewModel.openTagSelection(setOf(item.bookId)) },
            onShare = { item -> shareOriginalItem(item) },
            onSaveCopy = { item -> saveOriginalItem(item) },
            onAddParallel = { item ->
                val parallel = com.aryan.reader.whitebear.WhiteBearParallelState.get(context)
                val ids = (parallel.bookIds + item.bookId).distinct().take(3)
                parallel.updateSet(ids)
                viewModel.showBanner("Parallel set (${ids.size}/3): flip with a two-finger swipe.")
            },
            onStartParallel = { item ->
                val parallel = com.aryan.reader.whitebear.WhiteBearParallelState.get(context)
                parallel.startNewSet(item.bookId)
                viewModel.showBanner("New parallel set (1/3) — use “Add to parallel reading” on the next book.")
            },
            onDelete = { item -> wbDeleteCandidate = item }
        )
    }

    Box(modifier = Modifier.fillMaxSize()) {
        LibraryScreenContent(
            tabTitles = tabTitles,
            recentFiles = uiState.allRecentFiles,
            rawLibraryFiles = rawLibraryFiles,
            shelves = shelves,
            selectedItems = selectedItems,
            selectedShelves = selectedShelves,
            sortOrder = sortOrder,
            libraryFilters = uiState.libraryFilters,
            allTags = uiState.allTags,
            pinnedLibraryBookIds = uiState.pinnedLibraryBookIds,
            pagerState = pagerState,
            scope = scope,
            searchQuery = searchQuery,
            isSearchActive = isSearchActive,
            onSearchQueryChange = viewModel::onSearchQueryChange,
            onSearchActiveChange = viewModel::setSearchActive,
            onSortOrderChange = viewModel::setSortOrder,
            onFilterClick = { showFilterSheet = true },
            onClearFilters = { viewModel.updateLibraryFilters(LibraryFilters()) },
            onRemoveFilter = { viewModel.updateLibraryFilters(it) },
            onTagClick = { viewModel.openTagSelection(selectedItems.map { it.bookId }.toSet()) },
            onAddToShelfClick = { viewModel.openAddSelectedToShelf(selectedItems.map { it.bookId }.toSet()) },
            onPinClick = { viewModel.togglePinForContextualItems(isHome = false) },
            onClearSelection = { viewModel.clearContextualAction() },
            onItemClick = { item ->
                // 白い熊 UI: when "Add book for parallel reading" armed picking, the tapped
                // book joins the set and opens straight away.
                val parallel = com.aryan.reader.whitebear.WhiteBearParallelState.get(context)
                if (parallel.pickingArmed) {
                    parallel.disarmPicking()
                    parallel.addBook(item.bookId)
                    viewModel.showBanner("Parallel set (${parallel.bookIds.size}/3) — flip with a two-finger swipe.")
                }
                viewModel.onRecentFileClicked(item)
            },
            // Long-press selects (stock behavior); per-book actions live in the cover menu.
            onItemLongClick = viewModel::onRecentItemLongPress,
            onInfoClick = {
                if (selectedItems.size == 1) {
                    itemForInfoDialog = selectedItems.first()
                    showInfoDialog = true
                }
            },
            onSaveClick = selectedItems.singleOrNull()
                ?.takeIf { it.canExportOriginalFile() }
                ?.let { item -> { saveOriginalItem(item) } },
            onShareClick = selectedItems.singleOrNull()
                ?.takeIf { it.canExportOriginalFile() }
                ?.let { item -> { shareOriginalItem(item) } },
            onExportAnnotationsClick = selectedItems.singleOrNull()
                ?.let { item -> { showAnnotationExportFormatDialogFor = item } },
            onDeleteClick = { showDeleteConfirmDialog = true },
            onSelectAllClick = { viewModel.selectAllLibraryFiles() },
            onShelfClick = viewModel::onShelfClick,
            onShelfLongClick = viewModel::onShelfLongPress,
            onClearShelfSelection = viewModel::clearShelfContextualAction,
            onDeleteShelves = { showDeleteShelvesDialog = true },
            onNewShelfClick = viewModel::showCreateShelfDialog,
            onSelectFileClick = onSelectFileClick,
            onScanNowClick = viewModel::scanSyncedFolder,
            onSyncMetadataClick = viewModel::syncFolderMetadata,
            onRescanClick = { viewModel.rescanLibraryForNewBooks() },
            onScanFolderClick = viewModel::scanFolderForNewBooks,
            onSelectSyncFolderClick = onSelectSyncFolderClick,
            onEditFolderFiltersClick = { folder, filters -> viewModel.updateFolderFilters(folder, filters) },
            syncedFolders = uiState.syncedFolders,
            onRemoveFolderClick = { folder -> viewModel.removeSyncedFolder(folder) },
            onFolderLocalSyncChange = viewModel::setFolderLocalSyncEnabled,
            onDisconnectSyncFolderClick = viewModel::disconnectAllSyncedFolders,
            downloadingBookIds = uiState.downloadingBookIds,
            lastFolderScanTime = uiState.lastFolderScanTime,
            isLoading = uiState.isLoading,
            isRefreshing = uiState.isRefreshing,
            onOpdsBookDownloaded = { uri, title ->
                viewModel.showBanner(context.getString(R.string.banner_downloaded, title))
                viewModel.onFileSelected(uri, isFromRecent = false)
            },
            onStreamOpdsBook = { entry, catalog ->
                viewModel.streamOpdsBook(
                    bookId = entry.id,
                    title = entry.title,
                    urlTemplate = entry.pseUrlTemplate!!,
                    pageCount = entry.pseCount!!,
                    catalogId = catalog?.id
                )
            },
            onDeleteCatalogStreams = viewModel::deleteStreamedBooksForCatalog,
            onSettingsClick = { navController.navigateIfReady(AppDestinations.SETTINGS_SCREEN_ROUTE) },
            onSettingsLongClick = { navController.navigateIfReady(AppDestinations.WHITE_BEAR_UI_SCREEN_ROUTE) },
            onAnnotationLibraryClick = { navController.navigateIfReady(AppDestinations.ANNOTATION_LIBRARY_SCREEN_ROUTE) },
            onParallelReadClick = { showParallelOrderDialog = true },
            bookMenuActions = wbBookMenuActions,
            usePdfFileNameAsDisplayName = uiState.usePdfFileNameAsDisplayName
        )


        showAnnotationExportFormatDialogFor?.let { item ->
            AlertDialog(
                onDismissRequest = { showAnnotationExportFormatDialogFor = null },
                title = { Text(stringResource(R.string.dialog_export_annotations_title)) },
                text = {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        TextButton(onClick = {
                            showAnnotationExportFormatDialogFor = null
                            exportAnnotationsItem(item, AnnotationExportFormat.MARKDOWN)
                        }) {
                            Text(stringResource(R.string.export_annotations_markdown))
                        }
                        TextButton(onClick = {
                            showAnnotationExportFormatDialogFor = null
                            exportAnnotationsItem(item, AnnotationExportFormat.TEXT)
                        }) {
                            Text(stringResource(R.string.export_annotations_text))
                        }
                    }
                },
                confirmButton = {},
                dismissButton = {
                    TextButton(onClick = { showAnnotationExportFormatDialogFor = null }) {
                        Text(stringResource(R.string.action_cancel))
                    }
                }
            )
        }
        if (uiState.showCreateShelfDialog) {
            CreateShelfDialog(
                onConfirm = viewModel::createShelf,
                onDismiss = viewModel::dismissCreateShelfDialog
            )
        }

        wbDeleteCandidate?.let { candidate ->
            val wbFrame = remember { com.aryan.reader.whitebear.WhiteBearUiState.get(context) }
            AlertDialog(
                modifier = Modifier.border(
                    wbFrame.borderWidth.coerceAtLeast(1f).dp,
                    MaterialTheme.colorScheme.outline,
                    MaterialTheme.shapes.extraLarge
                ),
                onDismissRequest = { wbDeleteCandidate = null },
                icon = { Icon(Icons.Default.Delete, contentDescription = null) },
                title = { Text("Delete this book?") },
                text = {
                    Text(
                        "“${candidate.cardTitle(uiState.usePdfFileNameAsDisplayName)}” will be permanently deleted — " +
                            "the book file and all its reading data. This cannot be undone."
                    )
                },
                confirmButton = {
                    TextButton(
                        onClick = {
                            wbDeleteCandidate = null
                            viewModel.deleteBookPermanently(candidate.bookId)
                        }
                    ) {
                        Text(stringResource(R.string.action_delete), color = MaterialTheme.colorScheme.error)
                    }
                },
                dismissButton = {
                    Button(onClick = { wbDeleteCandidate = null }) {
                        Text(stringResource(R.string.action_cancel))
                    }
                }
            )
        }

        if (showParallelOrderDialog) {
            WhiteBearParallelOrderDialog(
                items = selectedItems.toList(),
                onStart = { orderedIds ->
                    com.aryan.reader.whitebear.WhiteBearParallelState.get(context).updateSet(orderedIds)
                    showParallelOrderDialog = false
                    val first = selectedItems.firstOrNull { it.bookId == orderedIds.firstOrNull() }
                    viewModel.clearContextualAction()
                    first?.let { viewModel.onRecentFileClicked(it) }
                },
                onClearSet = {
                    com.aryan.reader.whitebear.WhiteBearParallelState.get(context).clear()
                    showParallelOrderDialog = false
                    viewModel.showBanner("Parallel reading set cleared.")
                },
                onDismiss = { showParallelOrderDialog = false }
            )
        }

        if (showDeleteConfirmDialog) {
            DeleteConfirmationDialog(
                count = selectedItems.size,
                onConfirm = {
                    viewModel.deleteContextualItemsPermanently()
                    showDeleteConfirmDialog = false
                },
                onDismiss = { showDeleteConfirmDialog = false },
                isPermanentDelete = true,
                containsFolderItems = containsFolderItems
            )
        }

        if (showFilterSheet) {
            LibraryFilterSheet(
                filters = uiState.libraryFilters,
                allTags = uiState.allTags,
                syncedFolders = uiState.syncedFolders,
                onApply = { viewModel.updateLibraryFilters(it) },
                onDismiss = { showFilterSheet = false }
            )
        }

        if (showDeleteShelvesDialog) {
            DeleteShelvesConfirmationDialog(
                count = selectedShelves.size,
                onConfirm = {
                    viewModel.deleteSelectedShelves()
                    showDeleteShelvesDialog = false
                },
                onDismiss = { showDeleteShelvesDialog = false }
            )
        }

        HydratedFileInfoDialog(
            item = itemForInfoDialog,
            isVisible = showInfoDialog,
            uiState = uiState,
            viewModel = viewModel,
            onDismiss = {
                showInfoDialog = false
                itemForInfoDialog = null
            },
            onOpenTags = { bookId -> viewModel.openTagSelection(setOf(bookId)) },
            onShareFile = itemForInfoDialog
                ?.takeIf { it.canExportOriginalFile() }
                ?.let { item -> { shareOriginalItem(item) } },
            onSaveCopy = itemForInfoDialog
                ?.takeIf { it.canExportOriginalFile() }
                ?.let { item -> { saveOriginalItem(item) } },
            onSelectForActions = itemForInfoDialog?.let { item ->
                {
                    showInfoDialog = false
                    itemForInfoDialog = null
                    viewModel.onRecentItemLongPress(item)
                }
            },
            onDeleteBook = itemForInfoDialog?.let { item ->
                {
                    showInfoDialog = false
                    itemForInfoDialog = null
                    viewModel.deleteBookPermanently(item.bookId)
                }
            }
        )
        CustomTopBanner(bannerMessage = uiState.bannerMessage)
    }
}

@UnstableApi
@Composable
fun ShelfScreen(
    viewModel: MainViewModel,
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val selectedItems = uiState.contextualActionItems
    val viewingShelfId = uiState.viewingShelfId
    val isAddingBooks = uiState.isAddingBooksToShelf
    val shelves = uiState.shelves
    val sortOrder = uiState.sortOrder
    val showRenameDialogFor = uiState.showRenameShelfDialogFor
    val showDeleteDialogFor = uiState.showDeleteShelfDialogFor
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var showRemoveFromShelfDialog by remember { mutableStateOf(false) }
    var showInfoDialog by remember { mutableStateOf(false) }
    var itemForInfoDialog by remember { mutableStateOf<RecentFileItem?>(null) }
    var pendingSaveOriginalItem by remember { mutableStateOf<RecentFileItem?>(null) }

    val saveOriginalLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("application/octet-stream")
    ) { uri ->
        val item = pendingSaveOriginalItem
        pendingSaveOriginalItem = null
        if (uri != null && item?.uriString != null) {
            viewModel.saveOriginalFile(item.uriString.toUri(), uri)
        }
    }

    fun saveOriginalItem(item: RecentFileItem) {
        if (!item.canExportOriginalFile()) return
        pendingSaveOriginalItem = item
        saveOriginalLauncher.launch(item.suggestedOriginalFileName())
    }


    fun shareOriginalItem(item: RecentFileItem) {
        val uriString = item.uriString ?: return
        if (!item.canExportOriginalFile()) return
        scope.launch {
            viewModel.shareOriginalFile(
                activityContext = context,
                sourceUri = uriString.toUri(),
                fileType = item.type,
                filename = item.suggestedOriginalFileName()
            )
        }
    }

    BackHandler(enabled = true) {
        when {
            selectedItems.isNotEmpty() -> viewModel.clearContextualAction()
            isAddingBooks -> viewModel.dismissAddBooksToShelf()
            else -> viewModel.navigateBackFromShelf()
        }
    }

    val currentShelf = shelves.find { it.id == viewingShelfId }
    val childShelves = remember(shelves, currentShelf) {
        currentShelf?.childShelfIds?.mapNotNull { childId -> shelves.find { it.id == childId } } ?: emptyList()
    }

    Box(modifier = Modifier.fillMaxSize()) {
        if (viewingShelfId != null && currentShelf != null) {
            if (isAddingBooks) {
                AddBooksModeScreen(
                    shelfName = currentShelf.name,
                    availableBooks = uiState.booksAvailableForAdding,
                    selectedBookUris = uiState.booksSelectedForAdding,
                    currentSource = uiState.addBooksSource,
                    sortOrder = sortOrder,
                    onSortOrderChange = viewModel::setSortOrder,
                    onSourceChange = viewModel::setAddBooksSource,
                    onBookClick = { item -> viewModel.toggleBookSelectionForAdding(item.bookId) },
                    onBack = viewModel::dismissAddBooksToShelf,
                    onAddSelectedBooks = { viewModel.addBooksToShelf(viewingShelfId) },
                    downloadingBookIds = uiState.downloadingBookIds,
                    usePdfFileNameAsDisplayName = uiState.usePdfFileNameAsDisplayName
                )
            } else {
                ShelfDetailScreen(
                    shelf = currentShelf,
                    childShelves = childShelves,
                    selectedItems = selectedItems,
                    sortOrder = sortOrder,
                    onSortOrderChange = viewModel::setSortOrder,
                    onBack = viewModel::navigateBackFromShelf,
                    onAddBooksClick = viewModel::showAddBooksToShelf,
                    onChildShelfClick = viewModel::onShelfClick,
                    onBookClick = viewModel::onRecentFileClicked,
                    onBookLongClick = viewModel::onRecentItemLongPress,
                    onClearSelection = viewModel::clearContextualAction,
                    onTagClick = { viewModel.openTagSelection(selectedItems.map { it.bookId }.toSet()) },
                    onInfoClick = {
                        if (selectedItems.size == 1) {
                            itemForInfoDialog = selectedItems.first()
                            showInfoDialog = true
                        }
                    },
                    onSaveClick = selectedItems.singleOrNull()
                        ?.takeIf { it.canExportOriginalFile() }
                        ?.let { item -> { saveOriginalItem(item) } },
                    onShareClick = selectedItems.singleOrNull()
                        ?.takeIf { it.canExportOriginalFile() }
                        ?.let { item -> { shareOriginalItem(item) } },
                    onDeleteClick = { showRemoveFromShelfDialog = true },
                    onRenameShelf = { viewModel.showRenameShelfDialog(currentShelf.id) },
                    onDeleteShelf = { viewModel.showDeleteShelfDialog(currentShelf.id) },
                    downloadingBookIds = uiState.downloadingBookIds,
                    usePdfFileNameAsDisplayName = uiState.usePdfFileNameAsDisplayName
                )
            }
        }

        if (showRenameDialogFor != null) {
            val shelfToRename = shelves.find { it.id == showRenameDialogFor }
            if (shelfToRename != null) {
                RenameShelfDialog(
                    initialName = shelfToRename.name,
                    onConfirm = { newName -> viewModel.renameShelf(showRenameDialogFor, newName) },
                    onDismiss = viewModel::dismissRenameShelfDialog
                )
            }
        }

        if (showDeleteDialogFor != null) {
            DeleteShelfConfirmationDialog(
                shelfName = shelves.find { it.id == showDeleteDialogFor }?.name ?: "",
                onConfirm = { viewModel.deleteShelf(showDeleteDialogFor) },
                onDismiss = viewModel::dismissDeleteShelfDialog
            )
        }

        if (showRemoveFromShelfDialog) {
            RemoveFromShelfConfirmationDialog(
                count = selectedItems.size,
                shelfName = currentShelf?.name ?: "",
                onConfirm = {
                    viewModel.removeContextualItemsFromShelf()
                    showRemoveFromShelfDialog = false
                },
                onDismiss = { showRemoveFromShelfDialog = false }
            )
        }

        HydratedFileInfoDialog(
            item = itemForInfoDialog,
            isVisible = showInfoDialog,
            uiState = uiState,
            viewModel = viewModel,
            onDismiss = { showInfoDialog = false; itemForInfoDialog = null },
            onOpenTags = { bookId -> viewModel.openTagSelection(setOf(bookId)) }
        )
        CustomTopBanner(bannerMessage = uiState.bannerMessage)
    }
}

@Suppress("unused")
@OptIn(ExperimentalFoundationApi::class, ExperimentalMaterial3Api::class)
@Composable
fun LibraryScreenContent(
    tabTitles: List<String>,
    recentFiles: List<RecentFileItem>,
    rawLibraryFiles: List<RecentFileItem>,
    shelves: List<Shelf>,
    selectedItems: Set<RecentFileItem>,
    selectedShelves: Set<String>,
    sortOrder: SortOrder,
    libraryFilters: LibraryFilters,
    allTags: List<TagEntity>,
    pinnedLibraryBookIds: Set<String>,
    pagerState: PagerState,
    scope: CoroutineScope,
    searchQuery: String,
    isSearchActive: Boolean,
    onSearchQueryChange: (String) -> Unit,
    onSearchActiveChange: (Boolean) -> Unit,
    onSortOrderChange: (SortOrder) -> Unit,
    onFilterClick: () -> Unit,
    onClearFilters: () -> Unit,
    onRemoveFilter: (LibraryFilters) -> Unit,
    onTagClick: () -> Unit,
    onAddToShelfClick: () -> Unit,
    onPinClick: () -> Unit,
    onClearSelection: () -> Unit,
    onItemClick: (RecentFileItem) -> Unit,
    onItemLongClick: (RecentFileItem) -> Unit,
    onInfoClick: () -> Unit,
    onSaveClick: (() -> Unit)?,
    onShareClick: (() -> Unit)?,
    onExportAnnotationsClick: (() -> Unit)?,
    onDeleteClick: () -> Unit,
    onSelectAllClick: () -> Unit,
    onShelfClick: (Shelf) -> Unit,
    onShelfLongClick: (Shelf) -> Unit,
    onClearShelfSelection: () -> Unit,
    onDeleteShelves: () -> Unit,
    onNewShelfClick: () -> Unit,
    onSelectFileClick: () -> Unit,
    onScanNowClick: () -> Unit,
    onSyncMetadataClick: () -> Unit,
    // 白い熊: the fast "find new books" rescan, on the library's own top bar and pull-to-refresh.
    onRescanClick: () -> Unit = {},
    onScanFolderClick: (SyncedFolder) -> Unit = {},
    onSelectSyncFolderClick: () -> Unit,
    onEditFolderFiltersClick: (SyncedFolder, Set<FileType>) -> Unit,
    onDisconnectSyncFolderClick: () -> Unit,
    downloadingBookIds: Set<String>,
    lastFolderScanTime: Long?,
    isLoading: Boolean,
    isRefreshing: Boolean,
    syncedFolders: List<SyncedFolder>,
    onRemoveFolderClick: (SyncedFolder) -> Unit,
    onFolderLocalSyncChange: (SyncedFolder, Boolean, Boolean) -> Unit,
    onOpdsBookDownloaded: (Uri, String) -> Unit,
    onStreamOpdsBook: (OpdsEntry, OpdsCatalog?) -> Unit,
    onDeleteCatalogStreams: (String) -> Unit,
    onSettingsClick: () -> Unit,
    onSettingsLongClick: () -> Unit = {},
    onAnnotationLibraryClick: () -> Unit = {},
    onParallelReadClick: (() -> Unit)? = null,
    bookMenuActions: WhiteBearBookMenuActions? = null,
    usePdfFileNameAsDisplayName: Boolean,
) {
    val isBookContextualModeActive = selectedItems.isNotEmpty()
    val isShelfContextualModeActive = selectedShelves.isNotEmpty()
    var showSortMenu by remember { mutableStateOf(false) }

    // 白い熊 UI: list/grid layout, its metrics menu, and the author/tag quick filters.
    val wbContext = LocalContext.current
    val wbLibraryState = remember { WhiteBearLibraryState.get(wbContext) }
    var showWbLayoutMenu by remember { mutableStateOf(false) }
    var wbAuthorFilter by rememberSaveable { mutableStateOf<String?>(null) }
    var wbTagFilterId by rememberSaveable { mutableStateOf<String?>(null) }
    val wbAuthors = remember(rawLibraryFiles) {
        rawLibraryFiles.mapNotNull { it.filterAuthor() }
            .distinct()
            .sortedBy { it.lowercase() }
    }
    // 白い熊 UI: tapping an author name under a cover filters the grid to that author,
    // and tapping the same name again clears the filter. Suppressed while books are
    // being selected, so the tap falls through to the cell and toggles the selection.
    val onWbAuthorTap: ((String) -> Unit)? = if (isBookContextualModeActive) {
        null
    } else {
        { author -> wbAuthorFilter = if (wbAuthorFilter == author) null else author }
    }
    val searchFocusRequester = remember { FocusRequester() }
    val selectedBookIds = remember(selectedItems) { selectedItems.mapTo(mutableSetOf()) { it.bookId } }

    // Keep cursor/composition state local while Gboard is editing. Replacing the
    // field value from the filtered library state can move the cursor behind a
    // newly entered character or interrupt a held Backspace gesture.
    var textFieldValue by remember(isSearchActive) {
        mutableStateOf(TextFieldValue(searchQuery, TextRange(searchQuery.length)))
    }

    // 白い熊 UI: the field is the single source of truth while search is open — the query
    // is pushed to the view model DEBOUNCED, and never written back into the field. The
    // old per-keystroke round trip re-projected the whole library on every letter and its
    // stale echo overwrote the field, breaking the IME composition (autocorrect committed
    // after each character, mangling the input on large libraries).
    LaunchedEffect(isSearchActive) {
        if (!isSearchActive) return@LaunchedEffect
        androidx.compose.runtime.snapshotFlow { textFieldValue.text }
            .drop(1)
            .distinctUntilChanged()
            .collectLatest { query ->
                delay(220)
                onSearchQueryChange(query)
            }
    }

    LaunchedEffect(isSearchActive) {
        if (isSearchActive) {
            searchFocusRequester.requestFocus()
        }
    }

    Scaffold(
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        topBar = {
            Column {
                if (isBookContextualModeActive) {
                    ContextualTopAppBar(
                        selectedItemCount = selectedItems.size,
                        onNavIconClick = onClearSelection,
                        onTagClick = onTagClick,
                        onAddToShelfClick = onAddToShelfClick,
                        onPinClick = onPinClick,
                        onInfoClick = onInfoClick,
                        onSaveClick = onSaveClick,
                        onShareClick = onShareClick,
                        onExportAnnotationsClick = onExportAnnotationsClick,
                        onDeleteClick = onDeleteClick,
                        onSelectAllClick = onSelectAllClick,
                        compactSelectionActions = true,
                        onClearSelectionClick = onClearSelection,
                        onParallelReadClick = if (selectedItems.size in 2..3) onParallelReadClick else null
                    )
                } else if (isShelfContextualModeActive && pagerState.currentPage == 1) {
                    ContextualTopAppBar(
                        selectedItemCount = selectedShelves.size,
                        onNavIconClick = onClearShelfSelection,
                        onDeleteClick = onDeleteShelves
                    )
                } else if (isSearchActive) {
                    Surface(
                        shadowElevation = 4.dp,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .statusBarsPadding()
                                .height(64.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            IconButton(onClick = { onSearchActiveChange(false) }) {
                                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.content_desc_close_search))
                            }
                            OutlinedTextField(
                                value = textFieldValue,
                                onValueChange = { textFieldValue = it },
                                placeholder = { Text(stringResource(R.string.search_placeholder)) },
                                modifier = Modifier
                                    .weight(1f)
                                    .padding(vertical = 4.dp)
                                    .focusRequester(searchFocusRequester)
                                    .testTag("LibrarySearchTextField"),
                                singleLine = true,
                                colors = TextFieldDefaults.colors(
                                    focusedContainerColor = Color.Transparent,
                                    unfocusedContainerColor = Color.Transparent,
                                    disabledContainerColor = Color.Transparent,
                                    focusedIndicatorColor = Color.Transparent,
                                    unfocusedIndicatorColor = Color.Transparent,
                                ),
                                trailingIcon = {
                                    if (textFieldValue.text.isNotEmpty()) {
                                        IconButton(onClick = {
                                            textFieldValue = TextFieldValue("")
                                            onSearchQueryChange("")
                                        }) {
                                            Icon(Icons.Default.Close, contentDescription = stringResource(R.string.content_desc_clear_query))
                                        }
                                    }
                                }
                            )
                        }
                    }
                } else {
                    CustomTopAppBar(title = { Text(stringResource(R.string.library_title)) },
                        actions = {
                            if (pagerState.currentPage == 0) {
                                Box {
                                    IconButton(onClick = { showWbLayoutMenu = true }) {
                                        Icon(painterResource(id = R.drawable.wb_grid), contentDescription = "Layout")
                                    }
                                    DropdownMenu(
                                        expanded = showWbLayoutMenu,
                                        onDismissRequest = { showWbLayoutMenu = false }
                                    ) {
                                        DropdownMenuItem(
                                            text = { Text("List layout") },
                                            leadingIcon = { Icon(Icons.AutoMirrored.Filled.List, contentDescription = null) },
                                            trailingIcon = if (!wbLibraryState.gridLayout) {
                                                { Icon(Icons.Default.Check, contentDescription = null) }
                                            } else null,
                                            onClick = {
                                                wbLibraryState.updateGridLayout(false)
                                                showWbLayoutMenu = false
                                            }
                                        )
                                        DropdownMenuItem(
                                            text = { Text("Grid layout") },
                                            leadingIcon = { Icon(painterResource(id = R.drawable.wb_grid), contentDescription = null) },
                                            trailingIcon = if (wbLibraryState.gridLayout) {
                                                { Icon(Icons.Default.Check, contentDescription = null) }
                                            } else null,
                                            onClick = {
                                                wbLibraryState.updateGridLayout(true)
                                                showWbLayoutMenu = false
                                            }
                                        )
                                        HorizontalDivider()
                                        WhiteBearLayoutMenuSlider(
                                            label = "Thumbnail height",
                                            value = wbLibraryState.thumbnailHeight,
                                            valueText = "${wbLibraryState.thumbnailHeight.toInt()} dp",
                                            range = 100f..320f,
                                            steps = 21,
                                            onChange = { wbLibraryState.updateThumbnailHeight(it) }
                                        )
                                        WhiteBearLayoutMenuSlider(
                                            label = "Title size",
                                            value = wbLibraryState.titleFontSize,
                                            valueText = "${wbLibraryState.titleFontSize.toInt()} sp",
                                            range = 9f..24f,
                                            steps = 14,
                                            onChange = { wbLibraryState.updateTitleFontSize(it) }
                                        )
                                        WhiteBearLayoutMenuSlider(
                                            label = "Author size",
                                            value = wbLibraryState.authorFontSize,
                                            valueText = "${wbLibraryState.authorFontSize.toInt()} sp",
                                            range = 8f..20f,
                                            steps = 11,
                                            onChange = { wbLibraryState.updateAuthorFontSize(it) }
                                        )
                                    }
                                }
                                IconButton(onClick = onFilterClick) {
                                    Icon(Icons.Default.FilterList, contentDescription = stringResource(R.string.content_desc_filter))
                                }
                                Box {
                                    TextButton(
                                        onClick = { showSortMenu = true },
                                        modifier = Modifier.testTag("LibrarySortButton")
                                    ) {
                                        Icon(
                                            painter = painterResource(id = R.drawable.sort),
                                            contentDescription = stringResource(R.string.content_desc_sort),
                                            modifier = Modifier.size(20.dp)
                                        )
                                        Spacer(modifier = Modifier.width(8.dp))
                                        Text(stringResource(sortOrder.labelRes))
                                    }
                                    DropdownMenu(
                                        expanded = showSortMenu,
                                        onDismissRequest = { showSortMenu = false }
                                    ) {
                                        SortOrder.entries.forEach { order ->
                                            DropdownMenuItem(
                                                text = { Text(stringResource(order.labelRes)) },
                                                onClick = {
                                                    onSortOrderChange(order)
                                                    showSortMenu = false
                                                },
                                                trailingIcon = {
                                                    if (order == sortOrder) {
                                                        Icon(
                                                            Icons.Default.Check,
                                                            contentDescription = stringResource(R.string.content_desc_selected)
                                                        )
                                                    }
                                                }
                                            )
                                        }
                                    }
                                }
                                IconButton(onClick = { onSearchActiveChange(true) }) {
                                    Icon(Icons.Default.Search, contentDescription = stringResource(R.string.action_search))
                                }
                                // 白い熊: force a rescan without digging into the Folders tab.
                                IconButton(
                                    onClick = onRescanClick,
                                    enabled = !isRefreshing,
                                    modifier = Modifier.testTag("LibraryRescanButton")
                                ) {
                                    if (isRefreshing) {
                                        CircularProgressIndicator(
                                            modifier = Modifier.size(20.dp),
                                            strokeWidth = 2.dp
                                        )
                                    } else {
                                        Icon(
                                            Icons.Default.Refresh,
                                            contentDescription = stringResource(R.string.action_rescan_library)
                                        )
                                    }
                                }
                            }
                            // Fork: central annotation library across all books.
                            IconButton(onClick = onAnnotationLibraryClick) {
                                Icon(Icons.Default.CollectionsBookmark, contentDescription = "Annotations")
                            }
                            // Long-press opens the 白い熊 書籍閲覧 UI page directly.
                            Box(
                                modifier = Modifier
                                    .size(48.dp)
                                    .clip(CircleShape)
                                    .combinedClickable(
                                        onClick = onSettingsClick,
                                        onLongClick = onSettingsLongClick
                                    ),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(Icons.Default.Settings, contentDescription = stringResource(R.string.settings))
                            }
                        }
                    )
                    TabRow(selectedTabIndex = pagerState.currentPage) {
                        tabTitles.forEachIndexed { index, title ->
                            Tab(
                                selected = pagerState.currentPage == index,
                                onClick = {
                                    ReaderPerfLog.d("LibraryPager click page=$index title=$title")
                                    if (pagerState.currentPage != index) {
                                        scope.launch {
                                            val start = ReaderPerfLog.nowNanos()
                                            pagerState.animateScrollToPage(index)
                                            ReaderPerfLog.d(
                                                "LibraryPager settled page=$index elapsed=${ReaderPerfLog.elapsedMs(start)}ms"
                                            )
                                        }
                                    }
                                },
                                text = { Text(title) }
                            )
                        }
                    }
                    if (pagerState.currentPage == 0) {
                        WhiteBearLibraryFilterRow(
                            authors = wbAuthors,
                            allTags = allTags,
                            selectedAuthor = wbAuthorFilter,
                            selectedTagId = wbTagFilterId,
                            onAuthorSelected = { wbAuthorFilter = it },
                            onTagSelected = { wbTagFilterId = it },
                            onClearFilters = {
                                wbAuthorFilter = null
                                wbTagFilterId = null
                            }
                        )
                    }
                    androidx.compose.animation.AnimatedVisibility(
                        visible = libraryFilters.isActive && pagerState.currentPage == 0
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                                .padding(horizontal = 16.dp, vertical = 8.dp)
                                .horizontalScroll(rememberScrollState()),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            if (libraryFilters.fileTypes.isNotEmpty()) {
                                AssistChip(
                                    onClick = { onRemoveFilter(libraryFilters.copy(fileTypes = emptySet())) },
                                    label = { Text(stringResource(R.string.filter_types, libraryFilters.fileTypes.joinToString { it.name })) },
                                    trailingIcon = { Icon(Icons.Default.Close, contentDescription = stringResource(R.string.action_clear), modifier = Modifier.size(16.dp)) }
                                )
                            }
                            if (libraryFilters.sourceFolders.isNotEmpty()) {
                                AssistChip(
                                    onClick = { onRemoveFilter(libraryFilters.copy(sourceFolders = emptySet())) },
                                    label = { Text(stringResource(R.string.filter_folders, libraryFilters.sourceFolders.size)) },
                                    trailingIcon = { Icon(Icons.Default.Close, contentDescription = stringResource(R.string.action_clear), modifier = Modifier.size(16.dp)) }
                                )
                            }
                            if (libraryFilters.readStatus != ReadStatusFilter.ALL) {
                                AssistChip(
                                    onClick = { onRemoveFilter(libraryFilters.copy(readStatus = ReadStatusFilter.ALL)) },
                                    label = { Text(stringResource(R.string.filter_status, stringResource(libraryFilters.readStatus.labelRes))) },
                                    trailingIcon = { Icon(Icons.Default.Close, contentDescription = stringResource(R.string.action_clear), modifier = Modifier.size(16.dp)) }
                                )
                            }
                            if (libraryFilters.tagIds.isNotEmpty()) {
                                val selectedTags = allTags.filter { it.id in libraryFilters.tagIds }
                                val tagLabel = when {
                                    selectedTags.isEmpty() -> pluralStringResource(R.plurals.tag_count, libraryFilters.tagIds.size, libraryFilters.tagIds.size)
                                    selectedTags.size <= 2 -> selectedTags.joinToString { it.name }
                                    else -> pluralStringResource(R.plurals.tag_count, selectedTags.size, selectedTags.size)
                                }
                                AssistChip(
                                    onClick = { onRemoveFilter(libraryFilters.copy(tagIds = emptySet())) },
                                    label = { Text(stringResource(R.string.filter_tags, tagLabel)) },
                                    trailingIcon = { Icon(Icons.Default.Close, contentDescription = stringResource(R.string.action_clear), modifier = Modifier.size(16.dp)) }
                                )
                            }
                        }
                    }
                }
            }
        },
        floatingActionButton = {
            if (!isBookContextualModeActive && !isShelfContextualModeActive) {
                when (pagerState.currentPage) {
                    0 -> {
                        if (recentFiles.isNotEmpty()) {
                            ExtendedFloatingActionButton(
                                text = { Text(stringResource(R.string.fab_add_file)) },
                                icon = { Icon(Icons.Default.Add, contentDescription = stringResource(R.string.fab_add_file)) },
                                onClick = onSelectFileClick,
                                modifier = Modifier.padding(16.dp)
                            )
                        }
                    }
                    1 -> {
                        ExtendedFloatingActionButton(
                            text = { Text(stringResource(R.string.fab_new_shelf)) },
                            icon = { Icon(Icons.Default.Add, contentDescription = stringResource(R.string.fab_new_shelf)) },
                            onClick = onNewShelfClick,
                            modifier = Modifier
                                .padding(16.dp)
                                .testTag("LibraryNewShelfFab")
                        )
                    }
                }
            }
        }
    ) { paddingValues ->
        HorizontalPager(
            state = pagerState,
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues),
            flingBehavior = PagerDefaults.flingBehavior(
                state = pagerState,
                snapPositionalThreshold = 0.25f
            ),
            beyondViewportPageCount = 0,
            key = { it }
        ) { page ->
            when (page) {
                0 -> {
                    val wbVisibleFiles = remember(recentFiles, wbAuthorFilter, wbTagFilterId) {
                        recentFiles.filter { item ->
                            (wbAuthorFilter == null || item.filterAuthor() == wbAuthorFilter) &&
                                (wbTagFilterId == null || item.tags.any { it.id == wbTagFilterId })
                        }
                    }
                    // 白い熊: pulling down here forces the "find new books" rescan. The stock
                    // gesture on Home only re-read metadata sidecars, which can never surface
                    // a file that was added to the folder after the last scan.
                    PullToRefreshBox(
                        isRefreshing = isRefreshing,
                        onRefresh = onRescanClick,
                        modifier = Modifier.fillMaxSize()
                    ) {
                        if (recentFiles.isEmpty() && searchQuery.isNotEmpty()) {
                            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                Text(stringResource(R.string.no_results_found, searchQuery))
                            }
                        } else if (recentFiles.isEmpty()) {
                            EmptyState(
                                title = stringResource(R.string.your_library_empty),
                                message = stringResource(R.string.library_empty_desc),
                                onSelectFileClick = onSelectFileClick,
                                modifier = Modifier.fillMaxSize()
                            )
                        } else if (wbVisibleFiles.isEmpty()) {
                            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                Text("No books match the author/tag filter.")
                            }
                        } else if (wbLibraryState.gridLayout) {
                            val wbGridState = rememberLazyGridState()
                            // A filter change re-shortens the grid, so jump back to the top —
                            // dropping the first emission keeps the restored scroll position
                            // when the library is merely re-entered.
                            LaunchedEffect(wbGridState) {
                                androidx.compose.runtime.snapshotFlow { wbAuthorFilter to wbTagFilterId }
                                    .drop(1)
                                    .collect { wbGridState.scrollToItem(0) }
                            }
                            Box(modifier = Modifier.fillMaxSize()) {
                                LazyVerticalGrid(
                                    columns = GridCells.Adaptive(
                                        minSize = (wbLibraryState.thumbnailHeight * 0.7f).dp.coerceAtLeast(72.dp)
                                    ),
                                    state = wbGridState,
                                    modifier = Modifier.fillMaxSize(),
                                    contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 88.dp),
                                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                                    verticalArrangement = Arrangement.spacedBy(16.dp)
                                ) {
                                    gridItems(wbVisibleFiles, key = { it.bookId }) { item ->
                                        WhiteBearLibraryGridItem(
                                            item = item,
                                            isSelected = item.bookId in selectedBookIds,
                                            thumbnailHeightDp = wbLibraryState.thumbnailHeight,
                                            titleFontSp = wbLibraryState.titleFontSize,
                                            authorFontSp = wbLibraryState.authorFontSize,
                                            onItemClick = { onItemClick(item) },
                                            onItemLongClick = { onItemLongClick(item) },
                                            usePdfFileNameAsDisplayName = usePdfFileNameAsDisplayName,
                                            menuActions = bookMenuActions,
                                            onAuthorClick = onWbAuthorTap,
                                            isAuthorFilterActive = wbAuthorFilter != null
                                        )
                                    }
                                }
                                WhiteBearFastScrollbar(
                                    gridState = wbGridState,
                                    modifier = Modifier.align(Alignment.TopEnd)
                                )
                            }
                        } else {
                            val wbListState = rememberLazyListState()
                            // Same as the grid: a filter change re-shortens the list, so jump back
                            // to the top without disturbing a restored scroll position.
                            LaunchedEffect(wbListState) {
                                androidx.compose.runtime.snapshotFlow { wbAuthorFilter to wbTagFilterId }
                                    .drop(1)
                                    .collect { wbListState.scrollToItem(0) }
                            }
                            LazyColumn(
                                state = wbListState,
                                modifier = Modifier.fillMaxSize(),
                                contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 88.dp),
                                verticalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                items(wbVisibleFiles, key = { it.bookId }) { item ->
                                    LibraryListItem(
                                        item = item,
                                        isSelected = item.bookId in selectedBookIds,
                                        isPinned = item.bookId in pinnedLibraryBookIds,
                                        onItemClick = { onItemClick(item) },
                                        onItemLongClick = { onItemLongClick(item) },
                                        isDownloading = item.bookId in downloadingBookIds,
                                        usePdfFileNameAsDisplayName = usePdfFileNameAsDisplayName,
                                        menuActions = bookMenuActions,
                                        onAuthorClick = onWbAuthorTap,
                                        isAuthorFilterActive = wbAuthorFilter != null
                                    )
                                }
                            }
                        }
                    }
                }
                1 -> {
                    ShelvesScreen(
                        shelves = shelves,
                        onShelfClick = onShelfClick,
                        onShelfLongClick = onShelfLongClick,
                        selectedShelves = selectedShelves
                    )
                }
                2 -> {
                    FolderSyncScreen(
                        syncedFolders = syncedFolders,
                        allRecentFiles = rawLibraryFiles,
                        onAddFolderClick = onSelectSyncFolderClick,
                        onRemoveFolderClick = onRemoveFolderClick,
                        onFolderLocalSyncChange = onFolderLocalSyncChange,
                        onEditFolderFiltersClick = onEditFolderFiltersClick,
                        onScanNowClick = onScanNowClick,
                        onSyncMetadataClick = onSyncMetadataClick,
                        onScanFolderClick = onScanFolderClick,
                        isLoading = isLoading || isRefreshing
                    )
                }
                3 -> {
                    if (!BuildConfig.IS_OFFLINE) {
                        OpdsTab(
                            localLibraryFiles = rawLibraryFiles,
                            onBookDownloaded = onOpdsBookDownloaded,
                            onReadBook = onItemClick,
                            onStreamBook = onStreamOpdsBook,
                            onDeleteCatalogStreams = onDeleteCatalogStreams
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ShelvesScreen(
    shelves: List<Shelf>,
    onShelfClick: (Shelf) -> Unit,
    onShelfLongClick: (Shelf) -> Unit,
    selectedShelves: Set<String>,
) {
    val tagShelves = remember(shelves) { shelves.filter { it.type == ShelfType.TAG && it.bookCount > 0 } }
    val visibleShelves = remember(shelves) {
        shelves.filter { shelf ->
            when {
                shelf.type == ShelfType.TAG -> false
                shelf.type == ShelfType.FOLDER -> shelf.parentShelfId == null
                else -> true
            }
        }
    }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize(),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 88.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        if (tagShelves.isNotEmpty() && selectedShelves.isEmpty()) {
            item {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(
                        text = stringResource(R.string.section_browse_by_tag),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold
                    )
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        tagShelves.forEach { shelf ->
                            FilterChip(
                                selected = false,
                                onClick = { onShelfClick(shelf) },
                                label = { Text(shelf.name) },
                                leadingIcon = {
                                    Icon(
                                        painter = painterResource(id = R.drawable.tag),
                                        contentDescription = null,
                                        modifier = Modifier.size(16.dp)
                                    )
                                }
                            )
                        }
                    }
                }
            }
        }

        items(visibleShelves, key = { it.id }) { shelf ->
            ShelfListItem(
                shelf = shelf,
                isSelected = shelf.id in selectedShelves,
                onItemClick = { onShelfClick(shelf) },
                onItemLongClick = { onShelfLongClick(shelf) }
            )
        }
    }
}

@Composable
private fun CreateShelfDialog(onConfirm: (String) -> Unit, onDismiss: () -> Unit) {
    var text by remember { mutableStateOf("") }
    val focusRequester = remember { FocusRequester() }
    val outlineColor = MaterialTheme.colorScheme.outline
    val shape = RoundedCornerShape(4.dp)

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.create_new_shelf)) },
        text = {
            BasicTextField(
                value = text,
                onValueChange = { text = it },
                singleLine = true,
                textStyle = MaterialTheme.typography.bodyLarge.copy(
                    color = MaterialTheme.colorScheme.onSurface
                ),
                cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp)
                    .focusRequester(focusRequester)
                    .border(1.dp, outlineColor, shape)
                    .padding(horizontal = 16.dp),
                decorationBox = { innerTextField ->
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.CenterStart
                    ) {
                        if (text.isEmpty()) {
                            Text(
                                text = stringResource(R.string.shelf_name_hint),
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                style = MaterialTheme.typography.bodyLarge,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                        innerTextField()
                    }
                }
            )
        },
        confirmButton = {
            TextButton(
                onClick = { onConfirm(text) },
                enabled = text.isNotBlank()
            ) {
                Text(stringResource(R.string.action_create))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.action_cancel))
            }
        }
    )

    LaunchedEffect(Unit) {
        delay(100)
        focusRequester.requestFocus()
    }
}

@Composable
private fun ShelfDetailScreen(
    shelf: Shelf,
    childShelves: List<Shelf>,
    selectedItems: Set<RecentFileItem>,
    sortOrder: SortOrder,
    onSortOrderChange: (SortOrder) -> Unit,
    onBack: () -> Unit,
    onAddBooksClick: () -> Unit,
    onChildShelfClick: (Shelf) -> Unit,
    onBookClick: (RecentFileItem) -> Unit,
    onBookLongClick: (RecentFileItem) -> Unit,
    onClearSelection: () -> Unit,
    onTagClick: () -> Unit,
    onInfoClick: () -> Unit,
    onSaveClick: (() -> Unit)?,
    onShareClick: (() -> Unit)?,
    onDeleteClick: () -> Unit,
    onRenameShelf: () -> Unit,
    onDeleteShelf: () -> Unit,
    downloadingBookIds: Set<String>,
    usePdfFileNameAsDisplayName: Boolean,
) {
    val isContextualModeActive = selectedItems.isNotEmpty()
    val isFolderShelf = shelf.type == ShelfType.FOLDER
    var showSortMenu by remember { mutableStateOf(false) }
    var showMoreMenu by remember { mutableStateOf(false) }
    var isSearchActive by remember(shelf.id) { mutableStateOf(false) }
    var searchQuery by remember(shelf.id) { mutableStateOf("") }
    val searchFocusRequester = remember { FocusRequester() }
    var searchFieldValue by remember(isSearchActive, shelf.id) {
        mutableStateOf(TextFieldValue(searchQuery, TextRange(searchQuery.length)))
    }
    val normalizedQuery = searchQuery.trim()
    val filteredChildShelves = remember(childShelves, normalizedQuery) {
        if (normalizedQuery.isBlank()) {
            childShelves
        } else {
            childShelves.filter { childShelf ->
                childShelf.name.contains(normalizedQuery, ignoreCase = true) ||
                    childShelf.books.any { item ->
                        item.displayName.contains(normalizedQuery, ignoreCase = true) ||
                            item.title?.contains(normalizedQuery, ignoreCase = true) == true ||
                            item.author?.contains(normalizedQuery, ignoreCase = true) == true
                    }
            }
        }
    }
    val filteredDirectBooks = remember(shelf.directBooks, normalizedQuery) {
        if (normalizedQuery.isBlank()) {
            shelf.directBooks
        } else {
            shelf.directBooks.filter { item ->
                item.displayName.contains(normalizedQuery, ignoreCase = true) ||
                    item.title?.contains(normalizedQuery, ignoreCase = true) == true ||
                    item.author?.contains(normalizedQuery, ignoreCase = true) == true ||
                    item.tags.any { tag -> tag.name.contains(normalizedQuery, ignoreCase = true) }
            }
        }
    }

    LaunchedEffect(searchQuery) {
        if (searchFieldValue.text != searchQuery) {
            searchFieldValue = searchFieldValue.copy(
                text = searchQuery,
                selection = TextRange(searchQuery.length)
            )
        }
    }

    LaunchedEffect(isSearchActive) {
        if (isSearchActive) {
            searchFocusRequester.requestFocus()
        }
    }

    fun clearShelfSearchQuery() {
        searchQuery = ""
        searchFieldValue = TextFieldValue("", TextRange.Zero)
    }

    fun closeShelfSearch() {
        isSearchActive = false
        clearShelfSearchQuery()
    }

    BackHandler(enabled = isSearchActive) {
        closeShelfSearch()
    }

    Scaffold(
        modifier = Modifier,
        topBar = {
            if (isContextualModeActive) {
                ContextualTopAppBar(
                    selectedItemCount = selectedItems.size,
                    onNavIconClick = onClearSelection,
                    onTagClick = onTagClick,
                    onInfoClick = onInfoClick,
                    onSaveClick = onSaveClick,
                    onShareClick = onShareClick,
                    onDeleteClick = onDeleteClick
                )
            } else if (isSearchActive) {
                Surface(
                    shadowElevation = 4.dp,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .statusBarsPadding()
                            .height(64.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        IconButton(onClick = { closeShelfSearch() }) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.content_desc_close_search))
                        }
                        OutlinedTextField(
                            value = searchFieldValue,
                            onValueChange = {
                                searchFieldValue = it
                                searchQuery = it.text
                            },
                            placeholder = { Text(stringResource(R.string.search_placeholder)) },
                            modifier = Modifier
                                .weight(1f)
                                .padding(vertical = 4.dp)
                                .focusRequester(searchFocusRequester)
                                .testTag("ShelfSearchTextField"),
                            singleLine = true,
                            colors = TextFieldDefaults.colors(
                                focusedContainerColor = Color.Transparent,
                                unfocusedContainerColor = Color.Transparent,
                                disabledContainerColor = Color.Transparent,
                                focusedIndicatorColor = Color.Transparent,
                                unfocusedIndicatorColor = Color.Transparent,
                            ),
                            trailingIcon = {
                                if (searchQuery.isNotEmpty()) {
                                    IconButton(onClick = { clearShelfSearchQuery() }) {
                                        Icon(Icons.Default.Close, contentDescription = stringResource(R.string.content_desc_clear_query))
                                    }
                                }
                            }
                        )
                    }
                }
            } else {
                CustomTopAppBar(
                    title = {
                        Column {
                            Text(
                                text = shelf.name,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Text(
                                text = when {
                                    isFolderShelf && shelf.childShelfCount > 0 && shelf.directBookCount > 0 ->
                                        stringResource(
                                            R.string.folder_subtitle_folder_book_counts,
                                            pluralStringResource(R.plurals.folder_count, shelf.childShelfCount, shelf.childShelfCount),
                                            getBookCountString(shelf.directBookCount)
                                        )
                                    isFolderShelf && shelf.childShelfCount > 0 ->
                                        pluralStringResource(R.plurals.folder_count, shelf.childShelfCount, shelf.childShelfCount)
                                    isFolderShelf -> getBookCountString(shelf.directBookCount)
                                    else -> getBookCountString(shelf.bookCount)
                                },
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    },
                    navigationIcon = {
                        IconButton(onClick = onBack) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.action_back))
                        }
                    },
                    actions = {
                        Box {
                            TextButton(
                                onClick = { showSortMenu = true },
                                modifier = Modifier.testTag("ShelfSortButton")
                            ) {
                                Icon(
                                    painter = painterResource(id = R.drawable.sort),
                                    contentDescription = stringResource(R.string.content_desc_sort),
                                    modifier = Modifier.size(20.dp)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(stringResource(sortOrder.labelRes))
                            }
                            DropdownMenu(
                                expanded = showSortMenu,
                                onDismissRequest = { showSortMenu = false }
                            ) {
                                SortOrder.entries.forEach { order ->
                                    DropdownMenuItem(
                                        text = { Text(stringResource(order.labelRes)) },
                                        onClick = {
                                            onSortOrderChange(order)
                                            showSortMenu = false
                                        },
                                        trailingIcon = {
                                            if (order == sortOrder) {
                                                Icon(
                                                    Icons.Default.Check,
                                                    contentDescription = stringResource(R.string.content_desc_selected)
                                                )
                                            }
                                        }
                                    )
                                }
                            }
                        }

                        IconButton(onClick = { isSearchActive = true }) {
                            Icon(
                                imageVector = Icons.Default.Search,
                                contentDescription = stringResource(R.string.content_desc_search_shelf)
                            )
                        }

                        if (shelf.type == ShelfType.MANUAL && shelf.id != "unshelved") {
                            Box {
                                IconButton(onClick = { showMoreMenu = true }) {
                                    Icon(
                                        imageVector = Icons.Default.MoreVert,
                                        contentDescription = stringResource(R.string.content_desc_more_options)
                                    )
                                }
                                DropdownMenu(
                                    expanded = showMoreMenu,
                                    onDismissRequest = { showMoreMenu = false }
                                ) {
                                    DropdownMenuItem(
                                        text = { Text(stringResource(R.string.menu_rename_shelf)) },
                                        onClick = {
                                            onRenameShelf()
                                            showMoreMenu = false
                                        }
                                    )
                                    DropdownMenuItem(
                                        text = { Text(stringResource(R.string.menu_delete_shelf)) },
                                        onClick = {
                                            onDeleteShelf()
                                            showMoreMenu = false
                                        }
                                    )
                                }
                            }
                        }
                    }
                )
            }
        },
        floatingActionButton = {
            if (shelf.type == ShelfType.MANUAL && shelf.id != "unshelved" && !isContextualModeActive) {
                ExtendedFloatingActionButton(
                    onClick = onAddBooksClick,
                    icon = { Icon(Icons.Default.Add, contentDescription = null) },
                    text = { Text(stringResource(R.string.fab_add_books)) }
                )
            }
        },
        content = { paddingValues ->
            if (filteredChildShelves.isEmpty() && filteredDirectBooks.isEmpty()) {
                Box(
                    modifier = Modifier.fillMaxSize().padding(paddingValues),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = if (normalizedQuery.isBlank()) stringResource(R.string.shelf_empty) else stringResource(
                            R.string.no_results_found,
                            normalizedQuery
                        ),
                        style = MaterialTheme.typography.bodyLarge
                    )
                }
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize().padding(paddingValues),
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    if (filteredChildShelves.isNotEmpty()) {
                        if (isFolderShelf) {
                            item {
                                Text(
                                    text = stringResource(R.string.section_folders),
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.SemiBold,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                        items(filteredChildShelves, key = { it.id }) { childShelf ->
                            ShelfListItem(
                                shelf = childShelf,
                                isSelected = false,
                                onItemClick = { onChildShelfClick(childShelf) },
                                onItemLongClick = {},
                                showHierarchyIndent = false
                            )
                        }
                    }
                    if (filteredDirectBooks.isNotEmpty() && isFolderShelf && filteredChildShelves.isNotEmpty()) {
                        item {
                            Spacer(modifier = Modifier.height(4.dp))
                        }
                        item {
                            Text(
                                text = stringResource(R.string.section_files),
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                    items(filteredDirectBooks, key = { it.bookId }) { item ->
                        LibraryListItem(
                            item = item,
                            isSelected = selectedItems.any { it.bookId == item.bookId },
                            onItemClick = { onBookClick(item) },
                            onItemLongClick = { onBookLongClick(item) },
                            isDownloading = item.bookId in downloadingBookIds,
                            usePdfFileNameAsDisplayName = usePdfFileNameAsDisplayName
                        )
                    }
                }
            }
        }
    )
}

@Composable
private fun AddBooksModeScreen(
    shelfName: String,
    availableBooks: List<RecentFileItem>,
    selectedBookUris: Set<String>,
    currentSource: AddBooksSource,
    sortOrder: SortOrder,
    onSortOrderChange: (SortOrder) -> Unit,
    onSourceChange: (AddBooksSource) -> Unit,
    onBookClick: (RecentFileItem) -> Unit,
    onBack: () -> Unit,
    onAddSelectedBooks: () -> Unit,
    downloadingBookIds: Set<String>,
    usePdfFileNameAsDisplayName: Boolean,
) {
    var showSortMenu by remember { mutableStateOf(false) }

    Scaffold(
        modifier = Modifier,
        topBar = {
            Column {
                CustomTopAppBar(
                    title = { Text(stringResource(R.string.add_to_shelf, shelfName)) },
                    navigationIcon = {
                        IconButton(onClick = onBack) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.action_back))
                        }
                    },
                    actions = {
                        Box {
                            TextButton(
                                onClick = { showSortMenu = true },
                                modifier = Modifier.testTag("AddBooksSortButton")
                            ) {
                                Icon(
                                    painter = painterResource(id = R.drawable.sort),
                                    contentDescription = stringResource(R.string.content_desc_sort),
                                    modifier = Modifier.size(20.dp)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(stringResource(sortOrder.labelRes))
                            }
                            DropdownMenu(
                                expanded = showSortMenu,
                                onDismissRequest = { showSortMenu = false }
                            ) {
                                SortOrder.entries.forEach { order ->
                                    DropdownMenuItem(
                                        text = { Text(stringResource(order.labelRes)) },
                                        onClick = {
                                            onSortOrderChange(order)
                                            showSortMenu = false
                                        },
                                        trailingIcon = {
                                            if (order == sortOrder) {
                                                Icon(Icons.Default.Check, contentDescription = stringResource(R.string.content_desc_selected))
                                            }
                                        }
                                    )
                                }
                            }
                        }
                    }
                )
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 12.dp)
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    AddBooksSource.entries.forEach { source ->
                        FilterChip(
                            selected = source == currentSource,
                            onClick = { onSourceChange(source) },
                            label = { Text(stringResource(source.labelRes)) }
                        )
                    }
                }
            }
        },
        floatingActionButton = {
            if (selectedBookUris.isNotEmpty()) {
                ExtendedFloatingActionButton(
                    text = { Text(stringResource(R.string.fab_add_count, selectedBookUris.size)) },
                    icon = { Icon(Icons.Default.Check, contentDescription = stringResource(R.string.fab_add_books)) },
                    onClick = onAddSelectedBooks
                )
            }
        },
        content = { paddingValues ->
            if (availableBooks.isEmpty()) {
                Box(
                    modifier = Modifier.fillMaxSize().padding(paddingValues),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = if (currentSource == AddBooksSource.UNSHELVED) {
                            stringResource(R.string.no_unshelved_books)
                        } else {
                            stringResource(R.string.all_books_in_shelf)
                        },
                        style = MaterialTheme.typography.bodyLarge
                    )
                }
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize().padding(paddingValues),
                    contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 88.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    items(availableBooks, key = { it.bookId }) { item ->
                        val isSelected = item.bookId in selectedBookUris
                        LibraryListItem(
                            item = item,
                            isSelected = isSelected,
                            onItemClick = { onBookClick(item) },
                            onItemLongClick = { onBookClick(item) },
                            isDownloading = item.bookId in downloadingBookIds,
                            usePdfFileNameAsDisplayName = usePdfFileNameAsDisplayName
                        )
                    }
                }
            }
        }
    )
}

@Composable
private fun ShelfCover(shelf: Shelf) {
    val booksForCovers = shelf.books.take(4).reversed()
    val coverWidth = 52.dp
    val coverHeight = 75.dp
    val horizontalOffset = 12.dp
    val maxWidth = coverWidth + (horizontalOffset * (4 - 1))

    Box(
        modifier = Modifier
            .width(maxWidth)
            .height(coverHeight),
        contentAlignment = Alignment.CenterStart
    ) {
        if (booksForCovers.size <= 1) {
            val topBook = shelf.topBook
            if (topBook != null) {
                ThemedBookCover(
                    item = topBook,
                    contentDescription = stringResource(R.string.content_desc_shelf_cover, shelf.name),
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .size(width = coverWidth, height = coverHeight)
                        .clip(MaterialTheme.shapes.small)
                )
            } else {
                EmptyShelfCover(
                    shelfName = shelf.name,
                    modifier = Modifier
                        .size(width = coverWidth, height = coverHeight)
                        .clip(MaterialTheme.shapes.small)
                )
            }
        } else {
            Box(
                modifier = Modifier
                    .width(coverWidth + (horizontalOffset * (booksForCovers.size - 1)))
                    .height(coverHeight)
            ) {
                booksForCovers.forEachIndexed { index, book ->
                    Surface(
                        shape = MaterialTheme.shapes.small,
                        shadowElevation = 4.dp,
                        modifier = Modifier
                            .size(width = coverWidth, height = coverHeight)
                            .align(Alignment.CenterEnd)
                            .offset(x = -horizontalOffset * index)
                    ) {
                        ThemedBookCover(
                            item = book,
                            contentDescription = null,
                            contentScale = ContentScale.Crop
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun EmptyShelfCover(
    shelfName: String,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .background(
                androidx.compose.ui.graphics.Brush.linearGradient(
                    colors = listOf(
                        MaterialTheme.colorScheme.secondaryContainer,
                        MaterialTheme.colorScheme.surfaceContainerHighest
                    )
                )
            )
            .border(0.5.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f)),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = shelfName.takeIf { it.isNotBlank() } ?: stringResource(R.string.tab_shelves),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = androidx.compose.ui.text.style.TextAlign.Center,
            maxLines = 3,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(8.dp)
        )
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun ShelfListItem(
    shelf: Shelf,
    isSelected: Boolean,
    onItemClick: () -> Unit,
    onItemLongClick: () -> Unit,
    showHierarchyIndent: Boolean = true,
) {
    val folderIndent = if (showHierarchyIndent && shelf.type == ShelfType.FOLDER) (shelf.depth * 14).dp else 0.dp

    androidx.compose.material3.ElevatedCard(
        shape = MaterialTheme.shapes.large,
        colors = androidx.compose.material3.CardDefaults.elevatedCardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow
        ),
        elevation = androidx.compose.material3.CardDefaults.elevatedCardElevation(
            defaultElevation = if (isSelected) 8.dp else 2.dp
        ),
        modifier = Modifier
            .fillMaxWidth()
            .testTag("ShelfItem_${shelf.id}")
            .then(
                if (isSelected) Modifier.border(2.dp, MaterialTheme.colorScheme.primary, MaterialTheme.shapes.large)
                else Modifier
            )
            .clip(MaterialTheme.shapes.large)
            .combinedClickable(
                onClick = onItemClick,
                onLongClick = {
                    if (shelf.name != "Unshelved") {
                        onItemLongClick()
                    }
                }
            )
    ) {
        Row(
            modifier = Modifier.padding(start = 12.dp + folderIndent, end = 12.dp, top = 8.dp, bottom = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            ShelfCover(shelf = shelf)

            Spacer(modifier = Modifier.width(16.dp))

            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    val icon = when (shelf.type) {
                        ShelfType.SMART -> Icons.Default.Star
                        ShelfType.TAG -> Icons.AutoMirrored.Filled.LibraryBooks
                        ShelfType.FOLDER -> Icons.Default.Folder
                        ShelfType.SERIES -> Icons.AutoMirrored.Filled.LibraryBooks
                        ShelfType.MANUAL -> Icons.AutoMirrored.Filled.List
                    }
                    Icon(
                        imageVector = icon,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp),
                        tint = MaterialTheme.colorScheme.primary
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = shelf.name,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = getBookCountString(shelf.bookCount),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

/** 白い熊 UI: per-book actions offered by the cover hamburger menu. */
data class WhiteBearBookMenuActions(
    val onInfo: (RecentFileItem) -> Unit,
    val onTags: (RecentFileItem) -> Unit,
    val onShare: (RecentFileItem) -> Unit,
    val onSaveCopy: (RecentFileItem) -> Unit,
    val onAddParallel: (RecentFileItem) -> Unit,
    val onStartParallel: (RecentFileItem) -> Unit,
    val onDelete: (RecentFileItem) -> Unit
)

/**
 * 白い熊 UI: black-yellow fast scroller over the right edge of the library grid —
 * a full-height black track column with a thick yellow, black-outlined thumb.
 * It appears while the grid scrolls, stays while touched, and fades out shortly
 * after. The whole track is interactive: tap anywhere on the black column to jump
 * straight to that point of the library, or grab (anywhere) and drag to scrub —
 * the track maps linearly onto all items. The track ends above the FAB corner so
 * the bottom of the bar is never buried under the “+” button.
 */
@Composable
private fun WhiteBearFastScrollbar(
    gridState: LazyGridState,
    modifier: Modifier = Modifier
) {
    val scope = rememberCoroutineScope()
    var isDragging by remember { mutableStateOf(false) }

    val scrollable by remember {
        derivedStateOf {
            val info = gridState.layoutInfo
            info.totalItemsCount > info.visibleItemsInfo.size
        }
    }
    // Fraction of the way through the library, by item index — coarse but plenty
    // smooth at thousands of items.
    val scrollFraction by remember {
        derivedStateOf {
            val info = gridState.layoutInfo
            val denominator = (info.totalItemsCount - info.visibleItemsInfo.size).coerceAtLeast(1)
            (gridState.firstVisibleItemIndex.toFloat() / denominator).coerceIn(0f, 1f)
        }
    }

    var visible by remember { mutableStateOf(false) }
    LaunchedEffect(gridState.isScrollInProgress, isDragging, scrollable) {
        if ((gridState.isScrollInProgress || isDragging) && scrollable) {
            visible = true
        } else if (visible) {
            delay(2000)
            visible = false
        }
    }
    val barAlpha by animateFloatAsState(if (visible) 1f else 0f, label = "wbFastScrollAlpha")
    if (barAlpha == 0f) return

    // Bottom inset keeps the track clear of the “+” FAB corner; the track's bottom
    // still means “end of the library”, so the last books stay one tap away.
    BoxWithConstraints(
        modifier = modifier
            .fillMaxHeight()
            .padding(top = 8.dp, bottom = 96.dp, end = 2.dp)
            .width(40.dp)
    ) {
        val density = LocalDensity.current
        val thumbHeight = 88.dp
        val trackPx = with(density) { maxHeight.toPx() }
        val thumbPx = with(density) { thumbHeight.toPx() }
        val maxOffsetPx = (trackPx - thumbPx).coerceAtLeast(0f)
        var dragOffsetPx by remember { mutableStateOf(0f) }
        val thumbOffsetPx = if (isDragging) dragOffsetPx else scrollFraction * maxOffsetPx

        fun scrollToOffset(offsetPx: Float) {
            dragOffsetPx = offsetPx.coerceIn(0f, maxOffsetPx)
            val fraction = if (maxOffsetPx > 0f) dragOffsetPx / maxOffsetPx else 0f
            val lastIndex = gridState.layoutInfo.totalItemsCount - 1
            if (lastIndex >= 0) {
                val target = (fraction * lastIndex).roundToInt().coerceIn(0, lastIndex)
                scope.launch { gridState.scrollToItem(target) }
            }
        }

        // The black track: tap to jump, or drag from anywhere on it to scrub.
        Box(
            modifier = Modifier
                .fillMaxHeight()
                .width(40.dp)
                .graphicsLayer { alpha = barAlpha }
                .background(Color.Black.copy(alpha = 0.75f), RoundedCornerShape(12.dp))
                .pointerInput(maxOffsetPx) {
                    detectTapGestures { tap -> scrollToOffset(tap.y - thumbPx / 2f) }
                }
                .pointerInput(maxOffsetPx) {
                    detectVerticalDragGestures(
                        onDragStart = { start ->
                            isDragging = true
                            scrollToOffset(start.y - thumbPx / 2f)
                        },
                        onDragEnd = { isDragging = false },
                        onDragCancel = { isDragging = false },
                        onVerticalDrag = { change, dragAmount ->
                            change.consume()
                            scrollToOffset(dragOffsetPx + dragAmount)
                        }
                    )
                }
        )
        // The yellow thumb rides on the track; touches pass through to the track.
        Box(
            modifier = Modifier
                .offset { IntOffset(0, thumbOffsetPx.roundToInt()) }
                .size(width = 40.dp, height = thumbHeight)
                .graphicsLayer { alpha = barAlpha }
                .background(Color(0xFFFFD600), RoundedCornerShape(12.dp))
                .border(3.dp, Color.Black, RoundedCornerShape(12.dp)),
            contentAlignment = Alignment.Center
        ) {
            // Grip: three short black bars, so the thumb reads as grabbable.
            Column(verticalArrangement = Arrangement.spacedBy(5.dp)) {
                repeat(3) {
                    Box(
                        modifier = Modifier
                            .size(width = 20.dp, height = 3.dp)
                            .background(Color.Black, RoundedCornerShape(2.dp))
                    )
                }
            }
        }
    }
}

/** Yellow hamburger in the cover's bottom-left corner with the per-book action menu. */
@Composable
private fun WhiteBearBookCoverMenu(
    item: RecentFileItem,
    actions: WhiteBearBookMenuActions,
    modifier: Modifier = Modifier
) {
    var open by remember { mutableStateOf(false) }
    val wbContext = LocalContext.current
    val wbFrame = remember { com.aryan.reader.whitebear.WhiteBearUiState.get(wbContext) }
    Box(modifier = modifier) {
        // Bare three yellow dots — no pill behind them. A black outline (offset copies
        // of the glyph drawn underneath) keeps them visible on yellow and white covers.
        Box(
            modifier = Modifier
                .size(32.dp)
                .clip(CircleShape)
                .clickable { open = true },
            contentAlignment = Alignment.Center
        ) {
            val wbOutline = 1.2.dp
            listOf(
                -wbOutline to -wbOutline, wbOutline to -wbOutline,
                -wbOutline to wbOutline, wbOutline to wbOutline,
                0.dp to -wbOutline, 0.dp to wbOutline,
                -wbOutline to 0.dp, wbOutline to 0.dp
            ).forEach { (dx, dy) ->
                Icon(
                    Icons.Default.MoreVert,
                    contentDescription = null,
                    tint = Color.Black,
                    modifier = Modifier.offset(x = dx, y = dy)
                )
            }
            Icon(
                Icons.Default.MoreVert,
                contentDescription = "Book actions",
                tint = MaterialTheme.colorScheme.primary
            )
        }
        DropdownMenu(
            expanded = open,
            onDismissRequest = { open = false },
            modifier = Modifier.border(
                wbFrame.borderWidth.coerceAtLeast(1f).dp,
                MaterialTheme.colorScheme.outline,
                MaterialTheme.shapes.extraSmall
            )
        ) {
            DropdownMenuItem(
                text = { Text("Start new parallel reading") },
                leadingIcon = { Icon(painterResource(id = R.drawable.wb_parallel), contentDescription = null) },
                onClick = { open = false; actions.onStartParallel(item) }
            )
            DropdownMenuItem(
                text = { Text("Add to parallel reading") },
                leadingIcon = { Icon(painterResource(id = R.drawable.wb_parallel), contentDescription = null) },
                onClick = { open = false; actions.onAddParallel(item) }
            )
            DropdownMenuItem(
                text = { Text("File info") },
                leadingIcon = { Icon(Icons.Default.Info, contentDescription = null) },
                onClick = { open = false; actions.onInfo(item) }
            )
            DropdownMenuItem(
                text = { Text("Tags") },
                leadingIcon = { Icon(painterResource(id = R.drawable.tag), contentDescription = null) },
                onClick = { open = false; actions.onTags(item) }
            )
            if (item.canExportOriginalFile()) {
                DropdownMenuItem(
                    text = { Text("Share file") },
                    leadingIcon = { Icon(Icons.Default.Share, contentDescription = null) },
                    onClick = { open = false; actions.onShare(item) }
                )
                DropdownMenuItem(
                    text = { Text("Save a copy") },
                    leadingIcon = { Icon(painterResource(id = R.drawable.wb_save_alt), contentDescription = null) },
                    onClick = { open = false; actions.onSaveCopy(item) }
                )
            }
            HorizontalDivider()
            DropdownMenuItem(
                text = { Text(stringResource(R.string.action_delete), color = MaterialTheme.colorScheme.error) },
                leadingIcon = {
                    Icon(Icons.Default.Delete, contentDescription = null, tint = MaterialTheme.colorScheme.error)
                },
                onClick = { open = false; actions.onDelete(item) }
            )
        }
    }
}

/**
 * 白い熊 UI: order the selected 2–3 books left → right for parallel reading, then start.
 * While the set is active, a two-finger horizontal swipe in the reader flips books.
 */
@Composable
private fun WhiteBearParallelOrderDialog(
    items: List<RecentFileItem>,
    onStart: (List<String>) -> Unit,
    onClearSet: () -> Unit,
    onDismiss: () -> Unit
) {
    var order by remember { mutableStateOf(items) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Parallel reading") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(
                    "Order the books left → right. While reading, swipe horizontally with two fingers to flip between them.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                order.forEachIndexed { index, item ->
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            "${index + 1}.",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.width(28.dp)
                        )
                        Text(
                            item.cardTitle(false),
                            modifier = Modifier.weight(1f),
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                            style = MaterialTheme.typography.bodyMedium
                        )
                        IconButton(
                            onClick = {
                                if (index > 0) {
                                    order = order.toMutableList().also {
                                        val tmp = it[index]
                                        it[index] = it[index - 1]
                                        it[index - 1] = tmp
                                    }
                                }
                            },
                            enabled = index > 0,
                            modifier = Modifier.size(32.dp)
                        ) {
                            Icon(Icons.Default.KeyboardArrowUp, contentDescription = "Move left")
                        }
                        IconButton(
                            onClick = {
                                if (index < order.lastIndex) {
                                    order = order.toMutableList().also {
                                        val tmp = it[index]
                                        it[index] = it[index + 1]
                                        it[index + 1] = tmp
                                    }
                                }
                            },
                            enabled = index < order.lastIndex,
                            modifier = Modifier.size(32.dp)
                        ) {
                            Icon(Icons.Default.KeyboardArrowDown, contentDescription = "Move right")
                        }
                    }
                }
                TextButton(onClick = onClearSet) {
                    Text("Clear current parallel set")
                }
            }
        },
        confirmButton = {
            Button(onClick = { onStart(order.map { it.bookId }) }) {
                Text("Start reading")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.action_cancel))
            }
        }
    )
}

/** Compact slider row inside the layout dropdown menu; the grid reflows live. */
@Composable
private fun WhiteBearLayoutMenuSlider(
    label: String,
    value: Float,
    valueText: String,
    range: ClosedFloatingPointRange<Float>,
    steps: Int,
    onChange: (Float) -> Unit
) {
    Column(
        modifier = Modifier
            .width(280.dp)
            .padding(horizontal = 16.dp, vertical = 2.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(label, style = MaterialTheme.typography.bodySmall, modifier = Modifier.weight(1f))
            Text(
                valueText,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Slider(
            value = value,
            onValueChange = onChange,
            valueRange = range,
            steps = steps,
            modifier = Modifier
                .fillMaxWidth()
                .height(28.dp)
        )
    }
}

/** Author / Tag pull-down filters with a clear-filters icon on the left. */
@Composable
private fun WhiteBearLibraryFilterRow(
    authors: List<String>,
    allTags: List<TagEntity>,
    selectedAuthor: String?,
    selectedTagId: String?,
    onAuthorSelected: (String?) -> Unit,
    onTagSelected: (String?) -> Unit,
    onClearFilters: () -> Unit
) {
    var authorMenuOpen by remember { mutableStateOf(false) }
    var tagMenuOpen by remember { mutableStateOf(false) }
    var authorQuery by remember { mutableStateOf("") }
    var authorSortByLastName by rememberSaveable { mutableStateOf(false) }
    val filtersActive = selectedAuthor != null || selectedTagId != null
    val sortedTags = remember(allTags) { allTags.sortedBy { it.name.lowercase() } }
    val visibleAuthors = remember(authors, authorQuery, authorSortByLastName) {
        val filtered = if (authorQuery.isBlank()) {
            authors
        } else {
            authors.filter { it.contains(authorQuery.trim(), ignoreCase = true) }
        }
        if (authorSortByLastName) {
            filtered.sortedBy { it.trim().substringAfterLast(' ').lowercase() }
        } else {
            filtered.sortedBy { it.lowercase() }
        }
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        IconButton(onClick = onClearFilters, enabled = filtersActive) {
            Icon(
                painterResource(id = R.drawable.wb_filter_off),
                contentDescription = "Show all",
                tint = if (filtersActive) MaterialTheme.colorScheme.primary
                else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
            )
        }
        Box(modifier = Modifier.weight(1f)) {
            TextButton(onClick = { authorMenuOpen = true }, modifier = Modifier.fillMaxWidth()) {
                Text(
                    selectedAuthor ?: "Author",
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f, fill = false)
                )
                Icon(Icons.Default.ArrowDropDown, contentDescription = null)
            }
            DropdownMenu(expanded = authorMenuOpen, onDismissRequest = { authorMenuOpen = false }) {
                // 白い熊 UI: search-as-you-type plus first/last-name sorting.
                androidx.compose.material3.OutlinedTextField(
                    value = authorQuery,
                    onValueChange = { authorQuery = it },
                    placeholder = { Text("Search authors…") },
                    singleLine = true,
                    modifier = Modifier
                        .width(300.dp)
                        .padding(horizontal = 12.dp, vertical = 4.dp)
                )
                Row(
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 2.dp),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    androidx.compose.material3.FilterChip(
                        selected = !authorSortByLastName,
                        onClick = { authorSortByLastName = false },
                        label = { Text("First name") }
                    )
                    androidx.compose.material3.FilterChip(
                        selected = authorSortByLastName,
                        onClick = { authorSortByLastName = true },
                        label = { Text("Last name") }
                    )
                }
                HorizontalDivider()
                visibleAuthors.forEach { author ->
                    DropdownMenuItem(
                        text = { Text(author, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                        trailingIcon = if (author == selectedAuthor) {
                            { Icon(Icons.Default.Check, contentDescription = null) }
                        } else null,
                        onClick = {
                            onAuthorSelected(if (author == selectedAuthor) null else author)
                            authorMenuOpen = false
                            authorQuery = ""
                        }
                    )
                }
            }
        }
        Box(modifier = Modifier.weight(1f)) {
            TextButton(onClick = { tagMenuOpen = true }, modifier = Modifier.fillMaxWidth()) {
                Text(
                    sortedTags.firstOrNull { it.id == selectedTagId }?.name ?: "Tag",
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f, fill = false)
                )
                Icon(Icons.Default.ArrowDropDown, contentDescription = null)
            }
            DropdownMenu(expanded = tagMenuOpen, onDismissRequest = { tagMenuOpen = false }) {
                sortedTags.forEach { tag ->
                    DropdownMenuItem(
                        text = { Text(tag.name, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                        trailingIcon = if (tag.id == selectedTagId) {
                            { Icon(Icons.Default.Check, contentDescription = null) }
                        } else null,
                        onClick = {
                            onTagSelected(if (tag.id == selectedTagId) null else tag.id)
                            tagMenuOpen = false
                        }
                    )
                }
            }
        }
    }
}

/** Grid cell: cover thumbnail with bold title and author under it; sizes are user-set. */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun WhiteBearLibraryGridItem(
    item: RecentFileItem,
    isSelected: Boolean,
    thumbnailHeightDp: Float,
    titleFontSp: Float,
    authorFontSp: Float,
    onItemClick: () -> Unit,
    onItemLongClick: () -> Unit,
    usePdfFileNameAsDisplayName: Boolean,
    menuActions: WhiteBearBookMenuActions? = null,
    onAuthorClick: ((String) -> Unit)? = null,
    isAuthorFilterActive: Boolean = false
) {
    // Only a real author name can be filtered on; "No author listed" stays inert and lets
    // the tap through to the cell, so those covers still open on a single tap.
    val filterableAuthor = item.filterAuthor()?.takeIf { onAuthorClick != null }
    Column(
        modifier = Modifier
            .clip(MaterialTheme.shapes.medium)
            .then(
                if (isSelected) {
                    Modifier.border(2.dp, MaterialTheme.colorScheme.primary, MaterialTheme.shapes.medium)
                } else Modifier
            )
            .combinedClickable(onClick = onItemClick, onLongClick = onItemLongClick)
            .padding(2.dp)
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(thumbnailHeightDp.dp)
                .clip(MaterialTheme.shapes.medium)
                .border(1.dp, MaterialTheme.colorScheme.outlineVariant, MaterialTheme.shapes.medium)
        ) {
            ThemedBookCover(
                item = item,
                contentDescription = item.displayName,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize()
            )
            if (item.type == FileType.PDF) {
                FileTypeBadge(
                    type = item.type,
                    overlay = true,
                    compact = true,
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .padding(4.dp)
                )
            }
            if (menuActions != null) {
                WhiteBearBookCoverMenu(
                    item = item,
                    actions = menuActions,
                    modifier = Modifier
                        .align(Alignment.BottomStart)
                        .padding(4.dp)
                )
            }
        }
        Text(
            item.cardTitle(usePdfFileNameAsDisplayName),
            fontSize = titleFontSp.sp,
            lineHeight = (titleFontSp * 1.15f).sp,
            fontWeight = FontWeight.Bold,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(top = 4.dp)
        )
        Text(
            item.cardAuthor(),
            fontSize = authorFontSp.sp,
            lineHeight = (authorFontSp * 1.15f).sp,
            color = if (filterableAuthor != null && isAuthorFilterActive) {
                MaterialTheme.colorScheme.primary
            } else {
                MaterialTheme.colorScheme.onSurfaceVariant
            },
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = if (filterableAuthor != null && onAuthorClick != null) {
                // Long press keeps selecting the book, so the author line does not
                // become a hole in the grid's selection gesture.
                Modifier.combinedClickable(
                    onClick = { onAuthorClick(filterableAuthor) },
                    onLongClick = onItemLongClick
                )
            } else {
                Modifier
            }
        )
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun LibraryListItem(
    item: RecentFileItem,
    isSelected: Boolean,
    isPinned: Boolean = false,
    onItemClick: () -> Unit,
    onItemLongClick: () -> Unit,
    isDownloading: Boolean,
    usePdfFileNameAsDisplayName: Boolean = false,
    menuActions: WhiteBearBookMenuActions? = null,
    onAuthorClick: ((String) -> Unit)? = null,
    isAuthorFilterActive: Boolean = false,
) {
    // Only a real author name can be filtered on; "No author listed" stays inert and lets
    // the tap through to the card. Null on the shelf and add-books screens, where the
    // library author filter does not apply.
    val filterableAuthor = item.filterAuthor()?.takeIf { onAuthorClick != null }
    androidx.compose.material3.ElevatedCard(
        shape = MaterialTheme.shapes.large,
        colors = androidx.compose.material3.CardDefaults.elevatedCardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow
        ),
        elevation = androidx.compose.material3.CardDefaults.elevatedCardElevation(
            defaultElevation = if (isSelected) 6.dp else 2.dp
        ),
        modifier = Modifier
            .fillMaxWidth()
            .testTag("LibraryBookItem_${item.bookId}")
            .graphicsLayer { alpha = if (item.isAvailable) 1.0f else 0.8f }
            .then(
                if (isSelected) Modifier.border(2.dp, MaterialTheme.colorScheme.primary, MaterialTheme.shapes.large)
                else Modifier
            )
            .clip(MaterialTheme.shapes.large)
            .combinedClickable(
                onClick = onItemClick,
                onLongClick = onItemLongClick
            )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 12.dp)
                .height(132.dp),
            verticalAlignment = Alignment.Top,
            horizontalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Box(
                modifier = Modifier
                    .fillMaxHeight()
                    .aspectRatio(0.7f)
                    .clip(MaterialTheme.shapes.medium)
                    .border(0.5.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f), MaterialTheme.shapes.medium)
            ) {
                ThemedBookCover(
                    item = item,
                    contentDescription = item.displayName,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize()
                )
                if (isSelected) {
                    Box(
                        modifier = Modifier.matchParentSize().background(MaterialTheme.colorScheme.primary.copy(alpha = 0.2f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(Icons.Default.Check, contentDescription = stringResource(R.string.content_desc_selected), modifier = Modifier.size(36.dp).background(MaterialTheme.colorScheme.primary, CircleShape).padding(6.dp), tint = MaterialTheme.colorScheme.onPrimary)
                    }
                }
                if (menuActions != null && !isSelected) {
                    WhiteBearBookCoverMenu(
                        item = item,
                        actions = menuActions,
                        modifier = Modifier
                            .align(Alignment.BottomStart)
                            .padding(3.dp)
                    )
                }
            }

            Column(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight()
            ) {
                Row(
                    verticalAlignment = Alignment.Top,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = item.cardTitle(usePdfFileNameAsDisplayName),
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.SemiBold,
                            maxLines = 2,
                            minLines = 2,
                            overflow = TextOverflow.Ellipsis,
                            lineHeight = 20.sp
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = item.cardAuthor(),
                            style = MaterialTheme.typography.bodySmall,
                            maxLines = 1,
                            minLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            color = if (filterableAuthor != null && isAuthorFilterActive) {
                                MaterialTheme.colorScheme.primary
                            } else {
                                MaterialTheme.colorScheme.onSurfaceVariant
                            },
                            modifier = if (filterableAuthor != null && onAuthorClick != null) {
                                // Long press keeps selecting the book, so the author line does
                                // not become a hole in the list's selection gesture.
                                Modifier.combinedClickable(
                                    onClick = { onAuthorClick(filterableAuthor) },
                                    onLongClick = onItemLongClick
                                )
                            } else {
                                Modifier
                            }
                        )
                    }

                    if (item.sourceFolderUri != null || item.isOpdsStream() || isPinned) {
                        FileStatusBadges(
                            item = item,
                            isPinned = isPinned
                        )
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    FileTypeBadge(type = item.type, overlay = false)

                    if (item.tags.isNotEmpty()) {
                        BookTagChipsRow(
                            tags = item.tags,
                            compact = true,
                            modifier = Modifier.weight(1f, fill = false)
                        )
                    } else {
                        Spacer(modifier = Modifier.weight(1f))
                    }

                    if (!item.isAvailable) {
                        Surface(
                            shape = RoundedCornerShape(50),
                            color = if (isDownloading) {
                                MaterialTheme.colorScheme.primaryContainer
                            } else {
                                MaterialTheme.colorScheme.errorContainer
                            },
                            contentColor = if (isDownloading) {
                                MaterialTheme.colorScheme.onPrimaryContainer
                            } else {
                                MaterialTheme.colorScheme.onErrorContainer
                            }
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                if (isDownloading) {
                                    CircularProgressIndicator(
                                        modifier = Modifier.size(14.dp),
                                        strokeWidth = 2.dp
                                    )
                                } else {
                                    Icon(
                                        Icons.Filled.Info,
                                        contentDescription = stringResource(R.string.not_available_locally),
                                        modifier = Modifier.size(14.dp)
                                    )
                                }
                                Text(
                                    text = if (isDownloading) {
                                        stringResource(R.string.status_downloading)
                                    } else {
                                        stringResource(R.string.not_available_locally)
                                    },
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = FontWeight.Medium
                                )
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.weight(1f))

                ReadingProgressSection(
                    progressPercentage = item.progressPercentage,
                    compact = true,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }
    }
}

@Composable
private fun RenameShelfDialog(
    initialName: String,
    onConfirm: (String) -> Unit,
    onDismiss: () -> Unit
) {
    var textFieldValue by remember {
        mutableStateOf(
            TextFieldValue(
                text = initialName,
                selection = TextRange(initialName.length)
            )
        )
    }
    val focusRequester = remember { FocusRequester() }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.menu_rename_shelf)) },
        text = {
            OutlinedTextField(
                value = textFieldValue,
                onValueChange = { textFieldValue = it },
                placeholder = { Text(stringResource(R.string.shelf_name_hint)) },
                singleLine = true,
                modifier = Modifier.focusRequester(focusRequester)
            )
        },
        confirmButton = {
            TextButton(
                onClick = { onConfirm(textFieldValue.text) },
                enabled = textFieldValue.text.isNotBlank() && textFieldValue.text != initialName
            ) {
                Text(stringResource(R.string.action_rename))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.action_cancel))
            }
        }
    )

    LaunchedEffect(Unit) {
        delay(100)
        focusRequester.requestFocus()
    }
}

@Composable
private fun DeleteShelfConfirmationDialog(
    shelfName: String,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.dialog_delete_shelf)) },
        text = { Text(stringResource(R.string.dialog_delete_shelf_desc, shelfName)) },
        confirmButton = {
            TextButton(onClick = onConfirm) { Text(stringResource(R.string.action_delete)) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) }
        }
    )
}

@Composable
private fun RemoveFromShelfConfirmationDialog(
    count: Int,
    shelfName: String,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.dialog_remove_from_shelf)) },
        text = { Text(pluralStringResource(R.plurals.dialog_remove_from_shelf_desc, count, count, shelfName)) },
        confirmButton = {
            TextButton(onClick = onConfirm) { Text(stringResource(R.string.action_remove)) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) }
        }
    )
}

@Composable
private fun DeleteShelvesConfirmationDialog(
    count: Int,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit
) {
    val shelfStr = pluralStringResource(id = R.plurals.shelf_count, count)
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.dialog_delete_shelves, shelfStr)) },
        text = { Text(stringResource(R.string.dialog_delete_shelves_desc, count, shelfStr)) },
        confirmButton = {
            TextButton(onClick = onConfirm) { Text(stringResource(R.string.action_delete)) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) }
        }
    )
}

@Composable
internal fun FolderSyncScreen(
    syncedFolders: List<SyncedFolder>,
    allRecentFiles: List<RecentFileItem>,
    onAddFolderClick: () -> Unit,
    onRemoveFolderClick: (SyncedFolder) -> Unit,
    onFolderLocalSyncChange: (SyncedFolder, Boolean, Boolean) -> Unit,
    onEditFolderFiltersClick: (SyncedFolder, Set<FileType>) -> Unit,
    onScanNowClick: () -> Unit,
    onSyncMetadataClick: () -> Unit,
    // 白い熊: rescan a single folder instead of every linked folder.
    onScanFolderClick: (SyncedFolder) -> Unit = {},
    isLoading: Boolean
) {
    var editingFolder by remember { mutableStateOf<SyncedFolder?>(null) }
    var disablingFolder by remember { mutableStateOf<SyncedFolder?>(null) }
    val hasEnabledSyncFolders = syncedFolders.any { it.localSyncEnabled }
    val folderStatsByUri = remember(allRecentFiles) {
        allRecentFiles
            .asSequence()
            .filter { it.sourceFolderUri != null }
            .groupBy { it.sourceFolderUri!! }
            .mapValues { (_, files) ->
                FolderFileStats(
                    totalBooks = files.size,
                    countsByType = files.groupingBy { it.type }.eachCount()
                )
            }
    }

    Scaffold(
        floatingActionButton = {
            if (syncedFolders.size < 10) {
                ExtendedFloatingActionButton(
                    text = { Text(stringResource(R.string.fab_add_folder)) },
                    icon = { Icon(Icons.Default.Add, "Add") },
                    onClick = onAddFolderClick
                )
            }
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            if (syncedFolders.isNotEmpty()) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    FilledTonalButton(
                        onClick = onScanNowClick,
                        enabled = !isLoading && hasEnabledSyncFolders,
                        modifier = Modifier.weight(1f),
                        shape = MaterialTheme.shapes.small
                    ) {
                        if (isLoading) {
                            CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                        } else {
                            Icon(Icons.Default.Search, null, modifier = Modifier.size(18.dp))
                        }
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(if (isLoading) stringResource(R.string.scanning) else stringResource(R.string.scan_all))
                    }

                    androidx.compose.material3.OutlinedButton(
                        onClick = onSyncMetadataClick,
                        enabled = !isLoading && hasEnabledSyncFolders,
                        modifier = Modifier.weight(1f),
                        shape = MaterialTheme.shapes.small
                    ) {
                        Icon(painterResource(id = R.drawable.sync), null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(stringResource(R.string.sync_meta))
                    }
                }
            } else {
                EmptyState(
                    title = stringResource(R.string.sync_local_folders),
                    message = stringResource(R.string.sync_folders_desc),
                    onSelectFileClick = onAddFolderClick,
                    primaryButtonText = stringResource(R.string.action_select_folder),
                    modifier = Modifier.fillMaxSize()
                )
            }

            LazyColumn(
                verticalArrangement = Arrangement.spacedBy(12.dp),
                contentPadding = PaddingValues(bottom = 80.dp)
            ) {
                items(syncedFolders, key = { it.uriString }) { folder ->
                    FolderCard(
                        folder = folder,
                        stats = folderStatsByUri[folder.uriString] ?: FolderFileStats.Empty,
                        onScanFolderClick = onScanFolderClick,
                        onRemoveClick = onRemoveFolderClick,
                        onLocalSyncToggleClick = { selectedFolder ->
                            if (selectedFolder.localSyncEnabled) {
                                disablingFolder = selectedFolder
                            } else {
                                onFolderLocalSyncChange(selectedFolder, true, false)
                            }
                        },
                        onEditFiltersClick = { editingFolder = folder }
                    )
                }
            }
        }
    }

    editingFolder?.let { folder ->
        EditFolderFiltersDialog(
            folder = folder,
            onConfirm = { newFilters ->
                onEditFolderFiltersClick(folder, newFilters)
                editingFolder = null
            },
            onDismiss = { editingFolder = null }
        )
    }

    disablingFolder?.let { folder ->
        AlertDialog(
            onDismissRequest = { disablingFolder = null },
            title = { Text(stringResource(R.string.dialog_disable_folder_local_sync_title)) },
            text = {
                Text(
                    stringResource(
                        R.string.dialog_disable_folder_local_sync_desc,
                        LOCAL_FOLDER_SYNC_DATA_DIR
                    )
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        onFolderLocalSyncChange(folder, false, true)
                        disablingFolder = null
                    }
                ) {
                    Text(stringResource(R.string.action_disable_remove_sync_data))
                }
            },
            dismissButton = {
                Row {
                    TextButton(onClick = { disablingFolder = null }) {
                        Text(stringResource(R.string.action_cancel))
                    }
                    TextButton(
                        onClick = {
                            onFolderLocalSyncChange(folder, false, false)
                            disablingFolder = null
                        }
                    ) {
                        Text(stringResource(R.string.action_disable_keep_sync_data))
                    }
                }
            }
        )
    }
}

private data class FolderFileStats(
    val totalBooks: Int,
    val countsByType: Map<FileType, Int>
) {
    companion object {
        val Empty = FolderFileStats(totalBooks = 0, countsByType = emptyMap())
    }
}

@OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
@Composable
private fun FolderCard(
    folder: SyncedFolder,
    stats: FolderFileStats,
    onScanFolderClick: (SyncedFolder) -> Unit,
    onRemoveClick: (SyncedFolder) -> Unit,
    onLocalSyncToggleClick: (SyncedFolder) -> Unit,
    onEditFiltersClick: (SyncedFolder) -> Unit
) {
    var showMenu by remember { mutableStateOf(false) }
    val dateFormat = remember { SimpleDateFormat("MMM d, h:mm a", Locale.getDefault()) }
    val lastScanText = if (folder.lastScanTime == 0L) stringResource(R.string.never) else dateFormat.format(Date(folder.lastScanTime))

    androidx.compose.material3.ElevatedCard(
        modifier = Modifier.fillMaxWidth(),
        colors = androidx.compose.material3.CardDefaults.elevatedCardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh
        )
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                    Icon(
                        imageVector = Icons.Default.FolderSpecial,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = folder.name,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        if (!folder.localSyncEnabled) {
                            Text(
                                text = stringResource(R.string.folder_local_sync_disabled),
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.error
                            )
                        }
                    }
                }

                Box {
                    IconButton(onClick = { showMenu = true }) {
                        Icon(Icons.Default.MoreVert, "Options")
                    }
                    DropdownMenu(expanded = showMenu, onDismissRequest = { showMenu = false }) {
                        // 白い熊: rescanning just the folder the new books landed in is far
                        // cheaper than walking every linked folder.
                        if (folder.localSyncEnabled) {
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.menu_scan_this_folder)) },
                                leadingIcon = { Icon(Icons.Default.Refresh, contentDescription = null) },
                                onClick = {
                                    showMenu = false
                                    onScanFolderClick(folder)
                                }
                            )
                        }
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.menu_edit_filters)) },
                            onClick = {
                                showMenu = false
                                onEditFiltersClick(folder)
                            }
                        )
                        DropdownMenuItem(
                            text = {
                                Text(
                                    if (folder.localSyncEnabled) {
                                        stringResource(R.string.menu_disable_folder_local_sync)
                                    } else {
                                        stringResource(R.string.menu_enable_folder_local_sync)
                                    }
                                )
                            },
                            onClick = {
                                showMenu = false
                                onLocalSyncToggleClick(folder)
                            }
                        )
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.menu_remove_folder)) },
                            onClick = {
                                showMenu = false
                                onRemoveClick(folder)
                            },
                            colors = androidx.compose.material3.MenuDefaults.itemColors(
                                textColor = MaterialTheme.colorScheme.error
                            )
                        )
                    }
                }
            }

            HorizontalDivider(modifier = Modifier.padding(vertical = 12.dp))

            Row(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = stringResource(R.string.last_sync),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontWeight = FontWeight.Bold
                    )
                    Text(text = lastScanText, style = MaterialTheme.typography.bodySmall)
                }

                Column(modifier = Modifier.weight(1f), horizontalAlignment = Alignment.End) {
                    Text(
                        text = stringResource(R.string.books_count),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontWeight = FontWeight.Bold
                    )
                    Text(text = stats.totalBooks.toString(), style = MaterialTheme.typography.bodyMedium)
                }
            }

            if (stats.countsByType.isNotEmpty()) {
                Spacer(modifier = Modifier.height(12.dp))
                androidx.compose.foundation.layout.FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    stats.countsByType.forEach { (type, count) ->
                        AssistChip(
                            onClick = { },
                            label = { Text(stringResource(R.string.folder_filter_count, type.name, count)) }
                        )
                    }
                }
            }
        }
    }
}

@OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
@Composable
private fun EditFolderFiltersDialog(
    folder: SyncedFolder,
    onConfirm: (Set<FileType>) -> Unit,
    onDismiss: () -> Unit
) {
    var selectedTypes by remember { mutableStateOf(folder.allowedFileTypes) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Column {
                Text(
                    text = stringResource(R.string.filter_file_types),
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = stringResource(R.string.filter_file_types_desc),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        },
        text = {
            Column(modifier = Modifier.fillMaxWidth()) {
                HorizontalDivider(modifier = Modifier.padding(bottom = 16.dp))

                androidx.compose.foundation.layout.FlowRow(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    ANDROID_SYNCABLE_FILE_TYPES.forEach { type ->
                        val isSelected = type in selectedTypes
                        FilterChip(
                            selected = isSelected,
                            onClick = {
                                selectedTypes = if (isSelected) {
                                    selectedTypes - type
                                } else {
                                    selectedTypes + type
                                }
                            },
                            label = {
                                Text(
                                    text = type.name,
                                    style = MaterialTheme.typography.labelLarge
                                )
                            },
                            leadingIcon = if (isSelected) {
                                {
                                    Icon(
                                        imageVector = Icons.Default.Check,
                                        contentDescription = null,
                                        modifier = Modifier.size(16.dp)
                                    )
                                }
                            } else null,
                            shape = MaterialTheme.shapes.medium
                        )
                    }
                }
            }
        },
        confirmButton = {
            androidx.compose.material3.Button(
                onClick = { onConfirm(selectedTypes) },
                enabled = selectedTypes.isNotEmpty(),
                shape = MaterialTheme.shapes.medium
            ) {
                Text(stringResource(R.string.action_save))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.action_cancel))
            }
        }
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LibraryFilterSheet(
    filters: LibraryFilters,
    allTags: List<TagEntity>,
    syncedFolders: List<SyncedFolder>,
    onApply: (LibraryFilters) -> Unit,
    onDismiss: () -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var currentFilters by remember { mutableStateOf(filters) }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Text(stringResource(R.string.filter_library), style = MaterialTheme.typography.titleLarge)

            Text(stringResource(R.string.filter_file_type), style = MaterialTheme.typography.titleMedium)
            Row(
                modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                ANDROID_READABLE_FILE_TYPES.forEach { type ->
                    FilterChip(
                        selected = type in currentFilters.fileTypes,
                        onClick = {
                            val newSet = if (type in currentFilters.fileTypes) currentFilters.fileTypes - type else currentFilters.fileTypes + type
                            currentFilters = currentFilters.copy(fileTypes = newSet)
                        },
                        label = { Text(type.name) }
                    )
                }
            }

            Text(stringResource(R.string.filter_source_folder), style = MaterialTheme.typography.titleMedium)
            Row(
                modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                FilterChip(
                    selected = "IN_APP_STORAGE" in currentFilters.sourceFolders,
                    onClick = {
                        val newSet = if ("IN_APP_STORAGE" in currentFilters.sourceFolders) currentFilters.sourceFolders - "IN_APP_STORAGE" else currentFilters.sourceFolders + "IN_APP_STORAGE"
                        currentFilters = currentFilters.copy(sourceFolders = newSet)
                              },
                    label = { Text(stringResource(R.string.filter_in_app_storage)) }
                )
                syncedFolders.forEach { folder ->
                    FilterChip(
                        selected = folder.uriString in currentFilters.sourceFolders,
                        onClick = {
                            val newSet = if (folder.uriString in currentFilters.sourceFolders) currentFilters.sourceFolders - folder.uriString else currentFilters.sourceFolders + folder.uriString
                            currentFilters = currentFilters.copy(sourceFolders = newSet)
                                  },
                        label = { Text(folder.name) }
                    )
                }
            }

            Text(stringResource(R.string.filter_read_status), style = MaterialTheme.typography.titleMedium)
            Row(
                modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                ReadStatusFilter.entries.forEach { status ->
                    FilterChip(
                        selected = currentFilters.readStatus == status,
                        onClick = { currentFilters = currentFilters.copy(readStatus = status) },
                        label = { Text(stringResource(status.labelRes)) }
                    )
                }
            }

            if (allTags.isNotEmpty()) {
                Text(stringResource(R.string.section_tags), style = MaterialTheme.typography.titleMedium)
                FlowRow(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    allTags.forEach { tag ->
                        val selected = tag.id in currentFilters.tagIds
                        FilterChip(
                            selected = selected,
                            onClick = {
                                val newSet = if (selected) {
                                    currentFilters.tagIds - tag.id
                                } else {
                                    currentFilters.tagIds + tag.id
                                }
                                currentFilters = currentFilters.copy(tagIds = newSet)
                            },
                            label = { Text(tag.name) },
                            leadingIcon = {
                                Box(
                                    modifier = Modifier
                                        .size(10.dp)
                                        .background(
                                            Color(tag.color ?: 0xFF64B5F6.toInt()),
                                            CircleShape
                                        )
                                )
                            }
                        )
                    }
                }
            }

            Row(modifier = Modifier.fillMaxWidth().padding(top = 16.dp), horizontalArrangement = Arrangement.End) {
                TextButton(onClick = { currentFilters = LibraryFilters() }) {
                    Text(stringResource(R.string.clear_all))
                }
                Spacer(modifier = Modifier.width(8.dp))
                androidx.compose.material3.Button(onClick = { onApply(currentFilters); onDismiss() }) {
                    Text(stringResource(R.string.action_apply))
                }
            }
            Spacer(modifier = Modifier.height(32.dp))
        }
    }
}

@Composable
fun OpdsTab(
    localLibraryFiles: List<RecentFileItem>,
    onBookDownloaded: (Uri, String) -> Unit,
    onReadBook: (RecentFileItem) -> Unit,
    onStreamBook: (OpdsEntry, OpdsCatalog?) -> Unit,
    onDeleteCatalogStreams: (String) -> Unit,
    opdsViewModel: OpdsViewModel = viewModel()
) {
    val uiState by opdsViewModel.uiState.collectAsStateWithLifecycle()
    val downloadingState = uiState.downloadingState
    val context = LocalContext.current
    val coverImageLoader = rememberOpdsCoverImageLoader(uiState.currentCatalog)
    var selectedEntry by remember { mutableStateOf<OpdsEntry?>(null) }
    var showCatalogDialog by remember { mutableStateOf(false) }
    var editingCatalog by remember { mutableStateOf<OpdsCatalog?>(null) }
    var catalogToDelete by remember { mutableStateOf<OpdsCatalog?>(null) }

    BackHandler(enabled = uiState.isViewingCatalog) {
        opdsViewModel.navigateBack()
    }

    Box(modifier = Modifier.fillMaxSize()) {
        if (!uiState.isViewingCatalog) {
            Box(modifier = Modifier.fillMaxSize()) {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(
                        start = 16.dp,
                        end = 16.dp,
                        top = 16.dp,
                        bottom = 88.dp
                    ),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    items(uiState.catalogs, key = { it.id }) { catalog ->
                        OpdsCatalogCard(
                            catalog = catalog,
                            onClick = { opdsViewModel.openCatalog(catalog) },
                            onEdit = if (catalog.isDefault) null else {
                                {
                                    editingCatalog = catalog
                                    showCatalogDialog = true
                                }
                            },
                            onDelete = if (catalog.isDefault) null else {
                                { catalogToDelete = catalog }
                            })
                    }
                }

                ExtendedFloatingActionButton(
                    text = { Text(stringResource(R.string.fab_add_catalog)) },
                    icon = { Icon(Icons.Default.Add, "Add") },
                    onClick = {
                        editingCatalog = null
                        showCatalogDialog = true
                    },
                    modifier = Modifier.align(Alignment.BottomEnd).padding(16.dp)
                )
            }
        } else {
            // Screen 2: Viewing a specific feed/catalog
            Box(modifier = Modifier.fillMaxSize()) {
                Column(modifier = Modifier.fillMaxSize()) {
                    Surface(
                        color = MaterialTheme.colorScheme.surface,
                        tonalElevation = 2.dp,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        var showSearch by remember { mutableStateOf(false) }
                        var query by remember { mutableStateOf("") }

                        val searchFocusRequester = remember { FocusRequester() }

                        LaunchedEffect(showSearch) {
                            if (showSearch) {
                                delay(100)
                                searchFocusRequester.requestFocus()
                            }
                        }

                        Box(modifier = Modifier.fillMaxWidth()) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.fillMaxWidth().height(64.dp)
                                    .padding(horizontal = 4.dp)
                            ) {
                                IconButton(onClick = {
                                    if (showSearch) {
                                        showSearch = false
                                        query = ""
                                    } else {
                                        opdsViewModel.navigateBack()
                                    }
                                }) {
                                    Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back")
                                }

                                if (showSearch) {
                                    OutlinedTextField(
                                        value = query,
                                        onValueChange = { query = it },
                                        placeholder = { Text(stringResource(R.string.search_catalog_placeholder)) },
                                        modifier = Modifier.weight(1f).padding(vertical = 4.dp)
                                            .focusRequester(searchFocusRequester),
                                        singleLine = true,
                                        colors = TextFieldDefaults.colors(
                                            focusedContainerColor = Color.Transparent,
                                            unfocusedContainerColor = Color.Transparent,
                                            disabledContainerColor = Color.Transparent,
                                            focusedIndicatorColor = Color.Transparent,
                                            unfocusedIndicatorColor = Color.Transparent,
                                        ),
                                        trailingIcon = {
                                            IconButton(onClick = {
                                                if (query.isNotBlank()) {
                                                    opdsViewModel.search(query)
                                                    showSearch = false
                                                    query = ""
                                                }
                                            }) {
                                                Icon(Icons.Default.Search, "Search")
                                            }
                                        },
                                        keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(
                                            imeAction = androidx.compose.ui.text.input.ImeAction.Search
                                        ),
                                        keyboardActions = androidx.compose.foundation.text.KeyboardActions(
                                            onSearch = {
                                                if (query.isNotBlank()) {
                                                    opdsViewModel.search(query)
                                                    showSearch = false
                                                    query = ""
                                                }
                                            })
                                    )
                                } else {
                                    Text(
                                        text = uiState.currentFeed?.title ?: stringResource(R.string.status_loading),
                                        style = MaterialTheme.typography.titleLarge,
                                        fontWeight = FontWeight.SemiBold,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                        modifier = Modifier.weight(1f).padding(horizontal = 8.dp)
                                    )
                                    if (uiState.searchUrlTemplate != null) {
                                        IconButton(onClick = { showSearch = true }) {
                                            Icon(Icons.Default.Search, "Search")
                                        }
                                    }
                                }
                            }

                            if (uiState.isLoading) {
                                androidx.compose.material3.LinearProgressIndicator(
                                    modifier = Modifier.fillMaxWidth().align(Alignment.BottomCenter)
                                )
                            }
                        }
                    }

                    if (uiState.currentFeed?.entries?.isEmpty() == true && !uiState.isLoading) {
                        Box(
                            modifier = Modifier.fillMaxSize(),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(stringResource(R.string.feed_empty))
                        }
                    } else {
                        val facets = uiState.currentFeed?.facets ?: emptyList()
                        if (facets.isNotEmpty()) {
                            val groups = facets.groupBy { it.group }
                            LazyRow(
                                modifier = Modifier.fillMaxWidth()
                                    .padding(horizontal = 16.dp, vertical = 8.dp),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                groups.forEach { (groupName, groupFacets) ->
                                    item(key = groupName) {
                                        var expanded by remember { mutableStateOf(false) }
                                        val activeFacet = groupFacets.find { it.isActive }
                                            ?: groupFacets.firstOrNull()

                                        Box {
                                            FilterChip(
                                                selected = activeFacet?.isActive == true,
                                                onClick = { expanded = true },
                                                label = { Text(stringResource(R.string.filter_facet, groupName, activeFacet?.title ?: stringResource(R.string.action_select))) },
                                                trailingIcon = {
                                                    Icon(
                                                        Icons.Default.ArrowDropDown,
                                                        null
                                                    )
                                                })
                                            DropdownMenu(
                                                expanded = expanded,
                                                onDismissRequest = { expanded = false }) {
                                                groupFacets.forEach { facet ->
                                                    DropdownMenuItem(
                                                        text = { Text(facet.title) },
                                                        onClick = {
                                                            expanded = false
                                                            opdsViewModel.openFeedUrl(facet.url)
                                                        },
                                                        trailingIcon = if (facet.isActive) {
                                                            { Icon(Icons.Default.Check, null) }
                                                        } else null)
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }

                        LazyColumn(
                            modifier = Modifier.fillMaxSize(),
                            contentPadding = PaddingValues(16.dp),
                            verticalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            val entries = uiState.currentFeed?.entries ?: emptyList()
                            itemsIndexed(
                                entries,
                                key = { index, item -> "${item.id}_$index" }) { index, entry ->

                                if (index == entries.lastIndex) {
                                    LaunchedEffect(index) { opdsViewModel.loadNextPage() }
                                }

                                if (entry.isNavigation) {
                                    OpdsNavigationCard(entry) { opdsViewModel.openFeedUrl(it) }
                                } else {
                                    OpdsBookCard(
                                        entry = entry,
                                        localLibraryFiles = localLibraryFiles,
                                        downloadState = downloadingState[entry.id],
                                        coverImageLoader = coverImageLoader,
                                        onDownloadClick = { acquisition ->
                                            opdsViewModel.downloadBook(
                                                entry, acquisition, context
                                            ) { downloadedUri ->
                                                onBookDownloaded(downloadedUri, entry.title)
                                            }
                                        },
                                        onReadClick = onReadBook,
                                        onStreamClick = {
                                            onStreamBook(
                                                entry,
                                                uiState.currentCatalog
                                            )
                                        },
                                        onClick = { selectedEntry = entry })
                                }
                            }
                        }
                    }
                }
            }
        }

        // Error Banner overlay
        uiState.errorMessage?.let { error ->
            LaunchedEffect(error) {
                delay(4000)
                opdsViewModel.clearError()
            }
            Surface(
                color = MaterialTheme.colorScheme.errorContainer,
                shape = MaterialTheme.shapes.medium,
                modifier = Modifier.align(Alignment.BottomCenter).padding(16.dp)
                    .padding(bottom = 70.dp)
            ) {
                Text(
                    text = error,
                    color = MaterialTheme.colorScheme.onErrorContainer,
                    modifier = Modifier.padding(16.dp)
                )
            }
        }

        if (selectedEntry != null) {
            OpdsBookDetailsSheet(
                entry = selectedEntry!!,
                localLibraryFiles = localLibraryFiles,
                downloadState = downloadingState[selectedEntry!!.id],
                coverImageLoader = coverImageLoader,
                onDownloadFormat = { acquisition ->
                    opdsViewModel.downloadBook(selectedEntry!!, acquisition, context) { downloadedUri ->
                        onBookDownloaded(downloadedUri, selectedEntry!!.title)
                    }
                },
                onReadClick = onReadBook,
                onStreamClick = { selectedEntry?.let { onStreamBook(it, uiState.currentCatalog) } },
                onAuthorOrCategoryClick = { url, fallbackName ->
                    if (url != null) opdsViewModel.openFeedUrl(url)
                    else opdsViewModel.search(fallbackName)
                    selectedEntry = null
                },
                onDismiss = { selectedEntry = null }
            )
        }
    }

    // Dynamic Add/Edit Dialog
    if (showCatalogDialog) {
        var newTitle by remember(editingCatalog) { mutableStateOf(editingCatalog?.title ?: "") }
        var newUrl by remember(editingCatalog) { mutableStateOf(editingCatalog?.url ?: "") }
        var newUsername by remember(editingCatalog) { mutableStateOf(editingCatalog?.username ?: "") }
        var newPassword by remember(editingCatalog) { mutableStateOf(editingCatalog?.password ?: "") }

        val isEditMode = editingCatalog != null

        AlertDialog(
            onDismissRequest = {
                showCatalogDialog = false
                editingCatalog = null
            },
            title = { Text(if (isEditMode) stringResource(R.string.edit_catalog) else stringResource(R.string.add_opds_catalog)) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = newTitle,
                        onValueChange = { newTitle = it },
                        label = { Text(stringResource(R.string.catalog_name)) },
                        singleLine = true
                    )
                    OutlinedTextField(
                        value = newUrl,
                        onValueChange = { newUrl = it },
                        label = { Text(stringResource(R.string.url)) },
                        placeholder = { Text(stringResource(R.string.url_placeholder)) },
                        singleLine = true
                    )
                    Text(stringResource(R.string.auth_optional),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(top = 8.dp)
                    )
                    OutlinedTextField(
                        value = newUsername,
                        onValueChange = { newUsername = it },
                        label = { Text(stringResource(R.string.username)) },
                        singleLine = true
                    )
                    OutlinedTextField(
                        value = newPassword,
                        onValueChange = { newPassword = it },
                        label = { Text(stringResource(R.string.password)) },
                        singleLine = true,
                        visualTransformation = PasswordVisualTransformation(),
                        keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(keyboardType = KeyboardType.Password)
                    )
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        if (isEditMode) {
                            opdsViewModel.updateCatalog(editingCatalog!!.id, newTitle, newUrl, newUsername, newPassword)
                        } else {
                            opdsViewModel.addCatalog(newTitle, newUrl, newUsername, newPassword)
                        }
                        showCatalogDialog = false
                        editingCatalog = null
                    },
                    enabled = newTitle.isNotBlank() && newUrl.isNotBlank()
                ) { Text(stringResource(R.string.action_save)) }
            },
            dismissButton = {
                TextButton(onClick = {
                    showCatalogDialog = false
                    editingCatalog = null
                }) { Text(stringResource(R.string.action_cancel)) }
            }
        )
    }

    if (catalogToDelete != null) {
        val streamedBooksCount = localLibraryFiles.count { it.uriString?.contains("catalogId=${catalogToDelete!!.id}") == true }
        AlertDialog(
            onDismissRequest = { catalogToDelete = null },
            title = { Text(stringResource(R.string.delete_catalog)) },
            text = {
                Column {
                    Text(stringResource(R.string.delete_catalog_desc, catalogToDelete!!.title))
                    if (streamedBooksCount > 0) {
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            stringResource(R.string.delete_catalog_warning, streamedBooksCount),
                            color = MaterialTheme.colorScheme.error
                        )
                    }
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        opdsViewModel.removeCatalog(catalogToDelete!!.id)
                        if (streamedBooksCount > 0) {
                            onDeleteCatalogStreams(catalogToDelete!!.id)
                        }
                        catalogToDelete = null
                    },
                    colors = androidx.compose.material3.ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error)
                ) { Text(stringResource(R.string.action_delete)) }
            },
            dismissButton = {
                TextButton(onClick = { catalogToDelete = null }) { Text(stringResource(R.string.action_cancel)) }
            }
        )
    }
}

@Composable
private fun rememberOpdsCoverImageLoader(catalog: OpdsCatalog?): ImageLoader {
    val context = LocalContext.current.applicationContext
    val username = catalog?.username
    val password = catalog?.password
    val imageLoader = remember(context, username, password) {
        ImageLoader.Builder(context)
            .okHttpClient {
                OpdsRepository.sharedHttpClient.newBuilder()
                    .authenticator(OpdsRepository.OpdsAuthenticator(username, password))
                    .build()
            }
            .components {
                add(SvgDecoder.Factory())
            }
            .build()
    }
    DisposableEffect(imageLoader) {
        onDispose { imageLoader.shutdown() }
    }
    return imageLoader
}

@Composable
fun OpdsCatalogCard(catalog: OpdsCatalog, onClick: () -> Unit, onEdit: (() -> Unit)?, onDelete: (() -> Unit)?) {
    Surface(
        onClick = onClick,
        shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.colorScheme.surfaceContainer,
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(16.dp)
        ) {
            Icon(Icons.Default.FolderSpecial, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
            Spacer(modifier = Modifier.width(16.dp))
            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(catalog.title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    if (catalog.isDefault) {
                        Spacer(modifier = Modifier.width(8.dp))
                        Surface(
                            color = MaterialTheme.colorScheme.secondaryContainer,
                            shape = MaterialTheme.shapes.small
                        ) {
                            Text(stringResource(R.string.preset_label),
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSecondaryContainer,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                            )
                        }
                    }
                }
                Text(catalog.url, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            if (onEdit != null) {
                IconButton(onClick = onEdit) {
                    Icon(Icons.Default.Edit, contentDescription = stringResource(R.string.label_edit))
                }
            }
            if (onDelete != null) {
                IconButton(onClick = onDelete) {
                    Icon(Icons.Default.Delete, contentDescription = stringResource(R.string.action_remove))
                }
            }
        }
    }
}

@Composable
fun OpdsNavigationCard(entry: OpdsEntry, onClick: (String) -> Unit) {
    Surface(
        onClick = { entry.navigationUrl?.let { onClick(it) } },
        shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(16.dp)
        ) {
            Icon(Icons.Default.Folder, contentDescription = null, tint = MaterialTheme.colorScheme.secondary)
            Spacer(modifier = Modifier.width(16.dp))
            Column {
                Text(entry.title, style = MaterialTheme.typography.titleMedium)
                entry.summary?.let {
                    val cleanSummary = remember(it) { Jsoup.parse(it).text() }
                    Text(cleanSummary, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
        }
    }
}

@Composable
fun OpdsBookCard(
    entry: OpdsEntry,
    localLibraryFiles: List<RecentFileItem>,
    downloadState: OpdsDownloadState?,
    coverImageLoader: ImageLoader,
    onDownloadClick: (OpdsAcquisition) -> Unit,
    onReadClick: (RecentFileItem) -> Unit,
    onStreamClick: () -> Unit,
    onClick: () -> Unit
) {
    val libraryItem = remember(entry, localLibraryFiles) {
        SharedOpdsLocalBookMatcher.find(
            entry = entry,
            books = localLibraryFiles,
            title = { it.title },
            displayName = { it.displayName },
            path = { it.uriString }
        )
    }
    val isDownloading = downloadState?.isDownloading == true
    val progress = downloadState?.progress
    val uniqueAcquisitions = remember(entry.acquisitions) {
        entry.acquisitions.distinctBy { it.formatName }.sortedByDescending { it.priority }
    }
    var showFormatMenu by remember { mutableStateOf(false) }

    Surface(
        onClick = onClick,
        shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(modifier = Modifier.padding(12.dp)) {
            AsyncImage(
                model = entry.coverUrl,
                contentDescription = null,
                imageLoader = coverImageLoader,
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .size(width = 70.dp, height = 100.dp)
                    .clip(MaterialTheme.shapes.small)
                    .background(MaterialTheme.colorScheme.surfaceVariant)
            )
            Spacer(modifier = Modifier.width(16.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(entry.title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, maxLines = 2, overflow = TextOverflow.Ellipsis)
                entry.author?.let {
                    Text(it, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
                }
                entry.summary?.let {
                    val cleanSummary = remember(it) { Jsoup.parse(it).text() }
                    Text(cleanSummary, style = MaterialTheme.typography.bodySmall, maxLines = 2, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = 4.dp))
                }
                Spacer(modifier = Modifier.height(8.dp))

                if (libraryItem != null) {
                    androidx.compose.material3.OutlinedButton(
                        onClick = { onReadClick(libraryItem) },
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp)
                    ) {
                        Icon(Icons.Default.Check, null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(stringResource(R.string.action_read))
                    }
                } else if (isDownloading) {
                    Column(modifier = Modifier.fillMaxWidth()) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(stringResource(R.string.status_downloading), style = MaterialTheme.typography.labelMedium)
                            Spacer(modifier = Modifier.weight(1f))
                            if (progress != null) {
                                Text("${(progress * 100).toInt()}%", style = MaterialTheme.typography.labelMedium)
                            }
                        }
                        Spacer(modifier = Modifier.height(4.dp))
                        if (progress != null) {
                            androidx.compose.material3.LinearProgressIndicator(progress = { progress }, modifier = Modifier.fillMaxWidth())
                        } else {
                            androidx.compose.material3.LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                        }
                    }
                } else {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        if (entry.isStreamable) {
                            FilledTonalButton(
                                onClick = onStreamClick,
                                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp)
                            ) {
                                Icon(painterResource(id = R.drawable.play), null, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(stringResource(R.string.action_stream))
                            }
                        }

                        Box {
                            FilledTonalButton(
                                onClick = {
                                    if (uniqueAcquisitions.size == 1) {
                                        onDownloadClick(uniqueAcquisitions.first())
                                    } else if (uniqueAcquisitions.size > 1) {
                                        showFormatMenu = true
                                    }
                                },
                                enabled = uniqueAcquisitions.isNotEmpty(),
                                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp)
                            ) {
                                if (uniqueAcquisitions.isEmpty()) {
                                    Icon(Icons.Default.Info, null, modifier = Modifier.size(16.dp))
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text(stringResource(R.string.action_unavailable))
                                } else {
                                    Icon(Icons.Default.Add, null, modifier = Modifier.size(16.dp))
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text(stringResource(R.string.action_download))
                                }
                            }
                        }
                        DropdownMenu(
                            expanded = showFormatMenu,
                            onDismissRequest = { showFormatMenu = false }
                        ) {
                            uniqueAcquisitions.forEach { acq ->
                                DropdownMenuItem(
                                    text = { Text(acq.formatName) },
                                    onClick = {
                                        showFormatMenu = false
                                        onDownloadClick(acq)
                                    }
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun OpdsBookDetailsSheet(
    entry: OpdsEntry,
    localLibraryFiles: List<RecentFileItem>,
    downloadState: OpdsDownloadState?,
    coverImageLoader: ImageLoader,
    onDownloadFormat: (OpdsAcquisition) -> Unit,
    onReadClick: (RecentFileItem) -> Unit,
    onStreamClick: () -> Unit,
    onAuthorOrCategoryClick: (String?, String) -> Unit,
    onDismiss: () -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val libraryItem = remember(entry, localLibraryFiles) {
        SharedOpdsLocalBookMatcher.find(
            entry = entry,
            books = localLibraryFiles,
            title = { it.title },
            displayName = { it.displayName },
            path = { it.uriString }
        )
    }
    val isDownloading = downloadState?.isDownloading == true
    val progress = downloadState?.progress
    val uniqueAcquisitions = remember(entry.acquisitions) {
        entry.acquisitions.distinctBy { it.formatName }.sortedByDescending { it.priority }
    }

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp, vertical = 8.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                AsyncImage(
                    model = entry.coverUrl,
                    contentDescription = null,
                    imageLoader = coverImageLoader,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .size(width = 110.dp, height = 160.dp)
                        .clip(MaterialTheme.shapes.medium)
                        .background(MaterialTheme.colorScheme.surfaceVariant)
                )

                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = entry.title,
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        lineHeight = 28.sp
                    )

                    if (entry.authors.isNotEmpty()) {
                        Spacer(modifier = Modifier.height(4.dp))
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            entry.authors.forEach { author ->
                                Text(
                                    text = author.name,
                                    style = MaterialTheme.typography.titleMedium,
                                    color = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.clickable {
                                        onAuthorOrCategoryClick(author.url, author.name)
                                    }
                                )
                            }
                        }
                    }

                    entry.series?.takeIf { it.isNotBlank() }?.let { series ->
                        Spacer(modifier = Modifier.height(8.dp))
                        val seriesText = if (!entry.seriesIndex.isNullOrBlank()) "$series #${entry.seriesIndex}" else series
                        Text(
                            text = seriesText,
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.primary,
                            fontWeight = FontWeight.SemiBold,
                            modifier = Modifier.clickable {
                                onAuthorOrCategoryClick(null, series)
                            }
                        )
                    }
                }
            }

            if (libraryItem != null) {
                androidx.compose.material3.Button(
                    onClick = {
                        onDismiss()
                        onReadClick(libraryItem)
                    },
                    modifier = Modifier.fillMaxWidth(),
                    shape = MaterialTheme.shapes.medium
                ) {
                    Icon(Icons.Default.Check, contentDescription = stringResource(R.string.action_read))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(stringResource(R.string.action_read), fontWeight = FontWeight.Bold)
                }
            }

            if (isDownloading) {
                Column(modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(stringResource(R.string.status_downloading), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                        Spacer(modifier = Modifier.weight(1f))
                        if (progress != null) {
                            Text("${(progress * 100).toInt()}%", style = MaterialTheme.typography.titleMedium)
                        }
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                    if (progress != null) {
                        androidx.compose.material3.LinearProgressIndicator(progress = { progress }, modifier = Modifier.fillMaxWidth().height(8.dp))
                    } else {
                        androidx.compose.material3.LinearProgressIndicator(modifier = Modifier.fillMaxWidth().height(8.dp))
                    }
                }
            } else if (uniqueAcquisitions.isNotEmpty() || entry.isStreamable) {
                if (entry.isStreamable) {
                    androidx.compose.material3.Button(
                        onClick = {
                            onStreamClick()
                            onDismiss()
                        },
                        modifier = Modifier.fillMaxWidth(),
                        shape = MaterialTheme.shapes.medium
                    ) {
                        Icon(painterResource(id = R.drawable.play), null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(stringResource(R.string.action_stream_now), fontWeight = FontWeight.Bold)
                    }
                    Spacer(modifier = Modifier.height(16.dp))
                }

                if (uniqueAcquisitions.isNotEmpty()) {
                    Text(stringResource(R.string.download_format),
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        uniqueAcquisitions.forEach { acq ->
                            FilledTonalButton(onClick = { onDownloadFormat(acq) }) {
                                Icon(Icons.Default.Add, null, modifier = Modifier.size(18.dp))
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(acq.formatName, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }
            } else {
                Text(stringResource(R.string.no_supported_formats), color = MaterialTheme.colorScheme.error)
            }

            if (entry.categories.isNotEmpty()) {
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    entry.categories.distinct().forEach { category ->
                        Surface(
                            shape = MaterialTheme.shapes.small,
                            color = MaterialTheme.colorScheme.surfaceVariant,
                            onClick = { onAuthorOrCategoryClick(null, category) }
                        ) {
                            Text(
                                text = category,
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                            )
                        }
                    }
                }
            }

            val hasSecondaryMeta = !entry.publisher.isNullOrBlank() || !entry.published.isNullOrBlank() || !entry.language.isNullOrBlank()
            if (hasSecondaryMeta) {
                Surface(
                    shape = MaterialTheme.shapes.medium,
                    color = MaterialTheme.colorScheme.surfaceContainer,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(16.dp),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        entry.publisher?.takeIf { it.isNotBlank() }?.let {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(stringResource(R.string.publisher), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                Text(it, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium, maxLines = 2, overflow = TextOverflow.Ellipsis)
                            }
                        }
                        entry.published?.takeIf { it.isNotBlank() }?.let {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(stringResource(R.string.published), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                val cleanDate = it.substringBefore("T")
                                Text(cleanDate, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
                            }
                        }
                        entry.language?.takeIf { it.isNotBlank() }?.let {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(stringResource(R.string.language), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                Text(it.uppercase(), style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
                            }
                        }
                    }
                }
            }

            val summary = entry.summary
            if (!summary.isNullOrBlank()) {
                Text(stringResource(R.string.synopsis), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)

                val cleanSummary = remember(summary) {
                    val preProcessed = summary
                        .replace("<br>", "\n")
                        .replace("</p>", "\n\n")
                    Jsoup.parse(preProcessed).text().trim()
                }

                Text(
                    text = cleanSummary,
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurface,
                    lineHeight = 24.sp,
                    modifier = Modifier.padding(bottom = 48.dp)
                )
            } else {
                Spacer(modifier = Modifier.height(48.dp))
            }
        }
    }
}
