package com.example.mpod.playback

import android.content.Context
import com.example.mpod.data.network.model.PlaybackUpdateRequest
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken

internal data class PendingPlaybackSync(
    val activeEpisodeId: Int? = null,
    val playbackUpdates: List<PlaybackUpdateRequest> = emptyList(),
    val speedLabel: String? = null
) {
    val isEmpty: Boolean
        get() = activeEpisodeId == null && playbackUpdates.isEmpty() && speedLabel == null
}

internal interface PendingPlaybackSyncStore {
    fun snapshot(): PendingPlaybackSync
    fun putActive(episodeId: Int)
    fun clearActiveIf(episodeId: Int)
    fun putPlayback(request: PlaybackUpdateRequest)
    fun clearPlaybackIf(episodeId: Int, clientUpdatedAt: String)
    fun putSpeed(label: String)
    fun clearSpeedIf(label: String)
}

internal class SharedPreferencesPendingPlaybackSyncStore(
    context: Context,
    private val gson: Gson = Gson()
) : PendingPlaybackSyncStore {
    private val preferences = context.getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE)

    @Synchronized
    override fun snapshot(): PendingPlaybackSync {
        return PendingPlaybackSync(
            activeEpisodeId = preferences.getInt(ACTIVE_EPISODE_KEY, NO_EPISODE_ID)
                .takeUnless { it == NO_EPISODE_ID },
            playbackUpdates = readPlaybackUpdates().values.sortedBy { it.episodeId },
            speedLabel = preferences.getString(SPEED_LABEL_KEY, null)
        )
    }

    @Synchronized
    override fun putActive(episodeId: Int) {
        preferences.edit().putInt(ACTIVE_EPISODE_KEY, episodeId).commit()
    }

    @Synchronized
    override fun clearActiveIf(episodeId: Int) {
        if (preferences.getInt(ACTIVE_EPISODE_KEY, NO_EPISODE_ID) == episodeId) {
            preferences.edit().remove(ACTIVE_EPISODE_KEY).commit()
        }
    }

    @Synchronized
    override fun putPlayback(request: PlaybackUpdateRequest) {
        val updates = readPlaybackUpdates()
        updates[request.episodeId] = mergePendingPlayback(updates[request.episodeId], request)
        writePlaybackUpdates(updates)
    }

    @Synchronized
    override fun clearPlaybackIf(episodeId: Int, clientUpdatedAt: String) {
        val updates = readPlaybackUpdates()
        if (updates[episodeId]?.clientUpdatedAt != clientUpdatedAt) return
        updates.remove(episodeId)
        writePlaybackUpdates(updates)
    }

    @Synchronized
    override fun putSpeed(label: String) {
        preferences.edit().putString(SPEED_LABEL_KEY, label).commit()
    }

    @Synchronized
    override fun clearSpeedIf(label: String) {
        if (preferences.getString(SPEED_LABEL_KEY, null) == label) {
            preferences.edit().remove(SPEED_LABEL_KEY).commit()
        }
    }

    private fun readPlaybackUpdates(): MutableMap<Int, PlaybackUpdateRequest> {
        val json = preferences.getString(PLAYBACK_UPDATES_KEY, null) ?: return mutableMapOf()
        return runCatching {
            val type = object : TypeToken<List<PlaybackUpdateRequest>>() {}.type
            gson.fromJson<List<PlaybackUpdateRequest>>(json, type)
                .associateByTo(mutableMapOf()) { it.episodeId }
        }.getOrDefault(mutableMapOf())
    }

    private fun writePlaybackUpdates(updates: Map<Int, PlaybackUpdateRequest>) {
        val editor = preferences.edit()
        if (updates.isEmpty()) {
            editor.remove(PLAYBACK_UPDATES_KEY)
        } else {
            editor.putString(PLAYBACK_UPDATES_KEY, gson.toJson(updates.values.sortedBy { it.episodeId }))
        }
        editor.commit()
    }

    companion object {
        internal const val PREFERENCES_NAME = "PendingPlaybackSync"
        private const val ACTIVE_EPISODE_KEY = "active_episode_id"
        private const val PLAYBACK_UPDATES_KEY = "playback_updates"
        private const val SPEED_LABEL_KEY = "speed_label"
        private const val NO_EPISODE_ID = -1
    }
}

internal fun mergePendingPlayback(
    current: PlaybackUpdateRequest?,
    next: PlaybackUpdateRequest
): PlaybackUpdateRequest {
    if (current == null) return next
    if (current.completed) return current

    return next.copy(
        completed = next.completed,
        didSeek = current.didSeek || next.didSeek
    )
}
