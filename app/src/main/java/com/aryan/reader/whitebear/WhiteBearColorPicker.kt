package com.aryan.reader.whitebear

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlin.math.ceil
import kotlin.math.roundToInt

private fun packArgb(a: Int, r: Int, g: Int, b: Int): Int =
    (a shl 24) or (r shl 16) or (g shl 8) or b

/**
 * RGBA color picker: one-click recent-color boxes on top, old→new preview, then four
 * R/G/B/A sliders. Every change is pushed through [onPreview] so the whole UI behind the
 * dialog restyles live; the caller restores the original value on cancel.
 */
@Composable
fun WhiteBearColorPickerDialog(
    title: String,
    initialArgb: Int,
    defaultArgb: Int,
    recentColors: List<Int>,
    onPreview: (Int) -> Unit,
    onConfirm: (Int) -> Unit,
    onCancel: () -> Unit
) {
    var a by remember { mutableIntStateOf((initialArgb ushr 24) and 0xFF) }
    var r by remember { mutableIntStateOf((initialArgb shr 16) and 0xFF) }
    var g by remember { mutableIntStateOf((initialArgb shr 8) and 0xFF) }
    var b by remember { mutableIntStateOf(initialArgb and 0xFF) }
    val current = packArgb(a, r, g, b)

    fun apply(argb: Int) {
        a = (argb ushr 24) and 0xFF
        r = (argb shr 16) and 0xFF
        g = (argb shr 8) and 0xFF
        b = argb and 0xFF
        onPreview(argb)
    }

    AlertDialog(
        onDismissRequest = onCancel,
        title = { Text(title) },
        text = {
            Column {
                if (recentColors.isNotEmpty()) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        recentColors.forEach { recent ->
                            ColorSwatchBox(
                                argb = recent,
                                size = 32.dp,
                                selected = recent == current,
                                onClick = { apply(recent) }
                            )
                        }
                    }
                    Spacer(Modifier.height(10.dp))
                }

                Row(verticalAlignment = Alignment.CenterVertically) {
                    ColorSwatchBox(argb = initialArgb, size = 30.dp, onClick = { apply(initialArgb) })
                    Text(
                        "→",
                        modifier = Modifier.padding(horizontal = 8.dp),
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    ColorSwatchBox(argb = current, size = 30.dp)
                    Spacer(Modifier.width(12.dp))
                    Text(
                        String.format("#%08X", current),
                        style = MaterialTheme.typography.bodyMedium,
                        fontFamily = FontFamily.Monospace
                    )
                }
                Spacer(Modifier.height(6.dp))

                ChannelSlider("R", r, Color(0xFFE53935)) { r = it; onPreview(packArgb(a, r, g, b)) }
                ChannelSlider("G", g, Color(0xFF43A047)) { g = it; onPreview(packArgb(a, r, g, b)) }
                ChannelSlider("B", b, Color(0xFF1E88E5)) { b = it; onPreview(packArgb(a, r, g, b)) }
                ChannelSlider("A", a, MaterialTheme.colorScheme.onSurfaceVariant) {
                    a = it; onPreview(packArgb(a, r, g, b))
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { onConfirm(current) }) { Text("Apply") }
        },
        dismissButton = {
            Row {
                TextButton(onClick = { apply(defaultArgb) }) { Text("Default") }
                TextButton(onClick = onCancel) { Text("Cancel") }
            }
        }
    )
}

@Composable
private fun ChannelSlider(
    label: String,
    value: Int,
    labelColor: Color,
    onChange: (Int) -> Unit
) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(
            label,
            modifier = Modifier.width(20.dp),
            color = labelColor,
            fontWeight = FontWeight.Bold,
            style = MaterialTheme.typography.bodyMedium
        )
        Slider(
            value = value.toFloat(),
            onValueChange = { onChange(it.roundToInt().coerceIn(0, 255)) },
            valueRange = 0f..255f,
            modifier = Modifier
                .weight(1f)
                .height(32.dp)
        )
        Text(
            value.toString(),
            modifier = Modifier.width(34.dp),
            textAlign = TextAlign.End,
            style = MaterialTheme.typography.bodySmall
        )
    }
}

/** A color box over an alpha checkerboard, with an outline; optionally clickable. */
@Composable
fun ColorSwatchBox(
    argb: Int,
    size: Dp,
    selected: Boolean = false,
    onClick: (() -> Unit)? = null
) {
    val shape = RoundedCornerShape(6.dp)
    val outline = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline
    var modifier = Modifier
        .size(size)
        .clip(shape)
        .drawBehind { drawCheckerboard() }
        .background(Color(argb))
        .border(if (selected) 2.dp else 1.dp, outline, shape)
    if (onClick != null) modifier = modifier.clickable(onClick = onClick)
    Box(modifier = modifier)
}

private fun androidx.compose.ui.graphics.drawscope.DrawScope.drawCheckerboard() {
    val cell = 5.dp.toPx()
    val cols = ceil(size.width / cell).toInt()
    val rows = ceil(size.height / cell).toInt()
    drawRect(Color.White, size = size)
    for (row in 0 until rows) {
        for (col in 0 until cols) {
            if ((row + col) % 2 == 0) continue
            drawRect(
                Color(0xFFBBBBBB),
                topLeft = Offset(col * cell, row * cell),
                size = Size(cell, cell)
            )
        }
    }
}
