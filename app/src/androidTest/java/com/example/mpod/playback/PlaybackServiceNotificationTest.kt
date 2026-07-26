package com.example.mpod.playback

import androidx.media3.common.Player
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PlaybackServiceNotificationTest {
    @Test
    fun mediaNotificationExposesPlayPauseWithoutQueueNavigation() {
        val commands = mediaNotificationPlayerCommands()

        assertTrue(commands.contains(Player.COMMAND_PLAY_PAUSE))
        assertFalse(commands.contains(Player.COMMAND_SEEK_TO_PREVIOUS))
        assertFalse(commands.contains(Player.COMMAND_SEEK_TO_PREVIOUS_MEDIA_ITEM))
        assertFalse(commands.contains(Player.COMMAND_SEEK_TO_NEXT))
        assertFalse(commands.contains(Player.COMMAND_SEEK_TO_NEXT_MEDIA_ITEM))
    }
}
