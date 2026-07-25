package com.aryan.reader.whitebear

import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.database.Cursor
import android.net.Uri
import android.util.Base64
import androidx.documentfile.provider.DocumentFile
import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.sqlite.db.SupportSQLiteStatement
import com.aryan.reader.BuildConfig
import com.aryan.reader.data.AppDatabase
import org.json.JSONArray
import org.json.JSONObject
import java.io.BufferedReader
import java.io.File
import java.io.InputStream
import java.io.InputStreamReader
import java.io.OutputStream
import java.io.OutputStreamWriter
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

/**
 * 白い熊 export/import of everything the app holds, organized by category — the same idea
 * and flow as the sister forks: ONE ZIP per app, carrying a manifest.json plus one entry
 * per category. Settings categories are plain JSON dumps of whole SharedPreferences files
 * (type-tagged, so any keyset round-trips); library categories are JSON-lines dumps of the
 * Room tables and verbatim copies of the per-book sidecar files. Import merges key by key
 * and row by row (never clears), and categories missing from the ZIP are skipped, keeping
 * old exports importable forever.
 */
object WhiteBearExport {

    const val FORMAT = "shosekietsuran-export"
    const val VERSION = 2

    /**
     * Family-wide backup name (白い熊, 2026-07-25): `shiroikuma-shosekietsuran_<stamp>.zip`,
     * no version and no decoration, so every sister app's backups sort and read uniformly
     * in one directory.
     */
    const val EXPORT_PREFIX = "shiroikuma-shosekietsuran_"

    /** The pre-2026-07-25 name (`…-<version>-export_<stamp>.zip`) — still recognised. */
    private const val LEGACY_EXPORT_PREFIX = "shiroikuma-shosekietsuran-"

    /** Device-local keys never exported nor imported (ids, sync/session/tab state). */
    private val DEVICE_LOCAL_KEYS = setOf(
        "installation_id", "last_sync_timestamp", "app_open_count", "open_tab_ids",
        "active_tab_book_id", "last_open_book_id", "last_open_file_type",
        "last_folder_scan_time", "pending_external_file_removals", "missing_drive_permissions"
    )
    private val DEVICE_LOCAL_PREFIXES = listOf("imported_file_", "opds_stream_", "credits_")

    /** A file or directory under `filesDir` carried verbatim inside the ZIP. */
    internal data class FileSpec(val dir: String, val namePrefix: String? = null)

    /**
     * A selectable category. [id] is the ZIP entry name (`<id>.json` for prefs, `<id>.jsonl`
     * for tables, `<id>/…` for files) and the id accepted in the automation `items` extra.
     * A category with a [parentId] is a sub-option of that category and is selected
     * independently of it.
     */
    enum class Cat(
        val id: String,
        val label: String,
        val parentId: String? = null,
        val progressUnit: String = "区分",
        internal val prefsFiles: List<String> = emptyList(),
        internal val tables: List<String> = emptyList(),
        internal val files: List<FileSpec> = emptyList()
    ) {
        WB_UI(
            "wb_ui", "白い熊 UI — colors, fonts, shapes",
            prefsFiles = listOf("whitebear_ui_prefs")
        ),
        WB_GESTURES(
            "wb_gestures", "白い熊 gestures & page turning",
            prefsFiles = listOf("whitebear_gesture_prefs")
        ),
        WB_LIBRARY(
            "wb_library", "白い熊 library view",
            prefsFiles = listOf("whitebear_library_prefs")
        ),
        WB_WRITING(
            "wb_writing", "白い熊 writing 縦書き",
            prefsFiles = listOf("whitebear_writing_prefs")
        ),
        APP_SETTINGS(
            "app_settings", "App settings — theme, font, behavior",
            prefsFiles = listOf("reader_user_prefs")
        ),
        READER_SETTINGS(
            "reader_settings", "Reader settings — EPUB, PDF, TTS, annotations",
            prefsFiles = listOf("epub_reader_settings", "reader_prefs", "annotation_settings_global")
        ),
        LIBRARY(
            "library", "Book library — books, reading positions, bookmarks",
            progressUnit = "書籍",
            tables = listOf("recent_files")
        ),
        LIBRARY_SHELVES(
            "library.shelves", "Shelves & tags", parentId = "library", progressUnit = "行",
            tables = listOf("shelves", "tags", "book_shelf_cross_ref", "book_tag_cross_ref")
        ),
        LIBRARY_ANNOTATIONS(
            "library.annotations", "Annotations, notes, text boxes & page layouts",
            parentId = "library", progressUnit = "注釈",
            files = listOf(
                FileSpec("annotations"), FileSpec("pdf_highlights"), FileSpec("textboxes"),
                FileSpec("page_layouts"), FileSpec("whitebear"), FileSpec("", "rich_doc_")
            )
        ),
        LIBRARY_COVERS(
            "library.covers", "Cover images", parentId = "library", progressUnit = "表紙",
            files = listOf(FileSpec("cover_cache"))
        ),
        LIBRARY_FONTS(
            "library.fonts", "Custom fonts", parentId = "library", progressUnit = "字体",
            tables = listOf("custom_fonts"), files = listOf(FileSpec("custom_fonts"))
        );

        /** The label without its trailing explanation — what progress lines show. */
        val shortLabel: String get() = label.substringBefore(" —")

        val children: List<Cat> get() = entries.filter { it.parentId == id }
    }

