package gd.app.musicplayer.playback.shutdown

import gd.app.musicplayer.domain.model.Music
import gd.app.musicplayer.playback.state.PlaybackSnapshot
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ShutdownControllerTest {

    @Test
    fun `stop without clearing queue persists queue snapshot and publishes state`() {
        val callbacks = FakeShutdownCallbacks()
        val controller = ShutdownController(callbacks)

        controller.shutdown(ShutdownOptions.StopWithoutClearingQueue)

        assertEquals(1, callbacks.persistCalls.size)
        assertTrue(callbacks.persistCalls.single().persistQueue)
        assertEquals(1, callbacks.publishStateAfterShutdownCalls)
        assertEquals(0, callbacks.resetRuntimeStateCalls)
        assertEquals(0, callbacks.clearQueueStateCalls)
        assertEquals(1, callbacks.removeNotificationCalls)
        assertEquals(1, callbacks.onServiceShouldStopCalls)
    }

    @Test
    fun `clear queue keeping notification clears persisted queue and runtime state`() {
        val callbacks = FakeShutdownCallbacks()
        val controller = ShutdownController(callbacks)

        controller.shutdown(ShutdownOptions.ClearQueueKeepingNotification)

        assertTrue(callbacks.persistCalls.isEmpty())
        assertEquals(1, callbacks.clearArtworkStateCalls)
        assertEquals(1, callbacks.clearQueueStateCalls)
        assertEquals(1, callbacks.clearPersistedPlaybackStateCalls)
        assertEquals(1, callbacks.clearPersistedQueueCalls)
        assertEquals(1, callbacks.clearNotificationSessionCalls)
        assertEquals(1, callbacks.markRestoreEmptyCalls)
        assertEquals(1, callbacks.resetRuntimeStateCalls)
        assertEquals(0, callbacks.publishStateAfterShutdownCalls)
        assertEquals(0, callbacks.removeNotificationCalls)
        assertEquals(0, callbacks.onServiceShouldStopCalls)
    }

    private class FakeShutdownCallbacks : ShutdownCallbacks {
        val snapshot = snapshot()
        val persistCalls = mutableListOf<PersistCall>()
        var resetPlaybackStatisticsCalls = 0
        var resetTimedTransitionCalls = 0
        var cancelVolumeFadeCalls = 0
        var stopAndClearPlayerCalls = 0
        var abandonAudioFocusCalls = 0
        var clearArtworkStateCalls = 0
        var clearQueueStateCalls = 0
        var clearPersistedPlaybackStateCalls = 0
        var clearPersistedQueueCalls = 0
        var clearNotificationSessionCalls = 0
        var markRestoreEmptyCalls = 0
        var resetRuntimeStateCalls = 0
        var publishStateAfterShutdownCalls = 0
        var removeNotificationCalls = 0
        var onServiceShouldStopCalls = 0

        override fun capturePlaybackSnapshot(): PlaybackSnapshot = snapshot

        override fun persistPlaybackSnapshotAsync(snapshot: PlaybackSnapshot, persistQueue: Boolean) {
            persistCalls += PersistCall(snapshot, persistQueue)
        }

        override fun resetPlaybackStatistics() {
            resetPlaybackStatisticsCalls++
        }

        override fun resetTimedTransition() {
            resetTimedTransitionCalls++
        }

        override fun cancelVolumeFade() {
            cancelVolumeFadeCalls++
        }

        override fun stopAndClearPlayer() {
            stopAndClearPlayerCalls++
        }

        override fun abandonAudioFocus() {
            abandonAudioFocusCalls++
        }

        override fun clearArtworkState() {
            clearArtworkStateCalls++
        }

        override fun clearQueueState() {
            clearQueueStateCalls++
        }

        override fun clearPersistedPlaybackState() {
            clearPersistedPlaybackStateCalls++
        }

        override fun clearPersistedQueue() {
            clearPersistedQueueCalls++
        }

        override fun clearNotificationSession() {
            clearNotificationSessionCalls++
        }

        override fun markRestoreEmpty() {
            markRestoreEmptyCalls++
        }

        override fun resetRuntimeState() {
            resetRuntimeStateCalls++
        }

        override fun publishStateAfterShutdown(snapshot: PlaybackSnapshot) {
            assertEquals(this.snapshot, snapshot)
            publishStateAfterShutdownCalls++
        }

        override fun removeNotification() {
            removeNotificationCalls++
        }

        override fun onServiceShouldStop() {
            onServiceShouldStopCalls++
        }
    }

    private data class PersistCall(
        val snapshot: PlaybackSnapshot,
        val persistQueue: Boolean
    )

    companion object {
        private fun snapshot(): PlaybackSnapshot {
            val track = Music(
                id = 1L,
                title = "title",
                artist = "artist",
                album = "album",
                albumId = "album-id",
                playlistId = 0L
            )
            return PlaybackSnapshot(
                queue = listOf(track),
                currentIndex = 0,
                currentTrack = track,
                positionMs = 1000L,
                durationMs = 2000L,
                audioSessionId = 7
            )
        }
    }
}
