package com.aryan.reader.whitebear

import android.content.Context
import android.content.SharedPreferences
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

/** Fork-local (白い熊) library view settings: list/grid layout and grid metrics. */
class WhiteBearLibraryState private constructor(private val prefs: SharedPreferences) {

    var gridLayout by mutableStateOf(prefs.getBoolean(KEY_GRID_LAYOUT, false))
        private set

    /** Cover thumbnail height in dp; the grid cell width follows from the cover aspect. */
    var thumbnailHeight by mutableFloatStateOf(prefs.getFloat(KEY_THUMB_HEIGHT, 180f))
        private set

    var titleFontSize by mutableFloatStateOf(prefs.getFloat(KEY_TITLE_SIZE, 14f))
        private set

    var authorFontSize by mutableFloatStateOf(prefs.getFloat(KEY_AUTHOR_SIZE, 12f))
        private set

    fun updateGridLayout(value: Boolean) {
        gridLayout = value
        prefs.edit().putBoolean(KEY_GRID_LAYOUT, value).apply()
    }

    fun updateThumbnailHeight(value: Float) {
        thumbnailHeight = value
        prefs.edit().putFloat(KEY_THUMB_HEIGHT, value).apply()
    }

    fun updateTitleFontSize(value: Float) {
        titleFontSize = value
        prefs.edit().putFloat(KEY_TITLE_SIZE, value).apply()
    }

    fun updateAuthorFontSize(value: Float) {
        authorFontSize = value
        prefs.edit().putFloat(KEY_AUTHOR_SIZE, value).apply()
    }

    companion object {
        private const val PREFS_NAME = "whitebear_library_prefs"
        private const val KEY_GRID_LAYOUT = "wb_lib_grid_layout"
        private const val KEY_THUMB_HEIGHT = "wb_lib_thumb_height"
        private const val KEY_TITLE_SIZE = "wb_lib_title_size"
        private const val KEY_AUTHOR_SIZE = "wb_lib_author_size"

        @Volatile
        private var instance: WhiteBearLibraryState? = null

        fun get(context: Context): WhiteBearLibraryState {
            return instance ?: synchronized(this) {
                instance ?: WhiteBearLibraryState(
                    context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                ).also { instance = it }
            }
        }
    }
}
