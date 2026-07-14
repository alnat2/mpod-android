package com.example.mpod.playback

internal data class QueueEpisodeState(
    val episodeId: Int,
    val savedPositionMs: Long
)

internal data class QueuePlaybackTarget(
    val episodeId: Int,
    val positionMs: Long,
    val playWhenReady: Boolean
)

internal fun resolveQueuePlaybackTarget(
    queue: List<QueueEpisodeState>,
    backendActiveEpisodeId: Int?,
    currentEpisodeId: Int?,
    currentPositionMs: Long,
    currentPlayWhenReady: Boolean,
    preferredEpisodeId: Int? = null,
    forcePlayPreferred: Boolean = false
): QueuePlaybackTarget? {
    if (queue.isEmpty()) return null

    val queueById = queue.associateBy { it.episodeId }
    val currentStillQueued = currentEpisodeId != null && currentEpisodeId in queueById
    val preferred = preferredEpisodeId?.takeIf(queueById::containsKey)
    val targetEpisodeId = preferred
        ?: currentEpisodeId?.takeIf(queueById::containsKey)
        ?: backendActiveEpisodeId?.takeIf(queueById::containsKey)
        ?: queue.first().episodeId

    val positionMs = when {
        preferred != null -> 0L
        currentStillQueued && targetEpisodeId == currentEpisodeId -> currentPositionMs
        else -> queueById.getValue(targetEpisodeId).savedPositionMs
    }.coerceAtLeast(0L)

    return QueuePlaybackTarget(
        episodeId = targetEpisodeId,
        positionMs = positionMs,
        playWhenReady = (currentStillQueued && currentPlayWhenReady) ||
            (preferred != null && forcePlayPreferred)
    )
}
