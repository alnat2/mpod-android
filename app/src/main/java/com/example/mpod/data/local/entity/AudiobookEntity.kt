package com.example.mpod.data.local.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "audiobooks",
    indices = [
        Index(value = ["folderUri"], unique = true)
    ]
)
data class AudiobookEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val title: String,
    val author: String = "",
    val folderUri: String,
    val coverUri: String? = null,
    val durationSeconds: Long = 0,
    val isSingleFile: Boolean = false,
    val addedAt: Long = System.currentTimeMillis()
)
