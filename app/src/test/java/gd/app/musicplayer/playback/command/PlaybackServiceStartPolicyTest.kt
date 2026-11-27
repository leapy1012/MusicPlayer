package gd.app.musicplayer.playback.command

import org.junit.Assert.*
import org.junit.Test

class PlaybackServiceStartPolicyTest {
    @Test fun `play and navigation controls request a foreground start without a service-running check`() {
        listOf(PlaybackServiceActions.ACTION_PLAY, PlaybackServiceActions.ACTION_TOGGLE_PLAY_PAUSE,
            PlaybackServiceActions.ACTION_NEXT, PlaybackServiceActions.ACTION_PREVIOUS,
            PlaybackServiceActions.ACTION_RESTART_CURRENT).forEach {
            assertTrue(it, PlaybackServiceActions.requiresForegroundStart(it))
        }
    }
    @Test fun `queue selection that can play also requests foreground promotion`() {
        listOf(PlaybackServiceActions.ACTION_PLAY_FROM_QUEUE, PlaybackServiceActions.ACTION_PLAY_NEXT,
            PlaybackServiceActions.ACTION_REPLACE_QUEUE, PlaybackServiceActions.ACTION_CHANGE_MUSIC_BY_INDEX)
            .forEach { assertTrue(it, PlaybackServiceActions.requiresForegroundStart(it)) }
    }
    @Test fun `non-playing controls do not start an unnecessary foreground service`() {
        listOf(PlaybackServiceActions.ACTION_PAUSE, PlaybackServiceActions.ACTION_STOP,
            PlaybackServiceActions.ACTION_TOGGLE_FAVORITE, PlaybackServiceActions.ACTION_EXIT)
            .forEach { assertFalse(it, PlaybackServiceActions.requiresForegroundStart(it)) }
        assertFalse(PlaybackServiceActions.requiresForegroundStart(null))
    }
}
