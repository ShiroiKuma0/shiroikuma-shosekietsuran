package com.aryan.reader.epubreader

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.aryan.reader.FileType
import com.aryan.reader.MainViewModel
import com.aryan.reader.epub.EpubBook
import com.aryan.reader.paginatedreader.Locator
import com.aryan.reader.paginatedreader.NativeVerticalReaderScreen
import com.aryan.reader.whitebear.WhiteBearGestureState
import com.aryan.reader.whitebear.WhiteBearPageTurnAnimation

/**
 * 白い熊 UI: the second book of a parallel set, rendered in its own vertical-scroll pane
 * for the same-screen split modes, with the SAME gestures as the primary reader:
 * side-third taps turn pages, centre tap toggles the toolbars, right-third vertical swipe
 * changes this pane's font size, left-third changes brightness, one-finger horizontal
 * swipe flips the two books, and centre drags scroll the text.
 */
@Composable
fun WhiteBearCompanionPane(
    bookId: String,
    viewModel: MainViewModel,
    onToggleChrome: () -> Unit,
    onBrightnessStep: (Int) -> Unit,
    onFlip: (Int) -> Unit,
    onPageTurnSound: () -> Unit,
    modifier: Modifier = Modifier,
    // Live format values from the primary reader, so the format sheet styles BOTH panes.
    fontSizeMultiplier: Float = 1.0f,
    lineHeightMultiplier: Float = 1.0f,
    // 白い熊: upstream's reader gained typography weight/spacing; mirror them in the pane.
    fontWeight: Int = 0,
    letterSpacing: Float = 0f,
    paragraphGapMultiplier: Float = 1.0f,
    imageSizeMultiplier: Float = 1.0f,
    horizontalMarginMultiplier: Float = 1.0f,
    verticalMarginMultiplier: Float = 1.0f,
    fontFamily: FontFamily = FontFamily.Default,
    textAlign: ReaderTextAlign = ReaderTextAlign.DEFAULT
) {
    val context = LocalContext.current
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val gestures = remember { WhiteBearGestureState.get(context) }
    val item = remember(uiState.rawLibraryFiles, bookId) {
        uiState.rawLibraryFiles.firstOrNull { it.bookId == bookId }
    }

    var book by remember(bookId) { mutableStateOf<EpubBook?>(null) }
    var loadFailed by remember(bookId) { mutableStateOf(false) }

    LaunchedEffect(bookId, item?.uriString) {
        val target = item
        if (target == null || target.uriString == null ||
            target.type !in setOf(FileType.EPUB, FileType.MOBI, FileType.FB2)
        ) {
            loadFailed = true
            return@LaunchedEffect
        }
        loadFailed = false
        book = viewModel.loadWhiteBearCompanionBook(target)
        if (book == null) loadFailed = true
    }

    // Scroll-delta requests consumed by the vertical reader (page turns via side taps).
    var deltaRequest by remember(bookId) { mutableStateOf<Float?>(null) }
    var deltaRequestId by remember(bookId) { mutableLongStateOf(0L) }
    var lastLocator by remember(bookId) { mutableStateOf<Locator?>(null) }
    var paneHeightPx by remember { mutableFloatStateOf(0f) }
    var paneWidthPx by remember { mutableFloatStateOf(0f) }

    DisposableEffect(bookId) {
        onDispose {
            val current = lastLocator
            if (current != null && item != null) {
                viewModel.saveWhiteBearCompanionPosition(item, current)
            }
        }
    }

    fun turnPage(dir: Int) {
        if (paneHeightPx > 0f) {
            deltaRequest = dir * paneHeightPx * (gestures.pageTurnStepPercent.coerceIn(50, 100) / 100f)
            deltaRequestId += 1L
            onPageTurnSound()
        }
    }

    // Same gesture layer as the primary pane, zones from this pane's own size.
    val gestureModifier = Modifier.pointerInput(bookId, gestures.enabled, gestures.tapToTurnPages) {
        val stepPx = 48.dp.toPx()
        val flipThresholdPx = 56.dp.toPx()
        val slop = viewConfiguration.touchSlop
        awaitEachGesture {
            paneWidthPx = size.width.toFloat()
            paneHeightPx = size.height.toFloat()
            val third = paneWidthPx / 3f
            val down = awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
            val zone = when {
                third <= 0f -> 0
                down.position.x < third -> -1
                down.position.x > paneWidthPx - third -> 1
                else -> 0
            }
            var verticalDrag = false
            var bailed = false
            var accumulated = 0f
            var lastY = down.position.y
            while (true) {
                val event = awaitPointerEvent(PointerEventPass.Initial)
                val change = event.changes.firstOrNull { it.id == down.id } ?: break
                if (!change.pressed) {
                    val quickTap = (change.uptimeMillis - down.uptimeMillis) < 350L
                    if (!verticalDrag && !bailed && quickTap) {
                        if (zone != 0 && gestures.enabled && gestures.tapToTurnPages) {
                            change.consume()
                            turnPage(if (zone < 0) -1 else 1)
                        } else if (zone == 0) {
                            change.consume()
                            onToggleChrome()
                        }
                    }
                    break
                }
                val dx = change.position.x - down.position.x
                val dy = change.position.y - down.position.y
                if (!verticalDrag && !bailed) {
                    if (kotlin.math.abs(dx) > slop && kotlin.math.abs(dx) >= kotlin.math.abs(dy)) {
                        // One-finger horizontal swipe flips the two books.
                        change.consume()
                        if (kotlin.math.abs(dx) > flipThresholdPx) {
                            bailed = true
                            onFlip(if (dx < 0) 1 else -1)
                        }
                    } else if (kotlin.math.abs(dy) > slop) {
                        val featureOn = gestures.enabled && (
                            (zone == 1 && gestures.rightSwipeFontSize) ||
                                (zone == -1 && gestures.leftSwipeBrightness)
                            )
                        if (featureOn) {
                            verticalDrag = true
                            lastY = change.position.y
                        } else {
                            bailed = true // centre (or feature off) → the list scrolls
                        }
                    }
                }
                if (verticalDrag) {
                    change.consume()
                    accumulated += change.position.y - lastY
                    lastY = change.position.y
                    while (accumulated <= -stepPx) {
                        accumulated += stepPx
                        if (zone == 1) {
                            gestures.updateCompanionFontScale(gestures.companionFontScale + 0.1f)
                        } else {
                            onBrightnessStep(1)
                        }
                    }
                    while (accumulated >= stepPx) {
                        accumulated -= stepPx
                        if (zone == 1) {
                            gestures.updateCompanionFontScale(gestures.companionFontScale - 0.1f)
                        } else {
                            onBrightnessStep(-1)
                        }
                    }
                }
            }
        }
    }

    Box(
        modifier = modifier
            .background(MaterialTheme.colorScheme.background)
            .then(gestureModifier),
        contentAlignment = Alignment.Center
    ) {
        val loadedBook = book
        when {
            loadFailed -> Text(
                if (item?.type == FileType.PDF) {
                    "PDF companion view is not supported yet — flip to it instead."
                } else {
                    "This book cannot be shown in the split pane."
                },
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(24.dp)
            )
            loadedBook == null -> CircularProgressIndicator()
            else -> {
                val initialLocator = remember(item) {
                    item?.locatorBlockIndex?.let { block ->
                        Locator(
                            chapterIndex = item.lastChapterIndex ?: 0,
                            blockIndex = block,
                            charOffset = item.locatorCharOffset ?: 0
                        )
                    }
                }
                NativeVerticalReaderScreen(
                    modifier = Modifier.fillMaxSize(),
                    book = loadedBook,
                    bookId = bookId,
                    isDarkTheme = true,
                    effectiveBg = MaterialTheme.colorScheme.background,
                    effectiveText = MaterialTheme.colorScheme.onBackground,
                    searchQuery = "",
                    fontSizeMultiplier = fontSizeMultiplier * gestures.companionFontScale,
                    lineHeightMultiplier = lineHeightMultiplier,
                    paragraphGapMultiplier = paragraphGapMultiplier,
                    imageSizeMultiplier = imageSizeMultiplier,
                    horizontalMarginMultiplier = horizontalMarginMultiplier,
                    verticalMarginMultiplier = verticalMarginMultiplier,
                    fontFamily = fontFamily,
                    fontWeight = fontWeight,
                    letterSpacing = letterSpacing,
                    textAlign = textAlign,
                    ttsHighlightInfo = null,
                    initialLocator = initialLocator,
                    scrollDeltaRequest = deltaRequest,
                    scrollDeltaRequestId = deltaRequestId,
                    // Any non-instant page-turn style animates the companion's page turns
                    // as a smooth scroll (the bitmap overlay styles apply to the primary).
                    scrollDeltaRequestAnimated = gestures.pageTurnAnimation != WhiteBearPageTurnAnimation.NONE,
                    onScrollDeltaConsumed = { deltaRequest = null },
                    onPaginatorReady = {},
                    onVisiblePageChanged = { _, _, locator ->
                        if (locator != null) lastLocator = locator
                    },
                    onTap = {},
                    isProUser = false,
                    onShowDictionaryUpsellDialog = {},
                    onWordSelectedForAiDefinition = {},
                    onTranslate = {},
                    onSearch = {},
                    onStartTtsFromSelection = { _, _, _ -> },
                    onNoteRequested = {},
                    userHighlights = emptyList(),
                    onHighlightCreated = { _, _, _, _, _ -> },
                    onHighlightDeleted = {},
                    activeHighlightPalette = listOf(0xFFFFEB3B.toInt()),
                    onUpdatePalette = { _, _ -> }
                )
            }
        }
    }
}
