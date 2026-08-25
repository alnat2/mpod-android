package com.example.mpod.data.local.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "episodes",
    foreignKeys = [
        ForeignKey(
            entity = PodcastEntity::class,
            parentColumns = ["id"],
            childColumns = ["podcastId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [
        Index(value = ["podcastId"]),
        Index(value = ["podcastId", "guid"], unique = true)
    ]
)
data class EpisodeEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val podcastId: Long,
    val guid: String,
    val title: String,
    val description: String = "",
    val audioUrl: String,
    val durationSeconds: Long = 0,
    val publishedAt: Long = 0,
    val publishedAtString: String = "",
    val isListened: Boolean = false,
    val playbackPositionMs: Long = 0,
    val isDownloaded: Boolean = false,
    val localFilePath: String? = null
)
