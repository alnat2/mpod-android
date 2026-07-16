package com.example.mpod.playback

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.mpod.data.network.model.PlaybackUpdateRequest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class PendingPlaybackSyncStoreTest {
    private val context: Context = ApplicationProvider.getApplicationContext()

    @Before
    @After
    fun clearStore() {
        context.getSharedPreferences(
            SharedPreferencesPendingPlaybackSyncStore.PREFERENCES_NAME,
            Context.MODE_PRIVATE
        ).edit().clear().commit()
    }

    @Test
    fun pendingMutationsSurviveStoreRecreation() {
        val first = SharedPreferencesPendingPlaybackSyncStore(context)
        first.putActive(17)
        first.putSpeed("Speed 1.5x")
        first.putPlayback(
            PlaybackUpdateRequest(
                episodeId = 17,
                positionSeconds = 64,
                durationSeconds = 120,
                completed = false,
                didSeek = true,
                clientUpdatedAt = "2026-07-16T12:00:00Z"
            )
        )

        val restored = SharedPreferencesPendingPlaybackSyncStore(context).snapshot()

        assertFalse(restored.isEmpty)
        assertEquals(17, restored.activeEpisodeId)
        assertEquals("Speed 1.5x", restored.speedLabel)
        assertEquals(1, restored.playbackUpdates.size)
        assertEquals(64, restored.playbackUpdates.single().positionSeconds)
        assertEquals(true, restored.playbackUpdates.single().didSeek)
    }
}
