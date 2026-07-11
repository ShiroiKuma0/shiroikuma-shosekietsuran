package com.aryan.reader.whitebear

import android.graphics.Bitmap
import android.view.View
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.ClipOp
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Matrix
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay
import kotlin.math.sqrt

/**
 * 白い熊 UI: page-turn animation styles for the tap page turn.
 * NONE keeps the instant jump; SLIDE smooth-scrolls in the WebView itself; the overlay
 * styles (FADE / FLIP / CURL) capture the old page as a bitmap, turn the page underneath
 * instantly, and animate the captured old page away on top. CURL folds the paper over a
 * moving diagonal crease, showing the washed-out backside of the page as it turns.
 */
enum class WhiteBearPageTurnAnimation(val label: String, val jsMode: String, val usesOverlay: Boolean) {
    NONE("None", "auto", false),
    SLIDE("Slide", "slide", false),
    FADE("Fade", "auto", true),
    FLIP("Flip", "auto", true),
    CURL("Curl", "auto", true)
}

/** Capture the currently visible viewport of a (possibly scrolled) view as a bitmap. */
fun captureWhiteBearPageBitmap(view: View): Bitmap? {
    val width = view.width
    val height = view.height
    if (width <= 0 || height <= 0) return null
    return try {
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val canvas = android.graphics.Canvas(bitmap)
        canvas.translate(-view.scrollX.toFloat(), -view.scrollY.toFloat())
        view.draw(canvas)
        bitmap
    } catch (_: Throwable) {
        null
    }
}

/**
 * Draws the captured old page above the (already turned) reader and animates it away.
 * Anchored top-left at its natural pixel size, which matches the primary pane's position
 * in every split layout. `tick` restarts the animation; `onDone` clears the bitmap.
 */
@Composable
fun WhiteBearPageTurnOverlay(
    bitmap: Bitmap?,
    tick: Long,
    style: WhiteBearPageTurnAnimation,
    forward: Boolean,
    onDone: () -> Unit,
    durationMs: Int = 420,
    modifier: Modifier = Modifier
) {
    if (bitmap == null || !style.usesOverlay) return
    val progress = remember(tick) { Animatable(0f) }
    LaunchedEffect(tick) {
        progress.animateTo(1f, tween(durationMillis = durationMs, easing = FastOutSlowInEasing))
        onDone()
    }
    val image = remember(bitmap) { bitmap.asImageBitmap() }
    val density = LocalDensity.current
    val widthDp = with(density) { bitmap.width.toDp() }
    val heightDp = with(density) { bitmap.height.toDp() }
    val sizeModifier = modifier.size(widthDp, heightDp)

    when (style) {
        WhiteBearPageTurnAnimation.FADE -> Image(
            image,
            contentDescription = null,
            modifier = sizeModifier.graphicsLayer { alpha = 1f - progress.value }
        )

        WhiteBearPageTurnAnimation.FLIP -> Image(
            image,
            contentDescription = null,
            modifier = sizeModifier.graphicsLayer {
                // Forward: the old page lifts off the right edge and folds left over the
                // spine (left edge). Backward: mirrored.
                val p = progress.value
                transformOrigin = TransformOrigin(if (forward) 0f else 1f, 0.5f)
                rotationY = if (forward) -90f * p else 90f * p
                cameraDistance = 24f
                alpha = if (p >= 0.98f) 0f else 1f
            }
        )

        WhiteBearPageTurnAnimation.CURL -> {
            val backside = remember(bitmap) { deriveWhiteBearBacksideColor(bitmap) }
            Canvas(sizeModifier) {
                drawWhiteBearPageCurl(image, progress.value, forward, backside)
            }
        }

        else -> Unit
    }
}

/**
 * Deluxe page curl: the crease line x + y = c sweeps across the page (from the
 * bottom-right corner forward, from the top-left backward). The still-flat part of the
 * old page keeps its image; the part already past the crease is folded over — drawn as
 * the page's mirrored, washed-out backside lying on the flat part — with a shadow on the
 * revealed new page and shading along the crease.
 */
/**
 * Paper-backside colour matched to the page: near-white for light pages, a lifted dark
 * grey for dark pages, so the curl looks like the same sheet of paper in every theme.
 */
