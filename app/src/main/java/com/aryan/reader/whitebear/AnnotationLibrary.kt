package com.aryan.reader.whitebear

import android.content.Context
import androidx.compose.ui.graphics.Color
import com.aryan.reader.data.AppDatabase
import com.aryan.reader.pdf.data.PdfHighlightRepository
import com.aryan.reader.shared.EpubAnnotationSerializer
import com.aryan.reader.shared.HighlightStyle
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import timber.log.Timber
import java.io.File

/**
 * One annotation (text highlight, optionally with a note) surfaced in the central
 * annotation library, denormalized with everything the library screen needs so the
 * screen never has to open a book.
 */
data class AnnotationLibraryEntry(
    val bookId: String,
    val bookTitle: String,
    val coverImagePath: String?,
    val isPdf: Boolean,
    val annotationId: String,
    val text: String,
    val note: String?,
    val color: Color,
    val colorLabel: String,
    val style: HighlightStyle,
    val locationLabel: String,
    val chapterIndex: Int?,
    val cfi: String?,
    val charOffset: Int?,
    val pageIndex: Int?,
    val sortInBook: Long,
    val tags: List<String>
) {
    fun matches(query: String): Boolean {
        if (query.isBlank()) return true
        val q = query.trim()
        return text.contains(q, ignoreCase = true) ||
            (note?.contains(q, ignoreCase = true) == true) ||
            bookTitle.contains(q, ignoreCase = true) ||
            locationLabel.contains(q, ignoreCase = true) ||
            tags.any { it.contains(q, ignoreCase = true) }
    }
}

/**
 * Aggregates every annotation in the app from its existing stores — the
 * `recent_files.highlights` Room column for EPUB/text books and the per-book
 * sidecar JSON files for PDF highlights. Nothing is duplicated: this reads the
 * same data the readers write, so new annotations show up here automatically.
 */
object AnnotationLibrary {

    suspend fun loadEntries(context: Context): List<AnnotationLibraryEntry> = withContext(Dispatchers.IO) {
        val dao = AppDatabase.getDatabase(context).recentFileDao()
        val tagStore = AnnotationTagStore.get(context)
        val pdfRepository = PdfHighlightRepository(context)
        val entries = mutableListOf<AnnotationLibraryEntry>()

        for (entity in dao.getAllFiles()) {
            if (entity.isDeleted) continue
            val bookTitle = entity.customName?.takeIf { it.isNotBlank() }
                ?: entity.title?.takeIf { it.isNotBlank() }
                ?: entity.displayName

            for (highlight in EpubAnnotationSerializer.parseHighlightsJson(entity.highlights)) {
                entries += AnnotationLibraryEntry(
                    bookId = entity.bookId,
                    bookTitle = bookTitle,
                    coverImagePath = entity.coverImagePath,
                    isPdf = false,
                    annotationId = highlight.id,
                    text = highlight.text,
                    note = highlight.note,
                    color = highlight.effectiveColor,
                    colorLabel = highlight.colorArgb?.let { argbLabel(it) }
                        ?: highlight.color.id.replaceFirstChar { it.uppercase() },
                    style = highlight.style,
                    locationLabel = "Chapter ${highlight.chapterIndex + 1}",
                    chapterIndex = highlight.chapterIndex,
                    cfi = highlight.cfi,
                    charOffset = highlight.locator.charOffset,
                    pageIndex = null,
                    sortInBook = highlight.chapterIndex.toLong() * 1_000_000 +
                        (highlight.locator.charOffset ?: highlight.locator.startOffset ?: 0),
                    tags = tagStore.tagsFor(entity.bookId, highlight.id)
                )
            }

            if (pdfRepository.getFileForSync(entity.bookId).exists()) {
                for (highlight in pdfRepository.loadHighlights(entity.bookId)) {
                    entries += AnnotationLibraryEntry(
                        bookId = entity.bookId,
                        bookTitle = bookTitle,
                        coverImagePath = entity.coverImagePath,
                        isPdf = true,
                        annotationId = highlight.id,
                        text = highlight.text,
                        note = highlight.note,
                        color = highlight.colorArgb?.let { Color(it) } ?: highlight.color.color,
                        colorLabel = highlight.colorArgb?.let { argbLabel(it) }
                            ?: highlight.color.name.lowercase().replaceFirstChar { it.uppercase() },
                        style = highlight.style,
                        locationLabel = "Page ${highlight.pageIndex + 1}",
                        chapterIndex = null,
                        cfi = null,
                        charOffset = null,
                        pageIndex = highlight.pageIndex,
                        sortInBook = highlight.pageIndex.toLong() * 1_000_000 + highlight.range.first,
                        tags = tagStore.tagsFor(entity.bookId, highlight.id)
                    )
                }
            }
        }
        entries
    }

