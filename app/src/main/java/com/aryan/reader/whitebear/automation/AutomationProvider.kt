package com.aryan.reader.whitebear.automation

import android.content.ContentProvider
import android.content.ContentValues
import android.content.Context
import android.database.Cursor
import android.net.Uri
import android.os.Bundle
import android.os.ParcelFileDescriptor
import com.aryan.reader.whitebear.AutomationWire
import com.aryan.reader.whitebear.WhiteBearAutomation
import com.aryan.reader.whitebear.WhiteBearExport
import org.json.JSONArray
import org.json.JSONObject

/**
 * The data door: export this app's own state, and put it back, for a caller we can identify.
 *
 * ## Why a provider and not the broadcast receiver next to it
 *
 * Two reasons, and the first is the whole point of the v2 redesign.
 *
 * **A broadcast cannot tell you who sent it.** v1's answer to that was a shared secret, which
 * cannot survive the wipe this feature exists to recover from. A provider gets the caller's
 * identity from the framework — see [AutomationCallers] for what is actually checked, and why a
 * `shiroikuma.*` prefix would have been *weaker* than the token it replaces.
 *
 * **And a list needs a synchronous answer.** 応用管理 draws a row per installed app before any
 * export exists; a broadcast round trip per app to fill a list is the wrong shape entirely.
 *
 * ## What does NOT happen here
 *
 * The payload. `call()` validates, starts a foreground service and returns — a library export
 * with 「Book covers」 in it is thousands of files and minutes of work, and inside a binder call
 * that would block 応用管理's UI, report no progress, refuse cancellation and die silently if
 * this process were killed. The bytes go through a descriptor the caller opened; the terminal
 * answer comes back on the broadcast the family already proved on EMUI.
 *
 * ## Why a descriptor and not a path
 *
 * Because a backup is not a stable directory while it is being assembled. 応用管理 writes into a
 * temporary path and renames on commit, and it encrypts and checksums **per file it knows
 * about**. A file this app dropped in itself would be renamed out from under it, would sit in
 * plaintext inside an encrypted backup, and would be unverified rather than verified-and-failing.
 * A descriptor is also a capability that **expires when it is closed**.
 *
 * It also means the automation path no longer needs `MANAGE_EXTERNAL_STORAGE`. That permission
 * is still declared, because §1's `path` extra hands this app an absolute directory — but the
 * data door does not depend on the grant, which is what makes it work on a phone where nobody
 * has been to the All-files-access page yet.
 */
class AutomationProvider : ContentProvider() {

    override fun onCreate(): Boolean = true

    /**
     * Every method answers a [Bundle] with [KEY_RESULT] — `OK…` or `ERROR:…`, the same grammar
     * the broadcast contract uses, so a caller has one vocabulary rather than two.
     *
     * A refusal is returned, never thrown: an exception across a binder reaches the caller as a
     * `RuntimeException` carrying our stack trace, which tells 白い熊 nothing and tells a
     * misbehaving caller rather more than it should.
     */
    override fun call(method: String, arg: String?, extras: Bundle?): Bundle {
        val ctx = context?.applicationContext ?: return fail("ERROR:not ready")

        // WHO, before WHAT. A caller we cannot identify gets the same answer whatever it asked.
        when (val verdict = AutomationCallers.verify(ctx, callingPackage)) {
            is AutomationCallers.Verdict.Refused -> return fail(verdict.why)
            AutomationCallers.Verdict.Allowed -> Unit
        }
        // Then this app's own switches — a token is ignored unless this app asks for one.
        WhiteBearAutomation.refuse(ctx, extras?.getString(KEY_TOKEN))?.let { return fail(it) }

        return when (method) {
            METHOD_DESCRIBE -> runCatching { ok(describe(ctx)) }
                .getOrElse { fail("ERROR:${it.message ?: it.javaClass.simpleName}") }
            METHOD_EXPORT -> start(ctx, extras, importing = false)
            METHOD_IMPORT -> start(ctx, extras, importing = true)
            METHOD_CANCEL -> {
                AutomationJobs.cancel(extras?.getString(KEY_JOB_ID))
                ok("OK:cancelled")
            }
            else -> fail("ERROR:unknown method: $method")
        }
    }

