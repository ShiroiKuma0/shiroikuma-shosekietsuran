package com.aryan.reader.whitebear

import android.content.Context
import android.content.SharedPreferences
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

/** How two parallel books share the screen: not at all, stacked, or side by side. */
enum class WhiteBearSplitMode { NONE, VERTICAL, HORIZONTAL }

/**
 * Fork-local (白い熊) parallel-reading set: 2–3 books ordered left → right. While a set is
 * active, a two-finger horizontal swipe in the reader flips to the neighbouring book (with
 * wrap-around), each book keeping its own saved position.
 */
class WhiteBearParallelState private constructor(private val prefs: SharedPreferences) {

    var bookIds by mutableStateOf(loadIds())
        private set

    /** Armed when "Add book for parallel reading" was tapped: the next library click adds. */
    var pickingArmed by mutableStateOf(false)
        private set

    /** Same-screen display of two parallel books; persisted. */
    var splitMode by mutableStateOf(
        WhiteBearSplitMode.entries.getOrElse(prefs.getInt(KEY_SPLIT_MODE, 0)) { WhiteBearSplitMode.NONE }
    )
        private set

    fun updateSplitMode(mode: WhiteBearSplitMode) {
        splitMode = mode
        prefs.edit().putInt(KEY_SPLIT_MODE, mode.ordinal).apply()
    }

    fun armPicking() {
        pickingArmed = true
    }

    fun disarmPicking() {
        pickingArmed = false
    }

    fun addBook(bookId: String) = updateSet(bookIds + bookId)

    fun removeBook(bookId: String) = updateSet(bookIds - bookId)

    private fun loadIds(): List<String> {
        return prefs.getString(KEY_SET, null)
            ?.split('\n')
            ?.filter { it.isNotBlank() }
            .orEmpty()
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
        private const val KEY_SPLIT_MODE = "wb_parallel_split_mode"

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