    private fun argbLabel(argb: Int): String = "#%06X".format(argb and 0xFFFFFF)
}

/**
 * Fork-owned store for annotation tags, kept outside the upstream data models so
 * rebases stay clean. One JSON file: { bookId: { annotationId: [tag, …] } }.
 * Also the natural payload for a future annotation import/export.
 */
class AnnotationTagStore private constructor(private val file: File) {

    private val mutex = Mutex()
    private var cache: MutableMap<String, MutableMap<String, List<String>>>? = null

    suspend fun tagsFor(bookId: String, annotationId: String): List<String> =
        withContext(Dispatchers.IO) {
            mutex.withLock { loadLocked()[bookId]?.get(annotationId) ?: emptyList() }
        }

    suspend fun allTags(): List<String> = withContext(Dispatchers.IO) {
        mutex.withLock {
            loadLocked().values.flatMap { it.values.flatten() }.distinct().sortedBy { it.lowercase() }
        }
    }

    suspend fun setTags(bookId: String, annotationId: String, tags: List<String>) =
        withContext(Dispatchers.IO) {
            mutex.withLock {
                val root = loadLocked()
                val clean = tags.map { it.trim() }.filter { it.isNotEmpty() }.distinct()
                if (clean.isEmpty()) {
                    root[bookId]?.remove(annotationId)
                    if (root[bookId]?.isEmpty() == true) root.remove(bookId)
                } else {
                    root.getOrPut(bookId) { mutableMapOf() }[annotationId] = clean
                }
                saveLocked(root)
            }
        }

    private fun loadLocked(): MutableMap<String, MutableMap<String, List<String>>> {
        cache?.let { return it }
        val root = mutableMapOf<String, MutableMap<String, List<String>>>()
        try {
            if (file.exists()) {
                val json = JSONObject(file.readText())
                for (bookId in json.keys()) {
                    val bookObj = json.optJSONObject(bookId) ?: continue
                    val perAnnotation = mutableMapOf<String, List<String>>()
                    for (annotationId in bookObj.keys()) {
                        val array = bookObj.optJSONArray(annotationId) ?: continue
                        val tags = (0 until array.length()).mapNotNull { index ->
                            array.optString(index).takeIf { it.isNotBlank() }
                        }
                        if (tags.isNotEmpty()) perAnnotation[annotationId] = tags
                    }
                    if (perAnnotation.isNotEmpty()) root[bookId] = perAnnotation
                }
            }
        } catch (e: Exception) {
            Timber.e(e, "Failed to load annotation tags")
        }
        cache = root
        return root
    }

    private fun saveLocked(root: Map<String, Map<String, List<String>>>) {
        try {
            val json = JSONObject()
            for ((bookId, perAnnotation) in root) {
                val bookObj = JSONObject()
                for ((annotationId, tags) in perAnnotation) {
                    bookObj.put(annotationId, JSONArray(tags))
                }
                json.put(bookId, bookObj)
            }
            file.parentFile?.mkdirs()
            file.writeText(json.toString())
        } catch (e: Exception) {
            Timber.e(e, "Failed to save annotation tags")
        }
    }

    companion object {
        @Volatile
        private var instance: AnnotationTagStore? = null

        fun get(context: Context): AnnotationTagStore =
            instance ?: synchronized(this) {
                instance ?: AnnotationTagStore(
                    File(context.applicationContext.filesDir, "whitebear/annotation_tags.json")
                ).also { instance = it }
            }
    }
}
