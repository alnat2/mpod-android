package com.example.mpod.ui.util

/**
 * Standard unified duration and progress time formatting:
 * - If duration is less than 1 hour: "mm:ss" (e.g. "23:14", "05:03", "00:45")
 * - If duration is 1 hour or more: "h:mm:ss" (e.g. "1:25:39", "2:00:00")
 */
fun formatTime(seconds: Int): String {
    val safeSeconds = seconds.coerceAtLeast(0)
    val hours = safeSeconds / 3600
    val minutes = (safeSeconds % 3600) / 60
    val remainingSeconds = safeSeconds % 60
    return if (hours > 0) {
        "$hours:${minutes.toString().padStart(2, '0')}:${remainingSeconds.toString().padStart(2, '0')}"
    } else {
        "${minutes.toString().padStart(2, '0')}:${remainingSeconds.toString().padStart(2, '0')}"
    }
}

fun formatEpisodeDuration(seconds: Int?): String {
    val safeSeconds = seconds?.coerceAtLeast(0) ?: return ""
    if (safeSeconds <= 0) return ""
    return formatTime(safeSeconds)
}

fun formatTotalDuration(seconds: Int): String {
    val safeSeconds = seconds.coerceAtLeast(0)
    if (safeSeconds <= 0) return ""
    return formatTime(safeSeconds)
}

fun formatProgressTime(seconds: Int): String = formatTime(seconds)

fun formatRemainingTime(durationSeconds: Int, positionSeconds: Int): String =
    formatTime((durationSeconds - positionSeconds).coerceAtLeast(0))

fun formatPublishedDate(value: String?): String? {
    if (value.isNullOrBlank()) return null
    val trimmed = value.trim()
    if (trimmed.length >= 10 && trimmed[4] == '-' && trimmed[7] == '-') {
        val year = trimmed.substring(2, 4)
        val month = trimmed.substring(5, 7)
        val day = trimmed.substring(8, 10)
        return "$day.$month.$year"
    }
    val epochMillis = com.example.mpod.data.rss.RssFeedParser.parseDateOrNull(trimmed) ?: return null
    val sdf = java.text.SimpleDateFormat("dd.MM.yy", java.util.Locale.US)
    return sdf.format(java.util.Date(epochMillis))
}

fun cleanFeedText(value: String?): String {
    if (value.isNullOrBlank()) return ""
    return value
        .replace(Regex("<br\\s*/?>", RegexOption.IGNORE_CASE), " ")
        .replace(Regex("</p\\s*>", RegexOption.IGNORE_CASE), " ")
        .replace(Regex("<[^>]+>"), " ")
        .decodeHtmlEntities()
        .replace(Regex("<[^>]+>"), " ")
        .replace('\u00A0', ' ')
        .replace(Regex("\\s+"), " ")
        .trim()
}

private fun String.decodeHtmlEntities(): String {
    return replace(Regex("&(#x[0-9a-fA-F]+|#\\d+|[a-zA-Z][a-zA-Z0-9]+);")) { match ->
        val entity = match.groupValues[1]
        when {
            entity.startsWith("#x", ignoreCase = true) -> entity.drop(2).toIntOrNull(16)?.toChar()?.toString()
            entity.startsWith("#") -> entity.drop(1).toIntOrNull()?.toChar()?.toString()
            else -> namedHtmlEntities[entity]
        } ?: match.value
    }
}

private val namedHtmlEntities = mapOf(
    "amp" to "&",
    "apos" to "'",
    "gt" to ">",
    "lt" to "<",
    "nbsp" to " ",
    "quot" to "\""
)
