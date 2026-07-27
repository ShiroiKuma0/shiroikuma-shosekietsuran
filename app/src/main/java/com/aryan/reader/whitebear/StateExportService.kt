package com.aryan.reader.whitebear

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.Environment
import android.os.IBinder
import android.os.PowerManager
import android.os.SystemClock
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import com.aryan.reader.R
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.io.File
import java.io.FilterOutputStream
import java.io.OutputStream
import java.util.concurrent.atomic.AtomicBoolean

/**
 * The wire shape of the 保存復元 automation contract — the extras both the receiver and the
 * export service speak, and the two kinds of broadcast this app sends back.
 *
 * The reply is always a fresh broadcast: EMUI will not reliably carry a live Binder
 * (ResultReceiver / PendingIntent / Messenger) into another app's manifest receiver, and it
 * severs the ordered-broadcast result channel between third-party apps (verified on 白い熊's
 * Mate XT, 2026-07-23). [Intent.FLAG_INCLUDE_STOPPED_PACKAGES] so a backgrounded or stopped
 * caller still hears us.
 */
internal object AutomationWire {

    const val EXTRA_TOKEN = "token"
    const val EXTRA_PATH = "path"
    const val EXTRA_ITEMS = "items"
    const val EXTRA_PROGRESS_ACTION = "progress_action"
    const val EXTRA_REPLY_ACTION = "reply_action"
    const val EXTRA_REPLY_PACKAGE = "reply_package"
    const val EXTRA_REPLY_ID = "reply_id"
    const val EXTRA_RESULT = "result"

    const val TAG = "WhiteBearAutomation"

    /** What 自由作業盤's summary and progress panel call this app. */
    const val APP_LABEL = "白い熊 書籍閲覧"

    fun sendReply(
        context: Context,
        replyAction: String,
        replyPackage: String,
        replyId: String?,
        result: String
    ) {
        Log.i(TAG, "reply_id=$replyId -> $result")
        runCatching {
            context.sendBroadcast(
                Intent(replyAction).apply {
                    setPackage(replyPackage)
                    addFlags(Intent.FLAG_INCLUDE_STOPPED_PACKAGES)
                    putExtra(EXTRA_REPLY_ID, replyId)
                    putExtra(EXTRA_RESULT, result)
                }
            )
        }
    }

    /** Absent/empty `items` = everything; an unknown id is an error and writes nothing. */
    fun resolveCategories(items: String?): Result<Set<WhiteBearExport.Cat>> {
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
}

/**
 * Where the headless export actually runs.
 *
 * **A broadcast receiver cannot hold this export** — `goAsync()` does not extend the broadcast
 * window (~10 s foreground, ~60 s otherwise), so a manifest receiver that keeps the
 * `PendingResult` open across a real export is killed mid-write with an ANR. That is not
 * hypothetical: on 白い熊's Mate XT, 2026-07-27, this app was exporting 8444 covers, reached
 * ~1200, and was killed — no reply, half-written ZIP, and the 保存復元 batch left waiting on a
 * dead process. So [StateExportReceiver] does nothing but gate the request and start this
 * service, and everything slow lives here: the export, the progress broadcasts, and the one
 * terminal reply.
 */
class StateExportService : Service() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val busy = AtomicBoolean(false)

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        // Must happen within 5 s of the service starting, or the system kills us for it.
        startInForeground(getString(R.string.whitebear_backup_notification_working))

        val request = intent ?: run {
            stopEverything()
            return START_NOT_STICKY
        }
        val replyAction = request.getStringExtra(AutomationWire.EXTRA_REPLY_ACTION).orEmpty()
        val replyPackage = request.getStringExtra(AutomationWire.EXTRA_REPLY_PACKAGE).orEmpty()
        val replyId = request.getStringExtra(AutomationWire.EXTRA_REPLY_ID)

        // One export at a time: a second request never interleaves with a running one, and
        // never touches its lifecycle either.
        if (!busy.compareAndSet(false, true)) {
            if (replyAction.isNotEmpty() && replyPackage.isNotEmpty()) {
                AutomationWire.sendReply(
                    this, replyAction, replyPackage, replyId, "ERROR:export already running"
                )
            }
            return START_NOT_STICKY
        }

