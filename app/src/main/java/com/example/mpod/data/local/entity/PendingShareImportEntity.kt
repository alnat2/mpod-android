package com.example.mpod.data.local.entity

import androidx.room.Entity
import androidx.room.ColumnInfo
import androidx.room.Index
import androidx.room.PrimaryKey

enum class ShareImportStatus {
    ENQUEUED,
    COPYING,
    IMPORTING,
    DONE,
    FAILED
}

@Entity(
    tableName = "pending_share_imports",
    indices = [
        Index(value = ["status", "createdAt"]),
        Index(value = ["batchId"]),
        Index(value = ["fingerprint", "createdAt"])
    ]
)
data class PendingShareImportEntity(
    @PrimaryKey val token: String,
    val payload: String,
    val title: String?,
    val status: ShareImportStatus = ShareImportStatus.ENQUEUED,
    val sourceUri: String? = null,
    val copyPath: String? = null,
    val error: String? = null,
    val attempts: Int = 0,
    val batchId: String,
    val fingerprint: String,
    val progressBytes: Long = 0,
    val totalBytes: Long? = null,
    @ColumnInfo(defaultValue = "0") val completionAcknowledged: Boolean = false,
    val createdAt: Long,
    val updatedAt: Long
)
