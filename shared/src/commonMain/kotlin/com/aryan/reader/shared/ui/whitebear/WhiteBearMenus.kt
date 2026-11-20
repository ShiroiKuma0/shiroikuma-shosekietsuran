package com.aryan.reader.shared.ui.whitebear

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Book
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.BugReport
import androidx.compose.material.icons.filled.Restore
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Smartphone
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.FilterList
import androidx.compose.material.icons.filled.Fonts
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Translate
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Tag
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material.icons.filled.CollectionsBookmark
import androidx.compose.material.icons.filled.TextFields
import androidx.compose.material.icons.filled.TouchApp
import androidx.compose.material.icons.filled.ImportExport
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material.icons.filled.Ai
import androidx.compose.material.icons.filled.FileOpen
import androidx.compose.material3.DividerDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MenuItemColors
import androidx.compose.material3.ProvideTextStyle
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.PopupProperties
import com.aryan.reader.shared.ui.SharedMobileHomeOverflowAction

/**
 * The black-yellow MENU of every 白い熊 app (black-yellow-theme skill, “Menus”): every action
 * row is an outlined pill spanning the menu width with its icon inside at the left; a row that
 * opens something ends in a ›; a destructive row lends its red to frame and label; a toggle row
 * stays plain and carries the outlined switch; the icon of every row is coloured by what the row
 * does. Drop-in replacements — a file gets them by importing these instead of Material's.
 */

/** What a menu row does; it decides the colour of the row's icon. */
enum class MenuRole { View, Connect, Mark, Contact, Share, Block, Delete }

/** The settable role colours of menu icons; the app's theme provides its own. */
data class WhiteBearMenuColors(
    val connect: Color = Color(0xFF00FF00),
    val mark: Color = Color(0xFF4D94FF),
    val contact: Color = Color(0xFF00FFFF),
    val share: Color = Color(0xFFFF8000),
    val block: Color = Color(0xFFFF00FF),
    val delete: Color = Color(0xFFFF0000),
)

val LocalWhiteBearMenuColors = compositionLocalOf { WhiteBearMenuColors() }

/** True inside a [DropdownMenu]: dividers there are the clear darker-accent line. */
private val LocalWhiteBearInMenu = compositionLocalOf { false }

@Composable
fun MenuRole.color(): Color {
    val colors = LocalWhiteBearMenuColors.current
    return when (this) {
        MenuRole.View -> MaterialTheme.colorScheme.primary
        MenuRole.Connect -> colors.connect
        MenuRole.Mark -> colors.mark
        MenuRole.Contact -> colors.contact
        MenuRole.Share -> colors.share
        MenuRole.Block -> colors.block
        MenuRole.Delete -> colors.delete
    }
}

/** Icons for rows that bring none of their own, so a call site needs a single import. */
object WbMenuIcons {
    val Add = Icons.Default.Add
    val Bookmark = Icons.Default.Bookmark
    val Brightness get() = WbGlyphs.LightMode
    val Bug = Icons.Default.BugReport
    val Clean get() = WbGlyphs.CleaningServices
    val Close = Icons.Default.Close
    val Delete = Icons.Default.Delete
    val Devices get() = WbGlyphs.Devices
    val Download = Icons.Default.Download
    val Edit = Icons.Default.Edit
    val Filter = Icons.Default.FilterList
    val Info = Icons.Default.Info
    val Language get() = WbGlyphs.Language
    val LineSpacing get() = WbGlyphs.FormatLineSpacing
    val Mode = Icons.Default.Tune
    val OpenFile = Icons.Default.FileOpen
    val PageTurn get() = WbGlyphs.AutoStories
    val Play = Icons.Default.PlayArrow
    val Refresh = Icons.Default.Refresh
    val Save = Icons.Default.Save
    val Security get() = WbGlyphs.Security
    val Sparkle get() = WbGlyphs.AutoAwesome
    val Speed get() = WbGlyphs.Speed
    val Sync = Icons.Default.Sync
    val Tabs get() = WbGlyphs.Tab
    val Tag get() = WbGlyphs.Sell
    val Text = Icons.Default.TextFields
    val Tools get() = WbGlyphs.Widgets
    val Touch = Icons.Default.TouchApp
    val Translate = Icons.Default.Translate
    val VolumeKeys get() = WbGlyphs.SwapVert
}

