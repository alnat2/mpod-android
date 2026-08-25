package com.example.mpod.data.local.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "podcasts",
    indices = [
        Index(value = ["feedUrl"], unique = true)
    ]
)
data class PodcastEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val feedUrl: String,
    val title: String,
    val description: String = "",
    val author: String = "",
    val artworkUrl: String = "",
    val link: String = "",
    val lastBuildDate: String = "",
    val lastRefreshedAt: Long = System.currentTimeMillis()
)
