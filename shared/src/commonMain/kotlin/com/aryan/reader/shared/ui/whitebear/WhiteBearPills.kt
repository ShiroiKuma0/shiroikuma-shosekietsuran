package com.aryan.reader.shared.ui.whitebear

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ButtonColors
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ButtonElevation
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ProvideTextStyle
import androidx.compose.material3.SwitchColors
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

/**
 * The black-yellow pill and switch of every 白い熊 app (black-yellow-theme skill; numbers copied
 * from 自由作業盤 `Pills.kt` and 音楽 `SkSwitch.kt`).
 *
 * **Never filled.** The MAIN action of a block is a thick 3 dp frame with bold text ([Button]);
 * every other action — what Material calls an outlined or a text button — is a thin 1.25 dp
 * frame with normal text. All three are drop-in replacements: a file in this package gets them
 * by importing these instead of Material's — in the app and in the shared screens alike. A caller's `colors` lends its colour to frame and text.
 */
private val PILL_SHAPE = RoundedCornerShape(percent = 50)
private val PILL_STROKE_MAIN = 3.dp
private val PILL_STROKE = 1.25.dp
private const val DISABLED_ALPHA = 0.38f

/** The main action: thick accent frame, bold text, nothing filled. */
@Composable
fun Button(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    @Suppress("UNUSED_PARAMETER") shape: Shape = PILL_SHAPE,
    colors: ButtonColors? = null,
    @Suppress("UNUSED_PARAMETER") elevation: ButtonElevation? = null,
    @Suppress("UNUSED_PARAMETER") border: BorderStroke? = null,
    contentPadding: PaddingValues = ButtonDefaults.ContentPadding,
    @Suppress("UNUSED_PARAMETER") interactionSource: MutableInteractionSource? = null,
    content: @Composable RowScope.() -> Unit,
) = Pill(onClick, modifier, enabled, colors, contentPadding, main = true, content)

/** Every other action: thin accent frame, normal text. */
@Composable
fun OutlinedButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    @Suppress("UNUSED_PARAMETER") shape: Shape = PILL_SHAPE,
    colors: ButtonColors? = null,
    @Suppress("UNUSED_PARAMETER") elevation: ButtonElevation? = null,
    @Suppress("UNUSED_PARAMETER") border: BorderStroke? = null,
    contentPadding: PaddingValues = ButtonDefaults.ContentPadding,
    @Suppress("UNUSED_PARAMETER") interactionSource: MutableInteractionSource? = null,
    content: @Composable RowScope.() -> Unit,
) = Pill(onClick, modifier, enabled, colors, contentPadding, main = false, content)

/** A bare-text button reads as a note, not as something to press: it is a regular pill too. */
@Composable
fun TextButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    @Suppress("UNUSED_PARAMETER") shape: Shape = PILL_SHAPE,
    colors: ButtonColors? = null,
    @Suppress("UNUSED_PARAMETER") elevation: ButtonElevation? = null,
    @Suppress("UNUSED_PARAMETER") border: BorderStroke? = null,
    contentPadding: PaddingValues = ButtonDefaults.TextButtonContentPadding,
    @Suppress("UNUSED_PARAMETER") interactionSource: MutableInteractionSource? = null,
    content: @Composable RowScope.() -> Unit,
) = Pill(onClick, modifier, enabled, colors, contentPadding, main = false, content)

/** A tonal fill is still a fill: a regular pill. */
@Composable
fun FilledTonalButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    @Suppress("UNUSED_PARAMETER") shape: Shape = PILL_SHAPE,
    colors: ButtonColors? = null,
    @Suppress("UNUSED_PARAMETER") elevation: ButtonElevation? = null,
    @Suppress("UNUSED_PARAMETER") border: BorderStroke? = null,
    contentPadding: PaddingValues = ButtonDefaults.ContentPadding,
    @Suppress("UNUSED_PARAMETER") interactionSource: MutableInteractionSource? = null,
    content: @Composable RowScope.() -> Unit,
) = Pill(onClick, modifier, enabled, colors, contentPadding, main = false, content)