    fun catById(id: String): Cat? = Cat.entries.firstOrNull { it.id == id }

    /** `id<TAB>label[<TAB>parent-id]` per line — the automation LIST_CATEGORIES payload. */
    fun categoryLines(): String = Cat.entries.joinToString("\n") { cat ->
        if (cat.parentId != null) "${cat.id}\t${cat.label}\t${cat.parentId}" else "${cat.id}\t${cat.label}"
    }

    /** Reports work done while exporting — real counts, never a percentage. */
    fun interface Progress {
        fun report(current: Long, total: Long, unit: String, text: String)
    }

    /** Write a ZIP of the selected categories to [out]. Returns a short human summary. */
    fun export(
        context: Context,
        cats: Set<Cat>,
        out: OutputStream,
        onProgress: Progress? = null
    ): String {
        val ordered = Cat.entries.filter { it in cats }
        ZipOutputStream(out).use { zip ->
            val manifest = JSONObject()
                .put("format", FORMAT)
                .put("version", VERSION)
                .put("app", context.packageName)
                .put("appVersion", BuildConfig.VERSION_NAME)
                .put("createdTs", System.currentTimeMillis())
                .put("categories", JSONArray(ordered.map { it.id }))
            writeEntry(zip, "manifest.json", manifest.toString(2))

            ordered.forEachIndexed { index, cat ->
                onProgress?.report(
                    index.toLong(), ordered.size.toLong(), "区分",
                    "区分 ${index + 1}/${ordered.size} — ${cat.shortLabel}"
                )
                if (cat.prefsFiles.isNotEmpty()) {
                    val json = JSONObject()
                    cat.prefsFiles.forEach { name ->
                        json.put(
                            name,
                            prefsToJson(context.getSharedPreferences(name, Context.MODE_PRIVATE))
                        )
                    }
                    writeEntry(zip, "${cat.id}.json", json.toString(2))
                }
                if (cat.tables.isNotEmpty()) writeTables(context, zip, cat, onProgress)
                if (cat.files.isNotEmpty()) writeFiles(context, zip, cat, onProgress)
            }
            onProgress?.report(
                ordered.size.toLong(), ordered.size.toLong(), "区分",
                "区分 ${ordered.size}/${ordered.size} — 完了"
            )
        }
        return "${cats.size} ${if (cats.size == 1) "category" else "categories"}"
    }

    /**
     * Apply the selected categories from a ZIP, streaming it entry by entry so a backup
     * carrying covers and annotations never has to fit in memory. Entries for categories
     * that are absent from the ZIP or unselected are skipped. Returns a summary.
     */
    fun import(context: Context, openZip: () -> InputStream, cats: Set<Cat>): String {
        val counts = linkedMapOf<Cat, Int>()
        ZipInputStream(openZip()).use { zip ->
            var entry = zip.nextEntry
            while (entry != null) {
                val name = entry.name
                if (!entry.isDirectory && !name.contains("..")) {
                    val cat = cats.firstOrNull { c ->
                        name == "${c.id}.json" || name == "${c.id}.jsonl" || name.startsWith("${c.id}/")
                    }
                    if (cat != null) {
                        val added = runCatching {
                            when {
                                name.endsWith(".jsonl") -> importTables(context, zip)
                                name.endsWith(".json") && name == "${cat.id}.json" ->
                                    importPrefs(context, cat, zip.readBytes())
                                else -> importFile(context, cat, name, zip)
                            }
                        }.getOrDefault(0)
                        counts[cat] = (counts[cat] ?: 0) + added
                    }
                }
                zip.closeEntry()
                entry = zip.nextEntry
            }
        }
        if (counts.isEmpty()) return "Nothing imported."
        return counts.entries.joinToString("\n") { (cat, n) -> "${cat.shortLabel}: $n" }
    }

