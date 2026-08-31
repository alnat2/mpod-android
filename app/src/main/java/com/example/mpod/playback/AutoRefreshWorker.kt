package com.example.mpod.playback

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.example.mpod.data.local.preferences.AppSettingsDataStore
import com.example.mpod.data.repository.PodcastRepository
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import kotlinx.coroutines.flow.first

@HiltWorker
class AutoRefreshWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted params: WorkerParameters,
    private val podcastRepository: PodcastRepository,
    private val appSettingsDataStore: AppSettingsDataStore
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val settings = appSettingsDataStore.settingsFlow.first()
        if (!settings.isAutoRefreshEnabled) return Result.success()

        val result = podcastRepository.refreshAllPodcasts()
        return if (result.isSuccess) {
            Result.success()
        } else {
            Result.retry()
        }
    }
}
