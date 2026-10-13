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

internal sealed class QueueAction {
    data class Remove(val index: Int) : QueueAction()
    data class Move(val fromIndex: Int, val toIndex: Int) : QueueAction()
    data class Add(val index: Int, val episodeId: Long) : QueueAction()
    data class ReplaceUri(val index: Int, val episodeId: Long) : QueueAction()
}

internal fun requiresPlayerQueueRebuild(
    currentQueueEpisodeIds: List<Long>,
    roomQueueEpisodeIds: List<Long>,
    currentEpisodeId: Long?,
    targetEpisodeId: Long,
    preferredEpisodeId: Long?,
    currentMediaUri: String? = null,
    targetMediaUri: String? = null
): Boolean {
    return preferredEpisodeId != null ||
        currentEpisodeId == null ||
        currentQueueEpisodeIds.isEmpty() ||
        currentEpisodeId !in roomQueueEpisodeIds ||
        currentEpisodeId != targetEpisodeId ||
        ((currentMediaUri != null || targetMediaUri != null) && currentMediaUri != targetMediaUri)
}

internal fun calculateQueueSyncActions(
    currentQueueIds: List<Long>,
    roomQueueIds: List<Long>,
    currentUris: List<String?> = emptyList(),
    targetUris: List<String?> = emptyList(),
    currentPlayingIndex: Int = -1
): List<QueueAction> {
    val actions = mutableListOf<QueueAction>()
    val current = currentQueueIds.mapIndexed { index, id ->
        id to currentUris.getOrNull(index)
    }.toMutableList()

    var playingIndex = currentPlayingIndex

    // 1. Remove items no longer in room queue (from end to start to keep preceding indices stable)
    val roomIdsSet = roomQueueIds.toSet()
    for (i in current.indices.reversed()) {
        val (id, _) = current[i]
        if (id !in roomIdsSet) {
            actions.add(QueueAction.Remove(i))
            current.removeAt(i)
            if (i < playingIndex) {
                playingIndex--
            }
        }
    }

    // 2. Align current order with roomQueueIds, insert missing items, and update changed URIs
    for (targetIndex in roomQueueIds.indices) {
        val targetId = roomQueueIds[targetIndex]
        val targetUri = targetUris.getOrNull(targetIndex)

        if (targetIndex < current.size && current[targetIndex].first == targetId) {
            val currentUri = current[targetIndex].second
            if (targetIndex != playingIndex && targetUri != null && currentUri != targetUri) {
                actions.add(QueueAction.ReplaceUri(targetIndex, targetId))
                current[targetIndex] = targetId to targetUri
            }
        } else {
            val existingIndex = current.indices.firstOrNull { it > targetIndex && current[it].first == targetId }
            if (existingIndex != null) {
                actions.add(QueueAction.Move(existingIndex, targetIndex))
                val moved = current.removeAt(existingIndex)
                current.add(targetIndex, moved)
                if (existingIndex == playingIndex) {
                    playingIndex = targetIndex
                } else if (existingIndex > playingIndex && targetIndex <= playingIndex) {
                    playingIndex++
                }
                val currentUri = moved.second
                if (targetIndex != playingIndex && targetUri != null && currentUri != targetUri) {
                    actions.add(QueueAction.ReplaceUri(targetIndex, targetId))
                    current[targetIndex] = targetId to targetUri
                }
            } else {
                actions.add(QueueAction.Add(targetIndex, targetId))
                current.add(targetIndex, targetId to targetUri)
                if (targetIndex <= playingIndex) {
                    playingIndex++
                }
            }
        }
    }

    // 3. Defensive cleanup: remove any leftover trailing items if current has duplicates/extras beyond roomQueueIds.size
    for (i in current.lastIndex downTo roomQueueIds.size) {
        actions.add(QueueAction.Remove(i))
        current.removeAt(i)
    }

    return actions
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
