/*
 * Episteme Reader - A native Android document reader.
 * Copyright (C) 2026 Episteme
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as
 * published by the Free Software Foundation, either version 3 of the
 * License, or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/>.
 *
 * mail: epistemereader@gmail.com
 */
// FolderSyncWorker.kt
package com.aryan.reader

import android.content.Context
import timber.log.Timber
import androidx.core.net.toUri
import androidx.documentfile.provider.DocumentFile
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkerParameters
import androidx.work.WorkManager
import com.aryan.reader.data.RecentFileItem
import com.aryan.reader.data.RecentFilesRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import androidx.core.content.edit
import com.aryan.reader.data.LocalSyncUtils
import com.aryan.reader.data.FolderBookMetadata
import com.aryan.reader.data.toSharedFolderBookMetadata
import com.aryan.reader.shared.BookItem as SharedBookItem
import com.aryan.reader.shared.EpubAnnotationSerializer
import com.aryan.reader.shared.EpubBookmark
import com.aryan.reader.shared.LOCAL_FOLDER_SYNC_DATA_DIR
import com.aryan.reader.shared.LocalFolderSyncEngine
import com.aryan.reader.shared.ReaderLocator
import com.aryan.reader.shared.SharedFolderScannedFile
import com.aryan.reader.shared.SharedReaderScreenState
import com.aryan.reader.shared.reader.ReaderBookmark
import java.io.File
import android.provider.DocumentsContract

