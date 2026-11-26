package gd.app.musicplayer.playback.state

import gd.app.musicplayer.playback.queue.MusicPlaybackState
import org.junit.Assert.*
import org.junit.Test

class PlaybackRuntimeStateStoreTest {
    @Test fun `late stored snapshot cannot overwrite a newer live publication`() {
        val store = PlaybackRuntimeStateStore()
        val beforeRead = store.initializationToken()
        val live = MusicPlaybackState(initialized = true, isPlaying = true, positionMs = 5_000)
        store.setState(live)
        store.initializeFrom(beforeRead, MusicPlaybackState(initialized = true, positionMs = 90_000))
        assertEquals(live, store.state.value)
    }
    @Test fun `stored snapshot initializes an unchanged state`() {
        val store = PlaybackRuntimeStateStore()
        val restored = MusicPlaybackState(initialized = true, positionMs = 90_000)
        store.initializeFrom(store.initializationToken(), restored)
        assertEquals(restored, store.state.value)
    }
    @Test fun `reset also invalidates an older snapshot when the values compare equal`() {
        val store = PlaybackRuntimeStateStore()
        val beforeRead = store.initializationToken()
        store.reset()
        store.initializeFrom(beforeRead, MusicPlaybackState(initialized = true, positionMs = 90_000))
        assertEquals(MusicPlaybackState(), store.state.value)
    }
}
