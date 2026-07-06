package com.aryan.reader.whitebear

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.ui.text.font.Font
import com.aryan.reader.AppFontPreference
import com.aryan.reader.AppFontPreferenceKind
import com.aryan.reader.CustomTopAppBar
import com.aryan.reader.MainViewModel
import com.aryan.reader.R
import com.aryan.reader.data.CustomFontEntity
import com.aryan.reader.rememberFilePickerLauncher
import com.aryan.reader.supportedFontMimeTypes
import java.io.File
import kotlin.math.roundToInt

/** Indent per hierarchy level — deep on purpose, so orientation is instantaneous. */
private val IndentStep = 32.dp

@Composable
fun WhiteBearUiScreen(
    viewModel: MainViewModel,
    onBackClick: () -> Unit
) {
    val context = LocalContext.current
    val state = remember { WhiteBearUiState.get(context) }
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val customFonts by viewModel.customFonts.collectAsStateWithLifecycle()

    var pickerSlot by remember { mutableStateOf<WhiteBearSlot?>(null) }
    var pickerOriginal by remember { mutableIntStateOf(0) }
    var showResetDialog by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            CustomTopAppBar(
                title = { Text("白い熊 書籍閲覧 UI") },
                navigationIcon = {
                    IconButton(onClick = onBackClick) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        },
        contentWindowInsets = WindowInsets.navigationBars
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(start = 16.dp, end = 16.dp, bottom = 32.dp)
        ) {
            WhiteBearPreviewCard(state)

            SectionHeader("General")
            SwitchRow(
                label = "Use 白い熊 UI",
                checked = state.enabled,
                level = 1,
                onToggle = { state.updateEnabled(it) }
            )
            ActionRow(
                label = "Reset everything to defaults",
                level = 1,
                onClick = { showResetDialog = true }
            )

            SectionHeader("Colors")
            SubHeader("Foundation", level = 1)
            listOf(
                WhiteBearSlot.BACKGROUND,
                WhiteBearSlot.TEXT,
                WhiteBearSlot.ACCENT,
                WhiteBearSlot.BORDER
            ).forEach { slot ->
                ColorRow(slot = slot, argb = state.color(slot), level = 2) {
                    pickerOriginal = state.color(slot)
                    pickerSlot = slot
                }
            }
            SubHeader("Surfaces", level = 1)
            listOf(WhiteBearSlot.SURFACE, WhiteBearSlot.SURFACE_HIGH).forEach { slot ->
                ColorRow(slot = slot, argb = state.color(slot), level = 2) {
                    pickerOriginal = state.color(slot)
                    pickerSlot = slot
                }
            }

            SectionHeader("Typography")
            SubHeader("Font", level = 1)
            FontChoices(
                current = uiState.appFontPreference.sanitized(),
                customFonts = customFonts,
                level = 2,
                onSelect = viewModel::setAppFontPreference
            )
            AddFontRow(level = 2, viewModel = viewModel)
            SubHeader("Size & weight", level = 1)
            SliderRow(
                label = "Text size",
                value = state.fontScale,
                valueText = String.format("×%.2f", state.fontScale),
                range = 0.8f..1.4f,
                steps = 11,
                level = 2,
                onChange = { state.updateFontScale(it) }
            )
            SliderRow(
                label = "Text weight",
                value = state.fontWeight.toFloat(),
                valueText = if (state.fontWeight == 0) "Default" else state.fontWeight.toString(),
                range = 0f..900f,
                steps = 8,
                level = 2,
                onChange = { state.updateFontWeight((it / 100f).roundToInt() * 100) }
            )

            SectionHeader("Shape & borders")
            SliderRow(
                label = "Corner roundness",
                value = state.cornerRadius,
                valueText = "${state.cornerRadius.roundToInt()} dp",
                range = 0f..28f,
                steps = 27,
                level = 1,
                onChange = { state.updateCornerRadius(it.roundToInt().toFloat()) }
            )
            SliderRow(
                label = "Border thickness",
                value = state.borderWidth,
                valueText = String.format("%.1f dp", state.borderWidth),
                range = 0f..6f,
                steps = 11,
                level = 1,
                onChange = { state.updateBorderWidth((it * 2).roundToInt() / 2f) }
            )
        }
    }

    pickerSlot?.let { slot ->
        WhiteBearColorPickerDialog(
            title = slot.label,
            initialArgb = pickerOriginal,
            defaultArgb = slot.defaultArgb,
            recentColors = state.recentColors,
            onPreview = { state.setColor(slot, it, persist = false) },
            onConfirm = { argb ->
                state.setColor(slot, argb)
                state.addRecentColor(argb)
                pickerSlot = null
            },
            onCancel = {
                state.setColor(slot, pickerOriginal, persist = false)
                pickerSlot = null
            }
        )
    }

    if (showResetDialog) {
        AlertDialog(
            onDismissRequest = { showResetDialog = false },
            title = { Text("Reset 白い熊 UI") },
            text = { Text("Reset all colors, fonts, sizes and shapes to the black-yellow defaults?") },
            confirmButton = {
                TextButton(onClick = {
                    state.resetAll()
                    showResetDialog = false
                }) { Text("Reset") }
            },
            dismissButton = {
                TextButton(onClick = { showResetDialog = false }) { Text("Cancel") }
            }
        )
    }
}

