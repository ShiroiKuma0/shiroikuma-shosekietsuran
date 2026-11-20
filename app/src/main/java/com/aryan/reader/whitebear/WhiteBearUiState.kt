package com.aryan.reader.whitebear

import android.content.Context
import android.content.SharedPreferences
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

/**
 * Fork-local (白い熊) UI customization. Everything lives in its own prefs file and its own
 * package so upstream rebases never conflict with it.
 */
enum class WhiteBearSlot(val key: String, val label: String, val defaultArgb: Int) {
    BACKGROUND("wb_color_background", "Background", 0xFF000000.toInt()),
    SURFACE("wb_color_surface", "Surface (panels, cards)", 0xFF000000.toInt()),
    SURFACE_HIGH("wb_color_surface_high", "Raised surface (bars, menus, dialogs)", 0xFF000000.toInt()),
    TEXT("wb_color_text", "Text", 0xFFFFFF00.toInt()),
    ACCENT("wb_color_accent", "Accent", 0xFFFFFF00.toInt()),
    BORDER("wb_color_border", "Border", 0xFFFFFF00.toInt()),
    /** Dim gold of explanations and of the rule down their left edge. */
    NOTE("wb_color_note", "Notes and explanations", 0xFFC8C800.toInt()),
    /** A switch that is off: a darker, opaque yellow — never a faded one. */
    SWITCH_OFF("wb_color_switch_off", "Switch, off", 0xFF8C8C00.toInt()),
    /** Menu icons, coloured by what the row does. Plain view / open / settings rows use the accent. */
    MENU_CONNECT("wb_color_menu_connect", "Start, sync, load", 0xFF00FF00.toInt()),
    MENU_MARK("wb_color_menu_mark", "Edit, bookmark, tag, shelf", 0xFF4D94FF.toInt()),
    MENU_CONTACT("wb_color_menu_contact", "Account, devices", 0xFF00FFFF.toInt()),
    MENU_SHARE("wb_color_menu_share", "Share, save, export, print", 0xFFFF8000.toInt()),
    MENU_BLOCK("wb_color_menu_block", "Stop, clear, close", 0xFFFF00FF.toInt()),
    MENU_DELETE("wb_color_menu_delete", "Delete, remove", 0xFFFF0000.toInt()),
}

class WhiteBearUiState private constructor(private val prefs: SharedPreferences) {

    var enabled by mutableStateOf(prefs.getBoolean(KEY_ENABLED, true))
        private set

    var fontScale by mutableFloatStateOf(prefs.getFloat(KEY_FONT_SCALE, 1.0f))
        private set

    /** 0 = keep each text style's own weight; otherwise 100..900. */
    var fontWeight by mutableIntStateOf(prefs.getInt(KEY_FONT_WEIGHT, 0))
        private set

    /** Corner radius in dp of the "medium" shape; the other shape sizes scale from it. */
    var cornerRadius by mutableFloatStateOf(prefs.getFloat(KEY_CORNER_RADIUS, 12f))
        private set

    /** Border / rule thickness in dp; 0 removes borders entirely. */
    var borderWidth by mutableFloatStateOf(prefs.getFloat(KEY_BORDER_WIDTH, 1f))
        private set

    private val colorMap = mutableStateMapOf<WhiteBearSlot, Int>().apply {
        // Migration: the raised-surface default used to be #FF1C1C00 (yellow-tinted black),
        // which "Reset to defaults" may have persisted; pure black is the default now.
        if (prefs.getInt(WhiteBearSlot.SURFACE_HIGH.key, 0) == 0xFF1C1C00.toInt()) {
            prefs.edit().remove(WhiteBearSlot.SURFACE_HIGH.key).apply()
        }
        WhiteBearSlot.entries.forEach { slot -> put(slot, prefs.getInt(slot.key, slot.defaultArgb)) }
    }

    /** Most-recently-used picker colors, newest first. */
    val recentColors = mutableStateListOf<Int>().apply {
        val stored = prefs.getString(KEY_RECENT_COLORS, null)
        if (stored.isNullOrBlank()) {
            addAll(DEFAULT_RECENTS)
        } else {
            addAll(stored.split(',').mapNotNull { it.trim().toIntOrNull() })
        }
    }

    fun color(slot: WhiteBearSlot): Int = colorMap[slot] ?: slot.defaultArgb

    /** [persist] = false is used for live preview while a picker is open. */
    fun setColor(slot: WhiteBearSlot, argb: Int, persist: Boolean = true) {
        colorMap[slot] = argb
        if (persist) prefs.edit().putInt(slot.key, argb).apply()
    }