private val MENU_PILL = RoundedCornerShape(percent = 50)
private val MENU_STROKE = 1.25.dp
private val MENU_STROKE_MAIN = 3.dp
private const val MENU_DISABLED_ALPHA = 0.38f

/** Material's menu, marking its content so rows and dividers take the menu look. */
@Composable
fun DropdownMenu(
    expanded: Boolean,
    onDismissRequest: () -> Unit,
    modifier: Modifier = Modifier,
    offset: DpOffset = DpOffset(0.dp, 0.dp),
    scrollState: ScrollState = rememberScrollState(),
    properties: PopupProperties = PopupProperties(focusable = true),
    shape: Shape? = null,
    containerColor: Color? = null,
    border: BorderStroke? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    androidx.compose.material3.DropdownMenu(
        expanded = expanded,
        onDismissRequest = onDismissRequest,
        modifier = modifier,
        offset = offset,
        scrollState = scrollState,
        properties = properties,
        shape = shape ?: androidx.compose.material3.MenuDefaults.shape,
        containerColor = containerColor ?: androidx.compose.material3.MenuDefaults.containerColor,
        border = border,
    ) {
        CompositionLocalProvider(LocalWhiteBearInMenu provides true) { content() }
    }
}

/**
 * A menu row.
 *
 * @param icon the row's icon when the caller passes no [leadingIcon] slot.
 * @param role what the row does — colours the icon; [MenuRole.Delete] colours frame and label too.
 * @param opens true when the row opens a screen, dialog, picker or submenu: a › at the right.
 * @param checked non-null makes it a toggle row: plain, with the outlined switch at the right.
 * @param header true for a line that is only read (a title, a note): plain text, unframed.
 * @param iconColumn keeps the icon column although this row has no icon, so labels line up.
 * @param selected the current destination of a navigation list: the thick 3 dp frame, bold.
 */