    fun exportFileName(): String =
        EXPORT_PREFIX + SimpleDateFormat("yyyy-MM-dd_HH-mm-ss", Locale.ROOT).format(Date()) + ".zip"

    /** `4.6 MB`, `1.20 GB` — the display size the automation reply carries. */
    fun humanSize(bytes: Long): String {
        val kb = 1024.0
        return when {
            bytes < kb -> "$bytes B"
            bytes < kb * kb -> String.format(Locale.ROOT, "%.1f KB", bytes / kb)
            bytes < kb * kb * kb -> String.format(Locale.ROOT, "%.1f MB", bytes / (kb * kb))
            else -> String.format(Locale.ROOT, "%.2f GB", bytes / (kb * kb * kb))
        }
    }

    // ---- The persisted export directory (device-local; deliberately never exported) ----

    private const val EXIM_PREFS = "whitebear_eximport"
    private const val KEY_DIR_URI = "dir_uri"

    fun dirUri(context: Context): Uri? =
        context.getSharedPreferences(EXIM_PREFS, Context.MODE_PRIVATE)
            .getString(KEY_DIR_URI, null)?.let { runCatching { Uri.parse(it) }.getOrNull() }

    fun setDirUri(context: Context, uri: Uri) {
        runCatching {
            context.contentResolver.takePersistableUriPermission(
                uri, Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
            )
        }
        context.getSharedPreferences(EXIM_PREFS, Context.MODE_PRIVATE)
            .edit().putString(KEY_DIR_URI, uri.toString()).apply()
    }

    fun exportDir(context: Context): DocumentFile? =
        dirUri(context)?.let { runCatching { DocumentFile.fromTreeUri(context, it) }.getOrNull() }
            ?.takeIf { it.isDirectory }

    fun dirDisplayName(context: Context): String? =
        exportDir(context)?.name ?: dirUri(context)?.lastPathSegment

