package com.example.mpod.playback

import android.content.Context
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import com.example.mpod.data.local.preferences.AppSettings
import dagger.hilt.android.qualifiers.ApplicationContext
import java.time.Duration
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZonedDateTime
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AutoRefreshScheduler @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private val workManager: WorkManager? by lazy {
        try {
            WorkManager.getInstance(context)
        } catch (_: IllegalStateException) {
            null
        }
    }

    fun schedule(settings: AppSettings) {
        val wm = workManager ?: return
        if (!settings.isAutoRefreshEnabled) {
            wm.cancelUniqueWork(UNIQUE_WORK_NAME)
            return
        }

        val delay = computeDelayToNextRun(settings.dailyRefreshTime)
        val constraints = Constraints.Builder()
            .setRequiredNetworkType(NetworkType.CONNECTED)
            .build()

        val request = PeriodicWorkRequestBuilder<AutoRefreshWorker>(
            repeatInterval = 24,
            repeatIntervalTimeUnit = TimeUnit.HOURS
        )
            .setConstraints(constraints)
            .setInitialDelay(delay, TimeUnit.MILLISECONDS)
            .build()

        wm.enqueueUniquePeriodicWork(
            UNIQUE_WORK_NAME,
            ExistingPeriodicWorkPolicy.UPDATE,
            request
        )
    }

    fun cancel() {
        workManager?.cancelUniqueWork(UNIQUE_WORK_NAME)
    }

    companion object {
        private const val UNIQUE_WORK_NAME = "mpod_auto_refresh"

        internal fun computeDelayToNextRun(
            dailyRefreshTime: String,
            zoneId: ZoneId = ZoneId.systemDefault(),
            now: ZonedDateTime = ZonedDateTime.now(zoneId)
        ): Long {
            val parts = dailyRefreshTime.split(":")
            val hour = parts.getOrNull(0)?.toIntOrNull() ?: 3
            val minute = parts.getOrNull(1)?.toIntOrNull() ?: 0
            val targetTime = LocalTime.of(hour, minute)
            var target = now.toLocalDate().atTime(targetTime).atZone(zoneId)
            if (!target.isAfter(now)) {
                target = target.plusDays(1)
            }
            return Duration.between(now, target).toMillis().coerceAtLeast(60_000L)
        }
    }
}
