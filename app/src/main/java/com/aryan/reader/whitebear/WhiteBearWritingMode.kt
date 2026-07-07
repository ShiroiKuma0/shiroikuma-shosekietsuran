package com.aryan.reader.whitebear

import android.content.Context

/**
 * 白い熊 UI: per-book writing-direction preference for the WebView reader.
 * AUTO respects the book's own CSS (Japanese tategaki books render vertically),
 * VERTICAL forces vertical-rl columns, HORIZONTAL forces Western horizontal layout.
 */
object WhiteBearWritingMode {
    const val AUTO = "auto"
    const val VERTICAL = "vertical"
    const val HORIZONTAL = "horizontal"

    private const val PREFS_NAME = "whitebear_writing_prefs"

    fun load(context: Context, bookId: String): String {
        return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getString("wb_writing_$bookId", AUTO) ?: AUTO
    }

    fun save(context: Context, bookId: String, mode: String) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit().putString("wb_writing_$bookId", mode).apply()
    }

    /** Ruby accommodation: true = ruby lines get extra column spacing; false = uniform pitch. */
    fun loadRubySpace(context: Context): Boolean {
        return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getBoolean("wb_ruby_space", true)
    }

    fun saveRubySpace(context: Context, value: Boolean) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit().putBoolean("wb_ruby_space", value).apply()
    }

    fun cycle(mode: String): String = when (mode) {
        AUTO -> VERTICAL
        VERTICAL -> HORIZONTAL
        else -> AUTO
    }

    fun label(mode: String): String = when (mode) {
        VERTICAL -> "Writing: Vertical 縦書き"
        HORIZONTAL -> "Writing: Horizontal 横書き"
        else -> "Writing: Auto"
    }
}
