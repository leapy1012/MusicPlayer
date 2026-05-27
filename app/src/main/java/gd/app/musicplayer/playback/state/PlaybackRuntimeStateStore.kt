package gd.app.musicplayer.playback.state
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

    fun setState(state: MusicPlaybackState) {
        _state.value = state
    }

    fun update(block: (MusicPlaybackState) -> MusicPlaybackState) {
        _state.update(block)
    }

    fun reset() {
        _state.value = MusicPlaybackState()
    }

    fun initializeIfNeeded() {
        if (!_state.value.initialized) {
            _state.value = MusicPlaybackState(initialized = true)
        }
    }

    fun isInitialized(): Boolean {
        return _state.value.initialized
    }

    fun hasActiveState(): Boolean {
        val state = _state.value

        return state.initialized &&
                state.currentTrack != null
    }
}