    /**
     * What this app would export, answered without exporting anything.
     *
     * Returned from the call rather than written into the archive, deliberately: 応用管理 must
     * draw a row before an export exists, and at restore must judge compatibility **before**
     * streaming tens of megabytes into an app that would reject them — which it cannot do if the
     * header is buried inside an encrypted archive.
     *
     * `contains` is the default set's short labels, rendered verbatim by 応用管理, and
     * `size_estimate` is what it sizes the backup from. Both describe the **default** set rather
     * than the catalogue: 「Book covers」 is unticked by default and is most of the bytes, so
     * quoting the whole footprint would tell 白い熊 a routine backup is several times the size it
     * really is. **The books themselves are in neither number** — see
     * [WhiteBearExport.sizeEstimate] and the guard beside it: reading positions, bookmarks and
     * annotations are this app's data, the book files are 白い熊's own and stay where they are.
     */
    private fun describe(ctx: Context): String {
        val pkg = ctx.packageManager.getPackageInfo(ctx.packageName, 0)
        @Suppress("DEPRECATION")
        val versionCode = pkg.versionCode
        val cats = WhiteBearExport.defaultCats()
        val header = JSONObject()
            .put("app_id", ctx.packageName)
            .put("version_code", versionCode)
            .put("version_name", pkg.versionName.orEmpty())
            .put("format", FORMAT)
            .put("min_format_readable", MIN_FORMAT_READABLE)
            // This app writes prefs and merges DB rows; it needs no first run to have happened.
            .put("requires_launch_first", false)
            .put("contains", JSONArray(WhiteBearExport.Cat.entries.filter { it in cats }.map { it.shortLabel }))
            .put("size_estimate", WhiteBearExport.sizeEstimate(ctx, cats))
        return "OK:$header"
    }

    /**
     * Hand the descriptor to a foreground service and get out of the way.
     *
     * The descriptor is **duplicated** before it leaves this method. The one in [extras] belongs
     * to the binder transaction and is closed the moment `call()` returns; a service reading it
     * afterwards would find it shut. That is a bug you only see under load, so it is not left to
     * the service to remember.
     */
    private fun start(ctx: Context, extras: Bundle?, importing: Boolean): Bundle {
        @Suppress("DEPRECATION")
        val fd = extras?.getParcelable<ParcelFileDescriptor>(KEY_FD)
            ?: return fail("ERROR:no descriptor")
        // Refused before anything is started, so a bad id costs nothing and writes nothing —
        // the same order the broadcast receiver validates in.
        if (!importing) {
            AutomationWire.resolveCategories(extras?.getString(KEY_ITEMS))
                .onFailure { return fail(it.message ?: "ERROR:bad items") }
        }
        val dup = runCatching { fd.dup() }.getOrNull() ?: return fail("ERROR:descriptor unusable")
        val jobId = AutomationJobs.begin()
        val started = runCatching {
            AutomationDataService.start(ctx, jobId, dup, importing, extras)
        }
        started.onFailure { error ->
            // Nothing is going to answer for a job whose service never started, so the
            // descriptor is closed here and the caller is told now rather than left waiting.
            AutomationJobs.finish(jobId)
            runCatching { dup.close() }
            return fail("ERROR:cannot start the data service: ${error.javaClass.simpleName}")
        }
        return ok("OK:$jobId")
    }

    private fun ok(result: String) = Bundle().apply { putString(KEY_RESULT, result) }
    private fun fail(why: String) = Bundle().apply { putString(KEY_RESULT, why) }

    // A provider that is only ever `call()`ed still has to answer these. Refusing loudly beats
    // returning an empty cursor, which reads downstream as "there is no data" rather than
    // "wrong door".
    override fun query(u: Uri, p: Array<String>?, s: String?, a: Array<String>?, o: String?): Cursor? =
        throw UnsupportedOperationException("automation is call() only")

    override fun getType(uri: Uri): String? = null

    override fun insert(uri: Uri, values: ContentValues?): Uri? =
        throw UnsupportedOperationException("automation is call() only")

    override fun delete(uri: Uri, s: String?, a: Array<String>?): Int =
        throw UnsupportedOperationException("automation is call() only")

    override fun update(u: Uri, v: ContentValues?, s: String?, a: Array<String>?): Int =
        throw UnsupportedOperationException("automation is call() only")

    companion object {
        const val METHOD_DESCRIBE = "describe"
        const val METHOD_EXPORT = "export"
        const val METHOD_IMPORT = "import"
        const val METHOD_CANCEL = "cancel"

        const val KEY_RESULT = "result"
        const val KEY_FD = "fd"
        const val KEY_TOKEN = "token"
        const val KEY_JOB_ID = "job_id"
        const val KEY_ITEMS = "items"
        const val KEY_REPLY_ACTION = "reply_action"
        const val KEY_REPLY_PACKAGE = "reply_package"
        const val KEY_PROGRESS_ACTION = "progress_action"

        /**
         * This app's archive format — [WhiteBearExport.VERSION], so the number the door reports
         * and the number written into `manifest.json` can never drift apart.
         */
        const val FORMAT = WhiteBearExport.VERSION

        /**
         * The oldest archive this build can still read.
         *
         * Version skew has a direction: old data into a newer app is normally fine, because an
         * app migrates its own storage; newer data into an older app is not. This is what lets a
         * caller refuse the second case at discovery time, before anything is streamed. 1 is
         * honest here — the importer reads entries by name, skips categories an archive lacks,
         * and merges per key, so a v1 archive still restores into this build.
         */
        const val MIN_FORMAT_READABLE = 1
    }
}
