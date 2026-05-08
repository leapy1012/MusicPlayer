package gd.app.musicplayer.ui.player

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import gd.app.musicplayer.core.extension.albumArtSource
import gd.app.musicplayer.data.model.Music
import gd.app.musicplayer.data.model.MusicSet
import gd.app.musicplayer.data.repository.PlaylistRepo
import gd.app.musicplayer.domain.usecase.library.GetTracksUseCase
import gd.app.musicplayer.domain.usecase.library.ObserveTracksUseCase
import gd.app.musicplayer.playback.PlaybackController
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.distinctUntilChangedBy
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

data class TrackUiState(
    val musicId: Long? = null,
    val title: String = "",
    val artist: String = "",
    val album: String = "",
    val artworkSource: Any? = null,
    val isFavorite: Boolean = false,
)

data class PlaybackProgressUiState(
    val isPlaying: Boolean = false,
    val positionMs: Long = 0L,
    val durationMs: Long = 0L
)

data class VisualizerUiState(
    val audioSessionId: Int = -1,
    val isPlaying: Boolean = false
)

data class PlaybackHighlightState(
    val currentMusicId: Long? = null,
    val isPlaying: Boolean = false
)

@HiltViewModel
class PlayerViewModel @Inject constructor(
    private val playbackController: PlaybackController,
    private val playlistRepo: PlaylistRepo,
    private val getTracksUseCase: GetTracksUseCase
) : ViewModel() {

    @OptIn(ExperimentalCoroutinesApi::class)
    val trackUiState: StateFlow<TrackUiState> =
        playbackController.state
            .map { state -> state.currentTrack }
            .distinctUntilChangedBy { music -> music?.id }
            .flatMapLatest { music ->
                if (music == null) {
                    flowOf(TrackUiState())
                } else {
                    playlistRepo.observeIsFavorite(music.id)
                        .map { isFavorite ->
                            TrackUiState(
                                musicId = music.id,
                                title = music.title,
                                artist = music.artist,
                                album = music.album,
                                artworkSource = music.albumArtSource(),
                                isFavorite = isFavorite
                            )
                        }
                }
            }
            .stateIn(
                scope = viewModelScope,
                started = SharingStarted.WhileSubscribed(5_000),
                initialValue = TrackUiState()
            )

    val progressUiState: StateFlow<PlaybackProgressUiState> = playbackController.state
        .map { state ->
            PlaybackProgressUiState(
                isPlaying = state.isPlaying,
                positionMs = state.positionMs,
                durationMs = state.durationMs
            )
        }
        .distinctUntilChanged()
        .stateIn(
            viewModelScope,
            SharingStarted.WhileSubscribed(5_000),
            PlaybackProgressUiState()
        )

    val visualizerUiState: StateFlow<VisualizerUiState> =
        playbackController.state
            .map { state ->
                VisualizerUiState(
                    audioSessionId = state.audioSessionId,
                    isPlaying = state.isPlaying
                )
            }
            .distinctUntilChanged()
            .stateIn(
                scope = viewModelScope,
                started = SharingStarted.WhileSubscribed(5_000),
                initialValue = VisualizerUiState()
            )

    val currentIndex: StateFlow<Int> =
        playbackController.state
            .map { it.currentIndex }
            .stateIn(
                viewModelScope,
                SharingStarted.WhileSubscribed(5_000),
                -1
            )


    val playbackHighlightState: StateFlow<PlaybackHighlightState> =
        playbackController.state
            .map { state ->
                PlaybackHighlightState(
                    currentMusicId = state.currentTrack?.id,
                    isPlaying = state.isPlaying
                )
            }
            .distinctUntilChanged()
            .stateIn(
                viewModelScope,
                SharingStarted.WhileSubscribed(5_000),
                PlaybackHighlightState()
            )

    fun toggleFavorite() {
        val musicId = trackUiState.value.musicId ?: return

        viewModelScope.launch {
            playlistRepo .toggleFavorite(musicId)
        }
    }

    fun playQueue(
        context: Context,
        queue: List<Music>,
        startIndex: Int
    ) {
        playbackController.playQueue(
            context = context,
            queue = queue,
            startIndex = startIndex
        )
    }

    fun shufflePlay(context: Context, queue: List<Music>) {
        playbackController.shufflePlay(context, queue)
    }

    fun enqueue(context: Context, items: List<Music>) {
        playbackController.enqueue(context, items)
    }

    suspend fun playAllTracks(context: Context) {
        val queue = getTracksUseCase(MusicSet.Tracks)
        if (queue.isNotEmpty()) {
            playbackController.playQueue(
                context = context,
                queue = queue,
                startIndex = 0
            )
        }
    }

    fun togglePlayPause(context: Context) {
        playbackController.togglePlayPause(context)
    }

    fun play(context: Context) {
        playbackController.play(context)
    }

    fun pause(context: Context) {
        playbackController.pause(context)
    }

    fun playNext(context: Context) {
        playbackController.playNext(context)
    }

    fun playPrevious(context: Context) {
        playbackController.playPrevious(context)
    }

    fun seekTo(context: Context, positionMs: Int) {
        playbackController.seekTo(context, positionMs)
    }

    fun setStopAfterCurrentTrack(context: Context, enabled: Boolean) {
        playbackController.setStopAfterCurrentTrack(context, enabled)
    }

    fun clearQueue(context: Context) {
        playbackController.clearQueue(context)
    }

    fun replaceQueue(context: Context, queue: List<Music>, currentIndex: Int) {
        playbackController.replaceQueue(context, queue, currentIndex)
    }

    fun stop(context: Context) {
        playbackController.stop(context)
    }

    fun restartCurrentTrack(context: Context) {
        playbackController.restartCurrentTrack(context)
    }

    fun cyclePlayMode(context: Context) {
        playbackController.cyclePlayMode(context)
    }

    fun applyAudioEffects(context: Context) {
        playbackController.applyAudioEffects(context)
    }

    fun applyPlaybackTuning(context: Context) {
        playbackController.applyPlaybackTuning(context)
    }

    fun refreshNotificationStyle(context: Context) {
        playbackController.refreshNotificationStyle(context)
    }
}