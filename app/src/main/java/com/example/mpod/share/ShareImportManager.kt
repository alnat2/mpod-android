package com.example.mpod.share

import android.content.ContentResolver
import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.example.mpod.data.local.dao.PendingShareImportDao
import com.example.mpod.data.local.entity.PendingShareImportEntity
import com.example.mpod.data.local.entity.ShareImportStatus
import com.example.mpod.data.repository.SingleTrackRepository
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.supervisorScope
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.security.MessageDigest
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import javax.inject.Provider
import javax.inject.Singleton

@Singleton
class ShareImportManager @Inject constructor(
    @ApplicationContext private val context: Context,
    private val dao: PendingShareImportDao,
    private val singleTrackRepository: SingleTrackRepository,
    private val workManagerProvider: Provider<WorkManager>
) {
    companion object {
        const val UNIQUE_WORK_NAME = "share-import-queue"
        const val QUIET_WINDOW_MS = 400L
        const val REDELIVERY_GRACE_MS = 3_000L
        private val pendingStatuses = listOf(
            ShareImportStatus.ENQUEUED,
            ShareImportStatus.COPYING,
            ShareImportStatus.IMPORTING
        )
    }

    private val workManager by lazy { workManagerProvider.get() }
    private val processingMutex = Mutex()
    private val submissionMutex = Mutex()
    private val applicationScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val submissionJobs = ConcurrentHashMap<String, Job>()
    private val processingJobs = ConcurrentHashMap<String, Job>()
    private val _lastSubmittedToken = MutableStateFlow<String?>(null)
    val lastSubmittedToken = _lastSubmittedToken.asStateFlow()
    private val _activeSubmissionCount = MutableStateFlow(0)
    val activeSubmissionCount = _activeSubmissionCount.asStateFlow()
    private val _unsupportedShareMessage = MutableStateFlow<String?>(null)
    val unsupportedShareMessage = _unsupportedShareMessage.asStateFlow()
    val recentImports: Flow<List<PendingShareImportEntity>> = dao.observeRecent()

    fun submitFromActivity(
        payload: String,
        title: String?,
        sourceUri: String?,
        fingerprint: String,
        token: String = UUID.randomUUID().toString(),
        suppressExistingPending: Boolean = false
    ) {
        _lastSubmittedToken.value = token
        _activeSubmissionCount.update { it + 1 }
        _unsupportedShareMessage.value = null
        val job = applicationScope.launch(start = CoroutineStart.LAZY) {
            try {
                val event = submit(
                    payload = payload,
                    title = title,
                    sourceUri = sourceUri,
                    fingerprint = fingerprint,
                    token = token,
                    suppressExistingPending = suppressExistingPending
                )
                if (event.sourceUri != null && event.status in pendingStatuses &&
                    event.copyPath?.let(::File)?.isFile != true
                ) {
                    processingMutex.withLock {
                        val current = dao.getByToken(event.token) ?: return@withLock
                        if (current.status !in pendingStatuses || current.copyPath?.let(::File)?.isFile == true) {
                            return@withLock
                        }
                        try {
                            ensureOwnedCopy(current)
                        } catch (cancelled: CancellationException) {
                            throw cancelled
                        } catch (error: Throwable) {
                            val latest = dao.getByToken(event.token) ?: event
                            dao.updateStatus(
                                token = event.token,
                                status = ShareImportStatus.FAILED,
                                error = error.message ?: "Could not copy shared audio.",
                                attempts = latest.attempts + 1,
                                updatedAt = System.currentTimeMillis()
                            )
                            latest.sourceUri?.let { releasePersistableGrantWhenUnused(it) }
                        }
                    }
                }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Throwable) {
                _unsupportedShareMessage.value = error.message ?: "Could not queue shared audio."
                if (dao.getByToken(token) == null) {
                    sourceUri?.let { releasePersistableGrantWhenUnused(it) }
                }
            } finally {
                _activeSubmissionCount.update { (it - 1).coerceAtLeast(0) }
                if (_lastSubmittedToken.value == token) _lastSubmittedToken.value = null
                submissionJobs.remove(token)
            }
        }
        submissionJobs[token] = job
        job.start()
    }

    fun announceUnsupportedShare(message: String) {
        _unsupportedShareMessage.value = message
    }

    fun dismissUnsupportedShare() {
        _unsupportedShareMessage.value = null
    }

    fun resumePendingImports() {
        enqueueWorker()
    }

    suspend fun cancelImport(token: String) {
        val event = dao.getByToken(token) ?: return
        if (event.status !in setOf(ShareImportStatus.ENQUEUED, ShareImportStatus.COPYING)) return
        val cancelled = dao.updateStatus(
            token = token,
            status = ShareImportStatus.FAILED,
            error = "Import cancelled.",
            attempts = event.attempts,
            updatedAt = System.currentTimeMillis()
        )
        if (cancelled == 0) return
        submissionJobs[token]?.cancel()
        submissionJobs[token]?.join()
        processingJobs[token]?.cancel()
        processingJobs[token]?.join()
        event.sourceUri?.let { releasePersistableGrantWhenUnused(it) }
    }

    /** Persist first, then schedule durable work. The returned token is stable across recovery. */
    suspend fun submit(
        payload: String,
        title: String?,
        sourceUri: String? = null,
        fingerprint: String = fingerprint(payload),
        token: String = UUID.randomUUID().toString(),
        now: Long = System.currentTimeMillis(),
        suppressExistingPending: Boolean = false
    ): PendingShareImportEntity = withContext(Dispatchers.IO) {
        val event = submissionMutex.withLock {
            val previous = dao.findLatestByFingerprint(fingerprint)
            val existingPending = previous != null && previous.status in pendingStatuses
            if (previous != null && shouldSuppressShareRedelivery(
                    now = now,
                    previousCreatedAt = previous.createdAt,
                    isPending = existingPending,
                    suppressExistingPending = suppressExistingPending,
                    graceWindowMs = REDELIVERY_GRACE_MS
                )
            ) {
                previous
            } else {
                val last = dao.findMostRecentByStatuses(ShareImportStatus.entries.toList())
                val batchId = if (last != null && !last.completionAcknowledged &&
                    now - last.createdAt in 0..QUIET_WINDOW_MS
                ) {
                    last.batchId
                } else {
                    UUID.randomUUID().toString()
                }
                dao.insertOrReturn(
                    PendingShareImportEntity(
                        token = token,
                        payload = payload,
                        title = title,
                        sourceUri = sourceUri,
                        batchId = batchId,
                        fingerprint = fingerprint,
                        createdAt = now,
                        updatedAt = now
                    )
                )
            }
        }
        enqueueWorker()
        event
    }

    suspend fun retryFailed(batchId: String): Int = withContext(Dispatchers.IO) {
        val failed = dao.getBatchByStatuses(batchId, listOf(ShareImportStatus.FAILED))
        if (failed.isEmpty()) return@withContext 0
        val retryBatchId = UUID.randomUUID().toString()
        val now = System.currentTimeMillis()
        failed.forEach { event ->
            dao.update(
                event.copy(
                    status = ShareImportStatus.ENQUEUED,
                    error = null,
                    batchId = retryBatchId,
                    createdAt = now,
                    progressBytes = 0,
                    completionAcknowledged = false,
                    updatedAt = now
                )
            )
        }
        enqueueWorker()
        failed.size
    }

    suspend fun acknowledgeBatch(batchId: String) {
        dao.acknowledgeBatch(batchId, System.currentTimeMillis())
    }

    suspend fun processPending() {
        delay(QUIET_WINDOW_MS)
        processingMutex.withLock {
            val events = dao.getByStatuses(pendingStatuses)
            supervisorScope {
                for (event in events) {
                    launch { process(event) }.join()
                }
            }
        }
    }

    suspend fun hasPendingContentCopy(): Boolean = dao.getByStatuses(pendingStatuses).any { it.sourceUri != null }

    suspend fun copyProgressText(): String? = dao.getByStatuses(pendingStatuses)
        .firstOrNull { it.status == ShareImportStatus.COPYING }
        ?.let { event ->
            val total = event.totalBytes
            if (total != null && total > 0) {
                "Copying shared audio: ${(event.progressBytes * 100 / total).coerceIn(0, 100)}%"
            } else {
                "Copying shared audio"
            }
        }

    suspend fun cancelableContentToken(): String? = dao.getByStatuses(
        listOf(ShareImportStatus.ENQUEUED, ShareImportStatus.COPYING)
    ).firstOrNull { it.sourceUri != null }?.token

    private suspend fun process(initial: PendingShareImportEntity) {
        var event = dao.getByToken(initial.token) ?: return
        if (event.status in listOf(ShareImportStatus.DONE, ShareImportStatus.FAILED)) return
        val processingJob = currentCoroutineContext()[Job] ?: return
        processingJobs[event.token] = processingJob
        try {
            val ownedCopy = if (event.sourceUri != null) {
                val finalFile = event.copyPath?.let(::File)?.takeIf { it.isFile }
                    ?: ensureOwnedCopy(event)
                event = dao.getByToken(event.token) ?: return
                if (event.status !in pendingStatuses) return
                finalFile
            } else {
                null
            }
            event = dao.getByToken(event.token) ?: return
            if (event.status !in pendingStatuses) return

            dao.updateStatus(
                token = event.token,
                status = ShareImportStatus.IMPORTING,
                error = null,
                attempts = event.attempts,
                updatedAt = System.currentTimeMillis()
            )
            if (dao.getByToken(event.token)?.status !in pendingStatuses) return
            if (ownedCopy != null && event.sourceUri != null) {
                singleTrackRepository.registerCopiedAudio(
                    sourceUri = event.sourceUri,
                    title = event.title,
                    copyPath = ownedCopy.absolutePath
                )
            } else {
                singleTrackRepository.playSingleTrack(event.payload, event.title)
            }
            if (dao.getByToken(event.token)?.status != ShareImportStatus.IMPORTING) return
            dao.updateStatus(
                token = event.token,
                status = ShareImportStatus.DONE,
                error = null,
                attempts = event.attempts,
                updatedAt = System.currentTimeMillis()
            )
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (error: Throwable) {
            val latest = dao.getByToken(event.token) ?: event
            if (latest.status !in pendingStatuses) return
            dao.updateStatus(
                token = event.token,
                status = ShareImportStatus.FAILED,
                error = error.message ?: "Could not import shared audio.",
                attempts = latest.attempts + 1,
                updatedAt = System.currentTimeMillis()
            )
            latest.sourceUri?.let { releasePersistableGrantWhenUnused(it) }
        } finally {
            processingJobs.remove(event.token, processingJob)
        }
    }

    private suspend fun copyContentUri(event: PendingShareImportEntity): File = withContext(Dispatchers.IO) {
        val source = event.sourceUri ?: error("Missing source URI for shared audio.")
        val sourceUri = Uri.parse(source)
        val totalBytes = querySize(context.contentResolver, sourceUri)
        val displayName = queryDisplayName(context.contentResolver, sourceUri)
        val sharedTitle = event.title ?: displayName
            ?.substringBeforeLast('.', missingDelimiterValue = "")
            ?.takeIf { it.isNotBlank() }
        val extension = displayName?.substringAfterLast('.', "")
            ?.takeIf { it.matches(Regex("[A-Za-z0-9]{1,8}")) }
            ?.lowercase()
            ?: "audio"
        if (sharedTitle != null && event.title.isNullOrBlank()) {
            dao.updateTitleIfActive(event.token, sharedTitle, System.currentTimeMillis())
        }
        val directory = File(context.filesDir, "share-imports").apply { mkdirs() }
        val target = File(directory, "${event.token}.$extension")
        val temporary = File(directory, "${event.token}.$extension.part")
        val started = dao.updateCopyProgress(
            token = event.token,
            status = ShareImportStatus.COPYING,
            copyPath = target.absolutePath,
            progressBytes = 0,
            totalBytes = totalBytes,
            error = null,
            updatedAt = System.currentTimeMillis()
        )
        if (started == 0) throw CancellationException("Share import is no longer active.")
        try {
            val input = context.contentResolver.openInputStream(sourceUri)
                ?: error("The shared audio provider could not open this file.")
            input.use { stream ->
                FileOutputStream(temporary).use { output ->
                    val buffer = ByteArray(64 * 1024)
                    var copied = 0L
                    var reportedProgress = 0L
                    while (true) {
                        currentCoroutineContext().ensureActive()
                        val count = stream.read(buffer)
                        if (count < 0) break
                        output.write(buffer, 0, count)
                        copied += count
                        if (copied - reportedProgress >= 512 * 1024) {
                            dao.updateCopyProgress(
                                token = event.token,
                                status = ShareImportStatus.COPYING,
                                copyPath = target.absolutePath,
                                progressBytes = copied,
                                totalBytes = totalBytes,
                                error = null,
                                updatedAt = System.currentTimeMillis()
                            )
                            reportedProgress = copied
                        }
                    }
                    output.fd.sync()
                }
            }
            if (target.exists() && !target.delete()) error("Could not replace the previous audio copy.")
            if (!temporary.renameTo(target)) error("Could not finalize the shared audio copy.")
            val finalized = dao.updateCopyProgress(
                token = event.token,
                status = ShareImportStatus.IMPORTING,
                copyPath = target.absolutePath,
                progressBytes = target.length(),
                totalBytes = target.length(),
                error = null,
                updatedAt = System.currentTimeMillis()
            )
            if (finalized == 0) {
                target.delete()
                throw CancellationException("Share import was cancelled during file copy.")
            }
            releasePersistableGrantWhenUnused(source, event.token)
            target
        } catch (cancelled: CancellationException) {
            temporary.delete()
            if (dao.getByToken(event.token)?.status == ShareImportStatus.FAILED) target.delete()
            throw cancelled
        } catch (error: Throwable) {
            temporary.delete()
            throw error
        }
    }

    private suspend fun ensureOwnedCopy(event: PendingShareImportEntity): File {
        val sourceUri = event.sourceUri ?: error("Missing source URI for shared audio.")
        val existingPath = singleTrackRepository.findOwnedShareCopy(sourceUri)
        val existingFile = existingPath?.let(::File)?.takeIf { it.isFile }
        if (existingFile != null) {
            dao.updateCopyProgress(
                token = event.token,
                status = ShareImportStatus.IMPORTING,
                copyPath = existingFile.absolutePath,
                progressBytes = existingFile.length(),
                totalBytes = existingFile.length(),
                error = null,
                updatedAt = System.currentTimeMillis()
            )
            releasePersistableGrantWhenUnused(sourceUri, event.token)
            return existingFile
        }
        return event.copyPath?.let(::File)?.takeIf { it.isFile } ?: copyContentUri(event)
    }

    private fun querySize(resolver: ContentResolver, uri: Uri): Long? = runCatching {
        resolver.query(uri, arrayOf(OpenableColumns.SIZE), null, null, null)?.use { cursor ->
            if (cursor.moveToFirst() && !cursor.isNull(0)) cursor.getLong(0) else null
        }
    }.getOrNull()

    private fun queryDisplayName(resolver: ContentResolver, uri: Uri): String? = runCatching {
        resolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { cursor ->
            if (cursor.moveToFirst() && !cursor.isNull(0)) cursor.getString(0) else null
        }
    }.getOrNull()

    private suspend fun releasePersistableGrantWhenUnused(sourceUri: String, currentToken: String? = null) {
        val pendingCount = if (currentToken == null) {
            dao.countPendingBySourceUri(sourceUri)
        } else {
            dao.countPendingBySourceUriExcludingToken(sourceUri, currentToken)
        }
        if (pendingCount > 0) return
        val uri = Uri.parse(sourceUri)
        try {
            context.contentResolver.releasePersistableUriPermission(
                uri,
                android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION
            )
        } catch (_: SecurityException) {
            // The provider may have granted only temporary access.
        }
    }

    private fun enqueueWorker() {
        val request = OneTimeWorkRequestBuilder<ShareImportWorker>().build()
        workManager.enqueueUniqueWork(UNIQUE_WORK_NAME, ExistingWorkPolicy.APPEND_OR_REPLACE, request)
    }

    fun fingerprint(payload: String): String {
        val canonical = payload.trim()
        return MessageDigest.getInstance("SHA-256")
            .digest(canonical.toByteArray(Charsets.UTF_8))
            .joinToString("") { byte -> "%02x".format(byte) }
    }
}

internal fun shouldSuppressShareRedelivery(
    now: Long,
    previousCreatedAt: Long,
    isPending: Boolean,
    suppressExistingPending: Boolean,
    graceWindowMs: Long
): Boolean = now - previousCreatedAt in 0..graceWindowMs ||
    (suppressExistingPending && isPending)

internal fun selectVisibleShareBatchId(events: List<PendingShareImportEntity>): String? {
    val groups = events.groupBy { it.batchId }
    val activeOrFailed = groups.filterValues { batch ->
        batch.any {
            it.status in setOf(ShareImportStatus.ENQUEUED, ShareImportStatus.COPYING, ShareImportStatus.IMPORTING) ||
                it.status == ShareImportStatus.FAILED
        }
    }
    val candidates = activeOrFailed.ifEmpty {
        groups.filterValues { batch ->
            batch.any { it.status == ShareImportStatus.DONE && !it.completionAcknowledged }
        }
    }
    return candidates.maxByOrNull { (_, batch) -> batch.maxOf { it.createdAt } }?.key
}

@HiltWorker
class ShareImportWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted params: WorkerParameters,
    private val manager: ShareImportManager
) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result = try {
        if (manager.hasPendingContentCopy()) {
            trySetForeground(createForegroundInfo("Copying shared audio"))
        }
        val processing = CoroutineScope(currentCoroutineContext()).async {
            manager.processPending()
        }
        while (!processing.isCompleted) {
            delay(500L)
            if (manager.hasPendingContentCopy()) {
                trySetForeground(createForegroundInfo(manager.copyProgressText() ?: "Copying shared audio"))
            }
        }
        processing.await()
        Result.success()
    } catch (cancelled: CancellationException) {
        throw cancelled
    } catch (_: Throwable) {
        Result.retry()
    }

    private suspend fun createForegroundInfo(message: String): androidx.work.ForegroundInfo {
        val channelId = "share_import"
        val notificationManager = applicationContext.getSystemService(Context.NOTIFICATION_SERVICE) as android.app.NotificationManager
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
            notificationManager.createNotificationChannel(
                android.app.NotificationChannel(
                    channelId,
                    "Shared audio import",
                    android.app.NotificationManager.IMPORTANCE_LOW
                )
            )
        }
        val token = manager.cancelableContentToken()
        val cancelIntent = token?.let {
            android.content.Intent(applicationContext, com.example.mpod.MainActivity::class.java)
                .setAction(com.example.mpod.MainActivity.ACTION_CANCEL_SHARE_IMPORT)
                .putExtra(com.example.mpod.MainActivity.EXTRA_SHARE_IMPORT_TOKEN, it)
        }
        val cancelPendingIntent = cancelIntent?.let {
            android.app.PendingIntent.getActivity(
                applicationContext,
                token.hashCode(),
                it,
                android.app.PendingIntent.FLAG_UPDATE_CURRENT or android.app.PendingIntent.FLAG_IMMUTABLE
            )
        }
        val builder = androidx.core.app.NotificationCompat.Builder(applicationContext, channelId)
            .setSmallIcon(android.R.drawable.stat_sys_download)
            .setContentTitle("Importing shared audio")
            .setContentText(message)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
        if (cancelPendingIntent != null) builder.addAction(0, "Cancel", cancelPendingIntent)
        val notification = builder.build()
        val serviceType = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.Q) {
            android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC
        } else {
            0
        }
        return androidx.work.ForegroundInfo(IMPORT_NOTIFICATION_ID, notification, serviceType)
    }

    private suspend fun trySetForeground(info: androidx.work.ForegroundInfo) {
        try {
            setForeground(info)
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Exception) {
            // WorkManager may be unable to promote a worker when Android disallows an FGS start.
            // Keep the durable background copy running; the in-app banner remains observable.
        }
    }

    private companion object {
        const val IMPORT_NOTIFICATION_ID = 4815
    }
}
