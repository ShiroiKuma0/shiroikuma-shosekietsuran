package com.aryan.reader.whitebear

import android.content.Context
import android.content.SharedPreferences
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

/**
 * How the books of a parallel set share the reader screen. `minBooks` gates which
 * layouts the chooser offers; with fewer books available the layout degrades via
 * [effectiveParallelLayout].
 */
enum class WhiteBearParallelLayout(val label: String, val minBooks: Int) {
    SINGLE("One book at a time", 1),
    TWO_COLUMNS("Two — side by side", 2),
    TWO_ROWS("Two — stacked", 2),
    THREE_COLUMNS("Three — side by side", 3),
    THREE_ROWS("Three — stacked", 3),
    ONE_PLUS_TWO_RIGHT("One left + two stacked right", 3),
    TWO_PLUS_ONE_RIGHT("Two stacked left + one right", 3),
    ONE_PLUS_TWO_BOTTOM("One top + two side-by-side bottom", 3),
    TWO_PLUS_ONE_BOTTOM("Two side-by-side top + one bottom", 3)
}

/** Degrade the chosen layout to what the current number of books can actually show. */
fun effectiveParallelLayout(layout: WhiteBearParallelLayout, bookCount: Int): WhiteBearParallelLayout {
    if (bookCount >= layout.minBooks) return layout
    if (bookCount >= 2) {
        return when (layout) {
            WhiteBearParallelLayout.THREE_COLUMNS,
            WhiteBearParallelLayout.ONE_PLUS_TWO_RIGHT,
            WhiteBearParallelLayout.TWO_PLUS_ONE_RIGHT -> WhiteBearParallelLayout.TWO_COLUMNS
            WhiteBearParallelLayout.THREE_ROWS,
            WhiteBearParallelLayout.ONE_PLUS_TWO_BOTTOM,
            WhiteBearParallelLayout.TWO_PLUS_ONE_BOTTOM -> WhiteBearParallelLayout.TWO_ROWS
            else -> layout
        }
    }
    return WhiteBearParallelLayout.SINGLE
}

/**
 * Fork-local (白い熊) parallel-reading set: 2–3 books ordered left → right. While a set is
 * active, a two-finger horizontal swipe in the reader flips to the neighbouring book (with
 * wrap-around), each book keeping its own saved position. The set can also share the
 * screen: [layout] picks the pane arrangement, [mainRatio]/[subRatio] carry the positions
 * of the draggable dividers (fraction taken by the first pane of the split).
 */
class WhiteBearParallelState private constructor(private val prefs: SharedPreferences) {

    var bookIds by mutableStateOf(loadIds())
        private set

    /** Armed when "Add book for parallel reading" was tapped: the next library click adds. */
    var pickingArmed by mutableStateOf(false)
        private set

    /** Same-screen pane arrangement; persisted. */
    var layout by mutableStateOf(loadLayout())
        private set

    /** Fraction of the container taken by the first pane at the primary divider. */
    var mainRatio by mutableFloatStateOf(prefs.getFloat(KEY_MAIN_RATIO, 0.5f).coerceIn(RATIO_MIN, RATIO_MAX))
        private set

    /** Fraction taken by the first pane of the secondary split (third book / sub-pane). */
    var subRatio by mutableFloatStateOf(prefs.getFloat(KEY_SUB_RATIO, 0.5f).coerceIn(RATIO_MIN, RATIO_MAX))
        private set

    fun updateLayout(newLayout: WhiteBearParallelLayout) {
        layout = newLayout
        prefs.edit().putInt(KEY_LAYOUT, newLayout.ordinal).apply()
    }

    fun updateMainRatio(ratio: Float) {
        mainRatio = ratio.coerceIn(RATIO_MIN, RATIO_MAX)
        prefs.edit().putFloat(KEY_MAIN_RATIO, mainRatio).apply()
    }

    fun updateSubRatio(ratio: Float) {
        subRatio = ratio.coerceIn(RATIO_MIN, RATIO_MAX)
        prefs.edit().putFloat(KEY_SUB_RATIO, subRatio).apply()
    }

    fun armPicking() {
        pickingArmed = true
    }

    fun disarmPicking() {
        pickingArmed = false
    }

    fun addBook(bookId: String) = updateSet(bookIds + bookId)

    fun removeBook(bookId: String) = updateSet(bookIds - bookId)

    /** Drops any existing set and starts a fresh one with just this book. */
    fun startNewSet(bookId: String) = updateSet(listOf(bookId))

    private fun loadIds(): List<String> {
        return prefs.getString(KEY_SET, null)
            ?.split('\n')
            ?.filter { it.isNotBlank() }
            .orEmpty()
    }

    private fun loadLayout(): WhiteBearParallelLayout {
        val stored = prefs.getInt(KEY_LAYOUT, -1)
        if (stored >= 0) {
            return WhiteBearParallelLayout.entries.getOrElse(stored) { WhiteBearParallelLayout.SINGLE }
        }
        // Migrate the pre-layout split mode: 0 = none, 1 = vertical stack, 2 = side by side.
        return when (prefs.getInt(KEY_LEGACY_SPLIT_MODE, 0)) {
            1 -> WhiteBearParallelLayout.TWO_ROWS
            2 -> WhiteBearParallelLayout.TWO_COLUMNS
            else -> WhiteBearParallelLayout.SINGLE
        }
    }

    fun updateSet(ids: List<String>) {
        val cleaned = ids.filter { it.isNotBlank() }.distinct().take(3)
        bookIds = cleaned
        prefs.edit().putString(KEY_SET, cleaned.joinToString("\n")).apply()
    }

    fun clear() = updateSet(emptyList())

    /** dir +1 = flip right, -1 = flip left; wraps around. Null when not in an active set. */
    fun neighborOf(bookId: String, dir: Int): String? {
        val ids = bookIds
        if (ids.size < 2) return null
        val index = ids.indexOf(bookId)
        if (index < 0) return null
        val next = ((index + dir) % ids.size + ids.size) % ids.size
        return ids[next].takeIf { it != bookId }
    }

    fun positionLabel(bookId: String): String? {
        val ids = bookIds
        val index = ids.indexOf(bookId)
        return if (index >= 0 && ids.size >= 2) "${index + 1}/${ids.size}" else null
    }

    companion object {
        private const val PREFS_NAME = "whitebear_parallel_prefs"
        private const val KEY_SET = "wb_parallel_set"
        private const val KEY_LEGACY_SPLIT_MODE = "wb_parallel_split_mode"
        private const val KEY_LAYOUT = "wb_parallel_layout"
        private const val KEY_MAIN_RATIO = "wb_parallel_main_ratio"
        private const val KEY_SUB_RATIO = "wb_parallel_sub_ratio"

        const val RATIO_MIN = 0.15f
        const val RATIO_MAX = 0.85f

        @Volatile
        private var instance: WhiteBearParallelState? = null

        fun get(context: Context): WhiteBearParallelState {
            return instance ?: synchronized(this) {
                instance ?: WhiteBearParallelState(
                    context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                ).also { instance = it }
            }
        }
    }
}
