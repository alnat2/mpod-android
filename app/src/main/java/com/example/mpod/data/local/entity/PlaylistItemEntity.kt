package com.example.mpod.data.local.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "playlist_items",
    foreignKeys = [
        ForeignKey(
            entity = EpisodeEntity::class,
            parentColumns = ["id"],
            childColumns = ["episodeId"],
            onDelete = ForeignKey.CASCADE
        ),
        ForeignKey(
            entity = AudiobookEntity::class,
            parentColumns = ["id"],
            childColumns = ["audiobookId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [
        Index(value = ["episodeId"], unique = true),
        Index(value = ["audiobookId"], unique = true)
    ]
)
data class PlaylistItemEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val episodeId: Long? = null,
    val audiobookId: Long? = null,
    val position: Int
) {
    init {
        require((episodeId != null && audiobookId == null) || (episodeId == null && audiobookId != null)) {
            "PlaylistItemEntity must contain either episodeId or audiobookId, but not both or neither."
        }
    }
}
