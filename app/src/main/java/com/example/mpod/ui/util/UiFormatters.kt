package com.example.mpod.ui.util

import kotlin.math.roundToInt

fun formatEpisodeDuration(seconds: Int?): String {
    val safeSeconds = seconds?.coerceAtLeast(0) ?: return ""
    val minutes = (safeSeconds / 60).coerceAtLeast(1)
    return if (minutes >= 60) {
        val hours = minutes / 60
        val remainingMinutes = minutes % 60
        if (remainingMinutes == 0) "${hours}h" else "${hours}h ${remainingMinutes}m"
    } else {
        "${minutes}m"
    }
}

fun formatTotalDuration(seconds: Int): String {
    val minutes = (seconds / 60).coerceAtLeast(0)
    val hours = minutes / 60
    val remainingMinutes = minutes % 60
    return when {
        hours > 0 && remainingMinutes > 0 -> "${hours}h ${remainingMinutes}m"
        hours > 0 -> "${hours}h"
        else -> "${remainingMinutes}m"
    }
}

fun formatProgressTime(seconds: Int): String {
    val safeSeconds = seconds.coerceAtLeast(0)
    val minutes = safeSeconds / 60
    val remainingSeconds = safeSeconds % 60
    return "$minutes:${remainingSeconds.toString().padStart(2, '0')}"
}

fun formatPublishedDate(value: String?): String? {
    val date = value?.take(10)?.takeIf { it.length == 10 } ?: return null
    val year = date.substring(2, 4)
    val month = date.substring(5, 7)
    val day = date.substring(8, 10)
    return "$day.$month.$year"
}

fun Double?.toDurationSeconds(): Int? = this?.roundToInt()?.takeIf { it > 0 }
