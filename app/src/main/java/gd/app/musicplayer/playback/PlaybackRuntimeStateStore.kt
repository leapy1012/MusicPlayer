package gd.app.musicplayer.playback

import gd.app.musicplayer.playback.queue.MusicPlaybackState
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class PlaybackRuntimeStateStore @Inject constructor() {

    private val _state = MutableStateFlow(MusicPlaybackState())

    val state: StateFlow<MusicPlaybackState> = _state.asStateFlow()

    fun update(transform: (MusicPlaybackState) -> MusicPlaybackState) {
        _state.update(transform)
    }

    fun setState(state: MusicPlaybackState) {
        _state.value = state
    }

    // Compatibility wrapper for previous call sites.
    fun publish(state: MusicPlaybackState) {
        setState(state)
    }

    // Compatibility wrapper for previous call sites.
    fun reset() {
        _state.value = MusicPlaybackState()
    }
}