@Composable
fun DropdownMenuItem(
    text: @Composable () -> Unit,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    leadingIcon: (@Composable () -> Unit)? = null,
    trailingIcon: (@Composable () -> Unit)? = null,
    enabled: Boolean = true,
    @Suppress("UNUSED_PARAMETER") colors: MenuItemColors? = null,
    @Suppress("UNUSED_PARAMETER") contentPadding: PaddingValues? = null,
    @Suppress("UNUSED_PARAMETER") interactionSource: MutableInteractionSource? = null,
    icon: ImageVector? = null,
    role: MenuRole = MenuRole.View,
    opens: Boolean = false,
    checked: Boolean? = null,
    header: Boolean = false,
    iconColumn: Boolean = false,
    selected: Boolean = false,
) {
    val accent = MaterialTheme.colorScheme.primary
    val roleColor = role.color()
    val rowColor = if (role == MenuRole.Delete) roleColor else accent
    val alpha = if (enabled || header) 1f else MENU_DISABLED_ALPHA
    val ink = rowColor.copy(alpha = rowColor.alpha * alpha)
    val iconInk = roleColor.copy(alpha = roleColor.alpha * alpha)
    val hasIcon = leadingIcon != null || icon != null || iconColumn

    val leading: @Composable () -> Unit = {
        if (hasIcon) {
            Box(Modifier.size(24.dp), contentAlignment = Alignment.Center) {
                CompositionLocalProvider(LocalContentColor provides iconInk) {
                    when {
                        leadingIcon != null -> leadingIcon()
                        icon != null -> Icon(icon, contentDescription = null, modifier = Modifier.size(20.dp))
                    }
                }
            }
            Spacer(Modifier.width(12.dp))
        }
    }
    val label: @Composable (Modifier) -> Unit = { labelModifier ->
        Box(labelModifier) {
            CompositionLocalProvider(LocalContentColor provides ink) {
                ProvideTextStyle(
                    MaterialTheme.typography.labelLarge.merge(
                        LocalTextStyle.current.copy(
                            color = ink,
                            fontWeight = if (selected) FontWeight.Bold else null
                        )
                    )
                ) { text() }
            }
        }
    }
    // A menu sizes itself to its widest row; anywhere else the row takes the width it is given.
    val bounds = if (LocalWhiteBearInMenu.current) {
        modifier.fillMaxWidth().sizeIn(minWidth = 112.dp, maxWidth = 320.dp)
    } else {
        modifier.fillMaxWidth()
    }

    when {
        header -> Row(
            bounds.heightIn(min = 36.dp).padding(start = 22.dp, end = 20.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            leading()
            label(Modifier.weight(1f))
        }

        checked != null -> {
            val on = accent
            val off = LocalWhiteBearSwitchOff.current
            val base = if (checked) on else off
            val position by animateFloatAsState(
                targetValue = if (checked) 1f else 0f,
                animationSpec = tween(durationMillis = 180, easing = FastOutSlowInEasing),
                label = "whiteBearMenuSwitch"
            )
            Row(
                bounds
                    .heightIn(min = 36.dp)
                    .clickable(enabled = enabled, role = Role.Switch, onClick = onClick)
                    .padding(start = 22.dp, end = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                leading()
                label(Modifier.weight(1f))
                Spacer(Modifier.width(8.dp))
                WhiteBearSwitchTrack(
                    position = position,
                    ink = base.copy(alpha = base.alpha * alpha),
                    modifier = Modifier.size(width = 44.dp, height = 24.dp)
                )
            }
        }

        else -> Row(
            bounds
                .padding(horizontal = 8.dp, vertical = 3.dp)
                .heightIn(min = 42.dp)
                .clip(MENU_PILL)
                .border(if (selected) MENU_STROKE_MAIN else MENU_STROKE, ink, MENU_PILL)
                .clickable(enabled = enabled, role = Role.Button, onClick = onClick)
                .padding(start = 14.dp, end = 14.dp, top = 4.dp, bottom = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            leading()
            label(Modifier.weight(1f))
            if (trailingIcon != null) {
                Spacer(Modifier.width(8.dp))
                CompositionLocalProvider(LocalContentColor provides ink) { trailingIcon() }
            }
            if (opens) {
                Spacer(Modifier.width(8.dp))
                Text("›", color = ink, style = MaterialTheme.typography.titleMedium)
            }
        }
    }
}

/**
 * Material's divider everywhere but inside a menu, where it is the group divider: a clear 1 dp
 * line in an opaque darker accent (70 % of the way from the background to the accent), inset so
 * its ends meet the pill edges.
 */
@Composable
fun HorizontalDivider(
    modifier: Modifier = Modifier,
    thickness: Dp = DividerDefaults.Thickness,
    color: Color = DividerDefaults.color,
) {
    if (LocalWhiteBearInMenu.current) {
        androidx.compose.material3.HorizontalDivider(
            modifier = modifier.padding(horizontal = 8.dp, vertical = 3.dp),
            thickness = 1.dp,
            color = lerp(MaterialTheme.colorScheme.surface, MaterialTheme.colorScheme.primary, 0.7f)
        )
    } else {
        androidx.compose.material3.HorizontalDivider(modifier, thickness, color)
    }
}

/** Icon, role and kind of each row of the home screen's ⋮ menu. */
data class WhiteBearMenuSpec(
    val icon: ImageVector,
    val role: MenuRole = MenuRole.View,
    val opens: Boolean = false,
    val toggle: Boolean = false,
)

fun SharedMobileHomeOverflowAction.whiteBearMenuSpec(): WhiteBearMenuSpec = when (this) {
    SharedMobileHomeOverflowAction.ABOUT -> WhiteBearMenuSpec(WbMenuIcons.Info, opens = true)
    SharedMobileHomeOverflowAction.TABS_TOGGLE -> WhiteBearMenuSpec(WbMenuIcons.Tabs, toggle = true)
    SharedMobileHomeOverflowAction.SCREEN_CAPTURE_PROTECTION ->
        WhiteBearMenuSpec(WbMenuIcons.Security, MenuRole.Block, toggle = true)
    SharedMobileHomeOverflowAction.EXTERNAL_FILE_BEHAVIOR -> WhiteBearMenuSpec(WbMenuIcons.OpenFile, opens = true)
    SharedMobileHomeOverflowAction.STRICT_FILE_FILTER -> WhiteBearMenuSpec(WbMenuIcons.Filter, toggle = true)
    SharedMobileHomeOverflowAction.PDF_FILENAME_DISPLAY_NAME -> WhiteBearMenuSpec(WbMenuIcons.Text, toggle = true)
    SharedMobileHomeOverflowAction.LANGUAGE -> WhiteBearMenuSpec(WbMenuIcons.Language, opens = true)
    SharedMobileHomeOverflowAction.TOGGLE_READER_AI -> WhiteBearMenuSpec(WbMenuIcons.Sparkle, toggle = true)
    SharedMobileHomeOverflowAction.CLEAR_BOOK_CACHE -> WhiteBearMenuSpec(WbMenuIcons.Clean, MenuRole.Delete)
    SharedMobileHomeOverflowAction.CLEAR_REFLOW_CACHE -> WhiteBearMenuSpec(WbMenuIcons.Clean, MenuRole.Delete)
    SharedMobileHomeOverflowAction.TEST_PANEL_DETECTION -> WhiteBearMenuSpec(WbMenuIcons.Bug)
    SharedMobileHomeOverflowAction.TEST_SPEECH_BUBBLE_DETECTION -> WhiteBearMenuSpec(WbMenuIcons.Bug)
    SharedMobileHomeOverflowAction.EXPORT_LOGS -> WhiteBearMenuSpec(WbMenuIcons.Save, MenuRole.Share)
    SharedMobileHomeOverflowAction.SHOW_FPS_OVERLAY -> WhiteBearMenuSpec(WbMenuIcons.Speed, toggle = true)
    SharedMobileHomeOverflowAction.DEVICE_MANAGEMENT -> WhiteBearMenuSpec(WbMenuIcons.Devices, MenuRole.Contact, opens = true)
    SharedMobileHomeOverflowAction.CLEAR_CLOUD_LOCAL_DATA -> WhiteBearMenuSpec(WbMenuIcons.Delete, MenuRole.Delete)
}

/**
 * A row of a navigation drawer: the same full-width outlined pill as a menu row, its icon
 * coloured by what the row does, a › because it opens a screen, and the thick frame on the
 * destination that is showing.
 */
@Composable
fun NavigationDrawerItem(
    label: @Composable () -> Unit,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    icon: (@Composable () -> Unit)? = null,
    badge: (@Composable () -> Unit)? = null,
    @Suppress("UNUSED_PARAMETER") shape: Shape? = null,
    @Suppress("UNUSED_PARAMETER") colors: androidx.compose.material3.NavigationDrawerItemColors? = null,
    @Suppress("UNUSED_PARAMETER") interactionSource: MutableInteractionSource? = null,
    role: MenuRole = MenuRole.View,
) = DropdownMenuItem(
    text = label,
    onClick = onClick,
    modifier = modifier,
    leadingIcon = icon,
    trailingIcon = badge,
    role = role,
    opens = badge == null,
    iconColumn = true,
    selected = selected,
)

/** The floating action is the main action of its screen: a thick outlined pill, never a slab. */
@Composable
fun FloatingActionButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    shape: Shape = androidx.compose.material3.FloatingActionButtonDefaults.shape,
    containerColor: Color? = null,
    contentColor: Color? = null,
    @Suppress("UNUSED_PARAMETER") elevation: androidx.compose.material3.FloatingActionButtonElevation? = null,
    interactionSource: MutableInteractionSource? = null,
    content: @Composable () -> Unit,
) {
    val accent = whiteBearFabAccent(containerColor, contentColor)
    androidx.compose.material3.FloatingActionButton(
        onClick = onClick,
        modifier = modifier.border(MENU_STROKE_MAIN, accent, shape),
        shape = shape,
        containerColor = MaterialTheme.colorScheme.background,
        contentColor = accent,
        elevation = androidx.compose.material3.FloatingActionButtonDefaults.elevation(0.dp, 0.dp, 0.dp, 0.dp),
        interactionSource = interactionSource,
        content = content,
    )
}

@Composable
fun ExtendedFloatingActionButton(
    text: @Composable () -> Unit,
    icon: @Composable () -> Unit,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    expanded: Boolean = true,
    shape: Shape = MENU_PILL,
    containerColor: Color? = null,
    contentColor: Color? = null,
    @Suppress("UNUSED_PARAMETER") elevation: androidx.compose.material3.FloatingActionButtonElevation? = null,
    interactionSource: MutableInteractionSource? = null,
) {
    val accent = whiteBearFabAccent(containerColor, contentColor)
    androidx.compose.material3.ExtendedFloatingActionButton(
        text = { ProvideTextStyle(LocalTextStyle.current.copy(fontWeight = FontWeight.Bold)) { text() } },
        icon = icon,
        onClick = onClick,
        modifier = modifier.border(MENU_STROKE_MAIN, accent, shape),
        expanded = expanded,
        shape = shape,
        containerColor = MaterialTheme.colorScheme.background,
        contentColor = accent,
        elevation = androidx.compose.material3.FloatingActionButtonDefaults.elevation(0.dp, 0.dp, 0.dp, 0.dp),
        interactionSource = interactionSource,
    )
}

/** A caller's own colour (a red delete action, say) is lent to frame and content. */
@Composable
private fun whiteBearFabAccent(containerColor: Color?, contentColor: Color?): Color {
    val scheme = MaterialTheme.colorScheme
    return containerColor?.takeIf {
        it != scheme.primaryContainer && it != scheme.surface && it != scheme.background && it.alpha > 0f
    } ?: contentColor?.takeIf { it != scheme.onPrimaryContainer && it != scheme.onPrimary }
        ?: scheme.primary
}

/** A filled icon button is a slab too: an outlined round button with the accent glyph. */
@Composable
fun FilledIconButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    shape: Shape = androidx.compose.foundation.shape.CircleShape,
    @Suppress("UNUSED_PARAMETER") colors: androidx.compose.material3.IconButtonColors? = null,
    interactionSource: MutableInteractionSource? = null,
    content: @Composable () -> Unit,
) {
    val accent = MaterialTheme.colorScheme.primary
    val ink = if (enabled) accent else accent.copy(alpha = MENU_DISABLED_ALPHA)
    androidx.compose.material3.OutlinedIconButton(
        onClick = onClick,
        modifier = modifier,
        enabled = enabled,
        shape = shape,
        colors = androidx.compose.material3.IconButtonDefaults.outlinedIconButtonColors(
            contentColor = accent,
            disabledContentColor = accent.copy(alpha = MENU_DISABLED_ALPHA)
        ),
        border = BorderStroke(MENU_STROKE, ink),
        interactionSource = interactionSource,
        content = content,
    )
}

/**
 * What a settings row or destination does, read from its enum name — so a row upstream adds
 * later is coloured without a table to maintain.
 */
fun whiteBearRoleForName(name: String): MenuRole {
    val n = name.uppercase()
    fun has(vararg words: String) = words.any { it in n }
    return when {
        has("DELETE", "CLEAR", "REMOVE", "RESET", "WIPE", "PURGE") -> MenuRole.Delete
        has("EXPORT", "SHARE", "BACKUP", "SAVE", "PRINT", "LOGS") -> MenuRole.Share
        has("SYNC", "IMPORT", "RESTORE", "CLOUD", "DOWNLOAD", "SCAN", "OPDS") -> MenuRole.Connect
        has("ACCOUNT", "DEVICE", "SIGN", "PROFILE") -> MenuRole.Contact
        has("FEEDBACK", "TAG", "SHELF", "BOOKMARK", "ANNOTATION", "REPLACEMENT", "DICTIONARY") -> MenuRole.Mark
        has("BLOCK", "HIDE", "PROTECTION", "PRIVACY", "LOCK") -> MenuRole.Block
        else -> MenuRole.View
    }
}

/**
 * A pressable row of a settings page (black-yellow-theme, “Settings pages”): an outlined pill as
 * wide as its label, its icon coloured by [role], a › when it opens something, and the
 * explanation beneath in dim gold, aligned with the label. [main] is the thick 3 dp / bold pill.
 */
@Composable
fun WhiteBearSettingsPillRow(
    icon: ImageVector,
    role: MenuRole,
    label: String,
    summary: String? = null,
    note: String? = null,
    opens: Boolean = true,
    main: Boolean = false,
    enabled: Boolean = true,
    onClick: () -> Unit,
) {
    val accent = MaterialTheme.colorScheme.primary
    val roleColor = role.color()
    val rowColor = if (role == MenuRole.Delete) roleColor else accent
    val alpha = if (enabled) 1f else MENU_DISABLED_ALPHA
    val ink = rowColor.copy(alpha = rowColor.alpha * alpha)
    androidx.compose.foundation.layout.Column(Modifier.fillMaxWidth().padding(vertical = 3.dp)) {
        Row(
            Modifier
                .clip(MENU_PILL)
                .border(if (main) MENU_STROKE_MAIN else MENU_STROKE, ink, MENU_PILL)
                .clickable(enabled = enabled, role = Role.Button, onClick = onClick)
                .padding(horizontal = 14.dp, vertical = 9.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                icon,
                contentDescription = null,
                modifier = Modifier.size(20.dp),
                tint = roleColor.copy(alpha = roleColor.alpha * alpha)
            )
            Spacer(Modifier.width(10.dp))
            Text(label, color = ink, fontWeight = if (main) FontWeight.Bold else FontWeight.Normal)
            if (opens) {
                Spacer(Modifier.width(8.dp))
                Text("›", color = ink, style = MaterialTheme.typography.titleMedium)
            }
        }
        val dim = LocalWhiteBearNote.current.let { it.copy(alpha = it.alpha * alpha) }
        if (!note.isNullOrBlank()) {
            Text(
                note,
                color = accent.copy(alpha = alpha),
                style = MaterialTheme.typography.labelSmall,
                modifier = Modifier.padding(start = 44.dp, top = 2.dp)
            )
        }
        if (!summary.isNullOrBlank()) {
            Text(
                summary,
                color = dim,
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.padding(start = 44.dp, top = 2.dp)
            )
        }
    }
}

/** A switch row of a settings page: coloured icon, bold label, dim-gold explanation, the switch. */
@Composable
fun WhiteBearSettingsSwitchRow(
    icon: ImageVector,
    role: MenuRole,
    label: String,
    summary: String? = null,
    note: String? = null,
    checked: Boolean,
    enabled: Boolean = true,
    onToggle: () -> Unit,
) {
    val accent = MaterialTheme.colorScheme.primary
    val alpha = if (enabled) 1f else MENU_DISABLED_ALPHA
    val roleColor = role.color()
    Row(
        Modifier
            .fillMaxWidth()
            .clickable(enabled = enabled, role = Role.Switch, onClick = onToggle)
            .padding(vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Spacer(Modifier.width(14.dp))
        Icon(icon, contentDescription = null, modifier = Modifier.size(20.dp), tint = roleColor.copy(alpha = roleColor.alpha * alpha))
        Spacer(Modifier.width(10.dp))
        androidx.compose.foundation.layout.Column(Modifier.weight(1f)) {
            Text(label, color = accent.copy(alpha = alpha), fontWeight = FontWeight.Bold)
            if (!note.isNullOrBlank()) {
                Text(note, color = accent.copy(alpha = alpha), style = MaterialTheme.typography.labelSmall)
            }
            if (!summary.isNullOrBlank()) {
                val dim = LocalWhiteBearNote.current
                Text(summary, color = dim.copy(alpha = dim.alpha * alpha), style = MaterialTheme.typography.bodySmall)
            }
        }
        Spacer(Modifier.width(8.dp))
        Switch(checked = checked, onCheckedChange = { onToggle() }, enabled = enabled)
    }
}
