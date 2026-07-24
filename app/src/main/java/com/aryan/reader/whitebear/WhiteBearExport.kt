package com.aryan.reader.whitebear

import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.net.Uri
import androidx.documentfile.provider.DocumentFile
import org.json.JSONArray
import org.json.JSONObject
import java.io.OutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

/**
 * 白い熊 export/import of everything settable in the app, organized by category — the
 * same idea and flow as the sister forks: a ZIP of plain JSON files, one per category,
 * plus a manifest.json listing format, version and the categories present. Whole
 * SharedPreferences files are serialized generically and type-tagged, so any keyset
 * round-trips; import merges key by key (never clears), and categories missing from
 * the ZIP are skipped, keeping old exports importable forever.
 */
object WhiteBearExport {

    const val FORMAT = "shosekietsuran-export"
    const val VERSION = 1

    /** Export file names: shiroikuma-shosekietsuran-&lt;version&gt;-export_&lt;timestamp&gt;.zip. */
    const val EXPORT_PREFIX = "shiroikuma-shosekietsuran-"

    /** Device-local keys never exported nor imported (ids, sync/session/tab state). */
    private val DEVICE_LOCAL_KEYS = setOf(
        "installation_id", "last_sync_timestamp", "app_open_count", "open_tab_ids",
        "active_tab_book_id", "last_open_book_id", "last_open_file_type",
        "last_folder_scan_time", "pending_external_file_removals", "missing_drive_permissions"
    )
    private val DEVICE_LOCAL_PREFIXES = listOf("imported_file_", "opds_stream_", "credits_")

    /** A selectable category; `id` is the JSON file name (`<id>.json`) inside the ZIP. */
    enum class Cat(val id: String, val label: String, internal val prefsFiles: List<String>) {
        WB_UI("wb_ui", "白い熊 UI — colors, fonts, shapes", listOf("whitebear_ui_prefs")),
        WB_GESTURES("wb_gestures", "白い熊 gestures & page turning", listOf("whitebear_gesture_prefs")),
        WB_LIBRARY("wb_library", "白い熊 library view", listOf("whitebear_library_prefs")),
        WB_WRITING("wb_writing", "白い熊 writing 縦書き", listOf("whitebear_writing_prefs")),
        APP_SETTINGS("app_settings", "App settings — theme, font, behavior", listOf("reader_user_prefs")),
        READER_SETTINGS(
            "reader_settings", "Reader settings — EPUB, PDF, TTS, annotations",
            listOf("epub_reader_settings", "reader_prefs", "annotation_settings_global")
        ),
    }

    /** Write a ZIP of the selected categories to [out]. Returns a short human summary. */
    fun export(context: Context, cats: Set<Cat>, out: OutputStream): String {
        ZipOutputStream(out).use { zip ->
            val manifest = JSONObject()
                .put("format", FORMAT)
                .put("version", VERSION)
                .put("app", context.packageName)
                .put("createdTs", System.currentTimeMillis())
                .put("categories", JSONArray(cats.map { it.id }))
            writeEntry(zip, "manifest.json", manifest.toString(2))
            for (cat in cats) {
                val json = JSONObject()
                cat.prefsFiles.forEach { name ->
                    json.put(
                        name,
                        prefsToJson(context.getSharedPreferences(name, Context.MODE_PRIVATE))
                    )
                }
                writeEntry(zip, "${cat.id}.json", json.toString(2))
            }
        }
        return "${cats.size} ${if (cats.size == 1) "category" else "categories"}"
    }

    /** Apply the selected categories from a ZIP. Missing files are skipped. Returns a summary. */
    fun import(context: Context, zipBytes: ByteArray, cats: Set<Cat>): String {
        val files = readZip(zipBytes)
        val parts = mutableListOf<String>()
        for (cat in cats) {
            val data = files["${cat.id}.json"] ?: continue
            val n = try {
                val json = JSONObject(data.decodeToString())
                var count = 0
                cat.prefsFiles.forEach { name ->
                    json.optJSONObject(name)?.let { prefsJson ->
                        count += jsonToPrefs(
                            context.getSharedPreferences(name, Context.MODE_PRIVATE), prefsJson
                        )
                    }
                }
                count
            } catch (_: Exception) {
                -1
            }
            if (n >= 0) parts.add("${cat.label.substringBefore(" —")}: $n")
        }
        return if (parts.isEmpty()) "Nothing imported." else parts.joinToString("\n")
    }

    fun exportFileName(versionName: String): String =
        EXPORT_PREFIX + versionName + "-export_" +
            SimpleDateFormat("yyyy-MM-dd_HH-mm-ss", Locale.ROOT).format(Date()) + ".zip"

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
            dir.listFiles().filter {
                it.isFile && it.name?.startsWith(EXPORT_PREFIX) == true &&
                    it.name?.endsWith(".zip") == true
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

    private fun writeEntry(zip: ZipOutputStream, name: String, content: String) {
        zip.putNextEntry(java.util.zip.ZipEntry(name))
        zip.write(content.toByteArray())
        zip.closeEntry()
    }

    private fun readZip(bytes: ByteArray): Map<String, ByteArray> {
        val files = mutableMapOf<String, ByteArray>()
        ZipInputStream(bytes.inputStream()).use { zip ->
            var entry = zip.nextEntry
            while (entry != null) {
                if (!entry.isDirectory) files[entry.name] = zip.readBytes()
                entry = zip.nextEntry
            }
        }
        return files
    }
}
