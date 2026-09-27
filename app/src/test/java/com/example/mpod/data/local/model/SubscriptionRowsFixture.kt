package com.example.mpod.data.local.model

import com.example.mpod.data.local.entity.EpisodeEntity
import com.example.mpod.data.local.entity.PodcastEntity

internal fun subscriptionRows(
    podcasts: List<PodcastEntity>, episodes: List<EpisodeEntity> = emptyList(),
    queuedIds: Set<Long> = emptySet()
): List<SubscriptionRow> = podcasts.sortedWith(compareBy({ it.title }, { it.id })).flatMap { pod ->
    val data = SubscriptionPodcastData(pod.id, pod.title, pod.description, pod.feedUrl, pod.artworkUrl)
    episodes.filter { it.podcastId == pod.id }.sortedByDescending { it.publishedAt }.map { ep ->
        SubscriptionRow(data, SubscriptionEpisodeData(ep.id, ep.title, ep.description,
            ep.durationSeconds, ep.publishedAtString, ep.isListened, ep.isDownloaded), ep.id in queuedIds)
    }.ifEmpty { listOf(SubscriptionRow(data, null, false)) }
}
