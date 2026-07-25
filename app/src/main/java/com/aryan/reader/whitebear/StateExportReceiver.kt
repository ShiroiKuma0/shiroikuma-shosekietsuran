package com.aryan.reader.whitebear

import android.app.Activity
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Environment
import android.os.SystemClock
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.io.File
import java.io.FilterOutputStream
import java.io.OutputStream
import java.util.concurrent.atomic.AtomicBoolean

/**
 * The 保存復元 automation endpoint — 自由作業盤 fires a token-gated broadcast, this app
 * exports itself headlessly and answers with the written path and size.
 *
 * `<pkg>.action.EXPORT_STATE` — run the ordinary Export/Import export with no UI, into the
 * `path` directory when one is given, otherwise into the configured export directory.
 * `<pkg>.action.LIST_CATEGORIES` — answer with the selectable categories.
 *
 * The reply is always a fresh broadcast: EMUI will not reliably carry a live Binder
 * (ResultReceiver / PendingIntent / Messenger) into another app's manifest receiver, and it
 * severs the ordered-broadcast result channel between third-party apps. The ordered result
 * is set too — correct AOSP behaviour — but is never the only reply.
 */
class StateExportReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val app = context.applicationContext
        val action = intent.action ?: return
        val replyAction = intent.getStringExtra(EXTRA_REPLY_ACTION)
        val replyPackage = intent.getStringExtra(EXTRA_REPLY_PACKAGE)
        val replyId = intent.getStringExtra(EXTRA_REPLY_ID)
        val progressAction = intent.getStringExtra(EXTRA_PROGRESS_ACTION)

        val pending = goAsync()
        val ordered = isOrderedBroadcast
        val answered = AtomicBoolean(false)

        /** Exactly one terminal reply per request, however the work ends. */
        fun reply(result: String) {
            if (!answered.compareAndSet(false, true)) return
            Log.i(TAG, "$action reply_id=$replyId -> $result")
            runCatching {
                if (replyAction != null && replyPackage != null) {
                    app.sendBroadcast(
                        Intent(replyAction).apply {
                            setPackage(replyPackage)
                            addFlags(Intent.FLAG_INCLUDE_STOPPED_PACKAGES)
                            putExtra(EXTRA_REPLY_ID, replyId)
                            putExtra("result", result)
                        }
                    )
                }
            }
            if (ordered) runCatching { pending.setResult(Activity.RESULT_OK, result, null) }
            runCatching { pending.finish() }
        }

        if (replyAction == null || replyPackage == null || replyId == null) {
            Log.w(TAG, "$action without reply_action/reply_package/reply_id — ignored")
            reply("ERROR:missing reply_action/reply_package/reply_id")
            return
        }
        if (!WhiteBearAutomation.isEnabled(app)) {
            reply("ERROR:automation disabled")
            return
        }
        if (!WhiteBearAutomation.matches(app, intent.getStringExtra(EXTRA_TOKEN))) {
            reply("ERROR:bad token")
            return
        }

        when (action) {
            "${app.packageName}.action.LIST_CATEGORIES" ->
                reply("OK:" + WhiteBearExport.categoryLines())

            "${app.packageName}.action.EXPORT_STATE" -> {
                val cats = resolveCategories(intent.getStringExtra(EXTRA_ITEMS))
                    .getOrElse { error -> reply(error.message ?: "ERROR:bad items"); return }
                CoroutineScope(Dispatchers.IO).launch {
                    val result = runCatching {
                        exportHeadlessly(
                            app, cats, intent.getStringExtra(EXTRA_PATH),
                            progress = ProgressSender(app, progressAction, replyPackage, replyId)
                        )
                    }.getOrElse { "ERROR:${it.message ?: it.javaClass.simpleName}" }
                    reply(result)
                }
            }

            else -> reply("ERROR:unknown action $action")
        }
    }

    /** Absent/empty `items` = everything; an unknown id is an error and writes nothing. */
    private fun resolveCategories(items: String?): Result<Set<WhiteBearExport.Cat>> {
        val ids = items?.split(",")?.map { it.trim() }?.filter { it.isNotEmpty() }.orEmpty()
        if (ids.isEmpty()) return Result.success(WhiteBearExport.Cat.entries.toSet())
        val unknown = ids.filter { WhiteBearExport.catById(it) == null }
        if (unknown.isNotEmpty()) {
            return Result.failure(
                IllegalArgumentException("ERROR:unknown category in items: ${unknown.joinToString(",")}")
            )
        }
        return Result.success(ids.mapNotNull { WhiteBearExport.catById(it) }.toSet())
    }

    /**
     * Directory precedence: the `path` extra → the configured export directory →
     * `ERROR:no-directory`. Writing to an arbitrary absolute path needs All-Files-Access;
     * without it `path` is honoured only by falling back to the configured SAF directory.
     */
    private fun exportHeadlessly(
        context: Context,
        cats: Set<WhiteBearExport.Cat>,
        pathExtra: String?,
        progress: ProgressSender
    ): String {
        val name = WhiteBearExport.exportFileName()
        val requested = pathExtra?.trim().orEmpty()
        val allFilesAccess = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            Environment.isExternalStorageManager()
        } else {
            true
        }

        if (requested.isNotEmpty() && allFilesAccess) {
            val dir = File(requested)
            if (!dir.isDirectory && !dir.mkdirs()) return "ERROR:cannot create directory $requested"
            val file = File(dir, name)
            val written = file.outputStream().use { out -> writeExport(context, cats, out, progress) }
            val bytes = if (file.length() > 0L) file.length() else written
            progress.finish()
            return "OK:${file.absolutePath}|$bytes|${WhiteBearExport.humanSize(bytes)}|${cats.size} categories"
        }

        val dir = WhiteBearExport.exportDir(context)
            ?: return if (requested.isNotEmpty()) "ERROR:no-storage-access" else "ERROR:no-directory"
        val target = dir.createFile("application/zip", name) ?: return "ERROR:cannot create the file"
        val written = context.contentResolver.openOutputStream(target.uri)?.use { out ->
            writeExport(context, cats, out, progress)
        } ?: return "ERROR:cannot open the file for writing"
        val bytes = if (target.length() > 0L) target.length() else written
        progress.finish()
        val path = target.uri.path ?: target.uri.toString()
        return "OK:$path|$bytes|${WhiteBearExport.humanSize(bytes)}|${cats.size} categories"
    }

    private fun writeExport(
        context: Context,
        cats: Set<WhiteBearExport.Cat>,
        out: OutputStream,
        progress: ProgressSender
    ): Long {
        // The ZIP stream closes `counting` (and with it `out`) when the export returns, so
        // the count is read afterwards — never flushed again.
        val counting = CountingOutputStream(out)
        WhiteBearExport.export(context, cats, counting) { current, total, unit, text ->
            progress.report(current, total, unit, text)
        }
        return counting.count
    }

    private data class Step(val current: Long, val total: Long, val unit: String, val text: String)

    /**
     * Progress broadcasts with real counts — never a percentage — throttled to at most one
     * every 500 ms, with a final one always sent when the export finishes.
     */
    private class ProgressSender(
        private val context: Context,
        private val action: String?,
        private val replyPackage: String,
        private val replyId: String
    ) {
        private var lastSentAt = 0L
        private var last: Step? = null

        fun report(current: Long, total: Long, unit: String, text: String) {
            last = Step(current, total, unit, text)
            val now = SystemClock.elapsedRealtime()
            if (now - lastSentAt < THROTTLE_MS) return
            lastSentAt = now
            send(current, total, unit, text)
        }

        fun finish() {
            val step = last ?: return
            send(step.current, step.total, step.unit, step.text)
        }

        private fun send(current: Long, total: Long, unit: String, text: String) {
            if (action == null) return
            runCatching {
                context.sendBroadcast(
                    Intent(action).apply {
                        setPackage(replyPackage)
                        addFlags(Intent.FLAG_INCLUDE_STOPPED_PACKAGES)
                        putExtra(EXTRA_REPLY_ID, replyId)
                        putExtra("app", APP_LABEL)
                        putExtra("text", text)
                        putExtra("current", current)
                        putExtra("total", total)
                        putExtra("unit", unit)
                    }
                )
            }
        }
    }

    /** The written size has to come from us — the caller cannot stat the file. */
    private class CountingOutputStream(out: OutputStream) : FilterOutputStream(out) {
        var count = 0L
            private set

        override fun write(b: Int) {
            out.write(b)
            count++
        }

        override fun write(b: ByteArray, off: Int, len: Int) {
            out.write(b, off, len)
            count += len
        }
    }

    companion object {
        private const val TAG = "WhiteBearAutomation"
        private const val APP_LABEL = "白い熊 書籍閲覧"
        private const val THROTTLE_MS = 500L

        private const val EXTRA_TOKEN = "token"
        private const val EXTRA_PATH = "path"
        private const val EXTRA_ITEMS = "items"
        private const val EXTRA_PROGRESS_ACTION = "progress_action"
        private const val EXTRA_REPLY_ACTION = "reply_action"
        private const val EXTRA_REPLY_PACKAGE = "reply_package"
        private const val EXTRA_REPLY_ID = "reply_id"
    }
}
