package com.aryan.reader.whitebear

import android.content.Context
import android.content.SharedPreferences
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

/**
 * Fork-local (白い熊) reading-gesture configuration for the EPUB/text reader:
 * left/right tap zones turn pages, a vertical swipe on the right third changes font
 * size, a vertical swipe on the left third changes screen brightness.
 */
class WhiteBearGestureState private constructor(private val prefs: SharedPreferences) {

    var enabled by mutableStateOf(prefs.getBoolean(KEY_ENABLED, true))
        private set

    var tapToTurnPages by mutableStateOf(prefs.getBoolean(KEY_TAP_TURN, true))
        private set

    var rightSwipeFontSize by mutableStateOf(prefs.getBoolean(KEY_RIGHT_SWIPE_FONT, true))
        private set

    var leftSwipeBrightness by mutableStateOf(prefs.getBoolean(KEY_LEFT_SWIPE_BRIGHTNESS, true))
        private set

    /** Page-turn sound: 0 = off, 1..5 = the bundled page1..page5 effects. Default page1. */
    var pageTurnSound by mutableIntStateOf(prefs.getInt(KEY_PAGE_TURN_SOUND, 1))
        private set

    /** Tap page-turn distance as a percent of the viewport; 100 = full page, no overlap. */
    var pageTurnStepPercent by mutableIntStateOf(prefs.getInt(KEY_PAGE_TURN_STEP, 100))
        private set

    fun updatePageTurnSound(value: Int) {
        pageTurnSound = value
        prefs.edit().putInt(KEY_PAGE_TURN_SOUND, value).apply()
    }

    fun updatePageTurnStepPercent(value: Int) {
        pageTurnStepPercent = value
        prefs.edit().putInt(KEY_PAGE_TURN_STEP, value).apply()
    }

    fun updateEnabled(value: Boolean) {
        enabled = value
        prefs.edit().putBoolean(KEY_ENABLED, value).apply()
    }

    fun updateTapToTurnPages(value: Boolean) {
        tapToTurnPages = value
        prefs.edit().putBoolean(KEY_TAP_TURN, value).apply()
    }

    fun updateRightSwipeFontSize(value: Boolean) {
        rightSwipeFontSize = value
        prefs.edit().putBoolean(KEY_RIGHT_SWIPE_FONT, value).apply()
    }

    fun updateLeftSwipeBrightness(value: Boolean) {
        leftSwipeBrightness = value
        prefs.edit().putBoolean(KEY_LEFT_SWIPE_BRIGHTNESS, value).apply()
    }

    companion object {
        private const val PREFS_NAME = "whitebear_gesture_prefs"
        private const val KEY_ENABLED = "wb_gestures_enabled"
        private const val KEY_TAP_TURN = "wb_tap_turn_pages"
        private const val KEY_RIGHT_SWIPE_FONT = "wb_right_swipe_font"
        private const val KEY_LEFT_SWIPE_BRIGHTNESS = "wb_left_swipe_brightness"
        private const val KEY_PAGE_TURN_SOUND = "wb_page_turn_sound"
        private const val KEY_PAGE_TURN_STEP = "wb_page_turn_step"

        @Volatile
        private var instance: WhiteBearGestureState? = null

        fun get(context: Context): WhiteBearGestureState {
            return instance ?: synchronized(this) {
                instance ?: WhiteBearGestureState(
                    context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                ).also { instance = it }
            }
        }
    }
}