private fun deriveWhiteBearBacksideColor(bitmap: Bitmap): Color {
    val w = bitmap.width
    val h = bitmap.height
    if (w <= 4 || h <= 4) return Color(0xFFF4F0E6)
    val points = listOf(
        w / 5 to h / 5,
        4 * w / 5 to h / 5,
        w / 5 to 4 * h / 5,
        4 * w / 5 to 4 * h / 5,
        w / 2 to h / 2
    )
    var r = 0
    var g = 0
    var b = 0
    for ((x, y) in points) {
        val pixel = bitmap.getPixel(x, y)
        r += (pixel shr 16) and 0xFF
        g += (pixel shr 8) and 0xFF
        b += pixel and 0xFF
    }
    r /= points.size
    g /= points.size
    b /= points.size
    val luminance = 0.299f * r + 0.587f * g + 0.114f * b
    val towardWhite = if (luminance > 128f) 0.78f else 0.16f
    return Color(
        red = (r + (255 - r) * towardWhite).toInt().coerceIn(0, 255),
        green = (g + (255 - g) * towardWhite).toInt().coerceIn(0, 255),
        blue = (b + (255 - b) * towardWhite).toInt().coerceIn(0, 255)
    )
}

private fun DrawScope.drawWhiteBearPageCurl(
    image: androidx.compose.ui.graphics.ImageBitmap,
    p: Float,
    forward: Boolean,
    backside: Color
) {
    val w = size.width
    val h = size.height
    if (p <= 0.001f) {
        drawImage(image)
        return
    }
    if (p >= 0.999f) return

    // Crease position: for forward turns it sweeps c from w+h down to 0; the flat
    // (still-showing) region is x + y <= c. Backward turns mirror everything.
    val c = if (forward) (w + h) * (1f - p) else (w + h) * p

    val flatPath = Path()
    if (forward) {
        flatPath.moveTo(0f, 0f)
        flatPath.lineTo(minOf(c, w), 0f)
        if (c > w) flatPath.lineTo(w, c - w)
        if (c > h) {
            flatPath.lineTo(c - h, h)
            flatPath.lineTo(0f, h)
        } else {
            flatPath.lineTo(0f, c)
        }
        flatPath.close()
    } else {
        flatPath.moveTo(minOf(c, w), if (c <= w) 0f else c - w)
        if (c <= w) flatPath.lineTo(w, 0f)
        flatPath.lineTo(w, h)
        if (c <= h) {
            flatPath.lineTo(0f, h)
            flatPath.lineTo(0f, c)
        } else {
            flatPath.lineTo(c - h, h)
        }
        flatPath.close()
    }

    // Crease segment endpoints (clamped to the page rect) and its normal direction.
    val creaseStart = Offset(minOf(c, w), maxOf(0f, c - w))
    val creaseEnd = Offset(maxOf(0f, c - h), minOf(c, h))
    val creaseMid = Offset((creaseStart.x + creaseEnd.x) / 2f, (creaseStart.y + creaseEnd.y) / 2f)
    val inv = 1f / sqrt(2f)
    // Unit normal pointing to the revealed (beyond-crease) side.
    val n = if (forward) Offset(inv, inv) else Offset(-inv, -inv)

    // 1. The still-flat part of the old page.
    clipPath(flatPath) {
        drawImage(image)
    }

    // 2. Drop shadow of the lifted paper on the revealed new page.
    clipPath(flatPath, clipOp = ClipOp.Difference) {
        drawRect(
            brush = Brush.linearGradient(
                0f to Color.Black.copy(alpha = 0.28f),
                1f to Color.Transparent,
                start = creaseMid,
                end = creaseMid + Offset(n.x * 70f, n.y * 70f)
            ),
            size = Size(w, h)
        )
    }

    // 3. The folded-over backside: mirror the beyond-crease strip across the crease
    //    ((x, y) → (c − y, c − x)), clipped to the flat side ∩ the mirrored page rect.
    val mirror = Matrix()
    mirror.values[Matrix.ScaleX] = 0f
    mirror.values[Matrix.SkewY] = -1f
    mirror.values[Matrix.SkewX] = -1f
    mirror.values[Matrix.ScaleY] = 0f
    mirror.values[Matrix.TranslateX] = c
    mirror.values[Matrix.TranslateY] = c

    clipPath(flatPath) {
        clipRect(left = c - h, top = c - w, right = c, bottom = c) {
            drawIntoCanvas { canvas ->
                canvas.save()
                canvas.concat(mirror)
            }
            drawImage(image)
            // Paper backside: mostly washed out, the mirrored print faintly showing through.
            drawRect(color = backside.copy(alpha = 0.88f), size = Size(w, h))
            drawIntoCanvas { canvas -> canvas.restore() }
            // Curvature shading: darker toward the crease, brightening away from it.
            drawRect(
                brush = Brush.linearGradient(
                    0f to Color.Black.copy(alpha = 0.20f),
                    0.25f to Color.Transparent,
                    0.85f to Color.Transparent,
                    1f to Color.Black.copy(alpha = 0.10f),
                    start = creaseMid,
                    end = creaseMid - Offset(n.x * 160f, n.y * 160f)
                ),
                size = Size(w, h)
            )
        }
    }
}

