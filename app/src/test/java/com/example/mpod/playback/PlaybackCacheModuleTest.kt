package com.example.mpod.playback

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test
import java.lang.annotation.RetentionPolicy

class PlaybackCacheModuleTest {

    @Test
    fun maxCacheSizeIsConfiguredToTwoGigabytes() {
        val twoGigabytes = 2L * 1024 * 1024 * 1024
        assertEquals(twoGigabytes, PlaybackCacheModule.MAX_CACHE_SIZE_BYTES)
    }

    @Test
    fun mediaCacheDataSourceFactoryQualifierHasExpectedRetention() {
        val annotationClass = MediaCacheDataSourceFactory::class.java
        val retention = annotationClass.getAnnotation(java.lang.annotation.Retention::class.java)
        assertNotNull(retention)
        assertEquals(RetentionPolicy.CLASS, retention?.value)
    }

    @Test
    fun resetForTestingExecutesSafely() {
        // Safe to call even when not initialized
        PlaybackCacheModule.resetForTesting()
    }

    @Test
    fun buildEpisodeCustomCacheKeyGeneratesStableKey() {
        assertEquals("episode_42", buildEpisodeCustomCacheKey(42L))
        assertEquals("episode_12345", buildEpisodeCustomCacheKey(12345L))
    }
}
