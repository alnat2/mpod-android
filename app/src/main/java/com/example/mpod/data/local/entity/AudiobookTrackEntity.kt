package com.example.mpod.data.local.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "audiobook_tracks",
    foreignKeys = [
        ForeignKey(
            entity = AudiobookEntity::class,
            parentColumns = ["id"],
            childColumns = ["audiobookId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [
        Index(value = ["audiobookId"]),
        Index(value = ["audiobookId", "fileUri"], unique = true)
    ]
)
data class AudiobookTrackEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val audiobookId: Long,
    val title: String,
    val fileUri: String,
    val trackNumber: Int,
    val durationSeconds: Long = 0,
    val playbackPositionMs: Long = 0,
    val isListened: Boolean = false,
    val isAvailable: Boolean = true
)
