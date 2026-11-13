package com.example.mpod.data.local.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index

@Entity(
    tableName = "audiobook_playlist_tracks",
    primaryKeys = ["audiobookId", "trackId"],
    foreignKeys = [
        ForeignKey(
            entity = AudiobookEntity::class,
            parentColumns = ["id"],
            childColumns = ["audiobookId"],
            onDelete = ForeignKey.CASCADE
        ),
        ForeignKey(
            entity = AudiobookTrackEntity::class,
            parentColumns = ["id"],
            childColumns = ["trackId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [
        Index(value = ["audiobookId"]),
        Index(value = ["trackId"])
    ]
)
data class AudiobookPlaylistTrackEntity(
    val audiobookId: Long,
    val trackId: Long,
    val addedAt: Long = System.currentTimeMillis()
)