        scope.launch {
            val answered = AtomicBoolean(false)

            /** Exactly one terminal reply per request, however the work ends. */
            fun reply(result: String) {
                if (!answered.compareAndSet(false, true)) return
                if (replyAction.isEmpty() || replyPackage.isEmpty()) {
                    Log.w(AutomationWire.TAG, "no reply_action/reply_package — $result")
                    return
                }
                AutomationWire.sendReply(
                    this@StateExportService, replyAction, replyPackage, replyId, result
                )
            }

            val progress = ProgressSender(
                context = this@StateExportService,
                action = request.getStringExtra(AutomationWire.EXTRA_PROGRESS_ACTION),
                replyPackage = replyPackage,
                replyId = replyId,
                onText = { text -> updateNotification(text) }
            )
            // Every progress broadcast is also a heartbeat — 自由作業盤 presumes an app that
            // goes quiet is dead and fails its slot, so the last line is re-sent during a
            // single long step (zipping one huge library) even when the numbers have not moved.
            val heartbeat = scope.launch {
                while (isActive) {
                    delay(HEARTBEAT_MS)
                    progress.heartbeat()
                }
            }
            val wakeLock = acquireWakeLock()
            try {
                reply(runCatching { export(request, progress) }
                    .getOrElse { "ERROR:${it.message ?: it.javaClass.simpleName}" })
            } finally {
                heartbeat.cancel()
                runCatching { wakeLock?.takeIf { it.isHeld }?.release() }
                busy.set(false)
                stopEverything()
            }
        }
        return START_NOT_STICKY
    }

    override fun onDestroy() {
        scope.cancel()
        super.onDestroy()
    }

    /**
     * Directory precedence: the `path` extra → the configured export directory →
     * `ERROR:no-directory`. Writing to an arbitrary absolute path needs All-Files-Access;
     * without it `path` is honoured only by falling back to the configured SAF directory.
     */
    private fun export(request: Intent, progress: ProgressSender): String {
        val cats = AutomationWire.resolveCategories(request.getStringExtra(AutomationWire.EXTRA_ITEMS))
            .getOrElse { return it.message ?: "ERROR:bad items" }
        // A first line before any directory work, so the caller hears us immediately.
        progress.report(0L, cats.size.toLong(), "区分", "区分 0/${cats.size} — 開始")

        val name = WhiteBearExport.exportFileName()
        val requested = request.getStringExtra(AutomationWire.EXTRA_PATH)?.trim().orEmpty()
        val allFilesAccess = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            Environment.isExternalStorageManager()
        } else {
            true
        }

        if (requested.isNotEmpty() && allFilesAccess) {
            val dir = File(requested)
            if (!dir.isDirectory && !dir.mkdirs()) return "ERROR:cannot create directory $requested"
            val file = File(dir, name)
            val written = file.outputStream().use { out -> writeExport(cats, out, progress) }
            val bytes = if (file.length() > 0L) file.length() else written
            progress.finish()
            return "OK:${file.absolutePath}|$bytes|${WhiteBearExport.humanSize(bytes)}|${cats.size} categories"
        }

        val dir = WhiteBearExport.exportDir(this)
            ?: return if (requested.isNotEmpty()) "ERROR:no-storage-access" else "ERROR:no-directory"
        val target = dir.createFile("application/zip", name) ?: return "ERROR:cannot create the file"
        val written = contentResolver.openOutputStream(target.uri)?.use { out ->
            writeExport(cats, out, progress)
        } ?: return "ERROR:cannot open the file for writing"
        val bytes = if (target.length() > 0L) target.length() else written
        progress.finish()
        val path = target.uri.path ?: target.uri.toString()
        return "OK:$path|$bytes|${WhiteBearExport.humanSize(bytes)}|${cats.size} categories"
    }

    private fun writeExport(
        cats: Set<WhiteBearExport.Cat>,
        out: OutputStream,
        progress: ProgressSender
    ): Long {
        // The ZIP stream closes `counting` (and with it `out`) when the export returns, so
        // the count is read afterwards — never flushed again.
        val counting = CountingOutputStream(out)
        WhiteBearExport.export(this, cats, counting) { current, total, unit, text ->
            progress.report(current, total, unit, text)
        }
        return counting.count
    }

    // ---- Foreground plumbing ----

    /** EMUI dozes the CPU with the screen off; a library export runs for minutes. */
    private fun acquireWakeLock(): PowerManager.WakeLock? = runCatching {
        getSystemService(PowerManager::class.java)
            .newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "shosekietsuran:state-export")
            .apply { setReferenceCounted(false); acquire(WAKELOCK_TIMEOUT_MS) }
    }.getOrNull()

    private fun startInForeground(text: String) {
        runCatching {
            ensureChannel()
            ServiceCompat.startForeground(
                this, NOTIFICATION_ID, buildNotification(text),
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC
                } else {
                    0
                }
            )
        }
    }

    private fun updateNotification(text: String) {
        runCatching {
            getSystemService(NotificationManager::class.java)
                .notify(NOTIFICATION_ID, buildNotification(text))
        }
    }

    private fun buildNotification(text: String): Notification =
        NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_launcher_monochrome)
            .setContentTitle(getString(R.string.whitebear_backup_notification_title))
            .setContentText(text)
            .setOngoing(true)
            .setSilent(true)
            .setOnlyAlertOnce(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setForegroundServiceBehavior(NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE)
            .build()

    private fun ensureChannel() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        getSystemService(NotificationManager::class.java).createNotificationChannel(
            NotificationChannel(
                CHANNEL_ID,
                getString(R.string.whitebear_backup_notification_channel_name),
                NotificationManager.IMPORTANCE_LOW
            ).apply { setShowBadge(false) }
        )
    }

    private fun stopEverything() {
        ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    private data class Step(val current: Long, val total: Long, val unit: String, val text: String)

    /**
     * Progress broadcasts with real counts — never a percentage — throttled to at most one
     * every 500 ms, re-sent as a heartbeat while a long step makes no visible progress, and
     * always sent once more when the export finishes. Reported from the export thread and the
     * heartbeat coroutine both, hence the locking.
     */
    private class ProgressSender(
        private val context: Context,
        private val action: String?,
        private val replyPackage: String,
        private val replyId: String?,
        private val onText: (String) -> Unit
    ) {
        private var lastSentAt = 0L
        private var last: Step? = null

        @Synchronized
        fun report(current: Long, total: Long, unit: String, text: String) {
            val step = Step(current, total, unit, text)
            last = step
            if (SystemClock.elapsedRealtime() - lastSentAt < THROTTLE_MS) return
            send(step)
        }

        /** Re-send the last line when nothing has gone out for a while — proof we are alive. */
        @Synchronized
        fun heartbeat() {
            val step = last ?: return
            if (SystemClock.elapsedRealtime() - lastSentAt < HEARTBEAT_MS) return
            send(step)
        }

        @Synchronized
        fun finish() {
            last?.let { send(it) }
        }

        private fun send(step: Step) {
            lastSentAt = SystemClock.elapsedRealtime()
            onText(step.text)
            if (action.isNullOrEmpty() || replyPackage.isEmpty()) return
            runCatching {
                context.sendBroadcast(
                    Intent(action).apply {
                        setPackage(replyPackage)
                        addFlags(Intent.FLAG_INCLUDE_STOPPED_PACKAGES)
                        putExtra(AutomationWire.EXTRA_REPLY_ID, replyId)
                        putExtra("app", AutomationWire.APP_LABEL)
                        putExtra("text", step.text)
                        putExtra("current", step.current)
                        putExtra("total", step.total)
                        putExtra("unit", step.unit)
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
        private const val CHANNEL_ID = "whitebear_backup"
        private const val NOTIFICATION_ID = 4979
        private const val THROTTLE_MS = 500L
        private const val HEARTBEAT_MS = 20_000L
        private const val WAKELOCK_TIMEOUT_MS = 60L * 60L * 1000L

        /** The request, forwarded verbatim from [StateExportReceiver]. */
        fun intentFor(context: Context, request: Intent): Intent =
            Intent(context, StateExportService::class.java).apply {
                putExtra(AutomationWire.EXTRA_PATH, request.getStringExtra(AutomationWire.EXTRA_PATH))
                putExtra(AutomationWire.EXTRA_ITEMS, request.getStringExtra(AutomationWire.EXTRA_ITEMS))
                putExtra(
                    AutomationWire.EXTRA_PROGRESS_ACTION,
                    request.getStringExtra(AutomationWire.EXTRA_PROGRESS_ACTION)
                )
                putExtra(
                    AutomationWire.EXTRA_REPLY_ACTION,
                    request.getStringExtra(AutomationWire.EXTRA_REPLY_ACTION)
                )
                putExtra(
                    AutomationWire.EXTRA_REPLY_PACKAGE,
                    request.getStringExtra(AutomationWire.EXTRA_REPLY_PACKAGE)
                )
                putExtra(
                    AutomationWire.EXTRA_REPLY_ID,
                    request.getStringExtra(AutomationWire.EXTRA_REPLY_ID)
                )
            }
    }
}