/**
 * Small animated demo of a page turn for the settings screen: fake old/new pages loop
 * through the chosen style at the chosen speed.
 */
@Composable
fun WhiteBearPageTurnPreview(
    style: WhiteBearPageTurnAnimation,
    durationMs: Int,
    modifier: Modifier = Modifier
) {
    val density = LocalDensity.current
    val widthDp = 110.dp
    val heightDp = 150.dp
    val widthPx = with(density) { widthDp.roundToPx() }
    val heightPx = with(density) { heightDp.roundToPx() }
    val oldPage = remember(widthPx, heightPx) { makeWhiteBearPreviewPage(widthPx, heightPx, 1) }
    val newPage = remember(widthPx, heightPx) { makeWhiteBearPreviewPage(widthPx, heightPx, 2) }

    var phase by remember { mutableStateOf(0) } // 0 = old page, 1 = turning, 2 = new page
    var tick by remember { mutableStateOf(0L) }
    val slide = remember { Animatable(0f) }
    LaunchedEffect(style, durationMs) {
        while (true) {
            phase = 0
            delay(850)
            tick += 1L
            phase = 1
            when (style) {
                WhiteBearPageTurnAnimation.NONE -> phase = 2
                WhiteBearPageTurnAnimation.SLIDE -> {
                    slide.snapTo(0f)
                    slide.animateTo(1f, tween(durationMs, easing = FastOutSlowInEasing))
                    phase = 2
                }
                else -> {
                    delay(durationMs.toLong() + 30L)
                    phase = 2
                }
            }
            delay(850)
        }
    }

    Box(
        modifier = modifier
            .size(widthDp, heightDp)
            .border(1.dp, androidx.compose.material3.MaterialTheme.colorScheme.outline)
    ) {
        Image(newPage.asImageBitmap(), contentDescription = null)
        when (phase) {
            0 -> Image(oldPage.asImageBitmap(), contentDescription = null)
            1 -> when (style) {
                WhiteBearPageTurnAnimation.SLIDE -> Image(
                    oldPage.asImageBitmap(),
                    contentDescription = null,
                    modifier = Modifier.graphicsLayer { translationY = -heightPx * slide.value }
                )
                WhiteBearPageTurnAnimation.NONE -> Unit
                else -> WhiteBearPageTurnOverlay(
                    bitmap = oldPage,
                    tick = tick,
                    style = style,
                    forward = true,
                    onDone = {},
                    durationMs = durationMs
                )
            }
            else -> Unit
        }
    }
}

/**
 * Fake book page for the preview, in the fork's black-yellow aesthetics: black paper,
 * yellow border, yellow text bars.
 */
private fun makeWhiteBearPreviewPage(width: Int, height: Int, variant: Int): Bitmap {
    val bitmap = Bitmap.createBitmap(width.coerceAtLeast(1), height.coerceAtLeast(1), Bitmap.Config.ARGB_8888)
    val canvas = android.graphics.Canvas(bitmap)
    val paint = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG)
    val yellow = 0xFFFFFF00.toInt()
    canvas.drawColor(0xFF000000.toInt())
    val borderStroke = (width * 0.03f).coerceAtLeast(2f)
    paint.style = android.graphics.Paint.Style.STROKE
    paint.strokeWidth = borderStroke
    paint.color = yellow
    canvas.drawRect(
        borderStroke / 2f,
        borderStroke / 2f,
        width - borderStroke / 2f,
        height - borderStroke / 2f,
        paint
    )
    paint.style = android.graphics.Paint.Style.FILL
    paint.color = yellow
    val margin = width * 0.12f
    val lineHeight = height * 0.045f
    var y = height * 0.11f
    var index = 0
    while (y + lineHeight < height * 0.89f) {
        // Vary the last-line width per row and shift the pattern between the two pages.
        val fraction = when ((index + variant) % 4) {
            0 -> 1.0f
            1 -> 0.92f
            2 -> 0.97f
            else -> 0.62f
        }
        canvas.drawRoundRect(
            margin,
            y,
            margin + (width - 2 * margin) * fraction,
            y + lineHeight,
            lineHeight / 2f,
            lineHeight / 2f,
            paint
        )
        y += lineHeight * 1.8f
        index++
    }
    return bitmap
}
