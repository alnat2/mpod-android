package com.example.mpod.share

import com.example.mpod.data.local.dao.PendingShareImportDao
import com.example.mpod.data.local.entity.PendingShareImportEntity
import com.example.mpod.data.local.entity.ShareImportStatus
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map

internal class FakePendingShareImportDao : PendingShareImportDao {
    private val rows = linkedMapOf<String, PendingShareImportEntity>()
    private val state = MutableStateFlow<List<PendingShareImportEntity>>(emptyList())
    var insertCount = 0
        private set

    override suspend fun insertIfMissing(event: PendingShareImportEntity): Long {
        if (event.token in rows) return -1
        rows[event.token] = event
        insertCount++
        publish()
        return 1
    }

    override suspend fun getByToken(token: String): PendingShareImportEntity? = rows[token]

    override fun observeByToken(token: String): Flow<PendingShareImportEntity?> = state.map { rows ->
        rows.firstOrNull { it.token == token }
    }

    override suspend fun getByStatuses(statuses: List<ShareImportStatus>): List<PendingShareImportEntity> =
        rows.values.filter { it.status in statuses }.sortedBy { it.createdAt }

    override fun observeByStatuses(statuses: List<ShareImportStatus>): Flow<List<PendingShareImportEntity>> =
        state.map { rows -> rows.filter { it.status in statuses }.sortedBy { it.createdAt } }

    override suspend fun getByBatchId(batchId: String): List<PendingShareImportEntity> =
        rows.values.filter { it.batchId == batchId }.sortedBy { it.createdAt }

    override fun observeRecent(limit: Int): Flow<List<PendingShareImportEntity>> =
        state.map { rows -> rows.sortedByDescending { it.createdAt }.take(limit) }

    override suspend fun acknowledgeBatch(batchId: String, updatedAt: Long) {
        rows.replaceAll { _, event ->
            if (event.batchId == batchId) event.copy(completionAcknowledged = true, updatedAt = updatedAt) else event
        }
        publish()
    }

    override suspend fun getBatchByStatuses(
        batchId: String,
        statuses: List<ShareImportStatus>
    ): List<PendingShareImportEntity> = rows.values.filter { it.batchId == batchId && it.status in statuses }

    override suspend fun findRecentByFingerprint(
        fingerprint: String,
        statuses: List<ShareImportStatus>
    ): PendingShareImportEntity? = rows.values
        .filter { it.fingerprint == fingerprint && it.status in statuses }
        .maxByOrNull { it.createdAt }

    override suspend fun findLatestByFingerprint(fingerprint: String): PendingShareImportEntity? = rows.values
        .filter { it.fingerprint == fingerprint }
        .maxByOrNull { it.createdAt }

    override suspend fun countPendingBySourceUri(sourceUri: String): Int = rows.values.count {
        it.sourceUri == sourceUri && it.status in ACTIVE_STATUSES
    }

    override suspend fun countPendingBySourceUriExcludingToken(sourceUri: String, excludedToken: String): Int = rows.values.count {
        it.sourceUri == sourceUri && it.token != excludedToken && it.status in ACTIVE_STATUSES
    }

    override suspend fun findMostRecentByStatuses(statuses: List<ShareImportStatus>): PendingShareImportEntity? = rows.values
        .filter { it.status in statuses }
        .maxByOrNull { it.createdAt }

    override suspend fun update(event: PendingShareImportEntity) {
        rows[event.token] = event
        publish()
    }

    override suspend fun updateStatus(
        token: String,
        status: ShareImportStatus,
        error: String?,
        attempts: Int,
        updatedAt: Long
    ): Int {
        val current = rows[token] ?: return 0
        if (current.status !in ACTIVE_STATUSES) return 0
        rows[token] = current.copy(status = status, error = error, attempts = attempts, updatedAt = updatedAt)
        publish()
        return 1
    }

    override suspend fun updateCopyProgress(
        token: String,
        status: ShareImportStatus,
        copyPath: String?,
        progressBytes: Long,
        totalBytes: Long?,
        error: String?,
        updatedAt: Long
    ): Int {
        val current = rows[token] ?: return 0
        if (current.status !in ACTIVE_STATUSES) return 0
        rows[token] = current.copy(
            status = status,
            copyPath = copyPath,
            progressBytes = progressBytes,
            totalBytes = totalBytes,
            error = error,
            updatedAt = updatedAt
        )
        publish()
        return 1
    }

    override suspend fun updateTitleIfActive(token: String, title: String, updatedAt: Long): Int {
        val current = rows[token] ?: return 0
        if (current.status !in ACTIVE_STATUSES) return 0
        rows[token] = current.copy(title = title, updatedAt = updatedAt)
        publish()
        return 1
    }

    override suspend fun insertOrReturn(event: PendingShareImportEntity): PendingShareImportEntity {
        rows[event.token]?.let { return it }
        rows[event.token] = event
        insertCount++
        publish()
        return event
    }

    private fun publish() {
        state.value = rows.values.toList()
    }

    private companion object {
        val ACTIVE_STATUSES = setOf(
            ShareImportStatus.ENQUEUED,
            ShareImportStatus.COPYING,
            ShareImportStatus.IMPORTING
        )
    }
}
