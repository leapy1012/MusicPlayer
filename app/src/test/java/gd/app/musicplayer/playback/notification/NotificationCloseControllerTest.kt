package gd.app.musicplayer.playback.notification

import gd.app.musicplayer.domain.model.Music
import gd.app.musicplayer.playback.state.PlaybackSnapshot
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class NotificationCloseControllerTest {

    @Test
    fun `close notification syncs queue when snapshot points to valid current item`() {
        val callbacks = FakeNotificationCloseCallbacks(
            snapshot = snapshot(
                queue = listOf(track(1L)),
                currentIndex = 0
            )
        )
        val controller = NotificationCloseController(callbacks)

        controller.pauseAndCloseNotification()

        assertTrue(callbacks.dismissedByUser)
        assertEquals(1, callbacks.syncQueueFromSnapshotCalls)
        assertEquals(1, callbacks.pausePlayerIfNeededCalls)
        assertEquals(1, callbacks.persistCalls.size)
        assertTrue(callbacks.persistCalls.single().persistQueue)
        assertEquals(1, callbacks.updateSessionPlaybackStateCalls)
        assertEquals(1, callbacks.publishPausedSnapshotCalls)
        assertEquals(1, callbacks.removeNotificationCalls)
    }

    @Test
    fun `close notification skips queue sync when snapshot has invalid current index`() {
        val callbacks = FakeNotificationCloseCallbacks(
            snapshot = snapshot(
                queue = listOf(track(1L)),
                currentIndex = 5
            )
        )
        val controller = NotificationCloseController(callbacks)

        controller.pauseAndCloseNotification()

        assertEquals(0, callbacks.syncQueueFromSnapshotCalls)
        assertEquals(1, callbacks.persistCalls.size)
        assertFalse(callbacks.persistCalls.isEmpty())
    }

    private class FakeNotificationCloseCallbacks(
        private val snapshot: PlaybackSnapshot
    ) : NotificationCloseCallbacks {
        var dismissedByUser = false
        var syncQueueFromSnapshotCalls = 0
        var pausePlayerIfNeededCalls = 0
        val persistCalls = mutableListOf<PersistCall>()
        var updateSessionPlaybackStateCalls = 0
        var publishPausedSnapshotCalls = 0
        var removeNotificationCalls = 0

        override fun capturePlaybackSnapshot(): PlaybackSnapshot = snapshot

        override fun setNotificationDismissedByUser(dismissed: Boolean) {
            dismissedByUser = dismissed
        }

        override fun syncQueueFromSnapshot(snapshot: PlaybackSnapshot) {
            assertEquals(this.snapshot, snapshot)
            syncQueueFromSnapshotCalls++
        }

        override fun pausePlayerIfNeeded() {
            pausePlayerIfNeededCalls++
        }

        override fun persistPlaybackSnapshotAsync(snapshot: PlaybackSnapshot, persistQueue: Boolean) {
            persistCalls += PersistCall(snapshot, persistQueue)
        }

        override fun updateNotificationSessionPlaybackState() {
            updateSessionPlaybackStateCalls++
        }

        override fun publishPausedSnapshot(snapshot: PlaybackSnapshot) {
            assertEquals(this.snapshot, snapshot)
            publishPausedSnapshotCalls++
        }

        override fun removeNotification() {
            removeNotificationCalls++
        }
    }

    private data class PersistCall(
        val snapshot: PlaybackSnapshot,
        val persistQueue: Boolean
    )

    companion object {
        private fun track(id: Long): Music {
            return Music(
                id = id,
                title = "title$id",
                artist = "artist",
                album = "album",
                albumId = "album$id",
                playlistId = 0L
            )
        }

        private fun snapshot(queue: List<Music>, currentIndex: Int): PlaybackSnapshot {
            return PlaybackSnapshot(
                queue = queue,
                currentIndex = currentIndex,
                currentTrack = queue.getOrNull(currentIndex),
                positionMs = 1234L,
                durationMs = 5678L,
                audioSessionId = 3
            )
        }
    }
}
