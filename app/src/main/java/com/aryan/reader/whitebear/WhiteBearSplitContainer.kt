package com.aryan.reader.whitebear

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * 白い熊 UI: shares the reader screen between the primary reader (pane 1) and up to two
 * parallel companion panes according to [layout]. Every divider is draggable: dragging
 * moves the split and the new ratios are reported through [onMainRatioChange] /
 * [onSubRatioChange] (and persisted by the caller). The divider carries the theme border
 * colour plus a small centred grab handle.
 */
@Composable
fun WhiteBearSplitContainer(
    layout: WhiteBearParallelLayout,
    mainRatio: Float,
    subRatio: Float,
    onMainRatioChange: (Float) -> Unit,
    onSubRatioChange: (Float) -> Unit,
    companions: List<@Composable (Modifier) -> Unit>,
    primary: @Composable (Modifier) -> Unit
) {
    val context = LocalContext.current
    val wbFrame = remember { WhiteBearUiState.get(context) }
    val lineWidth = wbFrame.borderWidth.coerceAtLeast(1f).dp
    val lineColor = MaterialTheme.colorScheme.outline

    val pane2 = companions.getOrNull(0)
    val pane3 = companions.getOrNull(1)
    val main by rememberUpdatedState(mainRatio.coerceIn(WhiteBearParallelState.RATIO_MIN, WhiteBearParallelState.RATIO_MAX))
    val sub by rememberUpdatedState(subRatio.coerceIn(WhiteBearParallelState.RATIO_MIN, WhiteBearParallelState.RATIO_MAX))

    var widthPx by remember { mutableFloatStateOf(0f) }
    var heightPx by remember { mutableFloatStateOf(0f) }
    val sizedModifier = Modifier
        .fillMaxSize()
        .onSizeChanged {
            widthPx = it.width.toFloat()
            heightPx = it.height.toFloat()
        }

    when (layout) {
        WhiteBearParallelLayout.SINGLE -> primary(Modifier.fillMaxSize())

        WhiteBearParallelLayout.TWO_ROWS -> Column(sizedModifier) {
            primary(Modifier.weight(main).fillMaxWidth())
            WbRowDivider(lineWidth, lineColor) { dy ->
                if (heightPx > 0f) onMainRatioChange(main + dy / heightPx)
            }
            (pane2 ?: primary)(Modifier.weight(1f - main).fillMaxWidth())
        }

        WhiteBearParallelLayout.TWO_COLUMNS -> Row(sizedModifier) {
            primary(Modifier.weight(main).fillMaxHeight())
            WbColumnDivider(lineWidth, lineColor) { dx ->
                if (widthPx > 0f) onMainRatioChange(main + dx / widthPx)
            }
            (pane2 ?: primary)(Modifier.weight(1f - main).fillMaxHeight())
        }

        WhiteBearParallelLayout.THREE_ROWS -> Column(sizedModifier) {
            val rest = 1f - main
            primary(Modifier.weight(main).fillMaxWidth())
            WbRowDivider(lineWidth, lineColor) { dy ->
                if (heightPx > 0f) onMainRatioChange(main + dy / heightPx)
            }
            (pane2 ?: primary)(Modifier.weight((rest * sub).coerceAtLeast(0.05f)).fillMaxWidth())
            WbRowDivider(lineWidth, lineColor) { dy ->
                val restPx = heightPx * rest
                if (restPx > 0f) onSubRatioChange(sub + dy / restPx)
            }
            (pane3 ?: primary)(Modifier.weight((rest * (1f - sub)).coerceAtLeast(0.05f)).fillMaxWidth())
        }

        WhiteBearParallelLayout.THREE_COLUMNS -> Row(sizedModifier) {
            val rest = 1f - main
            primary(Modifier.weight(main).fillMaxHeight())
            WbColumnDivider(lineWidth, lineColor) { dx ->
                if (widthPx > 0f) onMainRatioChange(main + dx / widthPx)
            }
            (pane2 ?: primary)(Modifier.weight((rest * sub).coerceAtLeast(0.05f)).fillMaxHeight())
            WbColumnDivider(lineWidth, lineColor) { dx ->
                val restPx = widthPx * rest
                if (restPx > 0f) onSubRatioChange(sub + dx / restPx)
            }
            (pane3 ?: primary)(Modifier.weight((rest * (1f - sub)).coerceAtLeast(0.05f)).fillMaxHeight())
        }

        WhiteBearParallelLayout.ONE_PLUS_TWO_RIGHT -> Row(sizedModifier) {
            primary(Modifier.weight(main).fillMaxHeight())
            WbColumnDivider(lineWidth, lineColor) { dx ->
                if (widthPx > 0f) onMainRatioChange(main + dx / widthPx)
            }
            Column(
                Modifier
                    .weight(1f - main)
                    .fillMaxHeight()
            ) {
                (pane2 ?: primary)(Modifier.weight(sub).fillMaxWidth())
                WbRowDivider(lineWidth, lineColor) { dy ->
                    if (heightPx > 0f) onSubRatioChange(sub + dy / heightPx)
                }
                (pane3 ?: primary)(Modifier.weight(1f - sub).fillMaxWidth())
            }
        }

        WhiteBearParallelLayout.TWO_PLUS_ONE_RIGHT -> Row(sizedModifier) {
            Column(
                Modifier
                    .weight(main)
                    .fillMaxHeight()
            ) {
                primary(Modifier.weight(sub).fillMaxWidth())
                WbRowDivider(lineWidth, lineColor) { dy ->
                    if (heightPx > 0f) onSubRatioChange(sub + dy / heightPx)
                }
                (pane2 ?: primary)(Modifier.weight(1f - sub).fillMaxWidth())
            }
            WbColumnDivider(lineWidth, lineColor) { dx ->
                if (widthPx > 0f) onMainRatioChange(main + dx / widthPx)
            }
            (pane3 ?: primary)(Modifier.weight(1f - main).fillMaxHeight())
        }

        WhiteBearParallelLayout.TWO_PLUS_ONE_BOTTOM -> Column(sizedModifier) {
            Row(
                Modifier
                    .weight(main)
                    .fillMaxWidth()
            ) {
                primary(Modifier.weight(sub).fillMaxHeight())
                WbColumnDivider(lineWidth, lineColor) { dx ->
                    if (widthPx > 0f) onSubRatioChange(sub + dx / widthPx)
                }
                (pane2 ?: primary)(Modifier.weight(1f - sub).fillMaxHeight())
            }
            WbRowDivider(lineWidth, lineColor) { dy ->
                if (heightPx > 0f) onMainRatioChange(main + dy / heightPx)
            }
            (pane3 ?: primary)(Modifier.weight(1f - main).fillMaxWidth())
        }

        WhiteBearParallelLayout.ONE_PLUS_TWO_BOTTOM -> Column(sizedModifier) {
            primary(Modifier.weight(main).fillMaxWidth())
            WbRowDivider(lineWidth, lineColor) { dy ->
                if (heightPx > 0f) onMainRatioChange(main + dy / heightPx)
            }
            Row(
                Modifier
                    .weight(1f - main)
                    .fillMaxWidth()
            ) {
                (pane2 ?: primary)(Modifier.weight(sub).fillMaxHeight())
                WbColumnDivider(lineWidth, lineColor) { dx ->
                    if (widthPx > 0f) onSubRatioChange(sub + dx / widthPx)
                }
                (pane3 ?: primary)(Modifier.weight(1f - sub).fillMaxHeight())
            }
        }
    }
}

