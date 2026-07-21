package com.example.mpod.playback

import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class PlaybackQueueInvalidator @Inject constructor() {
    private val _events = MutableSharedFlow<Unit>(
        extraBufferCapacity = 1,
        onBufferOverflow = BufferOverflow.DROP_OLDEST
    )
    val events: SharedFlow<Unit> = _events.asSharedFlow()
    private val _homeRefreshEvents = MutableSharedFlow<Unit>(
        extraBufferCapacity = 1,
        onBufferOverflow = BufferOverflow.DROP_OLDEST
    )
    val homeRefreshEvents: SharedFlow<Unit> = _homeRefreshEvents.asSharedFlow()

    fun invalidate() {
        _events.tryEmit(Unit)
    }

    fun refreshHome() {
        _homeRefreshEvents.tryEmit(Unit)
    }
}