@Composable
private fun Pill(
    onClick: () -> Unit,
    modifier: Modifier,
    enabled: Boolean,
    colors: ButtonColors?,
    contentPadding: PaddingValues,
    main: Boolean,
    content: @Composable RowScope.() -> Unit,
) {
    // A filled caller's container colour was its accent; an outlined caller's content colour was.
    val accent = colors?.let { c ->
        c.containerColor.takeIf { it != Color.Transparent && it.alpha > 0f } ?: c.contentColor
    } ?: MaterialTheme.colorScheme.primary
    val ink = if (enabled) accent else accent.copy(alpha = DISABLED_ALPHA)
    androidx.compose.material3.OutlinedButton(
        onClick = onClick,
        modifier = modifier,
        enabled = enabled,
        shape = PILL_SHAPE,
        colors = ButtonDefaults.outlinedButtonColors(
            containerColor = Color.Transparent,
            contentColor = accent,
            disabledContainerColor = Color.Transparent,
            disabledContentColor = accent.copy(alpha = DISABLED_ALPHA),
        ),
        border = BorderStroke(if (main) PILL_STROKE_MAIN else PILL_STROKE, ink),
        contentPadding = contentPadding,
    ) {
        if (main) {
            ProvideTextStyle(LocalTextStyle.current.copy(fontWeight = FontWeight.Bold)) { content() }
        } else {
            content()
        }
    }
}

/** Colour of a switch that is off; the app's theme provides its settable role. */
val LocalWhiteBearSwitchOff = compositionLocalOf { Color(0xFF8C8C00) }

/** Dim gold of explanations and of the rule down their left edge; provided by the app's theme. */
val LocalWhiteBearNote = compositionLocalOf { Color(0xFFC8C800) }

/**
 * The switch: an outlined pill with the background showing through and a solid dot — right and
 * accent when on, left and the darker [LocalWhiteBearSwitchOff] yellow when off. Nothing is
 * ever filled, and both states share one shape. 52 × 28 dp, 2 dp frame, dot 46 % of the height.
 */
@Composable
fun Switch(
    checked: Boolean,
    onCheckedChange: ((Boolean) -> Unit)?,
    modifier: Modifier = Modifier,
    @Suppress("UNUSED_PARAMETER") thumbContent: (@Composable () -> Unit)? = null,
    enabled: Boolean = true,
    @Suppress("UNUSED_PARAMETER") colors: SwitchColors? = null,
    @Suppress("UNUSED_PARAMETER") interactionSource: MutableInteractionSource? = null,
) {
    val on = MaterialTheme.colorScheme.primary
    val off = LocalWhiteBearSwitchOff.current
    val base = if (checked) on else off
    val ink = if (enabled) base else base.copy(alpha = DISABLED_ALPHA)
    val position by animateFloatAsState(
        targetValue = if (checked) 1f else 0f,
        animationSpec = tween(durationMillis = 180, easing = FastOutSlowInEasing),
        label = "whiteBearSwitch"
    )
    val toggle = if (onCheckedChange != null) {
        Modifier
            .clip(PILL_SHAPE)
            .toggleable(value = checked, enabled = enabled, role = Role.Switch, onValueChange = onCheckedChange)
    } else {
        Modifier
    }
    WhiteBearSwitchTrack(position, ink, modifier.then(toggle).size(width = 52.dp, height = 28.dp))
}

/** The outlined track and its solid dot; `position` runs 0 (off, left) to 1 (on, right). */
@Composable
internal fun WhiteBearSwitchTrack(position: Float, ink: Color, modifier: Modifier) {
    Canvas(modifier) {
        val stroke = 2.dp.toPx()
        drawRoundRect(
            color = ink,
            topLeft = Offset(stroke / 2, stroke / 2),
            size = Size(size.width - stroke, size.height - stroke),
            cornerRadius = CornerRadius((size.height - stroke) / 2),
            style = Stroke(width = stroke)
        )
        val radius = size.height * 0.46f / 2
        // The dot keeps the same gap from the frame all round, at either end.
        val inset = size.height / 2
        val x = inset + (size.width - 2 * inset) * position
        drawCircle(color = ink, radius = radius, center = Offset(x, size.height / 2))
    }
}
