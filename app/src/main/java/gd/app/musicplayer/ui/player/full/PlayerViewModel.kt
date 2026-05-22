package gd.app.musicplayer.ui.player.full

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import gd.app.musicplayer.core.common.extension.albumArtSource
import gd.app.musicplayer.domain.model.Music
import gd.app.musicplayer.domain.model.MusicSet
import gd.app.musicplayer.domain.repository.PlaylistRepo
import gd.app.musicplayer.domain.usecase.library.ObserveAlbumPictureUseCase
import gd.app.musicplayer.domain.usecase.library.GetTracksUseCase
import gd.app.musicplayer.domain.usecase.playback.ObservePlaybackStateUseCase
import gd.app.musicplayer.playback.PlaybackController
import gd.app.musicplayer.playback.PlaybackStartupInitializer
import gd.app.musicplayer.playback.queue.MusicPlaybackState
import javax.inject.Inject
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.distinctUntilChangedBy
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class TrackUiState(
    val musicId: Long? = null,
    val title: String = "",
    val artist: String = "",
    val album: String = "",
    val artworkSource: Any? = null,
    val isFavorite: Boolean = false
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
    observePlaybackStateUseCase: ObservePlaybackStateUseCase,
    private val playbackController: PlaybackController,
    private val playbackStartupInitializer: PlaybackStartupInitializer,
    private val playlistRepo: PlaylistRepo,
    private val getTracksUseCase: GetTracksUseCase,
    private val observeAlbumPictureUseCase: ObserveAlbumPictureUseCase,
) : ViewModel() {

    private val playbackStateFlow = observePlaybackStateUseCase()

    init {
        viewModelScope.launch {
            playbackStartupInitializer.initialize()
        }
    }

    val playbackState: StateFlow<MusicPlaybackState> =
        playbackStateFlow

    @OptIn(ExperimentalCoroutinesApi::class)
    val trackUiState: StateFlow<TrackUiState> =
        playbackState
            .map { state ->
                state.currentTrack
            }
            .distinctUntilChangedBy { music ->
                music?.id
            }
            .flatMapLatest { music ->
                if (music == null) {
                    flowOf(TrackUiState())
                } else {
                    combine(
                        playlistRepo.observeIsFavorite(music.id),
                        observeAlbumPicture(music)
                    ) { isFavorite, albumPicture ->
                        music.copy(albumPicture = albumPicture)
                            .toTrackUiState(isFavorite = isFavorite)
                    }
                }
            }
            .stateIn(
                scope = viewModelScope,
                started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS),
                initialValue = TrackUiState()
            )

    val progressUiState: StateFlow<PlaybackProgressUiState> =
        playbackState
            .map { state ->
                PlaybackProgressUiState(
                    isPlaying = state.isPlaying,
                    positionMs = state.positionMs,
                    durationMs = state.durationMs
                )
            }
            .distinctUntilChanged()
            .stateIn(
                scope = viewModelScope,
                started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS),
                initialValue = PlaybackProgressUiState()
            )

    val visualizerUiState: StateFlow<VisualizerUiState> =
        playbackState
            .map { state ->
                VisualizerUiState(
                    audioSessionId = state.audioSessionId,
                    isPlaying = state.isPlaying
                )
            }
            .distinctUntilChanged()
            .stateIn(
                scope = viewModelScope,
                started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS),
                initialValue = VisualizerUiState()
            )

    val currentIndex: StateFlow<Int> =
        playbackState
            .map { state ->
                state.currentIndex
            }
            .distinctUntilChanged()
            .stateIn(
                scope = viewModelScope,
                started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS),
                initialValue =  NO_QUEUE_INDEX
            )

    val playbackHighlightState: StateFlow<PlaybackHighlightState> =
        playbackState
            .map { state ->
                PlaybackHighlightState(
                    currentMusicId = state.currentTrack?.id,
                    isPlaying = state.isPlaying
                )
            }
            .distinctUntilChanged()
            .stateIn(
                scope = viewModelScope,
                started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS),
                initialValue = PlaybackHighlightState()
            )

    fun toggleFavorite(context: Context) {
        if (trackUiState.value.musicId == null) return

        playbackController.toggleFavorite(context)
    }

    fun playQueue(
        context: Context,
        queue: List<Music>,
        startIndex: Int
    ) {
        if (queue.isEmpty()) return

        playbackController.playQueue(
            context = context,
            queue = queue,
            startIndex = startIndex.coerceIn(
                minimumValue = 0,
                maximumValue = queue.lastIndex
            )
        )
    }

    fun shufflePlay(
        context: Context,
        queue: List<Music>
    ) {
        if (queue.isEmpty()) return

        playbackController.shufflePlay(
            context = context,
            queue = queue
        )
    }

    fun enqueue(
        context: Context,
        items: List<Music>
    ) {
        if (items.isEmpty()) return

        playbackController.enqueue(
            context = context,
            items = items
        )
    }

    fun playAllTracks(context: Context) {
        viewModelScope.launch {
            val queue = getTracksUseCase(MusicSet.Tracks)

            if (queue.isNotEmpty()) {
                playbackController.playQueue(
                    context = context,
                    queue = queue,
                    startIndex = 0
                )
            }
        }
    }

    fun onPrimaryPlayPauseClicked(context: Context) {
        val playbackState = playbackState.value

        if (!playbackState.initialized) {
            play(context)
            return
        }

        if (playbackState.currentTrack == null) {
            playAllTracks(context)
            return
        }

        if (playbackState.isPlaying) {
            pause(context)
        } else {
            play(context)
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

    fun seekTo(
        context: Context,
        positionMs: Long
    ) {
        playbackController.seekTo(
            context = context,
            positionMs = positionMs.coerceAtLeast(0L).toInt()
        )
    }

    fun seekTo(
        context: Context,
        positionMs: Int
    ) {
        playbackController.seekTo(
            context = context,
            positionMs = positionMs.coerceAtLeast(0)
        )
    }

    fun restartCurrentTrack(context: Context) {
        playbackController.restartCurrentTrack(context)
    }

    fun cyclePlayMode(context: Context) {
        playbackController.cyclePlayMode(context)
    }

    fun setStopAfterCurrentTrack(
        context: Context,
        enabled: Boolean
    ) {
        playbackController.setStopAfterCurrentTrack(
            context = context,
            enabled = enabled
        )
    }

    fun replaceQueue(
        context: Context,
        queue: List<Music>,
        currentIndex: Int
    ) {
        if (queue.isEmpty()) {
            playbackController.clearQueue(context)
            return
        }

        playbackController.replaceQueue(
            context = context,
            queue = queue,
            currentIndex = currentIndex.coerceIn(
                minimumValue = 0,
                maximumValue = queue.lastIndex
            )
        )
    }

    fun clearQueue(context: Context) {
        playbackController.clearQueue(context)
    }

    fun stop(context: Context) {
        playbackController.stop(context)
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

    private fun Music.toTrackUiState(
        isFavorite: Boolean
    ): TrackUiState {
        return TrackUiState(
            musicId = id,
            title = title,
            artist = artist,
            album = album,
            artworkSource = albumArtSource(),
            isFavorite = isFavorite
        )
    }

    private fun observeAlbumPicture(music: Music): Flow<String?> {
        return observeAlbumPictureUseCase(music.id)
            .map { artworkPath ->
                artworkPath ?: music.albumPicture
            }
            .distinctUntilChanged()
    }

    private companion object {
        private const val STOP_TIMEOUT_MILLIS = 5_000L
        private const val NO_QUEUE_INDEX = -1
    }
}
