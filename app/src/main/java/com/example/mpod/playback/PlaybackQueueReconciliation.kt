package com.example.mpod.playback

internal data class QueueEpisodeState(
    val episodeId: Long,
    val savedPositionMs: Long
)

internal data class QueuePlaybackTarget(
    val episodeId: Long,
    val positionMs: Long,
    val playWhenReady: Boolean
)

internal fun requiresPlayerQueueRebuild(
    currentQueueEpisodeIds: List<Long>,
    roomQueueEpisodeIds: List<Long>,
    currentEpisodeId: Long?,
    targetEpisodeId: Long,
    preferredEpisodeId: Long?
): Boolean {
    return preferredEpisodeId != null ||
        currentQueueEpisodeIds != roomQueueEpisodeIds ||
        currentEpisodeId != targetEpisodeId
}

internal fun resolveQueuePlaybackTarget(
    queue: List<QueueEpisodeState>,
    savedActiveEpisodeId: Long?,
    currentEpisodeId: Long?,
    currentPositionMs: Long,
    currentPlayWhenReady: Boolean,
    isPlaying: Boolean = false,
    hasPendingLocalUpdate: Boolean = false,
    preferredEpisodeId: Long? = null,
    preferFirstEpisode: Boolean = false,
    forcePlayPreferred: Boolean = false
): QueuePlaybackTarget? {
    if (queue.isEmpty()) return null

    val queueById = queue.associateBy { it.episodeId }
    val currentStillQueued = currentEpisodeId != null && currentEpisodeId in queueById
    val preferred = preferredEpisodeId?.takeIf(queueById::containsKey)
        ?: queue.first().episodeId.takeIf { preferFirstEpisode }
    val targetEpisodeId = preferred
        ?: currentEpisodeId?.takeIf(queueById::containsKey)
        ?: savedActiveEpisodeId?.takeIf(queueById::containsKey)
        ?: queue.first().episodeId

    val positionMs = when {
        preferred != null -> queueById.getValue(preferred).savedPositionMs
        currentStillQueued && targetEpisodeId == currentEpisodeId -> {
            if (isPlaying || currentPlayWhenReady || hasPendingLocalUpdate) {
                currentPositionMs
            } else {
                queueById.getValue(targetEpisodeId).savedPositionMs
            }
        }
        else -> queueById.getValue(targetEpisodeId).savedPositionMs
    }.coerceAtLeast(0L)

    return QueuePlaybackTarget(
        episodeId = targetEpisodeId,
        positionMs = positionMs,
        playWhenReady = (currentStillQueued && currentPlayWhenReady) ||
            (preferred != null && forcePlayPreferred)
    )
}
