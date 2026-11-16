package com.example.mpod.playback

import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import javax.inject.Inject
import javax.inject.Singleton

sealed interface PlaybackStatusEvent {
    data class Error(val message: String) : PlaybackStatusEvent
}

@Singleton
class PlaybackStatusEvents @Inject constructor() {
    private val _events = MutableSharedFlow<PlaybackStatusEvent>(extraBufferCapacity = 64)
    val events: SharedFlow<PlaybackStatusEvent> = _events.asSharedFlow()

    fun postError(message: String) {
        _events.tryEmit(PlaybackStatusEvent.Error(message))
    }
}
