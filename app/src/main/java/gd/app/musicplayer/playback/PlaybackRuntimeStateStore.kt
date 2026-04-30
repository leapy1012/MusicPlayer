package gd.app.musicplayer.playback

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class PlaybackRuntimeStateStore @Inject constructor() {

    private val mutableState = MutableStateFlow(MusicPlaybackState())

    val state: StateFlow<MusicPlaybackState> = mutableState.asStateFlow()

    fun publish(state: MusicPlaybackState) {
        mutableState.value = state
    }

    fun reset() {
        mutableState.value = MusicPlaybackState()
    }
}