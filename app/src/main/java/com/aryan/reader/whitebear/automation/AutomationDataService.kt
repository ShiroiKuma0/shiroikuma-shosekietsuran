package com.aryan.reader.whitebear.automation

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.Bundle
import android.os.IBinder
import android.os.ParcelFileDescriptor
import android.os.PowerManager
import android.os.SystemClock
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import com.aryan.reader.R
import com.aryan.reader.whitebear.AutomationWire
import com.aryan.reader.whitebear.WhiteBearExport
import java.io.FilterOutputStream
import java.io.OutputStream
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Where a data export or import from the door in [AutomationProvider] actually runs.
 *
 * ## Why a foreground service and not the provider call
 *
 * The call returns in milliseconds; this can run for minutes. Two hard reasons it cannot be
 * done anywhere cheaper:
 *
 * - **A binder call holds the caller.** 応用管理 is drawing a list; a multi-minute synchronous
 *   call would freeze its UI, report no progress and refuse cancellation.
 * - **A backgrounded app writing for minutes is frozen mid-stream on this phone**, which yields
 *   a truncated archive underneath a success reply — the worst failure available, because it is
 *   indistinguishable from a good backup until the day it is restored.
 *
 * This app has the family's clearest case for both: with 「Book covers」 asked for, an export is
 * thousands of files and most of a gigabyte. Hence the wakelock as well — EMUI dozes the CPU out
 * from under a long export with the screen off, exactly as it does to [
 * com.aryan.reader.whitebear.StateExportService].
 *
 * ## The descriptor
 *
 * Already duplicated by [AutomationProvider] before it got here, because the original belongs to
 * the binder transaction and is closed the moment `call()` returns. This service owns the copy
 * and closes it in a `finally` — leaking one holds the caller's file open, and a caller cannot
 * checksum or encrypt a file that is still open.
 */
class AutomationDataService : Service() {

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val importing = intent?.getBooleanExtra(EXTRA_IMPORTING, false) ?: false
        // Within 5 s of the service starting, or the system kills us for it — before anything
        // that could fail, including looking the descriptor up.
        startInForeground(
            getString(
                if (importing) R.string.whitebear_automation_data_importing
                else R.string.whitebear_automation_data_exporting
            )
        )

        val request = intent
        val jobId = request?.getStringExtra(EXTRA_JOB)
        val fd = jobId?.let { HANDOVER.remove(it) }
        if (request == null || jobId == null || fd == null) {
            // A redelivery with nothing behind it, or a job whose descriptor another start
            // already took. Nothing to answer for and nothing to close.
            Log.w(AutomationWire.TAG, "data service started with no job — stopping")
            stopEverything()
            return START_NOT_STICKY
        }

        val replyAction = request.getStringExtra(AutomationProvider.KEY_REPLY_ACTION).orEmpty()
        val replyPackage = request.getStringExtra(AutomationProvider.KEY_REPLY_PACKAGE).orEmpty()
        val progressAction = request.getStringExtra(AutomationProvider.KEY_PROGRESS_ACTION)
        val items = request.getStringExtra(AutomationProvider.KEY_ITEMS)

        val answered = AtomicBoolean(false)
        fun reply(result: String) {
            // Exactly one terminal answer per job, whatever path got here — a synchronous
            // failure and an asynchronous success must never both fire. The same guard the
            // broadcast contract has carried since the first sister app.
            if (!answered.compareAndSet(false, true)) return
            AutomationJobs.finish(jobId)
            Log.i(AutomationWire.TAG, "job=$jobId -> $result")
            if (replyAction.isEmpty() || replyPackage.isEmpty()) return
            runCatching {
                sendBroadcast(
                    Intent(replyAction).apply {
                        setPackage(replyPackage)
                        // Without this a backgrounded caller never hears the answer — and on a
                        // clean phone the caller may not have been launched at all.
                        addFlags(Intent.FLAG_INCLUDE_STOPPED_PACKAGES)
                        putExtra(AutomationProvider.KEY_JOB_ID, jobId)
                        putExtra(AutomationProvider.KEY_RESULT, result)
                    }
                )
            }
        }

        val progress = ProgressSender(this, progressAction, replyPackage, jobId) { text ->
            updateNotification(text)
        }
        // A thread rather than a dispatcher, for the reason the §1 service found the hard way:
        // when this work wedges it must be abandonable, and abandoning a pooled thread poisons
        // whatever runs on it next.
        val beating = AtomicBoolean(true)
        val worker = Thread({
            val wakeLock = acquireWakeLock()
            try {
                val result = runCatching {
                    if (importing) runImport(jobId, fd) else runExport(jobId, fd, items, progress)
                }.getOrElse { error ->
                    if (error is WhiteBearExport.Cancelled) "ERROR:cancelled"
                    else "ERROR:${error.message ?: error.javaClass.simpleName}"
                }
                reply(result)
            } finally {
                beating.set(false)
                // The caller cannot checksum or encrypt a file we are still holding open.
                runCatching { fd.close() }
                runCatching { wakeLock?.takeIf { it.isHeld }?.release() }
                stopEverything()
            }
        }, "wb-automation-data")
        worker.isDaemon = true
        worker.start()

