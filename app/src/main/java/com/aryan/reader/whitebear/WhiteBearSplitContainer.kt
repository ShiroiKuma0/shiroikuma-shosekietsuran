package com.aryan.reader.whitebear

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.VerticalDivider
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp

/**
 * 白い熊 UI: shares the reader screen between the primary reader and the parallel
 * companion pane. VERTICAL stacks them (top/bottom), HORIZONTAL puts them side by side;
 * the divider carries the theme border colour.
 */
@Composable
fun WhiteBearSplitContainer(
    mode: WhiteBearSplitMode,
    companion: @Composable (Modifier) -> Unit,
    primary: @Composable (Modifier) -> Unit
) {
    val context = LocalContext.current
    val wbFrame = remember { WhiteBearUiState.get(context) }
    val dividerWidth = wbFrame.borderWidth.coerceAtLeast(1f).dp
    when (mode) {
        WhiteBearSplitMode.NONE -> primary(Modifier.fillMaxSize())
        WhiteBearSplitMode.VERTICAL -> Column(Modifier.fillMaxSize()) {
            primary(
                Modifier
                    .weight(1f)
                    .fillMaxWidth()
            )
            HorizontalDivider(thickness = dividerWidth, color = MaterialTheme.colorScheme.outline)
            companion(
                Modifier
                    .weight(1f)
                    .fillMaxWidth()
            )
        }
        WhiteBearSplitMode.HORIZONTAL -> Row(Modifier.fillMaxSize()) {
            primary(
                Modifier
                    .weight(1f)
                    .fillMaxHeight()
            )
            VerticalDivider(thickness = dividerWidth, color = MaterialTheme.colorScheme.outline)
            companion(
                Modifier
                    .weight(1f)
                    .fillMaxHeight()
            )
        }
    }
}
