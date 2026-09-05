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
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.io.File
import java.io.FilterOutputStream
import java.io.OutputStream
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicReference

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

    /**
     * The category id a leash entry belongs to, or null for a shape we did not anticipate.
     *
     * [WhiteBearExport] names every entry on its way into the ZIP, and the five shapes it uses
     * all carry the category first: `library`, `library.covers: scanning`,
     * `library.covers/cover_cache/a.png`, `library.shelves.jsonl: shelves`, and
     * `library.shelves.jsonl: shelves 12/100` — note the last has a `/` of its own, which is why
     * the path separator is cut before the colon rather than after it, and why the `.jsonl` a
     * table entry carries has to come off before the id is recognisable.
     *
     * Validated against the catalogue rather than trusted, so an entry shape added later reports
     * no `item` instead of a row id that nothing on the other side has.
     */
    fun categoryOf(entry: String): String? {
        val head = entry.substringBefore('/').substringBefore(':').trim()
            .removeSuffix(".jsonl")
            .removeSuffix(".json")
        return head.takeIf { WhiteBearExport.catById(it) != null }
    }

    /**
     * Absent/empty `items` = the default set, not the whole catalogue: a run that never picked
     * its items must not drag the covers in behind a default that says to leave them out. An
     * unknown id is an error and writes nothing.
     */
    fun resolveCategories(items: String?): Result<Set<WhiteBearExport.Cat>> {
        val ids = items?.split(",")?.map { it.trim() }?.filter { it.isNotEmpty() }.orEmpty()
        if (ids.isEmpty()) return Result.success(WhiteBearExport.defaultCats())
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
 *
 * **And a service is not enough either.** With the ANR gone, the export itself stopped part-way
 * — twice in one evening on the same phone, at two different offsets — and simply never came
 * back. Nothing was wrong with the plumbing: the coroutine was alive, the heartbeat was still
 * going out, so 自由作業盤 waited out its whole timeout instead of failing the app; the guard
 * flag was released in a `finally` that could not run, so every later request answered
 * `ERROR:export already running` for the rest of the process's life; and a half-written ZIP was
 * left behind each time, indistinguishable from a backup until someone tried to open it.
 *
 * So nothing here trusts the export to return. A watchdog judges the run from outside on what it
 * last really did, answers for it when it stops, and gives up the slot without waiting for it;
 * a request that finds the slot held by a run older than any run may live takes it over; and the
 * heartbeat is sent by the watchdog itself, so it cannot outlive the work it reports on.
 *
 * **And a run 白い熊 no longer wants must be able to stop.** With 「Book covers」 ticked an export
 * is a many-minute job, and 保存復元's 中止 used to stop only 自由作業盤 listening — the app carried
 * on and delivered a backup that had been called off. So [cancel] marks the run, the export
 * notices between entries, and it unwinds itself: partial file deleted, `ERROR:cancelled` sent,
 * wakelock and service given up exactly as on any other ending.
 */
class StateExportService : Service() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        // Must happen within 5 s of the service starting, or the system kills us for it.
        startInForeground(getString(R.string.whitebear_backup_notification_working))

        val request = intent ?: run {
            // A redelivery with nothing in it must not take down a run that is under way.
            if (current.get() == null) stopEverything()
            return START_NOT_STICKY
        }
        val run = Run(
            replyAction = request.getStringExtra(AutomationWire.EXTRA_REPLY_ACTION).orEmpty(),
            replyPackage = request.getStringExtra(AutomationWire.EXTRA_REPLY_PACKAGE).orEmpty(),
            replyId = request.getStringExtra(AutomationWire.EXTRA_REPLY_ID)
        )
        if (claim(run)) start(request, run)
        return START_NOT_STICKY
    }

    /**
     * One export at a time — but never for longer than an export can honestly last.
     *
     * A run that has been going longer than any run may live is not working, it is wedged, and
     * the flag it is holding is worth nothing to anyone. So it is superseded: its caller is told
     * so, and the new request takes the slot. A user who taps "retry" must never be refused by a
     * run that died half an hour ago.
     */
    private fun claim(run: Run): Boolean {
        while (true) {
            val existing = current.get()
            if (existing == null) {
                if (current.compareAndSet(null, run)) return true
                continue
            }
            val age = SystemClock.elapsedRealtime() - existing.startedAt
            if (age <= CEILING_MS) {
                run.reply(this, "ERROR:export already running")
                return false
            }
            Log.w(AutomationWire.TAG, "superseding a run wedged for ${age / 1000} s at ${existing.item}")
            abandon(existing, "ERROR:superseded")
            current.compareAndSet(existing, run)
        }
    }

    /**
     * The export runs on a thread of its own, not on a dispatcher: when it wedges it must be
     * abandonable, and abandoning a pooled thread poisons whatever runs on it next. A stuck
     * worker is left to die with the process — the service does not wait for it.
     */
    private fun start(request: Intent, run: Run) {
        val progress = ProgressSender(
            context = this,
            action = request.getStringExtra(AutomationWire.EXTRA_PROGRESS_ACTION),
            replyPackage = run.replyPackage,
            replyId = run.replyId,
            onText = { text -> updateNotification(text) }
        )
        run.wakeLock = acquireWakeLock()
        // The heartbeat and the watchdog are one loop on purpose. 自由作業盤 reads a heartbeat as
        // proof the app is still working and waits out its whole timeout while they keep coming,
        // so a heartbeat that can outlive the work it reports on is worse than none: the beat is
        // sent only while the export is really moving, and the same tick that finds it stopped
        // is the one that says so.
        run.watchdog = scope.launch {
            while (isActive) {
                delay(WATCHDOG_TICK_MS)
                val now = SystemClock.elapsedRealtime()
                val silent = now - run.lastProgressAt
                when {
                    now - run.startedAt > CEILING_MS -> {
                        fail(run, "ERROR:export timed out after ${CEILING_MS / 1000} s at ${run.item}")
                        return@launch
                    }
                    silent > STALL_MS -> {
                        fail(run, "ERROR:stalled — no progress for ${silent / 1000} s at ${run.item}")
                        return@launch
                    }
                    else -> progress.heartbeat()
                }
            }
        }
        val worker = Thread({
            val result = runCatching { export(request, run, progress) }
                .getOrElse { error ->
                    // 中止 is not a failure, and is answered for by type rather than by the flag:
                    // a cancel that arrives after the export already placed its file cancelled
                    // nothing, and must not report away a backup that is sitting there complete.
                    if (error is WhiteBearExport.Cancelled) "ERROR:cancelled"
                    else "ERROR:${error.message ?: error.javaClass.simpleName}"
                }
            run.reply(this, result)
            run.watchdog?.cancel()
            run.releaseWakeLock()
            retire(run)
        }, "wb-state-export")
        worker.isDaemon = true
        run.worker = worker
        worker.start()
    }

    /** Answer for a run that is not coming back, and take everything from it we can. */
    private fun fail(run: Run, message: String) {
        Log.w(AutomationWire.TAG, message)
        abandon(run, message)
        retire(run)
    }

    private fun abandon(run: Run, message: String) {
        run.reply(this, message)
        run.watchdog?.cancel()
        // Closing the sink is the one thing that can break a write that will not return:
        // Android signals the threads blocked on a file descriptor when it is closed. The
        // interrupt is the same bet on the read side. Neither is guaranteed, and neither is
        // waited for — the reply has already gone out.
        runCatching { run.sink?.close() }
        runCatching { run.worker?.interrupt() }
        run.releaseWakeLock()
    }

    /** Give up the slot, and with it the service — but only if this run still holds it. */
    private fun retire(run: Run) {
        if (current.compareAndSet(run, null)) stopEverything()
    }

    override fun onDestroy() {
        scope.cancel()
        super.onDestroy()
    }

    /**
     * One request, from its extras to its single reply. Everything the watchdog needs to judge
     * it — when it started, when it last really moved, and what it is on — lives here, written
     * by the export thread and read by the watchdog, hence the volatiles.
     */
    private class Run(
        val replyAction: String,
        val replyPackage: String,
        val replyId: String?
    ) {
        val startedAt: Long = SystemClock.elapsedRealtime()
        private val answered = AtomicBoolean(false)

        @Volatile var lastProgressAt: Long = startedAt
        @Volatile var item: String = "開始"

        /** 白い熊 pressed 中止. Read by the export between entries, and by nothing else. */
        @Volatile var cancelled: Boolean = false

        @Volatile var worker: Thread? = null
        @Volatile var watchdog: Job? = null
        @Volatile var sink: OutputStream? = null
        @Volatile var wakeLock: PowerManager.WakeLock? = null

        /** Exactly one terminal reply per request, however the work ends. */
        fun reply(context: Context, result: String) {
            if (!answered.compareAndSet(false, true)) return
            if (replyAction.isEmpty() || replyPackage.isEmpty()) {
                Log.w(AutomationWire.TAG, "no reply_action/reply_package — $result")
                return
            }
            AutomationWire.sendReply(context, replyAction, replyPackage, replyId, result)
        }

        /** Real work happened — the only thing that resets the stall clock. */
        fun alive(what: String? = null) {
            if (what != null) item = what
            lastProgressAt = SystemClock.elapsedRealtime()
        }

        fun releaseWakeLock() {
            runCatching { wakeLock?.takeIf { it.isHeld }?.release() }
            wakeLock = null
        }
    }

    /**
     * Directory precedence: the `path` extra → the configured export directory →
     * `ERROR:no-directory`. Writing to an arbitrary absolute path needs All-Files-Access;
     * without it `path` is honoured only by falling back to the configured SAF directory.
     */
    private fun export(request: Intent, run: Run, progress: ProgressSender): String {
        val cats = AutomationWire.resolveCategories(request.getStringExtra(AutomationWire.EXTRA_ITEMS))
            .getOrElse { return it.message ?: "ERROR:bad items" }
        // A first line before any directory work, so the caller hears us immediately.
        progress.report(WhiteBearExport.Step(0L, cats.size.toLong(), "区分", "区分 0/${cats.size} — 開始"))
        run.alive("開始")

        val name = WhiteBearExport.exportFileName()
        val requested = request.getStringExtra(AutomationWire.EXTRA_PATH)?.trim().orEmpty()
        val allFilesAccess = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            Environment.isExternalStorageManager()
        } else {
            true
        }

        // Both branches below write to `<name>.part` and move it onto `<name>` only once the ZIP
        // is closed and whole — see [WhiteBearExport.PART_SUFFIX] for why. Every way out that is
        // not that move takes the partial with it, so the directory is left with no file at all
        // rather than a short one; the size in the reply is read from the placed file, after the
        // move, and the caller never sees the `.part` name.
        if (requested.isNotEmpty() && allFilesAccess) {
            val dir = File(requested)
            if (!dir.isDirectory && !dir.mkdirs()) return "ERROR:cannot create directory $requested"
            WhiteBearExport.sweepStaleParts(dir)
            val file = File(dir, name)
            val part = File(dir, WhiteBearExport.partName(name))
            var placed = false
            try {
                val out = part.outputStream()
                run.sink = out
                val written = out.use { writeExport(cats, it, run, progress) }
                if (!part.renameTo(file)) return "ERROR:cannot put the finished file in place"
                placed = true
                val bytes = if (file.length() > 0L) file.length() else written.bytes
                progress.finish()
                return "OK:${file.absolutePath}|$bytes|${WhiteBearExport.humanSize(bytes)}|${written.summary}"
            } finally {
                if (!placed) runCatching { part.delete() }
            }
        }

        val dir = WhiteBearExport.exportDir(this)
            ?: return if (requested.isNotEmpty()) "ERROR:no-storage-access" else "ERROR:no-directory"
        WhiteBearExport.sweepStaleParts(dir)
        val target = WhiteBearExport.createPart(dir, name) ?: return "ERROR:cannot create the file"
        var placed = false
        try {
            val out = contentResolver.openOutputStream(target.uri)
                ?: return "ERROR:cannot open the file for writing"
            run.sink = out
            val written = out.use { writeExport(cats, it, run, progress) }
            // A successful rename re-points `target` at the placed document, so its length and
            // its uri below are the finished file's.
            if (!target.renameTo(name)) return "ERROR:cannot put the finished file in place"
            placed = true
            val bytes = if (target.length() > 0L) target.length() else written.bytes
            progress.finish()
            val path = target.uri.path ?: target.uri.toString()
            return "OK:$path|$bytes|${WhiteBearExport.humanSize(bytes)}|${written.summary}"
        } finally {
            if (!placed) runCatching { target.delete() }
        }
    }

    private data class Written(val bytes: Long, val summary: String)

    private fun writeExport(
        cats: Set<WhiteBearExport.Cat>,
        out: OutputStream,
        run: Run,
        progress: ProgressSender
    ): Written {
        // The ZIP stream closes `counting` (and with it `out`) when the export returns, so
        // the count is read afterwards — never flushed again.
        val counting = CountingOutputStream(out)
        // Every entry names itself on its way in: to logcat, so a run can be followed with
        // `adb logcat -s WhiteBearAutomation`, and to the run, so an error line can say what it
        // was on instead of just that it stopped.
        val leash = WhiteBearExport.Leash(
            ceilingMs = EXPORT_CEILING_MS,
            isCancelled = { run.cancelled }
        ) { entry ->
            Log.d(AutomationWire.TAG, "writing $entry")
            run.alive(entry)
            progress.enter(entry)
        }
        val outcome = WhiteBearExport.export(
            context = this,
            cats = cats,
            out = counting,
            onProgress = { step ->
                run.alive()
                progress.report(step)
            },
            leash = leash
        )
        return Written(counting.count, outcome.summary)
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

    /**
     * Progress broadcasts with real counts — never a percentage — throttled to at most one
     * every 500 ms, re-sent as a heartbeat while a long step makes no visible progress, and
     * always sent once more when the export finishes. Reported from the export thread and the
     * watchdog coroutine both, hence the locking.
     */
    private class ProgressSender(
        private val context: Context,
        private val action: String?,
        private val replyPackage: String,
        private val replyId: String?,
        private val onText: (String) -> Unit
    ) {
        private var lastSentAt = 0L
        private var last: WhiteBearExport.Step? = null
        private var item: String = ""

        /**
         * Which category is being written right now — sent on every broadcast as `item`.
         *
         * 自由作業盤 draws the categories as a list and highlights the running row from this.
         * It cannot work that out from `current`, because `current` is whatever is being counted
         * at that moment: categories while they are walked, files while one of them is written.
         * Without `item` the panel falls back to reading `current` as a row position, which is
         * only right while `total` happens to equal the number of rows — so through the cover
         * pass, where `current` runs into the thousands against nine rows, it highlighted
         * nothing at all.
         */
        @Synchronized
        fun enter(entry: String) {
            AutomationWire.categoryOf(entry)?.let { item = it }
        }

        @Synchronized
        fun report(step: WhiteBearExport.Step) {
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

        private fun send(step: WhiteBearExport.Step) {
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
                        // Which row is running. Sub-options name themselves rather than their
                        // parent, so the row that lights up is the part actually being written.
                        if (item.isNotEmpty()) putExtra("item", item)
                        putExtra("text", step.text)
                        putExtra("current", step.current)
                        putExtra("total", step.total)
                        putExtra("unit", step.unit)
                        // The same work measured the other way, 0 where the step cannot know it.
                        // 自由作業盤 draws both counters, and through the covers this is the pair
                        // that visibly moves.
                        putExtra("bytes", step.bytes)
                        putExtra("bytes_total", step.bytesTotal)
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

        /**
         * The run this process owns, or none. Never a bare flag — see [claim].
         *
         * Static, and reachable without the service, so [cancel] can answer for a 中止 without
         * starting anything: a cancel that finds nothing running must cost nothing at all. It is
         * as authoritative here as it was on the instance — an export lives in this process or
         * nowhere, and if the process went, so did the run.
         */
        private val current = AtomicReference<Run?>(null)

        /**
         * 白い熊 pressed 中止 — mark the run and return.
         *
         * Nothing here interrupts a thread, closes a descriptor or stops the service. A cancel
         * that tore the work down from under itself would leave behind exactly the half-written
         * archive it exists to prevent; instead the export notices between entries, unwinds
         * itself, takes its partial file with it and sends the one terminal reply. A cancel with
         * nothing to cancel — nothing running, or a run that already finished — is a no-op, and
         * says so rather than answering for a run it does not have.
         */
        fun cancel(): Boolean {
            val run = current.get() ?: return false
            run.cancelled = true
            Log.i(AutomationWire.TAG, "中止 — cancelling the export at ${run.item}")
            return true
        }

        private const val CHANNEL_ID = "whitebear_backup"
        private const val NOTIFICATION_ID = 4979
        private const val THROTTLE_MS = 500L
        private const val HEARTBEAT_MS = 20_000L
        private const val WAKELOCK_TIMEOUT_MS = 60L * 60L * 1000L

        /** How often the watchdog looks — and, while all is well, beats. */
        private const val WATCHDOG_TICK_MS = 5_000L

        /**
         * 自由作業盤 fails an app that has been silent for 180 s and knows nothing about why.
         * Well inside that, this app says so itself, and says what it was on when it stopped.
         */
        private const val STALL_MS = 90_000L

        /**
         * The export's own ceiling: it throws, so the run ends in an error, not a silence.
         *
         * Long, because a run that carries 「Book covers」 legitimately is one: thousands of files
         * and most of the archive's bytes, minutes rather than seconds. The old 8-minute ceiling
         * was set when this was a settings dump, and would now fail the very run it exists to
         * protect. Being slow is not the failure mode this guards against — being *silent* is,
         * and [STALL_MS] guards that, unchanged.
         */
        private const val EXPORT_CEILING_MS = 50L * 60L * 1000L

        /**
         * The backstop for when that throw cannot happen because a syscall never returns — and
         * the age past which a run is treated as wedged and superseded. Under 自由作業盤's
         * timeout, which is 3600 s for this app, so the batch hears an error rather than waiting
         * the whole way out; under [WAKELOCK_TIMEOUT_MS] too, so no run outlives the wakelock
         * that keeps its CPU on.
         */
        private const val CEILING_MS = 55L * 60L * 1000L

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
