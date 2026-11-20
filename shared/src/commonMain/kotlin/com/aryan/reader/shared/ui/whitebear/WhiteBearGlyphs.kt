package com.aryan.reader.shared.ui.whitebear

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.PathParser
import androidx.compose.ui.unit.dp

/**
 * Material glyphs the shared module's own small icon set does not carry, for the menu and
 * settings rows of the black-yellow theme. Kept in this package — not in the
 * androidx.compose.material.icons one — so they can never clash with an icon library.
 * Path data: Material Icons (Apache-2.0), 24 × 24 viewport.
 */
internal object WbGlyphs {
    val AutoStories: ImageVector by lazy { glyph("AutoStories", "M19 1l-5 5v11l5 -4.5L19 1ZM1 6v14.65c0 0.25 0.25 0.5 0.5 0.5c0.1 0 0.15 -0.05 0.25 -0.05C3.1 20.45 5.05 20 6.5 20c1.95 0 4.05 0.4 5.5 1.5L12 6c-1.45 -1.1 -3.55 -1.5 -5.5 -1.5S2.45 4.9 1 6ZM23 19.5L23 6c-0.6 -0.45 -1.25 -0.75 -2 -1v13.5c-1.1 -0.35 -2.3 -0.5 -3.5 -0.5c-1.7 0 -4.15 0.65 -5.5 1.5v2c1.35 -0.85 3.8 -1.5 5.5 -1.5c1.65 0 3.35 0.3 4.75 1.05c0.1 0.05 0.15 0.05 0.25 0.05c0.25 0 0.5 -0.25 0.5 -0.5v-1.1Z") }
    val CleaningServices: ImageVector by lazy { glyph("CleaningServices", "M16 11h-1V3c0 -1.1 -0.9 -2 -2 -2h-2C9.9 1 9 1.9 9 3v8H8c-2.76 0 -5 2.24 -5 5v7h18v-7C21 13.24 18.76 11 16 11ZM19 21h-2v-3c0 -0.55 -0.45 -1 -1 -1s-1 0.45 -1 1v3h-2v-3c0 -0.55 -0.45 -1 -1 -1s-1 0.45 -1 1v3H9v-3c0 -0.55 -0.45 -1 -1 -1s-1 0.45 -1 1v3H5v-5c0 -1.65 1.35 -3 3 -3h8c1.65 0 3 1.35 3 3V21Z") }
    val Devices: ImageVector by lazy { glyph("Devices", "M4 6h18L22 4L4 4c-1.1 0 -2 0.9 -2 2v11L0 17v3h14v-3L4 17L4 6ZM23 8h-6c-0.55 0 -1 0.45 -1 1v10c0 0.55 0.45 1 1 1h6c0.55 0 1 -0.45 1 -1L24 9c0 -0.55 -0.45 -1 -1 -1ZM22 17h-4v-7h4v7Z") }
    val FormatLineSpacing: ImageVector by lazy { glyph("FormatLineSpacing", "M6 7h2.5L5 3.5L1.5 7L4 7v10L1.5 17L5 20.5L8.5 17L6 17L6 7ZM10 5v2h12L22 5L10 5ZM10 19h12v-2L10 17v2ZM10 13h12v-2L10 11v2Z") }
    val Language: ImageVector by lazy { glyph("Language", "M11.99 2C6.47 2 2 6.48 2 12s4.47 10 9.99 10C17.52 22 22 17.52 22 12S17.52 2 11.99 2ZM18.92 8h-2.95c-0.32 -1.25 -0.78 -2.45 -1.38 -3.56c1.84 0.63 3.37 1.91 4.33 3.56ZM12 4.04c0.83 1.2 1.48 2.53 1.91 3.96h-3.82c0.43 -1.43 1.08 -2.76 1.91 -3.96ZM4.26 14C4.1 13.36 4 12.69 4 12s0.1 -1.36 0.26 -2h3.38c-0.08 0.66 -0.14 1.32 -0.14 2c0 0.68 0.06 1.34 0.14 2L4.26 14ZM5.08 16h2.95c0.32 1.25 0.78 2.45 1.38 3.56c-1.84 -0.63 -3.37 -1.9 -4.33 -3.56ZM8.03 8L5.08 8c0.96 -1.66 2.49 -2.93 4.33 -3.56C8.81 5.55 8.35 6.75 8.03 8ZM12 19.96c-0.83 -1.2 -1.48 -2.53 -1.91 -3.96h3.82c-0.43 1.43 -1.08 2.76 -1.91 3.96ZM14.34 14L9.66 14c-0.09 -0.66 -0.16 -1.32 -0.16 -2c0 -0.68 0.07 -1.35 0.16 -2h4.68c0.09 0.65 0.16 1.32 0.16 2c0 0.68 -0.07 1.34 -0.16 2ZM14.59 19.56c0.6 -1.11 1.06 -2.31 1.38 -3.56h2.95c-0.96 1.65 -2.49 2.93 -4.33 3.56ZM16.36 14c0.08 -0.66 0.14 -1.32 0.14 -2c0 -0.68 -0.06 -1.34 -0.14 -2h3.38c0.16 0.64 0.26 1.31 0.26 2s-0.1 1.36 -0.26 2h-3.38Z") }
    val LightMode: ImageVector by lazy { glyph("LightMode", "M12 7c-2.76 0 -5 2.24 -5 5s2.24 5 5 5s5 -2.24 5 -5S14.76 7 12 7L12 7ZM2 13l2 0c0.55 0 1 -0.45 1 -1s-0.45 -1 -1 -1l-2 0c-0.55 0 -1 0.45 -1 1S1.45 13 2 13ZM20 13l2 0c0.55 0 1 -0.45 1 -1s-0.45 -1 -1 -1l-2 0c-0.55 0 -1 0.45 -1 1S19.45 13 20 13ZM11 2v2c0 0.55 0.45 1 1 1s1 -0.45 1 -1V2c0 -0.55 -0.45 -1 -1 -1S11 1.45 11 2ZM11 20v2c0 0.55 0.45 1 1 1s1 -0.45 1 -1v-2c0 -0.55 -0.45 -1 -1 -1C11.45 19 11 19.45 11 20ZM5.99 4.58c-0.39 -0.39 -1.03 -0.39 -1.41 0c-0.39 0.39 -0.39 1.03 0 1.41l1.06 1.06c0.39 0.39 1.03 0.39 1.41 0s0.39 -1.03 0 -1.41L5.99 4.58ZM18.36 16.95c-0.39 -0.39 -1.03 -0.39 -1.41 0c-0.39 0.39 -0.39 1.03 0 1.41l1.06 1.06c0.39 0.39 1.03 0.39 1.41 0c0.39 -0.39 0.39 -1.03 0 -1.41L18.36 16.95ZM19.42 5.99c0.39 -0.39 0.39 -1.03 0 -1.41c-0.39 -0.39 -1.03 -0.39 -1.41 0l-1.06 1.06c-0.39 0.39 -0.39 1.03 0 1.41s1.03 0.39 1.41 0L19.42 5.99ZM7.05 18.36c0.39 -0.39 0.39 -1.03 0 -1.41c-0.39 -0.39 -1.03 -0.39 -1.41 0l-1.06 1.06c-0.39 0.39 -0.39 1.03 0 1.41s1.03 0.39 1.41 0L7.05 18.36Z") }
    val Security: ImageVector by lazy { glyph("Security", "M12 1L3 5v6c0 5.55 3.84 10.74 9 12c5.16 -1.26 9 -6.45 9 -12L21 5l-9 -4ZM12 11.99h7c-0.53 4.12 -3.28 7.79 -7 8.94L12 12L5 12L5 6.3l7 -3.11v8.8Z") }
    val Sell: ImageVector by lazy { glyph("Sell", "M21.41 11.41l-8.83 -8.83C12.21 2.21 11.7 2 11.17 2H4C2.9 2 2 2.9 2 4v7.17c0 0.53 0.21 1.04 0.59 1.41l8.83 8.83c0.78 0.78 2.05 0.78 2.83 0l7.17 -7.17C22.2 13.46 22.2 12.2 21.41 11.41ZM6.5 8C5.67 8 5 7.33 5 6.5S5.67 5 6.5 5S8 5.67 8 6.5S7.33 8 6.5 8Z") }
    val Speed: ImageVector by lazy { glyph("Speed", "M20.38 8.57l-1.23 1.85a8 8 0 0 1 -0.22 7.58L5.07 18A8 8 0 0 1 15.58 6.85l1.85 -1.23A10 10 0 0 0 3.35 19a2 2 0 0 0 1.72 1h13.85a2 2 0 0 0 1.74 -1a10 10 0 0 0 -0.27 -10.44ZM10.59 15.41a2 2 0 0 0 2.83 0l5.66 -8.49l-8.49 5.66a2 2 0 0 0 0 2.83Z") }
    val SwapVert: ImageVector by lazy { glyph("SwapVert", "M16 17.01V10h-2v7.01h-3L15 21l4 -3.99h-3ZM9 3L5 6.99h3V14h2V6.99h3L9 3Z") }
    val Tab: ImageVector by lazy { glyph("Tab", "M21 3L3 3c-1.1 0 -2 0.9 -2 2v14c0 1.1 0.9 2 2 2h18c1.1 0 2 -0.9 2 -2L23 5c0 -1.1 -0.9 -2 -2 -2ZM21 19L3 19L3 5h10v4h8v10Z") }
    val Widgets: ImageVector by lazy { glyph("Widgets", "M13 13v8h8v-8h-8ZM3 21h8v-8L3 13v8ZM3 3v8h8L11 3L3 3ZM16.66 1.69L11 7.34L16.66 13l5.66 -5.66l-5.66 -5.65Z") }
    val AutoAwesome: ImageVector by lazy { glyph("AutoAwesome", "M19 9l1.25 -2.75L23 5l-2.75 -1.25L19 1l-1.25 2.75L15 5l2.75 1.25L19 9ZM11.5 9.5L9 4L6.5 9.5L1 12l5.5 2.5L9 20l2.5 -5.5L17 12l-5.5 -2.5ZM19 15l-1.25 2.75L15 19l2.75 1.25L19 23l1.25 -2.75L23 19l-2.75 -1.25L19 15Z") }

    private fun glyph(name: String, vararg paths: String): ImageVector =
        ImageVector.Builder(
            name = "WbGlyphs.$name",
            defaultWidth = 24.dp,
            defaultHeight = 24.dp,
            viewportWidth = 24f,
            viewportHeight = 24f
        ).apply {
            paths.forEach { data ->
                addPath(pathData = PathParser().parsePathString(data).toNodes(), fill = SolidColor(Color.Black))
            }
        }.build()
}