        // A progress broadcast is also the caller's proof we are alive, and it gives up on an app
        // that goes quiet. One entry can legitimately take longer than that on its own — a single
        // large annotation file, or the ZIP's final flush — so the last line is re-sent while the
        // work is still running. It stops the moment the worker ends, so it can never outlive the
        // work it reports on.
        Thread({
            while (beating.get()) {
                Thread.sleep(BEAT_TICK_MS)
                if (beating.get()) progress.heartbeat()
            }
        }, "wb-automation-beat").apply { isDaemon = true }.start()
        return START_NOT_STICKY
    }

    /**
     * Write the archive straight into the caller's descriptor, counting as it goes.
     *
     * The size is counted rather than stat'ed because the caller owns the file and we may not be
     * able to see it at all — it can be an anonymous pipe, or a descriptor into a directory this
     * app cannot list.
     */
    private fun runExport(
        jobId: String,
        fd: ParcelFileDescriptor,
        items: String?,
        progress: ProgressSender
    ): String {
        val cats = AutomationWire.resolveCategories(items).getOrElse {
            return it.message ?: "ERROR:bad items"
        }
        progress.report(WhiteBearExport.Step(0L, cats.size.toLong(), "区分", "区分 0/${cats.size} — 開始"))

        val counting = CountingOutputStream(ParcelFileDescriptor.AutoCloseOutputStream(fd))
        val outcome = counting.use { out ->
            val leash = WhiteBearExport.Leash(
                ceilingMs = EXPORT_CEILING_MS,
                isCancelled = { AutomationJobs.isCancelled(jobId) }
            ) { entry ->
                Log.d(AutomationWire.TAG, "job=$jobId writing $entry")
                progress.enter(entry)
            }
            WhiteBearExport.export(
                context = this,
                cats = cats,
                out = out,
                onProgress = { step -> progress.report(step) },
                leash = leash
            )
        }
        if (AutomationJobs.isCancelled(jobId)) return "ERROR:cancelled"
        progress.finish()
        return "OK:${counting.count}|${WhiteBearExport.humanSize(counting.count)}|${outcome.summary}"
    }

    /**
     * Read the archive once, straight out of the descriptor.
     *
     * **Not buffered whole first**, which is where this departs from the smaller sister apps:
     * an archive of this app carrying annotations and covers is hundreds of megabytes, and both
     * ways of holding one — in memory, or spooled to `cacheDir` — cost more than the restore is
     * worth on a phone that has just been wiped. [WhiteBearExport.import] reads the ZIP entry by
     * entry and applies each as it arrives, so one pass is all it needs.
     *
     * What that gives up is the guarantee that a corrupt archive changes nothing: a stream that
     * fails halfway has already applied the entries before the break. That is an acceptable
     * trade **only because the caller has already verified the file** — 応用管理 checksums every
     * file in a backup before it hands one back — and because an entry that fails on its own is
     * skipped rather than fatal, so a restore is additive per category either way.
     *
     * Categories are taken from the archive rather than from our catalogue: asking for one the
     * archive lacks is how a restore ends up reporting success over nothing.
     */
    private fun runImport(jobId: String, fd: ParcelFileDescriptor): String {
        val cats = WhiteBearExport.Cat.entries.toSet()
        var opened = false
        val summary = WhiteBearExport.import(
            context = this,
            openZip = {
                opened = true
                ParcelFileDescriptor.AutoCloseInputStream(fd)
            },
            cats = cats
        )
        if (!opened) return "ERROR:archive unreadable"
        if (AutomationJobs.isCancelled(jobId)) return "ERROR:cancelled"
        // "Nothing imported." is [WhiteBearExport.import]'s answer for an archive that carried no
        // entry we recognise — an empty file, or somebody else's backup. Reported as an error,
        // because a caller that hears OK over nothing believes the app was restored.
        if (summary == "Nothing imported.") return "ERROR:archive carries no categories"
        // 応用管理 force-stops us straight after this, deliberately and on its side: a running
        // process writes its cached SharedPreferences back out at orderly shutdown and would
        // silently undo the import that just happened.
        return "OK:${summary.lineSequence().count()} categories restored"
    }

    // ---- Foreground plumbing ----

    /** EMUI dozes the CPU with the screen off; a library export runs for minutes. */
    private fun acquireWakeLock(): PowerManager.WakeLock? = runCatching {
        getSystemService(PowerManager::class.java)
            .newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "shosekietsuran:automation-data")
            .apply { setReferenceCounted(false); acquire(WAKELOCK_TIMEOUT_MS) }
    }.getOrNull()

    private fun startInForeground(text: String) {
        runCatching {
            ensureChannel()
            ServiceCompat.startForeground(
                this, NOTIFICATION_ID, buildNotification(text),
                // `specialUse` is what the manifest declares (contract v2 §4) and the constant
                // is API 34; on 白い熊's Mate XT, which reports 31, the type argument is simply
                // not carried and the service is an ordinary foreground one.
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                    ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE
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
     * Progress broadcasts with real counts — never a percentage — throttled to at most one every
     * 500 ms and re-sent as a heartbeat while a long step makes no visible progress.
     *
     * `item` is the category id being written right now, which is what lets 応用管理 and 自由作業盤
     * highlight the row that is running: `current` is whatever is being counted at that moment —
     * categories while they are walked, files while one of them is written — so a four-digit file
     * count against a nine-row list would otherwise light up nothing.
     */
    private class ProgressSender(
        private val context: Context,
        private val action: String?,
        private val replyPackage: String,
        private val jobId: String,
        private val onText: (String) -> Unit
    ) {
        private var lastSentAt = 0L
        private var last: WhiteBearExport.Step? = null
        private var item: String = ""

        /**
         * The leash names every entry on its way in — `library.covers/cover_cache/x.png`, or
         * `library.covers: scanning`. The category id is the head of that, and is checked against
         * the catalogue so a name shape we did not anticipate leaves `item` unset rather than
         * sending a row id nobody has.
         */
        @Synchronized
        fun enter(entry: String) {
            val head = entry.substringBefore('/').substringBefore(':').trim()
            if (WhiteBearExport.catById(head) != null) item = head
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
                        // The door correlates on job_id; reply_id is sent alongside it carrying
                        // the same value, so a reader written for the §1 broadcast contract
                        // recognises these lines too.
                        putExtra(AutomationProvider.KEY_JOB_ID, jobId)
                        putExtra(AutomationWire.EXTRA_REPLY_ID, jobId)
                        putExtra("app", AutomationWire.APP_LABEL)
                        if (item.isNotEmpty()) putExtra("item", item)
                        putExtra("text", step.text)
                        putExtra("current", step.current)
                        putExtra("total", step.total)
                        putExtra("unit", step.unit)
                        putExtra("bytes", step.bytes)
                        putExtra("bytes_total", step.bytesTotal)
                    }
                )
            }
        }
    }

    /** The written size has to come from us — the caller cannot stat its own file mid-write. */
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
        private const val NOTIFICATION_ID = 4980
        private const val THROTTLE_MS = 500L
        private const val HEARTBEAT_MS = 20_000L

        /** How often the beat thread looks — well inside the 30 s the contract allows. */
        private const val BEAT_TICK_MS = 5_000L
        private const val WAKELOCK_TIMEOUT_MS = 60L * 60L * 1000L
        private const val EXTRA_JOB = "job"
        private const val EXTRA_IMPORTING = "importing"

        /**
         * The same ceiling the §1 export runs under: long, because a run carrying 「Book covers」
         * legitimately is thousands of files and most of the archive's bytes. It throws, so the
         * run ends in an error rather than a silence.
         */
        private const val EXPORT_CEILING_MS = 50L * 60L * 1000L

        /**
         * The descriptor's way across, because an Intent is the wrong vehicle for one.
         *
         * A [ParcelFileDescriptor] in an Intent extra is duplicated by the system on delivery and
         * the copy's lifetime stops being ours to reason about. Handing it through a map keyed by
         * the job id keeps exactly one open descriptor with exactly one owner — this service,
         * which closes it in a `finally`.
         */
        private val HANDOVER = ConcurrentHashMap<String, ParcelFileDescriptor>()

        fun start(
            context: Context,
            jobId: String,
            fd: ParcelFileDescriptor,
            importing: Boolean,
            extras: Bundle?
        ) {
            HANDOVER[jobId] = fd
            val started = runCatching {
                context.startForegroundService(
                    Intent(context, AutomationDataService::class.java).apply {
                        putExtra(EXTRA_JOB, jobId)
                        putExtra(EXTRA_IMPORTING, importing)
                        putExtra(
                            AutomationProvider.KEY_ITEMS,
                            extras?.getString(AutomationProvider.KEY_ITEMS)
                        )
                        putExtra(
                            AutomationProvider.KEY_REPLY_ACTION,
                            extras?.getString(AutomationProvider.KEY_REPLY_ACTION)
                        )
                        putExtra(
                            AutomationProvider.KEY_REPLY_PACKAGE,
                            extras?.getString(AutomationProvider.KEY_REPLY_PACKAGE)
                        )
                        putExtra(
                            AutomationProvider.KEY_PROGRESS_ACTION,
                            extras?.getString(AutomationProvider.KEY_PROGRESS_ACTION)
                        )
                    }
                )
            }
            // A descriptor left in the handover map for a service that never started would be
            // held open until this process dies, with the caller's file wedged behind it.
            started.onFailure {
                HANDOVER.remove(jobId)
                throw it
            }
        }
    }
}