    /** Section ids of the UI page in the order 白い熊 arranged them; unknown ids go last. */
    var sectionOrder by mutableStateOf(
        prefs.getString(KEY_SECTION_ORDER, null)?.split(",")?.filter { it.isNotBlank() } ?: emptyList()
    )
        private set

    /** Sections of the UI page that are unfolded; every section starts folded. */
    var openSections by mutableStateOf(
        prefs.getString(KEY_SECTIONS_OPEN, null)?.split(",")?.filter { it.isNotBlank() }?.toSet() ?: emptySet()
    )
        private set

    fun updateSectionOrder(value: List<String>) {
        sectionOrder = value
        prefs.edit().putString(KEY_SECTION_ORDER, value.joinToString(",")).apply()
    }

    fun resetSectionOrder() {
        sectionOrder = emptyList()
        prefs.edit().remove(KEY_SECTION_ORDER).apply()
    }

    fun setSectionOpen(id: String, open: Boolean) {
        openSections = if (open) openSections + id else openSections - id
        prefs.edit().putString(KEY_SECTIONS_OPEN, openSections.joinToString(",")).apply()
    }

    fun updateEnabled(value: Boolean) {
        enabled = value
        prefs.edit().putBoolean(KEY_ENABLED, value).apply()
    }

    fun updateFontScale(value: Float) {
        fontScale = value
        prefs.edit().putFloat(KEY_FONT_SCALE, value).apply()
    }

    fun updateFontWeight(value: Int) {
        fontWeight = value
        prefs.edit().putInt(KEY_FONT_WEIGHT, value).apply()
    }

    fun updateCornerRadius(value: Float) {
        cornerRadius = value
        prefs.edit().putFloat(KEY_CORNER_RADIUS, value).apply()
    }

    fun updateBorderWidth(value: Float) {
        borderWidth = value
        prefs.edit().putFloat(KEY_BORDER_WIDTH, value).apply()
    }

    fun addRecentColor(argb: Int) {
        recentColors.remove(argb)
        recentColors.add(0, argb)
        while (recentColors.size > MAX_RECENTS) recentColors.removeAt(recentColors.size - 1)
        prefs.edit().putString(KEY_RECENT_COLORS, recentColors.joinToString(",")).apply()
    }

    fun resetAll() {
        WhiteBearSlot.entries.forEach { colorMap[it] = it.defaultArgb }
        enabled = true
        fontScale = 1.0f
        fontWeight = 0
        cornerRadius = 12f
        borderWidth = 1f
        prefs.edit().apply {
            WhiteBearSlot.entries.forEach { putInt(it.key, it.defaultArgb) }
            putBoolean(KEY_ENABLED, true)
            putFloat(KEY_FONT_SCALE, 1.0f)
            putInt(KEY_FONT_WEIGHT, 0)
            putFloat(KEY_CORNER_RADIUS, 12f)
            putFloat(KEY_BORDER_WIDTH, 1f)
        }.apply()
    }

    companion object {
        private const val PREFS_NAME = "whitebear_ui_prefs"
        private const val KEY_ENABLED = "wb_enabled"
        private const val KEY_SECTION_ORDER = "wb_ui_section_order"
        private const val KEY_SECTIONS_OPEN = "wb_ui_sections_open"
        private const val KEY_FONT_SCALE = "wb_font_scale"
        private const val KEY_FONT_WEIGHT = "wb_font_weight"
        private const val KEY_CORNER_RADIUS = "wb_corner_radius"
        private const val KEY_BORDER_WIDTH = "wb_border_width"
        private const val KEY_RECENT_COLORS = "wb_recent_colors"
        private const val MAX_RECENTS = 8

        private val DEFAULT_RECENTS = listOf(
            0xFFFFFF00.toInt(), // yellow
            0xFF000000.toInt(), // black
            0xFFFFFFFF.toInt(), // white
            0xFFFF0000.toInt(), // red
            0xFF00FF00.toInt(), // green
            0xFF00A0FF.toInt(), // blue
            0xFFFF8800.toInt(), // orange
            0xFF00FFFF.toInt()  // cyan
        )

        @Volatile
        private var instance: WhiteBearUiState? = null

        fun get(context: Context): WhiteBearUiState {
            return instance ?: synchronized(this) {
                instance ?: WhiteBearUiState(
                    context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                ).also { instance = it }
            }
        }
    }
}