/** Live sample of text, icons, borders, corners and buttons — restyles as settings change. */
@Composable
private fun WhiteBearPreviewCard(state: WhiteBearUiState) {
    val borderWidth = state.borderWidth.dp
    var boxModifier = Modifier
        .fillMaxWidth()
        .padding(top = 12.dp)
        .background(MaterialTheme.colorScheme.surface, MaterialTheme.shapes.medium)
    if (state.borderWidth > 0f) {
        boxModifier = boxModifier.border(
            borderWidth, MaterialTheme.colorScheme.outline, MaterialTheme.shapes.medium
        )
    }
    Column(modifier = boxModifier.padding(12.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                Icons.Default.Settings,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.size(22.dp)
            )
            Spacer(Modifier.width(10.dp))
            Icon(
                painterResource(id = R.drawable.palette),
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(22.dp)
            )
            Spacer(Modifier.width(10.dp))
            Text(
                "白い熊 書籍閲覧 — AaBb 012 あ亜",
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurface
            )
        }
        Text(
            "Secondary sample text",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        if (state.borderWidth > 0f) {
            HorizontalDivider(
                modifier = Modifier.padding(vertical = 6.dp),
                thickness = borderWidth,
                color = MaterialTheme.colorScheme.outline
            )
        } else {
            Spacer(Modifier.height(6.dp))
        }
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Button(onClick = {}) { Text("Accent") }
            OutlinedButton(onClick = {}) { Text("Outline") }
        }
    }
}

@Composable
private fun SectionHeader(text: String) {
    Column(Modifier.fillMaxWidth().padding(top = 24.dp, bottom = 6.dp)) {
        Text(
            text,
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.primary
        )
        Spacer(Modifier.height(3.dp))
        Box(
            Modifier
                .fillMaxWidth()
                .height(2.dp)
                .background(MaterialTheme.colorScheme.primary)
        )
    }
}

@Composable
private fun SubHeader(text: String, level: Int) {
    Text(
        text,
        style = MaterialTheme.typography.titleSmall,
        fontWeight = FontWeight.Bold,
        textDecoration = TextDecoration.Underline,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(start = IndentStep * level, top = 8.dp, bottom = 2.dp)
    )
}

@Composable
private fun ColorRow(slot: WhiteBearSlot, argb: Int, level: Int, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(start = IndentStep * level)
            .heightIn(min = 38.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            slot.label,
            modifier = Modifier.weight(1f),
            style = MaterialTheme.typography.bodyMedium
        )
        ColorSwatchBox(argb = argb, size = 26.dp)
    }
}

@Composable
private fun SwitchRow(label: String, checked: Boolean, level: Int, onToggle: (Boolean) -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onToggle(!checked) }
            .padding(start = IndentStep * level)
            .heightIn(min = 38.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(label, modifier = Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
        Switch(checked = checked, onCheckedChange = onToggle)
    }
}

@Composable
private fun ActionRow(label: String, level: Int, onClick: () -> Unit, icon: ImageVector? = null) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(start = IndentStep * level)
            .heightIn(min = 38.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (icon != null) {
            Icon(
                icon,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(18.dp)
            )
            Spacer(Modifier.width(8.dp))
        }
        Text(label, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.primary)
    }
}

@Composable
private fun SliderRow(
    label: String,
    value: Float,
    valueText: String,
    range: ClosedFloatingPointRange<Float>,
    steps: Int,
    level: Int,
    onChange: (Float) -> Unit
) {
    Column(Modifier.padding(start = IndentStep * level)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(label, modifier = Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
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
                .height(32.dp)
        )
    }
}

private data class FontChoice(
    val label: String,
    val preference: AppFontPreference,
    val family: FontFamily?
)

/** Every option is rendered in its own glyphs; custom fonts come from the app's font store. */
@Composable
private fun FontChoices(
    current: AppFontPreference,
    customFonts: List<CustomFontEntity>,
    level: Int,
    onSelect: (AppFontPreference) -> Unit
) {
    val choices = remember(customFonts) {
        buildList {
            add(FontChoice("System default", AppFontPreference.System, null))
            add(FontChoice("Serif", AppFontPreference.Serif, FontFamily.Serif))
            add(FontChoice("Sans serif", AppFontPreference.SansSerif, FontFamily.SansSerif))
            add(FontChoice("Monospace", AppFontPreference.Monospace, FontFamily.Monospace))
            customFonts
                .filterNot { it.isDeleted }
                .sortedBy { it.displayName.lowercase() }
                .forEach { font ->
                    val family = runCatching {
                        FontFamily(Font(File(font.path)))
                    }.getOrNull() ?: return@forEach
                    add(FontChoice(font.displayName, AppFontPreference.custom(font.id), family))
                }
        }
    }
    choices.forEach { choice ->
        val selected = current == choice.preference.sanitized()
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable { onSelect(choice.preference) }
                .padding(start = IndentStep * level)
                .heightIn(min = 36.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(Modifier.width(26.dp), contentAlignment = Alignment.CenterStart) {
                if (selected) {
                    Icon(
                        Icons.Default.Check,
                        contentDescription = "Selected",
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }
            Text(
                choice.label,
                fontFamily = choice.family,
                fontSize = 16.sp,
                color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onBackground
            )
        }
    }
}

@Composable
private fun AddFontRow(level: Int, viewModel: MainViewModel) {
    val pickFontLauncher = rememberFilePickerLauncher(viewModel::importFonts)
    val fontMimeTypes = remember { supportedFontMimeTypes() }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { pickFontLauncher.launch(fontMimeTypes) }
            .padding(start = IndentStep * level)
            .heightIn(min = 36.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            Icons.Default.Add,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(18.dp)
        )
        Spacer(Modifier.width(8.dp))
        Text(
            "Add external font…",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.primary
        )
    }
}

/** The row injected at the top of the stock Settings screen. */
@Composable
fun WhiteBearSettingsEntryRow(onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            painterResource(id = R.drawable.palette),
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(22.dp)
        )
        Spacer(Modifier.width(16.dp))
        Text(
            "白い熊 書籍閲覧 UI",
            style = MaterialTheme.typography.bodyLarge,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.primary
        )
    }
}
