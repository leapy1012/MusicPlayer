package gd.app.musicplayer.playback.state

import gd.app.musicplayer.domain.model.Music
import gd.app.musicplayer.feature.widget.provider.WidgetPlaybackSnapshot
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Test

class PlaybackStateUpdateCoordinatorTest {

    @Test
    fun `publishPlaybackState does not update widgets on progress tick by default`() {
        val callbacks = FakeCallbacks()
        val coordinator = PlaybackStateUpdateCoordinator(callbacks)

        coordinator.publishPlaybackState(reason = PublishReason.ProgressTick)

        assertEquals(0, callbacks.updatedWidgets.size)
    }

    @Test
    fun `publishPlaybackState updates widgets on progress tick when forced`() {
        val callbacks = FakeCallbacks()
        val coordinator = PlaybackStateUpdateCoordinator(callbacks)

        coordinator.publishPlaybackState(
            reason = PublishReason.ProgressTick,
            forceWidgetUpdate = true
        )

        assertEquals(1, callbacks.updatedWidgets.size)
        assertSame(callbacks.runtimeSnapshot, callbacks.updatedWidgets.single())
    }

    @Test
    fun `publishPlaybackState updates widgets for non-progress reasons`() {
        val callbacks = FakeCallbacks()
        val coordinator = PlaybackStateUpdateCoordinator(callbacks)

        coordinator.publishPlaybackState(reason = PublishReason.QueueChanged)

        assertEquals(1, callbacks.updatedWidgets.size)
        assertSame(callbacks.runtimeSnapshot, callbacks.updatedWidgets.single())
    }

    @Test
    fun `publishStateAfterShutdown uses blocking widget update with shutdown snapshot`() {
        val callbacks = FakeCallbacks()
        val coordinator = PlaybackStateUpdateCoordinator(callbacks)
        val shutdown = PlaybackSnapshot(
            queue = callbacks.runtimeSnapshot.queue,
            currentIndex = 0,
            currentTrack = callbacks.runtimeSnapshot.currentTrack,
            positionMs = 111L,
            durationMs = 222L,
            audioSessionId = 3
        )

        coordinator.publishStateAfterShutdown(snapshot = shutdown)

        assertEquals(0, callbacks.updatedWidgets.size)
        assertEquals(1, callbacks.blockingUpdatedWidgets.size)
        assertSame(callbacks.shutdownSnapshot, callbacks.blockingUpdatedWidgets.single())
    }

    private class FakeCallbacks : PlaybackStateUpdateCoordinator.Callbacks {
        val runtimeSnapshot = widgetSnapshot(positionMs = 10L)
        val shutdownSnapshot = widgetSnapshot(positionMs = 20L)
        val updatedWidgets = mutableListOf<WidgetPlaybackSnapshot>()
        val blockingUpdatedWidgets = mutableListOf<WidgetPlaybackSnapshot>()

        override fun stateOrchestratorOrNull(): PlaybackStateOrchestrator? = null

        override fun runtimeWidgetSnapshot(): WidgetPlaybackSnapshot = runtimeSnapshot

        override fun shutdownWidgetSnapshot(snapshot: PlaybackSnapshot?): WidgetPlaybackSnapshot =
            shutdownSnapshot

        override fun updateWidgets(snapshot: WidgetPlaybackSnapshot) {
            updatedWidgets += snapshot
        }

        override fun updateWidgetsBlocking(snapshot: WidgetPlaybackSnapshot) {
            blockingUpdatedWidgets += snapshot
        }
    }

    companion object {
        private fun widgetSnapshot(positionMs: Long): WidgetPlaybackSnapshot {
            val track = Music(
                id = 1L,
                title = "title",
                artist = "artist",
                album = "album",
                albumId = "album-id",
                playlistId = 1L
            )
            return WidgetPlaybackSnapshot(
                queue = listOf(track),
                currentTrack = track,
                currentIndex = 0,
                positionMs = positionMs,
                isPlaying = true,
                playMode = 0
            )
        }
    }
}