class FolderSyncWorker(
    private val appContext: Context,
    workerParams: WorkerParameters
) : CoroutineWorker(appContext, workerParams) {

    private val recentFilesRepository = RecentFilesRepository(appContext)

    // Entries walked across every folder this run, reported back in the worker's output data.
    private var filesSeenTotal = 0

    companion object {
        const val WORK_NAME = "FolderSyncWorker"
        const val WORK_NAME_ONETIME = "FolderSyncWorker_OneTime"

        // Fork: a discovery pass may chain the full reconciliation behind itself. It runs
        // under its own unique name so enqueueing it cannot cancel the pass that spawned it.
        const val WORK_NAME_FOLLOWUP = "FolderSyncWorker_FollowUp"

        const val KEY_METADATA_ONLY = "key_metadata_only"
        const val KEY_TARGET_FOLDER_URI = "key_target_folder_uri"

        // Fork: "find new files only" mode — walks the folder tree and inserts the books the
        // library does not have yet, skipping every per-book sidecar read. On a 9000-book
        // folder those sidecar opens, not the tree walk, are what made a rescan take minutes.
        const val KEY_DISCOVER_ONLY = "key_discover_only"
        const val KEY_CHAIN_FULL_SYNC = "key_chain_full_sync"

        const val PROGRESS_PHASE = "progress_phase"
        const val PROGRESS_FILES_SEEN = "progress_files_seen"
        const val PROGRESS_NEW_BOOKS = "progress_new_books"
        const val OUTPUT_NEW_BOOKS = "output_new_books"

        // Reported back so the completion banner can say what the pass actually covered —
        // "scanned 0 files" is the difference between a fast scan and one that never ran.
        // The wait is reported apart from the work: time spent queued or blocked behind
        // another pass is not scanning, and folding the two together hides exactly the kind
        // of stall this worker is prone to.
        const val OUTPUT_FILES_SEEN = "output_files_seen"
        const val OUTPUT_WAIT_MS = "output_wait_ms"
        const val OUTPUT_WORK_MS = "output_work_ms"

        const val PHASE_DISCOVER = "discover"
        const val PHASE_SCAN = "scan"

        // Newly found books are written to the DB in batches while the walk is still
        // running, so they show up in the library instead of waiting for the whole pass.
        private const val DISCOVER_FLUSH_SIZE = 200
        private const val PROGRESS_REPORT_INTERVAL = 100

        // 白い熊: the full reconciliation reads every per-book sidecar and takes minutes on a
        // large library. Chaining it behind every discovery pass meant the next rescan blocked
        // on syncMutex until it finished — 25 s of spinner before the walk could even start.
        // It now runs at most this often; a manual "Scan All" still forces one at any time.
        private const val FULL_SYNC_MIN_INTERVAL_MS = 6 * 60 * 60 * 1000L
        private const val KEY_LAST_FULL_SYNC_TIME = "last_full_folder_sync_time"

        private val syncMutex = Mutex()
    }

    override suspend fun doWork(): Result {
        val workerStart = ReaderPerfLog.nowNanos()
        val isMetadataOnly = inputData.getBoolean(KEY_METADATA_ONLY, false)
        val isDiscoverOnly = inputData.getBoolean(KEY_DISCOVER_ONLY, false)
        val chainFullSync = inputData.getBoolean(KEY_CHAIN_FULL_SYNC, false)
        val targetFolderUri = inputData.getString(KEY_TARGET_FOLDER_URI)
        val prefs = appContext.getSharedPreferences("reader_user_prefs", Context.MODE_PRIVATE)

        val jsonString = prefs.getString(SyncedFolderPrefs.KEY_SYNCED_FOLDERS_JSON, null)
        val folders = SyncedFolderPrefs.decodeSyncedFolders(
            jsonString = jsonString,
            legacyUri = prefs.getString(SyncedFolderPrefs.KEY_LEGACY_SYNCED_FOLDER_URI, null),
            syncableTypes = ANDROID_SYNCABLE_FILE_TYPES
        )

        if (folders.isEmpty()) {
            ReaderPerfLog.w("FolderSync worker aborted: no linked folders")
            return Result.success()
        }

        val enabledFolders = folders.filter { it.localSyncEnabled }
        val foldersToProcess = if (targetFolderUri.isNullOrBlank()) {
            enabledFolders
        } else {
            enabledFolders.filter { it.uriString == targetFolderUri }
        }

        if (foldersToProcess.isEmpty()) {
            ReaderPerfLog.w("FolderSync worker aborted: target folder not linked or disabled target=$targetFolderUri")
            return Result.success()
        }

        ReaderPerfLog.d(
            "FolderSync worker start folders=${foldersToProcess.size}/${folders.size} " +
                "target=${targetFolderUri ?: "ALL"} metadataOnly=$isMetadataOnly discoverOnly=$isDiscoverOnly"
        )

        suspend fun runPass(): Result {
            val workStart = ReaderPerfLog.nowNanos()
            val waitMs = ReaderPerfLog.elapsedMs(workerStart)
            var allSuccess = true
            var newBooks = 0

            for (folderConfig in foldersToProcess) {
                if (isDiscoverOnly) {
                    newBooks += discoverNewBooksForFolder(folderConfig)
                } else {
                    val success = performSyncForFolder(folderConfig, isMetadataOnly)
                    if (!success) allSuccess = false
                }
            }

            if (jsonString != null) {
                try {
                    val array = org.json.JSONArray(jsonString)
                    val now = System.currentTimeMillis()
                    val processedUris = foldersToProcess.mapTo(mutableSetOf()) { it.uriString }
                    for (i in 0 until array.length()) {
                        val obj = array.getJSONObject(i)
                        if (obj.optString("uri") in processedUris) {
                            obj.put("lastScanTime", now)
                        }
                    }
                    prefs.edit { putString(SyncedFolderPrefs.KEY_SYNCED_FOLDERS_JSON, array.toString()) }
                } catch (_: Exception) {}
            }

            if (!isDiscoverOnly && !isMetadataOnly && !isStopped) {
                prefs.edit { putLong(KEY_LAST_FULL_SYNC_TIME, System.currentTimeMillis()) }
            }

            if (isDiscoverOnly && chainFullSync && !isStopped) {
                val sinceFullSync = System.currentTimeMillis() - prefs.getLong(KEY_LAST_FULL_SYNC_TIME, 0L)
                if (sinceFullSync >= FULL_SYNC_MIN_INTERVAL_MS) {
                    enqueueFollowUpFullSync(targetFolderUri)
                } else {
                    ReaderPerfLog.d("FolderDiscover full sync not chained: last one ${sinceFullSync}ms ago")
                }
            }

            val workMs = ReaderPerfLog.elapsedMs(workStart)
            ReaderPerfLog.i(
                "FolderSync worker finished status=${if (allSuccess) "success" else "failure"} " +
                    "folders=${foldersToProcess.size} discoverOnly=$isDiscoverOnly " +
                    "entries=$filesSeenTotal newBooks=$newBooks waited=${waitMs}ms work=${workMs}ms"
            )

            val output = androidx.work.Data.Builder()
                .putInt(OUTPUT_NEW_BOOKS, newBooks)
                .putInt(OUTPUT_FILES_SEEN, filesSeenTotal)
                .putLong(OUTPUT_WAIT_MS, waitMs)
                .putLong(OUTPUT_WORK_MS, workMs)
                .build()
            return if (allSuccess) Result.success(output) else Result.failure(output)
        }

        return withContext(Dispatchers.IO) {
            // 白い熊: a discovery pass deliberately runs OUTSIDE syncMutex. It only inserts books
            // the folder does not have yet, and a concurrent reconciliation computes its removals
            // from a snapshot taken before those rows existed, so it cannot delete them. Taking
            // the lock only made the rescan button wait out whatever slow pass held it.
            if (isDiscoverOnly) {
                runPass()
            } else {
                syncMutex.withLock { runPass() }
            }
        }
    }

    /**
     * Fork: the fast path behind every "rescan" entry point. It walks the folder tree and
     * writes only the books the library does not know yet, in batches, so they appear while
     * the walk is still running. Sidecar reconciliation (reading progress, annotations,
     * removals, id migrations) is left to the full sync that may be chained behind it.
     */
    private suspend fun discoverNewBooksForFolder(folderConfig: SyncedFolder): Int {
        val folderUriString = folderConfig.uriString
        if (folderUriString.isBlank()) return 0
        val folderUri = folderUriString.toUri()
        val folderStart = ReaderPerfLog.nowNanos()

        if (!isFolderStillLinked(folderUriString)) {
            ReaderPerfLog.w("FolderDiscover folder skipped: no longer linked folder=$folderUriString")
            return 0
        }

        try {
            appContext.contentResolver.takePersistableUriPermission(
                folderUri,
                android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION
            )
        } catch (_: SecurityException) {
            ReaderPerfLog.w("FolderDiscover folder skipped: no permission folder=$folderUriString")
            return 0
        }

        val knownBookIds = ReaderPerfLog.measureSuspend(
            name = "FolderDiscover phase load-known-ids",
            minLogMs = 25L
        ) {
            recentFilesRepository.getBookIdsBySourceFolder(folderUriString).toHashSet()
        }

        val nowMillis = System.currentTimeMillis()
        val pending = mutableListOf<RecentFileItem>()
        var newBooks = 0

        suspend fun flushPending() {
            if (pending.isEmpty()) return
            recentFilesRepository.addRecentFiles(pending.toList())
            newBooks += pending.size
            pending.clear()
        }

        val walkStats = try {
            val stats = walkFolderFiles(
                folderUri = folderUri,
                folderUriString = folderUriString,
                allowedFileTypes = folderConfig.allowedFileTypes,
                onProgress = { filesSeen -> reportProgress(PHASE_DISCOVER, filesSeen, newBooks + pending.size) }
            ) { file ->
                val bookId = file.stableBookId
                if (knownBookIds.add(bookId)) {
                    pending += file.toDiscoveredRecentFileItem(bookId, nowMillis)
                    if (pending.size >= DISCOVER_FLUSH_SIZE) flushPending()
                }
            }
            // Do not write the tail batch into a folder that was unlinked mid-walk.
            if (!stats.stoppedForUnlinkedFolder && isFolderStillLinked(folderUriString)) {
                flushPending()
            }
            stats
        } catch (e: Exception) {
            Timber.tag("FolderDiscover").e(e, "Error during folder discovery.")
            FolderWalkStats()
        }

        ReaderPerfLog.i(
            "FolderDiscover folder finished elapsed=${ReaderPerfLog.elapsedMs(folderStart)}ms " +
                "dirs=${walkStats.dirsScanned} entries=${walkStats.filesSeen} known=${knownBookIds.size} " +
                "new=$newBooks unlinkedAbort=${walkStats.stoppedForUnlinkedFolder} folder=$folderUriString"
        )

        filesSeenTotal += walkStats.filesSeen

        if (newBooks > 0 && !isStopped && !walkStats.stoppedForUnlinkedFolder) {
            enqueueMetadataExtraction(folderUriString)
        }

        return newBooks
    }

    private suspend fun reportProgress(phase: String, filesSeen: Int, newBooks: Int) {
        try {
            setProgress(
                androidx.work.Data.Builder()
                    .putString(PROGRESS_PHASE, phase)
                    .putInt(PROGRESS_FILES_SEEN, filesSeen)
                    .putInt(PROGRESS_NEW_BOOKS, newBooks)
                    .build()
            )
        } catch (_: Exception) {
            // A cancelled worker can no longer publish progress; the scan itself still stops cleanly.
        }
    }

    private fun enqueueMetadataExtraction(folderUriString: String) {
        ReaderPerfLog.i("FolderSync enqueue metadata extraction folder=$folderUriString")
        val metaRequest = OneTimeWorkRequestBuilder<MetadataExtractionWorker>()
            .setInputData(
                androidx.work.Data.Builder()
                    .putString(MetadataExtractionWorker.KEY_SOURCE_FOLDER_URI, folderUriString)
                    .build()
            )
            .build()
        WorkManager.getInstance(appContext).enqueueUniqueWork(
            MetadataExtractionWorker.WORK_NAME,
            ExistingWorkPolicy.REPLACE,
            metaRequest
        )
    }

    private fun enqueueFollowUpFullSync(targetFolderUri: String?) {
        val data = androidx.work.Data.Builder()
            .putBoolean(KEY_METADATA_ONLY, false)
            .apply {
                if (!targetFolderUri.isNullOrBlank()) {
                    putString(KEY_TARGET_FOLDER_URI, targetFolderUri)
                }
            }
            .build()
        WorkManager.getInstance(appContext).enqueueUniqueWork(
            WORK_NAME_FOLLOWUP,
            ExistingWorkPolicy.REPLACE,
            OneTimeWorkRequestBuilder<FolderSyncWorker>().setInputData(data).build()
        )
        ReaderPerfLog.d("FolderDiscover chained full sync target=${targetFolderUri ?: "ALL"}")
    }

    private fun SharedFolderScannedFile.toDiscoveredRecentFileItem(
        bookId: String,
        nowMillis: Long
    ): RecentFileItem {
        // Mirrors what LocalFolderSyncEngine builds for a book it has never seen, so the
        // chained full sync finds the row unchanged and does not rewrite it.
        return RecentFileItem(
            bookId = bookId,
            uriString = path,
            type = type,
            displayName = name,
            timestamp = nowMillis,
            title = name.substringBeforeLast('.', missingDelimiterValue = name),
            isRecent = false,
            isAvailable = true,
            lastModifiedTimestamp = nowMillis,
            isDeleted = false,
            sourceFolderUri = sourceFolder,
            fileSize = size,
            fileContentModifiedTimestamp = lastModified,
            folderTextMetadataParsed = false,
            folderCoverMetadataParsed = false
        )
    }

    private suspend fun performSyncForFolder(folderConfig: SyncedFolder, metadataOnly: Boolean): Boolean {
        val folderUriString = folderConfig.uriString
        val allowedFileTypes = folderConfig.allowedFileTypes
        if (folderUriString.isBlank()) return true
        val folderUri = folderUriString.toUri()
        val folderStart = ReaderPerfLog.nowNanos()
        var dirsScanned = 0
        var filesSeen = 0
        var supportedBooksSeen = 0
        var dbFlushes = 0
        var sidecarsImported = 0
        var stoppedForUnlinkedFolder = false

        try {
            if (!isFolderStillLinked(folderUriString)) {
                ReaderPerfLog.w("FolderSync folder skipped: no longer linked folder=$folderUriString")
                return true
            }

            try {
                appContext.contentResolver.takePersistableUriPermission(
                    folderUri,
                    android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION
                )
            } catch (_: SecurityException) {
                return false
            }

            val documentTree = DocumentFile.fromTreeUri(appContext, folderUri)
            if (documentTree == null || !documentTree.isDirectory) {
                return false
            }

            ReaderPerfLog.d("FolderSync phase legacy-sidecar-migration mapped-to-shared")

            val folderMetadataMap = ReaderPerfLog.measureSuspend(
                name = "FolderSync phase metadata-sidecars",
                minLogMs = 25L,
                details = { "metadataOnly=$metadataOnly" }
            ) {
                LocalSyncUtils.getAllFolderMetadata(appContext, folderUri) { isStopped }.toMutableMap()
            }
            ReaderPerfLog.d(
                "FolderSync metadata-sidecars records=${folderMetadataMap.size} metadataOnly=$metadataOnly folder=$folderUriString"
            )

            val existingFolderBooks = ReaderPerfLog.measureSuspend(
                name = "FolderSync phase load-existing-db",
                minLogMs = 25L
            ) {
                recentFilesRepository.getFilesBySourceFolder(folderUriString)
            }
            val existingItemsMap = existingFolderBooks.associateBy { it.bookId }.toMutableMap()

            val scanResult = if (metadataOnly) {
                AndroidFolderScanResult()
            } else {
                ReaderPerfLog.measureSuspend(
                    name = "FolderSync phase scan-folder",
                    minLogMs = 25L
                ) {
                    scanFolderFiles(
                        folderUri = folderUri,
                        folderUriString = folderUriString,
                        allowedFileTypes = allowedFileTypes
                    )
                }
            }
            dirsScanned = scanResult.dirsScanned
            filesSeen = scanResult.filesSeen
            filesSeenTotal += scanResult.filesSeen
            supportedBooksSeen = scanResult.files.size
            stoppedForUnlinkedFolder = scanResult.stoppedForUnlinkedFolder

            if (isStopped || stoppedForUnlinkedFolder) {
                ReaderPerfLog.w(
                    "FolderSync folder aborted before shared engine stopped=$isStopped " +
                        "unlinkedAbort=$stoppedForUnlinkedFolder folder=$folderUriString"
                )
                return true
            }

            val nowMillis = System.currentTimeMillis()
            val folder = SyncedFolder(
                uriString = folderUriString,
                name = documentTree.name ?: folderConfig.name,
                lastScanTime = nowMillis,
                allowedFileTypes = allowedFileTypes,
                localSyncEnabled = true
            )
            val sharedState = SharedReaderScreenState(
                rawLibraryBooks = existingFolderBooks.map { it.toFolderSyncSharedBookItem() },
                syncedFolders = listOf(folder)
            )
            val syncResult = LocalFolderSyncEngine.syncFolder(
                state = sharedState,
                folder = folder,
                files = scanResult.files,
                remoteMetadata = folderMetadataMap.mapValues { it.value.toSharedFolderBookMetadata() },
                nowMillis = nowMillis,
                metadataOnly = metadataOnly
            )

            if (syncResult.idMigrations.isNotEmpty()) {
                val preloadedSidecars = ReaderPerfLog.measureSuspend(
                    name = "FolderSync phase migration-sidecars",
                    minLogMs = 25L
                ) {
                    LocalSyncUtils.preloadAnnotationSidecars(appContext, folderUri) { isStopped }.toMutableMap()
                }
                syncResult.idMigrations.forEach { (oldId, newId) ->
                    Timber.tag("FolderSync").i("Migrating folder book ID via shared engine $oldId -> $newId")
                    migrateFolderBookId(
                        folderUriString = folderUriString,
                        oldId = oldId,
                        newId = newId,
                        folderMetadataMap = folderMetadataMap,
                        preloadedSidecars = preloadedSidecars,
                        existingItemsMap = existingItemsMap
                    )
                }
            }

            if (!isFolderStillLinked(folderUriString)) {
                ReaderPerfLog.w("FolderSync folder abort: folder unlinked before DB write folder=$folderUriString")
                stoppedForUnlinkedFolder = true
                return true
            }

            val scannedFilesById = scanResult.files.associateBy { it.stableBookId }
            val syncedItems = syncResult.state.rawLibraryBooks.map { book ->
                val existing = existingItemsMap[book.id]
                val metadata = appliedMetadataFor(
                    book = book,
                    existing = existing,
                    metadata = folderMetadataMap[book.id]
                )
                book.toFolderSyncRecentFileItem(
                    existing = existing,
                    appliedMetadata = metadata,
                    scannedFile = scannedFilesById[book.id],
                    nowMillis = nowMillis
                )
            }
            val changedItems = syncedItems.filter { item -> existingItemsMap[item.bookId] != item }

            changedItems
                .filter { item ->
                    val previous = existingItemsMap[item.bookId]
                    previous != null && folderFileContentChanged(previous, item)
                }
                .forEach { item ->
                    Timber.tag("FolderSync").i("File content changed for ${item.displayName}; refreshing extracted metadata.")
                    recentFilesRepository.clearLocalCachesForBook(item.bookId)
                }

            if (changedItems.isNotEmpty()) {
                recentFilesRepository.addRecentFiles(changedItems)
                dbFlushes++
            }

            if (!metadataOnly && syncResult.removedBookIds.isNotEmpty()) {
                Timber.tag("FolderSync").i("Cleaning up ${syncResult.removedBookIds.size} missing folder books.")
                recentFilesRepository.deleteFilePermanently(syncResult.removedBookIds.toList())
            }

            val booksForAnnotationSync = if (metadataOnly) {
                syncedItems
            } else {
                ReaderPerfLog.measureSuspend(
                    name = "FolderSync phase load-post-scan-db",
                    minLogMs = 25L
                ) {
                    recentFilesRepository.getFilesBySourceFolder(folderUriString)
                }
            }
            sidecarsImported += importAnnotationSidecarsForBooks(
                folderUri = folderUri,
                folderUriString = folderUriString,
                books = booksForAnnotationSync,
                phase = if (metadataOnly) "metadata-only" else "post-scan"
            )

            val elapsed = ReaderPerfLog.elapsedMs(folderStart)
            ReaderPerfLog.i(
                "FolderSync folder finished metadataOnly=$metadataOnly elapsed=${elapsed}ms " +
                    "dirs=$dirsScanned entries=$filesSeen supported=$supportedBooksSeen " +
                    "new=${syncResult.stats.newBooks} updated=${syncResult.stats.updatedBooks} " +
                    "remoteUpdates=${syncResult.stats.remoteMetadataUpdates} unchanged=${syncResult.stats.unchangedBooks} " +
                    "removed=${syncResult.stats.removedBooks} migrated=${syncResult.stats.migratedBooks} " +
                    "dbFlushes=$dbFlushes sidecarsImported=$sidecarsImported " +
                    "unlinkedAbort=$stoppedForUnlinkedFolder folder=$folderUriString"
            )

            if (!isStopped && !stoppedForUnlinkedFolder && !metadataOnly) {
                if (recentFilesRepository.hasFolderBooksNeedingTextMetadata(folderUriString)) {
                    enqueueMetadataExtraction(folderUriString)
                } else {
                    ReaderPerfLog.d("FolderSync metadata extraction skipped: no pending books folder=$folderUriString")
                }
            }

            return true

        } catch (e: Exception) {
            Timber.tag("FolderSync").e(e, "Error during folder sync worker execution.")
            return false
        }
    }

    private suspend fun importAnnotationSidecarsForBooks(
        folderUri: android.net.Uri,
        folderUriString: String,
        books: List<RecentFileItem>,
        phase: String
    ): Int {
        if (books.isEmpty()) {
            ReaderPerfLog.d("FolderSync phase annotation-sidecars skipped phase=$phase reason=no-books folder=$folderUriString")
            return 0
        }

        val preloadedSidecars = ReaderPerfLog.measureSuspend(
            name = "FolderSync phase annotation-sidecars",
            minLogMs = 25L,
            details = { "phase=$phase" }
        ) {
            LocalSyncUtils.preloadAnnotationSidecars(appContext, folderUri) { isStopped }
        }

        ReaderPerfLog.d(
            "FolderSync annotation-sidecars records=${preloadedSidecars.size} books=${books.size} phase=$phase folder=$folderUriString"
        )

        if (preloadedSidecars.isEmpty()) return 0

        var imported = 0
        Timber.tag("FolderAnnotationSync").d("Checking annotation sidecars phase=$phase for ${books.size} books...")
        for (book in books) {
            if (isStopped || !isFolderStillLinked(folderUriString)) break

            val sidecarData = preloadedSidecars[book.bookId] ?: continue
            val (remoteTs, jsonPayload) = sidecarData

            val safeSlashBookId = book.bookId.replace("/", "_")
            val safeRichTextBookId = book.bookId.replace("[^a-zA-Z0-9._-]".toRegex(), "_")
            val localFiles = listOf(
                File(appContext.filesDir, "annotations/annotation_$safeSlashBookId.json"),
                File(appContext.filesDir, "rich_doc_${safeRichTextBookId}.json"),
                File(appContext.filesDir, "page_layouts/layout_$safeSlashBookId.json"),
                File(appContext.filesDir, "textboxes/textboxes_$safeSlashBookId.json"),
                File(appContext.filesDir, "pdf_highlights/highlights_$safeSlashBookId.json")
            )
            val localTs = localFiles.maxOfOrNull { if (it.exists()) it.lastModified() else 0L } ?: 0L

            if (remoteTs > (localTs + 1000)) {
                Timber.tag("FolderAnnotationSync").i(">>> Newer sidecar found for ${book.displayName}. Importing.")
                recentFilesRepository.importAnnotationBundle(book.bookId, jsonPayload)
                imported++
            } else {
                Timber.tag("FolderAnnotationSync").v("Sidecar for ${book.displayName} is not newer. Skipping.")
            }
        }

        ReaderPerfLog.i(
            "FolderSync annotation-sidecars imported=$imported records=${preloadedSidecars.size} phase=$phase folder=$folderUriString"
        )
        return imported
    }

    private data class AndroidFolderScanResult(
        val files: List<SharedFolderScannedFile> = emptyList(),
        val dirsScanned: Int = 0,
        val filesSeen: Int = 0,
        val stoppedForUnlinkedFolder: Boolean = false
    )

    private data class FolderWalkStats(
        val dirsScanned: Int = 0,
        val filesSeen: Int = 0,
        val stoppedForUnlinkedFolder: Boolean = false
    )

    private suspend fun scanFolderFiles(
        folderUri: android.net.Uri,
        folderUriString: String,
        allowedFileTypes: Set<FileType>
    ): AndroidFolderScanResult {
        val files = mutableListOf<SharedFolderScannedFile>()
        val stats = walkFolderFiles(
            folderUri = folderUri,
            folderUriString = folderUriString,
            allowedFileTypes = allowedFileTypes,
            onProgress = { filesSeen -> reportProgress(PHASE_SCAN, filesSeen, newBooks = 0) }
        ) { file ->
            files += file
        }
        return AndroidFolderScanResult(
            files = files,
            dirsScanned = stats.dirsScanned,
            filesSeen = stats.filesSeen,
            stoppedForUnlinkedFolder = stats.stoppedForUnlinkedFolder
        )
    }

    /**
     * Walks the folder tree once and hands every eligible book file to [onFile]. Both the
     * full sync (which collects them into a list) and the discovery pass (which writes them
     * to the DB as it goes) share this walker.
     */
    private suspend fun walkFolderFiles(
        folderUri: android.net.Uri,
        folderUriString: String,
        allowedFileTypes: Set<FileType>,
        onProgress: suspend (filesSeen: Int) -> Unit = {},
        onFile: suspend (SharedFolderScannedFile) -> Unit
    ): FolderWalkStats {
        Timber.tag("FolderSync").d("Phase 2: Scanning physical files using raw ContentResolver...")
        val contentResolver = appContext.contentResolver
        val rootDocId = DocumentsContract.getTreeDocumentId(folderUri)
        val dirQueue = ArrayDeque<String>()
        var dirsScanned = 0
        var filesSeen = 0
        var lastProgressAt = 0
        var stoppedForUnlinkedFolder = false
        dirQueue.add(rootDocId)

        val projection = arrayOf(
            DocumentsContract.Document.COLUMN_DOCUMENT_ID,
            DocumentsContract.Document.COLUMN_DISPLAY_NAME,
            DocumentsContract.Document.COLUMN_MIME_TYPE,
            DocumentsContract.Document.COLUMN_SIZE,
            DocumentsContract.Document.COLUMN_LAST_MODIFIED
        )

        while (dirQueue.isNotEmpty()) {
            if (isStopped) break
            if (!isFolderStillLinked(folderUriString)) {
                ReaderPerfLog.w("FolderSync folder abort: folder unlinked during scan folder=$folderUriString")
                stoppedForUnlinkedFolder = true
                break
            }
            val currentDocId = dirQueue.removeFirst()
            dirsScanned++
            val childrenUri = DocumentsContract.buildChildDocumentsUriUsingTree(folderUri, currentDocId)

            try {
                contentResolver.query(childrenUri, projection, null, null, null)?.use { cursor ->
                    val idCol = cursor.getColumnIndexOrThrow(DocumentsContract.Document.COLUMN_DOCUMENT_ID)
                    val nameCol = cursor.getColumnIndexOrThrow(DocumentsContract.Document.COLUMN_DISPLAY_NAME)
                    val mimeCol = cursor.getColumnIndexOrThrow(DocumentsContract.Document.COLUMN_MIME_TYPE)
                    val sizeCol = cursor.getColumnIndexOrThrow(DocumentsContract.Document.COLUMN_SIZE)
                    val modCol = cursor.getColumnIndexOrThrow(DocumentsContract.Document.COLUMN_LAST_MODIFIED)

                    while (cursor.moveToNext() && !isStopped && !stoppedForUnlinkedFolder) {
                        val docId = cursor.getString(idCol)
                        val name = cursor.getString(nameCol) ?: ""
                        val mimeType = cursor.getString(mimeCol)
                        filesSeen++

                        if (filesSeen % 100 == 0 && !isFolderStillLinked(folderUriString)) {
                            ReaderPerfLog.w("FolderSync folder abort: folder unlinked after entries=$filesSeen folder=$folderUriString")
                            stoppedForUnlinkedFolder = true
                            break
                        }

                        if (filesSeen - lastProgressAt >= PROGRESS_REPORT_INTERVAL) {
                            lastProgressAt = filesSeen
                            onProgress(filesSeen)
                        }

                        if (mimeType == DocumentsContract.Document.MIME_TYPE_DIR) {
                            if (!name.startsWith(".") && name != LOCAL_FOLDER_SYNC_DATA_DIR) {
                                dirQueue.add(docId)
                            }
                            continue
                        }

                        val type = getFileType(name, mimeType)
                        if (
                            type == null ||
                            type !in allowedFileTypes ||
                            !isLocalFolderSyncEligibleFile(name, mimeType) ||
                            name.endsWith(".json") ||
                            name.startsWith(".")
                        ) {
                            continue
                        }

                        val docUri = DocumentsContract.buildDocumentUriUsingTree(folderUri, docId)
                        val relativePath = buildRelativePath(rootDocId, docId, name)
                        onFile(
                            SharedFolderScannedFile(
                                name = name,
                                path = docUri.toString(),
                                sourceFolder = folderUriString,
                                relativePath = relativePath,
                                type = type,
                                size = if (!cursor.isNull(sizeCol)) cursor.getLong(sizeCol) else 0L,
                                lastModified = if (!cursor.isNull(modCol)) cursor.getLong(modCol) else 0L
                            )
                        )
                    }
                }
            } catch (e: Exception) {
                Timber.tag("FolderSync").e(e, "Failed to query children for docId: $currentDocId")
            }

            if (stoppedForUnlinkedFolder) break
        }

        if (filesSeen > lastProgressAt) onProgress(filesSeen)

        return FolderWalkStats(
            dirsScanned = dirsScanned,
            filesSeen = filesSeen,
            stoppedForUnlinkedFolder = stoppedForUnlinkedFolder
        )
    }

    private fun RecentFileItem.toFolderSyncSharedBookItem(): SharedBookItem {
        return SharedBookItem(
            id = bookId,
            path = uriString,
            type = type,
            displayName = displayName,
            timestamp = lastModifiedTimestamp,
            coverImagePath = coverImagePath,
            title = title,
            author = author,
            description = description,
            originalTitle = originalTitle,
            originalAuthor = originalAuthor,
            originalSeriesName = originalSeriesName,
            originalSeriesIndex = originalSeriesIndex,
            originalDescription = originalDescription,
            progressPercentage = progressPercentage,
            isRecent = isRecent,
            fileSize = fileSize,
            fileContentModifiedTimestamp = fileContentModifiedTimestamp,
            sourceFolder = sourceFolderUri,
            folderTextMetadataParsed = folderTextMetadataParsed,
            seriesName = seriesName,
            seriesIndex = seriesIndex,
            lastPageIndex = lastPage,
            readerPosition = readerPositionOrNull(),
            readerBookmarks = parseReaderBookmarks(),
            readerHighlights = EpubAnnotationSerializer.parseHighlightsJson(highlightsJson),
            readingPositionModifiedTimestamp = readingPositionModifiedTimestamp
        )
    }

    private fun SharedBookItem.toFolderSyncRecentFileItem(
        existing: RecentFileItem?,
        appliedMetadata: FolderBookMetadata?,
        scannedFile: SharedFolderScannedFile?,
        nowMillis: Long
    ): RecentFileItem {
        val contentChanged = existing != null && folderFileContentChanged(existing, this)
        val localModifiedTimestamp = when {
            appliedMetadata != null -> appliedMetadata.lastModifiedTimestamp
            contentChanged && fileContentModifiedTimestamp > 0L -> fileContentModifiedTimestamp
            timestamp > 0L -> timestamp
            else -> nowMillis
        }
        val legacyPosition = readerPosition
        val mappedBookmarksJson = readerBookmarks.toAndroidBookmarksJson(id)
        val mappedHighlightsJson = readerHighlights
            .takeIf { it.isNotEmpty() }
            ?.let(EpubAnnotationSerializer::highlightsToJson)
        val bookmarksJson = if (appliedMetadata != null || existing == null) {
            mappedBookmarksJson ?: appliedMetadata?.bookmarksJson ?: existing?.bookmarksJson
        } else {
            existing.bookmarksJson
        }
        val highlightsJson = if (appliedMetadata != null || existing == null) {
            mappedHighlightsJson ?: appliedMetadata?.highlightsJson ?: existing?.highlightsJson
        } else {
            existing.highlightsJson
        }

        return RecentFileItem(
            bookId = id,
            uriString = path,
            type = type,
            displayName = scannedFile?.name ?: existing?.displayName ?: displayName,
            timestamp = when {
                existing == null -> timestamp.takeIf { it > 0L } ?: localModifiedTimestamp
                appliedMetadata?.isRecent == true -> appliedMetadata.lastModifiedTimestamp
                else -> existing.timestamp
            },
            coverImagePath = coverImagePath,
            title = title,
            author = author,
            lastChapterIndex = legacyPosition?.chapterIndex ?: appliedMetadata?.lastChapterIndex ?: existing?.lastChapterIndex,
            lastPage = legacyPosition?.pageIndex ?: lastPageIndex ?: appliedMetadata?.lastPage ?: existing?.lastPage,
            lastPositionCfi = legacyPosition?.cfi ?: appliedMetadata?.lastPositionCfi ?: existing?.lastPositionCfi,
            locatorBlockIndex = legacyPosition?.blockIndex ?: appliedMetadata?.locatorBlockIndex ?: existing?.locatorBlockIndex,
            locatorCharOffset = legacyPosition?.charOffset ?: appliedMetadata?.locatorCharOffset ?: existing?.locatorCharOffset,
            progressPercentage = progressPercentage,
            isRecent = isRecent,
            isAvailable = true,
            lastModifiedTimestamp = localModifiedTimestamp,
            isDeleted = false,
            bookmarksJson = bookmarksJson,
            sourceFolderUri = sourceFolder,
            isReflowPreferred = existing?.isReflowPreferred ?: false,
            customName = appliedMetadata?.customName ?: existing?.customName,
            highlightsJson = highlightsJson,
            fileSize = fileSize,
            fileContentModifiedTimestamp = fileContentModifiedTimestamp,
            seriesName = seriesName,
            seriesIndex = seriesIndex,
            description = description,
            originalTitle = originalTitle,
            originalAuthor = originalAuthor,
            originalSeriesName = originalSeriesName,
            originalSeriesIndex = originalSeriesIndex,
            originalDescription = originalDescription,
            folderTextMetadataParsed = folderTextMetadataParsed,
            folderCoverMetadataParsed = if (contentChanged) false else existing?.folderCoverMetadataParsed ?: false,
            readingPositionModifiedTimestamp = readingPositionModifiedTimestamp,
            tags = existing?.tags.orEmpty()
        )
    }

    private fun appliedMetadataFor(
        book: SharedBookItem,
        existing: RecentFileItem?,
        metadata: FolderBookMetadata?
    ): FolderBookMetadata? {
        if (metadata == null) return null
        val existingModified = existing?.lastModifiedTimestamp ?: Long.MIN_VALUE
        return metadata.takeIf { existing == null || it.lastModifiedTimestamp > existingModified }
    }

    private fun RecentFileItem.readerPositionOrNull(): ReaderLocator? {
        if (lastChapterIndex == null && lastPage == null && lastPositionCfi.isNullOrBlank()) return null
        return ReaderLocator.fromLegacy(
            chapterIndex = lastChapterIndex,
            cfi = lastPositionCfi,
            pageIndex = lastPage
        )
    }

    private fun RecentFileItem.parseReaderBookmarks(): List<ReaderBookmark> {
        return EpubAnnotationSerializer.parseBookmarksJson(bookmarksJson)
            .mapIndexed { index, bookmark ->
                val locator = bookmark.locator.withFallbacks(
                    chapterIndex = bookmark.chapterIndex,
                    cfi = bookmark.cfi,
                    pageIndex = bookmark.pageInChapter?.minus(1),
                    textQuote = bookmark.snippet
                )
                val pageIndex = locator.pageIndex ?: bookmark.pageInChapter?.minus(1) ?: 0
                ReaderBookmark(
                    id = "bookmark_${bookId}_$index",
                    pageIndex = pageIndex.coerceAtLeast(0),
                    chapterTitle = bookmark.chapterTitle,
                    preview = bookmark.snippet,
                    locator = locator
                )
            }
    }

    private fun List<ReaderBookmark>.toAndroidBookmarksJson(bookId: String): String? {
        val bookmarks = mapIndexed { index, bookmark ->
            val locator = bookmark.locator
            val chapterIndex = locator.chapterIndex ?: 0
            val cfi = locator.cfi ?: "android:$bookId:$index:${bookmark.pageIndex}"
            EpubBookmark(
                cfi = cfi,
                chapterTitle = bookmark.chapterTitle,
                label = null,
                snippet = bookmark.preview,
                pageInChapter = bookmark.pageIndex + 1,
                totalPagesInChapter = null,
                chapterIndex = chapterIndex,
                locator = locator.withFallbacks(
                    chapterIndex = chapterIndex,
                    cfi = cfi,
                    pageIndex = bookmark.pageIndex,
                    textQuote = bookmark.preview
                )
            )
        }
        return bookmarks.takeIf { it.isNotEmpty() }?.let(EpubAnnotationSerializer::bookmarksToJson)
    }

    private fun folderFileContentChanged(previous: RecentFileItem, next: RecentFileItem): Boolean {
        val sizeChanged = previous.fileSize > 0L && next.fileSize > 0L && previous.fileSize != next.fileSize
        val modifiedChanged = next.fileContentModifiedTimestamp > 0L &&
            previous.fileContentModifiedTimestamp != next.fileContentModifiedTimestamp
        return sizeChanged || modifiedChanged
    }

    private fun folderFileContentChanged(previous: RecentFileItem, next: SharedBookItem): Boolean {
        val sizeChanged = previous.fileSize > 0L && next.fileSize > 0L && previous.fileSize != next.fileSize
        val modifiedChanged = next.fileContentModifiedTimestamp > 0L &&
            previous.fileContentModifiedTimestamp != next.fileContentModifiedTimestamp
        return sizeChanged || modifiedChanged
    }

    private fun isFolderStillLinked(folderUriString: String): Boolean {
        val prefs = appContext.getSharedPreferences("reader_user_prefs", Context.MODE_PRIVATE)
        return SyncedFolderPrefs.isLocalSyncEnabled(
            jsonString = prefs.getString(SyncedFolderPrefs.KEY_SYNCED_FOLDERS_JSON, null),
            legacyUri = prefs.getString(SyncedFolderPrefs.KEY_LEGACY_SYNCED_FOLDER_URI, null),
            folderUriString = folderUriString,
            syncableTypes = ANDROID_SYNCABLE_FILE_TYPES
        )
    }

    private fun getFileType(name: String, mimeType: String?): FileType? {
        return resolveFileTypeFromMetadata(name, mimeType)
    }

    private fun buildRelativePath(rootDocId: String, docId: String, fallbackName: String): String {
        val rootPath = rootDocId.substringAfter(':', "")
        val docPath = docId.substringAfter(':', "")
        if (docPath.isBlank()) return fallbackName
        val relative = if (rootPath.isNotBlank() && docPath.startsWith(rootPath)) {
            docPath.removePrefix(rootPath).trimStart('/')
        } else {
            docPath.substringAfterLast('/', fallbackName)
        }
        return relative.ifBlank { fallbackName }
    }

    private suspend fun migrateFolderBookId(
        folderUriString: String,
        oldId: String,
        newId: String,
        folderMetadataMap: MutableMap<String, FolderBookMetadata>,
        preloadedSidecars: MutableMap<String, Pair<Long, String>>,
        existingItemsMap: MutableMap<String, RecentFileItem>
    ) {
        if (oldId == newId) return

        recentFilesRepository.migrateBookIdLocally(oldId, newId)

        val oldMetadata = folderMetadataMap.remove(oldId)
        if (oldMetadata != null && newId !in folderMetadataMap) {
            val migratedMetadata = oldMetadata.copy(bookId = newId)
            LocalSyncUtils.saveMetadataToFolder(appContext, folderUriString.toUri(), migratedMetadata)
            folderMetadataMap[newId] = migratedMetadata
        }

        val oldSidecar = preloadedSidecars.remove(oldId)
        if (oldSidecar != null && newId !in preloadedSidecars) {
            LocalSyncUtils.saveAnnotationSidecar(
                context = appContext,
                sourceFolderUri = folderUriString.toUri(),
                bookId = newId,
                jsonPayload = oldSidecar.second,
                timestamp = oldSidecar.first
            )
            preloadedSidecars[newId] = oldSidecar
        }

        LocalSyncUtils.deleteBookSidecars(appContext, folderUriString.toUri(), oldId)

        existingItemsMap.remove(oldId)
        recentFilesRepository.getFileByBookId(newId)?.let {
            existingItemsMap[newId] = it
        }
    }
}
