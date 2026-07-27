package com.aryan.reader.whitebear

import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.database.Cursor
import android.net.Uri
import android.os.SystemClock
import android.util.Base64
import android.util.Log
import androidx.documentfile.provider.DocumentFile
import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.sqlite.db.SupportSQLiteStatement
import com.aryan.reader.BuildConfig
import com.aryan.reader.data.AppDatabase
import org.json.JSONArray
import org.json.JSONObject
import java.io.BufferedReader
import java.io.File
import java.io.IOException
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
     * independently of it; one with [defaultSelected] false is offered but left unticked, so
     * it goes in only when it is asked for by name.
     */
    enum class Cat(
        val id: String,
        val label: String,
        val parentId: String? = null,
        val defaultSelected: Boolean = true,
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
        LIBRARY_FONTS(
            "library.fonts", "Custom fonts", parentId = "library", progressUnit = "字体",
            tables = listOf("custom_fonts"), files = listOf(FileSpec("custom_fonts"))
        ),

        /**
         * Its own item, and unticked until asked for (白い熊, 2026-07-27).
         *
         * The covers are thousands of files and the bulk of an archive's bytes, and unlike
         * everything else here they are derived from the book files rather than authored — so
         * they are the one part worth leaving out of a routine backup, and the one part whose
         * absence costs nothing that cannot be made again. Last in the enum, so it is also last
         * into the ZIP: whatever goes wrong while writing them, everything irreplaceable is
         * already in the archive.
         */
        LIBRARY_COVERS(
            "library.covers", "Book covers — cached cover images",
            defaultSelected = false, progressUnit = "表紙",
            files = listOf(FileSpec("cover_cache"))
        );

        /** The label without its trailing explanation — what progress lines show. */
        val shortLabel: String get() = label.substringBefore(" —")

        val children: List<Cat> get() = entries.filter { it.parentId == id }
    }

    fun catById(id: String): Cat? = Cat.entries.firstOrNull { it.id == id }

    /** What a request that names no categories gets: everything ticked by default. */
    fun defaultCats(): Set<Cat> = Cat.entries.filter { it.defaultSelected }.toSet()

    /**
     * `id<TAB>label<TAB>parent-id<TAB>on|off` per line — the automation LIST_CATEGORIES payload.
     *
     * All four fields, always: the third is empty for a top-level category, and the fourth is
     * how 自由作業盤's picker knows to open with 「Book covers」 unticked. The first three keep
     * their meaning and order, so a reader that only knows about three still works.
     */
    fun categoryLines(): String = Cat.entries.joinToString("\n") { cat ->
        "${cat.id}\t${cat.label}\t${cat.parentId.orEmpty()}\t${if (cat.defaultSelected) "on" else "off"}"
    }

    /** Reports work done while exporting — real counts, never a percentage. */
    fun interface Progress {
        fun report(current: Long, total: Long, unit: String, text: String)
    }

    // ---- The leash: why a run can no longer write forever, or write nothing forever ----

    /** One entry's share of a run — a file still copying after this is not going to finish. */
    private const val PER_ENTRY_MS = 60_000L

    /** Slack over a file's own length before its stream is taken for one that never EOFs. */
    private const val SIZE_SLACK = 1L shl 20

    /** Rows past the count a table just reported before its cursor is taken for a loop. */
    private const val ROW_SLACK = 5_000L

    /** Entries abandoned back to back before the whole run is called off. */
    private const val MAX_TIMEOUTS_IN_A_ROW = 5

    private const val COPY_BUFFER = 64 * 1024

    /** `cover_cache` and the annotation directories are flat; deeper than this is a loop. */
    private const val MAX_WALK_DEPTH = 6

    /**
     * The export's leash — what keeps one bad entry from costing the whole backup.
     *
     * Every entry announces itself through [enter], so whatever the run is on is always
     * nameable from outside (the service puts it in its stall error and in logcat, which is how
     * a hang gets identified at all). Every copy loop asks [overrun] between chunks and gives up
     * on what will not finish; what it gives up on is counted in [skipped], so a partial backup
     * says it is partial instead of being silently lossy. [checkCeiling] ends a run that has
     * simply gone on too long with an error rather than a silence.
     *
     * None of this can rescue a syscall that never returns — nothing inside the process can.
     * That is the service watchdog's job. The leash bounds everything that *does* come back.
     */
    class Leash(
        private val perEntryMs: Long = PER_ENTRY_MS,
        private val ceilingMs: Long = Long.MAX_VALUE,
        private val onEnter: (String) -> Unit = {}
    ) {
        private val startedAt = SystemClock.elapsedRealtime()
        private var entryStartedAt = startedAt
        private var timeoutsInARow = 0
        private val abandoned = mutableListOf<String>()

        /** Entries given up on, in ZIP order. */
        val skipped: List<String> get() = abandoned

        /** Name what is about to be written and restart its clock. Never throws. */
        fun enter(entry: String) {
            entryStartedAt = SystemClock.elapsedRealtime()
            onEnter(entry)
        }

        /** True once this entry has had its share of the run — stop copying and move on. */
        fun overrun(): Boolean =
            SystemClock.elapsedRealtime() - entryStartedAt > perEntryMs || exhausted()

        /** True once the run as a whole is out of time — finish the current step and get out. */
        fun exhausted(): Boolean = SystemClock.elapsedRealtime() - startedAt > ceilingMs

        /**
         * End the run when it is out of time. Called only from the category loop, never from
         * inside a `runCatching`, so the error reaches the caller instead of becoming a skip.
         */
        fun checkCeiling() {
            if (exhausted()) throw IOException("export timed out after ${ceilingMs / 1000L} s")
        }

        /**
         * Record an entry we are not waiting for. A handful of timeouts in a row is no longer
         * one bad file — it is the target we are writing to, and finishing would only produce a
         * shell of a backup reported as a good one.
         */
        fun skip(entry: String, why: String, timedOut: Boolean = false) {
            Log.w(AutomationWire.TAG, "skipped $entry — $why")
            abandoned += entry
            if (!timedOut) return
            if (++timeoutsInARow >= MAX_TIMEOUTS_IN_A_ROW) {
                throw IOException("export stalled — $timeoutsInARow entries in a row timed out at $entry")
            }
        }

        /** An entry that finished normally — the run is healthy again. */
        fun done() {
            timeoutsInARow = 0
        }
    }

    /** What a run produced: how many categories, and what it had to give up on. */
    data class Outcome(val categories: Int, val skipped: List<String>) {
        /** The one line the automation reply and the export sheet both show. */
        val summary: String
            get() = "$categories ${if (categories == 1) "category" else "categories"}" +
                if (skipped.isEmpty()) {
                    ""
                } else {
                    " (${skipped.size} ${if (skipped.size == 1) "entry" else "entries"} skipped)"
                }
    }

    /** Write a ZIP of the selected categories to [out], bounded by [leash]. */
    fun export(
        context: Context,
        cats: Set<Cat>,
        out: OutputStream,
        onProgress: Progress? = null,
        leash: Leash = Leash()
    ): Outcome {
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
                leash.checkCeiling()
                leash.enter(cat.id)
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
                if (cat.tables.isNotEmpty()) writeTables(context, zip, cat, onProgress, leash)
                if (cat.files.isNotEmpty()) writeFiles(context, zip, cat, onProgress, leash)
            }
            // Out of time on the last category too: an error, never a quietly half-full ZIP
            // reported as a backup.
            leash.checkCeiling()
            onProgress?.report(
                ordered.size.toLong(), ordered.size.toLong(), "区分",
                "区分 ${ordered.size}/${ordered.size} — 完了"
            )
        }
        return Outcome(cats.size, leash.skipped)
    }

    /** The category an entry belongs to, by the naming [export] writes. */
    private fun catOf(name: String, among: Collection<Cat>): Cat? = among.firstOrNull { c ->
        name == "${c.id}.json" || name == "${c.id}.jsonl" || name.startsWith("${c.id}/")
    }

    /**
     * What an archive actually carries — the categories an import may offer.
     *
     * Read from the entry names rather than from `manifest.json`: the manifest records what a
     * run set out to write, and a run that had to skip its way through the covers still lists
     * them there. What can be restored is what is really in the file.
     */
    fun categoriesIn(openZip: () -> InputStream): Set<Cat> {
        val found = linkedSetOf<Cat>()
        ZipInputStream(openZip()).use { zip ->
            var entry = zip.nextEntry
            while (entry != null) {
                if (!entry.isDirectory) catOf(entry.name, Cat.entries)?.let { found += it }
                if (found.size == Cat.entries.size) break
                zip.closeEntry()
                entry = zip.nextEntry
            }
        }
        return found
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
                    val cat = catOf(name, cats)
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

    private fun writeTables(
        context: Context,
        zip: ZipOutputStream,
        cat: Cat,
        onProgress: Progress?,
        leash: Leash
    ) {
        val database = db(context)
        zip.putNextEntry(ZipEntry("${cat.id}.jsonl"))
        val writer = OutputStreamWriter(zip, Charsets.UTF_8)
        for (table in cat.tables) {
            leash.enter("${cat.id}.jsonl: $table")
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
                            // Also the liveness tick: a table this long must keep saying so,
                            // or the watchdog is right to call the run dead.
                            leash.enter("${cat.id}.jsonl: $table $done/$total")
                            onProgress?.report(
                                done, total, cat.progressUnit, "${cat.progressUnit} $done/$total"
                            )
                        }
                        // A cursor still yielding rows well past the count the same table just
                        // reported is not a cursor we keep reading.
                        if (done > total + ROW_SLACK || leash.exhausted()) break
                    }
                    onProgress?.report(done, total, cat.progressUnit, "${cat.progressUnit} $done/$total")
                }
            }
        }
        writer.flush()
        zip.closeEntry()
        leash.done()
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
                // Bounded depth: these directories are flat, so a walk that goes deeper is
                // following a symlink back into itself and would never come out.
                base.walkTopDown().maxDepth(MAX_WALK_DEPTH).filter { it.isFile }
                    .forEach { found += it }
            }
        }
        // The same file reached through two specs would be a duplicate ZIP entry, which throws
        // and costs the entries after it.
        return found.distinctBy { it.absolutePath }
    }

    private fun writeFiles(
        context: Context,
        zip: ZipOutputStream,
        cat: Cat,
        onProgress: Progress?,
        leash: Leash
    ) {
        val root = context.filesDir
        leash.enter("${cat.id}: scanning")
        val files = collectFiles(context, cat)
        val total = files.size.toLong()
        for ((index, file) in files.withIndex()) {
            if (leash.exhausted()) break
            val relative = file.toRelativeString(root).replace(File.separatorChar, '/')
            val entry = "${cat.id}/$relative"
            leash.enter(entry)
            val copied = runCatching { copyEntry(zip, file, entry, leash) }
            when {
                copied.isFailure ->
                    leash.skip(entry, copied.exceptionOrNull()?.message ?: "unreadable")
                copied.getOrDefault(false) -> leash.done()
                else -> leash.skip(entry, "did not finish in time", timedOut = true)
            }
            val done = (index + 1).toLong()
            if (done % 25L == 0L || done == total) {
                onProgress?.report(done, total, cat.progressUnit, "${cat.progressUnit} $done/$total")
            }
        }
    }

    /**
     * Copy one file into the archive, and stop copying rather than wait forever: a stream that
     * keeps yielding well past the length its own file reports is one that will never EOF, and
     * a copy that outlives its slice of the run is one nothing is gained by waiting on. Either
     * way the entry is closed, so the archive stays readable — `false` says it is short, and the
     * caller counts it as skipped. Returns true when the whole file went in.
     */
    private fun copyEntry(zip: ZipOutputStream, file: File, entry: String, leash: Leash): Boolean {
        var complete = false
        zip.putNextEntry(ZipEntry(entry))
        try {
            val cap = file.length() + SIZE_SLACK
            var copied = 0L
            val buffer = ByteArray(COPY_BUFFER)
            file.inputStream().use { input ->
                while (true) {
                    val read = input.read(buffer)
                    if (read < 0) {
                        complete = true
                        break
                    }
                    zip.write(buffer, 0, read)
                    copied += read
                    if (copied > cap || leash.overrun()) break
                }
            }
        } finally {
            runCatching { zip.closeEntry() }
        }
        return complete
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
