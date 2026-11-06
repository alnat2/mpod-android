package com.example.mpod.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import com.example.mpod.data.local.entity.PendingShareImportEntity
import com.example.mpod.data.local.entity.ShareImportStatus
import kotlinx.coroutines.flow.Flow

@Dao
interface PendingShareImportDao {
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertIfMissing(event: PendingShareImportEntity): Long

    @Query("SELECT * FROM pending_share_imports WHERE token = :token LIMIT 1")
    suspend fun getByToken(token: String): PendingShareImportEntity?

    @Query("SELECT * FROM pending_share_imports WHERE token = :token LIMIT 1")
    fun observeByToken(token: String): Flow<PendingShareImportEntity?>

    @Query("SELECT * FROM pending_share_imports WHERE status IN (:statuses) ORDER BY createdAt ASC")
    suspend fun getByStatuses(statuses: List<ShareImportStatus>): List<PendingShareImportEntity>

    @Query("SELECT * FROM pending_share_imports WHERE status IN (:statuses) ORDER BY createdAt ASC")
    fun observeByStatuses(statuses: List<ShareImportStatus>): Flow<List<PendingShareImportEntity>>

    @Query("SELECT * FROM pending_share_imports WHERE batchId = :batchId ORDER BY createdAt ASC")
    suspend fun getByBatchId(batchId: String): List<PendingShareImportEntity>

    @Query("SELECT * FROM pending_share_imports ORDER BY createdAt DESC LIMIT :limit")
    fun observeRecent(limit: Int = 100): Flow<List<PendingShareImportEntity>>

    @Query("UPDATE pending_share_imports SET completionAcknowledged = 1, updatedAt = :updatedAt WHERE batchId = :batchId")
    suspend fun acknowledgeBatch(batchId: String, updatedAt: Long)

    @Query("""SELECT * FROM pending_share_imports
        WHERE batchId = :batchId AND status IN (:statuses) ORDER BY createdAt ASC""")
    suspend fun getBatchByStatuses(
        batchId: String,
        statuses: List<ShareImportStatus>
    ): List<PendingShareImportEntity>

    @Query("""SELECT * FROM pending_share_imports
        WHERE fingerprint = :fingerprint AND status IN (:statuses)
        ORDER BY createdAt DESC LIMIT 1""")
    suspend fun findRecentByFingerprint(
        fingerprint: String,
        statuses: List<ShareImportStatus>
    ): PendingShareImportEntity?

    @Query("SELECT * FROM pending_share_imports WHERE fingerprint = :fingerprint ORDER BY createdAt DESC LIMIT 1")
    suspend fun findLatestByFingerprint(fingerprint: String): PendingShareImportEntity?

    @Query("""SELECT COUNT(*) FROM pending_share_imports
        WHERE sourceUri = :sourceUri AND status IN ('ENQUEUED', 'COPYING', 'IMPORTING')""")
    suspend fun countPendingBySourceUri(sourceUri: String): Int

    @Query("""SELECT COUNT(*) FROM pending_share_imports
        WHERE sourceUri = :sourceUri AND token != :excludedToken
          AND status IN ('ENQUEUED', 'COPYING', 'IMPORTING')""")
    suspend fun countPendingBySourceUriExcludingToken(sourceUri: String, excludedToken: String): Int

    @Query("""SELECT * FROM pending_share_imports
        WHERE status IN (:statuses) ORDER BY createdAt DESC LIMIT 1""")
    suspend fun findMostRecentByStatuses(statuses: List<ShareImportStatus>): PendingShareImportEntity?

    @Update
    suspend fun update(event: PendingShareImportEntity)

    @Query("""UPDATE pending_share_imports SET status = :status, error = :error,
        attempts = :attempts, updatedAt = :updatedAt WHERE token = :token
        AND status IN ('ENQUEUED', 'COPYING', 'IMPORTING')""")
    suspend fun updateStatus(
        token: String,
        status: ShareImportStatus,
        error: String?,
        attempts: Int,
        updatedAt: Long
    ): Int

    @Query("""UPDATE pending_share_imports SET status = :status, copyPath = :copyPath,
        progressBytes = :progressBytes, totalBytes = :totalBytes, error = :error,
        updatedAt = :updatedAt WHERE token = :token
        AND status IN ('ENQUEUED', 'COPYING', 'IMPORTING')""")
    suspend fun updateCopyProgress(
        token: String,
        status: ShareImportStatus,
        copyPath: String?,
        progressBytes: Long,
        totalBytes: Long?,
        error: String?,
        updatedAt: Long
    ): Int

    @Query("""UPDATE pending_share_imports SET title = :title, updatedAt = :updatedAt
        WHERE token = :token AND status IN ('ENQUEUED', 'COPYING', 'IMPORTING')""")
    suspend fun updateTitleIfActive(token: String, title: String, updatedAt: Long): Int

    @Transaction
    suspend fun insertOrReturn(event: PendingShareImportEntity): PendingShareImportEntity {
        insertIfMissing(event)
        return getByToken(event.token) ?: event
    }
}
