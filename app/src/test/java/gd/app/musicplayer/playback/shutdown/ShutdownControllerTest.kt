package gd.app.musicplayer.playback.shutdown

import java.lang.reflect.Proxy
import gd.app.musicplayer.domain.model.Music
import gd.app.musicplayer.playback.state.PlaybackSnapshot
import org.junit.Assert.*
import org.junit.Test

class ShutdownControllerTest {
    @Test fun `quit quiesces callbacks and saves the selected position before clearing the player`() {
        val events = mutableListOf<String>()
        val music = Music(7, "Track", "Artist", "Album", "1", 0, duration = 180_000)
        val snapshot = PlaybackSnapshot(listOf(music), 0, music, 42_000, 180_000, -1)
        var saved: PlaybackSnapshot? = null
        val callbacks = Proxy.newProxyInstance(
            ShutdownCallbacks::class.java.classLoader, arrayOf(ShutdownCallbacks::class.java)
        ) { _, method, arguments ->
            events += method.name
            when (method.name) {
                "capturePlaybackSnapshot" -> snapshot
                "persistPlaybackSnapshotBlocking" -> { saved = arguments!![0] as PlaybackSnapshot; null }
                else -> null
            }
        } as ShutdownCallbacks
        ShutdownController(callbacks).shutdown(ShutdownOptions.ExitService)
        assertEquals("beginShutdown", events.first())
        assertTrue(events.indexOf("persistPlaybackSnapshotBlocking") < events.indexOf("stopAndClearPlayer"))
        assertTrue(events.indexOf("publishStateAfterShutdown") < events.indexOf("onServiceShouldStop"))
        assertEquals(42_000L, saved?.positionMs)
        assertFalse(events.contains("clearPersistedPlaybackState"))
        assertFalse(events.contains("clearPersistedQueue"))
    }
}
