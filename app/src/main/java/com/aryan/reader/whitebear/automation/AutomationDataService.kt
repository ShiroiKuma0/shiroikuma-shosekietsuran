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
import java.util.concurrent.atomic.AtomicReference

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
        if (request == null || jobId == null) {
            // A redelivery with nothing behind it. No id, so nobody is waiting on an answer we
            // could correlate, and there is nothing to close.
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

        // Taken only now, so the guard below can still answer under the id the caller holds. The
        // descriptor going missing used to end the service in silence — the caller had already
        // been handed `OK:<jobId>` by the provider and was left waiting out its whole timeout on
        // a job that had stopped before it started.
        val fd = HANDOVER.remove(jobId)
        if (fd == null) {
            // Which of the two it is decides the cure, and neither is guessable after the fact,
            // so the answer says. An **empty** map means this is not the process the provider
            // put the descriptor in — the handover is a static field, so a process rebuilt
            // between `startForegroundService` and `onStartCommand` finds nothing. A map that
            // still holds other jobs means a genuine id mismatch instead.
            val why = if (HANDOVER.isEmpty()) "process was rebuilt" else "id not in the handover"
            Log.w(AutomationWire.TAG, "job=$jobId has no descriptor — $why")
            reply("ERROR:no descriptor for this job — $why")
            stopEverything()
            return START_NOT_STICKY
        }

        val progress = ProgressSender(this, progressAction, replyPackage, jobId) { text ->
            updateNotification(text)
        }
        // A thread rather than a dispatcher, for the reason the §1 service found the hard way:
        // when this work wedges it must be abandonable, and abandoning a pooled thread poisons
        // whatever runs on it next.
        val beating = AtomicBoolean(true)
        val wakeLockRef = AtomicReference<PowerManager.WakeLock?>()
        val worker = Thread({
            val wakeLock = acquireWakeLock().also { wakeLockRef.set(it) }
            try {
                val result = runCatching {
                    if (importing) runImport(jobId, fd, progress)
                    else runExport(jobId, fd, items, progress)
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

        // The watchdog, and the heartbeat, and one loop for both — the §1 export service's shape,
        // learnt there the hard way and not inherited here until this run needed it.
        //
        // A progress broadcast is the caller's proof we are alive, and it gives up on an app that
        // goes quiet. One entry can legitimately take longer than a beat on its own — a single
        // large annotation file, the ZIP's final flush — so the last line is re-sent while the
        // work is still moving. But a beat that can outlive the work it reports on is worse than
        // no beat at all, so the same tick that finds the work stopped is the one that says so:
        // it answers for the run, tries to break whatever it is wedged on, and gives up the
        // service. **Nothing may leave the caller waiting on a job that will not answer.**
        val startedAt = SystemClock.elapsedRealtime()
        Thread({
            while (beating.get()) {
                Thread.sleep(BEAT_TICK_MS)
                if (!beating.get()) break
                val now = SystemClock.elapsedRealtime()
                val silent = now - progress.movedAt
                val verdict = when {
                    now - startedAt > CEILING_MS ->
                        "ERROR:timed out after ${CEILING_MS / 1000} s at ${progress.doing}"
                    silent > STALL_MS ->
                        "ERROR:stalled — no progress for ${silent / 1000} s at ${progress.doing}"
                    else -> null
                }
                if (verdict == null) {
                    progress.heartbeat()
                    continue
                }
                Log.w(AutomationWire.TAG, "job=$jobId $verdict")
                beating.set(false)
                reply(verdict)
                // Closing the descriptor is the one thing that can break a read or a write that
                // will not return: Android signals the threads blocked on it. The interrupt is
                // the same bet. Neither is guaranteed and neither is waited for — the answer has
                // already gone out.
                runCatching { fd.close() }
                runCatching { worker.interrupt() }
                runCatching { wakeLockRef.get()?.takeIf { it.isHeld }?.release() }
                stopEverything()
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
     *
     * ## Why this reports, when the first version of it did not
     *
     * It was written to say nothing at all: no [ProgressSender], no [WhiteBearExport.Leash], no
     * ceiling, and the cancel flag read once after the whole archive had already been applied.
     * That is survivable for an app whose backup is a few hundred kilobytes and fatal for this
     * one. 応用管理 gives an app ten minutes of silence and then declares it dead; restoring
     * 白い熊's library is 8,543 entries and 2.4 GB, whose *export* alone took seven minutes on
     * this phone. So the restore was killed at exactly ten minutes, every time, with 「heard 0
     * progress, 0 replies」 — the 0 progress not a symptom but a guarantee (白い熊, 2026-09-08).
     */
    private fun runImport(
        jobId: String,
        fd: ParcelFileDescriptor,
        progress: ProgressSender
    ): String {
        val cats = WhiteBearExport.Cat.entries.toSet()
        // The denominator, when the caller handed us a real file rather than a pipe. 応用管理
        // knows this number before it opens the descriptor, so a line carrying it is a line it
        // can draw a real bar from; -1 (a pipe) simply reports no total.
        val totalBytes = runCatching { fd.statSize }.getOrDefault(-1L).coerceAtLeast(0L)
        progress.report(
            WhiteBearExport.Step(0L, totalBytes, "bytes", "復元 — 開始", 0L, totalBytes)
        )
        var opened = false
        val summary = WhiteBearExport.import(
            context = this,
            openZip = {
                opened = true
                ParcelFileDescriptor.AutoCloseInputStream(fd)
            },
            cats = cats,
            onProgress = { step -> progress.report(step) },
            leash = WhiteBearExport.Leash(
                ceilingMs = IMPORT_CEILING_MS,
                isCancelled = { AutomationJobs.isCancelled(jobId) }
            ) { entry -> progress.enter(entry) },
            totalBytes = totalBytes
        )
        if (!opened) return "ERROR:archive unreadable"
        if (AutomationJobs.isCancelled(jobId)) return "ERROR:cancelled"
        // "Nothing imported." is [WhiteBearExport.import]'s answer for an archive that carried no
        // entry we recognise — an empty file, or somebody else's backup. Reported as an error,
        // because a caller that hears OK over nothing believes the app was restored.
        if (summary == "Nothing imported.") return "ERROR:archive carries no categories"
        progress.finish()
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
         * When the work last really moved, and what it was on — read by the watchdog, which runs
         * on another thread and must never be told a throttled broadcast means a stalled run.
         *
         * Deliberately **not** [lastSentAt]: that only moves when a line actually goes out, and
         * lines are throttled to one every 500 ms. What the watchdog has to judge is whether the
         * *work* is moving, which is every call that arrives here, sent or swallowed.
         */
        @Volatile
        var movedAt: Long = SystemClock.elapsedRealtime()
            private set

        /** What the run is on, for the message a stall is answered with. */
        @Volatile
        var doing: String = "開始"
            private set

        /**
         * The leash names every entry on its way in; [AutomationWire.categoryOf] turns that into
         * the category id, and answers null for a shape it does not recognise so `item` is left
         * unset rather than carrying a row id nobody has.
         *
         * Shared with the §1 export service deliberately: this derivation was written twice, and
         * the copy here quietly missed the two `<id>.jsonl: <table>` shapes — the table entries —
         * because it did not strip the extension. One implementation cannot drift from itself.
         */
        @Synchronized
        fun enter(entry: String) {
            AutomationWire.categoryOf(entry)?.let { item = it }
            doing = entry
            movedAt = SystemClock.elapsedRealtime()
        }

        @Synchronized
        fun report(step: WhiteBearExport.Step) {
            last = step
            movedAt = SystemClock.elapsedRealtime()
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
                        // The SAME line under both names. 自由作業盤 reads 「text」 (the §1
                        // contract); 応用管理 reads 「result」, the same key its terminal reply
                        // uses — so until now every progress line this app sent reached it with
                        // a null label and it drew the bare numbers (白い熊, 2026-09-09).
                        putExtra(AutomationProvider.KEY_RESULT, step.text)
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

        /**
         * Silence past which this run is treated as wedged and answered for.
         *
         * 応用管理 fails an app it has not heard from for 600 s and knows nothing about why.
         * Well inside that, this app says so itself and says what it was on when it stopped —
         * which is the difference between a log 白い熊 can act on and 「heard 0 progress」.
         */
        private const val STALL_MS = 90_000L

        /**
         * The backstop for a run that neither finishes nor stalls visibly — a syscall that never
         * returns cannot be caught by the leash, because the leash is only asked between steps.
         * Under [WAKELOCK_TIMEOUT_MS], so no run outlives the wakelock keeping its CPU on.
         */
        private const val CEILING_MS = 55L * 60L * 1000L

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
         * The same ceiling for the other direction, and for the same reason — a restore that
         * carries 「Book covers」 writes back every one of those thousands of files. It throws,
         * so a run that really has gone on too long ends in an error rather than a silence.
         */
        private const val IMPORT_CEILING_MS = 50L * 60L * 1000L

        /**
         * How long the provider call stays open waiting for the service to claim the descriptor,
         * and how often it looks. Long enough for a cold start's `Application.onCreate` plus the
         * main thread reaching `onStartCommand`; short enough that a door which really is broken
         * says so in seconds instead of costing the caller its whole silence timeout.
         */
        private const val CLAIM_TIMEOUT_MS = 15_000L
        private const val CLAIM_POLL_MS = 25L

        /**
         * The descriptor's way across, because an Intent is the wrong vehicle for one.
         *
         * A [ParcelFileDescriptor] in an Intent extra is duplicated by the system on delivery and
         * the copy's lifetime stops being ours to reason about. Handing it through a map keyed by
         * the job id keeps exactly one open descriptor with exactly one owner — this service,
         * which closes it in a `finally`.
         *
         * **What that costs, and how it is paid for.** A static field only reaches the service if
         * `onStartCommand` runs in the same process the provider wrote it from, which is not
         * guaranteed for a job that arrives into a freshly force-stopped app. So [start] does not
         * answer until the entry has been taken: an id still sitting here when the wait runs out
         * means nothing claimed it, and the caller is told so instead of being promised a job
         * that will never run. An entry is therefore only ever removed by the service that is
         * about to do the work, or by [start] giving up on it.
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
            val request = Intent(context, AutomationDataService::class.java).apply {
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
            val started = runCatching {
                // The return value is load-bearing, and used to be dropped on the floor.
                // `startForegroundService` answers **null** when the component could not be
                // started at all — disabled, or blocked by an Intent Firewall rule, which is
                // precisely what 応用管理's own component blocker installs. Nothing throws. The
                // door would then answer `OK:<jobId>` for a job that does not exist and the
                // caller would wait out its whole ten-minute silence timeout on a service that
                // was never running: a promise we cannot keep is worse than a refusal.
                checkNotNull(context.startForegroundService(request)) {
                    "the data service did not start"
                }
            }
            // A descriptor left in the handover map for a service that never started would be
            // held open until this process dies, with the caller's file wedged behind it.
            started.onFailure {
                HANDOVER.remove(jobId)
                throw it
            }

            // Do not answer `OK:` until the service has actually taken the descriptor.
            //
            // This is the fix for the restore that accepted a job and then did nothing at all
            // (白い熊, 2026-09-08). The handover is a **static field**, so it only reaches the
            // service if `onStartCommand` runs in *this* process — and the import is the one
            // call where that is not a given. 応用管理 force-stops us and calls `import`
            // immediately, so this binder call is the sole reason the process exists; the
            // export never gets there cold, because `describe()` has already warmed the process
            // and no force-stop precedes it. A process whose only client reference is released
            // the instant `call()` returns is exactly the process the system trims, and the
            // queued service start then arrives in a **rebuilt** process with an empty map —
            // `onStartCommand` finds no descriptor and stops, having already been answered
            // `OK:` by us. From outside that is a job accepted, no CPU, no progress, no reply,
            // for as long as the caller is willing to wait.
            //
            // Staying inside `call()` closes that window from both ends: the caller's provider
            // reference keeps this process up and important while we wait, and by the time we
            // answer, the service has the descriptor and owns the job. If it never takes it, we
            // say so now rather than leaving 応用管理 to a ten-minute silence.
            val deadline = SystemClock.elapsedRealtime() + CLAIM_TIMEOUT_MS
            while (HANDOVER.containsKey(jobId) && SystemClock.elapsedRealtime() < deadline) {
                Thread.sleep(CLAIM_POLL_MS)
            }
            // Generous, because a cold start has to get through `Application.onCreate` before
            // the main thread can reach `onStartCommand`; the caller waits on a worker thread
            // and is happy to wait minutes, so seconds here cost nothing anyone notices.
            if (HANDOVER.remove(jobId) != null) {
                throw IllegalStateException(
                    "the data service did not pick the job up within ${CLAIM_TIMEOUT_MS / 1000} s"
                )
            }
        }
    }
}
