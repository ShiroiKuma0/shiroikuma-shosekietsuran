package com.aryan.reader.whitebear

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp

/** Border thickness for fork-drawn chrome rules (e.g. the top-bar underline). 0 = none. */
val LocalWhiteBearBorderWidth = compositionLocalOf<Dp> { 0.dp }

/**
 * Wraps the app content and, when enabled, overrides the Material theme with the
 * 白い熊 black-yellow scheme built from [WhiteBearUiState]. Sits inside AppTheme so the
 * app-wide font family chosen upstream stays in effect.
 */
@Composable
fun WhiteBearTheme(content: @Composable () -> Unit) {
    val context = LocalContext.current
    val state = remember { WhiteBearUiState.get(context) }

    if (!state.enabled) {
        CompositionLocalProvider(LocalWhiteBearBorderWidth provides 0.dp, content = content)
        return
    }

    val background = Color(state.color(WhiteBearSlot.BACKGROUND))
    val surface = Color(state.color(WhiteBearSlot.SURFACE))
    val surfaceHigh = Color(state.color(WhiteBearSlot.SURFACE_HIGH))
    val text = Color(state.color(WhiteBearSlot.TEXT))
    val accent = Color(state.color(WhiteBearSlot.ACCENT))
    val border = Color(state.color(WhiteBearSlot.BORDER))
    val fontScale = state.fontScale
    val fontWeight = state.fontWeight
    val cornerRadius = state.cornerRadius
    val borderWidth = state.borderWidth

    val base = MaterialTheme.colorScheme
    val scheme = remember(base, background, surface, surfaceHigh, text, accent, border) {
        whiteBearColorScheme(base, background, surface, surfaceHigh, text, accent, border)
    }

    val baseTypography = MaterialTheme.typography
    val typography = remember(baseTypography, fontScale, fontWeight) {
        baseTypography.withWhiteBearText(fontScale, fontWeight)
    }

    val shapes = remember(cornerRadius) { whiteBearShapes(cornerRadius) }

    CompositionLocalProvider(LocalWhiteBearBorderWidth provides borderWidth.dp) {
        MaterialTheme(
            colorScheme = scheme,
            typography = typography,
            shapes = shapes,
            content = content
        )
    }
}

private fun whiteBearColorScheme(
    base: ColorScheme,
    background: Color,
    surface: Color,
    surfaceHigh: Color,
    text: Color,
    accent: Color,
    border: Color
): ColorScheme {
    val onAccent = if (accent.luminance() > 0.5f) Color.Black else Color.White
    val textSecondary = text.copy(alpha = 0.75f * text.alpha)
    return base.copy(
        primary = accent,
        onPrimary = onAccent,
        primaryContainer = surfaceHigh,
        onPrimaryContainer = accent,
        secondary = accent,
        onSecondary = onAccent,
        secondaryContainer = surfaceHigh,
        onSecondaryContainer = accent,
        tertiary = accent,
        onTertiary = onAccent,
        tertiaryContainer = surfaceHigh,
        onTertiaryContainer = accent,
        background = background,
        onBackground = text,
        surface = surface,
        onSurface = text,
        surfaceVariant = surfaceHigh,
        onSurfaceVariant = textSecondary,
        surfaceTint = accent,
        inverseSurface = text,
        inverseOnSurface = background,
        inversePrimary = background,
        outline = border,
        outlineVariant = border.copy(alpha = 0.5f * border.alpha),
        scrim = Color.Black,
        surfaceBright = surfaceHigh,
        surfaceDim = background,
        surfaceContainerLowest = background,
        surfaceContainerLow = surface,
        surfaceContainer = surfaceHigh,
        surfaceContainerHigh = surfaceHigh,
        surfaceContainerHighest = surfaceHigh
    )
}

private fun Typography.withWhiteBearText(scale: Float, weight: Int): Typography {
    if (scale == 1.0f && weight == 0) return this
    val weightOverride = if (weight in 100..900) FontWeight(weight) else null
    fun TextStyle.adjust(): TextStyle = copy(
        fontSize = fontSize.scaled(scale),
        lineHeight = lineHeight.scaled(scale),
        fontWeight = weightOverride ?: fontWeight
    )
    return copy(
        displayLarge = displayLarge.adjust(),
        displayMedium = displayMedium.adjust(),
        displaySmall = displaySmall.adjust(),
        headlineLarge = headlineLarge.adjust(),
        headlineMedium = headlineMedium.adjust(),
        headlineSmall = headlineSmall.adjust(),
        titleLarge = titleLarge.adjust(),
        titleMedium = titleMedium.adjust(),
        titleSmall = titleSmall.adjust(),
        bodyLarge = bodyLarge.adjust(),
        bodyMedium = bodyMedium.adjust(),
        bodySmall = bodySmall.adjust(),
        labelLarge = labelLarge.adjust(),
        labelMedium = labelMedium.adjust(),
        labelSmall = labelSmall.adjust()
    )
}

private fun TextUnit.scaled(scale: Float): TextUnit =
    if (this == TextUnit.Unspecified || scale == 1.0f) this else this * scale

/** Scales all M3 shape sizes from the "medium" radius; 12dp reproduces the M3 defaults. */
private fun whiteBearShapes(mediumRadiusDp: Float): Shapes {
    val r = mediumRadiusDp.coerceAtLeast(0f)
    return Shapes(
        extraSmall = RoundedCornerShape((r / 3f).dp),
        small = RoundedCornerShape((r * 2f / 3f).dp),
        medium = RoundedCornerShape(r.dp),
        large = RoundedCornerShape((r * 4f / 3f).dp),
        extraLarge = RoundedCornerShape((r * 7f / 3f).dp)
    )
}
