package gd.app.musicplayer.playback.state

import gd.app.musicplayer.playback.queue.MusicPlaybackState
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class PlaybackRuntimeStateStore @Inject constructor() {
    private val lock = Any()
    private var revision = 0L
    private val mutableState = MutableStateFlow(MusicPlaybackState())
    val state: StateFlow<MusicPlaybackState> = mutableState.asStateFlow()

    fun initializationToken(): Long = synchronized(lock) { revision }

    /** Even a reset to an equal value invalidates a storage read already in flight. */
    fun initializeFrom(expectedRevision: Long, restored: MusicPlaybackState) = synchronized(lock) {
        if (revision == expectedRevision) {
            revision++
            mutableState.value = restored
        }
    }

    fun setState(state: MusicPlaybackState) = synchronized(lock) {
        revision++
        mutableState.value = state
    }

    fun update(block: (MusicPlaybackState) -> MusicPlaybackState) = synchronized(lock) {
        revision++
        mutableState.value = block(mutableState.value)
    }

    fun reset() = setState(MusicPlaybackState())

    fun initializeIfNeeded() = synchronized(lock) {
        if (!mutableState.value.initialized) {
            revision++
            mutableState.value = MusicPlaybackState(initialized = true)
        }
    }

    fun isInitialized(): Boolean = state.value.initialized
    fun hasActiveState(): Boolean = state.value.let { it.initialized && it.currentTrack != null }
}
