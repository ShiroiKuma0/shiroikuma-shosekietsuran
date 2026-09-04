package com.aryan.reader.whitebear

import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.database.Cursor
import android.net.Uri
import android.os.SystemClock
import android.provider.DocumentsContract
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

    /**
     * One line of progress: real counts, never a percentage.
     *
     * [bytes] / [bytesTotal] are the same work measured the other way, and are 0 where a step
     * cannot know them. They exist for the cover pass: thousands of files, where the count of
     * files says nothing about how much of the archive is written, and where 自由作業盤 draws the
     * byte pair as the one that really moves.
     */
    data class Step(
        val current: Long,
        val total: Long,
        val unit: String,
        val text: String,
        val bytes: Long = 0L,
        val bytesTotal: Long = 0L
    )

    /** Reports work done while exporting. */
    fun interface Progress {
        fun report(step: Step)
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
        private val isCancelled: () -> Boolean = { false },
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
            isCancelled() || SystemClock.elapsedRealtime() - entryStartedAt > perEntryMs || exhausted()

        /** True once the run as a whole is out of time — finish the current step and get out. */
        fun exhausted(): Boolean = SystemClock.elapsedRealtime() - startedAt > ceilingMs

        /** True once 白い熊 has called this run off. */
        fun cancelled(): Boolean = isCancelled()

        /**
         * Unwind a run 白い熊 called off, by throwing where the caller can act on it.
         *
         * Called between entries and nowhere else. A cancel is not an emergency and must never be
         * served by interrupting the thread, closing the descriptor under it or killing the
         * process: those are the things that leave a half-written archive behind, which is the
         * one outcome a cancel exists to avoid. So the copy loops merely stop early — see
         * [overrun] — and the unwinding is done here, in a place where the ZIP is between entries
         * and the caller can take its partial file away with it.
         */
        fun checkCancelled() {
            if (isCancelled()) throw Cancelled()
        }

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

    /**
     * Thrown to end a run 白い熊 pressed 中止 on — not a failure of the export, and told apart
     * from one by its type: whoever answers for the run says 「cancelled」 rather than reporting
     * an error nothing went wrong to cause.
     */
    class Cancelled : IOException("cancelled")

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
                leash.checkCancelled()
                leash.enter(cat.id)
                onProgress?.report(
                    Step(
                        index.toLong(), ordered.size.toLong(), "区分",
                        "区分 ${index + 1}/${ordered.size} — ${cat.shortLabel}"
                    )
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
            // reported as a backup. And the last moment a 中止 can still take the archive with
            // it — after this the file is placed under its real name and the run has won.
            leash.checkCeiling()
            leash.checkCancelled()
            onProgress?.report(
                Step(
                    ordered.size.toLong(), ordered.size.toLong(), "区分",
                    "区分 ${ordered.size}/${ordered.size} — 完了"
                )
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

    // ---- Nothing wears a backup's name until it is a backup ----

    /**
     * What an export is called while it is still being written.
     *
     * An interrupted export — killed, crashed, out of disk — leaves behind whatever it managed to
     * write. Under the *final* name that is worse than leaving nothing: 白い熊 keeps every app's
     * backups in one directory sorted by date, so a truncated archive becomes "the latest backup"
     * and stays that way until the day it is needed. (Three of them elsewhere in the family on
     * 2026-07-28 — 454 MB, 1007 MB, 1072 MB, none with an end-of-central-directory, all wearing
     * good names, all deleted by hand.) So every writer here creates `<name>.part`, streams into
     * that, and moves it onto the real name only once the archive is closed and whole. The
     * temporary lives in the destination directory and never in a cache directory to be copied
     * across afterwards: same filesystem is what makes the move atomic and instant.
     */
    const val PART_SUFFIX = ".part"

    /**
     * `application/octet-stream`, deliberately. SAF forces a created name to carry an extension
     * matching the type it was created with, so asking for `<name>.zip.part` as a zip yields
     * `<name>.zip.part.zip`; octet-stream matches any extension and leaves the name alone. What
     * the finished file reports is read from its extension, so the type is right again after the
     * rename.
     */
    private const val PART_MIME = "application/octet-stream"

    /** Longer than any run may last: past this a `.part` is a corpse, not a live export. */
    private const val PART_STALE_MS = 60L * 60L * 1000L

    fun partName(name: String): String = name + PART_SUFFIX

    private fun stalePart(name: String?, lastModified: Long): Boolean =
        name != null && name.startsWith(EXPORT_PREFIX) && name.endsWith(".zip$PART_SUFFIX") &&
            System.currentTimeMillis() - lastModified > PART_STALE_MS

    /**
     * Take away what a killed run left behind. The `finally` that deletes a partial cannot run
     * when the process itself is gone, so the next export sweeps for it — matched by our own
     * prefix and by age, so an export running right now is never the one swept.
     */
    fun sweepStaleParts(dir: File) {
        runCatching {
            dir.listFiles()?.forEach { file ->
                if (file.isFile && stalePart(file.name, file.lastModified())) file.delete()
            }
        }
    }

    fun sweepStaleParts(dir: DocumentFile) {
        runCatching {
            dir.listFiles().forEach { file ->
                if (file.isFile && stalePart(file.name, file.lastModified())) file.delete()
            }
        }
    }

    /** The file a SAF export streams into, under [partName]. */
    fun createPart(dir: DocumentFile, name: String): DocumentFile? =
        runCatching { dir.createFile(PART_MIME, partName(name)) }.getOrNull()

    /**
     * Rename by uri: [DocumentFile.renameTo] refuses outright for the single-document uri the
     * system's "save as" picker hands back, and that file is created under its final name before
     * a byte is written — so it has to be moved aside the same way, by hand.
     */
    fun renameDocument(context: Context, uri: Uri, newName: String): Uri? =
        runCatching { DocumentsContract.renameDocument(context.contentResolver, uri, newName) }
            .getOrNull()

    fun deleteDocument(context: Context, uri: Uri): Boolean =
        runCatching { DocumentsContract.deleteDocument(context.contentResolver, uri) }
            .getOrDefault(false)

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
        // `commit()`, not `apply()` — and this is load-bearing for the automation data door.
        // 応用管理 force-stops this app the instant an import is answered `OK`, deliberately: a
        // running process writes its cached SharedPreferences back out at orderly shutdown and
        // would silently undo the import that just happened. But the force-stop is a SIGKILL,
        // so an `apply()` still in flight is simply lost — the restore reports success and the
        // settings it wrote are gone. `commit()` returns only once the file is written, which is
        // the guarantee the reply is claiming. Every caller is already off the main thread (the
        // Export/Import sheet runs this in `Dispatchers.IO`, the door in its own worker), so the
        // synchronous write costs nothing anyone waits on.
        editor.commit()
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
                                Step(done, total, cat.progressUnit, "${cat.progressUnit} $done/$total")
                            )
                        }
                        // A cursor still yielding rows well past the count the same table just
                        // reported is not a cursor we keep reading. Cancelling breaks out the
                        // same way rather than throwing: the throw would be swallowed by the
                        // `runCatching` around this loop, and the category loop above rethrows
                        // it properly on the next turn anyway.
                        if (done > total + ROW_SLACK || leash.exhausted() || leash.cancelled()) break
                    }
                    onProgress?.report(
                        Step(done, total, cat.progressUnit, "${cat.progressUnit} $done/$total")
                    )
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
        return found.distinctBy { it.absolutePath }.filterNot { isBookFile(root, it) }
    }

    /**
     * The books themselves are **not** this app's data, and never go in a backup.
     *
     * Reading positions, bookmarks, shelves, annotations and notes are what this app made and
     * what cannot be made again; the book files are what 白い熊 put on the phone, they are
     * gigabytes of it, and they are already his — restoring them is the file manager's job, not
     * a settings backup's. Two directories under `filesDir` hold them: `books` (imported copies,
     * [com.aryan.reader.BookImporter]) and `cloud-folder-sync` (offline roots,
     * `cloudFolderAppRootDirectory`).
     *
     * No [FileSpec] names either today — the one that walks the `filesDir` root is restricted by
     * a name prefix — so this changes nothing now. It exists because the failure it prevents is
     * silent and expensive: a later `FileSpec("")` without a prefix, or a new book cache added
     * under a directory already being swept, would put 白い熊's whole library inside every
     * automated backup, and 応用管理 would faithfully copy it onto the next phone. A guard here
     * costs one comparison per file (白い熊 保存復元 contract v2 sizing note, 2026-09-04).
     */
    private fun isBookFile(root: File, file: File): Boolean {
        val relative = file.toRelativeString(root).replace(File.separatorChar, '/')
        return NEVER_EXPORTED.any { relative == it || relative.startsWith("$it/") }
    }

    /** Directories under `filesDir` that hold book files rather than this app's own data. */
    private val NEVER_EXPORTED = listOf("books", "cloud-folder-sync")

    /**
     * Roughly how many bytes an export of [cats] would carry — what 応用管理 sizes a backup from.
     *
     * The file categories are the whole of it: prefs dumps and table rows are kilobytes next to
     * the annotation and cover directories. Answered from a stat walk rather than a trial export
     * because it is read on a binder thread while 応用管理 draws its list, and the walk is over
     * this app's own small directories — the covers, the one large category, are unticked by
     * default and so are usually not in [cats] at all.
     */
    fun sizeEstimate(context: Context, cats: Set<Cat>): Long =
        cats.filter { it.files.isNotEmpty() }.sumOf { cat ->
            runCatching { collectFiles(context, cat).sumOf { it.length() } }.getOrDefault(0L)
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
        // Stat'd once, up front: for the covers this is the only number that says how much of the
        // archive is really written, and it must be known before the first file goes in.
        val bytesTotal = files.sumOf { it.length() }
        var bytes = 0L
        for ((index, file) in files.withIndex()) {
            if (leash.exhausted()) break
            // Between entries, with the ZIP at rest — the one safe place to walk away from
            // thousands of covers, and near enough to instant while they are what is being
            // written.
            leash.checkCancelled()
            val relative = file.toRelativeString(root).replace(File.separatorChar, '/')
            val entry = "${cat.id}/$relative"
            leash.enter(entry)
            val outcome = runCatching { copyEntry(zip, file, entry, leash) }
            val copied = outcome.getOrNull()
            bytes += copied?.bytes ?: 0L
            when {
                copied == null ->
                    leash.skip(entry, outcome.exceptionOrNull()?.message ?: "unreadable")
                copied.complete -> leash.done()
                else -> leash.skip(entry, "did not finish in time", timedOut = true)
            }
            // Every file, not every twenty-fifth: a step whose numbers do not change for three
            // minutes is written off as hung by 自由作業盤 however hard it is really working, and
            // thousands of covers is the one step long enough for that to happen. What actually
            // goes on the wire is throttled by the sender upstream, so this costs nothing.
            val done = (index + 1).toLong()
            onProgress?.report(
                Step(done, total, cat.progressUnit, "${cat.progressUnit} $done/$total", bytes, bytesTotal)
            )
        }
    }

    /**
     * Copy one file into the archive, and stop copying rather than wait forever: a stream that
     * keeps yielding well past the length its own file reports is one that will never EOF, and
     * a copy that outlives its slice of the run is one nothing is gained by waiting on. Either
     * way the entry is closed, so the archive stays readable — an incomplete [Copied] says it is
     * short, and the caller counts it as skipped.
     */
    private fun copyEntry(zip: ZipOutputStream, file: File, entry: String, leash: Leash): Copied {
        var complete = false
        var copied = 0L
        zip.putNextEntry(ZipEntry(entry))
        try {
            val cap = file.length() + SIZE_SLACK
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
        return Copied(copied, complete)
    }

    /** What one entry cost the archive: the bytes that went in, and whether all of them did. */
    private data class Copied(val bytes: Long, val complete: Boolean)

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