/** Horizontal divider inside a Column — drags vertically. */
@Composable
private fun WbRowDivider(lineWidth: Dp, color: Color, onDragPx: (Float) -> Unit) {
    val currentOnDrag by rememberUpdatedState(onDragPx)
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(maxOf(lineWidth, 12.dp))
            .pointerInput(Unit) {
                detectDragGestures { change, amount ->
                    change.consume()
                    currentOnDrag(amount.y)
                }
            },
        contentAlignment = Alignment.Center
    ) {
        Box(
            Modifier
                .fillMaxWidth()
                .height(lineWidth)
                .background(color)
        )
        // Grab handle so the draggable border is discoverable.
        Box(
            Modifier
                .width(36.dp)
                .height(6.dp)
                .clip(RoundedCornerShape(3.dp))
                .background(color)
        )
    }
}

/** Vertical divider inside a Row — drags horizontally. */
@Composable
private fun WbColumnDivider(lineWidth: Dp, color: Color, onDragPx: (Float) -> Unit) {
    val currentOnDrag by rememberUpdatedState(onDragPx)
    Box(
        modifier = Modifier
            .fillMaxHeight()
            .width(maxOf(lineWidth, 12.dp))
            .pointerInput(Unit) {
                detectDragGestures { change, amount ->
                    change.consume()
                    currentOnDrag(amount.x)
                }
            },
        contentAlignment = Alignment.Center
    ) {
        Box(
            Modifier
                .fillMaxHeight()
                .width(lineWidth)
                .background(color)
        )
        Box(
            Modifier
                .height(36.dp)
                .width(6.dp)
                .clip(RoundedCornerShape(3.dp))
                .background(color)
        )
    }
}