    /** (message, isWarning) for the "last export" line — newest matching file in the directory. */
    fun lastExportStatus(context: Context): Pair<String, Boolean> {
        val dir = exportDir(context)
            ?: return "No directory set yet — pick one to enable one-tap export." to true
        val newest = runCatching {
            dir.listFiles().filter { file ->
                val name = file.name
                file.isFile && name != null && name.endsWith(".zip") &&
                    (name.startsWith(EXPORT_PREFIX) || name.startsWith(LEGACY_EXPORT_PREFIX))
            }.maxByOrNull { it.lastModified() }
        }.getOrNull() ?: return "No export in this directory yet." to true
        val ts = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.ROOT).format(Date(newest.lastModified()))
        return "Last export: $ts" to false
    }

    // ---- Generic type-tagged SharedPreferences serialization ----

    private fun excluded(key: String): Boolean =
        key in DEVICE_LOCAL_KEYS || DEVICE_LOCAL_PREFIXES.any { key.startsWith(it) }

    private fun prefsToJson(prefs: SharedPreferences): JSONObject {
        val json = JSONObject()
        prefs.all.forEach { (key, value) ->
            if (excluded(key)) return@forEach
            val entry = JSONObject()
            when (value) {
                is Boolean -> entry.put("t", "b").put("v", value)
                is Int -> entry.put("t", "i").put("v", value)
                is Long -> entry.put("t", "l").put("v", value)
                is Float -> entry.put("t", "f").put("v", value.toDouble())
                is String -> entry.put("t", "s").put("v", value)
                is Set<*> -> entry.put("t", "ss").put("v", JSONArray(value.map { it.toString() }))
                else -> return@forEach
            }
            json.put(key, entry)
        }
        return json
    }

    /** Merge: each key is put back by its type tag; keys absent from the JSON keep their value. */
    private fun jsonToPrefs(prefs: SharedPreferences, json: JSONObject): Int {
        var count = 0
        val editor = prefs.edit()
        json.keys().forEach { key ->
            if (excluded(key)) return@forEach
            val entry = json.optJSONObject(key) ?: return@forEach
            when (entry.optString("t")) {
                "b" -> editor.putBoolean(key, entry.getBoolean("v"))
                "i" -> editor.putInt(key, entry.getInt("v"))
                "l" -> editor.putLong(key, entry.getLong("v"))
                "f" -> editor.putFloat(key, entry.getDouble("v").toFloat())
                "s" -> editor.putString(key, entry.getString("v"))
                "ss" -> {
                    val arr = entry.getJSONArray("v")
                    editor.putStringSet(key, (0 until arr.length()).mapTo(mutableSetOf()) { arr.getString(it) })
                }
                else -> return@forEach
            }
            count++
        }
        editor.apply()
        return count
    }

    private fun importPrefs(context: Context, cat: Cat, bytes: ByteArray): Int {
        val json = JSONObject(bytes.decodeToString())
        var count = 0
        cat.prefsFiles.forEach { name ->
            json.optJSONObject(name)?.let { prefsJson ->
                count += jsonToPrefs(context.getSharedPreferences(name, Context.MODE_PRIVATE), prefsJson)
            }
        }
        return count
    }

    // ---- Room tables as JSON lines: a header object per table, then one array per row ----

    private fun db(context: Context): SupportSQLiteDatabase =
        AppDatabase.getDatabase(context).openHelper.writableDatabase

    private fun writeTables(context: Context, zip: ZipOutputStream, cat: Cat, onProgress: Progress?) {
        val database = db(context)
        zip.putNextEntry(ZipEntry("${cat.id}.jsonl"))
        val writer = OutputStreamWriter(zip, Charsets.UTF_8)
        for (table in cat.tables) {
            val total = rowCount(database, table) ?: continue
            runCatching {
                database.query("SELECT * FROM `$table`").use { cursor ->
                    val columns = cursor.columnNames
                    writer.write(
                        JSONObject()
                            .put("table", table)
                            .put("columns", JSONArray(columns.toList()))
                            .put("rows", total)
                            .toString()
                    )
                    writer.write("\n")
                    var done = 0L
                    while (cursor.moveToNext()) {
                        val row = JSONArray()
                        for (i in columns.indices) row.put(cursorValue(cursor, i))
                        writer.write(row.toString())
                        writer.write("\n")
                        done++
                        if (done % 50L == 0L) {
                            onProgress?.report(
                                done, total, cat.progressUnit, "${cat.progressUnit} $done/$total"
                            )
                        }
                    }
                    onProgress?.report(done, total, cat.progressUnit, "${cat.progressUnit} $done/$total")
                }
            }
        }
        writer.flush()
        zip.closeEntry()
    }

    private fun rowCount(database: SupportSQLiteDatabase, table: String): Long? = runCatching {
        database.query("SELECT COUNT(*) FROM `$table`").use { c ->
            if (c.moveToFirst()) c.getLong(0) else 0L
        }
    }.getOrNull()

    private fun cursorValue(cursor: Cursor, index: Int): Any = when (cursor.getType(index)) {
        Cursor.FIELD_TYPE_NULL -> JSONObject.NULL
        Cursor.FIELD_TYPE_INTEGER -> cursor.getLong(index)
        Cursor.FIELD_TYPE_FLOAT -> cursor.getDouble(index)
        Cursor.FIELD_TYPE_BLOB ->
            JSONObject().put("b64", Base64.encodeToString(cursor.getBlob(index), Base64.NO_WRAP))
        else -> cursor.getString(index) ?: JSONObject.NULL
    }

    /**
     * Restore rows table by table. Columns the current schema no longer has are dropped, so
     * an older backup still imports; rows whose foreign keys are missing (shelf entries for a
     * book that was not restored) are skipped instead of failing the whole category.
     */
    private fun importTables(context: Context, input: InputStream): Int {
        val database = db(context)
        val reader = BufferedReader(InputStreamReader(input, Charsets.UTF_8))
        var imported = 0
        var columns: List<String> = emptyList()
        var keptIndices: List<Int> = emptyList()
        var statement: SupportSQLiteStatement? = null

        fun finishTable() {
            statement?.close()
            statement = null
        }

        // One transaction for the whole entry — a per-row commit would take minutes on a
        // library of thousands of books. A row that fails its foreign keys aborts only
        // itself, so the rest of the restore still lands.
        database.beginTransaction()
        try {
            while (true) {
                val line = reader.readLine() ?: break
                if (line.isBlank()) continue
                if (line.startsWith("{")) {
                    finishTable()
                    val header = JSONObject(line)
                    val table = header.getString("table")
                    val declared = header.getJSONArray("columns")
                    columns = (0 until declared.length()).map { declared.getString(it) }
                    val existing = tableColumns(database, table)
                    keptIndices = columns.indices.filter { columns[it] in existing }
                    if (keptIndices.isEmpty()) continue
                    val kept = keptIndices.map { columns[it] }
                    statement = runCatching {
                        database.compileStatement(
                            "INSERT OR REPLACE INTO `$table` (" +
                                kept.joinToString(", ") { "`$it`" } + ") VALUES (" +
                                kept.joinToString(", ") { "?" } + ")"
                        )
                    }.getOrNull()
                    continue
                }
                val target = statement ?: continue
                runCatching {
                    val row = JSONArray(line)
                    target.clearBindings()
                    keptIndices.forEachIndexed { slot, source ->
                        bind(target, slot + 1, if (source < row.length()) row.get(source) else JSONObject.NULL)
                    }
                    target.executeInsert()
                    imported++
                }
            }
            finishTable()
            database.setTransactionSuccessful()
        } finally {
            finishTable()
            database.endTransaction()
        }
        return imported
    }

    private fun tableColumns(database: SupportSQLiteDatabase, table: String): Set<String> = runCatching {
        database.query("PRAGMA table_info(`$table`)").use { c ->
            val nameIndex = c.getColumnIndex("name")
            buildSet { while (c.moveToNext()) add(c.getString(nameIndex)) }
        }
    }.getOrDefault(emptySet())

    private fun bind(statement: SupportSQLiteStatement, index: Int, value: Any?) {
        when (value) {
            null, JSONObject.NULL -> statement.bindNull(index)
            is Boolean -> statement.bindLong(index, if (value) 1L else 0L)
            is Int -> statement.bindLong(index, value.toLong())
            is Long -> statement.bindLong(index, value)
            is Double -> statement.bindDouble(index, value)
            is Float -> statement.bindDouble(index, value.toDouble())
            is JSONObject -> statement.bindBlob(index, Base64.decode(value.getString("b64"), Base64.NO_WRAP))
            else -> statement.bindString(index, value.toString())
        }
    }

    // ---- Per-book sidecar files, carried verbatim under `<category id>/<path in filesDir>` ----

    private fun collectFiles(context: Context, cat: Cat): List<File> {
        val root = context.filesDir
        val found = mutableListOf<File>()
        for (spec in cat.files) {
            val base = if (spec.dir.isEmpty()) root else File(root, spec.dir)
            if (!base.isDirectory) continue
            if (spec.namePrefix != null) {
                base.listFiles()
                    ?.filter { it.isFile && it.name.startsWith(spec.namePrefix) }
                    ?.let { found += it }
            } else {
                base.walkTopDown().filter { it.isFile }.forEach { found += it }
            }
        }
        return found
    }

    private fun writeFiles(context: Context, zip: ZipOutputStream, cat: Cat, onProgress: Progress?) {
        val root = context.filesDir
        val files = collectFiles(context, cat)
        val total = files.size.toLong()
        files.forEachIndexed { index, file ->
            val relative = file.toRelativeString(root).replace(File.separatorChar, '/')
            runCatching {
                zip.putNextEntry(ZipEntry("${cat.id}/$relative"))
                file.inputStream().use { it.copyTo(zip) }
                zip.closeEntry()
            }
            val done = (index + 1).toLong()
            if (done % 25L == 0L || done == total) {
                onProgress?.report(done, total, cat.progressUnit, "${cat.progressUnit} $done/$total")
            }
        }
    }

    private fun importFile(context: Context, cat: Cat, entryName: String, input: InputStream): Int {
        val relative = entryName.removePrefix("${cat.id}/")
        if (relative.isEmpty() || relative.contains("..")) return 0
        val target = File(context.filesDir, relative)
        target.parentFile?.mkdirs()
        target.outputStream().use { input.copyTo(it) }
        return 1
    }

    private fun writeEntry(zip: ZipOutputStream, name: String, content: String) {
        zip.putNextEntry(ZipEntry(name))
        zip.write(content.toByteArray())
        zip.closeEntry()
    }
}
